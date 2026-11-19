# COLOC ABF handover evidence

**Tested native component; catalog admission and ordinary-summary CLI remain
unfinished.** This packet awards zero new family credit. It does not supersede
the PLINK cumulative catalog proof or qualify an entire paper/workflow.

`SNAPSHOT.json` pins the four production sources, tests/probe, example, frozen
synthetic original-reference ZIP, source auditor, readback tool and evidence.
`source-roles.json` records 27 positive roles (24 direct package roles, two
FUSION-hosted roles and one host-unspecified role), the 250 checked XML
identities and paragraph hashes. It contains paraphrases and limited version
contexts; full author XML and software runtimes remain private.

`evidence.zip` preserves original-source intake pins, final original-reference
capture status, source files with upstream attribution, candidate build pins,
saved JVM reports/stdout/stderr and selected retained failure records.
The separate 72,738-field comparison used candidate build 1; the final build 2
was tested against all 30 references in all three JVM profiles. Do not present
the earlier comparison as a fresh final-build numerical campaign.

Run this offline integrity readback from the repository root with Python 3.11+:

```powershell
python -B tools/literature/verify_coloc_abf_handover.py --out build/coloc-handover-readback-NEW
```

Exit 0 verifies packet bytes and recorded counts; it **does not rerun R/JUnit**.
Missing/unreadable exact files yield SKIP3; changed/invalid bytes yield FAIL2.
Restore the named original prerequisite, preserve errors and use a new output.
Do not refresh saved hashes to make a changed packet pass. Rerun meaningful
scientific checks after code changes. The focused JUnit command and Java
example are in the [example README](../../../examples/coloc-abf/README.md).

To repeat the source-only audit, restore all exact private XML files and Git
predecessor `2d820447e7e4b4aab414f3185916e83d5ea76a5c`, then run:

```powershell
python -B tools/literature/coloc_source_roles.py --cache D:/projects/JLinAlg/build/literature-review/xml --out build/coloc-source-audit-NEW
```

That command verifies identities and curated source anchors; it executes no
scientific program and writes no canonical metadata. An early generic version
regex rejected XML-flattened version/citation text; attempt 1 is retained.
Attempt 2 passed with exact literal contexts. This is a check correction,
not a change to scientific input or output.
