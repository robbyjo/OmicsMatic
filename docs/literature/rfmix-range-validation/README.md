# RFMix interval formatting and source-range verification

Use the [RFMix workflow guide](../../rfmix-tools.md) with a JAR built from the current source. This successor fixes restricted-range argument formatting and checks the actual bp range printed by the original runtime. It preserves the [previous qualification](../rfmix-validation/QUALIFICATION.json), producing JAR, source archive and failed attempts. It adds **zero tool-family or paper-review credit**. The full 250-paper implementation goal remains active.

## Corrected behavior

The original RFMix v2.03-r0 parser splits `--analyze-range` at its first hyphen. Java's ordinary double formatting can write `0.0001` Mbp as `1.0E-4`; that exponent hyphen was then interpreted as the interval separator. The source might select different SNPs or fail. An output-population check alone cannot establish that a requested range was parsed correctly when the available genotypes happen to lie inside both ranges.

The adapter now writes plain round-trip decimal Mbp values, retaining the validated binary64 values and the source's multiplication by one million followed by signed-int truncation. For example:

```text
Requested: start_mbp=0.0001, end_mbp=0.00025
Original argument now used: --analyze-range=0.0001-0.00025
Required source notice: Analysis restricted to positions in range 100 to 250
```

It also requires exactly one original restricted-range notice and compares both printed bp coordinates with the independent native calculation. Missing, repeated, malformed, overflowing or mismatched notices fail with exit 2 and no complete result. Unrestricted requests require the absence of a restriction notice. No original RFMix source, input genotype, output table or threshold is repaired.

`OUTPUT-AUDIT.json` now records `source_execution.range_selection`, including the original notice, source internal range and positive-VCF selection domain. An unrestricted original runtime defaults internally to signed-int minimum through maximum. The input audit's existing `source_effective_range_bp` describes its eligible positive-VCF selection domain, from zero through signed-int maximum. These select the same admitted positive VCF positions; the output audit makes that distinction explicit.

## Use and verify

Continue using the same typed `rfmix-tools` request schema. Set `settings.range` to null for an unrestricted chromosome or to explicit numeric `start_mbp`/`end_mbp` values for an inclusive restricted interval. The captured request retains the numeric values; the source argument uses a plain decimal representation. Zero and negative zero designate the same physical coordinate. Empty intervals produce a visible FAIL2 rather than a fabricated ancestry result.

Existing bindings/captures still require their exact producing JAR. **Create a NEW runtime binding and NEW capture for the newly built JAR.** Do not replace an older JAR in place or update its saved hashes. The original Stanford academic-use notice, private-runtime requirements and Linux x86_64 execution requirement still apply. Source-built Java 21 is used for execution validation; no release or tag is created here.

```console
java -jar /absolute/new-source-built/omicsmatic-0.4.1.jar rfmix-tools --example binding > new-binding-request.json
java -jar /absolute/new-source-built/omicsmatic-0.4.1.jar rfmix-tools --bind new-binding-request.json --out /absolute/new-binding
java -jar /absolute/new-source-built/omicsmatic-0.4.1.jar rfmix-tools --request interval-request.json --out /absolute/new-capture
java -jar /absolute/new-source-built/omicsmatic-0.4.1.jar rfmix-tools --verify /absolute/new-capture --scratch /absolute/new-replay
```

Edit every example path and model parameter first. Set `interval-request.json`'s runtime to the new binding. Original input files remain captured unchanged. Missing or forbidden input/runtime/helper/index/platform prerequisites produce SKIP3 and skip the dependent analysis; supply the named prerequisite or install only where permitted. Invalid or changed evidence, parameters, model/QC, producer, runtime, cancellation or resource exhaustion produce FAIL2. Preserve the attempt and correct the cause before retrying NEW paths.

## Qualification and preserved history

[QUALIFICATION.json](QUALIFICATION.json) and its source archive identify the exact new producer and retained evidence. Qualification includes 46 native tests per OS; the eight complete original workflow cases from the predecessor rerun with the current JAR; five restricted-range captures and fresh replays; and five separate direct-original comparisons. Restricted cases cover a small positive start, fractional bp truncation, the smallest positive binary64 start, negative zero with flat bootstrap, and a compressed synthetic coordinate grid where both endpoints would ordinarily use exponent notation. The previous producing JAR's failure on that same short-coordinate case is retained with no complete result.

These are synthetic software qualification cases. The five complete core model output sets remain byte-identical to the retained direct-original baselines, including all 536 previously observed truth-state mismatches among 96,000 haplotype states. No biological-accuracy, statistical-convergence, author-revision or whole-paper claim follows. The new range cases do not establish a biological validation cohort.

Read-only current evidence validation:

```console
python -B tools/literature/verify_rfmix_range_workflows.py --private /absolute/retained/rfmix-895 --xml-cache /absolute/original/xml --upstream-source /absolute/original/rfmix-source
python -B tools/literature/rfmix_catalog.py --xml-cache /absolute/original/xml --upstream-source /absolute/original/rfmix-source
```

The first command verifies complete retained evidence populations/hashes, current code, original profile and exact derivation from the preserved predecessor context. It does not run new inference by default. With `--execute-replays --scratch /absolute/new-root --java /absolute/linux/java`, it performs all 13 current original-model replays on the bound host. The second validates the unchanged canonical catalog and complete original main-text source review. Metadata-only checks do not re-read original articles or execute inference.

The predecessor verifier recognizes this explicit successor, validates its current qualification and exact source changes, then retains its original historical interpretation. Running that verifier with the old private evidence still checks the original producer/captures. Previous seals are never refreshed. Source restoration for any later code change must use a NEW private matching context.

## Remaining skips

**SIS-dependent analysis remains SKIPPED.** The original writer does not certify final-iteration probabilities; preserve every original byte and warning. **P041's original v1.5.4 runtime remains skipped/unimplemented; this v2 adapter is not a substitute.** Exact author revisions, reference panels, supplementary recipes and downstream masking/dating remain separate. P142's reported version remains unreported. P229's reported 0.2 cM windows must not be silently equated with inspected current 50/5 SNP defaults. Cell Ranger remains excluded, MaxQuant runtime remains skipped/pending, and ConstructASCATFiles/PREASCAT code-use permission remains pending. Literature HTML reports remain withheld from Pages.
