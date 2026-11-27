# Native Java audit of RFMix output

`org.jlinalg.genetics.RfmixOutputAudit` checks original RFMix v2.03-r0 output against a complete, independently supplied input contract. **It does not run RFMix or infer ancestry. The execution adapter and catalog admission are still pending.** This component receives zero new tool-family credit; the catalog remains at 171 scoped families and RFMix remains missing.

The original v2 source at commit `09599c19c69f81571c7a7aab4cd2d847a9e0633a` was built unchanged in a private Linux prefix and exercised in five experiments. This inspected source is not asserted to be an author's historical revision. See the [four paper roles](rfmix-source-audit/README.md) and [component qualification](rfmix-native-audit/QUALIFICATION.json).

## Warnings and required actions

**Do not use the auxiliary `.sis.tsv` as a qualified final-iteration result.** Its original header includes reference samples, and sometimes internal simulation samples, that are absent from its numeric rows. The original writer also leaves its iteration streams open. The native component retains and checks every numeric row but sets `finalIterationCertified=false`; it does not repair the header or certify those probabilities as the last iteration. Skip dependent SIS analyses until an upstream correction and a new original-runtime qualification are available. Byte-identical replay reproduces source behavior; it does not resolve this defect.

**Keep weighting and coordinates explicit.** The source `.rfmix.Q` averages haplotype ancestry over equally weighted CRF markers. It is not a SNP-weighted, base-pair-weighted or genetic-length-weighted proportion. The three-population experiment demonstrates the difference for two admixed samples. MSP segment endpoints and `n snps` follow original source conventions, including a shared printed boundary between adjacent segments. The audit does not convert them to BED or silently infer inclusive endpoints.

**Supply the actual input contract.** Include every retained, filtered locus, its unrounded interpolated genetic position, query sample order, reference sample order/population and actual model settings. Do not derive the contract from the output being checked. The audit explicitly reproduces the source's binary32 storage of SNP map positions before grid selection. It preserves source ceiling/end-collapse behavior rather than substituting nearest-SNP selection. This component does not parse arbitrary VCF/BCF inputs, establish reference-panel validity or prove that upstream filtering was correct.

**Treat scientific failures as failures.** Malformed/nonfinite data, omitted/excess rows, changed files, incorrect marker grids, inconsistent Q fractions, invalid identities or exceeded capacities throw `IOException` without a report. Preserve the failed attempt, investigate the named cause and retry into new workflow paths. No identifiers, probabilities, maps or model states are repaired. Cancellation preserves the thread interrupt and returns no audit.

