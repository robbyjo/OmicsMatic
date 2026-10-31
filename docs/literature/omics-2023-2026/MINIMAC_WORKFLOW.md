# Minimac: typed original-tool workflows

The development `minimac-tools` command connects original Minimac processes to
native Java input, identity, reference, dosage and QC audits. It supports five
explicit operations: `prepare-reference`, `convert-reference`,
`inspect-reference`, `check` and `impute`. `check` never runs imputation.
Text-only inspection needs Java; binary MSAV inspection needs the admitted
original Savvy exporter. Read `minimac-workflow-validation.json` for the exact
candidate, hashes and evidence; this is a development subset, not a release or
a reproduction of every reported paper/server workflow.

**Warnings and required action are part of every result.** Read `WARNINGS.md`,
`analysis-status.json`, `session.json`, the retained recipe and original logs.
Exit 3 skips an unavailable, forbidden or unsupported runtime/capability with
the cause and recovery. Exit 2 records input, process, resource or audit failure.
Provisional artifacts move under `partial/` and are not completed results.
Correct the named cause and choose a new output directory; existing outputs
and runtimes are preserved. Exit 0 still requires scientific review.

## Runtime and setup

Original processes require Linux x86_64 Java. On Windows use Linux Java in WSL.
The profiles keep original versions separate:

| Profile | Original software | Additional constraint |
| --- | --- | --- |
| `linux-x86_64-minimac4-4.1.6` | Minimac4 4.1.6, Savvy 2.1.0 | Current MSAV preparation, conversion and export |
| `linux-x86_64-minimac4-1.0.2-qemu8.2.2` | Minimac4 1.0.2 | Explicit private QEMU 8.2.2 |
| `linux-x86_64-minimac4-1.0.0-private-gomp14` | Minimac4 1.0.0 | Explicit private GOMP dependency |
| `linux-x86_64-minimac3-2.0.1` | Minimac3 2.0.1 | Legacy reference estimation and imputation |

Supply the matching admitted `--home`. Admission verifies original binaries,
notices, scientific health checks, the complete host-library inventory and
kernel; identities and hashes are checked before and after process execution.
Legacy execution disables phone-home. Owned processes have explicit time and
workspace bounds, recorded process IDs and cleanup. No global OS/kernel change,
registration, participant upload or version substitution is performed.

Analysis commands never install. Where permitted, run
`python3 tools/literature/install_minimac.py --help`, then choose the profile,
new private home, archive cache and new report. Installation requires
`--allow-install`; downloads additionally require `--allow-download`, or use
verified offline archives. If installation is forbidden or the host is
unsupported, skip the affected analysis until a supported admitted runtime is
provided. Preserve changed homes and failed attempts. See
[runtime admission](MINIMAC_RUNTIME_STATUS.md). Cell Ranger remains excluded.

## Recipe and input contract

See [13 executable examples](../../../examples/minimac-workflow/README.md).
Recipes declare assembly, one literal contig/region and regional interpretation,
scientific review, reference/target types, identity TSVs, column semantics,
phase/missing policies, exact allele-key matching, target-only exclusions,
logical reference/target person overlap, output-ID policies, supplied-call and
ordered-copy change policies, engine parameters, post-QC and resource limits.
Unknown fields and arbitrary commands fail. Resource paths are portable members
under an explicit real `resource_root`; traversal and linked files fail.

Identity TSV columns are `contig`, `column_id`, `column_copy`, `person_id` and
`person_copy`, tab-separated. They map every actual ordered copy on that contig.
No suffix, sex, chromosome-name or genome-build heuristic supplies identities
or ploidy. Person-sample and flat haplotype-column encodings remain distinct.
An owned original Min3 preparation may generate `*_HAP_1/2` columns: only that
exact producer's expected complete header and haplotype matrix establish the
new output ledger. Supplied arbitrary suffixes are never interpreted this way.

VCF input supports complete biallelic literal-DNA phased GT. Reference calls
must be complete. Missing targets and unphased homozygotes require their
explicit policies; heterozygous phase is never inferred. `gt-only-lf-bgzf`
staging deliberately removes QUAL/FILTER/INFO and non-GT payload. Original
bytes remain in immutable snapshots, with representation changes ledgered.
Targets join by exact contig, position, REF and ALT. Target-only exclusions
require an explicit policy and full ledger; no allele flip, normalization,
liftover, sample selection or panel substitution occurs.

