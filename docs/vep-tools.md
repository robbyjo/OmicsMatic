# VEP consequence workflows

`vep-tools` provides portable native Java extraction of complete VEP VCF annotations and an original Ensembl VEP105.0/API105 Linux execution adapter. The Java component retains every variant, sample/genotype token, CSQ field and transcript record. It groups annotations by variant row, explicit alternate-allele number and gene, retaining all features and consequence terms. It also exposes the 40 original API105 consequence ranks/impact facts through `VepConsequences105`. Consequence prediction and transcript selection use the original VEP runtime.

This qualification covers `vep-tools`. The existing `variant-consequence --engine vep` integration retains its separate behavior and scope.

**Require exit0 and `analysis-status.json` with `results_admitted: true`, then a successful fresh `--verify`.** Read `WARNINGS.md`, `execution.json`, and every original stdout/stderr/warning file. **SKIP3 / exit3** skips dependent analysis: restore the named exact permitted prerequisite, or retain the skip. **FAIL2 / exit2** admits no complete batch result: keep the failure, partial files and logs, correct its named input/integrity/process/capacity cause, and retry a new output directory. Do not use earlier completed cases from a failed batch, repair alleles or missing values to pass, or replace software/cache editions. Existing output is preserved.

## Native extraction

Build the current source with `./gradlew executableJar`; an earlier released JAR does not acquire this command automatically. With Java17 or newer on Windows or Linux:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "backend": "NATIVE",
  "out": "annotation-ledgers",
  "original_vcf": "complete-unannotated-input.vcf.gz",
  "annotated_vcf": "complete-original-vep-output.vcf"
}
```

```sh
java -Xmx1g -jar build/cli/omicsmatic-0.4.1.jar vep-tools --request request.json
java -Xmx1g -jar build/cli/omicsmatic-0.4.1.jar vep-tools --verify annotation-ledgers
```

Paths in a request resolve against that request's directory. Gzip/BGZF input is detected by its bytes. `original_vcf` is optional; when supplied, every original row and every non-CSQ column must survive, including IDs, REF/ALT, QUAL/FILTER, FORMAT and sample tokens. Non-CSQ INFO key/value pairs must agree; changed INFO ordering is recorded. Existing input CSQ annotations are refused instead of overwritten. Without `original_vcf`, extraction cannot establish preservation against an earlier input.

CSQ requires unique field names and explicit `Allele`, `Consequence` and `ALLELE_NUM`; allele-string matching is not used to guess a multiallelic assignment. For VEP's consequence-only `--most_severe`/`--summary` output, explicitly set `source_summary_mode: true`. Blank source allele numbers then remain **unassigned**; empty gene, feature and IMPACT remain empty. The extractor never supplies ALT1, derives missing impact from a rank, or calls missing annotation benign/no effect. All raw percent-encoded fields, order and duplicate annotations are preserved. It performs no normalization, liftover, canonical selection, identifier repair, frequency filtering or loss-of-function mask construction.

`results/native` contains five complete derived files:

| File | Contents |
| --- | --- |
| `variants.tsv` | Every literal VCF data row; annotation/uncertainty counts and observed impacts by alternate allele |
| `annotations.tsv` | Every CSQ record, its unchanged raw text, all declared fields and explicit allele assignment |
| `genes.tsv` | Every variant–allele–gene group, all distinct features/consequences, observed impacts and missing-impact count |
| `schema.json` | Original columns, complete CSQ field order and original VEP version headers |
| `summary.json` | Complete populations, missing/unassigned counts and scope |

A maximum **observed** IMPACT summarizes supplied annotations; missing annotations remain unknown. Consequence ranks can tie across different impact classes. `minimumRankTerms` preserves all minimum-rank ties and does not implement VEP `--pick`. Unknown API105 terms passed to its rank/impact fact methods fail instead of receiving an inferred low rank. Native CSQ extraction can retain unknown consequence text without assigning it a rank.

## Original VEP105

Original execution uses the exact private Linux x86_64 dependency/host closure in [RUNTIME.json](../src/main/resources/org/jlinalg/omics/vep105/RUNTIME.json). It runs offline and does not install software during an analysis. Missing or forbidden installation/resource access leaves dependent analysis skipped. A changed exact runtime/input/host is a failure requiring the named cause to be resolved.

The older [LOFTEE workflow](literature/LOFTEE_WORKFLOWS.md) keeps its own guards. Its original host profile is **not refreshed** by the new core qualification. On a host that differs from that older profile, retain the refusal or use its exact isolated qualified host; do not downgrade shared libraries or change hashes to force admission.

When the exact original VEP/BioPerl dependency files are already available, prepare a new private core without changing them:

```sh
python3 tools/literature/prepare_vep_runtime.py \
  --environment /permitted/exact-vep105/environment \
  --bioperl /permitted/exact-bioperl \
  --out /short/private/vep105-core --allow-prepare

