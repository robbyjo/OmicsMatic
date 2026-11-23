# Restorable Squidpy development snapshot

**Unqualified development work; zero new family credit or reviews.**
Read the [handover and exact next work](../HANDOVER_2026-11-22_SQUIDPY.md).
Production source, commands and catalog remain at the preceding admitted baseline.

- `drafts/` contains the exact new Java kernel and three original BSD notices.
  They are outside compiled sources. `DRAFT-RESTORE.json` maps each saved file to
  its intended original path; restore only in a clean branch for qualification.
- `helpers/` contains the seven original development helpers and one corrected
  standalone probe. The original JSON-cast failure is retained separately; the
  corrected probe produced the preliminary comparison. Install helpers contain
  machine-specific paths, create NEW directories and are not portable installers.
  Read them before use; do not rerun into an existing prefix/output directory.
- `receipts/` contains selected source intake metadata, hashed dependency locks,
  install/import receipts, synthetic inputs and original/native outputs. It
  deliberately omits full private environments, wheels, installer binaries and
  primary-paper paragraphs. Transfer the complete private evidence root separately.
- `SOURCE-AUDIT.json` records the six source candidates and exact XML/paragraph
  identities. It adds no canonical review and does not claim a whole-paper pipeline.
- `CAPTURED-FILES.json` maps saved files to their original locations and hashes.
  `FILES.json` pins every snapshot file except itself. The verifier checks those
  bytes, restore mappings, explicit qualification limits and current catalog totals.

```powershell
python -B docs/literature/squidpy-workflow-development/verify_handover.py
```

This is an offline saved-state check. It does not install software, execute the
draft, reproduce original scientific analyses or qualify an adapter. The handover
lists the known import failures, edition differences, prerequisites and remedies.
