# Synthetic Signac LSI example

`counts.mtx` is a reproducible synthetic 60-feature by45-cell integer matrix,
generated with Python seed490142. Entries are deliberately unordered and include
a duplicated coordinate and an explicitly stored zero. The one-column feature and
cell files preserve matrix order. This is not paper or patient data.

Use the copyable command and runtime requirements in
[the typed LSI guide](../../docs/literature/SIGNAC_LSI_DEVELOPMENT.md).
Use `org.jlinalg.cli.SignacLsiRequestCli --request examples/signac-lsi/request.json`
with the development JAR named in the guide. Relative paths inside the UTF-8 JSON
resolve against that JSON file's directory. Keep the request file itself in an
ASCII-only path on legacy Windows launchers; Unicode data/output paths belong
inside the JSON. `verify-request.json` verifies the example output.
The lower-level `SignacLsiToolsCli` value-option entry point remains available;
neither entry point is in the canonical executable's root command dispatch.
Choose an explicit package/source profile and a new output path.
Read `WARNINGS.md` and `effective-settings.tsv` before interpreting any result.
