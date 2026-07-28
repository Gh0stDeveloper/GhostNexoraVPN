# Build and Release

## Toolchain

- JDK 17
- Gradle wrapper 8.9
- Android Gradle Plugin 8.7.3
- Kotlin 2.0
- compile/target SDK 35
- min SDK 26
- NDK `27.0.12077973`
- CMake 3.22.1
- AndroidLibXrayLite version pinned by CI

## Local build

Place the Xray AAR in `app/libs/` or use the repository fetch process, then run:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

## Release signing

Expected environment variables:

```text
KEYSTORE_FILE
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

The keystore and passwords must not be committed. CI should restore the keystore from protected secrets only for the signed-release job.

## Required CI stages

1. Checkout with pinned/maintained actions.
2. JDK and Android SDK setup.
3. NDK/CMake installation.
4. Pinned Xray AAR download and checksum verification.
5. Unit tests.
6. Android Lint.
7. Debug APK + JNI build.
8. Release APK + R8/resource shrinking.
9. Source deprecation gate.
10. DEX inspection for required runtime/reflection classes.
11. Native library ABI inspection.
12. Manifest policy checks.
13. Secret scan.
14. Dependency vulnerability scan.
15. SBOM generation.
16. APK signing-certificate, package, version, and SHA-256 verification.
17. Artifact upload.
18. Stable release publication from `main` only.

Some security stages are roadmap items until the workflow contains them; they should not be described as active merely because they appear in this checklist.

## Manifest checks

Reject:

- top-level `BIND_VPN_SERVICE` requests;
- obsolete broad storage permissions;
- exported components without a documented reason;
- missing VPN service permission;
- cleartext network policy changes without review;
- package visibility broader than required.

## Native checks

The APK should contain `libghostguard.so` and required Xray libraries for:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

CI must fail when an expected ABI is missing.

## R8 checks

Required stable runtime classes include:

- JSch crypto providers;
- `AndroidRandomBridge`;
- `AndroidSecureRandomProvider`;
- diagnostics engine;
- error catalog;
- application-routing preference model;
- payload engine where support tooling depends on its stable name.

Do not broadly disable R8. Add focused keep rules only when reflection/JNI/support tooling requires them.

## Versioning

- Increment `versionCode` for every installable release candidate.
- Use semantic `versionName` for public releases.
- Update `CHANGELOG.md` and README current version.
- Include commit SHA, version, Xray tag, APK SHA-256, and signing-certificate digest in release metadata.

## Release checklist

- CI green on exact head commit.
- Draft PR reviewed.
- Physical regression tests complete.
- Compatibility matrix updated.
- No unverified compatibility claims.
- GNX export/import migration tested.
- Update path tested from previous stable signing key.
- Privacy/security documentation current.
- Debug/validation artifacts distinguished from signed production APK.
- Release notes include known limitations.

## Rollback

Keep the previous signed stable APK and release metadata. A rollback must use the same signing identity and a higher Android version code unless users uninstall first. Never instruct users to uninstall before reminding them to export profiles.