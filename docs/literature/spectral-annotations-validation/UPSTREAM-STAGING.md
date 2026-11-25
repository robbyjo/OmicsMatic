# Keep the validated preparation capture unchanged

**Copy the prepared table to a NEW, separate upstream working directory before giving it to DIA-NN.** DIA-NN writes library caches beside a text library. Those extra files would invalidate the native capture's deliberately strict file inventory.

Keep the complete `prepared-new` directory and its exact producing JAR. Work with a copy of `prepared-new/outputs/library.tsv`, rather than processing files inside the capture.

PowerShell:

```powershell
New-Item -ItemType Directory -Path '.\diann-work-new' -ErrorAction Stop
Copy-Item -LiteralPath '.\prepared-new\outputs\library.tsv' -Destination '.\diann-work-new\library.tsv' -ErrorAction Stop
```

Linux or macOS:

```shell
mkdir diann-work-new && cp prepared-new/outputs/library.tsv diann-work-new/library.tsv
```

Point the separately permitted upstream workflow at the copied table in `diann-work-new`, and use separate NEW upstream output paths. Preserve its original inputs, caches, results and warnings. Use the software edition, references and scientific settings required by your actual analysis; these copy commands do not select or run an analysis.

Recheck the original preparation capture:

```shell
java -jar build/cli/omicsmatic-0.4.1.jar spectral-library-annotations --verify prepared-new
```

**If a permitted runtime, licence, reference or installation permission is missing, skip the dependent upstream analysis and state the missing requirement.** Do not infer successful MS processing from a prepared table, library import, or exit0 with zero MS files.

See the [native preparation guide](../SPECTRAL_LIBRARY_ANNOTATIONS.md) and [scoped qualification](QUALIFICATION.json). The original frozen source/evidence checkpoint remains unchanged; this additional staging guidance is recorded separately by the finishing metadata.
