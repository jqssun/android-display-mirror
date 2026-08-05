// provides gomobile bindings for doubletake
package airplaylib

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"sync"
	"time"

	"doubletake/internal/airplay"
)

type Device struct {
	Name     string
	IP       string
	Port     int
	DeviceID string
}

type EventHandler interface {
	OnDeviceFound(deviceJSON string)
	OnConnected()
	OnDisconnected(err string)
	OnPinRequired()
	OnError(err string)
	OnLog(msg string)
}

type Session struct {
	mu      sync.Mutex
	client  *airplay.AirPlayClient
	mirror  *airplay.MirrorSession
	handler EventHandler
	cancel  context.CancelFunc

	pipeW        *io.PipeWriter
	firstSendLog bool
	sessionStart time.Time

	audioW       *io.PipeWriter
	audioCapture *airplay.AudioCapture

	airplay1Stored bool
	airplay1Width  int
	airplay1Height int

	streamWidth  int
	streamHeight int
}

// encode resolution (receiver display when known), read by the Android side
func (s *Session) StreamWidth() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.streamWidth
}

func (s *Session) StreamHeight() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.streamHeight
}

// airplay1's software decoder needs landscape, <=1280x720, even dimensions
func clampAirPlay1(w, h int) (int, int) {
	if h > w {
		w, h = h, w
	}
	if w > 1280 {
		h = h * 1280 / w
		w = 1280
	}
	if h > 720 {
		w = w * 720 / h
		h = 720
	}
	return w &^ 1, h &^ 1
}

func NewSession(handler EventHandler) *Session {
	return &Session{handler: handler}
}

func (s *Session) logf(format string, args ...interface{}) {
	s.handler.OnLog(fmt.Sprintf(format, args...))
}

func (s *Session) Discover(durationMs int) {
	go func() {
		timeout := time.Duration(durationMs) * time.Millisecond
		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		defer cancel()

		devices, err := airplay.DiscoverAirPlayDevices(ctx)
		if err != nil {
			s.handler.OnError("discover: " + err.Error())
			return
		}
		for _, d := range devices {
			dev := Device{Name: d.Name, IP: d.IP, Port: d.Port, DeviceID: d.DeviceID}
			data, _ := json.Marshal(dev)
			s.handler.OnDeviceFound(string(data))
		}
	}()
}

func (s *Session) Connect(host string, port int, pin string, width int, height int, fps int) {
	go func() {
		s.mu.Lock()
		if s.cancel != nil {
			s.cancel()
		}
		ctx, cancel := context.WithCancel(context.Background())
		s.cancel = cancel
		s.mu.Unlock()

		airplay.DebugMode = true
		// cmd/doubletake default; 1ms library default gives Apple no jitter budget
		airplay.SetTargetLatency(100 * time.Millisecond)
		client := airplay.NewAirPlayClient(host, port)
		if err := client.Connect(ctx); err != nil {
			s.handler.OnError("connect: " + err.Error())
			return
		}

		s.mu.Lock()
		s.client = client
		s.mu.Unlock()

		// match the receiver's display; airplay1 keeps caller dims + clamp
		if airplay.AirPlay1Mode {
			airplay.AirPlay1Password = pin
			width, height = clampAirPlay1(width, height)
		} else {
			info, err := s.setupAirPlay2(ctx, client, pin)
			if err != nil {
				return
			}
			if rw, rh := info.DisplaySize(); rw > 0 && rh > 0 {
				s.logf("[AIRPLAY] using receiver display %dx%d (caller requested %dx%d)", rw, rh, width, height)
				width, height = rw, rh
			}
		}
		s.mu.Lock()
		s.streamWidth = width
		s.streamHeight = height
		s.mu.Unlock()

		s.logf("[AIRPLAY] setting up mirror session %dx%d@%d (airplay1=%v)", width, height, fps, airplay.AirPlay1Mode)
		var mirror *airplay.MirrorSession
		var setupErr error
		if airplay.AirPlay1Mode {
			mirror, setupErr = client.SetupMirrorAirPlay1(ctx)
		} else {
			mirror, setupErr = client.SetupMirror(ctx, airplay.StreamConfig{FPS: fps})
		}
		if setupErr != nil {
			if errors.Is(setupErr, airplay.ErrAirPlay1PasswordRequired) {
				s.logf("[AIRPLAY] receiver requires password")
				client.Close()
				s.handler.OnPinRequired()
				return
			}
			s.handler.OnError("setup_mirror: " + setupErr.Error())
			client.Close()
			return
		}
		s.logf("[AIRPLAY] mirror session ready, data port=%d", mirror.DataPort)

		pipeR, pipeW := io.Pipe()

		s.mu.Lock()
		s.mirror = mirror
		s.pipeW = pipeW
		s.sessionStart = time.Now()
		s.airplay1Stored = false
		s.firstSendLog = false
		s.mu.Unlock()

		go func() {
			var streamErr error
			if airplay.AirPlay1Mode {
				streamErr = mirror.StreamFramesAirPlay1(ctx, pipeR)
			} else {
				streamErr = mirror.StreamFrames(ctx, pipeR, 0)
			}
			if streamErr != nil {
				s.logf("[AIRPLAY] frame forwarder ended: %v", streamErr)
			}
			s.handler.OnDisconnected(fmt.Sprintf("%v", streamErr))
		}()

		// StreamAudio reads only after first video frame, so this pipe backpressures until then
		if mirror.HasAudio() {
			capture, audioW := airplay.NewPipeAudioCapture()
			s.mu.Lock()
			s.audioCapture = capture
			s.audioW = audioW
			s.mu.Unlock()
			go func() {
				if err := mirror.StreamAudio(ctx, capture, mirror.AudioStream()); err != nil && ctx.Err() == nil {
					s.logf("[AIRPLAY] audio forwarder ended: %v", err)
				}
			}()
			s.logf("[AIRPLAY] audio stream negotiated")
		} else {
			s.logf("[AIRPLAY] receiver negotiated no audio stream")
		}

		s.handler.OnConnected()
	}()
}

