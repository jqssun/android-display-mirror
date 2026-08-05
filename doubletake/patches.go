package airplay

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"regexp"
	"strconv"
	"strings"
)

/*
backs AudioCapture with pipe instead of gst subprocess; android feeds S16LE 44.1kHz stereo PCM from MediaProjection and ReadFrame does the ALAC framing
*/
func NewPipeAudioCapture() (*AudioCapture, *io.PipeWriter) {
	r, w := io.Pipe()
	return &AudioCapture{pcmPipe: r, waitCh: make(chan struct{})}, w
}

/*
AudioCapture.Stop waits on waitCh which only gst closes, so it deadlocks here
*/
func StopPipeAudioCapture(ac *AudioCapture, w *io.PipeWriter) {
	if ac == nil || ac.stopped {
		return
	}
	ac.stopped = true
	w.Close()
	close(ac.waitCh)
}

/*
UxPlay and HAP-paired receivers hash the pair-verify ECDH secret into the FairPlay stream key; Apple receivers use raw key over plaintext pair-verify
*/
var AppleReceiver bool

func mixesStreamKey(encrypted bool) bool {
	return encrypted || !AppleReceiver
}

/*
airPlayDescription_copyPairingPeerPublicKey
*/
type pkBytes []byte

func (p *pkBytes) UnmarshalPlist(unmarshal func(interface{}) error) error {
	var data []byte
	if unmarshal(&data) == nil {
		*p = data
		return nil
	}
	var s string
	if err := unmarshal(&s); err != nil {
		return err
	}
	b, err := hex.DecodeString(s)
	if err != nil {
		return fmt.Errorf("pk: neither data nor hex: %w", err)
	}
	*p = b
	return nil
}

type plistNum int

func (n *plistNum) UnmarshalPlist(unmarshal func(interface{}) error) error {
	var i int64
	if unmarshal(&i) == nil {
		*n = plistNum(i)
		return nil
	}
	var f float64
	if err := unmarshal(&f); err != nil {
		return err
	}
	*n = plistNum(f)
	return nil
}

/*
CFBoolean
*/
type plistBool bool

func (b *plistBool) UnmarshalPlist(unmarshal func(interface{}) error) error {
	var v bool
	if unmarshal(&v) == nil {
		*b = plistBool(v)
		return nil
	}
	var i int64
	if err := unmarshal(&i); err != nil {
		return err
	}
	*b = i != 0
	return nil
}

/*
airPlayDescription_isAppleReceiver, airPlayDescription_isThirdPartyDevice
*/
const (
	featureMaskThirdParty = uint64(1)<<26 | uint64(1)<<51
	uxplayPI              = "2e388006-13ba-4041-9a67-25dd4a43d536"
)

func IsAppleReceiver(info *ReceiverInfo) bool {
	if info == nil || info.PI == "" || info.PI == uxplayPI {
		return false
	}
	if info.Features&featureMaskThirdParty != 0 {
		return false
	}
	return strings.HasPrefix(info.Model, "AppleTV") ||
		strings.HasPrefix(info.Model, "AudioAccessory")
}

func DetectAppleReceiver(info *ReceiverInfo) bool {
	AppleReceiver = IsAppleReceiver(info)
	return AppleReceiver
}

/*
airPlayDescription_supportsCUPairingAndEncryption, apsession_requiresHKPairVerify: only the numbers are asserted
*/
const (
	featureMaskHomeKitPairing = uint64(1)<<38 | uint64(1)<<43 |
		uint64(1)<<46 | uint64(1)<<48
	statusFlagPasswordRequired = uint64(1) << 7
)

func UseHomeKitPairing(info *ReceiverInfo) bool {
	if info == nil {
		return false
	}
	if info.StatusFlags&statusFlagPasswordRequired != 0 {
		return false // password path wins over features
	}
	return info.Features&featureMaskHomeKitPairing != 0
}

/*
pairing flavor chosen rather than probed; needs GetInfo first
*/
func (c *AirPlayClient) PairTransientAuto(ctx context.Context) error {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return fmt.Errorf("generate ed25519: %w", err)
	}
	c.PairKeys = &PairKeys{Ed25519Public: pub, Ed25519Private: priv}

	if UseHomeKitPairing(c.info) {
		dbg("[PAIR] /info selects CoreUtils/HomeKit pairing")
		if err := c.pairSetupTransient(ctx); err != nil {
			return fmt.Errorf("pair-setup: %w", err)
		}
		return c.PairVerify(ctx)
	}

	dbg("[PAIR] /info selects legacy pairing")
	serverPub, err := c.rawPairSetup(ctx)
	if err != nil {
		return fmt.Errorf("raw pair-setup: %w", err)
	}
	// legacy pair-verify checks the sig against pair-setup's key, not /info's
	if c.info == nil {
		c.info = &ReceiverInfo{}
	}
	c.info.PK = serverPub
	return c.rawPairVerify(ctx)
}

/*
parses server video data port from the transport header
*/
var portRegex = regexp.MustCompile(`server_port=(\d+)`)

func parseTransportServerPort(transport string) int {
	match := portRegex.FindStringSubmatch(transport)
	if len(match) > 1 {
		port, _ := strconv.Atoi(match[1])
		return port
	}
	return 0
}
