# Release guide

## Unsigned CI artifacts

GitHub Actions builds `app-release-unsigned.apk`. These are for smoke testing only.

## Signed release APK / AAB (local)

1. Create a keystore (once):

```bash
keytool -genkey -v \
  -keystore soriano-code-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias soriano-code
```

2. Add to `~/.gradle/gradle.properties` (do not commit):

```properties
SORIANO_CODE_STORE_FILE=/absolute/path/soriano-code-release.jks
SORIANO_CODE_STORE_PASSWORD=...
SORIANO_CODE_KEY_ALIAS=soriano-code
SORIANO_CODE_KEY_PASSWORD=...
```

3. Optional: wire `signingConfigs` in `app/build.gradle.kts` reading those properties, then:

```bash
./gradlew assembleGithubRelease
# or
./gradlew bundleGithubRelease
```

4. Verify:

```bash
apksigner verify --print-certs app/build/outputs/apk/github/release/app-github-release.apk
```

## Versioning

- `versionName` / `versionCode` live in `app/build.gradle.kts`
- Tag releases as `vX.Y.Z` matching `versionName`
- Update `CHANGELOG.md` (or GitHub Release notes) with user-facing changes

## Application identity

- `namespace` is `com.sorianocode` and controls Kotlin package names, `R`, and `BuildConfig`.
- `applicationId` stays `com.yugahashimoto.andcode`. Android treats it as the installed app identity.
- Never change `applicationId` for an existing release. Doing so creates a separate app, blocks in-place updates, loses access to the old data directory, requires new Firebase registration, and needs new Play and F-Droid listings.
- Always publish a `versionCode` greater than the previous release. Sign with the key that matches the existing distribution line; v1.2.28 intentionally starts a new line (see below).
- `google-services.json`, benchmark targets, F-Droid metadata paths, and battery scripts must use the same `applicationId`.

## Signing key transition (v1.2.28)

v1.2.28 starts a new APK signing lineage. The previous release key had certificate
SHA-256 `f036e07002d8c2e6a5a64000f1211398d4831ff37cf280456a9a26d2f12617df`.
The v1.2.28 release key has certificate SHA-256
`f5fc56a9af5460e40e06761a6c2957d96cc1d987a816601a6137c606f062952b`.

Existing installs signed with the previous key cannot update in place. Users must
export their app data and reinstall, or keep the old installation. The new key does
not affect Google Play installs signed by Play App Signing, but the GitHub and
self-hosted F-Droid APKs use the new key.

The F-Droid metadata snapshot below still describes v1.2.27 and keeps the old
`AllowedAPKSigningKeys` fingerprint. Update that field to the new fingerprint when
submitting v1.2.28 to fdroiddata.

## Pre-release checklist

- [ ] `./gradlew testGithubDebugUnitTest lintGithubDebug assembleGithubRelease`
- [ ] Manual smoke: local install, chat, permission approve/reject, remote connect
- [ ] `THIRD_PARTY_NOTICES.md` still accurate
- [ ] No secrets in git history

## F-Droid-compatible binary repository

The repository also has a `Publish F-Droid repository` workflow. It publishes
the signed APKs from GitHub Releases as a self-hosted F-Droid binary repository on
GitHub Pages. This is not an application submission to the official F-Droid
repository and does not require an F-Droiddata review.

Before enabling the workflow, create a dedicated repository signing keystore
and add these GitHub Actions secrets:

- `F_DROID_REPO_KEYSTORE_BASE64`: base64-encoded repository keystore
- `F_DROID_REPO_KEYSTORE_PASSWORD`: keystore password
- `F_DROID_REPO_KEY_ALIAS`: repository key alias
- `F_DROID_REPO_KEY_PASSWORD`: repository key password

For example, create the keystore locally with:

```bash
keytool -genkeypair -v \
  -keystore fdroid-repo.keystore \
  -alias soriano-code-fdroid \
  -keyalg RSA -keysize 4096 -validity 10000
base64 fdroid-repo.keystore | tr -d '\n'
```

Put the final command's output in `F_DROID_REPO_KEYSTORE_BASE64`. The other
three values must match the keystore when it is created. Do not commit the
keystore or its passwords.

The repository key is separate from the APK signing key. Back it up securely;
changing it makes existing clients treat the repository as a new repository.

Enable GitHub Pages with `GitHub Actions` as the source. After a published
release, users can add:

```text
https://soriano-glitch.github.io/soriano-code/fdroid/repo/
```

The workflow retains the latest 100 non-draft, non-prerelease GitHub releases.

## Official F-Droid catalog (build-from-source)

The self-hosted repository above only republishes the GitHub-signed APK; it does
not put Soriano Code in the official F-Droid catalog (browsable by category, e.g.
"AI Chat"). That requires F-Droid's own build server to compile the app from
source, which does not accept Firebase/Google Play services.

The `app` module has a `distribution` flavor dimension for this:

- `github` — current behavior, includes Firebase Analytics/Crashlytics.
- `fdroid` — no Firebase code at all (`app/src/fdroid/.../diagnostics/`
  provides no-op `AnalyticsReporter`/`CrashReporter` in place of
  `app/src/github/.../diagnostics/`, which keeps the Firebase-backed ones).

Build it locally with:

```bash
# The fdroid flavor builds proot, libtalloc and libandroid-shmem from source with the NDK
# (no prebuilt binary is downloaded), exactly as F-Droid's build server does. The
# dependencies and NDK are fetched on the first run.
bash scripts/build_android_runtime_native_debs.sh --setup --output-dir build/termux-debs
./gradlew -Psorianocode.fdroidBuild=true -Psorianocode.sourceNativeLibs=true :app:assembleFdroidRelease
```

`sorianocode.sourceNativeLibs=true` makes the asset step extract the `.deb` files under
`build/termux-debs` instead of downloading the pinned mirror packages; without it the build
uses the mirror path (which is what normal CI and day-to-day `assembleGithubDebug` do, so no
NDK is needed). `Psorianocode.fdroidBuild=true` additionally skips applying the
`com.google.gms.google-services` / `com.google.firebase.crashlytics` Gradle plugins outright
(they process `google-services.json` project-wide regardless of flavor, so leaving them
applied would still embed inert Google project identifiers in the fdroid build).

Submitting to the official catalog means opening a merge request against
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). This has been done:
[!48005](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48005). The
metadata below is a snapshot of the branch at the time of writing (v1.2.27);
the live copy is the MR itself. Before submitting v1.2.28, update `versionName`,
`versionCode`, and `commit` in the snapshot to the tagged release commit.

```yaml
AntiFeatures:
  NonFreeAdd:
    en-US: can install and run proprietary agents at runtime (Anthropic Claude Code
      from downloads.claude.ai, Google Antigravity CLI), never required for core use
  NonFreeNet:
    en-US: running agents talks to hosted, non-free LLM provider APIs, and the app
      also calls the GitHub and GitLab APIs and Google OAuth for Antigravity; the
      optional GitHub OAuth sign-in is disabled in this build
  TetheredNet:
    en-US: the on-demand Vosk wake-word speech model is only ever fetched from alphacephei.com
Categories:
  - AI Chat
License: MIT
AuthorName: soriano-glitch
SourceCode: https://github.com/soriano-glitch/soriano-code
IssueTracker: https://github.com/soriano-glitch/soriano-code/issues
Changelog: https://github.com/soriano-glitch/soriano-code/releases

AutoName: Soriano Code

RepoType: git
Repo: https://github.com/soriano-glitch/soriano-code
Binaries: 
  https://github.com/soriano-glitch/soriano-code/releases/download/v%v/soriano-code-v%v-fdroid-release.apk

Builds:
  - versionName: 1.2.27
    versionCode: 66
    commit: db8ed2e86e7f32b4b9151c0bd1d7ba974a1dcb5a
    subdir: app
    sudo:
      - apt-get update
      - apt-get install -y autoconf autogen automake autopoint bison build-essential
        curl file flex g++ g++-multilib gawk gettext gnupg gperf git intltool jq libfl-dev
        libglib2.0-dev libltdl-dev libtool-bin lrzip lzip lz4 lzop m4 patch patchelf
        pkg-config python-is-python3 python3-pip python3-setuptools python3-venv python-wheel-common
        rsync scons tar unzip wget xz-utils zip zstd
      - mkdir -p /data/data /tmp/sorianocode-termux-build
      - chown -R vagrant /data /tmp/sorianocode-termux-build
    gradle:
      - fdroid
    srclibs:
      - TermuxPackages@2a342d4bd78454dd20760e1fded33939f1712b2d
    prebuild:
      - sed -i -e '/firebase/d' -e '/gms/d' {..,.}/build.gradle.kts
      - export REPO_ROOT=$(git rev-parse --show-toplevel); TERMUX_PACKAGES_DIR=$$TermuxPackages$$
        NDK=$$NDK$$ ANDROID_HOME=$$SDK$$ bash "$REPO_ROOT/scripts/build_android_runtime_native_debs.sh"
    ndk: r29
    gradleprops:
      - sorianocode.fdroidBuild=true
      - sorianocode.sourceNativeLibs=true

AllowedAPKSigningKeys: f036e07002d8c2e6a5a64000f1211398d4831ff37cf280456a9a26d2f12617df

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.2.27
CurrentVersionCode: 66
```

This is the exact field order/quoting `fdroid rewritemeta` produces (it also
drops YAML comments, so any explanatory comments only live in this file and
the MR's discussion thread, not in the metadata itself).

`AllowedAPKSigningKeys` is the SHA-256 of the app's signing certificate,
extracted directly from a published release APK's APK Signing Block v2 (not
from the keystore) — `keytool`/`apksigner` weren't available locally, so this
was parsed by hand from the APK's binary signing block. `Binaries:` is a URL
template (`%v` = versionName) F-Droid's build server uses to fetch the
officially-published binary and diff it against what it builds from source,
as a supply-chain check.

The `Binaries:` URL points at a `-fdroid-release.apk` asset, not the plain
`-release.apk` one — the latter is the `github` flavor (Firebase included)
and will never byte-diff-match a `fdroid` flavor build. The Release workflow
(`.github/workflows/release.yml`) compiles the native runtime payload from the
pinned Termux recipes and then runs
`./gradlew -Psorianocode.fdroidBuild=true -Psorianocode.sourceNativeLibs=true :app:assembleFdroidRelease`
(deliberately without `GITHUB_CLIENT_ID`, matching how F-Droid's own build server
invokes it) and publishes that APK alongside the existing assets so this comparison
has something correct to compare against — and so no prebuilt binary is shipped. It's
signed with the same release signing config as the `github` flavor, hence the shared
`AllowedAPKSigningKeys` fingerprint above.
