package airplay

import (
	"context"
	"crypto/md5"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"time"

	"howett.net/plist"
)

var (
	AirPlay1Mode     bool
	AirPlay1Password string
)

var ErrAirPlay1PasswordRequired = fmt.Errorf("airplay1 receiver requires password")

func (c *AirPlayClient) SetupMirrorAirPlay1(ctx context.Context, cfg StreamConfig) (*MirrorSession, error) {
	videoURI := fmt.Sprintf("rtsp://%s:%d/video", c.host, c.port)
	setupHeaders := map[string]string{
		"Transport": "RTP/AVP/TCP;unicast;interleaved=0-1;mode=screen",
	}

	_, respHeaders, err := airplay1AuthRequest(c, "SETUP", videoURI, "", nil, setupHeaders)
	if err != nil {
		return nil, fmt.Errorf("airplay1 SETUP /video: %w", err)
	}
	dataPort := parseTransportServerPort(respHeaders["transport"])
	if dataPort == 0 {
		return nil, fmt.Errorf("airplay1 SETUP /video: no server_port in response (headers=%+v)", respHeaders)
	}
	dbg("[AIRPLAY1] /video SETUP returned server_port=%d", dataPort)

	if _, _, err := airplay1AuthRequest(c, "RECORD", videoURI, "", nil, nil); err != nil {
		return nil, fmt.Errorf("airplay1 RECORD /video: %w", err)
	}

	dataAddr := net.JoinHostPort(c.host, strconv.Itoa(dataPort))
	dataConn, err := net.DialTimeout("tcp", dataAddr, 5*time.Second)
	if err != nil {
		return nil, fmt.Errorf("airplay1 dial %s: %w", dataAddr, err)
	}
	if tc, ok := dataConn.(*net.TCPConn); ok {
		tc.SetNoDelay(true)
		tc.SetWriteBuffer(64 * 1024)
	}
	// data socket stays in HTTP parse mode until the client posts to /stream with a parsable bplist body; without this preamble every subsequent video packet is silently discarded
	preambleBody, err := plist.Marshal(map[string]interface{}{
		"latencyMs": int64(100),
	}, plist.BinaryFormat)
	if err != nil {
		dataConn.Close()
		return nil, fmt.Errorf("airplay1 preamble marshal: %w", err)
	}
	preambleHeader := fmt.Sprintf(
		"POST /stream HTTP/1.1\r\nContent-Type: application/x-apple-binary-plist\r\nContent-Length: %d\r\n\r\n",
		len(preambleBody),
	)
	if _, err := dataConn.Write([]byte(preambleHeader)); err != nil {
		dataConn.Close()
		return nil, fmt.Errorf("airplay1 preamble header: %w", err)
	}
	if _, err := dataConn.Write(preambleBody); err != nil {
		dataConn.Close()
		return nil, fmt.Errorf("airplay1 preamble body: %w", err)
	}
	dbg("[AIRPLAY1] data connected: %s, /stream preamble sent (%d bytes)", dataAddr, len(preambleBody))

	return &MirrorSession{
		client:      c,
		dataConn:    dataConn,
		DataPort:    dataPort,
		videoWidth:  cfg.Width,
		videoHeight: cfg.Height,
		sessionURI:  videoURI,
		noAudio:     true,
	}, nil
}

func airplay1AuthRequest(c *AirPlayClient, method, uri, contentType string, body []byte, extraHeaders map[string]string) ([]byte, map[string]string, error) {
	resp, headers, err := c.rtspRequest(method, uri, contentType, body, extraHeaders)
	if err == nil {
		return resp, headers, nil
	}
	if !strings.Contains(err.Error(), "HTTP 401") {
		return resp, headers, err
	}
	challenge := headers["www-authenticate"]
	if challenge == "" {
		return resp, headers, fmt.Errorf("401 without WWW-Authenticate: %w", err)
	}
	if AirPlay1Password == "" {
		return resp, headers, ErrAirPlay1PasswordRequired
	}
	authHeader, derr := buildDigestAuth(challenge, method, uri, AirPlay1Password)
	if derr != nil {
		return resp, headers, fmt.Errorf("digest: %w", derr)
	}
	dbg("[AIRPLAY1] %s %s got 401; retrying with Digest", method, uri)
	retryHeaders := map[string]string{"Authorization": authHeader}
	for k, v := range extraHeaders {
		retryHeaders[k] = v
	}
	retryResp, retryRespHeaders, retryErr := c.rtspRequest(method, uri, contentType, body, retryHeaders)
	if retryErr != nil && strings.Contains(retryErr.Error(), "HTTP 401") {
		// wrong password: clear it and surface the same sentinel so the UI re-prompts instead of "HTTP 401"
		AirPlay1Password = ""
		return retryResp, retryRespHeaders, ErrAirPlay1PasswordRequired
	}
	return retryResp, retryRespHeaders, retryErr
}

