# MEME discovery execution and portable verification: development

**The scoped development workflow is validated.** Read the complete source and
control record in `meme-discovery-workflow-development/VALIDATION.json` and its
offline verifier `tools/literature/meme_discovery_workflow_evidence.py`.
The new classes are packaged in a separate development JAR. They do not change the
canonical/released executable. The current input/reference contract matrix passes all 91
tests on Windows Java 21, Windows Java 25 and Linux Java 21; that matrix does not
execute a motif fit.

The first complete 5.5.9 workflow has passed actual original fitting, output
auditing and Linux/Windows portable verification on the weighted synthetic
control. It reports two motifs, 53 contributing sites, 60 sequence diagrams and
56 selected hits. All four profiles have passed original fitting and portable
VERIFY on Linux Java 21 and Windows Java 21/25.
Forty Windows recovery controls pass with explicit skips/refusals, including
36 copied-bundle corruptions whose outer and native-capture hashes were updated
to exercise the scientific/receipt checks.

**Zero reported motifs:** four genuine original empty reports at an explicit
E-value cutoff are captured unchanged. The additional empty-result validator
retains the frozen component's input/settings/training/background checks, then
checks the original stopping reason, absence of motifs/diagrams, and complete
text/HTML carriers. Forty altered empty-report controls are refused across the
four profiles. All four full public zero-motif workflows have passed original
execution and portable verification on Linux Java 21 and Windows Java 21/25.
The documented final root example has also passed an original positive fit
and portable verification on all three JVMs. There are five positive user
analyses (four earlier profile controls plus the final root example), four
complete zero-motif workflows and 27 workflow portable verifications. Every
full analysis also executes its own fresh positive scientific/report health
control. These counts exclude the separately scoped root/recovery controls.
An accepted empty report does not establish absence of binding sites or
biological signal. Preserve the stated stopping reason and cutoff.

This adapter runs the original source-built MEME program. Native Java separately
checks raw FASTA/weights/background preparation, complete learned XML sites,
counts, prior-based scores, position probabilities and sequence diagrams. It also
checks all text PWMs and centibit matrices, decodes PNG carriers and binds the
complete HTML report to audited XML by byte-identical replay through the same
original renderer. Original EM fitting, motif significance and figure pixels
remain upstream computations. Renderer replay establishes source consistency;
it does not independently certify every rendered field, browser behavior or
biological validity.

Supported exact profiles are `MEME_4_9_1_BASE`, `MEME_4_9_1_PATCH2`, `MEME_5_5_4`
and `MEME_5_5_9`. Base and patch 2 are distinct original source populations.
Reported paper versions do not identify an unreported patch level. MEME-ChIP,
DREME, STREME, FIMO, Tomtom, HyPhy MEME and BWA-MEME have separate roles and
admission boundaries.

Execution requires Linux x86_64 Java and a canonical ASCII private source-built
MEME home with its completed build evidence. Original home relocation is not
assumed safe. Full reports require the exact declared Perl/XML module,
Ghostscript, font/configuration and ELF dependencies. There is no silent text
fallback. Analysis does not download, install, edit the installation or modify
host configuration. The user confirmed educational, research or nonprofit use
for this project; MEME's original license remains applicable.

Use the project command from the separate root development JAR. The direct
`org.jlinalg.cli.MemeToolsCli` entry point remains available for the backend JAR.
The root build changes only `JLinAlgCli.class`; every backend/resource entry
matches its selected parent. Sixteen Windows root checks pass, including real
portable verification, runtime skips and unchanged existing-output refusals.

```sh
meme_jar="/absolute/path/to/meme-discovery-root-development.jar"
java -jar "$meme_jar" meme-tools --profiles
java -jar "$meme_jar" meme-tools --template MEME_5_5_9 > recipe.json
```

Edit both absolute input paths in `recipe.json`. FASTA records must have unique
names, canonical uppercase A/C/G/T, at least two records and no record shorter
than the requested maximum width. Header descriptions and all original bytes
are retained. One optional initial `>WEIGHTS` header declares exactly one
positive weight at most one per record; it is control metadata, not a sequence.
There is no silent filtering, renaming, ambiguity replacement, decompression,
clipping or normalization.

For weighted inputs, the original text score-matrix `n` uses
`max(sum(weight * sequence_length) - number_of_records * (width - 1), 2)`,
truncated to an integer. It does not use `sum(weight * possible_windows)`.
The Java check preserves the original convention and reports the distinction.
All 32 motifs from 16 retained edge/weight/background families match the exact
official source formula and text matrices; eight weighted headers differ from
the ordinary weighted-window interpretation.

The background file contains exactly A, C, G and T with positive probabilities
summing to one. Comments and ordinary whitespace are allowed. Both-strand fits
use the original complement-averaged background. Empirical and higher-order
backgrounds need their own audited input contract and are skipped here.

