# GSEApy runtime setup

This kit installs four pinned original editions into separate namespaces. It
does not run an enrichment analysis. Linux x86_64 CPython 3.11.17 is required.
Use the read-only project scientific Python base and its exact successful
CellPhoneDB setup receipt. No CellPhoneDB analysis is needed. Read the base
preparation guide if that base is missing; never substitute another environment
or refresh its saved hashes.

Run with that base Python from the exported kit:

```sh
/ABS/BASE/bin/python -I -B setup/prepare.py \
  --base-runtime /ABS/BASE-SETUP/COMPLETED.json \
  --prefix /ABS/NEW-CASE-SENSITIVE-PREFIX \
  --out /ABS/NEW-SETUP-RECEIPTS --allow-install
```

The four original wheels are bundled, so setup can work offline. Installation
requires --allow-install. If a pinned archive is absent, supply an exact
--archive-cache directory or use --allow-download only where permitted.
Downloads are restricted to the exact official archive URL and hash. Otherwise
SKIP3 skips all dependent analysis and records the missing dependency/action.
Do not install into the shared base. Use NEW paths and retain failed prefixes.
Full original imports and complete before/after inventories precede admission.
Require exit0 AND PASS_BOUND_ORIGINAL_GSEAPY_WORKFLOW_RUNTIME_ONLY in COMPLETED.json.

RUN installs nothing. An execution failure admits no earlier partial batch.
Source errors, duplicate/case preprocessing, nonfinite results and exact library
contents must remain explicit. This kit does not recover historical Enrichr
libraries or infer paper modes. The1.0.4 package has no GSVA API. Preserve its
legacy parameter and report differences. Whole-package/paper reproduction is
not claimed. Read WARNINGS.md and every original/process log.
