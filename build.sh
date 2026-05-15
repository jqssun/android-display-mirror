#!/usr/bin/env bash
set -e
mkdir -p app/libs

ANDROID_NDK_VERSION=$(awk -F '"' '/ndkVersion/ {print $2}' app/build.gradle)
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "ndk;${ANDROID_NDK_VERSION}"
fi
ANDROID_NDK_HOME=$ANDROID_HOME/ndk/${ANDROID_NDK_VERSION}
GOMOBILE_VERSION="v0.0.0-20260514233045-7de0a8fa7f4d"
OUT_DIR=$(realpath app/libs)
WORK_DIR=/tmp/airplaylib

# use canonical path for fdroid
rm -rf $WORK_DIR && mkdir -p $WORK_DIR
cp -a doubletake $WORK_DIR
cd $WORK_DIR/doubletake/doubletake
go install "golang.org/x/mobile/cmd/gomobile@${GOMOBILE_VERSION}"
go install "golang.org/x/mobile/cmd/gobind@${GOMOBILE_VERSION}"
export PATH="$(go env GOPATH)/bin:$PATH"
grep -q "^toolchain " go.mod || sed -i '/^go /a toolchain go1.25.10' go.mod

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
gomobile bind -v -trimpath -ldflags="-buildid=" -target android -androidapi 26 -o $OUT_DIR/airplaylib.aar ./airplaylib/ # -overlay $(realpath ../overlay.json)
