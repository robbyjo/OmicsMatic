# Snakemake deployment verification and installation policy

**Current source update:** private managed bundle and replay qualification is
recorded later in this guide. The newer public commands still need complete
runtime qualification; see [SNAKEMAKE_WORKFLOWS.md](SNAKEMAKE_WORKFLOWS.md).
The opening checkpoint below describes an earlier frozen code edition.

**WARNING: developer qualification only. Captured managed execution now binds
inputs, original job events, software payloads and outputs to its process policy.
Final managed bundle verification/replay, the public original-engine CLI and
catalog admission remain unfinished. The final managed analysis/bundle entrypoint
still produces SKIP3 before execution. These controls add no tool integration
credit and reproduce no paper.**

`SnakemakeOriginalDeployment` independently verifies the adopted Conda 26.9.4
and Apptainer 1.5.5 providers in Java. It checks the complete adoption metadata
and source-control export census, exact selected archive/license declarations,
installation/adoption cross-bindings, every installed file byte and POSIX mode,
symlink target and resolution, unresolved package-cache links, and host-library
bytes and aliases. Before/after member identities reject changes during a full
check. Eight bounded workers perform file checks. Kernel, architecture and real
and effective UID must match the adoption; otherwise skip dependent execution
and adopt the exact permitted software on the intended host into new paths.

The tested adoption contains 29,625 Conda and 733 Apptainer tree entries, 282
active ELF objects and 58 external library paths. Raw package-cache payloads and
the retained Debian source tree remain sealed as data. This does not capture a
complete operating system or arbitrary dynamically loaded workflow dependencies.
Java controls accept the genuine adoption and an unchanged small fixture, and
reject changed bytes, missing/unlisted members, changed modes, changed links,
newly resolved dangling targets, special files, malformed nodes/pins, false
analysis claims and contradictory metadata. One damaged copy has its changed
provider manifest and all installation/adoption hashes resealed; the actual
unchanged installed bytes still expose the contradiction. These are integrity
checks, not an authorship signature or security sandbox.

The developer `deployment_policy.py` instruments the selected original
`Env.create` and `Image.pull` entrypoints. Every permitted call executes the
original function; environment hashing, activation, solving, archive installation
and image pulling retain the upstream algorithms. Permission decisions record
the original module/function/source hash, selected path, whether installation is
required and whether the original method was called. Scoped bindings are restored
on exit; changed bindings are an integrity failure.

Nine real-engine controls passed across Conda, Apptainer and combined deployment:
three fresh installations with permission, three complete software reuses with
installation forbidden, and three missing-software skips with installation
forbidden. Both Conda creation controls used genuine original-engine package
archives; the container control pulled the immutable selected OCI manifest.
Existing qualified environments, image and provider trees remained unchanged.
Twelve original-method edge controls additionally check restoration, nested and
reused scope rejection, preserved incomplete prefixes, original return values,
original failures and dry runs. Small method fixtures are not runnable software
environments; the nine separate engine controls supply actual execution evidence.

**Required action on SKIP3:** skip the dependent analysis. Restore the complete
qualified environment/image at its original selected location, or enable
installation only where permitted and retry new output/setup paths. Conda hashes
include the real absolute installation directory: copying an installed prefix to
a new directory does not qualify it for reuse. A package archive still requires
installation. An incomplete prefix must remain retained when installation is
forbidden; the permission guard prevents the original method from removing it.

**Required action on FAIL2:** retain original inputs, partial prefixes, raw events
and diagnostics. Resolve the named integrity discrepancy and retry new paths.
Do not refresh old hashes, remove warnings or silently substitute an engine,
provider, solver or deployment mode. Original channel-priority warnings and
intentional missing-software error logs remain retained with their control labels.

The scoped guard does not constrain arbitrary trusted workflow code, filesystem
races or independently started programs. The spawned-process protocol below is
separately qualified; it is not yet connected to Java workflow admission. Use `--help` on
`qualify_providers.py`, `qualify_installation_policy.py` and `compile_probe.py`
for developer control inputs. They require the selected qualified software,
complete earlier native source fixtures and new persistent output paths; they
are not user analysis commands. See the
[original-engine development guide](SNAKEMAKE_ORIGINAL_DEVELOPMENT.md) for setup
and the remaining admission gates.

