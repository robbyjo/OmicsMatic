# Prepare and run the original LDAK tutorial

This example prepares the upstream tutorial data with the pinned original
LDAK **5.2** runtime, then generates two explicit requests: SumHer and
thinning/kinship/REML with an illustrative alpha grid. These are tutorial
controls. They do not reproduce an author's cohort or BLD-LDAK reference.

**Missing or forbidden prerequisite: SKIP3; skip its dependent analysis.**
Use a permitted Linux x86_64 environment, including WSL when allowed, and
supply the named prerequisite. **Other errors: FAIL2; no complete result.**
Keep the failed directory, read `FAILURE.json` or `FAILURE.txt`, `WARNINGS.md`
and both original consoles, resolve the reported cause and use a new output
directory. Never edit saved inputs, results or hashes to obtain a pass.

## Quick start

Run these commands in a Linux/WSL shell from the current source checkout.
The original executable cannot run directly under Windows. Build with Java 17
or later; use Python 3.11 or later for preparation. This command is not present
in the released OmicsMatic 0.4.0 JAR.

```bash
./gradlew executableJar

# Separately prepare the exact original runtime; this is not an analysis.
python3 -B tools/literature/ldak_original/setup.py \
  --allow-download --out build/ldak52-runtime

# Allow the pinned tutorial-data download; no runtime is installed here.
python3 -B examples/ldak-workflow/prepare.py \
  --runtime build/ldak52-runtime \
  --allow-download --out build/ldak-tutorial-inputs

java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar ldak-tools run \
  --request build/ldak-tutorial-inputs/sumher.properties \
  --out build/ldak-sumher-run
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar ldak-tools verify \
  --bundle build/ldak-sumher-run --out build/ldak-sumher-verify

java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar ldak-tools run \
  --request build/ldak-tutorial-inputs/thin-reml.properties \
  --out build/ldak-thin-reml-run
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar ldak-tools verify \
  --bundle build/ldak-thin-reml-run --out build/ldak-thin-reml-verify
```

Each output path must be new. Preparation success creates `COMPLETED.json`
with status `READY_ORIGINAL_TUTORIAL_REQUESTS_NOT_COMPLETED_ANALYSES`.
It runs original linear association and tagging preparation, captures every
result and diagnostic, and creates the two requests. It does **not** execute
SumHer or REML. Each later workflow must independently return exit 0, create
`COMPLETED.properties`, and pass verification.

Preserve the exact application JAR used for a bundle. `verify` requires matching
method bytecode and works on Windows as well as Linux. It checks complete
membership/hashes and regenerates native audits without executing LDAK.
`replay` actually executes the captured original workflow again in Linux:

```bash
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar ldak-tools replay \
  --bundle build/ldak-thin-reml-run --out build/ldak-thin-reml-replay
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar ldak-tools verify \
  --bundle build/ldak-thin-reml-replay --out build/ldak-thin-reml-replay-verify
```

## Offline preparation

Use the exact archives listed in the
[runtime profile](../../tools/literature/ldak_original/source-profile.json) and
[tutorial profile](tutorial-profile.json). Their size, SHA-256 and archive
membership are checked. Neither invocation permits downloading when
`--allow-download` is omitted.

```bash
python3 -B tools/literature/ldak_original/setup.py \
  --archive /path/to/ldak5.2.linux.zip --out build/ldak52-offline
python3 -B examples/ldak-workflow/prepare.py \
  --runtime build/ldak52-offline \
  --archive /path/to/data.zip --out build/ldak-tutorial-offline
```

The prepared runtime is checked and copied without changing its source
directory. The complete 1,552,339-byte tutorial archive is retained. Five pinned
inputs are extracted for these workflows: `human.bed`, `human.bim`, `human.fam`,
`human.covar` and `quant.pheno`. Their original bytes remain unchanged; the
tutorial FAM's `NA` sex tokens are preserved. This selection of example files
does not truncate the human BED dataset's 424 samples or 3,289 variants.

## Model choices and warnings

The SumHer tutorial uses **unit weights, power -0.25 and a 1,000 kb tagging
window**, with a single category. It is not a substitute for the 66-category
BLD-LDAK reference. The generated quantitative-trait request has no liability
conversion, no inflation adjustment and no cutoff, with the original maximum
likelihood solver. Ambiguous alleles are explicitly allowed so that the
tutorial's complete summary/tagging sets can be checked together.

The original tutorial SumHer run replaces 18 zero statistics with `1e-6` and
floors four tagging values at one. The generated request explicitly uses
`upstream_adjustments=allow-recorded`. Review the exact warnings and counts in
the accepted workflow's `WARNINGS.md`, `DIAGNOSTICS.tsv` and input audit.
To refuse these replacements, make a separate request with
`upstream_adjustments=forbid` and new output; this tutorial then returns FAIL2
after original execution and retains the failed attempt.

The genotype request explicitly uses pruning r-squared 0.98 within 100 kb,
unit weights, HWE standardization, constrained REML and alpha values
`-0.5,-0.25,0`. Its declared selection uses the largest original printed
alternative likelihood, taking the first grid position on a printed tie.
This illustrative grid does not recover an unknown paper grid or tie policy.

Native Java independently checks every kinship entry and the declared liability
conversion, and checks thinning set handoff and original model reports. It
does not independently refit SumHer/REML or implement the thinning algorithm.
Keep original unconstrained negative estimates and every warning. A finite,
converged tutorial estimate is not evidence of a real biological finding.

Read the [workflow scope and exact request keys](../../docs/literature/LDAK_WORKFLOWS.md)
before creating a study request. Supply the actual model, reference ancestry,
sample sizes, prevalence/ascertainment and genotype coding; the adapter does
not infer them. LDAK 5.2 has no supported SumHer `truncate` option in this
profile, and no seed flag is fabricated.
