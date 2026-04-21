package airplay

import (
	"regexp"
	"strconv"
)

/*
This sets the video key SHA-512 derivation to 16 zero bytes as its session key input instead of the encKey derived from FairPlay. This is needed to support Apple receivers where real FairPlay SAP is used, so we intentionally keep this value bzero'd in the setup phase in order to skip FairPlay in no audio mode.
*/
var AppleReceiver bool

func patchAppleReceiverKey(k []byte) []byte {
	if AppleReceiver {
		return make([]byte, 16)
	}
	return k
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
