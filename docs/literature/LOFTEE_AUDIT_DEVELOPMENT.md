# LOFTEE output audit and original reference controls

The native Java `LofteeVcfAudit` component checks complete VEP/LOFTEE VCF outputs against their original input VCF and original logs. It preserves allele and transcript rows, confidence calls, filters, flags and raw information. It does not predict consequences or assign loss-of-function confidence.

**The typed LOFTEE execution adapter is unfinished. LOFTEE remains missing in the tool inventory and receives no implementation credit from this component.** The current catalog still records 80 new scoped integrations and 251 missing integrations. Eight source-reviewed LOFTEE role drafts remain unapplied. The canonical/released JAR is unchanged. Use a current source build or the separate development artifact named in the component validation record.

**FAIL2: no complete audited result is returned after an input, identity, capacity, plugin, output or ledger failure.** Preserve original inputs, complete VCFs, logs and provisional ledgers. Correct the named cause and retry NEW paths. Callbacks may receive provisional rows before a later error; a caller must stage those rows and admit them only after the entire audit returns successfully. Never relabel a partial callback stream as a successful analysis.

**SKIP3: skip dependent annotation when original dependencies or resources are unavailable or forbidden.** The Java audit itself requires Java 17 or newer and installs nothing. Original annotation additionally requires the exact permitted VEP/LOFTEE/Perl environment and declared reference resources. The experimental preparation helpers and recovered local installation are research evidence, not a complete fresh-checkout annotation installer. A working dependency probe is not a successful annotation or full workflow admission.

## What is checked

The reader streams plain or gzip UTF-8 VCF and log files with explicit limits on combined decoded characters, line length, variants and annotations. It retains every original variant in order, ALT identity, QUAL/FILTER, non-CSQ INFO, FORMAT columns, sample order and genotype token. It requires complete per-ALT `ALLELE_NUM` coverage and all source transcript rows. It refuses stale input CSQ, duplicate INFO/header fields, invalid references, truncated families and unsupported symbolic alleles. This profile accepts literal uppercase sequence alleles; BCF and symbolic/spanning alleles need a separate validated profile.

The required CSQ fields are `Allele`, `Consequence`, `Gene`, `Feature_type`, `Feature`, `BIOTYPE`, `ALLELE_NUM`, `LoF`, `LoF_filter`, `LoF_flags` and `LoF_info`. Additional original fields, including names such as `genes.gtf.gz`, remain unchanged. No canonical/MANE transcript selection, gene-level aggregation, identifier repair, strand correction or confidence imputation is inferred.

Protein-coding transcript stop-gained, frameshift, splice-donor and splice-acceptor consequences require an original LOFTEE call. A missing call fails rather than becoming HC or a benign prediction. The auditor detects original plugin-instantiation and per-variant plugin failures even when VEP exits successfully. Supply every original stdout/stderr and warning file to the audit; do not omit a warning file to obtain a pass.

HC with a low-confidence filter is invalid; LC requires its original filter reason. HC flags such as `SINGLE_EXON` remain HC with the flag retained. OS is distinct and requires declared extended splice prediction. Empty confidence on an ineligible/non-target consequence remains empty. Raw `NaN`/`Inf` information is retained, not repaired. These are transport and source-contract checks; they do not independently recalibrate biological predictions.

## Minimal Java example

Compile against a current source build or the separate development JAR. Pass an unannotated original input VCF, its complete original VEP/LOFTEE output VCF, and every original log path. The example counts provisional rows without writing scientific results. It prints success only after the complete audit returns.

```java
import java.nio.file.Path;
import java.util.Arrays;
import org.jlinalg.genetics.LofteeVcfAudit;

public final class LofteeAuditExample {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("input.vcf output.vcf original-log [more logs...]");
        }
        var logs = Arrays.stream(args).skip(2).map(Path::of).toList();
        var summary = LofteeVcfAudit.audit(
            Path.of(args[0]), Path.of(args[1]), logs, false,
            new LofteeVcfAudit.Limits(20_000_000, 100_000, 1_000_000, 2_000_000),
            variant -> { }, annotation -> { });
        System.out.println("AUDIT PASSED: " + summary.variants() + " variants, "
            + summary.annotations() + " annotations. No new annotation model was run.");
    }
}
```

The final `false` declares that extended splice prediction was disabled in the original run. Supply the actual original setting. The four capacities count combined decoded UTF-16 code units, variant rows, annotation rows and characters in a single line, respectively. Reaching a limit fails; no implicit sample/transcript reduction occurs. Caller sinks can retain the immutable `Variant` and `Annotation` records, including the complete raw CSQ token, in an owning transaction.

## Original runtime and controls

The retained source capture includes [original LOFTEE GRCh38](https://github.com/konradjk/loftee/tree/a46b502a68c812c8ae0c5a5721c0603fe81cae8d), tag `v1.0.4_GRCh38`, and the [fork used by the cited BRaVa pipeline](https://github.com/populationgenomics/loftee_38/tree/c8fdde00e515148450416128d43fcf01f1ee6bb8). Both carry the original Apache-2.0 notice. They remain unchanged and separate from the Java implementation.

VEP 105.0 and 224 dependency packages were installed in a new private Linux environment. The first setup helper failed while reading micromamba's package-list JSON; that failed attempt remains retained. A real module-load check then found missing `Bio::Perl`. Original VEP 105's installer prepared its selected `release-1-6-924` BioPerl/API source in another private directory. Its own test stage reported `NOTESTS`, which remains a warning, not a passed upstream suite. Subsequent help/module checks and real annotation runs are separate evidence. Shared Perl libraries and existing environments were not replaced.

An initial module-health command failed because inline Perl source was transported through WSL command parsing. The corrected probe executes a retained physical Perl file with `--exec`; it records loaded module hashes without changing the original plugin. All failed probes and original smartmatch warnings remain retained.

The original program was exercised using an entirely synthetic 4,000-base FASTA, five GTF transcript models, a declared two-level synthetic GERP BigWig and 15 variants. Controls include both strands, stop/frameshift variants, a multiallelic site, splice sites, a noncoding transcript and a single-exon flag. Each of four complete cases contains 15 variants and 80 transcript annotations. Independent Python/source reconstruction matches all eight Java variant/annotation ledgers.

The GTF-only control mode uses an explicit local GTF/FASTA and no database/cache source. In VEP 105, `--offline` automatically enables a cache; an initial control run failed because a human cache was absent. The retained file-mode runs use VEP's explicit custom annotation source. They do not claim GENCODE v39, human GRCh38 reference coverage, a paper container, or cohort validation.

Two original settings remain distinct: default GERP truncation produced 8 HC and 2 LC annotations per case; the explicit percentile setting produced 10 HC and 0 LC on this control set. Perl's false value is passed as literal `use_gerp_end_trunc:0`; the string `false` is truthy in that source branch. No filter is changed to hide a failure. One initial harness wrongly expected LC in both settings; its failed batch remains unadmitted. The short-intron GC-to-GT control received `coding_sequence_variant` from this VEP/GTF combination and does **not** establish coverage of the `SMALL_INTRON` or `GC_TO_GT_DONOR` filters.

The [cited BRaVa pipeline](https://github.com/BRaVa-genetics/vep105_loftee) now advertises changed dbNSFP/CADD resources and registration requirements. The paper role drafts retain reported versions and transcript/mask choices. Current pipeline source is not proof of a paper's exact execution commit. No registered dbNSFP resource, full human cache or private study data was downloaded for these controls.