Compressed M3VCF/MVCF admission checks every physical and selected row,
representative mapping, ordered copy, model parameter, extent and boundary.
Boundary policy is explicit; historical consumers require shared boundaries.
Both shared physical rows and ID/model changes remain recorded. Unsafe v2
cumulative indices and missing ALT lists fail before the upstream converter.
No all-reference production markers are silently removed. Binary MSAV uses a
complete original export with `--update-info never`; coordinate-cropped export
is not used. See [reference findings](MINIMAC_REFERENCE_BLOCKS.md).

M3 staging explicitly chooses preserved compressed bytes, lossless gzip, or
`lf-gzip`. The latter changes only line endings, with decoded fields and full
identity/model/matrix fingerprints checked. CRLF is not silently canonicalized
under the lossless policies. Native CSI indexes are constructed for the owned
BGZF target, preserving its bytes. Region flags preserve literal contig names,
including colons; no chromosome alias is inferred.
The exact legacy profiles can still reject such names: original 1.0.2, 1.0.0
and Min3 2.0.1 rejected `chr20:fixture` with their chromosome whitelists despite
explicit `--chr`. These are retained failures with the original causes promoted into `WARNINGS.md`;
the input contig is never renamed to bypass it. Use a separately reviewed
compatible profile/input representation or skip that analysis.

## Operation and result contract

Current preparation runs original compression, and current conversion runs
original M3VCF-to-MSAV conversion. Complete exports must retain exact marker
keys, the full ordered haplotype matrix and declared people/copies. Conversion
also preserves binary32 model values and physical block/boundary counts.
Preparation may estimate new parameters and repartition blocks. Result bundles
contain relative reference/identity paths and hashes; inspect
`reference-bundle.json` and `reference-transition.tsv` before reuse.

Imputation requests complete current GT/HDS/DS/GP/SD or legacy GT/DS/GP output.
The full declared reference-marker family and target order/ploidy must pass
before explicit post-QC. Missing, duplicated, altered or unexpected markers
fail. Original input/stage and output hashes are verified across the audits.
`genotype-changes.tsv` distinguishes unordered observed-call conflicts from
known ordered-copy conflicts and pure phase swaps. The recipe may reject or
explicitly retain these changes; original calls are never overwritten with
input calls to conceal a conflict.

`genotypes.vcf` preserves all original field strings for retained variants.
`variant-results.tsv` retains every original variant and its QC decision,
reference/target/upstream IDs, source AF/MAF/R2, precision bounds and separate
stable HDS/R2 diagnostic. `variant-join.tsv`, target exclusion/representation
ledgers, complete reference audits and original stdout/stderr remain available.

Posterior identities use current six-significant-digit binary32 envelopes or
legacy three-decimal FORMAT/five-decimal INFO envelopes. Current HDS is ordered;
legacy GP/DS does not identify ordered HDS and is not converted into it. SD is
posterior variance. Current source AF/R2 accumulation follows the explicitly
chosen temporary-sample grouping. Native stable R2 is diagnostic and undefined
at AF 0/1; upstream's zero convention is separate. Compatible source R2 values
above one remain unchanged with a warning about numerical cancellation.
These checks are not empirical imputation accuracy or calibration.

Post-QC chooses `none` or source `INFO/R2`, exact decimal-text `minimum`, strict
`gt` or inclusive `ge`, and an explicit missing-value policy. Source R2 is never
replaced by stable R2. A header-only post-QC result receives a prominent warning.
Resource limits bound input/decoded text, lines, samples, markers/cells,
estimated native retained memory and process/audit workspace. They do not cap
the upstream C++ process's peak RAM; final status/manifest diagnostics add
metadata outside the process/audit workspace checks. Increase limits/heap only
after review, or explicitly choose smaller inputs; no automatic analysis
retuning or retry is performed.

## Unsupported routes and retained findings

Current original compression accepts custom block flags but does not honor
them: `--max-block-size 16` emitted a 120-variant block; pinned source assigns
all three compression flags to a different field. Requests for these custom
settings skip with cause and recovery. Explicit pinned defaults are supported.
Historical Min4 reference-only estimation returned 255 without a reference and
is unavailable. Historical profiles have no MSAV exporter/converter. Min3
imputation requires a compatible v1 reference; no v2 rewrite is inferred.

The original mixed-ploidy preparation wrote AN=48 for 40 actual copies on 350 X
variants. Such a panel fails admission; no AN repair or membership change is
made. Separately valid haploid/diploid fixtures do not certify mixed preparation
or X PAR/non-PAR workflows. Original Savvy coordinate-query omissions, source
version ambiguity, panel access/phasing/eligibility, server-managed pipelines,
received-data roles and historical/full-paper reproduction remain separate.
Read [the source-role review](MINIMAC_SOURCE_REVIEW.md) before mapping a paper's
reported operation to this bounded adapter.