Native audit of existing permitted output files needs Java only. Reproducing upstream inference also needs an appropriately licensed original runtime; the [v2 terms](https://github.com/slowkoni/rfmix/blob/09599c19c69f81571c7a7aab4cd2d847a9e0633a/README.md) restrict use to academic research unless separately licensed. Existing session authorization covers this private research use. Keep original source/notices/binaries private. If required installation is unavailable or forbidden, skip that dependent upstream analysis with a prominent SKIP3 and instructions to supply a permitted runtime. This component performs no installation.

RFMix v1.5.4 is separate and remains pending a permitted original archive/provenance; its original download page redirected to Google login. P142's version remains unreported. P229's stated default 0.2 cM windows differ from the inspected source's default 50/5 SNP settings. The manual also labels RF-window `-r`, whereas the source uses `-s` and reserves `-r` for the reference file. Valued original long options require `--name=value`; separated values crashed the first retained invocations before data loading. A future adapter must expose explicit settings and actual source syntax.

## Java API

Use a source-built OmicsMatic JAR containing this class; existing release downloads predate the component. The API is source-compatible with Java 17 and was executed with Java 21 on Windows and Linux. The following four-locus contract is illustrative: replace every identity, locus and parameter with the independently established contract of the actual run.

```java
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.genetics.RfmixOutputAudit;
import org.jlinalg.genetics.RfmixOutputAudit.*;

public class AuditRfmix {
    public static void main(String[] args) throws Exception {
        var contract = new Contract("22", List.of("Q"),
            List.of(new Reference("RA", "A"), new Reference("RB", "B")),
            List.of(new Locus(100, 0), new Locus(200, .02),
                    new Locus(300, .04), new Locus(400, .06)),
            1.0, 0, false, false);
        var limits = new Limits(64L * 1024 * 1024, 1024 * 1024,
                                1_000_000, 10_000, 100, 100_000_000);
        var report = RfmixOutputAudit.audit(
            Path.of("ancestry.msp.tsv"), Path.of("ancestry.fb.tsv"),
            Path.of("ancestry.rfmix.Q"), Path.of("ancestry.sis.tsv"),
            contract, limits);
        System.out.println(report);
        for (String warning : report.warnings()) System.err.println(warning);
    }
}
```

The last four contract parameters are CRF spacing, requested maximum EM iterations, reference reanalysis and automatic CRF-weight selection. As in the inspected source, spacing below 1 means cM; spacing at least 1 increments an integer SNP index, truncating fractional increments. Prefer explicit integer SNP spacing to avoid unintended source truncation. For positive requested EM counts, the original Q output includes reference rows even though MSP/FB remain query-only. Reanalyzed-reference Q rows receive population, finite-probability and printing checks; query-only paths cannot independently reconstruct them, which the report warns about.

Save the example as `AuditRfmix.java`, supply the four matching original files, and compile against the source-built JAR:

```console
javac --release 17 -cp /path/to/omicsmatic.jar AuditRfmix.java
java -cp .:/path/to/omicsmatic.jar AuditRfmix
```

On Windows, use `;` instead of `:` in the classpath and quote it, for example `java -cp ".;C:\path\omicsmatic.jar" AuditRfmix`.

Limits apply per file, per line and to complete locus/sample/population/cell counts per table. Every table's actual parsed byte stream is hashed, checked against its original fingerprint and consumed completely. Files are checked again after the audit. Long lines and changed/partially read byte streams fail without a report.

## Evidence and remaining work

Twenty focused tests passed on each OS with no skips. Five complete original experiments cover two/three populations, count/distance windows, automatic CRF-weight calibration, EM and reference reanalysis. Five fresh runs from saved input copies reproduced all four original output files byte for byte. The native core audits matched exactly across Windows and Linux. Every one of 96,000 known synthetic haplotype states was compared with truth; 536 differences remain retained without changing genotypes, thresholds or model outputs. These are validation experiments, not a biological performance benchmark or proof of whole-tool equivalence.

The first native comparison assumed binary64 SNP-map storage and failed four genetic-distance cases. Its producing JAR, exact sources and diagnostics remain retained. The corrected component explicitly uses the source's binary32 storage, with a regression test. Five earlier private build attempts stopped during SDK setup; the successful build used original Ubuntu build prerequisites, private path configuration and original build auxiliary files. The source `AC_OUTPUT` and unused-variable compiler warnings remain retained; no `autoupdate` or scientific source patch was applied.

The [retained-evidence checker](../../tools/literature/verify_rfmix_output_component.py) requires the private evidence directory and matching source context. It checks retained evidence; it does not execute a new RFMix analysis or a new numerical replay:

```console
python -B tools/literature/verify_rfmix_output_component.py --evidence-root /path/to/rfmix-893
```

Missing/inaccessible private evidence yields SKIP3 and skips that verification. Restore the exact retained files and matching context or retain the skip. Changed evidence yields FAIL2; preserve it and investigate rather than refreshing old hashes. Old source contexts remain historical qualifications when later source changes occur.

Next work is the original execution/binding adapter, full raw-input contracts and audits, typed model/resource settings, failure/refusal tests, saved-input workflow replay and a guarded catalog transition. The four source reviews remain eligible but unapplied. Whole-tool qualification, exact author recipes and the full 250-paper implementation goal remain open. ConstructASCATFiles/PREASCAT runtime permission skips, Cell Ranger exclusion and MaxQuant runtime skip/pending decisions persist.
