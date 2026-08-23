# InnerTube contract fixtures

These files are small, offline inputs for parsers and response mappers. They are not live-network
tests. The existing `player/` snippets were added from the parser regressions in commits
`1bd1ea90`, `75ea2eb6`, `058ae6dc`, and `3c8dfc91`; the `cipher/` and `next/` files are reduced,
sanitized examples of the public response formats used by those code paths.

Sanitization rules:

- keep only the fields required to reproduce the parser contract;
- use `.invalid` hosts, synthetic IDs, and placeholder signatures;
- never include cookies, visitor data, tokens, PoTokens, account IDs, or personal data.

When adding a regression, add a new fixture named after the format or failure, add a deterministic
test that loads it from the classpath, and record the source and reduction in the test or commit
message. Do not replace an older fixture when a format changes: keep the old case and add the new
one so historical breakages remain covered.

The intended areas are `player/`, `cipher/`, `browse/`, `next/`, `search/`, and `transcript/`.
Only areas with a current offline contract test need files; empty directories are intentionally not
tracked.
