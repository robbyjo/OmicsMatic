# Native repeat checks, masking and mask audits

These synthetic inputs demonstrate `repeat-tools` from this checkout. Rebuild
the JAR first; an older downloaded `0.3.7` JAR does not automatically contain
this command. Native operations require Java 17 or later and no RepeatMasker
installation, download or external service.

From the project directory, build with `./gradlew executableJar` on Linux/macOS
or `.\gradlew.bat executableJar` in Windows PowerShell. Then run:

```text
java -Xmx256m -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --recipe examples/repeat-native-workflow/mask.json --out build/repeat-native-mask-example
java -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --verify build/repeat-native-mask-example
```

Always choose a **new output directory**. Read its `WARNINGS.md` before using
results. A completed operation requires `COMPLETED.json`, matching manifest
hashes and a successful `--verify` check. SHA-256 checks integrity; it is not a
signature or a certificate of upstream search completion.

The mask result is `native-mask/native-masked.fa`. Its complete sequence
population matches `expected.masked.fa`; the supplied interval covers four
bases of `example`. The unannotated `negative` record remains present, with its
original case. `native-mask/native-coverage.tsv` reports lengths, covered bases
and characters changed for every query. These last two counts can differ when
a selected character already equals the mask character.

Two additional operations use the same inputs:

```text
java -Xmx256m -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --recipe examples/repeat-native-workflow/check.json --out build/repeat-native-check-example
java -Xmx256m -jar build/cli/omicsmatic-0.3.7.jar repeat-tools --recipe examples/repeat-native-workflow/audit.json --out build/repeat-native-audit-example
```

| Operation | What completion proves | Main output |
| --- | --- | --- |
| `CHECK` | Complete structural parsing and declared query/coordinate joins | Protected preparation and `results.json` |
| `MASK` | Complete native masking from the explicitly selected supplied annotations | `native-mask/native-masked.fa` and coverage |
| `AUDIT` | Every supplied masked query and base matches the declared native masking contract | `audit-coverage.tsv` and `results.json` |

For your own data, edit the recipe's assembly and all six `input_review`
statements. Supply the correct OUT/UCSC layout and explicit plain/gzip encoding.
Data paths resolve relative to `resource_root`, which itself resolves relative
to the recipe. Query identifiers, full headers and case are preserved for
native reuse. No chromosome alias, coordinate build or biological repeat class
is inferred. Review every zero-coverage query: zero rows or zero selected rows
do not prove annotation completeness or biological absence.

The declared workspace and member budgets cover the **whole bundle**, including
metadata, protected preparation and native output. The controller subtracts
existing root metadata and reserves 256 KiB/eight members from component
budgets for finalization and retained diagnostics. Increase reviewed capacities
when necessary; the command never admits a truncated result to fit a limit.

For paths containing Unicode that a Windows launcher cannot pass unchanged,
save a UTF-8 options file with exactly two string fields:

```json
{
  "recipe": "D:/YOUR_PROJECT/examples/repeat-native-workflow/mask.json",
  "out": "D:/YOUR_OUTPUT/new-analysis"
}
```

Run `repeat-tools --options-file options.json`. These two paths resolve from
the working directory; use absolute paths for clarity. The command snapshots
and checks the options file and recipe throughout execution. Unknown,
duplicate, recursive and non-string CLI fields are refused.

For a Unicode output path, verification has its own UTF-8 options form:

```json
{
  "directory": "D:/YOUR_OUTPUT/new-analysis"
}
```

Save it as `verify-options.json`, then run
`repeat-tools --verify-options-file verify-options.json`. Keep the options
filename passed to an affected Windows launcher in its supported character
set. Verification checks this file through EOF and confirms it is unchanged;
it never writes to the analysis bundle.

**Warnings and recovery:** exit `0` completes only the operation named in the
marker. Exit `2` means invalid input, integrity, capacity, execution or
interruption failure. Retain `WARNINGS.md`, `session.json`,
`analysis-status.json` and applicable `PREPARATION_FAILED.txt`,
`NATIVE_MASK_FAILED.txt`, `FINALIZATION_FAILED.md` or `INTERRUPTED.md`. Correct
the named cause and rerun to a new output; keep the failed evidence. If final
metadata cannot fit, the top-level warning or finalization diagnostic explains
the failure. An absent or invalid completion marker means the bundle is
incomplete, even when a mask file exists.

**Original-search status:** this candidate does not implement admitted original
RepeatMasker searches. Such requests exit `3` with a prominent `SKIP3` record.
Use a candidate with documented runtime/resource support, or run the exact
permitted upstream separately and explicitly identify its annotations for
native reuse. Imported annotations remain `EXTERNAL_NOT_CERTIFIED`; no edition
is substituted, and an original no-hit message does not certify a search.

See the [recipe reference](../../docs/literature/omics-2023-2026/REPEATMASKER_RECIPES.md)
and [validation, scope and recovery notes](../../docs/literature/omics-2023-2026/REPEATMASKER_NATIVE_STATUS.md).
