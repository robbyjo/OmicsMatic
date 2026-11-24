# Prepared FastQTL runtime checkpoint

**Catalog admission is pending.** This checkpoint supplies zero family/review
credit. The canonical cohort remains 250 papers, 158 scoped families and
1,755 reviewed / 969 pending uses. FastQTL 2.165 remains unqualified.

The source-built Java interface executes Ubuntu 2.184/GTEx nominal and
fixed/adaptive permutation operations on prepared single-chromosome biallelic
SNV data. [Installation, warnings, examples and restrictions](../../FASTQTL_WORKFLOW.md)
and the [five source roles](../FASTQTL_SOURCE_ROLES_2026-11-24.md) remain part of
its scope. The existing release JAR and hosted literature reports are unchanged.

The saved qualification records eight byte-exact direct-original comparisons,
two fresh upstream VERIFY/REPLAY computations, fifteen actual refusal controls,
240 independent R statistics and 30 selected Java tests (8 FastQTL, 9 IMPUTE,
13 process-ownership tests). Windows tests the explicit ELF-analysis skip;
actual FastQTL fits execute under Linux. Both hosts check the saved evidence
reader; Windows also invokes the unchanged historical cytometry reader from its
preserved physical source context. These are finite synthetic checks, not
whole-paper reproduction or biological calibration.

`evidence.zip` stores deduplicated objects; `FILES.json` maps their logical names.
`STATUS.json` binds exact source and producer identities. `PRIVATE-FILES.json`
protects retained originals, all three development producers, source overlays,
runtimes, failed attempts and historical contexts; it does not grant permission
to redistribute them. `OMISSIONS.json` identifies deliberately excluded runtime
binaries, primary text and reproducible cleanup candidates. Later reader,
publication and cleanup receipts are separate from that freeze-time inventory.
The failed first reader draft is retained; its relative-path fix changes no
scientific producer or output.

Check saved public evidence from the exact source revision:

```sh
python3 tools/literature/verify_fastqtl_workflow_checkpoint.py
```

Optional stronger checks require your retained local evidence:

```sh
python3 tools/literature/verify_fastqtl_workflow_checkpoint.py \
  --jar /absolute/path/to/exact-producing-omicsmatic-0.4.0.jar \
  --private-root /absolute/path/to/fastqtl-701 \
  --cache /absolute/path/to/frozen-primary-XML-cache \
  --predecessor-root /absolute/path/to/fastqtl-701/predecessor-context-1/source \
  --predecessor-jar /absolute/path/to/exact-cytometry-intensity-producer.jar
```

The private-root/cache/predecessor checks are optional and reported individually.
Saved-evidence verification performs no new scientific fit. To run models again,
use the [synthetic harness](../../../tools/literature/fastqtl_workflow/README.md)
or the Java workflow's fresh VERIFY/REPLAY mode. Preserve failed/partial outputs.
Exit 3 identifies an unavailable exact prerequisite: restore it through a
permitted method or keep that dependent check skipped. Exit 2 identifies an
invalid qualification: restore exact source/inputs, investigate and retain the
failure. Never edit historical hashes to claim success. SHA-256 provides
integrity, not execution authentication.
