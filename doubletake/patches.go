package airplay

import (
	"io"
	"regexp"
	"strconv"
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
3rd party receivers (UxPlay) and HAP-paired receivers hash the pair-verify ECDH secret into the FairPlay stream key; whereas Apple receivers decrypt with the raw key over a plaintext pair-verify
*/
var AppleReceiver bool

func mixesStreamKey(encrypted bool) bool {
	return encrypted || !AppleReceiver
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
