# Native GSEA prepared-data file workflow qualification

**The standalone RUN/VERIFY/REPLAY workflow is qualified within its explicit
prepared-data source-core scope. Global registration, source-role crosswalk and
catalog admission remain open. No new catalog credit is added here.**

Use the [beginner workflow guide](../../vignettes/gsea-native-workflow.md) and
[runnable synthetic examples](../../../examples/native-gsea/README.md).

The final normal JAR passes eight focused JUnit tests. All 23,508 predecessor
JAR members remain unchanged; only workflow classes are added. Windows Java 21,
Windows Java 25 and Linux Java 21 each pass 668,739 exact original-source fields:
nine 10,000-permutation compositions and 423 option cases. Each host also passes
34 expected refusal controls, with actual partial-batch, numerical, input/schema,
tamper/reseal, resource, owned-process and physical-alias cases. Additional checks
cover literal Unicode/control identities, signed 64-bit seed limits, exact
float32 midpoint parsing, cross-host VERIFY/REPLAY and an explicit class-directory
SKIP3. Parent bytes and modification times remain preserved.

`workflow-results.zip` retains exact native binary objects for all 432 positive
original-counterpart cases from the final producer. The checksummed original
fixtures remain in the preceding engine checkpoint and test resource.
`checks.zip` retains producer/build/test/process records, controls, transport,
previous failures and their source snapshots, and local orchestration scripts.
Absolute machine paths in historical scripts/logs are provenance, not portable
execution instructions. SHA-256 is integrity, not a signature.

```powershell
python -B tools/literature/verify_broad_gsea_workflow.py --out build/gsea-file-readback-NEW
```

The saved reader checks all original counterpart fields from archive bytes and
the closed qualification records without executing Java, Gradle, R or models.
Optionally add `--jar EXACT-JAR` to check the exact final producer and every JAR
member. It does not replace the actual workflow's saved-input semantic VERIFY.
Do not refresh old fingerprints to admit changed code; qualify a new producer.

Earlier attempts are retained. A WSL preflight was blocked while the original
distribution was being compacted; the final Linux runs use an independently
preflighted existing Java 21 environment. An initial heap-test input exceeded its
input cap before launch; its correct FAIL2 is retained, and the corrected test
exercises genuine worker heap exhaustion. Cross-host checks caught CRLF/LF
differences in two JSON-lines ledgers despite identical numerical binaries.
The final producer emits canonical LF and passes the cross-host controls.

**Warnings and recovery:** missing or forbidden exact prerequisites produce
SKIP3 with a named action. Invalid data/design, integrity, numerical, capacity,
timeout and process failures produce FAIL2 and no complete result. Preserve raw
inputs, conditions, partials, null prefixes and failed/superseded evidence. Correct
the named cause and use NEW output; never silently repair identifiers, substitute
values/algorithms or change editions to force success. Original GSEA runtimes
are needed only to regenerate reference evidence, not native workflow RUN.

The full 250-paper implementation/review goal remains unfinished: 139 admitted
scoped integrations, 1,520 reviewed uses and 1,185 pending. A concurrent completed
cleanup retired 12 historical worktrees with verified source recovery archives;
20 current worktrees are retained. Publication receipts separately account for
the explicit cache retirements and unchanged live evidence. Ignored runtimes,
producer JARs and full private evidence still require a separate machine transfer.
The literature reports remain withheld from Pages; no release is claimed.
