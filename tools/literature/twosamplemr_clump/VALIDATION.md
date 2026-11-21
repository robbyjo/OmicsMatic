# Original local-clumping validation programs

The [checkpoint](../../../docs/literature/TWOSAMPLEMR_CLUMP_CHECKPOINT_2026-11-21.md)
defines the supported scope and evidence. `compare_original.R` performs fresh
unmodified-package comparisons of captured full objects/RNG endpoints.
`PackagedTwoSampleMrClumpTests.java` checks normal-JAR class origins and runs
707 focused source-statistic/workflow/clumping tests. These do not count as
whole-tool, historical-edition, biological or genome-wide performance validation.

`validation/` retains the actual Windows development/qualification scripts.
They name this machine's absolute historical worktree/runtime paths and use
fail-on-exist outputs. **Read and adapt them to a fresh isolated checkout and
NEW output directories; do not blindly rerun generators over saved evidence.**
The source/fixture generators target repository files. The lifetime-only timeout
harness starts a sleeping owned process and checks complete cleanup; it is not
a scientific R analysis. The unqualified-binary control mutates a private copy
and proves refusal before execution. Runtime binaries and complete generated
builds remain separate private artifacts.

For a read-only saved check with Python3.11+, use
`tools/literature/verify_twosamplemr_clump_checkpoint.py --out NEW_DIRECTORY`.
That reader runs no scientific tools. Preserve all original failures,
superseded attempts and raw inputs. Runtime/dependency restoration or changed
host/source editions require their own original execution qualification.
