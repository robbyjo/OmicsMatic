# Global TwoSampleMR examples

Read the [command guide](../../docs/literature/TWOSAMPLEMR_TOOLS.md).
Seven synthetic recipes cover every qualified profile:

| Recipe | Operation |
| --- | --- |
| `modern-prepared.json` | Original0.7.14 harmonization/MR/sensitivity |
| `modern-clump.json` | Original0.7.14 local clumping |
| `format-0.4.9.json` | Original0.4.9 formatting |
| `mr-0.5.6.json` | Original0.5.6 MR |
| `pipeline-0.5.7.json` | Original0.5.7 harmonization/MR/sensitivity |
| `multivariable-0.6.8.json` | Original0.6.8 MV preparation, not causal estimation |
| `historical-clump.json` | Original0.6.8 local clumping |

Copy a recipe to a NEW filename in the same `recipes/` directory. Supply your
explicit Rscript and ordered library paths, plus exact PLINK path for clumping.
Historical library order is selected version, historical extras, shared
dependencies; the modern profile uses its private library. Preserve the exact
profile/hash and declared scientific settings. Relative paths resolve against
the JSON file without environment-variable expansion. Every run needs NEW output.

```powershell
java -Xmx1g -jar "C:/path/to/omicsmatic.jar" twosamplemr-tools --request "D:/path/to/examples/twosamplemr-global/recipes/my-mr-0.5.6.json"
```

These recipes were executed after only runtime-path substitution. Inputs are
copied byte-for-byte from the preceding public synthetic examples. Read their
version-specific guides for column names, original defaults, standalone scope,
0.4.9 documentation-only build and present-day dependency limits.

Verify a completed capture without R:

```powershell
java -Xmx1g -jar "C:/path/to/omicsmatic.jar" twosamplemr-tools --verify "D:/path/to/completed-analysis"
```

Pass `recipes/replay.json` through `--request` to replay the example it names,
after setting absolute or recipe-relative paths and NEW output. The capture's
engine/profile selects the same original workflow; replay does not relabel it.
All seven tested replays preserved parent bytes and reproduced result exports.

**SKIP3** skips dependent analysis and names what must be restored through
permitted installation/download. **FAIL2: NO COMPLETE RESULT** retains the
failed/partial attempt; fix the named cause and retry NEW output. Inspect
WARNINGS.md,analysis-status.json and original logs. Do not silently repair
scientific inputs, thresholds or versions. Singleton/empty clumping and MV
preparation need their documented interpretation and are not MR estimates.
