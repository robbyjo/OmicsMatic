# Synthetic prepared trans-QTL example

Set python and source_directory in request.json to the permitted, exact
qualified Linux runtime and original source listed in
src/main/resources/org/jlinalg/omics/tensorqtl-workflow/SOURCE-PROFILES.json.
Use a NEW out path, then run tensorqtl-tools --request request.json from
the project executable JAR. No actual study data or author recipe is included.

The request declares CPU execution, a project seed and a retained-output
threshold of 1 so all tested pairs can be inspected. That threshold makes
no significance claim. Read WARNINGS.md and analysis-status.json before
using outputs. Missing runtime/source prerequisites cause SKIP3 (exit 3);
the dependent analysis is skipped until the named exact dependency is
restored. Invalid/changed inputs, original errors, numerical/convergence
failures and capacity failures cause FAIL2 (exit 2) and admit no complete
result. Preserve the failed capture and correct the named cause before a
NEW run. See docs/tensorqtl-tools.md for cis, qvalue, interaction and SuSiE
contracts and full recovery instructions.