python3 tools/literature/prepare_vep_example.py \
  --runtime-home /short/private/vep105-core --out build/vep-example
java -Xmx1g -jar build/cli/omicsmatic-0.4.1.jar vep-tools \
  --request build/vep-example/request.json
java -Xmx1g -jar build/cli/omicsmatic-0.4.1.jar vep-tools \
  --verify build/vep-example/analysis
```

The runtime preparer checks exact host/source bytes and enumerated prefix-relocation fields, copies the original `which.debianutils` helper, and runs a VEP help check. It downloads nothing and runs no variant analysis. Its health success is not variant qualification. Other versions/hosts require separate qualification; preserve its `WARNINGS.md`/`COMPLETED.json` and correct the specific problem before retrying new paths. The example retains the entire project-synthetic15-row/two-sample/five-transcript/4000-base input. Its12 analyses are software controls, not biological data.

An original request uses `backend: "ORIGINAL"`, `profile: "original-VEP105.0-API105-linux64"`, `input_vcf`, `runtime: {"environment": "...", "bioperl": "...", "helpers": "..."}` and an explicit `reference`:

| Reference | Required fields and behavior |
| --- | --- |
| `kind: "gtf"` or `"gff"` | Complete BGZF `annotation`, uncompressed `fasta`, matching complete `fai`, explicit `species`/`assembly`, `index_mode: "generate-from-captured-BGZF"`. A fresh original tabix index is generated inside the capture. No old index is overwritten or assumed to match. |
| `kind: "human-cache105-GRCh38-ensembl"` | `bundle` and explicit `trust: "verified-original-ensembl-resource-bytes"`. This permits only the entire byte-bound original Ensembl105 human GRCh38 cache and complete primary-assembly FASTA/index declared by the packaged reference profile. No release84 test cache, RefSeq/merged cache, reduced chromosome panel or relabelled resource is substituted. |

Original cache deserialization is restricted to those exact original resource bytes. The large immutable reference bundle remains external, with all file identities captured and checked before/after execution; it is retained for replay. GTF/GFF/custom resources and ordinary VCF inputs are copied into each capture. GTF/GFF basenames are retained because original VEP uses them in SOURCE values and CSQ column names. Supplied GTF/GFF calls use the pinned source default `database=0`; the `--offline` flag is used for cache calls because VEP105's flag also enables cache lookup. Before execution, every original REF is checked against the exact indexed FASTA without contig aliasing, assembly conversion or allele swaps. N-containing comparisons remain explicitly unassessable; non-N mismatches fail. Assembly labels are assertions rather than independent biological certification. Shell-backed original lookup requires portable ASCII paths and literal contig/allele representations; unsupported names are refused without rewriting them.

For human-cache preparation, download the complete [original105 indexed cache](https://ftp.ensembl.org/pub/release-105/variation/indexed_vep_cache/homo_sapiens_vep_105_GRCh38.tar.gz) and [original primary-assembly FASTA](https://ftp.ensembl.org/pub/release-105/fasta/homo_sapiens/dna/Homo_sapiens.GRCh38.dna.primary_assembly.fa.gz) only where permitted. Expected full-archive identities and every extracted file identity are in the packaged `REFERENCE.json`; allow storage for both original archives, the entire cache, the uncompressed genome and required runtime. Extract **all** cache members under a new `bundle/cache`, decompress **all** primary FASTA bytes to `bundle/genome.fa`, and run the exact prepared runtime's `samtools faidx` to create `bundle/genome.fa.fai`. Do not select chromosomes, rename contig headers, copy a different index or substitute a RefSeq/merged cache. Then bind only an exact complete tree:

```sh
python3 tools/literature/verify_vep_reference.py --bundle /new/private/bundle \
  --prepare-profile --allow-prepare
