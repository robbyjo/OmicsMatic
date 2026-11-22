# MACS runtime failure and required recovery

**MACS CALLPEAK IS UNAVAILABLE IN THE CURRENT WINDOWS RUNTIME. SKIP THESE
ANALYSES.** A private MACS3 3.1.0 source installation completed and its version,
`callpeak` import and package dependency checks passed. Its actual known-truth
BEDPE `callpeak` analysis then crashed with Windows access violation
`0xc0000005` (exit 3221225477), during P-value/Q-value table construction.
There is no validated project MACS adapter or successful callpeak result.

**HMMRATAC/ANNDATA-DEPENDENT ANALYSES ARE ALSO UNAVAILABLE.** Importing AnnData
failed in `h5py.defs` because a DLL procedure could not be found. The exact ABI
cause remains unresolved. Package installation and `pip check` did not detect
this runtime failure. Do not infer HMMRATAC availability from those checks.

## What users need to do

1. Use a permitted Linux or macOS host with Python 3.12 or later and the compiler
   and Python development headers required by upstream. MACS3 currently tests
   Linux/macOS and does not support Windows. See the
   [official installation instructions](https://macs3-project.github.io/MACS/docs/INSTALL.html).
2. Create a private environment and explicitly install the intended version.
   For the modern version investigated here:

   ```sh
   python3 --version
   python3 -m venv macs3env
   . macs3env/bin/activate
   python -m pip install --upgrade pip
   python -m pip install 'MACS3==3.1.0'
   macs3 --version
   python -m pip check
   ```

3. Run scientific fixtures for the **actual intended program**, file format,
   control, genome size and peak model before using study data. Check successful
   completion and expected coordinates/scores, rather than only version/import
   checks. HMMRATAC additionally needs working HDF5/AnnData data roundtrips and
   its own HMM fixture. These supported-host validations have not been run by
   this task; the project adapter remains pending.
4. Reconcile each paper's reported version and options. MACS2/old MACS3,
   narrow/broad calling, paired fragments, fixed single-tag shifts, score-track
   threshold calling, and HMMRATAC are separate contracts. Do not silently repair
   ambiguous paper flags or replace an old model with a modern one.

If installation is forbidden or a suitable runtime cannot be supplied, **keep
the affected analyses skipped and retain the error and these recovery steps**.
This task does not automatically provision WSL, an operating system or remote
services. Do not run the research-only `build/omics-macs3-ucrt` environment on
study data as if it were validated.

## Retained evidence and limited successes

[macs-runtime-attempt.json](macs-runtime-attempt.json) records source, package,
compatibility-patch and error identities. Full logs and partial outputs remain
under `build/literature-review/macs3-runtime/`; failed results are retained as
failed research attempts. The source build uses Windows-specific serial BGZF
and memory-monitoring changes plus a separately validated POSIX random helper.
It is not an official Windows build. Upstream build flags suppress compiler
diagnostics; a successful build must not be described as warning-free.

Separate raw `bdgpeakcall` and `bdgcmp ppois` smoke runs succeeded on small tracks.
They do not validate the failed alignment-based caller, HMMRATAC, every score
method or an OmicsMatic adapter. The generic
[bdgpeakcall program](https://macs3-project.github.io/MACS/docs/bdgpeakcall.html)
thresholds an already generated score track; it does not estimate the statistical
background used by `callpeak`.

The [native genomic-signal command](GENOMIC_SIGNAL_WORKFLOWS.md) already supplies
Java bedGraph means and state/genome log2 enrichment for supplied tracks and an
explicit genome universe. It requires no MACS installation, but does not produce
MACS P-values or peaks. All 32 MACS-family paper uses now have individually
reviewed summaries and explicit compatibility gaps in the catalog; paper-wide
semantic completeness remains open.

The [native track-peaks command](TRACK_PEAK_WORKFLOWS.md) additionally implements
supplied-score threshold/gap/span and plateau summits in Java. Its positive
float32 geometry/summits passed 129 actual MACS3 `bdgpeakcall` references and
packaged checks. It requires no external runtime. The catalog's native-subset
MACS badge refers to this limited program; **alignment-based callpeak and
HMMRATAC remain unavailable**. Thresholding an existing track does not repair
those failures or generate their statistical background/P/Q scores.
