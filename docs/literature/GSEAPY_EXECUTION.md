# GSEApy: local enrichment and ranked workflows

`org.jlinalg.cli.GseapyWorkflowCli` runs explicit original GSEApy classes and
checks their complete outputs with Java. It supports local Enrichr, preranked
GSEA, phenotype and gene-set GSEA, ssGSEA, and GSVA. Declare edition **1.0.4,
1.1.2, 1.1.3 or 1.1.8**. Edition 1.0.4 has no GSVA API.

This is a development adapter. Build its separate JAR; the canonical executable
has not been replaced. Original permutations, preprocessing and GSVA fitting
execute upstream. Java checks the captured scoring equations and conditional
null statistics, identifiers, full arrays and reports. It does not independently
generate permutations, fit GSVA, recover historical remote libraries or reproduce
a paper. Unreported paper settings must remain unresolved.

**Require exit code 0 AND `analysis-status.json` with `result_admitted: true`.**
Read `WARNINGS.md`, `native-audit.json`, original stdout/stderr and all process
receipts. A CSV file or a successful upstream worker alone is insufficient.

**SKIP3 means skip the dependent analysis.** Obtain the exact permitted Linux
runtime and its successful setup receipt, or retain the skip. Installation and
downloads need permission in the operating environment. RUN installs nothing.
Windows Java can export the kit, but analysis requires Linux x86_64. WSL is
supported through Linux Java and Linux paths.

**FAIL2 means no case from the requested batch is admitted, including earlier
completed cases.** Preserve the whole directory, correct the named input,
source, numeric-domain, runtime, timeout or capacity cause, and retry NEW paths.
Never refresh an old scientific binding hash to make a changed run pass.
If final receipts cannot be written, preserve the directory as unadmitted.

## Build and prepare the runtime

From the repository root, with the validated foundation development artifact
already present:

```powershell
python -B tools/literature/build_gseapy_execution.py --out build/literature-review/gseapy-runtime/NEW-EXECUTION-BUILD
$gseapyJar = 'build/literature-review/gseapy-runtime/NEW-EXECUTION-BUILD/omicsmatic-0.3.7-gseapy-execution-development.jar'
java -cp $gseapyJar org.jlinalg.cli.GseapyExecutionSetupCli --export build/NEW-GSEAPY-KIT
```

The builder uses the exact artifact and source pins recorded in the
[foundation proof](gseapy-workflow-foundation/VALIDATION.json). If it is absent,
follow the retained foundation build history; do not substitute an unrelated
JAR or update that proof. Export requires a NEW directory under an existing
parent and installs nothing. Read the exported `SETUP.md` and the
[base runtime guide](CELLPHONEDB_WORKFLOW_DEVELOPMENT.md).

The kit includes four original wheels and their licenses. It needs the exact
read-only Python 3.11.17 scientific base and successful base setup receipt;
no CellPhoneDB analysis is needed. From the exported kit on Linux:

```sh
/ABS/BASE/bin/python -I -B setup/prepare.py \
  --base-runtime /ABS/BASE-SETUP/COMPLETED.json \
  --prefix /ABS/NEW-CASE-SENSITIVE-PREFIX \
  --out /ABS/NEW-SETUP-RECEIPTS --allow-install
```

Use `--allow-install` only where installation is permitted. Intact bundled
wheels allow offline setup. For a missing permitted archive, supply the exact
`--archive-cache`, or use `--allow-download` where allowed. If installation is
forbidden or dependencies cannot be obtained, retain SKIP3 and skip analysis.
A damaged kit is FAIL2: preserve it and export a NEW intact kit. Do not modify
the shared base. Require setup exit 0 and
`PASS_BOUND_ORIGINAL_GSEAPY_WORKFLOW_RUNTIME_ONLY` in `COMPLETED.json`.

## Run the six-gene example

The example creator writes a local prepared library and an explicit request.
It runs no analysis. On Linux, replace the absolute paths below with the
development JAR and your successful GSEApy setup receipt:

```sh
python3 -B tools/literature/create_gseapy_example.py \
  --runtime /ABS/GSEAPY-SETUP/COMPLETED.json --out build/NEW-GSEAPY-EXAMPLE
java -Xmx2g -cp /ABS/DEVELOPMENT.jar org.jlinalg.cli.GseapyWorkflowCli \
  --request "$PWD/build/NEW-GSEAPY-EXAMPLE/request.json"
echo "$?"
cat build/NEW-GSEAPY-EXAMPLE/result/analysis-status.json
cat build/NEW-GSEAPY-EXAMPLE/result/WARNINGS.md
```

On Windows, the creator's `--path-style wsl` explicitly converts drive paths
to `/mnt/<drive>/...`. Supply the runtime receipt as a Linux path, then run the
same Java class through your WSL distribution. Do not pass Windows paths to
Linux Java. Each retry needs a NEW example directory.

