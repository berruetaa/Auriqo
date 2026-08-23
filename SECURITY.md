# Security policy

## Reporting a vulnerability

Please do not open a public issue for a security vulnerability. Use GitHub's private vulnerability reporting for the [Auriqo repository](https://github.com/Auriqo/Auriqo/security/advisories/new). If private reporting is unavailable, contact the maintainers through the repository's security contact and include only the minimum reproducible detail.

Please include:

- the affected commit, tag or build variant;
- the component and Android version, if relevant;
- reproduction steps that do not expose real accounts or credentials;
- impact and any suggested mitigation.

Redact cookies, OAuth tokens, API keys, webhook URLs, device identifiers, private logs and personal data. If a secret was committed, say which file and commit contain it without pasting the value; rotate it through the service owner.

## Scope

The primary scope is the Android application and the `workers/youtube-attribution` Worker in this repository. Third-party services, upstream provider infrastructure, package registries, Android itself and the user's device are outside Auriqo's direct control; report their vulnerabilities to the relevant owner as well.

Security fixes are evaluated against the latest `main` and the latest published pre-release. There is no guaranteed response or remediation SLA. Release decisions remain maintainer-controlled.

## Security-sensitive design notes

- The app handles user-supplied YouTube cookies, OAuth access/refresh tokens, AI keys, scrobbling tokens and proxy credentials. The current implementation stores these values in app-private DataStore/preferences and does not provide encrypted-at-rest storage for every credential.
- Android system backup excludes the settings DataStore from cloud backup and device transfer. The explicit in-app Backup action still exports settings and the local database when the user chooses it, so treat that archive as sensitive.
- The network security configuration requires TLS for remote traffic. Cleartext WebSocket connections are accepted only for localhost and common Android emulator loopback addresses; use WSS for remote Listen Together servers.
- The optional attribution Worker forwards playlist requests to YouTube/Google. Its authentication and CORS settings are deployment-sensitive; see [docs/WORKERS.md](docs/WORKERS.md).
- Debug logging must not include cookies, bearer tokens, PoTokens, Botguard responses, full provider responses or user identifiers. Release builds are not a substitute for safe debug logging.
- `app/persistent-debug.keystore` is deterministic debug-only signing material used for local upgrades. It is not a release credential and must never sign an official artifact.
- The Better Lyrics renderer is local-only and origin-scoped; provider/theme network access stays in Kotlin. Remote JavaScript is not an accepted theme format.
- The Unison private identity is encrypted with Android Keystore. A user-requested identity export contains the private JWK in plain JSON and must be handled as a credential.
- Last.fm's direct native-client protocol requires a shared secret to generate request signatures. A packaged Android client cannot keep that value confidential; treat it as a revocable public-client credential and see [docs/LASTFM_SECURITY.md](docs/LASTFM_SECURITY.md) for the exact boundary and alternatives.

These notes describe residual risks in the current tree; they are not claims that the app is secure against all threats.

## Secret handling

The following must remain local and untracked:

- `local.properties`, `app/google-services.json` and environment files;
- release keystores, PEM/certificate material and signing properties;
- API keys, OAuth client secrets, cookies, bearer tokens and CI webhook secrets;
- device logs, crash dumps and generated APKs.

The FOSS build is intended to compile without private credentials. A public APK cannot keep a client secret: any value compiled into `BuildConfig` can be extracted. The CI/release implications are documented in [docs/CI_RELEASE_REVIEW.md](docs/CI_RELEASE_REVIEW.md).

The Last.fm shared secret is a documented protocol exception to the general rule above, not a secure
storage mechanism. The direct Android client currently needs it locally to produce Last.fm
`api_sig` values. Do not reuse it as a privileged backend secret; see
[docs/LASTFM_SECURITY.md](docs/LASTFM_SECURITY.md).

Two Google/YouTube client API identifiers are intentionally present in the InnerTube/PoToken
protocol source and are recoverable from every APK. Treat them as public client identifiers, not as
storage for a private maintainer credential. Review their necessity and provider-side restrictions
at every release; never reuse them for a privileged server API.

## Disclosure process

After triage, maintainers may request a coordinated disclosure window, prepare a fix, credit the reporter when requested, and publish a concise advisory. Do not publish exploit details before maintainers confirm that affected releases and users have a mitigation.
