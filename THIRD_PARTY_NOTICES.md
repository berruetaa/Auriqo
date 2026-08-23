# Third-party notices

This file records third-party assets and source integrations identified in the repository. It is intentionally explicit about what is verified and what still needs maintainer evidence. It is not a machine-generated exhaustive license report for every Gradle dependency.

## Fonts

The following font binaries are tracked under `app/src/main/res/font/`. The SHA-256 values identify the current repository blobs; they do not replace an upstream source record or the applicable license.

### BBH Bartle

- Files: `bbh_bartle_regular.ttf` and `bbh_bartle.xml`.
- Upstream project: [Studio-DRAMA/BBH](https://github.com/Studio-DRAMA/BBH).
- Declared license: SIL Open Font License 1.1, with a copy at [third_party/fonts/OFL-1.1.txt](third_party/fonts/OFL-1.1.txt).
- Current TTF SHA-256: `bbh_bartle_regular.ttf` - `6240252862fa9dadea44af7e9eb119320c89c667c9ef176bbff1d77241965f3a`.
- Before the next official artifact, verify the exact upstream binary and retain the corresponding source/version record.

### Cabinet Grotesk

- Fixed artwork: `branding/auriqo-logo.svg`, `branding/auriqo-wordmark.svg` and matching Android/Wear vector drawables.
- Source: [Cabinet Grotesk on Fontshare](https://www.fontshare.com/fonts/cabinet-grotesk).
- License reference: [Fontshare ITF Free Font License](https://fontshare.com/licenses/itf-ffl).
- Source binary used for the fixed outlines: Cabinet Grotesk Bold, SHA-256 `f2e2f7b99f1c17715567a84046e6ae2c13bbb24bb76847644df903f4b361f38d`.
- Fontshare permits use to create logos, vector drawings and static images, while its ITF FFL restricts redistribution of the Font Software. Auriqo therefore tracks only fixed glyph outlines and does not track or package the Cabinet TTF/OTF files.
- The outlined Auriqo wordmark is product artwork, not an installable font and not a general Cabinet glyph set. Contributors who need to regenerate it must obtain Cabinet directly from Fontshare and comply with its license.

### Removed unused font binaries

The unused `google_sans_flex.ttf` and `sans_flex.ttf` files were removed from the current tree because no source code referenced them and their exact redistributable source/license record was not established. Their historical blobs remain in Git history; they are not packaged by the current build.

## Source integrations

### BetterLyrics

The `betterlyrics` module contains the Kotlin client and TTML parser used by Auriqo. The Android implementation was introduced in commit `5721f005` and is currently under `betterlyrics/src/main/kotlin/com/auriqa/music/betterlyrics/`. The repository history does not contain a file-level mapping to the upstream browser extension, so the Kotlin code is not described as a byte-for-byte vendored copy.

The upstream [Better Lyrics repository](https://github.com/better-lyrics/better-lyrics) is GPLv3-licensed and requests attribution. Auriqo documents the relationship and keeps the code under the repository's GPLv3-compatible project licensing. If the maintainer has contrary authorship or permission information, update this notice before a stable release.

Historical notes refer to a pinned browser renderer and npm packages, but the current checkout has
no `betterlyrics/web` source, npm lockfile or generated web asset tree. The current shipped
Better Lyrics boundary is the Kotlin client and TTML parser above. Do not claim that the historical
renderer or its npm packages are part of a release until their source boundary, hashes and license
record are restored and verified.

### Other adapted or referenced code

- `kugou/src/main/kotlin/com/music/kugou/KuGou.kt` contains an attribution comment identifying an adaptation from [ViMusic](https://github.com/vfsfitvnm/ViMusic). Preserve that notice and verify the file-level license and modifications.
- Comments and history also refer to Metrolist, VIVI Music, SimpMusic and NewPipe Extractor. A project name alone is not proof that code was copied. File-level mappings and notices are tracked in [docs/PROVENANCE.md](docs/PROVENANCE.md).

### FFmpegKit

- Auriqo uses `dev.ffmpegkit-maintained:ffmpeg-kit-audio:6.0.3` for local MP3 export.
- The artifact is the community-maintained Android continuation of the retired
  `com.arthenica:ffmpeg-kit-audio` line. It keeps the `com.arthenica.ffmpegkit` API and is
  resolved from Maven Central.
- The maintained prebuilt AAR currently contains `arm64-v8a` and `x86_64` native libraries;
  it does not contain the legacy `armeabi-v7a` or `x86` libraries shipped by the retired AAR.
  Do not claim 32-bit FFmpeg export support for builds using this dependency until those ABIs
  are built and verified separately.
- License declared by the artifact: LGPL-3.0. Upstream source and notices:
  [ffmpegkit-maintained/ffmpeg](https://github.com/ffmpegkit-maintained/ffmpeg).

## Gradle and npm dependencies

The Android build resolves dependencies from Google Maven, Maven Central, JitPack and the additional repositories configured in Gradle. The Worker uses the exact packages and integrity values recorded in `workers/youtube-attribution/package-lock.json`.

A release-specific dependency inventory still needs to:

1. resolve the exact graph for every published Android variant;
2. collect each component's license and required notices from authoritative metadata;
3. check transitive native binaries and generated resources;
4. verify that packaging exclusions do not remove required notices; and
5. attach the result to the release review.

The lockfile and version catalog make this work reproducible, but package availability alone is not a license grant. Do not add a permissive-license claim without verifying the exact component.

## Services are not bundled dependencies

YouTube/Google, Spotify, Discord, Last.fm, ListenBrainz, Shazam-compatible endpoints, lyrics providers, AI providers, Cloudflare and Firebase are remote services, not licenses granted by this repository. Their terms, trademarks and availability remain the responsibility of the user and service operator. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) and [docs/LYRICS_PROVIDERS.md](docs/LYRICS_PROVIDERS.md).

## Maintainer release gate

Before an official artifact, attach the complete dependency license inventory. Keep the Cabinet artwork record, BetterLyrics attribution and adapted-code notices above in every release review.
