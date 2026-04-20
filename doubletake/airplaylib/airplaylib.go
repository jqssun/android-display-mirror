// Package airplaylib provides Android gomobile bindings for AirPlay 2 screen mirroring.
package airplaylib

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"sync"
	"time"

	"doubletake/internal/airplay"
)

// Device represents a discovered AirPlay receiver.
type Device struct {
	Name     string
	IP       string
	Port     int
	DeviceID string
}

// EventHandler receives callbacks from the AirPlay session.
type EventHandler interface {
	OnDeviceFound(deviceJSON string)
	OnConnected()
	OnDisconnected(err string)
	OnPinRequired()
	OnError(err string)
	OnLog(msg string)
}

// Session manages an AirPlay mirroring connection.
type Session struct {
	mu      sync.Mutex
	client  *airplay.AirPlayClient
	mirror  *airplay.MirrorSession
	handler EventHandler
	cancel  context.CancelFunc

	pipeW *io.PipeWriter
}

// NewSession creates a new AirPlay session.
func NewSession(handler EventHandler) *Session {
	return &Session{handler: handler}
}

func (s *Session) logf(format string, args ...interface{}) {
	s.handler.OnLog(fmt.Sprintf(format, args...))
}

// SetAppleReceiver toggles the Apple receiver key-derivation path.
func SetAppleReceiver(enabled bool) {
	airplay.AppleReceiver = enabled
}

// Discover scans the network for AirPlay devices for the given duration (ms).
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

// Connect establishes connection and sets up mirroring.
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

		if _, err := client.GetInfo(); err != nil {
			s.handler.OnError("getinfo: " + err.Error())
			client.Close()
			return
		}

		if err := client.Pair(ctx, pin); err != nil {
			s.logf("[AIRPLAY] pairing failed: %v", err)
			if pinErr := client.StartPINDisplay(); pinErr != nil {
				s.logf("[AIRPLAY] StartPINDisplay failed: %v", pinErr)
			}
			client.Close()
			s.handler.OnPinRequired()
			return
		}
		s.logf("[AIRPLAY] pairing succeeded")

		if err := client.FairPlaySetup(ctx); err != nil {
			s.logf("[AIRPLAY] FairPlay setup FAILED: %v", err)
		} else {
			s.logf("[AIRPLAY] FairPlay setup succeeded")
		}

		cfg := airplay.StreamConfig{
			Width:   width,
			Height:  height,
			FPS:     fps,
			NoAudio: true,
		}
		s.logf("[AIRPLAY] setting up mirror session %dx%d@%d", width, height, fps)
		mirror, err := client.SetupMirror(ctx, cfg)
		if err != nil {
			s.handler.OnError("setup_mirror: " + err.Error())
			client.Close()
			return
		}
		s.logf("[AIRPLAY] mirror session ready, data port=%d", mirror.DataPort)

		// Create pipe: Java writes Annex-B → StreamFrames reads & processes
		pipeR, pipeW := io.Pipe()

		s.mu.Lock()
		s.mirror = mirror
		s.pipeW = pipeW
		s.mu.Unlock()

		// Start StreamFrames in background — the EXACT same code path as Linux
		go func() {
			err := mirror.StreamFrames(ctx, pipeR, 0)
			if err != nil {
				s.logf("[AIRPLAY] StreamFrames ended: %v", err)
			}
			s.handler.OnDisconnected(fmt.Sprintf("%v", err))
		}()

		s.handler.OnConnected()
	}()
}

// SendFrame writes raw Annex-B H.264 data into the pipe for StreamFrames to process.
func (s *Session) SendFrame(annexBData []byte, isKeyframe bool) {
	s.mu.Lock()
	w := s.pipeW
	s.mu.Unlock()
	if w == nil {
		return
	}
	// Just write raw Annex-B data — StreamFrames handles all NAL parsing,
	// AVCC conversion, codec frame generation, and encryption.
	_, err := w.Write(annexBData)
	if err != nil {
		s.logf("[AIRPLAY] pipe write error: %v", err)
	}
}

// Disconnect tears down the session.
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
