# PoPS catalog integration

**Scope:** one native family credit for Java PoPS 0.2 ridge/OLS, supplemented by
the explicit original PoPS 0.1 and 0.2 adapters. Native 0.1 and native LassoCV are
not supplied. Build current source; released v0.4.0 executables lack these commands.
Read the [native guide](POPS_WORKFLOWS.md) and
[original-program guide](POPS_ORIGINAL_WORKFLOWS.md) for inputs, resource bounds,
runtime setup, output admission and genuine replay.

```powershell
.\gradlew.bat executableJar --no-daemon --max-workers=2 --console=plain
java -jar build/cli/omicsmatic-0.4.0.jar pops-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar pops-original-tools --help
```

The admission records six source-specific roles, including a previously missed
mixed-case `PoPs` observation in P236. It preserves the 250-paper cohort and 446
families, giving 2,692 observations, 1,209 reviewed uses and 1,483 unreviewed uses.
The resulting count is 125 scoped integrations: 23 native and 102 upstream, with
262 missing families. Including three existing adapters gives 128. The registry
still has 485 definitions. These counts are admission results only when the
[catalog validation record](omics-2023-2026/pops-catalog-validation.json) and
the current independent catalog check both pass.

| Paper | Reported use | Unresolved or separate work |
|---|---|---|
| P088 | PoPS 0.2 joint gene-feature modeling following MAGMA 1.10, with locus/genome-wide ranks contributing to cardiomyopathy gene prioritization | Author commit/options, biological LD preparation, rank weights and final combined score |
| P090 | PoPS 0.1 computation, then top-three candidate genes and eight equally weighted evidence sources | Exact author runtime/features and final evidence combination; original 0.1 must not be replaced by native 0.2 |
| P120 | MAGMA-based gene associations/correlations and PoPS enrichment over more than 50,000 features; top score decile prioritized | Exact commands, resource versions and decile ties; Code availability establishes reported PoPS 0.2 separately from the Methods anchor |
| P153 | Intersected the top two model-ranked genes with the top two PoPS-ranked genes per credible set | No fresh PoPS fit or software version is established; credible sets/model scores are separate |
| P220 | Downloaded published BMI, WHRadjBMI and body-fat-percentage scores for gene-set comparisons | Score-release identities, trait/gene mapping and downstream one-sided tests; this is result reuse |
| P236 | Published PoPs gene-prioritization comparator for Speos ulcerative-colitis core genes | No fresh fit/version is established; score provenance, comparator selection and enrichment remain separate |

P239's `POPS` membrane lipid is explicitly rejected as a software-name match.
P090's primary pointer is moved from the still-valid combined-prioritization
paragraph to its explicit PoPS 0.1 computation paragraph. The previous paragraph
and its hash remain supporting evidence. Raw primary XML and old hashes are
unchanged. P120 keeps its original Methods anchor and adds separate version support.

**SKIP3:** missing or forbidden dependencies skip dependent analysis. Install
only where allowed, supply the named permitted setup, or leave it skipped.
Unreported author parameters remain unresolved; do not choose project defaults
to claim an author-specific reproduction.

**FAIL2:** analysis, verification, convergence or resource failure admits no
complete result. Retain inputs, raw reports, source warnings and failed runs;
correct the named cause and retry new output. A finite LassoCV fit with 35 real
source convergence warnings was rejected. Never repair nonfinite values,
substitute versions, thin dependencies or refresh old fingerprints to force a pass.

Original 0.1's single-selected-feature source failure and empty-selection
control-only result are preserved. Munging/selection are preparation, not fitted
models. The full official examples retain 18,383 genes and 508 features; fitting
uses 16,980 targets and 258 selected features. Scientific controls do not establish
author-environment or biological reproduction, or whole-tool equivalence.

The workflow qualification remains frozen at
`2bdd5c41fb55e685950fabaf7619f69a4a28c599`, and the predecessor cumulative catalog
at `82162f75683ebd651a05f8a21d8e482ca54c2885`. Their unchanged consumers run in
genuine clean checkouts. The current consumer hashes every other existing source
file against the workflow checkpoint and verifies the exact PoPS-only metadata
transition, including an inverse journal of all eight changed metadata files.
No scientific source, verifier implementation or Git identity is overlaid.

```powershell
python -B tools/literature/pops_catalog/catalog.py --verify --workflow-checkout D:/trusted/clean-2bdd5c41 --catalog-checkout D:/trusted/clean-82162f75 --catalog-parent-checkout D:/trusted/clean-070ddea4 --previous-parent-checkout D:/trusted/clean-1a9ff82 --baseline-checkout D:/trusted/clean-357c9301 --out build/NEW-pops-catalog-check
```

The workflow records require their exact named private inputs/results; the
catalog lineage also requires the frozen primary XML cache and predecessor
records. Missing prerequisites are reported with recovery instructions. This is
saved-record/source verification; it performs no fresh model fitting or current
runtime-health certification. Source literature pages remain excluded from website
deployment. Whole-paper semantic completeness and the full 250-paper implementation
objective remain open.
