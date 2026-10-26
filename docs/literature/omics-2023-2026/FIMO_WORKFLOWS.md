# DNA motif scanning: native Java and original FIMO

The canonical source build passed 1,593 tests with zero failures/errors, five
optional skips and all 72 required suites without skips. All 215 current policy
tests passed without skips, along with 26 earlier-tool regression groups and the
separate original Trim Galore/Unicode/fresh-Python checks. The broader 250-paper
implementation and semantic-review goal remains active. This is an unreleased
source build; it is not a claim about a hosted release or complete paper replay.

The project provides independent native DNA PSSM/p-value methods and a typed
adapter to the actual original FIMO 5.5.9 program. Native scanning needs Java only.
Original q-value analysis needs the exact admitted private Windows x86_64 runtime.
The adapter preserves the original statistical estimator and tested cohort; it does
not substitute a BH calculation for the original program.

Canonical source JAR: `build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`66be181d854806d9ae64d6f060e8defba8af5bdf594d3d352c9c76097a2a8d2e`.
The [FIMO manual](https://meme-suite.org/meme/doc/fimo.html) and
[minimal MEME format](https://meme-suite.org/meme/doc/meme-format.html)
describe the original method and file format. Historical paper versions and complete
upstream/paper pipelines are outside the validated claim.

## Choices that affect the scientific result

- Select background, scanning strands and coordinate policy explicitly. A region/read
  control cohort is not the nucleotide-frequency background. FIMO 5.5.9 defaults to
  NRDB; it does not automatically select a background declared in the motif file.
  Do not infer an older paper's unstated choices from this current-version behavior.
- Native scan background preprocessing follows the tested convention: optional A/T
  and C/G averaging for both strands, then addition of 5e-7 per base and normalization.
  This is separate from the motif pseudocount. The supplied and resolved backgrounds
  are recorded. Core `DnaPssm.build` accepts an already resolved background and does
  not apply scanner preprocessing implicitly.
- The motif pseudocount is weighted by the resolved background and motif site count.
  The integer score lattice determines the reported back-transformed score and null
  tail. A separately labeled continuous log-odds score is not the lattice statistic.
- A native P-value is the zero-order null probability of a score at least as high.
  It is not a q-value or the P007 empirical fraction of background sequence scores.
  P007's custom completion/calibration and human-minus-ancestral comparison remain
  separate operations; a missing thresholded allele match is never a zero score.
- Original q-values depend on the actual per-motif tested population and estimator.
  Preserve the declared FASTA cohort, motif selection and estimator logs. Do not
  combine thresholded tables and recalculate a different correction silently.

## Native P-value scan

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar motif-scan `
  --motifs motifs.meme --fasta regions.fa.gz --background motif `
  --strands both --coordinates genomic --threshold 1e-4 `
  --out output/native-motif-scan
```

The background/strand choices above are illustrative, not a reconstruction of an
unstated paper setting. Use a new output directory. `--background` accepts `uniform`,
`nrdb`, `motif`, or an explicit comma-separated A,C,G,T vector. `motif` requires a
declared motif-file background. Use `--threshold 1` to score every canonical window,
including allele windows a stricter scan would omit.

Native input is bounded minimal MEME DNA probability text: explicit `ALPHABET=ACGT`,
`alength=4`, width and probability rows. Site count defaults to the documented 20
when absent. At most 16 MiB / 1,000 motifs / 512 positions per motif are accepted. Counts,
log-odds matrices, HTML and custom alphabets are not guessed. Rounded probabilities
require explicit `--normalize-probabilities true`; every changed row is counted.
The original adapter preserves its own reader's preprocessing instead.

FASTA is streamed, with gzip detected by magic bytes. IUPAC ambiguity masks complete
windows. U/u scores as T/t while its forward matched text is retained; X/x/dot aliases
N. Other invalid ASCII defaults to failure; `--invalid-residues convert-n` is an
explicit choice. Genomic mode parses `name:start-end` as a one-based anchor and
warns if the declared span differs from the observed sequence. Sequence mode keeps
the complete first-token identifier. Duplicate names remain distinguishable by
input ordinal in native results and the JSON-escaped metadata ledger.

Native output includes `results.tsv`, model metadata, sequence metadata, complete
window/mask/site counts, source hashes, resource limits and `session.yaml`. Q-values
are blank. A hit/resource/input failure returns 2, quarantines scientific streams
under `partial/` and publishes actionable `WARNINGS.md`, not successful results.
An attainable null tail below floating-point range fails instead of becoming an
exact zero probability. Model defaults are 256 MiB estimated workspace and 1 billion
convolution updates; limits can be explicitly set.

The reusable API is `org.jlinalg.sequence.DnaPssm`, with `MemeDnaMotifs` and
`DnaMotifScanner`. Core scores require an oriented complete A/C/G/T word; the
scanner handles aliases, ambiguous windows, strands, file parsing and coordinates.

## Original q-value scan and permitted installation

```powershell
python tools/literature/install_fimo.py `
  --home build/omics-fimo-private `
  --reference-home build/omics-fimo-5.5.9-gnurx-reference `
  --cache build/literature-review/fimo-runtime `
  --intended-use educational-research-nonprofit --allow-install

java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar fimo-tools `
  --home build/omics-fimo-private --motifs motifs.meme --fasta declared-cohort.fa.gz `
  --background motif --strands both --coordinates genomic `
  --threshold-type q --threshold 0.01 --out output/original-fimo-q
```

