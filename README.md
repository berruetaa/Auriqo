# Auriqo

Auriqo is an open-source Android music player built around YouTube Music/YouTube playback, local media, playlists, synchronized lyrics and optional integrations. It is a community project released under the GNU General Public License v3.0.

Auriqo is an independent project. It is not affiliated with, endorsed by or operated by YouTube, Google, Spotify, Discord, Last.fm, ListenBrainz, Shazam, or any lyrics provider.

## Project status

Auriqo is in active development with a stable `v1.0.5` release line. Core playback, local media,
playlists and lyrics are available today, while integrations continue to evolve with the services
they use. Provider APIs and media availability can change, so occasional breakage is expected.

The current release line is `v1.0.5`; its exact artifacts and checksums are recorded in
[docs/releases/v1.0.5.md](docs/releases/v1.0.5.md). Stable artifacts use the protected production
signing key. Historical alpha, RC and debug artifacts are not supported release baselines.

## Current functionality

The current codebase includes these user-facing areas, subject to provider availability and build variant:

- YouTube Music/YouTube playback through the local InnerTube client, plus local media playback.
- Queues, library and playlist workflows, including optional account-backed playlist access.
- Synchronized lyrics from multiple providers, rendered through a pinned Better Lyrics experience with source switching, offsets, translation/romanization, themes and signed Unison community actions.
- Optional lyrics translation through a user-selected AI endpoint.
- Artwork and canvas/video-related playback surfaces when a provider supplies the required data.
- Optional Spotify playlist import, Last.fm and ListenBrainz scrobbling, Discord Rich Presence, music recognition and Listen Together sessions.
- Standard Media3 controls on external surfaces plus a branded Wear OS companion/Tile; rich phone-to-watch synchronization and Google Cast are available in the GMS variant.

The list above describes code present in this repository; it is not a guarantee that every remote service is available in every country or at every point in time.

## Screenshots

There is not a current screenshot gallery yet. Earlier images were removed because they no longer represented the app. Current screenshots and short demos are welcome when they match a released build.

## Requirements

- JDK 21.
- Android SDK Platform 36 and Build-Tools provided by the Android SDK installation.
- Android NDK `27.0.12077973` for native components.
- Git. Android Studio is optional; the Gradle wrapper is the canonical build entry point.
- Node.js/npm only when changing or regenerating the embedded Better Lyrics web renderer or the attribution Worker.
- Linux, macOS or Windows with a working Android SDK path. Windows users should use `gradlew.bat`.

The repository pins Gradle 9.3.1, Android Gradle Plugin 9.0.0 and Kotlin 2.3.10 in the checked-in build configuration. Do not commit `local.properties`, Firebase configuration, private API credentials or release signing material. The tracked persistent debug keystore is intentionally public and must never sign a release.

The public CI builds the FOSS reference variant without private credentials. GMS and official release builds remain separate maintainer workflows.

## Build

Clone the repository and configure the SDK path locally:

```bash
git clone https://github.com/Auriqo/Auriqo.git
cd Auriqo

# Linux/macOS
cp local.properties.template local.properties
# Edit local.properties and set sdk.dir to your Android SDK directory.
```

On Windows PowerShell, use `Copy-Item local.properties.template local.properties` instead.

The FOSS debug variant does not require private credentials or a Firebase file:

```bash
./gradlew :app:assembleUniversalFossDebug --no-daemon
```

The APK is written to:

```text
app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
```

Install a locally built debug APK on an authorized device or emulator with:

```bash
adb install -r app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
```

The GMS debug variant enables Google Play Services integrations such as Cast:

```bash
./gradlew :app:assembleUniversalGmsDebug --no-daemon
```

`app/google-services.json` is optional and ignored by Git. When present, the current Gradle configuration also enables the Firebase plugins for that local configuration. It is only needed for the maintainer's Firebase setup; the FOSS build does not need it.

Release builds require maintainer-controlled signing material and are not part of the contributor setup. See [SETUP.md](SETUP.md) and [RELEASE_INFO.md](RELEASE_INFO.md).

## Tests and checks

Useful local checks include:

