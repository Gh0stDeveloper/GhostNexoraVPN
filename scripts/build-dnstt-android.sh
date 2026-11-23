#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
ndk_bin="${ANDROID_SDK_ROOT:?}/ndk/${ANDROID_NDK_VERSION:-27.0.12077973}/toolchains/llvm/prebuilt/linux-x86_64/bin"
upstream="$(mktemp -d)"
trap 'rm -rf "$upstream"' EXIT
revision=17aa1fed864cd5493e3d09a14df1b8b5cc1df123
git -C "$upstream" init --quiet
git -C "$upstream" fetch --quiet --depth=1 https://github.com/Mygod/dnstt.git "$revision"
git -C "$upstream" checkout --quiet --detach FETCH_HEAD
test "$(git -C "$upstream" rev-parse HEAD)" = "$revision"
cp "$root/scripts/dnstt/android.go" "$upstream/dnstt-client/ghostnexora.go"

# Bind an ephemeral loopback port in the native listener and report it over stdout.
python3 - "$upstream/dnstt-client/main.go" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
source = path.read_text()
anchor = "\tdefer ln.Close()"
assert source.count(anchor) == 1, "DNSTT listener changed"
path.write_text(source.replace(anchor, anchor + '\n\tfmt.Printf("GNX_DNSTT_PORT=%d\\n", ln.Addr().(*net.TCPAddr).Port)'))
PY

cd "$upstream"
go get github.com/phuslu/fastdns@46de6b716ce572d9183e7ab38a620f4461803667
go test ./dnstt-client
go build -mod=readonly -o "$upstream/gnx-dnstt-client" ./dnstt-client
go build -mod=readonly -o "$upstream/gnx-dnstt-server" ./dnstt-server
timeout 60s python3 "$root/scripts/dnstt/smoke.py" "$upstream/gnx-dnstt-client" "$upstream/gnx-dnstt-server"
build_abi() {
    local abi="$1" arch="$2" compiler="$3"
    local output="$root/app/build/generated/dnstt/jniLibs/$abi"
    test -x "$ndk_bin/$compiler"
    mkdir -p "$output"
    GOOS=android GOARCH="$arch" CGO_ENABLED=1 CC="$ndk_bin/$compiler" \
        go build -mod=readonly -trimpath -buildmode=pie -ldflags="-s -w -extldflags=-Wl,-z,max-page-size=16384" \
        -o "$output/libgnxdnstt.so" ./dnstt-client
}
build_abi arm64-v8a arm64 aarch64-linux-android26-clang
GOARM=7 build_abi armeabi-v7a arm armv7a-linux-androideabi26-clang
build_abi x86_64 amd64 x86_64-linux-android26-clang

mkdir -p "$root/app/build/generated/dnstt/licenses"
cp COPYING "$root/app/build/generated/dnstt/licenses/DNSTT.txt"
fastdns_dir="$(go list -m -f '{{.Dir}}' github.com/phuslu/fastdns)"
cp "$fastdns_dir/LICENSE" "$root/app/build/generated/dnstt/licenses/fastdns.txt"
