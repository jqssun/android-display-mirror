#!/usr/bin/env bash
mkdir -p app/libs
cd "$(dirname "$0")"/doubletake/doubletake
go install golang.org/x/mobile/cmd/gomobile@latest
export PATH="$(go env GOPATH)/bin:$PATH"

sed -i 's|\*ScreenCapture|io.Reader|' internal/airplay/mirror.go
sed -i 's|videoTimestampBias = 5 |videoTimestampBias = 500 |' internal/airplay/mirror.go
ln -sfn ../airplaylib airplaylib
go get golang.org/x/mobile/bind 2>/dev/null || true;
gomobile init && gomobile bind -v -target android -androidapi 28 -o ../../app/libs/airplaylib.aar ./airplaylib/ # -overlay $(realpath ../overlay.json)
git reset --hard && git clean -fdx
