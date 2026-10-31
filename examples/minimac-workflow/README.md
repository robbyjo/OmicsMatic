# Typed Minimac workflows

These recipes use the repository's synthetic chromosome-20 fixture: 24 diploid
reference samples, six targets, 120 reference markers and 31 typed markers.
The identity TSVs explicitly declare the fixture's people and ordered copies.
They must not be reused as identities for another dataset. No participant data,
real assembly, biological eligibility or imputation accuracy is established.

Use a development JAR containing `minimac-tools`; see
[the operation and warning contract](../../docs/literature/omics-2023-2026/MINIMAC_WORKFLOW.md).
The original processes require Linux x86_64 Java. On Windows use Linux Java in
WSL and Linux paths. Native text inspection also works with Windows Java.
Run commands from the repository root. Set `JAR` to the built JAR and `HOME4`
to the explicitly admitted current Minimac runtime; these are shell variables,
not changes to the operating-system HOME. Select a new output for every run.

```sh
java -jar "$JAR" minimac-tools --help
java -jar "$JAR" minimac-tools --recipe examples/minimac-workflow/inspect-min3.json \
  --out examples/minimac-workflow/work/native-inspection
java -jar "$JAR" minimac-tools --recipe examples/minimac-workflow/prepare-current.json \
  --home "$HOME4" --out examples/minimac-workflow/work/current-reference
java -jar "$JAR" minimac-tools --recipe examples/minimac-workflow/check-current.json \
  --home "$HOME4" --out examples/minimac-workflow/work/current-check
java -jar "$JAR" minimac-tools --recipe examples/minimac-workflow/impute-current.json \
  --home "$HOME4" --out examples/minimac-workflow/work/current-imputation
```

The current inspect/check/impute recipes consume `work/current-reference/`,
produced by the preparation command above. Current preparation preserves the
original pinned defaults; custom block parameters cannot be honored by this
upstream version and produce a reported skip. Conversion is a separate command:

```sh
java -jar "$JAR" minimac-tools --recipe examples/minimac-workflow/convert-current.json \
  --home "$HOME4" --out examples/minimac-workflow/work/converted-reference
```

Historical `impute-legacy102.json`, `impute-legacy100.json` and `impute-min3.json`
consume the supplied original M3VCF fixture. Supply each recipe's matching
admitted runtime, including its explicit private QEMU/GOMP profile where needed.
Do not substitute the current runtime for a historical request. The matching
`check-*.json` recipes validate inputs without imputation. `prepare-min3.json`
explicitly runs the original Min3 reference estimator; its result has flat
haplotype columns with a newly verified original-person/copy TSV.

**Always read `WARNINGS.md` and its Required action.** Exit 3 means a missing,
forbidden or unsupported dependency/capability skipped the operation. Exit 2
means input, resource, process or result-audit failure. Provisional files move
under `partial/`; they are not completed analysis results. Exit 0 still requires
scientific review. Missing runtimes are never installed by an analysis command.
Where permitted, use `python3 tools/literature/install_minimac.py --help` for
separate private installation; if installation is forbidden, skip the affected
analysis until an administrator supplies a supported admitted runtime.

Raw posterior strings remain unchanged in `genotypes.vcf`. The example requests
no post-imputation QC. To choose source INFO/R2 filtering explicitly, use a
separate recipe with `kind: source-INFO-R2`, `operator: gt` or `ge`, a decimal
**string** `minimum`, and a declared missing-value policy. Native stable HDS/R2
diagnostics never replace source R2 or reconstruct ordered HDS from legacy GP.
Review `variant-results.tsv`, `genotype-changes.tsv`, `variant-join.tsv`, the
reference/target audit files, immutable snapshots and original process logs.
Cell Ranger remains excluded.
