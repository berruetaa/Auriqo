# Last.fm credential boundary

Auriqo integrates with Last.fm as a standalone Android client. Last.fm's authentication protocol
requires an API key plus a shared secret to construct `api_sig` values for authenticated calls.
That requirement applies to the documented mobile flow and also to the browser-assisted desktop
flow used by public native clients.

Official protocol references:

- https://www.last.fm/api/authentication
- https://www.last.fm/api/mobileauth
- https://www.last.fm/api/desktopauth

## Threat model

A value bundled into an Android APK is recoverable. `LASTFM_SECRET` must therefore **not** be
considered confidential merely because CI injects it from a GitHub Secret or Gradle property. The
build-time secret store protects the value before packaging; it cannot make the packaged value
private.

For the current direct-to-Last.fm architecture, treat the Last.fm API key/shared-secret pair as a
revocable public-client credential with limited purpose:

- never reuse the same secret for another service or privileged server API;
- do not log the key, shared secret, generated signature, user password, or Last.fm session key;
- keep user session keys separate from the application credential and clear them on logout;
- rotate the Last.fm API account credential if abuse or unintended disclosure is suspected;
- review Last.fm account restrictions and protocol changes before release.

## Why it remains in BuildConfig

Removing the shared secret from `BuildConfig` without changing the architecture would only move an
extractable value somewhere else in the APK. The client needs the value locally to generate
Last.fm signatures.

There are only two ways to make the maintainer-owned shared secret genuinely unavailable to the
APK:

1. **Server-side signing/proxy** — Auriqo sends the minimum required request data to an Auriqo
   backend, which owns the Last.fm secret and performs or signs Last.fm calls. This creates a new
   service, privacy boundary, availability dependency, abuse surface, and operating cost.
2. **User-owned Last.fm API credentials** — each user supplies their own API key/shared secret.
   This removes the maintainer credential from distributed builds but has poor onboarding UX and
   shifts credential management to every user.

Until one of those architectures is intentionally adopted, the direct client implementation should
keep its current protocol compatibility and describe the residual risk accurately.

## Distinction from ordinary client secrets

This exception is narrow. It does **not** justify compiling OAuth client secrets, backend API keys,
service-account credentials, signing keys, or arbitrary provider tokens into Auriqo. Those values
must use a public-client flow, user-provided credential, or backend boundary instead.
