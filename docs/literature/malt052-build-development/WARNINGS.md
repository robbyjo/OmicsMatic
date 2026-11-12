# Warnings and required actions

**Original accession-map indexing is unqualified.** This component uses the
explicit `native-accession-alternative`. Keep the original unclassified index
and the separate derived index. Do not describe the result as unchanged original
MALT0.5.2 accession mapping or the authors' exact index.

**Unavailable or forbidden prerequisites mean SKIP3 and no dependent analysis.**
Original fitting needs the exact permitted Linux x86_64 MALT0.5.2/Java14 provider,
`setsid`, `prlimit` and sufficient declared resources. Build the controller with
a permitted JDK21/25. Use the documented setup only where download/install is
allowed; otherwise skip. Do not substitute a different upstream version or a
generic aligner. Saved seed verification needs only the Java component; combined
alignment replays additionally need the exact reader jars and HTSJDK dependency.

**Invalid or incomplete data mean FAIL2 and no result.** Blank-prefixed FASTA or
an empty record can make the original loader exit zero with zero references.
Duplicate first-word IDs, non-ASCII sequence bytes, missing/conflicting mapping,
unknown taxa, altered files or exceeded complete-input capacities are refused.
Retain raw inputs, `FAILURE.json`, `WARNINGS_FAILURE.md`, partial outputs and full
process diagnostics. Resolve the named issue through an explicitly versioned
complete input or a justified larger capacity, then retry a NEW directory. No
prefix, header, accession, sequence or population is silently repaired or dropped.

**Gapped references are unsupported by the native mapping stage.** The source
DNA5 gap byte is preserved by the FASTA audit, not converted to N. Do not remove
gaps automatically to make mapping pass. Supply an appropriate complete ungapped
genomic reference for the intended analysis, retaining the original and the
method decision, or skip the dependent analysis.

Two upstream seed-table files differ between the single-file and multi-file
productions. Both are retained and downstream complete scientific outputs are
compared. No cross-production bitwise identity of seed tables is claimed.

Rehashed metadata do not override actual command/limit consistency. Deliberately
corrupted metadata fixtures are retained as refusals. Historical producer sources
are preserved separately from the current stricter verifier sources. Captured
consistency and SHA256 integrity do not authenticate an unsigned producer.

All controls are narrowly scoped software checks. Full MALT/HOPS/MaltExtract,
the paper's91-reference database, full initial pathogen screening and biological
reproduction remain incomplete. This component adds no whole-tool credit or
biological-result admission. An empty match set or a skip is not biological absence.
