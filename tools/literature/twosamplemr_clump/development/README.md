# Unfinished local-clumping development

**Do not compile or expose these drafts as a working command.** See the
[current handover](../../../../docs/literature/HANDOVER_2026-11-21_LOCAL_CLUMPING.md).

The two Java skeletons and resource directory were relocated byte-for-byte
from `src/main` for handover. Contracts, Files and Audit classes are missing.
The R worker is a tested prototype, not a qualified Java adapter. None of these
files grants new integration or paper-review credit.

`scripts/` preserves the original development generators and R body. They use
absolute historical paths and fail-on-exist directories. Review their source
and adapt paths to fresh output before running; the Java generator still targets
`src/main` and must not be run without completing the missing classes and review.
`compare_original.R` in the parent directory is the independent original-call
comparison. The saved 53-return comparison applies to original-controls-3, not
the later 25-case suite. All failed/superseded attempts remain in the packet.