The installer reuses the exact authenticated local Windows/Cygwin/GNU-regex reference
build. It verifies the pinned original source archives and binary/DLL/license closure,
runs actual P/q scientific probes and writes a new private home. It does not install
OS features, change global PATH, compile an alternate build or validate another host.
Add `--allow-download` only when missing source-archive downloads are permitted.
Existing verified homes can be checked/reused without install/download permission;
changed homes are retained and rejected. If the reference/profile is absent or
installation is forbidden, the helper and dependent adapter skip with exit 3 with exact cause,
diagnostics and recovery. A future source bootstrap or alternate-host profile needs
its own validation before admission; it is not supplied by this fixed-profile helper.

Original [MEME terms](https://meme-suite.org/meme/doc/copyright.html) apply to the
original runtime. This workflow's user confirmed educational/research/nonprofit
use. The declaration does not grant commercial or redistribution permission and
does not settle unrelated software's eligibility. The native mathematical Java
implementation uses no MEME library code. The publisher's supported
[installation guidance](https://meme-suite.org/meme/doc/install.html) is separate
from this privately tested Cygwin/GNU profile; stock Cygwin's alphabet-regex failure
and the successful separate-backend reference attempts remain retained.

Use `--format text --threshold-type p --threshold 1e-4` for the original text scan
reported in P007. Text mode does not calculate q-values and has no stored-hit cap.
Full mode preserves original HTML/TSV/GFF/CisML/XML reports and estimator logs.
Q thresholds require full output with q-value calculation enabled. A zero-order
background file is selected using `--background file --background-file bg.txt`.
`--motif-id ID` selects one exact motif; absent IDs fail rather than implying no hits.
The original adapter accepts the tested minimal DNA text/FASTA contract, preserves
rounded rows for the original reader and does not repair probabilities silently.

## Required warnings and recovery

| Condition | Result and required action |
|---|---|
| Missing, changed or unsupported runtime | Exit 3 and skip. Inspect `WARNINGS.md`; provision a new permitted admitted home with the explicit helper. Preserve the changed home and diagnostics. |
| Original stored-hit cap reached | Exit 2 even if original exit 0 and the table is empty. Original scientific streams/reports are quarantined. Raise `--maximum-stored-scores`, restrict the declared cohort or choose a scientifically justified threshold, then rerun to a new directory. An empty capped table is not evidence of absent binding. |
| Full output with q-values disabled | Raw upstream q-value zeros are uncomputed placeholders. The project table leaves them blank, preserves the raw report and warns. Enable q-values when needed; never interpret those zeros as zero FDR. |
| Complete scan with no retained hits | A canonical header is emitted only after original summary/loaded-motif and truncation checks. The result means no sites passed the stated settings; it does not establish universal absence of binding. |
| Small original motif population | Original pi0=1 fallback and q-values are retained with a warning. Report the declared cohort and estimator condition; do not silently substitute another estimator/population. |
| Repeated emitted names or inconsistent genomic spans | Inspect the ordinal/header/coordinate ledger. Give alleles distinct identifiers and reconcile coordinates before downstream joins or score comparisons. Original output cannot supply a unique input ordinal for ambiguous names. |
| Input/resource/timeout error | Exit 2, retained logs/partial data and exact named cause. Correct the input or explicit budget; investigate the cause before increasing timeout. Owned original processes are terminated after timeout. Never treat failed partial output as completed analysis. |

Use an ASCII-named UTF-8 JSON `--options-file` when a Windows Java launcher loses
Unicode command-line paths. Values are strings; command-line values override the
recipe. The same recipe mechanism covers Unicode runtime, motif, FASTA and output
paths. Existing outputs are not overwritten.

## Source-paper boundaries

The seven reviewed uses are P003 (P<1e-5; two named JASPAR motifs and downstream TAD
boundary selection), P007 (text P<1e-4 plus separate custom allele completion/empirical
calibration), P053 (FIMO 5.5.3/JASPAR, followed by conservation summaries), P068 (PRDM9
q<0.01 with a separately sampled 100,000 CCS read cohort), P153 (FIMO 5.5.9/TP73
MA0861.1/rs7696969 allele-score interpretation), P215 (lost ARID1A-bound regions;
HOMER shuffled-background E-values are separate) and P217 (MEME 5.5.4,
JASPAR/HOCOMOCO, P<0.001, promoter contexts and separate experiments).

Unstated background/strand/pseudocount settings remain unstated. Database retrieval,
sequence construction, conservation tracks, TADs, enhancer-gene links, mutagenesis,
custom empirical calibration and experimental causal validation are not supplied
by a FIMO occurrence scan. This implementation does not claim full-paper replay.

## Validation scope

The source build passed 48 focused Java tests, 72 native reference workflows/6,805 sites,
96 original P/q workflows/31,643 complete sites plus 16 quarantined sites, and 17 original
adapter controls. The original adapter campaign used 115 Java calls and 208 actual
pinned original processes. Independent reference work includes 118 core models,
24,069 words, 5,628 short-model null enumerations and an exact 90-site binomial/BH check
where original pi0=1. The configured full gate, all 26 older-tool regression groups, the complete
215-test policy suite and separate original Trim Galore checks passed this exact
JAR. Historical versions and complete paper pipelines remain outside this claim.

Primary source records: [P003](https://doi.org/10.1038/s41586-025-09890-3), [P007](https://doi.org/10.1038/s41586-026-11053-x), [P053](https://doi.org/10.1038/s41586-023-06819-6), [P068](https://doi.org/10.1038/s41586-026-10901-0), [P153](https://doi.org/10.1038/s41588-026-02695-8), [P215](https://doi.org/10.1016/j.ajhg.2024.07.022), [P217](https://doi.org/10.1016/j.ajhg.2024.07.020).