python3 tools/literature/verify_vep_reference.py --bundle /new/private/bundle
```

The helper reads every expected file and refuses different, missing, extra or linked files. It writes only a previously absent `PROFILE.json` with the packaged exact bytes; it runs no annotation and does not authenticate a coordinated replacement. If installation/download is forbidden or unavailable, retain the named SKIP3 instead of starting dependent analysis. Preserve download/preparation errors and original input archives.

The fixed original cache reports GENCODE39, GRCh38.p13, SIFT5.2.2, PolyPhen2.2.2, gnomADr2.1.1 and ClinVar202106. Those are the actual cache data editions, and are not inferred to match a paper's unreported or differently dated resources.

Supply1–16 unique `analyses`, each with `id` and `options`. Options support explicit `transcript_mode` values `all`, `pick`, `pick_allele`, `pick_allele_gene`, `per_gene`, `flag_pick`, `flag_pick_allele`, `flag_pick_allele_gene`, `most_severe`, `summary`; compatible explicit `pick_order` among the10 original criteria; `sift`/`polyphen` (`none`, `b`, `s`, `p`); symmetric `distance`; and `max_sv_size`. Presence flags accept **true or omission**: `canonical`, `mane`, `biotype`, `symbol`, `protein`, `numbers`, `ccds`, `domains`, `hgvs`, `hgvsg`, `variant_class`, `check_existing`, `af`, `af_1kg`, `af_gnomad`, `max_af`, `regulatory`, `gencode_basic`, `tsl`, `appris`, `uniprot`, `minimal`, `no_intergenic`, `coding_only`, `total_length`, `check_ref`. Omission retains original defaults/auto-enabling; it does not claim an option is disabled. Invalid original summary-mode combinations and cache-only predictions on supplied GTF/GFF are refused.

Optional `custom_annotations` name local VCF/BED/BigWig files with explicit `format`, `type: "exact"`/`"overlap"`, `coordinates` and a `fields` array for VCF INFO values. BGZF VCF/BED require `index_mode: "generate-from-captured-BGZF"`; their new indexes are generated from exact captured data without sorting or altering records. Original VEP decides annotation semantics. A nonempty `plugins` request returns SKIP3 because this core profile does not qualify arbitrary plugin code/resources. LOFTEE, SIFT/PolyPhen lookup data, custom ClinVar editions and author masks remain distinct prerequisites/workflows.

Explicit `threads` defaults to1 (`--fork` only above1), `buffer_size` to5000, and `perl_hash_seed` to hexadecimal `"0"` with original `PERL_PERTURB_KEYS=0`. These are declared software-control settings, not inferred author seeds. All original commands, process exit codes, owned-descendant cleanup receipts and stdout/stderr/warnings are retained. Each analysis has its original `worker/ID/original.vcf` plus all five ledgers under `results/ID`.

## Capacities, verification and source scope

The closed request accepts explicit complete-file capacities: input bytes (default8GiB), resource bytes (256GB), output bytes (16GiB), output file count (100,000), decoded VCF characters (32Gi), line characters (8Mi), variants (10million) and annotations (100million); original process timeout defaults to3600seconds. Original runtime/reference integrity checks are additional work outside that per-process timeout. All cases use complete inputs; hitting a bound fails without truncation, pruning or an automatic smaller rerun. Output bounds are monitored rather than OS-enforced quotas. RAM/process limits can still fail; retain that failure. Increase a declared capacity only when appropriate for the complete intended analysis and available resources.

`--verify` checks capture population/hashes and the current packaged source bindings, then recomputes **every byte of all five ledgers** from the retained literal VCFs. It checks the reference-check ledger against original input rows and its declared uncertainty states. It does not run Perl, load Storable cache objects, reread the external reference/runtime, independently predict consequences or certify clinical significance. Hashes establish integrity, not authentication against coordinated replacement. Failed or skipped captures cannot be verified as complete.

The [38 primary-source roles](literature/vep-source-audit/ROLES.json) distinguish actual VEP editions, species, assemblies, RefSeq/custom resources, plugin hosts, per-gene annotation units and downstream selection/masks. The105 core is one scoped family; its native components do not earn duplicate family credit. Other historical/current versions, all source plugins, the complete Vcftomaf/vcf2maf/WGSA/BRaVa recipes, author inputs and full paper analyses remain outside this qualification. The250-paper implementation goal remains open.