Current local development evidence is retained under `build/snakemake-original/`:
`provider-controls-2`, `installation-policy-1`, `installation-policy-review-1`,
`provider-package-1` and `provider-packaged-controls-1`. The private package passed
34 Snakemake Java tests; all previously packaged members remain byte-identical
to the published baseline. Its provider classes pass a genuine adoption and
reject a resealed contradiction, and its reader verifies an earlier portable
bundle without executing Snakemake. The first incomplete unit fixture lacked a
source-cache interface; its failed test source remains retained. Its temporary
directories were not found when archival was attempted. Later unit fixtures use
persistent native directories and a separate exported archive. No recovery of
the unavailable earlier fixtures is claimed.

## Propagation into original spawned engine processes

The selected original local executor starts separate engine processes for shadow
jobs and `run:` directives. Its general arguments omit logger plugins, and the
original CLI deliberately disables plugin log handlers in subprocess mode.
Appending a logger flag alone therefore did not start a child policy. The first
failed source control and its complete records remain retained.

The project hook calls the unchanged original local executor's
`additional_general_args` method, then appends `--logger omicsmanaged` to the
returned arguments. The original CLI constructs the selected plugin's
`LogHandlerSettings` even when child log handlers are disabled. That settings
initialization starts a per-process installation policy. The controller closes
its policy after the original API returns; child processes close theirs at exit.
Original child log suppression remains unchanged. Parent events and complete
original stderr are retained alongside separate child policy records; no extra
child log handlers are enabled.

Twelve original-engine controls passed across Conda, Apptainer and combined
deployment using shadow jobs: three complete reuses with installation forbidden,
three missing-software skips in the controller, three permitted fresh installs,
and three missing-software skips in a spawned child. The child controls use an
original `onstart` hook after deployment to temporarily move software created
specifically for the fixture. The child refuses reinstallation, the process
exits, and the coordinator restores the software. Before/after complete tree
records agree. Earlier qualified environments, images and both provider trees
also remain unchanged; they are never moved by these fault controls.

`SnakemakeOriginalInstallationPolicy` independently reads the protocol in Java
without executing an engine. It checks the explicit run, permission and selected
interpreter; caller-supplied process ownership; complete opened/closed file pairs;
one child policy per formatted local spawn; selected upstream source hashes;
permission decisions and original-call status; Conda prefix/setup-flag bindings;
the original image URL/cache hash; and required skip recovery text. It accepts
the twelve source families and a valid independently resealed copy. Seventeen
contradictions are rejected, including a resealed permission change, another run,
unowned/missing children, removed skips, changed original-call claims and changed
spawn arguments, an image falsely relabeled local and an impossible successful
external-environment creation. Metadata hashes are recomputed in the damaged copies: these
checks test semantic consistency as well as byte integrity.

**WARNING: an earlier development reader accepted a resealed remote-image record
relabeled as local.** Its frozen classes, accepted damaged copy and failed
qualification remain retained. The corrected reader derives the selected Linux
URI classification and rejects this contradiction; it also preserves the
original assertion failure for an invalid external-environment `create` call.
Use newly qualified readers and records. Do not refresh old hashes to make an
earlier reader or evidence family appear corrected.

The reader checks ownership against the set supplied by its caller. The new
package-private `SnakemakeOriginalManagedProcess` derives that set from the actual
owned-process supervisor's completed ledger. It binds every reported policy PID
to an observed PID/start-time identity, rejects ambiguous PID reuse, checks the
controller's actual arguments and verifies complete cleanup. It also checks the
selected engine and provider trees before and after execution and materializes
the exact bundled project helpers. Twelve fresh original-engine controls passed
through this Java path, covering the same three deployment modes and four
permission scenarios. These are process and policy controls; the separately
qualified captured-execution path is described below. Final managed bundle
verification, replay and public analysis admission remain unfinished.
Missing, unclosed or contradictory records prevent acceptance of
the process/policy receipt. Current raw
records are retained in `propagated-policy-2` and the current propagated-reader
control folders under
`build/snakemake-original/`; `qualify_propagated_policy.py` and
`qualify_propagated_records.py` describe the developer inputs with `--help`.

**WARNING: the first Java-supervised fresh image pull was stopped while unpacking
a legitimate container-root symlink.** Its failed native staging tree, original
stderr, supervisor ledger and incomplete controller record remain retained in
`supervised-policy-1`; no result was accepted. The corrected observer treats
explicit owned container staging and package-cache directories as software data.
It counts symlink text and special-file metadata without following payload links
or opening devices. It still enforces file-count and byte limits. Workflow links
outside these declared data directories retain the original containment checks.
Eight separate controls cover payload links, workflow escapes, replacement of a
declared directory, overlapping scopes and file/byte limits. A fresh complete
twelve-case Java-supervised family passed in `supervised-policy-2`.

