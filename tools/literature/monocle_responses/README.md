# Monocle response source controls and qualification

The active Java implementation is under src/main and its tests under src/test.
See [the current command guide](../../../docs/literature/MONOCLE_RESPONSES.md).
The development directory retains the exact **historical P98 draft** and its
then-missing-fixture warning; do not restore that older copy over current sources.

reference.py/reference.R generate exact official-source controls using the
declared private R4.3.3/Monocle1.0.0/spdep1.4-1 environment. They retain local
sibling-owner assumptions and are not portable installers. freeze.py admits only
a completed guarded reference and writes a NEW fixture directory. The argument
probe and saved-model diagnostic preserve the serialization and degeneracy
findings; neither changes installed source or repairs an original result.

qualification/build.py builds the normal source JAR and runs focused tests.
packaged.py checks the actual JAR on three JVM hosts and compares old payloads.
examples.py exercises six complete response cases and the public native example;
stage_graph.py, stage_neighbors.py and stage_weights.py exercise exact global
routes. Each helper requires NEW output and retains commands/errors. The graph
and neighbor execution helpers need their separate exact original runtime.

qualification/verify.py verifies the saved public checkpoint with Python3.11+
without executing R, Java or models. Its --restore option extracts under NEW
output only. The preceding verify_handover.py belongs to P98; run it on the
actual unchanged predecessor owner or exact P98 checkout, without hash refresh.
