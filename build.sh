#!/usr/bin/env bash
set -e
mkdir -p app/libs

ANDROID_NDK_VERSION=$(awk -F '"' '/ndkVersion/ {print $2}' app/build.gradle)
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "ndk;${ANDROID_NDK_VERSION}"
fi
ANDROID_NDK_HOME=$ANDROID_HOME/ndk/${ANDROID_NDK_VERSION}
GOMOBILE_VERSION="v0.0.0-20260611195102-4dd8f1dbf5d2"
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

# receiver hashes the ECDH secret into the stream key when pairing produced one
sed -i 's|deriveStreamMasterKey(c.fpAesKey, sharedSecret(c.PairKeys), c.encrypted)|deriveStreamMasterKey(c.fpAesKey, sharedSecret(c.PairKeys), true)|' internal/airplay/fairplay.go
sed -i 's|if c.encrypted \&\& len(sharedSecret(c.PairKeys)) > 0 {|if len(sharedSecret(c.PairKeys)) > 0 {|' internal/airplay/fairplay.go
sed -i 's|return fmt.Errorf("SETUP response omitted eventPort")|if !modernControlSetup { dbg("[EVENT] receiver omitted eventPort, continuing without event channel"); return nil }\n\t\treturn fmt.Errorf("SETUP response omitted eventPort")|' internal/airplay/mirror.go

# transient pair-setup
sed -i 's|func (c \*AirPlayClient) pairSetupTransient(ctx context.Context) error {|func (c *AirPlayClient) pairSetupTransient(ctx context.Context) error { return c.pairSetupTransientPassword(ctx, "") }\nfunc (c *AirPlayClient) pairSetupTransientPassword(ctx context.Context, password string) error {|' internal/airplay/pairing.go
sed -i 's|return c.completeSRPExchange(ctx, "", serverSalt, serverPub)|return c.completeSRPExchange(ctx, password, serverSalt, serverPub)|' internal/airplay/pairing.go

# might rewrite entire thing
go get golang.org/x/mobile/bind 2>/dev/null || true;
gomobile bind -v -trimpath -ldflags="-buildid= -extldflags=-Wl,-z,max-page-size=16384" -target android -androidapi 26 -o $OUT_DIR/airplaylib.aar ./airplaylib/ # -overlay $(realpath ../overlay.json)
