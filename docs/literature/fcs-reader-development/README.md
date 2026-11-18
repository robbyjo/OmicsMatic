# Native stored-channel FCS reader checkpoint

Use the [API guide](../../vignettes/fcs-stored-channels.md) for complete-input
capacities, stored-value units, explicit legacy encoding and TEXT-padding
policies, unsupported capabilities and recovery. This is the shared event-input
component for subsequent cytometry workflows. There is no `fcs-tools` command,
gate execution, compensation or vendor-workspace/instrument implementation in
this checkpoint. The released 0.4.0 artifact is unchanged.

Six focused tests pass on Windows and Linux. These are the same six tests on
two platforms, containing 25 independent Python-struct storage cases, including
15 complete accepted cases and ten explicit refusals. They cover both byte
orders, mixed integer widths, unsigned/masked integers, IEEE floats/doubles,
negative/over-range/nonfinite values, Unicode and literal `$`/delimiter values,
explicit encoding/padding choices, empty input, data preceding TEXT, complete
population/capacity checks and refusal to finalize an incomplete/changed stream.

The final packaged API matches every stored value against original FlowIO
1.4.0 in four complete upstream control files: 101,945 events and 469,994
values. The comparison hashes the entire canonical IEEE64 value stream. The
24-bit integer capability is covered separately by the independent literal
storage cases. Two original offset-discrepancy files are refused by both
readers. One legacy file is accepted by the original parser but skipped by the
native profile because of an ambiguous trailing metadata token. This known
difference remains explicit; that file is not counted as native success.

One legacy file needs explicitly selected ISO-8859-1. One vendor fixture needs
explicit trailing-space-padding support. Another points supplemental TEXT to
the primary segment itself; the alias is decoded once with its warning retained.
Original files, identities and raw failures remain unchanged. No stored value
is imputed, clipped or transformed to make a comparison pass.

The packaged build adds eight reader/API classes and their package-directory
entry. Every pre-existing JAR member is byte-identical to the preceding qualified
ATAC source-build JAR. `VALIDATION.json` records the exact artifact identity,
test counts, comparisons and scope. This supplies native API availability; it
does not qualify an entire FlowJo/FACSDiva workflow or add catalog family credit.

`controls.zip` is an ordinary ZIP with size/SHA-256 `INDEX.json` records for
every payload. Every member is read back and checked. It contains current new
code, the independent complete storage fixtures/generator, source census,
original reader-source archive and license, selected command/test receipts,
retained failures and the final full-file comparison references. Compiled JARs,
NumPy and original vendor fixture binaries remain separately provisioned and
retained privately; the ordinary ZIP is not a runtime distribution.

The pinned complete reference files can be retrieved without installation:

```sh
python3 tools/literature/cytometry/intake_reader_reference.py --out NEW_REFERENCE_DIRECTORY
```

The source lock supplies the exact edition, source commit, source archive and
seven complete original file hashes/URLs. This optional development intake is
separate from native analysis. If retrieval is unavailable or forbidden, skip
its dependent reference comparison and retain the named failure. Native use
does not need Python or those external packages. See `WARNINGS.md` and
`UPSTREAM_CREDITS.md` for the retained discrepancies and reuse boundaries.

The frozen census verifies all 250 primary XML hashes and finds 48 name-matching
paragraphs in 33 unique papers (FlowJo27/FACSDiva17). It is a source-name census,
not 44 independent computational workflows or completed role review. Acquisition,
sorting, analysis models, plugin workflows and particular gating choices remain
separate; the full 250-paper implementation goal remains open.
