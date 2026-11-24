# v0.4.1 validation

The first local check stopped at missing citation blocks in newly added Markdown tutorials. The registry and generated blocks were repaired, and the website check and citation generator passed. Existing method-specific reference notes are preserved.

The Windows build uses Java 21 with the project's Java 17 release target. The packaged executable reports `0.4.1`. The committed synthetic linked-MR example retains an exact reliable null, selects a qualifying proxy for a poorly measured outcome and reports a missing outcome as untestable. Its complete output inventory and hashes verify successfully. The full local `check assemble` run is in progress at the release commit.

The tagged GitHub workflow runs the authoritative Linux/Java 17 `clean check assemble` gate and publishes only after it succeeds. Observed complete test totals, workflow links and artifact verification are appended to this record on main after the gate. Optional-runtime skips remain distinct from executed checks; no live-provider or cohort scientific analysis is claimed.

The local evidence directory is `build/release-v0.4.1-20261124/`. It retains initial and final build logs, the synthetic example, branch inventory/audit and the eventual publication and cleanup receipts.

The full Windows run identified a stale LIMMA root-help projection snapshot. The reviewed difference consists of 19 subsequently added command lines; the expected snapshot now includes those additions. The first tagged workflow ([45454794398](https://github.com/robbyjo/OmicsMatic/actions/runs/45454794398)) was cancelled before publication. The original candidate remains in Git history. GitHub confirmed that no v0.4.1 release existed before the unpublished candidate tag was advanced to this correction.

With the corrected fixture loaded, 789 existing Windows tests across MR, colocalization, CLI/study MR, LIMMA routing/help and transform parsing passed (zero failures, errors or skips). These ran through JUnit's launcher against the current compiled classes and corrected source resources; the full Windows run retained its earlier snapshot failure separately.

The second hosted attempt ([45455972677](https://github.com/robbyjo/OmicsMatic/actions/runs/45455972677)) stopped during compilation because Gradle's default 512-MiB build-daemon heap exhausted its capacity. The project now declares a 3-GiB build heap and 1-GiB metaspace cap. The existing 2-GiB test-worker limit is unchanged. This attempt did not reach test execution or publication; its raw logs are retained.

The remaining legacy root-help fixtures were also audited: PEER and TwoSampleMR required only subsequently added command lines. All three current help projections and the MR/colocalization/transform regression selection passed together: **822 tests passed, zero failures, errors or skips**. The earlier full Windows run uses its initially copied fixtures and retains those old snapshot failures separately.
