# RepeatMasker host/provider verification: development status

**Current source snapshot (2026-10-31): experimental public integration.** The admission CLI, typed original SEARCH path and completion verifier now compile, but their public end-to-end validation is unfinished. The validation records below describe earlier component gates. Do not treat the new SEARCH path as validated or use its output for scientific conclusions yet. SEARCH without an admitted runtime still returns SKIP3. RepeatMasker remains outside the completed tool count.

**Public original SEARCH is still unavailable and returns SKIP3.** The host
inspection below is an internal development component. It does not admit a
scientific runtime, certify a paper reconstruction or change the tool count.
The catalog remains at 53 new validated integrations, 56 including existing
adapters, and 266 missing integrations. Cell Ranger remains excluded.

The native `repeat-tools` CHECK, supplied-annotation MASK and supplied-mask
AUDIT workflows remain available. Read [the native status](REPEATMASKER_NATIVE_STATUS.md)
and [the recipe guide](REPEATMASKER_RECIPES.md) for their exact scope.

## What the Java implementation checks

`RepeatHost` discovers the actual default Perl import paths and the pinned,
isolated historical Python import paths. `RepeatHostFiles` reads complete
declared module trees, including empty directories, hidden files, symlink
targets and explicit absent roots. It hashes complete file contents, records
file modes and actual readability/executability, and detects changes during
the census. Hardlink aliases consume their full logical byte capacity. A hash
cache belongs to one census and cannot survive an original process execution.

Helpers, loader configuration, character-conversion modules, locale data,
NSS providers, relevant configuration lookups and Python startup sentinels
form part of the declared inventory. Known log-destination nodes record their
presence, type and mode without reading unrelated log contents. Metadata and
hashes are retained; configuration contents are not copied into the report.

`RepeatHostElf` reads bounded ELF64/x86-64 headers, interpreter paths and actual
DT_NEEDED names through their file-backed string-table mappings. It checks
every reported `ldd` row and each declared needed name. Original addresses are
retained in raw logs; ASLR addresses do not define a binding identity. A required
loader can be reported without a `name => path` arrow. Library paths containing
`..` retain their original spelling and are resolved against the filesystem
before any lexical normalization, including h5py's `$ORIGIN` paths.

Each probe uses the owned process supervisor with explicit process, workspace
and member capacities. `ldd` may return 1 for an independently checked image
with no needed libraries. Original searches and postprocessing still require
exit 0. A disappearing procfs descriptor is checked against the process's
identity and terminal liveness; an unreadable live process remains an error.
Two identity reads prevent assigning metrics across PID reuse. RSS is sampled,
not an OS-enforced aggregate memory quota. Process limits apply per probe;
fingerprinting is outside a child-process wall-time bound.

The ELF interpretation follows the primary [ELF header](https://gabi.xinuos.com/elf/02-eheader.html),
[program header](https://gabi.xinuos.com/elf/07-pheader.html) and
[dynamic linking](https://gabi.xinuos.com/elf/08-dynamic.html) specifications.

## Validation and remaining work

The expanded family contains 278 tests; see [fixed health status](REPEATMASKER_HEALTH_STATUS.md). Exact private-JAR checks pass on
Windows Java 21/25 and Linux Java 21. Windows runs 272 tests and skips six
Linux-only filesystem/process cases; Linux runs 277 and skips the Windows-only
host-refusal case. Strict Java 17 compilation and private Javadoc checks pass.

Original process/file access traces cover both editions, positive and genuine
no-hit cases, and the actual more-than-ten-million-base compression boundary.
Original masks and independent annotation-parser references remain separate
from host inspection. The failed large trace exposed a real procfs exit race;
its diagnostics and empty owned-process cleanup result are retained alongside
the successful fresh replay. The earlier loader-only and h5py-path capture
failures are also retained.

Independent Python checks compare full filesystem membership, file bytes,
permissions and logical counts. GNU `readelf` independently checks ELF metadata.
Both profiles pass a second, fully re-derived host capture. Independent checks
cover 3,227 member occurrences, 185,457,469 logical bytes and 379 ELF images
for the current profile, and 4,702 member occurrences, 288,619,871 logical bytes
and 485 ELF images for the historical profile. Ancestor directories retain only
their own type/mode metadata; their unrelated children are not read. Canonical
absent/directory nodes do not broaden regular-file or symlink-target policies.
The access review covers five fresh controls, including real cwd changes and
actual directory-fd paths, with no unresolved or unaccounted selected paths.
Fresh internal runtime admission now captures both profiles with the latest
exact candidate and verifies persisted identity in a separate JVM. Earlier
captures retain their own original candidate identities.
Private evidence is under
`build/literature-review/repeatmasker-runtime/host-*`; these development
artifacts are not a public release or an upstream download.

**Remaining gates:** expose the [verified internal runtime API](REPEATMASKER_RUNTIME_STATUS.md),
wire
the typed public SEARCH workflow and its completion verifier, then apply the
reviewed paper-role updates and rerun catalog/browser gates. Inspection alone
cannot satisfy these gates. Hashes establish integrity, not authenticity or
biological calibration. The broader 250-paper/toolbox goal remains active.

## Required recovery

**Missing, forbidden or unsupported prerequisites: skip the affected analysis
with SKIP3.** Use the documented permitted Linux x86-64 profile, inside WSL on
Windows. If installation is forbidden, retain the warning and skip; do not
substitute an edition or interpreter. [Exported payload setup](REPEATMASKER_PAYLOAD_SETUP.md)
requires explicit installation/download authorization and does not admit a
runtime. Analysis and host inspection never install software.

**Input, integrity, execution or capacity errors: FAIL2; partials are not results.**
Read `WARNINGS.md`, `HOST_FAILED.txt`, retained stdout/stderr and process
ledgers. Preserve failed directories and their source/candidate identities.
Correct the named cause or reviewed capacities, then use NEW paths. Never
repair a sealed payload in place or infer biological absence from missing
outputs. Temporary test homes can disappear; actual dependency homes must be
checked again rather than inferred from an old receipt.
