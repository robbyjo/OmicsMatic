# Preserve the qualified checkout bytes

The shared stylesheet was originally qualified with CRLF line endings; the
repository license was qualified with LF. Neither file's recorded bytes or saved
hashes are changed by this work. `site/assets/.gitattributes` preserves the existing
stylesheet as exact bytes, without Git newline conversion. Its existing CRLF bytes
are now retained in Git exports as well as working checkouts. No stylesheet content
or recorded hash changes.

Use a NEW checkout with conversion otherwise disabled, then preserve the declared
file attributes:

```sh
git -c core.autocrlf=false -c core.eol=lf worktree add --detach NEW_CHECKOUT COMMIT
```

The isolated validation checkout receives the unchanged qualified CLI prerequisite
at its own `build/cli/omicsmatic-0.3.7.jar`. The active shared workspace's different
development JAR stays untouched. Source/hash mismatches remain failures; no old
scientific verifier, result, input, attribute history or saved hash is refreshed.
Failed initial checkout and browser attempts remain retained.
