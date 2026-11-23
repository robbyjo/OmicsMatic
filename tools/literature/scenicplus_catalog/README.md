# SCENIC+ source-role catalog transaction

This integrates one bounded upstream family and six individually reviewed source
roles. It adds the missed P153 benchmark observation without changing unrelated
uses or the 1,701 unrelated prior reviews. P061 is expanded without new review
credit; five new reviews are added. Prepared workflows and skipped scanning
analyses remain distinct. Exact paper editions, seeds and unavailable references
are not inferred; source review does not reproduce author outcomes.

`close_scenicplus_catalog.py` supports `--plan`, `--inspect`, `--apply` and
`--verify`. Every command requires a NEW `--out` under the current checkout's
`build/` plus explicit `--cache`, `--jar`, `--private-root`, `--predecessor-root`,
`--predecessor-jar` and `--author-root` paths. Inspect/apply also require the saved
`--plan-dir`. The [guide](../../../docs/literature/SCENICPLUS_CATALOG_WORKFLOWS.md)
gives concrete paths, warnings and required actions.

The unchanged RNA-only SCENIC catalog reader executes in its physical archived
pre-extension source context, with its exact producer and all 250 primary XML.
The current global SCENIC+ reader separately proves the precise dispatcher
extension, all current source pins and saved private evidence. Historical hashes
and verifier sources are never refreshed to bypass a source mismatch.

The transaction verifies a deterministic preview, exact predecessor Git bytes,
current source/producer identities, all 250 primary XML and 42 author Git blobs.
It writes each owned destination atomically, checks preconditions immediately
before replacement, rolls back its own changes on failure, retains failed new
metadata, and preserves concurrent writers. The before archive retains all eight
original catalog files. The full 250-paper objective remains incomplete.
