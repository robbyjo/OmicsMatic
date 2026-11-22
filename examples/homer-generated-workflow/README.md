# Original HOMER generated genomic backgrounds

These synthetic examples run the original genomic wrapper with a private,
already admitted Linux x86_64 genomic home and a separate generated-operation
admission. Use Linux Java inside WSL on Windows. They do not use a named genome
package, paper data, Cell Ranger, or biological calibration.

Read every analysis `WARNINGS.md` and `session.json`. A count shortfall is retained
only under the explicit policy below. Original generation is unseeded; identical
input files do not promise identical selected backgrounds.

First admit the additional operation on an existing genomic home:

```bash
python3 tools/literature/admit_homer_generated.py \
  --home /private/homer-genome-5.1 --profile linux-x86_64-genome-5.1 \
  --mode generated-old --out /private/NEW-old-admission-5.1
```

Use separate admission paths per version and algorithm. Old generation is
available for 5.1, 4.11 and 4.11.1; new generation is available for 5.1. Admission
does not install or download. Old preparsing additionally requires `chmod` and
its libraries. Missing, forbidden or changed dependencies skip the affected
analysis with exit 3. Correct the named cause where permitted and use new paths;
preserve failed reports. Genomic-home installation is described in
[`HOMER_GENOMIC_RUNTIME.md`](../../tools/literature/HOMER_GENOMIC_RUNTIME.md).

Generate version-specific recipes and run one:

```bash
python3 examples/homer-generated-workflow/prepare_inputs.py \
  --version 5.1 --mode generated-old \
  --generated-admission /private/NEW-old-admission-5.1 --out /private/NEW-inputs
java -Xmx1536m -jar build/cli/jlinalg-0.3.7.jar homer-tools genome \
  --options-file /private/NEW-inputs/given-gc-normalized.json \
  --home /private/homer-genome-5.1 --profile linux-x86_64-genome-5.1 \
  --out /private/NEW-analysis --timeout 120
```

For new generation, admit `--mode generated-new`, generate recipes with that
mode and use `overall-normalized.json`. Other recipes exercise positional
matching, all-candidate GC selection, forward strands, and separately requested
known/de novo phases. Old recipes also exercise CpG weighting, asymmetric
resizing and lowercase masking. Four-base known models avoid the legacy
zero-percent report defect in this technical fixture; discovery remains eight
bases. This is not a substitute for a paper's actual motif library or contrast.

| Choice | Original behavior and required review |
| --- | --- |
| `background-randomness original-unseeded` | Actual pools/draws are retained; no wrapper seed is invented. |
| Old `background-total` | N means targets plus backgrounds, before unsupported-bin exclusions; choose at least twice eligible targets. |
| Old `background-overlap` | 5.1 requires `remove-original`, using literal inclusive intervals, same strand for forward selection. 4.11/4.11.1 requires `retain`; those wrappers do not filter generated backgrounds against targets. Old self-overlaps remain allowed. |
| Old `background-boundary-policy` | `error` fails for selected out-of-reference regions. Explicit `retain-original` keeps the original left padding/body extension and right truncation; inspect `generated-pool.tsv`. No bases/coordinates are repaired. |
| `background-count-policy` | `exact` fails on a selected-count mismatch. Explicit `retain-original` preserves it with a prominent warning and requested/actual counts. |
| New `background-count`, `background-candidates` | N requests backgrounds; NN requests screening rather than imposing a hard cap. NN=0 considers all candidate windows. Actual selection can be substantially smaller than N. |
| New mask/N/weight choices | Fixed N fraction 0.5; requires lowercase mask `no` and weighting `none`. GC strata and optional kmer matching still apply. Initial weights are one before oligonucleotide normalization. |
| New overlap choices | Target and background self-overlap policies are explicit; source inclusive windows treat touching as overlap. |
| New positional matching | Equal retained target lengths are required. Source average-length profile arrays can be unsafe for variable lengths. Choose an appropriate explicit size, overall matching, or retain SKIP. |
| Memory and capacities | Review pool record/residue/sort limits for old generation. New generation reserves genome-sized pointer address space even for a small NN. Resident memory, virtual address space, unsafe contig/iteration bounds and Java profile capacity are checked separately. Periodic monitors are not OS quotas. |

New target IDs discard source identities and are reindexed by bin. Native checks
join geometry and multiplicity. `generated-target-mapping.tsv` retains all
compatible source aliases where a unique source assignment cannot be recovered;
`regions.tsv` retains every original input row and quality exclusion. Same-start,
same-strand targets of different lengths are unsupported because the final IDs
lose the end coordinate.

Old native audits check the complete preparsed position population, produced DNA
hashes, quality exclusions, frequencies/bins, availability and deterministic
selection quotas. New audits check all retained targets and selected backgrounds,
their coordinates/DNA/GC bins and complete canonical kmer profiles. The adaptive
unretained new candidate stream, matching optimizer and selection probabilities
are not reconstructed. `ProfileScore` is a matching score, not P/q/E.

The shared genomic audit checks initial weights, replays original normalization
on the exact native planned population, and checks native known/learned presence
statistics. Review weighted population flooring and the original forward q-value
algorithm in the main genomic documentation.

Run the public verifier against the development JAR and separately admitted homes:

```bash
python3 tools/literature/verify_homer_generated_workflow.py \
  --jar build/cli/jlinalg-0.3.7.jar --out /private/NEW-verification \
  --home-5.1 /private/homer-genome-5.1 \
  --old-admission-5.1 /private/old-admission-5.1 \
  --new-admission-5.1 /private/new-admission-5.1 \
  --home-4.11 /private/homer-genome-4.11 \
  --old-admission-4.11 /private/old-admission-4.11 \
  --home-4.11.1 /private/homer-genome-4.11.1 \
  --old-admission-4.11.1 /private/old-admission-4.11.1
```

The verifier runs five recipes for each selected version/algorithm. Use repeated
`--case VERSION:MODE` to select a subset. Missing cases skip independently. It
checks exact-population original known/discovery/comparison replays and runs a
separate fresh original generated draw. It does not compare independent unseeded
draws as if they were identical. Exit 0 means all selected checks passed; exit 2
means a retained failure; exit 3 means a dependency/case was skipped. Inspect the
top-level `WARNINGS.md`, `verification.json` and per-analysis warnings. Preserve
failed/partial outputs and use new paths after correcting their stated cause.
