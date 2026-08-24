# Architecture

Auriqo is a multi-module Kotlin Android project. The Gradle wrapper and version catalog are the
source of truth for toolchain and dependency versions.

## Application layers

- `app/`: Android application, Compose UI, playback service, settings, library workflows and
  optional integrations.
- `innertube/`: YouTube and YouTube Music request models, parsers and page clients.
- `betterlyrics/`: Kotlin Better Lyrics client, models and TTML parser. The current checkout does
  not contain the historical browser-renderer source or generated web asset tree.
- `unison/`: lyrics identity and signed community actions.
- `lrclib/`, `paxsenixlyrics/`, `kugou/`, `simpmusic/`, `youlyplus/` and `letras/`: lyrics
  provider adapters.
- `canvas/`, `applecanvas/`, `artistvideo/` and related modules: artwork and media enrichment.
- `wear/`: Wear OS companion application and Tile.
- `workers/youtube-attribution/`: optional attribution Worker; it is not required by the FOSS
  playback build.

## Playback flow

1. The playback service assigns a `PB-XXXXXXXX` trace to the user request and keeps the player
   instance, Media3 factories and OkHttp client alive for reuse.
2. The current item and a bounded look-ahead (at most three items, concurrency two) resolve on
   `Dispatchers.IO`. The next item is the highest-priority candidate and, on an unmetered network
   without Data Saver, only its first 64 KiB are warmed into the player cache.
3. A valid stream-resolution cache entry is accepted only outside its expiry safety margin and
   only if its media/generation token is current. Cache hits therefore skip InnerTube, player
   JavaScript and PoToken resolution in the Media3 critical path.
4. Media3's `ResolvingDataSource` remains a synchronous fallback boundary. It first checks the
   same short-lived cache; only a genuine miss calls the resolver on an IO dispatcher. The normal
   cold path defers stream validation to Media3's real GET rather than paying for a duplicate HEAD.
5. YouTube player JavaScript is fetched and evaluated by the native cipher runtime when signature
   or `n` transformations are needed. A small WebView bridge is retained for PoToken acquisition
   only.
6. The service publishes state to Android system controls and, in the GMS variant, to the Wear
   data channel and Cast integration. `PLAYER_READY` is not reported as audio start: the diagnostic
   `FIRST_AUDIO` marker is emitted on the READY + `isPlaying` transition, the closest public Media3
   signal available without an audio-sink first-sample callback.

### Playback recovery and diagnostics

Every Media3 error keeps its original Media3 code/class and cause chain while being classified into
an Auriqo stage/category/code. HTTP status, safe host/query metadata, playability status, cache
state, network type, timing, generation and bounded recovery actions are retained in a circular
in-memory diagnostic buffer. Recoverable stream/network failures invalidate only the remote stream
resolution and use a one-shot or bounded reconnect budget; download bytes and local media are not
deleted. A second failed attempt or an explicitly permanent playability/decoder error publishes
the structured failure to the player UI, which offers Retry, Details and Copy debug report.

Logs use the `PlaybackTrace` tag and `[PB-XXXXXXXX] EVENT key=value` format. Signed URLs, cookies,
authorization headers, PoTokens, visitor data and integration secrets are redacted at the
diagnostic boundary.

Provider changes should stay isolated in the relevant client, parser or runtime boundary. UI code
should consume stable models rather than parse provider response text directly.

## Variants

The `variant` dimension provides `foss` and `gms`; the `abi` dimension provides universal and
architecture-specific outputs. FOSS is the reference contributor build and must not require
Firebase files or private credentials. GMS-only code belongs behind the GMS source set or a clear
feature boundary.

## Data boundaries

- Account credentials and user-provided integration keys are configured at runtime, not committed.
- Provider requests and parsers live outside the UI layer.
- The current Better Lyrics boundary is Kotlin source under `betterlyrics/src/main/kotlin/`; there
  is no tracked web source, npm lockfile or generated renderer tree in this checkout.
- Official release signing is external to public CI and uses the maintainer-only keystore.

## Change guide

- Playback or provider behavior: start with `innertube/` and the relevant `app` runtime boundary.
- Lyrics source or rendering: inspect the provider module and `betterlyrics/` together.
- Wear behavior: change the phone publisher and `wear/` consumer as a protocol pair, then update
  [WEAR_OS.md](WEAR_OS.md).
- A new external service: document endpoint, authentication, payload, failure behavior and
  provenance before merging.
- UI-only changes: preserve the application ID, preferences, deep links and variant behavior.
