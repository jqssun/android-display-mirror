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

// airplay receiver
type Device struct {
	Name     string
	IP       string
	Port     int
	DeviceID string
}

// airplay session
type EventHandler interface {
	OnDeviceFound(deviceJSON string)
	OnConnected()
	OnDisconnected(err string)
	OnPinRequired()
	OnError(err string)
	OnLog(msg string)
}

// airplay connection
type Session struct {
	mu      sync.Mutex
	client  *airplay.AirPlayClient
	mirror  *airplay.MirrorSession
	handler EventHandler
	cancel  context.CancelFunc

	pipeW        *io.PipeWriter
	firstSendLog bool
	sessionStart time.Time

	airplay1Stored bool
	airplay1Width  int
	airplay1Height int
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
		client := airplay.NewAirPlayClient(host, port)
		if err := client.Connect(ctx); err != nil {
			s.handler.OnError("connect: " + err.Error())
			return
		}

		s.mu.Lock()
		s.client = client
		s.mu.Unlock()

		if airplay.AirPlay1Mode {
			airplay.AirPlay1Password = pin
		} else {
			if err := s.setupAirPlay2(ctx, client, pin); err != nil {
				return
			}
		}

		cfg := airplay.StreamConfig{
			Width:   width,
			Height:  height,
			FPS:     fps,
			NoAudio: true,
		}
		s.logf("[AIRPLAY] setting up mirror session %dx%d@%d (airplay1=%v)", width, height, fps, airplay.AirPlay1Mode)
		var mirror *airplay.MirrorSession
		var setupErr error
		if airplay.AirPlay1Mode {
			mirror, setupErr = client.SetupMirrorAirPlay1(ctx, cfg)
		} else {
			mirror, setupErr = client.SetupMirror(ctx, cfg)
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

func (s *Session) Disconnect() {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.pipeW != nil {
		s.pipeW.Close()
		s.pipeW = nil
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