func (s *Session) SendFrame(annexBData []byte, isKeyframe bool) {
	s.mu.Lock()
	w := s.pipeW
	firstLog := !s.firstSendLog
	if firstLog {
		s.firstSendLog = true
	}
	needStore := airplay.AirPlay1Mode && !s.airplay1Stored
	if needStore {
		s.airplay1Stored = true
	}
	frameWidth, frameHeight := s.airplay1Width, s.airplay1Height
	tsMillis := time.Since(s.sessionStart).Milliseconds()
	s.mu.Unlock()
	if w == nil {
		return
	}
	if firstLog {
		dumpN := len(annexBData)
		if dumpN > 32 {
			dumpN = 32
		}
		s.logf("[AIRPLAY] first SendFrame: %d bytes, keyframe=%v, leading hex=%x", len(annexBData), isKeyframe, annexBData[:dumpN])
	}

	if airplay.AirPlay1Mode {
		s.sendFrameAirPlay1(w, annexBData, needStore, frameWidth, frameHeight, uint64(tsMillis))
		return
	}
	// AirPlay 2: StreamFrames on the reader side does NAL parsing, AVCC wrapping, codec-frame packetization and ChaCha20 encryption, so the sender just dumps raw Annex-B into the pipe
	if _, err := w.Write(annexBData); err != nil {
		s.logf("[AIRPLAY] pipe write error: %v", err)
	}
}

// airplay1 never negotiates one
func (s *Session) HasAudio() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.audioW != nil
}

// interleaved S16LE 44.1kHz stereo PCM
func (s *Session) SendAudio(pcm []byte) {
	s.mu.Lock()
	w := s.audioW
	s.mu.Unlock()
	if w == nil {
		return
	}
	if _, err := w.Write(pcm); err != nil {
		s.logf("[AIRPLAY] audio pipe write error: %v", err)
	}
}

func (s *Session) Disconnect() {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.pipeW != nil {
		s.pipeW.Close()
		s.pipeW = nil
	}
	if s.audioW != nil {
		airplay.StopPipeAudioCapture(s.audioCapture, s.audioW)
		s.audioW = nil
		s.audioCapture = nil
	}
	if s.cancel != nil {
		s.cancel()
		s.cancel = nil
	}
	if s.mirror != nil {
		s.mirror.Close()
		s.mirror = nil
	}
	if s.client != nil {
		s.client.Close()
		s.client = nil
	}
}
