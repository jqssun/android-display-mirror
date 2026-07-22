package airplay

import (
	"regexp"
	"strconv"

	"howett.net/plist"
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

/*
receiver display resolution from GET /info; (0,0) if not advertised
*/
type receiverDisplay struct {
	Width        int `plist:"width"`
	Height       int `plist:"height"`
	WidthPixels  int `plist:"widthPixels"`
	HeightPixels int `plist:"heightPixels"`
}

func (c *AirPlayClient) ReceiverDisplaySize() (int, int) {
	resp, err := c.httpRequest("GET", "/info", "application/x-apple-binary-plist", nil)
	if err != nil {
		return 0, 0
	}
	var info struct {
		Displays []receiverDisplay `plist:"displays"`
	}
	if _, err := plist.Unmarshal(resp, &info); err != nil {
		return 0, 0
	}
	if len(info.Displays) == 0 {
		return 0, 0
	}
	d := info.Displays[0]
	if d.WidthPixels > 0 && d.HeightPixels > 0 {
		return d.WidthPixels, d.HeightPixels
	}
	return d.Width, d.Height
}