Software-data observation is not complete software capture or a security
sandbox. The final managed analysis/bundle entrypoint still returns **SKIP3**. Use the supported
preinstalled development path only within its documented scope; retain all
warnings and failed attempts, and use new output paths after resolving a named
failure. Do not interpret `SUPERVISED-POLICY.json` as a completed user analysis or
refresh older receipts to make changed code appear qualified.

## Effective original managed workflow profiles

The schema-2 development controller now reads the selected original parser's
actual settings after its workflow-profile reparse. It checks nineteen explicit
recipe settings, including deployment methods, both cache prefixes, the Conda
frontend, Apptainer arguments and the project logger, together with the named
disabled report, destructive and partial-DAG controls. Java independently checks
the same complete setting census and the original option-source provenance.
Actual settings come from the original parser; they are not copied from the
request or inferred from CLI text.

A conflicting profile produces **SKIP3 before the original API is called**.
The project explicitly opens the controller's scoped installation-policy record
before parsing, since the original logger settings are constructed later inside
`args_to_api`. An early rejection closes that actual policy with no deployment
calls, no workers and no event collectors. Java requires those zero-call and
zero-worker conditions and checks the controller's PID/start-time identity in
the actual supervisor ledger. Missing software after a valid parser policy is a
different skip: the API was called, and its installation-policy records identify
the blocked original entrypoint. Both cases retain their original diagnostics.

The selected parser stores deployment methods as a set. The unchanged recorder
sorts set values, so combined deployment is recorded as `APPTAINER, CONDA` even
when the CLI lists `conda apptainer`. A source audit verifies the original set
type and recorded representation. This changes the expected metadata contract;
it does not replace the parser or reorder an author's workflow.

Twenty-seven packaged original-engine controls passed across all three managed
modes: plain workflows, resource profiles, explicit CLI precedence, partial-DAG
profiles, environment-module profiles, dry-run profiles, missing-software skips,
and two legacy process-only request cases. Independent review checks 39 actual
policy-process identities, twelve original local spawns, nine pre-API profile
rejections and six blocked deployment calls. The original qualified environments,
images and both provider trees match their earlier before/after records.

The packaged portable reader passes 21 genuine schema-2 records and a separately
reencoded positive copy, and rejects 21 damaged contracts or success/skip claims.
These checks run on Windows with an empty `PATH` and no engine execution. The
private JAR passes forty Snakemake Java tests and verifies an existing
preinstalled-workflow bundle without changing that bundle or executing an engine.
Current records are retained under `managed-parser-packaged-2`,
`managed-parser-review-1`, `managed-parser-records-1` and
`managed-parser-package-2` in `build/snakemake-original/`.

**WARNING: earlier attempts failed because a shadow fixture omitted its script
input, an early rejection lacked an explicit controller policy record, and the
initial combined-method contract used CLI ordering for a recorded set.** Their
source snapshots, actual packages, original diagnostics and failed controls are
retained separately. None is reclassified as a passing current family or admitted
as analysis. Use the current qualified helper/JAR edition and NEW output paths.
For a rejected profile, inspect the named setting and remove the conflict or
select a separately qualified mode. For missing software, restore the complete
permitted software or use separately qualified permitted installation. Never
refresh an older record's hashes to make changed code pass.

This is a parent parser/process-policy gate. It does not establish compatibility
with every Snakemake profile option or complete final managed bundle verification,
replay, a public original-engine CLI, or the six paper roles.
Schema-1 process-only controls remain readable but do not establish this new
parser contract. Top-level managed workflow analysis still returns **SKIP3**;
Snakemake receives no new catalog credit from these development controls.

## Captured managed execution

The development `execute` path now accepts schema-2 recipes with explicit
`managed_software` fields: `providers`, `conda_environments`, and
`container_images`. The provider path names a qualified adoption. Each cache
field is either a canonical existing prefix or `null` for a new owned prefix.
With installation permitted, both cache declarations must be `null`: existing
borrowed prefixes remain immutable inputs. With installation forbidden, missing
software produces **SKIP3** and no accepted output. Installed Conda prefixes are
used at their actual absolute paths; the adapter never relocates them to reuse
an environment under a different original-engine hash.

