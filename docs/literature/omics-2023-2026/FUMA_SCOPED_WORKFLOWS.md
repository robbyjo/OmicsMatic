# FUMA: available native workflows and remaining scope

**A validated native subset is available through `fuma-tools`.** Its current
candidate is identified in
[the corrected validation record](../fuma-expression-preparation-2/VALIDATION.json).
The canonical release JAR has not been updated to this candidate. The catalog
uses `native-subset-validated`, the same scoped category used for other bounded
native methods. This does not certify an entire hosted service or paper pipeline.

Start with either runnable workflow:

- [Prepare a genotype reference, replay it and construct loci](../FUMA_REFERENCE_WORKFLOW.md).
- [Prepare sample-level expression and hand it into GENE2FUNC analysis](../FUMA_EXPRESSION_PREPARATION.md).

Each guide supplies copyable commands, synthetic inputs, expected output and
failure recovery. Choose new output directories and check every exit code.

## Available methods

| Command | What it does | Inputs you must declare |
| --- | --- | --- |
| `prepare-reference`, `replay-reference` | Autosomal diploid MAF/LD, complete selected-call capture, exclusions and replay | BED/BIM/FAM, exact sample/variant selections, founder policy, assembly/population/release, MAF/floor/windows |
| `loci` | Candidate/independent/lead selection and complete interval-component loci | GWAS plus verified reference bundle or explicitly declared raw MAF/LD coverage; thresholds, ties, regions and merge policy |
| `map-genes` | Supplied positional/eQTL/chromatin evidence mapping and distinct score/state filters | Candidates, gene annotation, selected tracks and filtering/coordinate policies |
| `annotate-variants` | Supplied consequence/score/state/LD annotation postprocessing | Declared annotation/reference resources and missingness policies |
| `gene-set` | GENE2FUNC overrepresentation | Query/background IDs, annotation, every intended GMT family, correction and display choices |
| `prepare-expression` | TPM/RPKM label means, centered expression and MAGMA covariates | Raw expression, exact sample/label mapping/order, units, build, namespace and release declarations |
| `expression` | Expression selection and supplied-DEG enrichment | Prepared/supplied matrices, exact symbols/query/background and independently supplied DEG sets |
| `transcript-sites` | Explicit TSS/TES mapping | Transcript coordinates/strands/biotypes, variants and P/window boundaries |
| `magma-report` | Supplied gene/set report interpretation | Fitted results and explicit missingness/correction/display policies |
| `locus-novelty` | Distance-based prior-association comparison | Leads, declared reference/coverage and distance/identity policy |
| `known-associations`, `fisher-enrichment` | Complete-family association union and count/group enrichment | Baseline fits or counts/groups, matching keys, planned comparisons, correction and overlap policies |
| `workflow` | Connect selected typed stages and retain independent branches | Explicit JSON stage configuration and resource identities |

Original MAGMA gene, set and property fitting is available separately through
`magma-tools`; classic cell-type fitting uses that adapter. These fitting stages
need the permitted managed MAGMA1.10 runtime. Its version is explicit: a source
paper reporting1.08 has not been reproduced by running1.10.

## What still needs separate work

The subset does not acquire/predict raw variant annotations, discover eQTL or
chromatin interaction tracks, generate DEG sets, perform their t-tests, cluster
heatmaps or expose every hosted FUMA module. Provide the resources required by
the supported methods or use a separately declared upstream analysis. Do not
interpret supplied annotation postprocessing as new consequence prediction.

Annotation tracks, gene sets, DEG memberships and selected genotype/expression
data are ordinary method inputs. Their absence is distinct from an unimplemented
method. Likewise, unspecified author settings remain unknown; they do not
establish defaults and do not require inventing a historical command.

[The fourteen-paper role map](fuma-role-coverage.json) retains the source XML and
paragraph identities, reported releases, reviewed uses, supporting pointers and
command scope. P243 does not identify a precise FUMA module; no command is
attributed from that broad description. The original source reviews remain in
[FUMA_SOURCE_REVIEW.md](FUMA_SOURCE_REVIEW.md). Its older availability/count
statements describe historical checkpoints. Current availability is defined here
and in the corrected validation record.

## Warnings and exact recovery

**Require a completed analysis and inspect every `WARNINGS.md` and audit.**
Reference-bundle admission recomputes captured hardcalls. Outside-window pairs
are uncomputed, and undefined eligible LD fails the complete request. Native
complete interval components deliberately repair observed original merge defects;
other source differences remain documented in each module guide.

Expression eligibility is strict raw mean>1 before capping/logging. Selected-
sample centering and unweighted label `Average` serve different purposes.
Inspect label counts, exact namespaces, exclusions and boundary/units warnings.
Eighteen deliberately extreme raw-mean fields differ from independent base-R
overflow results; those controls are not original FUMA preprocessing code.

**Required upstream runtime unavailable/installation forbidden: SKIP3.** Where
permitted run `python tools/literature/install_magma.py --allow-download`, then
supply `--home build/omics-magma` on the supported host. Otherwise keep dependent
fitting skipped until a permitted runtime is supplied. Native stages need Java
and declared data, without R/MAGMA/FUMA installation. Cell Ranger remains excluded.

**Input/integrity/numerical/cancellation/capacity failure: FAIL2.** Read the named
cause in the session and retained `partial/`, correct it and rerun the complete
request to a new directory. Partial tables are diagnostics. A failed upstream
stage skips its dependents; independent completion retains the parent's overall
failure or partial-skip status. No biological accuracy or whole-paper result
follows from these synthetic and original-tool controls.
