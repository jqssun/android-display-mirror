package airplaylib

import (
	"fmt"
	"io"

	"doubletake/internal/airplay"
)

func SetAirPlay1Mode(enabled bool) {
	airplay.AirPlay1Mode = enabled
}

func (s *Session) SetAirPlay1FrameSize(width, height int) {
	s.mu.Lock()
	s.airplay1Width = width
	s.airplay1Height = height
	s.mu.Unlock()
}

// wire format: 128-byte little-endian header (u32 payload-length @0, u16 packet-type @4, u64 pts-ms @8, rest zero) followed by N payload bytes
// packetType = 1 is a one-shot "store" hdr record, 0 is a per-frame trigger
// first call emits store + trigger; subsequent calls emit just the trigger
func (s *Session) sendFrameAirPlay1(w io.Writer, annexBData []byte, needStore bool, frameWidth, frameHeight int, pts uint64) {
	writeHeader := func(payloadLen uint32, packetType uint16) error {
		var header [128]byte
		header[0] = byte(payloadLen)
		header[1] = byte(payloadLen >> 8)
		header[2] = byte(payloadLen >> 16)
		header[3] = byte(payloadLen >> 24)
		header[4] = byte(packetType)
		header[5] = byte(packetType >> 8)
		header[8] = byte(pts)
		header[9] = byte(pts >> 8)
		header[10] = byte(pts >> 16)
		header[11] = byte(pts >> 24)
		header[12] = byte(pts >> 32)
		header[13] = byte(pts >> 40)
		header[14] = byte(pts >> 48)
		header[15] = byte(pts >> 56)
		_, err := w.Write(header[:])
		return err
	}
	if needStore {
		if frameWidth <= 0 || frameHeight <= 0 {
			s.logf("[AIRPLAY1] frame size not set (call SetAirPlay1FrameSize first); aborting store")
			return
		}
		// hdr part is capped at 287 bytes so we send dimensions as text and let it pick up SPS/PPS inline from the first keyframe's NALs
		storePayload := []byte(fmt.Sprintf("[vsize]%d,%d", frameWidth, frameHeight))
		if err := writeHeader(uint32(len(storePayload)), 1); err != nil {
			s.logf("[AIRPLAY1] store header write error: %v", err)
			return
		}
		if _, err := w.Write(storePayload); err != nil {
			s.logf("[AIRPLAY1] store body write error: %v", err)
			return
		}
	}
	if err := writeHeader(uint32(len(annexBData)), 0); err != nil {
		s.logf("[AIRPLAY1] trigger header write error: %v", err)
		return
	}
	if _, err := w.Write(annexBData); err != nil {
		s.logf("[AIRPLAY1] trigger body write error: %v", err)
	}
}