Execution captures the complete declared workflow/data roots and preserves typed
configuration and explicit output bindings. It records software before and after
the original run, binds original controller events to supervised process/policy
records, requires all observed scheduled jobs to finish, and captures every
declared output. A missing declared output prevents acceptance even when the
original engine exited successfully. Caller inputs and borrowed software are
verified unchanged. Selected runtime and provider trees are verified before and
after execution; their complete adoption metadata is retained.

`SnakemakeOriginalSoftwareCapture` stores every regular-file byte in the owned
content-addressed store and records complete directory/member, mode, file identity,
literal symlink, FIFO, socket and device metadata. It neither follows payload
links as host paths nor opens device/FIFO/socket streams. This is a literal
payload capture, not a seal of arbitrary external dependencies loaded by trusted
workflow code or a security sandbox. Use the qualified persistent native Linux
filesystem/profile; a portable metadata reader does not establish execution
support on its host.

Eighteen packaged original-engine controls passed across Conda, Apptainer and
combined deployment: six successful captured executions using existing software
or permitted fresh installation, six expected missing-software/profile skips,
and six expected upstream-failure/missing-output refusals. They exercise external
input rebinding, a large integer, a tiny numeric parameter, shadow workers,
original software activation/container execution, complete events and exact
expected output values. No author dataset or biological result is certified.

The private package passes fifty Snakemake Java tests. Ten native software-tree
controls include literal links, FIFO/socket/device metadata, unchanged-timestamp
byte changes, mode/link/census changes, limits, corrupt objects and contradictory
shared-blob lengths; no device control was skipped on this host. Nineteen portable
software-tree controls run without an engine. The preinstalled path additionally
passes twelve fresh bundle controls, a two-generation replay and fifteen portable
bundle checks with the changed request/event classes.

**WARNING: the first fresh container pull in the captured-execution matrix hit
the fixture's 64 MiB per-process file limit.** That failed run, its partial
software, source stderr and original request remain retained. `limits.file_bytes`
bounds each captured regular file; `limits.output_file_bytes` becomes the hard
per-process file-size limit and also applies to downloaded OCI layers and SIF
creation. Set both sufficiently high for the selected software and keep an
explicit total workspace budget. The corrected matrix uses 1 GiB for each of
these file limits and new native/output prefixes. Never edit the failed request
or refresh its evidence hashes to make it pass. A separate native fixture first
exceeded the Unix socket pathname limit; its failed family is retained and the
complete retry uses a shorter socket basename.

Current development records are under `managed-capture-controls-3`,
`managed-capture-package-1`, `software-capture-controls-2`,
`software-capture-portable-2` and the `managed-capture-preinstalled-*` families in
`build/snakemake-original/`. Exported case object files may share storage through
hard links to `shared-objects`; each remains a regular hash-verified file.
The producer writes `EXECUTED.json` with `user_analysis_admitted=false` only after
its captured-execution checks pass. It writes no managed final bundle completion
marker. The separate managed bundle/replay path is described below; public CLI
and paper-role qualification remain required before catalog admission.

## Portable managed bundles and genuine replay

The development managed bundle path now copies complete control evidence and
the exact union of captured input, working-state, software/cache and output
objects into a standalone bundle. It checks a provisional bundle independently
before writing the completion marker atomically. A complete physical file and
directory census, content-addressed closure, reader/producer edition, declared
parameters, selected software, original parser settings, source process records,
closed per-process installation policies, original job events and declared
outputs must agree. The reader reconstructs the managed binding from the raw
records; it does not accept the saved binding or a zero process exit as proof.

Software payload links and device metadata stay literal. Portable verification
does not open the original Linux paths, execute the engine or interpret container
links as links on the reader's host. The selected JDK ZIP provider is supported
as an archive-byte namespace; ordinary filesystem reads retain their strict
no-follow operation. ZIP verification rejects duplicate/traversing member names
and checks the complete outer archive bytes before and after reading. Hashes
establish integrity and consistency, not a signature or trusted-host attestation.
Arbitrary extra dependencies loaded by trusted workflow code remain outside the
declared capture scope. These checks do not establish biological validity or
reproduce an author's environment or complete paper.

