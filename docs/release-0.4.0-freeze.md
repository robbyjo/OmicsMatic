# OmicsMatic v0.4.0 toolset freeze

The v0.4.0 release candidate is isolated on `codex/release-v0.4.0-frozen`.
Its integration base is `f87564288cf6e9d73a7d30164a96689b6ed861da`, captured
on 2026-11-15. Completed, validated additions are included; ongoing tool
development continues independently. The original freeze created no public tag
or release. Publication is now authorized for v0.4.0 with the known catalog
limitation disclosed in the [release guide](release-0.4.0.md).

**Known catalog qualification limitation.** The merged-source catalog verifier refuses
the historical SingleR predecessor baseline after the study/manuscript changes.
The original tool-specific qualifications remain preserved, but they do not
establish a newly qualified whole catalog on this merged release tree. The
recorded classifications below describe those existing scoped qualifications.

## Included scope

| Catalog classification | Families |
| --- | ---: |
| Validated native subsets | 21 |
| Validated upstream workflows | 101 |
| Existing adapters | 3 |
| Existing lookups | 7 |
| Existing exports | 1 |
| Related native methods; upstream equivalence not established | 47 |
| Missing | 265 |
| Explicitly excluded | 1 |
| Total named families | 446 |

The first two categories are the **122 newly integrated families**. Including
the existing adapter, lookup, export and related-method categories gives 180
entries with some documented support. This is not a count of fully implemented,
installed, bundled or scientifically validated upstream packages. Supported
operations and runtime/resource requirements remain specific to each entry.

The frozen integration includes BIGCAT, HICAT, SingleR, celldex, SPOTlight,
MELODI and native TPMCalculator. iDEP stays in the missing category. Development
records retained in the source tree do not admit an unfinished tool. Cell
Ranger remains excluded. The full research/implementation project remains open.

[toolset-freeze.json](releases/0.4.0/toolset-freeze.json) records the exact
catalog hashes, all tool IDs by status and the integration commit. The original
catalog, usage records, warnings and tool-specific validation receipts remain
in the frozen source. New admissions belong to subsequent development; release
fixes require explicit review and a new validation record without silently
expanding the admitted toolset.

The analysis pseudocode includes the requested conditional SNP selection and
useful tool-based follow-up policy. These are workflow requirements, with the
distinction between available standalone methods and integrated study operations
preserved. Existing supplied-result and artifact-linking contracts still apply.

## Release validation

The isolated software build/test gate passed on Windows with JDK 21.0.4:
`gradlew.bat check assemble --no-daemon --no-parallel --no-build-cache
--max-workers=2 --console=plain`. Across 878 suites, 25,519 tests were reported:
**25,078 passed, 441 skipped, zero failures and zero errors**. The skips include
Linux-only controls, optional original runtimes and explicitly gated live or
fixture checks; they are not fresh upstream validation. The
[test receipt](releases/0.4.0/validation/tests.json) retains all skip reasons,
suite counts and raw-result hashes. Raw XML and HTML reports remain under
`build/test-results/test` and `build/reports/tests/test` in the release checkout.

The packaged executable reports `0.4.0`; its six-sample synthetic OLS
coefficients and standard errors match an independent NumPy calculation to
relative/absolute tolerance 1e-12. FUMA, SuSiE and study help entry points work. All 5,839 compiled library
classes use Java class-file major version 61 (Java 17). Executable, library and
source and Javadoc JAR ZIP integrity checks passed. These checks do not install or execute
every optional upstream runtime.

The initial packaging attempt exposed Javadoc's implicit source lookup through
resource directories. A resource Java launcher requires its private IGV runtime
and is not a library API source. The release build now explicitly limits
Javadoc source lookup to `src/main/java`; upstream resource bytes are unchanged.
The corrected `check assemble` completed successfully, reusing the completed
full-suite test results. Two existing Javadoc text warnings remain. Both build
attempts and artifact hashes are recorded in the
[software-build receipt](releases/0.4.0/validation/software-build.json).

Local candidate assets are in `build/release/` in the release checkout:

- `omicsmatic-0.4.0.jar` (executable)
- `omicsmatic-0.4.0-library.jar`
- `omicsmatic-0.4.0-sources.jar`
- `omicsmatic-0.4.0-javadoc.jar`
- `toolset-freeze.json` and `SHA256SUMS.txt`

The toolset freeze and software gate are complete. The
[GitHub release](https://github.com/robbyjo/OmicsMatic/releases/tag/v0.4.0)
distributes the software with the catalog limitation below. Original JSON
receipts preserve their pre-publication status and exact bytes; publication does
not turn the refused catalog check into a pass. Tagged CI build results and
published checksums describe the downloadable assets; local artifact hashes
above describe the original Windows candidate.

### Catalog verification and retained attempts

1. The new checkout initially lacked the cached primary-paper XML and correctly
   returned SKIP3. The permitted existing public-source cache was copied with
   hashes; no source evidence was regenerated.
2. Python 3.13 reproduced the same decompressed review delta but a different
   gzip OS-header byte. The original exact-byte verifier refused that result.
3. Running the unchanged verifier with the existing Windows Python 3.12.9
   runtime reproduced the recorded gzip encoding and reached the substantive
   predecessor-source check. It returned FAIL2 because preceding tracked files
   differ from baseline `96fc65d3e37917728901f88906cd15c3b386702e`.

The [source-baseline refusal](releases/0.4.0/validation/catalog-source-baseline-refusal.json)
and initial attempts are retained. No expected hash, old qualification or
scientific verifier was changed to turn the refusal into a pass. Full catalog
verification requires a separate predecessor integration accounting for the
merged study/manuscript/cleanup changes and release packaging changes. This
must be resolved before describing v0.4.0 as a fully verified public release.
