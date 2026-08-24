package airplay

import "io"

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