The example declares GSEApy 1.1.8, query A/B, background A–F and three local
sets. Two sets have positive overlap; the zero-overlap set is excluded by the
original source before BH correction. It is a synthetic workflow example,
not evidence that any gene family or enrichment threshold is biologically valid.
The original full table is `result/worker/local-enrichr/full-result.tsv`.

## Prepare another analysis

Edit a NEW request and input, keeping the existing example as evidence. Requests
use `schema_version: 1`, `mode: "RUN"`, an exact edition, successful runtime
receipt, NEW output directory and a nonempty `analyses` array. Each case declares
a unique portable ID, method, `interface: "original-class"`, global seed,
all scientific options, input path/format/source and all three policies:
`source_input_changes`, `nonfinite_diagnostics`, `empty_results`. Each policy is
`"fail"` or `"retain-with-warning"`; no option is inferred from a paper title.

Prepared inputs are data-only JSON with `schema_version: 1`,
`numeric_dtype: "float64"` and a local `gene_sets` mapping from literal term
labels to lists of literal gene labels. No pickle, remote Enrichr library name
or automatic identifier repair is accepted.

| Method | Required input | Explicit options |
| --- | --- | --- |
| `enrichr` | `query` list; `background` list, integer population or null for library union | organism, cutoff |
| `prerank` | ids, one score-column label, rectangular values | min_size, max_size, weight, threads, seed, permutation_num, ascending |
| `gsea` | ids, sample-column labels, rectangular values, two-level classes in sample order | prerank options plus permutation_type and ranking method |
| `ssgsea` | ids, sample-column labels, rectangular values | prerank options plus sample_norm_method and correl_norm_type |
| `gsva` | ids, sample-column labels, rectangular values | min_size, max_size, weight, threads, seed, kcdf, mx_diff, abs_rnk |

For ranked/matrix inputs the keys are `ids`, `columns`, `values`; GSEA also uses
`classes`. Finite float64 input is required; lexical nonzero underflow is
rejected. Quoted Unicode, whitespace, tabs and newlines in identifiers are
preserved. Source aggregation/filtering/case conversion must pass the declared
policy. Limits bound inputs, outputs, file count, worker time, matrix cells and
native operations; see the exact
[request parser](../../src/main/java/org/jlinalg/cli/GseapyWorkflowRequest.java).
The JSON parser also has a four-million-node bound. Supervision samples owned
processes, wall time and output limits; it is not hard OS CPU/memory containment.

## Source conventions that affect interpretation

- Local Enrichr uses a **one-sided upper hypergeometric tail**. Edition 1.0.4
  has different odds and report columns. BH uses the source tested family;
  no zero-overlap terms are added. Numeric backgrounds trigger an original
  high-level TypeError in 1.1.8; use an explicit gene universe when appropriate,
  or preserve that failure. Native population statistics remain a separate API.
- The original 1.0.4 ssGSEA convenience wrapper ignores several named settings.
  This adapter calls the unchanged original classes and binds constructor
  options. Zero-permutation None-to-zero bookkeeping is retained.
- All four editions use the fast ssGSEA sum equation, including permuted
  ssGSEA. Original SymRank computes an expression without assigning its result;
  that no-op is retained and warned. The upstream warning against publishing
  permuted ssGSEA P/FDR results remains applicable.
- Phenotype GSEA in 1.0.4/1.1.2/1.1.3 records raw signed curves while ES uses
  absolute power weights; 1.1.8 weights before both. Leading genes follow the
  original recorded curve. The original phenotype backend sorts descending,
  including when the constructor asks for ascending order; policy governs this
  source change. Do not replace curves or annotations to force agreement.
- Original Poisson GSVA clips negative values after preprocessing. Raw negative
  counts in warnings do not establish the exact post-aggregation clipping count.
  Its kernel fitting remains upstream; native report coherence is not a refit.
- Zero-permutation GSEA/prerank NES slots are uncomputed placeholders. They do
  not establish normalized significance. P/FDR/FWER fields are not invented.
- Original zero, NaN and Inf states are retained under explicit policies.
  Native normalized-null auditing requires finite sort inputs; an unsupported
  family fails the whole batch and must not be claimed independently audited.
  There is no epsilon, add-one P correction, generic BH substitution for ranked
  FDR, automatic monotonic adjustment or independent permutation calibration.

The [execution proof](gseapy-execution-development/VALIDATION.json) records the
exact tested artifact, sources, positive workflows, error/lifecycle controls
and retained failed attempts. Hashes establish integrity and reproducibility,
not authenticity, biological validity or whole-paper reproduction. The larger
250-paper implementation goal remains active. Cell Ranger remains excluded.

The current checkpoint passes 90 fresh original-class workflows: 18 for 1.0.4
and 24 each for 1.1.2, 1.1.3 and 1.1.8. Their complete native audits replay on
Windows Java 21/25 and Linux Java 21. The fresh runs check 40,420 numeric,
111,240 identity and 5,410 TSV states. Eighteen portable contracts, 36 scientific
input rejections, 15 corrupted output families, seven real source/policy/process
failures and the exact six-gene example also pass. Failed earlier attempts stay
failed; replay never changes their original admission status.
