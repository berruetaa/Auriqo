# Troubleshooting

Use this page to identify the failing boundary before opening an issue. Include the app version,
variant, Android version, device, exact steps and a short redacted log excerpt in the report.

## The app will not install

- Confirm that the APK matches the device architecture and package variant.
- Remove an old debug build if installing a stable build for the first time; debug and production
  certificates are different.
- Check that Android has permission to install from the file manager or browser you used.
- Download the APK again and compare its SHA-256 with the release notes.

## Search or playback fails

- Try a public song to separate account access from general playback.
- Check the network and retry after a short delay; YouTube may rate-limit or rotate player scripts.
- Try the FOSS build if the failure is in Cast, Firebase or another GMS-only integration.
- Record whether metadata loads but playback fails, or whether the page itself fails to parse.
- On the playback error panel, use **Copy debug report**. Include the Trace ID and the full
  redacted report; it contains the Auriqo code, original Media3 code, HTTP/playability evidence,
  timing, cause chain, recovery attempts and recent trace breadcrumbs.
- A recoverable 403/410/429, expired stream, extractor/cipher failure or transient network loss
  is retried automatically. The panel appears only for an explicit permanent failure or after the
  bounded recovery budget is exhausted.

## Lyrics are missing or out of sync

- Try another lyrics source from the lyrics screen.
- Adjust the offset for a source with a constant timing difference.
- Check [lyrics provider behavior](LYRICS_PROVIDERS.md) before treating an outage as an app bug.
- Renderer or marketplace problems should include the renderer version and affected theme.

## Wear controls do not update

- Use the GMS phone variant for custom Auriqo synchronization.
- Confirm that the companion is installed, paired and allowed to run in the background.
- Start playback on the phone, then wait for the first state heartbeat before testing browse actions.
- Standard Android media controls may still work when the custom data channel is unavailable.

## An integration does not work

- Verify that the feature is enabled and its account or endpoint configuration is complete.
- Test playback without the integration to isolate the failure.
- For Listen Together, use WSS remotely; plain `ws://` is limited to local development hosts.
- For AI translation, check the selected endpoint, model and quota without posting the API key.

## Build failures

- Verify JDK 21, Android SDK Platform 36 and NDK `27.0.12077973`.
- Confirm `local.properties` points to the intended SDK and is not committed.
- Start with `:app:assembleUniversalFossDebug`; it does not require Firebase or release signing.
- Keep Gradle caches and report the first meaningful error rather than only the final task failure.

## What to include in a report

Include the stable/debug version, FOSS/GMS variant, device model, Android version, reproduction
steps, expected result, actual result and the copied Auriqo Playback Diagnostic. Never include
cookies, OAuth tokens, API keys, signing details or private playlist URLs. Security-sensitive reports belong in
[SECURITY.md](../SECURITY.md), not a public issue.
