# Separate review of the merged application source

**This transition adds zero tool integrations.** It preserves the predecessor's
250-paper catalog and bounded original qualifications across explicitly reviewed
application changes. It does not reproduce papers, validate biology, qualify
whole software packages or finish the 250-paper implementation objective.

The [original merged-main warning](MAIN_MERGE_20261115_STATUS.md) remains valid:
the unchanged SingleR predecessor guard refuses the merged application tree.
The old command is not made to pass there. Its baseline, scientific functions,
verifiers, saved hashes and original evidence remain unchanged.

The separate route checks the reviewed source transition from historical commit
`357c9301be726b3b6dc2a4e92cf71ff05ed053d2` to application commit
`fc6e0afcabb6144df9a3c161e3007e4d36423f2d`. It runs the original catalog verifier
in a clean checkout of the historical commit, then checks the reviewed merged
source, all eight unchanged catalog files and all 485 complete registry
definitions. Neither a scientific-source overlay nor a monkeypatched Git result
is used.

## What changes and what the evidence establishes

The complete transition contains 174 changed/added files, including 27 Java
source files. Exact before/after Git identities and working-copy SHA-256 values
are retained. Ten current files have explicitly recorded CRLF checkout forms.
Only each recorded working-copy identity or its exact Git-blob identity is
accepted, with the expected filtered Git object also required. Files are never
rewritten; no general newline normalization of scientific inputs/results is
allowed. A JDK21 syntax inventory
records complete canonical units and changed members. Parsing is not a semantic
equivalence proof; see the official
[JavacTask reference](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html).

The reviewed changes include:

* Cleanup-aware cache/verification/index/archive behavior. Removed or pending
  outputs are not reused; exact recorded removal exemptions and their retained
  replacement history must verify. No user research result is cleaned by this
  review. Cleanup tests operate on synthetic projects.
* Manuscript policy, abstract, participant-table, citation, word-count and math
  presentation changes. Reporting behavior deliberately changes; a readable
  manuscript is not a scientific-validation certificate. Native statistical
  kernels and previously qualified tool implementations are not replaced.
* Additional iDEP/HOPS development material and a historical velociraptor
  dossier. They remain separately scoped and receive no new catalog credit.
  The current iDEP profiles do not identify P050's original iDEP.96 deployment.
* Application documentation and historical/example manuscript helpers. The
  unfinished literature-review/warning website pages remain excluded from Pages
  deployment.

The unchanged historical verifier completed its primary-source rescan. This
rescans the saved primary-paper corpus; it is not a new online search or fresh
upstream scientific computation. All eight catalog files and all 485 registry
definitions match the merged application. Counts remain 446 families, 2,690
observations, 1,197 reviewed uses, 1,493 unreviewed uses, 122 new scoped
integrations (21 native, 101 upstream), 125 including existing adapters, and 265
missing families.

The current focused run passed **276 tests in 13 suites with zero failures,
errors or skips**, plus library/executable JAR builds and `checkWebsite`, under
Windows JDK21. Compilation tasks used Gradle's content-addressed build cache;
the test task executed. Its outputs use a separate directory. The earlier
1,159-test application run remains retained, and its Java/test/resource/build
sources match the reviewed application. Class dependencies were recorded with
[jdeps](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdeps.html);
that is not complete method-call or reflection analysis.

The dependency check identifies the unchanged `StudyAssistance.json(Object)`
method as a consumer dependency of the non-study `VariantToolsCli` metadata
command. Its class imports, fields and initialization remain unchanged. A
separate actual **13-test consumer suite** passed with no failures/errors/skips.
The first dependency attempt used an obsolete artifact filename and returned
zero with a missing-path warning; it is retained as invalid evidence. The
corrected check pins the actual executable JAR and records 253 incoming class
edges to reviewed classes. Fifteen source, metadata, verifier, real
metadata-view and explicit-checkout controls exercise the new guard boundaries.

## Reproduce the separate check

Keep the exact baseline in its own clean worktree. From this repository:

~~~powershell
git worktree add --detach build/catalog-baseline-357c 357c9301be726b3b6dc2a4e92cf71ff05ed053d2
New-Item -ItemType Directory build/catalog-baseline-357c/build
New-Item -ItemType Junction `
  -Path build/catalog-baseline-357c/build/literature-review `
  -Target "D:/projects/JLinAlg/build/literature-review"
~~~

The target must be your actual permitted saved primary-paper cache. No original
paper text, installed upstream software or private scientific RDS is included
in this transition's public evidence.

Then use NEW paths:

~~~powershell
python -B tools/literature/catalog_source_transition/bridge.py --verify `
  --baseline-checkout "D:/projects/JLinAlg/build/catalog-baseline-357c" `
  --rescan-sources `
  --out build/NEW-application-source-transition-check
~~~

The baseline verifier writes a NEW ignored output under that baseline checkout's
`build`; its sources must stay clean. Complete stdout/stderr, its original
completion record and this transition's result remain retained. The new
`COMPLETED.json` identifies the separately reviewed route; it never asserts
that the old command passed on the merged source tree. This check repeats
historical evidence/source checks, not all original-tool analyses.

**SKIP3:** skip qualification when the exact permitted clean historical checkout,
Git history, cached primary papers or another named prerequisite is unavailable.
Supply that prerequisite and retry NEW paths. Preserve a dirty checkout and
provide a separate clean one rather than resetting or deleting its files.

**FAIL2:** no complete transition is admitted for changed/unreviewed sources,
metadata, registry definitions, evidence or recording failures. Read
`FAILURE.json`, `WARNINGS.md` and raw consoles, retain the originals, resolve the
named cause and repeat at NEW paths. Do not replace old hashes or edit a
historical source guard to hide the difference. Further source changes require
their own appropriate qualification/review.

The [transition evidence](application-source-transition-20261115/README.md)
retains the review dispositions, exact syntax delta, validation identities,
unchanged registry and the original refusal. Its hashes establish stored
content integrity, not a signature or upstream binary authenticity. Existing
scientific qualifications keep their original release, caller, input and
failure boundaries. The remaining paper-use review and missing tools remain
work to complete.
