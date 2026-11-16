# Review of the release and development source transition

**This source transition adds zero tool integrations.** It preserves the
predecessor's 250-paper catalog across the reviewed release/development changes.
Full semantic review, missing tools and the complete 250-paper implementation
objective remain open. It does not qualify whole tools, biology or papers.

The [previous transition](APPLICATION_SOURCE_TRANSITION_20261115.md) remains
unchanged. Its original guard refuses later source outside its declared scope.
This new route runs that original verifier in the exact clean
`1a9ff82adbf53e7b36fad065a9b45b1ab1c018f6` checkout, using its exact clean
`357c9301be726b3b6dc2a4e92cf71ff05ed053d2` baseline. No old scientific function,
source path, verifier, saved hash or Git result is replaced or monkeypatched.
The original primary-paper rescan runs against the retained primary-source
cache; it is not a new online search or fresh upstream scientific computation.

The reviewed target is `a97510234fa2fb992341d5d6ad9bf2741204a639`.
The [exact source inventory](application-source-transition-20261116/CHANGE-INVENTORY.json)
classifies all 272 changed/added files. The complete target tree records all
13,297 tracked files with Git objects and actual-byte identities. Exactly one
working file has a separately recorded checkout conversion; only that captured
identity and the exact Git blob are accepted. Files are never rewritten to make
a check pass. Scientific inputs and outputs are not normalized or repaired.

There are 16 added production Java files and one modified production file.
The complete JDK21 syntax inventory shows only two changed existing members:
`JLinAlgCli.run(String[],PrintStream,PrintStream)` and `help()`. The source patch
adds exactly three lines and removes no old line. These route the explicit
`sambamba-tools` command to the unchanged streaming profile before automatic
project configuration and advertise it in help. The integration's executable
JAR retains identical bytes for 19,688 other class entries relative to the
preceding development candidate. Syntax and byte comparisons have the stated
scope; they are not general semantic-equivalence or biological-validity proofs.

The remaining production additions are separately documented native geNorm,
original Sambamba wrappers/raw BAM auditors and native BAM/BAI auditing.
Their frozen source comparisons, runtime controls, discrepancies and failed
attempts remain intact. Native geNorm quantity/log2 controls do not identify
P040's Cq workflow or earn paper-specific credit. Sambamba profiles preserve
their explicit settings, genuine original-runtime replay, platform/resource
bounds and collateral-index warnings. The per-file BAI checks do not promote
old workflow receipts or qualify arbitrary downstream consumers.

The build/workflow changes are version and release packaging/report handling,
test heap/fork limits, explicit optional Nextflow runtime settings and javadoc
source-path selection. Production dependency versions and pre-existing
numerical kernels are unchanged. Three existing test files change deterministic
artifact selection and explicit optional-runtime availability. Static website,
documentation and frozen release/development records are reviewed separately;
unfinished literature-review/warning pages remain excluded from Pages.

## Checks and boundaries

The Windows focused run completed 207 tests: 203 passed and four Linux
filesystem contracts were skipped. All six tests in that filesystem suite then
passed on the real permitted Linux filesystem, including all four skipped
Windows cases. Library/executable JAR builds and the website check passed.

The normal `sambamba-tools` command completed fresh original mark/remove
analysis and genuine replay with both selected editions in a Linux JVM. Eight
successful original analysis phases and a separate failed 0.6.6 score-overflow
phase were observed. A deliberately invalid project configuration is rejected
by the ordinary application path and bypassed by this explicit-request profile,
as intended. The native Windows attempt returns `SKIP3`; the score-overflow
attempt returns `FAIL2`; neither has an accepted completion receipt.

Real isolated source controls test complete membership, actual-byte hashing
despite same-size/time and Git's assume-unchanged flag, undeclared additions,
exactly declared new consumer files, source changes and forbidden exclusions.
Metadata controls require all eight exact predecessor files and reject changed
or incomplete views. The unchanged old command still refuses the current tree.
The first isolated checkout failed on Windows path length before controls ran;
its raw logs are retained. The successful attempt uses a new short checkout
path and a command-local `core.longpaths=true`, preserving user Git settings.
An initial preparation attempt also found an absent primary-cache link in the
current checkout. Its failure is retained; the permitted existing cache is
then linked explicitly, without changing original paper or source bytes.

The new guard hashes actual source bytes. Git's stat cache is not treated as
content proof. A future separately qualified catalog consumer can declare
exact new file names and identities; it cannot exclude an existing source file
or a path alias. Such a consumer may supply only the exact eight predecessor
metadata byte strings while separately checking its own current catalog
transition. This is an explicitly labelled metadata-only historical view,
without changing source lookup or Git. The standalone command always checks
the actual current predecessor catalog state.

## Reproduce the check

Keep both exact historical commits in separate clean worktrees with the
permitted saved primary-source cache available to the baseline. The earlier
transition guide explains cache setup. The current checkout also needs that
cache because constructing predecessor registry rows rereads primary papers.
If `build/literature-review` does not exist, the Windows setup used here is:

```powershell
New-Item -ItemType Junction -Path build/literature-review -Target D:/projects/JLinAlg/build/literature-review
```

Use your own permitted frozen cache elsewhere; preserve any existing destination.
Short checkout paths are preferable on
Windows. Do not reset or overwrite a worktree containing unrelated changes.

```text
python -B tools/literature/catalog_source_transition_v2/bridge.py --verify --parent-checkout D:/projects/JLinAlg/build/git-publish/20261115-all-main-1/checkout --baseline-checkout D:/projects/JLinAlg/build/catalog-source-transition/20261115-review-1/baseline-checkout --rescan-sources --out build/NEW-release-source-transition
```

Those two historical checkout paths are the ones used for this project; supply
your own exact clean equivalents elsewhere. Use a new output directory for
each attempt. The old parent command writes its own new ignored build output
in the historical checkout; its sources remain clean.

Read saved public metadata without rerunning the gate or scientific analysis:

```text
python -B tools/literature/catalog_source_transition_v2/records.py
```

The [qualification record](application-source-transition-20261116/QUALIFICATION.json)
and its source/metadata archives identify the complete review. Use the reader's
`--help` for complete named private-record readback or an explicit current
source-tree check. Large executable JARs, the control checkout and the primary
paper cache are not public metadata; Git target identities and named source
snapshots preserve their declared source scope. SHA-256 gives integrity, not
authorship or upstream executable authenticity.

**SKIP3:** supply the named permitted Git/JDK/runtime/cache/clean checkout and
retry a new output, or skip dependent analysis if it cannot be supplied.
**FAIL2:** no complete transition is admitted. Retain the failure and raw
consoles, correct the named contradiction and retry a new output. A late
completion marker is invalidated if recording fails. Do not refresh historical
hashes or alter scientific values to bypass refusal. The
[Sambamba guide](SAMBAMBA_TOOLS.md) describes the retained 0.6.6 metadata and
quality-score failures and exact recovery actions.
