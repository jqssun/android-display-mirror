#!/usr/bin/env bash
mkdir -p app/libs
cd "$(dirname "$0")"/doubletake/doubletake
go install golang.org/x/mobile/cmd/gomobile@latest
export PATH="$(go env GOPATH)/bin:$PATH"

# android support
sed -i 's|\*ScreenCapture|io.Reader|' internal/airplay/mirror.go
ln -sfn ../airplaylib airplaylib

# fix stuck frames
sed -i 's|videoTimestampBias = 5 |videoTimestampBias = 500 |' internal/airplay/mirror.go

# fix apple receiver
sed -i 's/deriveVideoKeys(encKey, videoStreamConnectionID)/deriveVideoKeys(keyPatch(encKey), videoStreamConnectionID)/' internal/airplay/mirror.go
ln -sf ../../../key_patch.go internal/airplay/key_patch.go

# fix audio setup
sed -i '/audioRespBody, _, err2/i\\tif !cfg.NoAudio {' internal/airplay/mirror.go
sed -i '/audioLatencySamples := uint32(0)/i\\t}' internal/airplay/mirror.go
sed -i '/videoURI := fmt.Sprintf/a\\tif cfg.NoAudio { audioURI = videoURI; controlURI = videoURI; }' internal/airplay/mirror.go
# TODO: cleanup setup

go get golang.org/x/mobile/bind 2>/dev/null || true;
gomobile init && gomobile bind -v -target android -androidapi 28 -o ../../app/libs/airplaylib.aar ./airplaylib/ # -overlay $(realpath ../overlay.json)
git reset --hard && git clean -fdx
