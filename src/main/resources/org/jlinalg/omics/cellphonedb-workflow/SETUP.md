# CellPhoneDB5.0.1 development runtime and analysis

This kit contains the exact original MIT-licensed software, source notices, safe data-only worker, and dependency locks. No curated interaction database or biological dataset is bundled. Java17+ can export it; original analysis requires Linux x86_64, including WSL. A case-sensitive filesystem is required for the Python environment. Use a new directory on a normal Linux filesystem if the Windows-mounted workspace is case insensitive.

Export from the development JAR (replace `DEVELOPMENT.jar` with its actual filename):

```sh
java -cp DEVELOPMENT.jar org.jlinalg.cli.CellPhoneDbSetupCli --export /absolute/new-kit
```

Run the exported installer using system Python3.11 or newer. All paths must be absolute, canonical, and new where indicated. The micromamba2.10.0 Linux binary must be19028608bytes with SHA256 `701f39c06f8e6a670a0e186c8479b9e5c0502a2f0cda6cef65d83bdd2875e8bd`. Obtain that exact installer through an approved channel; no alternate installer/version is silently used. The installer checks the binary before executing it.

```sh
python3 -I -B /absolute/new-kit/setup/prepare.py \
  --out /absolute/new-setup-receipt \
  --prefix /absolute/new-runtime \
  --micromamba /absolute/permitted/micromamba \
  --allow-install --allow-download
```

`--allow-install` explicitly permits installation. `--allow-download` additionally permits missing locked archives to be downloaded from the recorded official Conda/PyPI URLs. For permitted offline installation, omit `--allow-download` and supply one or more `--archive-cache /absolute/cache` directories containing the exact named, checksummed archives. The43Conda archives and49Python source archives are fully locked. Python source wheels are built with the locked installed build tools, without network dependency resolution. No shared environment is modified. Keep the setup receipt, full inventories, source capture, install logs and WARNINGS.md together; analysis verifies the entire environment against them.

**SKIP3: analysis must remain skipped** if installation/download is forbidden, a required archive or installer is unavailable, the filesystem is unsuitable, or setup does not pass. Read the specific error and required action in the setup WARNINGS.md; obtain permission or the exact missing archives, choose a suitable NEW prefix/output, and retry. Do not fabricate a receipt, refresh its hashes, or run analysis on a partially installed environment.

**FAIL2: no requested batch result is admitted** for input/source failures, incompatible result assembly, changed runtime/inputs, audit discrepancies, cancellation, or exceeded capacities. Earlier successful cases remain provisional. Preserve the entire failed directory and original inputs, resolve the named cause, and retry NEW paths. Only exit0 together with `analysis-status.json` `result_admitted=true` admits the entire batch. Read the analysis WARNINGS.md and all original logs before use.

RUN uses a typed JSON request through `org.jlinalg.cli.CellPhoneDbWorkflowCli --request /absolute/request.json`; `--help` explains its admission contract. Inputs are explicitly declared CSV/TSV counts or H5AD X, CSV/TSV metadata/optional tables, and a supplied data-only database ZIP. Provide normalized expression appropriate to the original method; the adapter does not normalize or fit DEGs. Every threshold, precision, separator, seed, thread count and statistical/subsampling option must be declared. No reported5.1/5.0.0/2.1.4 edition is substituted by5.0.1. Gene synonyms/species conversion and arbitrary Python/pickle input formats are unsupported. Original input bytes and parser interpretations remain retained; metadata coercion that changes identifiers/labels fails admission.

Original5.0.1 `Subsampler` is initialized with `debug_seed=None` by the full statistical method and reseeds NumPy from entropy. Global/permutation seeds do not make its selection deterministic. It also catches internal sampling errors and returns full counts. Choose `statistical.subsampling.failure_policy="fail"` to reject that fallback, or explicitly choose `"retain-original-full-counts"` to preserve it with a prominent warning. Actual selected cells, errors, RNG states and source output remain recorded. Multi-process null draws are original upstream behavior; serial/parallel byte reproducibility is not promised.

The native audit recomputes mapping, float32 compensated gene means, filters, complex minima, observed cluster/interaction means, expression fractions, strict gates and supplied-DEG flags. It derives P values from every actual packed null-comparison bit with strict `>observed`, no plus-one correction, and source zero/gate rules. It checks every exported table cell against its typed source return. Original scoring, CellSign, random draws and subsampling geometry remain upstream implementations. Simple exported means are ungated; source P cutoff0 is falsy. Original UserWarning suppression is retained. Debug pickle bytes stay opaque and are never deserialized by this adapter. These controls do not authenticate a supplied database, reproduce a paper, establish biology, or validate every original algorithm independently.

Runtime execution has wall-time, file/output and owned-process cleanup limits. These are not operating-system RAM/CPU isolation, and self-detaching processes are outside the profile. Java heap, matrix/input/audit capacities are explicit; exceeded limits admit no partial family. Preserve source input and output missing/nonfinite/error states; never repair them to force a pass.
