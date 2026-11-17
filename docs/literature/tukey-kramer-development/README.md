# Native Tukey–Kramer checkpoint

Use the [worked guide](../../vignettes/posthoc-comparisons.md) for the source-build
CLI, Java API, model assumptions and failure recovery. The numerical method
supports ordinary independent equal-variance one-way comparisons, including
unequal sample sizes. It does not complete the GraphPad Prism family.

[VALIDATION.json](VALIDATION.json) records 46 passing focused tests, 57 pair
comparisons across ten independent R fits, ten additional tail checks, 17
packaged processes and five compatibility processes. Two-group references use
the exact Student-t identity; the unmodified legacy TukeyHSD values remain
retained, including their documented numerical discrepancies. Windows and
Linux comparison tables match byte for byte. Existing scientific packaged
classes remain unchanged except for the root CLI routing addition.

[SOURCE_REVIEW.json](SOURCE_REVIEW.json) distinguishes seven explicit one-way
paper uses, other model-specific post-hoc uses and box-plot mentions. The
[priority record](../IMPLEMENTATION_PRIORITY.md) explains the broader queue.
No new catalog family credit is awarded by this statistical component.

[controls.zip](controls.zip) contains the source/fixture snapshot, original R
outputs, selected complete test/process records and retained failed attempts.
Every archive member was read back and compared with its saved source bytes.
The archive does not contain the large compiled JARs or complete existing
adapter runtime bundles; their exact local reader identities are recorded.
The released 0.4.0 JAR remains unchanged. Rebuild current source to use this
command; no R or GraphPad installation is needed for native analysis.

**SKIP3:** skip dependent work when input/output access is missing or forbidden;
supply permitted paths and retry a new output directory. **FAIL2:** no complete
comparison family was admitted; preserve raw input and partial files, read
`FAILURE.txt` and `WARNINGS.md`, correct the named cause and retry new output.
Read `NUMERIC-WARNING.txt` when numeric zeros occur. Require exit 0 and
`COMPLETED.json`; partial tables are not completed analyses.
