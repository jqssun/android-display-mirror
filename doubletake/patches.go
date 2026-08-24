package airplay

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"errors"
	"fmt"
	"io"
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

func (c *AirPlayClient) PairTransientPassword(ctx context.Context, password string) error {
	c.pairType = c.transientPairingType()

	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return fmt.Errorf("generate ed25519: %w", err)
	}
	c.PairKeys = &PairKeys{Ed25519Public: pub, Ed25519Private: priv}

	if _, err := c.httpRequest("POST", "/pair-pin-start", "", nil, c.pinStartHeaders()); err != nil {
		var statusErr *HTTPStatusError
		if !errors.As(err, &statusErr) || statusErr.StatusCode != 453 {
			return fmt.Errorf("pair-pin-start: %w", err)
		}
	}
	if err := c.pairSetupTransientPassword(ctx, password); err != nil {
		return fmt.Errorf("pair-setup: %w", err)
	}
	return c.PairVerify(ctx)
}