The recipe declares OOPS, ZOOPS or ANR; strand choice; motif count and width
bounds; positive Dirichlet beta; iteration and convergence limits; and optional
E-value stopping and site limits. OOPS accepts no ignored min/max-sites options.
MEME 5.5.4/5.5.9 override `-nmotifs` to 1,000 whenever `-evt` is specified.
For those profiles, a non-null `maximum_motif_evalue` therefore requires explicit
`motifs: 1000` and sufficient motif capacity. This adapter rejects a conflicting
request instead of silently changing it. The 4.9.1 profiles retain their supplied
motif count with the cutoff. The validated empty path requires the source
E-value stopping reason; other empty-result reasons remain unadmitted.
For 4.9.1, seed and search size must be null because its CLI has neither option.
Newer editions require both explicitly. Source input sampling must still match
the independently audited complete input population; no sampled population is
silently substituted for the declared records.

Create a separate operation JSON file, using your actual absolute paths:

```json
{
  "schema_version": 1,
  "operation": "DISCOVER",
  "recipe": "/data/recipe.json",
  "runtime_home": "/var/tmp/my-source-build/home-5.5.9",
  "build_evidence": "/data/source-build-evidence",
  "out": "/data/new-meme-result"
}
```

```sh
java -Xmx768m -jar "$meme_jar" meme-tools --request discover.json
```

The output parent must exist and the output path must be new and outside the
managed installation and source-build evidence trees. Declare canonical,
unlinked input file paths. Original input
paths may contain Unicode and spaces; the adapter copies their unchanged bytes
to private ASCII command carriers. It records original and staged identities.
Each run admits current dependencies and executes a fresh two-motif scientific
and report health control for the exact edition before fitting user inputs.
Health compares every retained learned motif/site/matrix/diagram field to the
pinned exact-edition reference, with elapsed timing excluded.

The complete native worker is retained for recovery and copied into the result
bundle. `native/prepared` holds the raw recipe and inputs; `native/runtime`
contains build/host/probe/health evidence; `native/fit` contains the original
fit logs and complete result family; `native/render` contains independent
original-helper replay logs and HTML. `OUTPUT-AUDIT.json` describes the native
scientific audit. Read `WARNINGS.md` and both original stderr files even after
exit 0. The bundle contains the complete declared input data and should be
handled with the same access controls as those inputs.

To verify a copied result, create another operation JSON file:

```json
{"schema_version":1,"operation":"VERIFY","bundle":"/data/completed-meme-result"}
```

```sh
java -Xmx768m -jar "$meme_jar" meme-tools --request verify.json
```

On Windows use a Windows absolute bundle path, escaping JSON backslashes or
using forward slashes. Portable VERIFY reads captured files only. It checks
the overall completion/manifest, complete namespace and bytes, typed command
and process receipts, raw input preparation, native scientific output, text
matrices, report replay parity and the captured exact-edition health control.
Producer source/build/host receipts remain provenance and integrity records;
VERIFY does not reopen producer paths, authenticate execution or grant a
current-host runtime ticket. Filesystem modes are retained as producer metadata
and do not substitute for permissions on the receiving machine.

**SKIP3 means skip the dependent analysis.** When a required runtime, dependency
or capability is missing or forbidden, provide it only on a permitted host.
For the current 5.5.9 source profile, the explicit setup helper is
`tools/literature/build_meme_linux_reference.py`; it requires Linux x86_64,
the pinned official archive, permitted build prerequisites, `--allow-build` and
a NEW output under `build/literature-review/meme-runtime`. Historical profiles
use `build_meme_historical_reference.py` with their exact source archive and
edition. These helpers are research reference setup; a build receipt alone
does not admit analysis. Read the original license and preserve all setup logs.
If installation is prohibited, retain the skip and complete other permitted
work. Cell Ranger remains excluded at the user's request.

For example, from the project root with permitted Linux Python and the pinned
official 5.5.9 source archive already obtained:

```sh
python3 tools/literature/build_meme_linux_reference.py \
  --archive /data/meme-5.5.9.tar.gz \
  --out "$PWD/build/literature-review/meme-runtime/my-new-559-build" \
  --allow-build
```

Inspect the setup's completion/failure record and original logs. Use the
resulting exact home prefix and build-evidence directory in the operation JSON;
do not copy an example prefix from another machine. Historical setup helpers
have additional edition/compiler inputs; run their `--help` and use the exact
declared profile. Missing build/report prerequisites still skip dependent work
until they can be provided on a permitted host.

**FAIL2 means no result is admitted.** Read `FAILURE.json` for the failing stage
and retained native worker path. Preserve all partial files and original logs,
correct the reported input/integrity/process/capacity/capture/finalization cause,
and retry to a NEW output. Require overall `COMPLETED.json`, no `INCOMPLETE.json`
or `FAILURE.json`, and a successful VERIFY before downstream use. Component
completions and older successful attempts do not clear a failed overall run.
Requested process limits do not guarantee enough heap for Java verification;
use sufficient permitted Java memory for the declared complete population.
Periodically sampled aggregate RSS is not a hard aggregate memory bound.

User paths and original diagnostic text are preserved verbatim. Shared process
helpers may retain RSEM or Tomtom component names in their details; the MEME
error prefix and failing stage identify this workflow. Such names are not
replaced inside filenames or identifiers.

On the current Windows/WSL validation host, starting another `wsl.exe` command
rewrites `/etc/resolv.conf` symlink timestamps while retaining its bytes. This
correctly invalidates an overlapping strict host census. The workflow test
controller therefore launches one Linux parent for its batch and does not
start additional WSL commands during admission. A genuine host change still
fails; the census and DNS configuration are not weakened or rewritten.
