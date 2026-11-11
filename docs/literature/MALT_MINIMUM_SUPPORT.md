# Native MALT minimum-support relocation

**This adds minimum-support relocation to the Java library. The complete MALT
adapter remains unfinished.** `MaltSupport` implements the May2018 MEGAN
`MinSupportFilter` operation used after taxonomic assignment. It accepts a
complete `MaltTaxonomy`, direct per-taxon `Float` weights, an already determined
integer minimum support and disabled taxon IDs. It does not select a percentage
denominator, parse RMA6, align reads, or establish biological findings. It adds
zero catalog-tool credit; the full 250-paper implementation goal remains open.

The calculation preserves the original child order and float addition. Weak
assignments and assignments to disabled nodes move to the first supporting
ancestor. Orphans above the root map to unassigned (`-2`). Negative-ID subtrees
are ignored. A strong node's own assignments remain there, and an empty returned
map means no relocation, not an empty classification.

**This is the raw source-compatible API.** Absent/null weights are zero in the
original operation; unknown or unreachable weights are ignored. Negative and
nonfinite float behavior is retained, not repaired or interpreted as valid
biological evidence. The extended controls test those source states. Callers
must retain the identities and provenance of their actual scientific inputs.

The implementation uses an immutable topology and local arrays. It avoids
recursive traversal and repeated set copying while retaining the original
relocation values. It leaves the taxonomy, weight map and disabled set unchanged
and returns an immutable map containing only changed taxon assignments.

## Evidence

Both Java21 and Java25 builds, targeting Java17, passed on Windows Java21,
Windows Java25 and Linux Java21. Each of the six configurations matched
**8,274 original-runtime cases across eight trees**:

- 8,150 cases on six small trees, including nonnegative/negative roots,
  negative internal subtrees, zero/absent/null/unknown weights, disabled nodes,
  integer support boundaries and nonfinite values.
- 100 cases on the original-runtime 65,539-node wide tree.
- 24 profiles on the complete 1,703,609-node bundled taxonomy, using the two
  control libraries' direct RMA read weights at support1,3,10,50,100 and1000,
  with and without a disabled root.

The positive-weight rounding controls reorder siblings while keeping their
weights fixed. This can change which node meets the minimum because float sums
are order-dependent. The native implementation matches that source behavior.
Two focused project JUnit tests check every small-tree reference and ensure
that topology reuse and returned mappings do not share mutable caller state.

The [validation record](malt-support-development/VALIDATION.json) and evidence
archive contain the original bytecode oracle, source selection, complete
inputs/results and compiler/runtime receipts. The executed provider is the
May24,2018 MALT0.4.1 image with bundled MEGAN6.11.5. Historical source context is
Daniel H. Huson's GPL3-or-later `MinSupportFilter` at MEGAN commit
`24e70bf6cd0a434ff40a62b98cf0b54d72954908`; this is not a claim that a nightly
binary was built from that exact author commit. No upstream JAR, image or
complete private engine bundle is redistributed.

## Replay and use

From the repository root, select a **new** output directory:

```powershell
python tools/literature/verify_malt_support.py --out build/literature-review/nfcore-runtime/my-malt-support-check-1
```

The verifier checks source hashes, archive contents and saved original/JUnit
receipts, then independently compiles and reruns all 8,274 native cases in six
configurations against the complete saved references. It does not rerun the
original provider or JUnit. It leaves the canonical executable unchanged. SHA256
checks provide integrity, not a digital signature.

It checks runtime/compiler major versions before executing the matrix. If your
permitted installations are elsewhere, pass `--java21-bin` and `--java25-bin`
with their `bin` directories, and `--linux-distro` with the WSL distribution
containing Linux Java21 at `/usr/bin/java`. Version observations are retained in
`VERSIONS.json`; the requested labels are not accepted as version evidence.

**Missing runtimes skip their dependent replay; failed checks admit no result.**
The verifier names the missing runtime or failed comparison and retains
diagnostics. Supply the permitted named JDK21/JDK25 and Linux Java21 environment,
or correct the named input, and retry in a new directory. Do not interpret a
skip or failure as a negative biological finding. Ordinary library use needs
Java17 or later and does not require upstream MALT.

Given the complete taxonomy and the actual direct per-taxon weights:

```java
var filter = new org.jlinalg.metagenomics.MaltSupport(taxonomy);
var weights = java.util.Map.of(9606, 3f, 37349, 4f);
var relocations = filter.relocate(weights, 5, java.util.Set.of());
int finalTaxon = relocations.getOrDefault(originalTaxon, originalTaxon);
```

Apply the returned map once to each read's original assignment. Do not replace
direct weights with subtree totals, omit weak taxa, or deduplicate reads.
These are development sources; an unchanged older released JAR does not contain
this class. The earlier [native filtering/LCA component](MALT_NATIVE_ASSIGNMENT.md)
and its retained failures remain a separate immutable checkpoint. Percentage
threshold selection, RMA input integration and the complete MALT adapter remain
open work.
