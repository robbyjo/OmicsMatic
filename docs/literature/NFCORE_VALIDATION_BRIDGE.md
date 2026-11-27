# Original nf-validation engine bridge

`nfcore-validation-tools` adds a captured, offline plugin directory to the original Nextflow24.04.4 engine. It admits the complete original nf-validation1.1.3 payload, identified by the published release SHA-512 and 47 installed file SHA-256 hashes. The plugin remains a private prerequisite; its binary classes and libraries are not distributed in OmicsMatic.

The managed historical `all` packaging refuses third-party plugins. This bridge therefore captures the unchanged original standard24.04.4 bootstrap, its original `one` capsule and all 51 explicit dependency JARs as resource `engine`. The original capsule's manifest excludes all transitives; the exact JARs are extracted unchanged from the already pinned managed24.04.4 distribution. The existing managed home supplies its qualified Java11 runtime and baseline setup proof. Capsule extraction stays in the new session, and both bootstrap and Maven resolution are offline. The original missing-artifact-descriptor messages remain in stderr and are called out in the result warnings; no dependency is substituted or downloaded during analysis.

Download the original [24.04.4 bootstrap](https://github.com/nextflow-io/nextflow/releases/download/v24.04.4/nextflow) and its [standard capsule](https://www.nextflow.io/releases/v24.04.4/nextflow-24.04.4-one.jar). Run `nfcore-validation-tools --install-engine ORIGINAL_BOOTSTRAP --one-jar ORIGINAL_ONE.jar --home MANAGED_HOME --setup-report REPORT --packaging one --out NEW_ENGINE_SETUP`, then capture `NEW_ENGINE_SETUP/engine` as resource `engine`. Setup verifies the managed distribution, both provided assets and every extracted dependency. It executes no analysis.

This is an execution component. It does not establish a complete nf-core/rnasplice analysis, whole-tool coverage, a new catalog family, or a paper reproduction. The six nf-core paper memberships still require their separate source and execution contracts.

Supply the standard captured-workflow recipe and an additional resource named `plugins`, pointing to the parent of `nf-validation-1.1.3`. The recipe must explicitly choose `linux-x86_64-nextflow-24.04.4`. Original workflows, input roots, configuration files, parameters, required executables, expected outputs and capacities remain explicitly declared. The bridge sets `NXF_PLUGINS_DIR` to the captured plugin directory and keeps `NXF_OFFLINE=true` and implicit plugins disabled. It performs no downloads or installations during analysis.

Download the exact [original plugin ZIP](https://github.com/nextflow-io/nf-validation/releases/download/1.1.3/nf-validation-1.1.3.zip) separately where permitted. Then run `nfcore-validation-tools --install-plugin ORIGINAL_RELEASE.zip --out NEW_SETUP`. This explicit Java setup command checks the complete release hash before creating the destination, expands its unchanged payload, and verifies all 47 resulting files. Use `NEW_SETUP/plugins` as the recipe's `plugins` resource. No Python extractor is required. Setup failure retains the partial directory with a prominent warning; dependent analyses remain skipped until a new setup succeeds.

```
java -jar omicsmatic-0.4.1.jar nfcore-validation-tools --help
java -jar omicsmatic-0.4.1.jar nfcore-validation-tools --recipe RECIPE.json --home EXACT_HOME --setup-report COMPLETED_SETUP --out NEW_SESSION
java -jar omicsmatic-0.4.1.jar nfcore-validation-tools --resume SESSION --home EXACT_HOME --setup-report COMPLETED_SETUP
java -jar omicsmatic-0.4.1.jar nfcore-validation-tools --verify SESSION_OR_STANDALONE_ATTEMPT
```

Plugin bytes, the complete file/directory family and the selected engine are checked before execution, during process admission, and in the saved result verifier. Missing runtime/plugin prerequisites produce **SKIP3, exit3**: skip the dependent analysis and complete the named permitted setup. Changed or extra plugin members, malformed requests, task/numerical failures, capacity failures and failed finalization produce **FAIL2, exit2**: no result is admitted. Read the latest attempt's `WARNINGS.md`, `FAILURE.json`, original engine log and trace. Preserve failed attempts; use fresh paths when changing a captured request. A successful previous attempt cannot substitute for a failed latest attempt.

Saved verification checks the complete captured evidence and original completion/trace consistency without requiring R, Python, containers or the producer host. It does not rerun biology or authenticate execution history. Historical plugin-free engine classes and their qualified results are unchanged.

The engine setup declares Maven's required empty `repository/.locks` directory. It admits no extra files or subdirectories there at an execution boundary. The unchanged original plugin accepts the supplied valid synthetic example and refuses the invalid probability and identifier controls; the Java bridge preserves those decisions. A cache resume retains the complete output and observes the original task as `CACHED`. See [the reproducible example](../../examples/nfcore-validation/README.md).

The rnasplice1.0.4 source uses STAR2.7.10a, whereas P027 reports STAR2.7.9a. The complete upstream human chromosome-X test input is a software control, and this discrepancy remains unresolved for the paper's C. elegans analysis. Original source/default tests cannot establish equivalence to the paper's complete recipe. No nf-core family credit is added by this bridge.

The original [plugin release](https://github.com/nextflow-io/nf-validation/releases/tag/1.1.3) and its [Apache2 license](https://github.com/nextflow-io/nf-validation/blob/1.1.3/LICENSE) apply to the private plugin. Its ZIP repeats two central-directory records and overwrites an earlier manifest entry. Intake checks physical compressed intervals, equal repeated-offset records, CRCs and archive-order destinations; no upstream class or library is patched.
