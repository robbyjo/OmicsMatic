# Checked source census; proposal only

**No canonical catalog admission or new family credit.** Read the
[checkpoint](../PLINK_SOURCE_CENSUS_2026-11-19.md) and
[96-role crosswalk](CROSSWALK.md). `CENSUS.json` retains primary identity/hash,
paragraph and author-code bindings, individual decisions, editions, method
hosts, component overlap and unresolved scope. Full paper text and author
scripts/archives are not redistributed.

[READBACK.json](READBACK.json) records actual frozen-proposal reproduction on
Windows and Linux, plus Windows reproduction with a fresh 250-paper/author-code
audit. All four generated documents matched the retained proposed bytes;
canonical metadata and source evidence remained unchanged.

`proposal.zip` retains exact before/after bytes for four proposed core metadata
documents, the auditor/model/test source snapshot and shared parser/cohort
dependencies. `QUALIFICATION.json` pins the files and records the successful
fresh source audit, 20 tests and four actual missing/changed-source controls.
Earlier diagnostic failures remain local and their status records are retained
in `diagnostics.zip`; they are not claimed as qualified scientific executions.

With Python 3.11+, reproduce the proposed metadata into a fresh directory:

```powershell
python -B tools/literature/plink_catalog/reproduce_proposal.py --out build/plink-proposal-readback-NEW
```

This frozen readback needs neither private XML/author source nor Git history.
It checks source/artifact/member hashes, preserves the old review prefix and
compares all four proposed documents byte-for-byte. It never applies them or
executes scientific software. `fresh_primary_audit` is false unless both
`--source-cache` and `--author-archive` are supplied; in that mode the exact
sources and frozen Git predecessor are also required. See the
[auditor instructions](../../../tools/literature/plink_catalog/README.md).

Missing exact source is SKIP3: audit/admission is skipped until the named
permitted source is restored. Changed source or inconsistent decisions are
FAIL2: no catalog result admitted; retain the failure, correct the cause and
retry new paths. The legacy KING 1.4/CNV roles and unresolved source editions
are not silently replaced with newer software. Complete cumulative admission
and component qualification closure before changing implementation statuses.
