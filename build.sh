#!/usr/bin/env bash
mkdir -p app/libs
cd "$(dirname "$0")"/doubletake/doubletake
go install golang.org/x/mobile/cmd/gomobile@latest
export PATH="$(go env GOPATH)/bin:$PATH"

# android support
sed -i 's|\*ScreenCapture|io.Reader|' internal/airplay/mirror.go
ln -sfn ../airplaylib airplaylib
ln -sf ../../../patches.go internal/airplay/patches.go
ln -sf ../../../airplay1.go internal/airplay/airplay1.go

# fix stuck frames
sed -i 's|videoTimestampBias = 5 |videoTimestampBias = 500 |' internal/airplay/mirror.go

# apple receiver hax + fix audio setup
sed -i 's/deriveVideoKeys(encKey, videoStreamConnectionID)/deriveVideoKeys(patchAppleReceiverKey(encKey), videoStreamConnectionID)/' internal/airplay/mirror.go
sed -i '/audioRespBody, _, err2/i\\tif !cfg.NoAudio || !AppleReceiver {' internal/airplay/mirror.go
sed -i '/audioLatencySamples := uint32(0)/i\\t}' internal/airplay/mirror.go
sed -i '/videoURI := fmt.Sprintf/a\\tif cfg.NoAudio && AppleReceiver { audioURI = videoURI; }' internal/airplay/mirror.go

# pass video headers
sed -Ezi 's|if err != nil \{[[:space:]]+return nil, nil, err|if err != nil { return nil, respHeaders, err|' internal/airplay/client.go
sed -i 's|videoRespBody, _|videoRespBody, videoRespHeaders|' internal/airplay/mirror.go
sed -i 's|return nil, fmt.Errorf("no video data port in SETUP response")|if rp := parseTransportServerPort(videoRespHeaders["transport"]); rp > 0 { dataPort = rp; } else { return nil, fmt.Errorf("no video data port in SETUP response (headers=%+v, body_hex=%x, parsed=%+v)", videoRespHeaders, videoRespBody, videoResp) }|' internal/airplay/mirror.go

# might rewrite entire thing
go get golang.org/x/mobile/bind 2>/dev/null || true;
gomobile init && gomobile bind -v -target android -androidapi 28 -o ../../app/libs/airplaylib.aar ./airplaylib/ # -overlay $(realpath ../overlay.json)
git reset --hard && git clean -fdx
