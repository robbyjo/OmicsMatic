# Enrichr source census and API-component evidence

**This qualifies current-service API components and saved response parsing.
The general file workflow and cumulative catalog admission remain unfinished.**
Read the [checkpoint](../ENRICHR_COMPONENT_CHECKPOINT_2026-11-19.md) and
[API guide](../../vignettes/enrichr-api-component.md) before using results.

`CENSUS.json` preserves all 250 XML identities, 44 named paragraphs, 25 curated
roles, declared clients/editions/library labels and explicit uncertainties.
It proposes three missing observations and 23 potential new reviews; it does
not write the canonical catalog or replace its two old Enrichr reviews.

`UPSTREAM-SOURCES.json` records exact primary-source URLs and hashes, including
the source/API specification and client snapshots. `API-PROTOCOL.json` preserves
the live POST-versus-documented-GET discrepancy and current odds-ratio naming.
Missing optional license/empty terms-page responses are recorded as limitations,
not interpreted as permission to redistribute the original server/client code
or complete gene-set libraries.

`QUALIFICATION.json` pins the exact Java source/tests, Python source/readback
helpers, synthetic fixture, example and guide/checkpoint. `evidence.zip` holds
the original synthetic protocol captures, 64 focused test executions, saved
source/control outcomes, runtime versions and the exact small development
component JAR with the project license. The full downloaded library and raw
paper text stay private; their hashes/transfer paths are retained. This is not
a complete upstream runtime, source-paper result or official software release.

From the repository root:

```powershell
python -B tools/literature/verify_enrichr_component.py --out build/enrichr-readback-NEW
python -B tools/literature/verify_enrichr_component.py --out build/enrichr-restore-NEW --restore-component
```

The standard-library Python verifier checks all declared live source hashes,
packet hashes, archive member set/CRC/payload hashes, census/test populations
and component identity. Optional restoration creates a NEW output directory
and writes only the validated component JAR/license inside it. It does not
recontact Enrichr, refit scientific models or freshly audit private XML.
Follow the [synthetic example](../../../examples/enrichr-api/README.md) to
explicitly upload its eight predefined symbols and capture a fresh service run.
Use the source census helper's `--cache`/`--out` options with the original exact
private XML cache for a fresh source audit.

**SKIP3:** restore an exact missing permitted prerequisite or retain the skipped
dependent work. **FAIL2:** preserve changed evidence, invalid outputs and errors,
correct the cause and retry NEW outputs. Do not refresh hashes, substitute
another library or overwrite earlier evidence. SHA-256 establishes integrity,
not authentication against coordinated replacement. Preserve the linked
historical handovers and their unresolved marker-runtime skips.