```bash
./gradlew :app:compileUniversalFossDebugKotlin --no-daemon
./gradlew :app:testUniversalFossDebugUnitTest --no-daemon
./gradlew :betterlyrics:testDebugUnitTest :unison:test --no-daemon
./gradlew :wear:testDebugUnitTest --no-daemon
./gradlew :innertube:testDebugUnitTest --no-daemon
./gradlew :letras:test --no-daemon
./gradlew :canvas:test --no-daemon
./gradlew :app:lintUniversalFossDebug --no-daemon
./gradlew :app:assembleUniversalFossDebug --no-daemon
```

The current checkout contains the Kotlin Better Lyrics client and TTML parser, but no tracked web
renderer source or npm project. Run the Better Lyrics Kotlin tests when changing that module. A web
renderer regeneration workflow must be documented before new renderer source is added.

Run the worker type check separately when changing `workers/youtube-attribution`:

```bash
cd workers/youtube-attribution
npm ci
npm run typecheck
```

## Variants

The `variant` dimension provides `foss` and `gms` builds. The `abi` dimension provides `universal`, `arm64`, `armeabi`, `x86` and `x86_64` builds. The `UniversalFossDebug` build is the least dependent on external credentials and is the reference build for pull requests. The custom Wear Data Layer publisher is GMS-only; FOSS still exposes standard Media3 controls to system surfaces.

The application identifier remains `com.auriqa.music` for compatibility with existing installs, preferences and deep links. Some URI hosts and package names inherited from earlier development also remain in technical code; do not rename them as a cosmetic cleanup.

## Optional integrations

Auriqo can connect to external services for lyrics, playlist access, scrobbling, recognition, Discord Rich Presence and Listen Together. These integrations are optional and are used when you choose the corresponding feature. The current data flows are summarized in [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

- Lyrics providers and BetterLyrics: [docs/LYRICS_PROVIDERS.md](docs/LYRICS_PROVIDERS.md).
- Better Lyrics providers and provenance: [docs/LYRICS_PROVIDERS.md](docs/LYRICS_PROVIDERS.md) and [docs/PROVENANCE.md](docs/PROVENANCE.md).
- Wear OS surfaces and variant boundary: [docs/WEAR_OS.md](docs/WEAR_OS.md).
- The playlist-attribution Worker: [docs/WORKERS.md](docs/WORKERS.md).
- Provenance and open license questions: [docs/PROVENANCE.md](docs/PROVENANCE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Documentation

- User guide: [docs/USER_GUIDE.md](docs/USER_GUIDE.md).
- Architecture and module map: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- Troubleshooting: [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).
- Setup and build help: [SETUP.md](SETUP.md) and [SUPPORT.md](SUPPORT.md).
- Contribution guide: [CONTRIBUTING.md](CONTRIBUTING.md).
- Project direction: [ROADMAP.md](ROADMAP.md).
- Release process: [RELEASE_INFO.md](RELEASE_INFO.md) and [docs/CI_RELEASE_REVIEW.md](docs/CI_RELEASE_REVIEW.md).
- Full documentation index: [docs/README.md](docs/README.md).


## Known limitations

- YouTube/YouTube Music and lyrics providers can change protocols, rate limits, authentication requirements or content availability.
- Some account features require a sign-in flow, cookie or OAuth token. These settings are kept in the app and should only be configured on a device you trust.
- Better Lyrics and Unison are fixed to documented upstream/service contracts; browser-only extension features are replaced by Android adapters and remote service changes can still cause partial outages.
- End-to-end custom Wear synchronization requires the GMS phone variant and a paired Wear OS device; FOSS uses standard system media controls.
- Listen Together uses WSS for remote servers; `ws://` is limited to localhost and common Android emulator loopback addresses.
- Official release signing is maintainer-only; contributors can build and install the FOSS debug APK.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), follow the [Code of Conduct](CODE_OF_CONDUCT.md), and open a focused pull request. Keep local configuration, credentials and generated build output out of commits. Security reports must follow [SECURITY.md](SECURITY.md), not a public issue.

## License

Auriqo is licensed under the [GNU General Public License v3.0](LICENSE). Third-party code, fixed brand artwork and services have additional notices and remaining dependency-provenance work documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [docs/PROVENANCE.md](docs/PROVENANCE.md).
