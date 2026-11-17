# Snakemake deployment verification and installation policy

**WARNING: developer qualification only. Managed execution through the Java
workflow adapter, propagation of policy to nested engine subprocesses, complete
managed-software capture/replay, the public original-engine CLI and catalog
admission remain unfinished. Managed Java analysis requests still produce SKIP3.
These controls add no tool integration credit and reproduce no paper.**

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

The scoped guard currently covers the instrumented controller process. It does
not constrain arbitrary trusted workflow code, filesystem races or independently
started subprocesses. Nested engine propagation must be qualified before the
managed Java execution path can be admitted. Use `--help` on
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
