# Snakemake workflow commands: development snapshot

**WARNING: qualification of the new public workflow commands is incomplete.
The current source compiles and passes its Java command tests, but the new
filesystem checks, output protection, ZIP replay and command failure handling
still need their complete original-engine qualification. Do not treat these
commands as validated paper analyses or additional catalog integration credit.**

The current source exposes these commands:

```text
snakemake-tools run --request RECIPE.json --runtime ADOPTION --out NEW
snakemake-tools verify --bundle DIRECTORY_OR_ZIP --out NEW
snakemake-tools replay --bundle DIRECTORY_OR_ZIP --runtime ADOPTION --out NEW
snakemake-tools benchmark --help
```

Run `java -jar PATH_TO_CURRENT_BUILD.jar snakemake-tools --help` for the current
requirements and warnings. The published v0.4.0 release does not contain the new
workflow commands. The native benchmark component has its own validated scope
in [SNAKEMAKE_BENCHMARKS.md](SNAKEMAKE_BENCHMARKS.md).

Execution and replay require the selected Snakemake 9.27.1 runtime adoption,
Linux x86-64, Java 21 or later, a native POSIX output filesystem, and a complete
explicit recipe. Managed recipes additionally require the selected qualified
Conda/Apptainer providers and explicit cache declarations. Installation follows
the recipe's `allow_install` setting. The command does not substitute software
found on PATH or relocate an installed Conda prefix. Use trusted workflow code;
these commands are not a security sandbox.

**SKIP3 means the dependent analysis was skipped.** Read stderr and any retained
`FAILURE.json` and `FAILURE_WARNINGS.md`. Restore the named permitted runtime,
provider or cache, or use a permitted setup procedure, then retry with a new
output directory. An unsupported output filesystem requires a native Linux
location. If installation is forbidden, leave the analysis skipped.

**FAIL2 admits no completed result for the failed command.** Preserve inputs,
partial outputs, original diagnostics, invalidated completion markers and failed
attempts. Correct the named discrepancy and retry a new output directory.
Argument and overlap failures can occur before an output directory is created;
their explanation is on stderr. A successful nested parent does not replace a
failed replay. Verification checks captured evidence, not biological accuracy,
authorship or reproduction of a paper.

The earlier private bundle and replay qualifications remain documented in
[SNAKEMAKE_ORIGINAL_DEVELOPMENT.md](SNAKEMAKE_ORIGINAL_DEVELOPMENT.md) and
[SNAKEMAKE_DEPLOYMENT_DEVELOPMENT.md](SNAKEMAKE_DEPLOYMENT_DEVELOPMENT.md).
Those receipts apply to their frozen code editions. Use their matching reader;
never update historical hashes to admit changed code. Their results do not
establish qualification of this newer public-command edition.
