# libs/

RTPBuddy's jar goes here, and nothing else.

It is not published to any maven repository, so the build takes it from this
folder by file name: `rtpbuddy-<rtpbuddy_version>.jar`, with the version coming
from `gradle.properties`.

Put it here with:

```bash
python <RTPBuddy>/integration/install-rtpbuddy-jar.py .
```

The jar is **compile-time only** (`modCompileOnly`) plus a dev-client runtime
(`modLocalRuntime`). It is never packed into this mod's own jar — at runtime the
`dev.rtpbuddy.api` classes come from the RTPBuddy mod the player installed.

The jar is not committed; `.gitignore` keeps `libs/*.jar` out.