The current private package passes **51 Snakemake Java tests** and the website
check. Its eighteen fresh original-engine controls cover all three deployment
modes: six completed bundles, six expected missing-software/profile skips and six
expected upstream-failure/missing-output refusals. A separate Python review
checks all eighteen raw capture closures and actual process identities.
Twenty-two Windows controls accept genuine/reencoded evidence and an unordered
policy-process set, while rejecting nineteen contradictory or damaged copies.
All six mode/permission combinations also have standalone archives verified by
the Windows reader with an empty executable search path.
The negative copies have rebuilt physical manifests, including resealed cached
bindings where relevant; the reader must still reject inconsistent original
settings, process ownership, jobs, outputs, software, provider editions and
warning claims. Neither a Linux runtime nor the producer paths are available to
that Windows reader.

The changed shared classes also pass twelve fresh preinstalled controls, their
two-generation original-engine replay and fifteen portable bundle checks. The
older captured-execution packages and receipts remain frozen; a new reader
edition does not justify refreshing an old bundle's code hashes.

Genuine managed replay materializes the captured workflow/data, preserves the
declared settings and typed parameters, and calls the original engine again.
Borrowed software must still exist at its original prefix and match its complete
captured state. With installation permitted, the new run uses NEW owned prefixes
and the original deployers; it never relocates an installed Conda environment.
Both complete software records are retained. Installation prefixes, software
tree timestamps and prefix-bearing software bytes can differ between permitted
fresh installations. Fresh installation follows the unchanged declared
environment specification and available upstream artifacts; byte-identical
dependency sets are not certified. Inspect both captured software trees and use
pinned packages or captured source archives for version-specific comparisons.
Declared exact outputs still have to match byte for byte:
an output containing an old absolute Conda prefix is not silently normalized.
For a path-independent scientific comparison, declare stable analysis outputs in
a NEW recipe and retain software provenance in the complete working-state
records. A changed output contract starts a new workflow history; it does not
retroactively make an earlier failed comparison pass.

**WARNING: missing or forbidden prerequisites skip the dependent replay.** Read
`FAILURE_WARNINGS.md` and `FAILURE.json`, restore the named permitted prerequisite
at its declared location, or perform separately permitted setup, then retry NEW
output. An upstream execution error, incomplete capture, contradictory evidence
or output comparison mismatch is retained as **FAIL2**, with no matching replay
admitted. Preserve the parent, new result, stderr, policies and partial bundle;
an earlier nested success cannot replace a failed outer replay.

Twelve genuine original-engine replay generations now pass: two generations for
each of the three deployment modes, under both software reuse and permitted
fresh installation. The controlled original workflow/data directories are
temporarily unavailable throughout each pair of runs and restored afterward.
The workflow still computes the expected result from captured inputs. Two
separate prefix-bearing output controls retain **FAIL2** comparison mismatches;
a missing-provider control produces **SKIP3** before a new execution starts.
For output mismatches, both the new result and failed outer replay remain
available for inspection.
Original provider/cache trees remain unchanged. All six complete two-generation
chains, including their parents and both results, also pass offline Windows
verification from standalone ZIPs with an empty executable search path.

Current development evidence uses `managed-bundle-package-1`,
`managed-bundle-controls-2`, `managed-bundle-native-review-1`,
`managed-bundle-conda-archive-1`, `managed-bundle-portable-1` and
`managed-bundle-preinstalled-*` under `build/snakemake-original/`.
The replay controls are in `managed-bundle-replay-controls-1`; all six initial
and replay archive readers are recorded in `managed-bundle-archive-matrix-1`
and `managed-bundle-replay-archives-1`. `managed-bundle-closure-1` retains the
private reader, probe, source snapshot, validation records and byte-pinned
archives with shared storage. Its `HOW-TO-VERIFY.txt` gives an offline verification
example. Earlier compiler/control editions and the original failed ZIP preflight
remain separate; their hashes were not refreshed.
`qualify_managed_capture.py --bundle --defer-export` retains complete native
records for compact archival; deferring export is not analysis completion.
`archive_managed_capture.py --include-bundles` preserves all raw cases and
completed bundles with shared blobs. `archive_workflow_bundle.py` produces a
standalone ZIP without copying live installed prefixes. Archive failure requires
a NEW archive path; do not repeat an analysis merely because export failed.

**Public managed analysis and catalog admission remain gated.** This is a
development qualification path, not a new supported public analysis command.
It adds no Snakemake catalog credit; the six paper roles and the broader
250-paper objective remain pending their respective implementation/review work.
