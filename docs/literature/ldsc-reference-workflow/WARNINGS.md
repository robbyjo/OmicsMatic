# Warnings and required actions

**This is a bounded native reference-preparation workflow. The complete
250-paper goal, whole LDSC tool and paper reproduction are unfinished.**
Use the [guide](../../vignettes/ldsc-reference-preparation.md) and complete
synthetic examples, then supply the scientifically intended reference inputs.

## Admission and failures

**Require exit 0, a verified `COMPLETED.json`/manifest pair and
`analysis.json` with `result_admitted: true`.** A preflight record is a
component only. Partial scores/model files may remain after a failure and
must not be interpreted as a complete result. Preserve failure evidence and
any `unadmitted-analysis.json`.

- **SKIP3 — dependent analysis skipped.** Supply the named permitted missing
  input or complete Java backend, or use a separately qualified supported
  scope. Whole-reference refusal requires reviewing the actual chunk graph,
  map, population and capacity before explicitly opting in. Mixed chromosomes
  need separate declared jobs. Unsupported estimators are not substituted.
- **FAIL2 — no complete result.** Correct the stated literal-input, ordering,
  numerical, integrity, console-recording, capacity, heap or storage issue and
  retry with a new output directory. Retain exact inputs and old attempts.
  Do not repair IDs, round dosages/continuous annotations, remove failed pairs
  or rows, force positive scores or replace missing diagnostics.
- **Existing output is protected.** Choose a new output directory. A refused
  retry does not invalidate or modify the old bundle's completion marker.

Native analysis requires the complete current source-build JAR and Java 17+.
The earlier published 0.4.0 release predates this command. There is no native
Python/R/bitarray installation requirement. For any separately chosen upstream
workflow, if its required installation is forbidden or unavailable, warn the
user with the exact prerequisite/action and skip dependent analysis. Never
treat general download permission as supplied scientific input/provenance.

## Scientific interpretation

Verify ancestry, sample relatedness, assembly, coordinate map, alleles and
annotation identity yourself; filenames and provenance strings do not prove
compatibility. This command reads declared diploid hard calls, one chromosome
and exact literal sample/variant identities. It does not construct annotations,
interpolate maps, harmonize GWAS alleles, munge summary statistics, convert to
liability scale or reproduce outer wrapper pipelines.

Source chunk expansion/fallback can include pairs beyond the nominal window
and can create a whole-reference graph. Inspect both graph tables and observed
warnings. Negative unbiased squared correlations/scores and the original A2
floating MAF boundary are retained. The strict common population is distinct
from the complete retained population.

Printing fewer variants changes output rows only; full/common M and overlap
summaries still describe the complete declared contributing population. Review
`print-selection.tsv`: membership policy can omit unavailable print IDs only
with an explicit warning/audit; strict policy refuses them. Continuous M values
are annotation-weight sums, not binary SNP counts. Do not round them into
binary enrichment inputs or combine counts/overlaps from a different universe.

The retained-byte estimate is an SDK engine admission estimate, including
count and binary-overlap arrays, not total process RSS. Permit adequate heap
and storage; inspect default or complete custom limits. Capacity failures do
not authorize truncating scientific populations. Captured source bytes have
separate I/O limits; small manifest/completion metadata is additional.

## Observed original-CLI failures

**No original full-CLI subset result is admitted.** The unchanged source root
CLI's `--extract` and `--keep` fail before estimation at `parse.py`'s
`np.nonzero(pd.Series)` join in the pinned NumPy/pandas reference runtime.
Keep those errors and their exact inputs. If requiring original full-CLI
subset execution, resolve it through a separately audited upstream edition or
runtime before claiming success. The native method has its separate original-
core subset controls; choosing it requires accepting that documented scope.
No source patch or monkeypatch was used to manufacture an original result.

The selected package is CBIIT 3.0.2; the root CLI literally displays 3.0.1.
Do not erase this distinction or generalize to every historical LDSC edition.
The five successful original CLI controls print three decimal places; native
outputs retain full precision. The `3.55e-15` continuous-sum accumulation
difference is retained, with explicit comparison tolerance.

## Historical evidence and verification

Do not refresh old source hashes after modifying the SDK. The old API verifier
is bound to its earlier snapshot and will correctly reject changed current
SDK/test source. Use the new source-bound workflow audit and retain both
qualifications. A missing audit prerequisite yields SKIP3; inconsistent source
or evidence yields FAIL2. Review the mismatch and freshly qualify a changed
component rather than weakening fingerprints or relabeling failed evidence.
