# v0.4.1 validation

The first local check stopped at missing citation blocks in newly added Markdown tutorials. The registry and generated blocks were repaired, and the website check and citation generator passed. Existing method-specific reference notes are preserved.

The Windows build uses Java 21 with the project's Java 17 release target. The packaged executable reports `0.4.1`. The committed synthetic linked-MR example retains an exact reliable null, selects a qualifying proxy for a poorly measured outcome and reports a missing outcome as untestable. Its complete output inventory and hashes verify successfully. The full local `check assemble` run is in progress at the release commit.

The tagged GitHub workflow runs the authoritative Linux/Java 17 `clean check assemble` gate and publishes only after it succeeds. Observed complete test totals, workflow links and artifact verification are appended to this record on main after the gate. Optional-runtime skips remain distinct from executed checks; no live-provider or cohort scientific analysis is claimed.

The local evidence directory is `build/release-v0.4.1-20261124/`. It retains initial and final build logs, the synthetic example, branch inventory/audit and the eventual publication and cleanup receipts.

The full Windows run identified a stale LIMMA root-help projection snapshot. The reviewed difference consists of 19 subsequently added command lines; the expected snapshot now includes those additions. The first tagged workflow ([45454794398](https://github.com/robbyjo/OmicsMatic/actions/runs/45454794398)) was cancelled before publication. The original candidate remains in Git history. GitHub confirmed that no v0.4.1 release existed before the unpublished candidate tag was advanced to this correction.

With the corrected fixture loaded, 789 existing Windows tests across MR, colocalization, CLI/study MR, LIMMA routing/help and transform parsing passed (zero failures, errors or skips). These ran through JUnit's launcher against the current compiled classes and corrected source resources; the full Windows run retained its earlier snapshot failure separately.

The second hosted attempt ([45455972677](https://github.com/robbyjo/OmicsMatic/actions/runs/45455972677)) stopped during compilation because Gradle's default 512-MiB build-daemon heap exhausted its capacity. The project now declares a 3-GiB build heap and 1-GiB metaspace cap. The existing 2-GiB test-worker limit is unchanged. This attempt did not reach test execution or publication; its raw logs are retained.

The remaining legacy root-help fixtures were also audited: PEER and TwoSampleMR required only subsequently added command lines. All three current help projections and the MR/colocalization/transform regression selection passed together: **822 tests passed, zero failures, errors or skips**. The earlier full Windows run uses its initially copied fixtures and retains those old snapshot failures separately.

The first complete Linux/Java 17 gate ([45456925428](https://github.com/robbyjo/OmicsMatic/actions/runs/45456925428)) finished 39,571 tests: 39,159 passed, 410 skipped, and only the two already-corrected PEER/TwoSampleMR help snapshots failed. It reported no other failures. Its full raw logs are retained as `release-attempt3-logs.zip`. The final candidate includes both corrected snapshots and reruns the complete gate before publication.


## Published validation

The final tagged Linux/Java 17 `clean check assemble` gate [passed](https://github.com/robbyjo/OmicsMatic/actions/runs/45460381931) at release commit `2f3032a9af6d9a3796043c80db3c7daffb31d79e`:

- **39,161 tests passed**, 410 skipped, zero failures or errors, across 1,035 suites (39,571 tests total).
- Executable, library, source and Javadoc JARs, the frozen inventory and SHA-256 checksums were published as [OmicsMatic v0.4.1](https://github.com/robbyjo/OmicsMatic/releases/tag/v0.4.1). The release is public and marked as the latest stable release.
- Every downloaded asset matched its GitHub digest where supplied, and all entries in `SHA256SUMS.txt` matched. All four JAR ZIP inventories passed integrity checks. The executable reported `0.4.1` and exposed the new command in `--help`.
- The downloaded executable ran the committed synthetic example successfully: reliable exact null retained, poorly measured outcome replaced by a qualifying proxy, and unavailable outcome explicitly untestable. Its output bundle's complete inventory and hashes verified.
- The deployed home page, release guide and download navigation matched the committed website source. The independent focused Windows selection passed 822 tests without failures, errors or skips.

Optional-runtime skips and the historical catalog/source-baseline restrictions remain explicit. No new live-provider, whole-catalog or cohort scientific validation is claimed. The unsuccessful and cancelled pre-publication attempts remain recorded above. The published release tag and its assets are immutable.

The original complete Windows run retained its initially copied legacy fixtures: 39,108 passed, 460 skipped, 3 failures and 0 errors. Its failures were the three old root-help snapshots corrected and revalidated above; this original run is preserved rather than relabeled as a pass.

Raw workflow logs, JUnit XML/report archives, published asset identities, synthetic example outputs, the local validation producer, branch audit and preservation/cleanup receipts are retained in `build/release-v0.4.1-20261124/`.

The corrected fixtures also passed through Gradle's normal resource-copy/test pipeline on Windows: 40 tests passed, 0 skipped, zero failures/errors. The final website gate passed in the same invocation. The complete original Windows reports were archived and verified before this focused rerun.
