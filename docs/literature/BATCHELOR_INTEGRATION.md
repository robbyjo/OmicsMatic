# Batchelor and FastMNN: two paper roles with different support

The 250-paper source scan found two omitted roles. Both now have their own
source-bound records in the catalog. **Batchelor remains marked missing**
because the second role is not implemented. A selected implementation for
the first role is available; this does not award a new package badge.

| Paper | Reported use | Available implementation | Remaining uncertainty |
| --- | --- | --- | --- |
| P051, [neutrophil compartment](https://doi.org/10.1038/s41586-025-09807-0) | Batchelor1.20.0 integration before Monocle3 1.3.7 pseudotime | Original Monocle1.3.7 `align_cds` calling original Batchelor1.20 `reducedMNN`, followed by the documented trajectory workflow | The authors' integration function, exact inputs, transformation, settings, commit and roots are unreported. The selected workflow is an interpretation. |
| P040, [tumour-draining lymph nodes](https://doi.org/10.1038/s41586-026-10954-1) | FastMNN correction of imaging mass cytometry measurements after transformation/QC, followed by annotation | Missing | Method version `1.29` is reported. The exact Batchelor package patch and correction call were not recovered. `reducedMNN` does not substitute for `fastMNN`, and protein measurements are not RNA counts. |

For P040, the authors' linked processing notebook imports `batchelor` at
reviewed commit `c8074a59e2fd6497e4e613eb2b2f9ea4f2a85e3d`. No correction call
or session information was found in the reviewed text files, and its
cell-annotation notebook is empty. This supports package association while
leaving the actual function call and version unresolved. The repository
does not declare a license in GitHub metadata. Its code was inspected in
private intake, not executed or redistributed here. The public evidence
contains paths, hashes and review findings only.

## Run the available P051 interpretation

Follow [the complete Monocle1.3.7/Batchelor1.20 workflow guide](MONOCLE137_WORKFLOW_DEVELOPMENT.md)
for the exact JSON request, matrices, metadata, explicit batch labels, runtime
setup and runnable examples. Use its pinned standalone candidate, or build
from the recorded source. The released/canonical JAR is unchanged and does
not acquire a new root command from this catalog amendment.

```text
java -jar docs/literature/monocle137-workflow-development/monocle137-workflow-jdk21.jar --help
python tools/literature/monocle137/setup.py --help
java -jar docs/literature/monocle137-workflow-development/monocle137-workflow-jdk21.jar --request /absolute/path/request.json
```

Original analysis requires the separately provisioned, source-qualified Linux
R4.3.3/Monocle3 1.3.7/Batchelor1.20.0 runtime and its declared dependencies.
Use permitted Linux Java, including WSL when applicable, and Linux paths.
The candidate contains no original R runtime. Setup requires explicit
installation/download flags on a host where those actions are permitted;
analysis installs nothing. A Windows Java saved-evidence audit does not
establish that original analysis ran on Windows.

**SKIP3 — skip dependent analysis when required software cannot be supplied
or installed, or execution is forbidden.** Read the retained cause and
required-action message. Supply the named exact prerequisites on a permitted
host, or leave that analysis skipped. Do not silently replace versions.

**FAIL2 — no result is admitted.** Retain complete inputs, original process
logs, RDS diagnostics, `analysis-status.json` and `WARNINGS.md`. Correct the
named input, integrity, capacity, process or scientific cause and retry a
NEW output directory. Never drop cells or repair nonfinite values to obtain
a passing result. The disconnected control retains 240 infinite pseudotime
values among all 480 cells.

LEARN admits graph learning but does not admit a pseudotime result. RUN
requires explicit roots. Graph identity can be bound to a prior LEARN
request as documented; no biological root is inferred.

## Catalog evidence and verification

[The amendment evidence](batchelor-catalog-integration/README.md) retains the
exact eight-file predecessor, a 250-source name/function scan, the two
new reviews, and the original workflow qualification. All 2,479 earlier
observations and 1,052 earlier reviews remain unchanged. The updated cohort
has 406 named tools, 2,481 observations and 1,054 reviewed uses. One supporting
main-article pointer was added for P040's annotation paragraph.

The checkpoint remains **102 new scoped integrations**, with **245 missing
tools**. The missing count increases because an omitted package is now
visible. The original workflow's saved discovery record remains frozen with
its historical catalog-merge-pending status; this amendment records the
later merge without rewriting qualified scientific evidence.

```text
python tools/literature/batchelor120_catalog/verify.py --out build/literature-review/monocle-runtime/batchelor-catalog-check-NEW
python tools/literature/batchelor120_catalog/verify.py --rescan-sources --out build/literature-review/monocle-runtime/batchelor-source-check-NEW
```

The default check validates exact current transitions, predecessor lineage,
source/evidence integrity and preserved scientific gates. It does not refit
R models or independently authenticate historical execution. The optional
rescan requires the original checksummed XML cache; missing sources yield
SKIP3 rather than a false rescan pass. Neither check proves complete paper
semantics, full-package equivalence or biological reproduction. Cell Ranger
remains excluded at the user's request. The broader goal remains active.