func buildDigestAuth(challenge, method, uri, password string) (string, error) {
	params := parseDigestParams(challenge)
	realm := params["realm"]
	nonce := params["nonce"]
	qop := params["qop"]
	if realm == "" || nonce == "" {
		return "", fmt.Errorf("missing realm/nonce in %q", challenge)
	}
	const username = "AirPlay"
	ha1 := md5Hex(username + ":" + realm + ":" + password)
	ha2 := md5Hex(method + ":" + uri)

	var response, cnonce, nc string
	if qop == "" {
		response = md5Hex(ha1 + ":" + nonce + ":" + ha2)
	} else {
		cnonce = randomHex(8)
		nc = "00000001"
		response = md5Hex(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2)
	}

	var b strings.Builder
	fmt.Fprintf(&b, `Digest username="%s", realm="%s", nonce="%s", uri="%s", response="%s"`,
		username, realm, nonce, uri, response)
	if qop != "" {
		fmt.Fprintf(&b, `, qop=%s, nc=%s, cnonce="%s"`, qop, nc, cnonce)
	}
	return b.String(), nil
}

func parseDigestParams(challenge string) map[string]string {
	out := map[string]string{}
	s := strings.TrimSpace(challenge)
	if strings.HasPrefix(strings.ToLower(s), "digest ") {
		s = s[len("Digest "):]
	}
	for len(s) > 0 {
		eq := strings.IndexByte(s, '=')
		if eq < 0 {
			break
		}
		key := strings.TrimSpace(s[:eq])
		s = s[eq+1:]
		var val string
		if len(s) > 0 && s[0] == '"' {
			end := strings.IndexByte(s[1:], '"')
			if end < 0 {
				break
			}
			val = s[1 : 1+end]
			s = s[2+end:]
		} else {
			end := strings.IndexByte(s, ',')
			if end < 0 {
				val = strings.TrimSpace(s)
				s = ""
			} else {
				val = strings.TrimSpace(s[:end])
				s = s[end+1:]
			}
		}
		out[strings.ToLower(key)] = val
		s = strings.TrimLeft(s, " ,\t")
	}
	return out
}

func md5Hex(s string) string {
	sum := md5.Sum([]byte(s))
	return hex.EncodeToString(sum[:])
}

func randomHex(n int) string {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		// fall back to time-based value so digest auth still produces a unique cnonce
		return fmt.Sprintf("%016x", time.Now().UnixNano())
	}
	return hex.EncodeToString(buf)
}

func (s *MirrorSession) StreamFramesAirPlay1(ctx context.Context, capture io.Reader) error {
	defer s.Close()
	buf := make([]byte, 64*1024)
	var totalBytes int64
	firstWrite := true
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		n, err := capture.Read(buf)
		if n > 0 {
			if firstWrite {
				dumpN := n
				if dumpN > 32 {
					dumpN = 32
				}
				dbg("[AIRPLAY1] first write: %d bytes, leading hex=%x", n, buf[:dumpN])
				firstWrite = false
			}
			s.dataMu.Lock()
			_, werr := s.dataConn.Write(buf[:n])
			s.dataMu.Unlock()
			if werr != nil {
				return fmt.Errorf("airplay1 data write (after %d bytes total): %w", totalBytes, werr)
			}
			totalBytes += int64(n)
			if totalBytes%(1<<20) < int64(n) {
				dbg("[AIRPLAY1] forwarded %d bytes total", totalBytes)
			}
		}
		if err != nil {
			if err == io.EOF {
				dbg("[AIRPLAY1] capture EOF after %d bytes", totalBytes)
				return nil
			}
			return err
		}
	}
}
