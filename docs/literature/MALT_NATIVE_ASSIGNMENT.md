# Native MALT taxonomy and assignment component

**This is a Java library component, not a complete MALT adapter.** It implements
the original May2018 numeric taxonomy addressing, active-hit filters and
unweighted LCA assignment. It does not align sequences, read or write RMA6,
relocate assignments for minimum support, apply taxonomic-rank identity
adjustments, or establish biological findings. No catalog-tool credit is added;
the full 250-paper implementation goal remains open.

`MaltTaxonomy` reads the complete ordered numeric tree used by the original
runtime's bundled `ncbi.tre`. It appends the original default no-hit,
unassigned and low-complexity nodes when absent. The reader refuses unsupported
Newick dialects, duplicate IDs, malformed input or insufficient explicit
character, node or depth capacities. It never truncates the tree. Taxon names
and ranks are not required or inferred by this component.

`MaltAssignment.assign` takes the original total match count, every available
match in its original order, a score/expectation/identity filter, the shared
disabled-taxon set, coverage percent and ancestor policy. Match scores,
expectations and identities retain source `float` precision and nonfinite
comparisons. Duplicate hit addresses count separately. Coverage uses the
original float multiplication before rounding upward. The 100% common-prefix
path and the original treatment of ancestor addresses remain distinct from
the lower-coverage path. Large sibling sets use the original two-character
addresses, including an unassigned result if the selected prefix is not a node.

`assignActive` exposes the original separate LCA operation on an explicit active
bit set; `naiveLca` retains the original collection method's singleton behavior.
An assignment of `-1` means the original match count was zero; `-2` means no
assignment was possible. Neither value is a negative biological finding.

## Validation and retained failures

The corrected original-runtime audit loads `ncbi.map` and `ncbi.tre` from
MALT0.4.1's bundled `data.jar`, not the older database taxonomy. It reproduces
the saved final assignments for **all 172 reads and 175 matches stored in both
control RMA files**. Those RMA files contain aligned reads; this count does not
claim classification of every original input query. The actual workflow used
minimum support1, and the audit verified equality with its final assignments.

Both Java21 and Java25 compiler builds, targeting Java17, passed on Windows
Java21, Windows Java25 and Linux Java21. Each of the six configurations matched:

- Every ID, parent, child count and address in the complete **1,703,609-node**
  original bundled taxonomy.
- A separate **65,539-node** original-runtime wide-tree control, including the
  two-character address carry and the source's unmapped-prefix assignment.
- **12,275 synthetic cases**, including duplicate and ancestor hits, coverage
  rounding, disabled taxa, explicit active sets, score boundaries, absent
  matches, unknown IDs and nonfinite values.
- Every active-hit set and assignment for the complete 172-read RMA population.

Three focused project JUnit tests check malformed/capacity-limited trees,
incomplete match populations, disabled filtering, explicit fallback, and
defensive result copies. The [validation record](malt-native-assignment-development/VALIDATION.json)
and evidence archive retain the exact source and execution receipts.

**Two failed native-control attempts are retained and superseded.** The first
had 918 synthetic differences because its API/control design incorrectly
treated the active and assignment disabled sets as independent. The original
Taxonomy runtime aliases those sets; the corrected API and oracle verify that
relationship. The second matched all numerical and assignment values but its
expected RMA table used Windows line endings. A new run fixed that harness
serialization; no scientific values or earlier evidence were overwritten.
The two earlier runtime/taxonomy audit failures remain in the separate
[historical assignment audit](MALT_ASSIGNMENT_AUDIT.md).

## Replay and use

From the repository root, use a **new** output directory:

```powershell
python tools/literature/verify_malt_native_assignment.py --out build/literature-review/nfcore-runtime/my-malt-native-check-1
```

This checks the archive, source hashes, original execution receipts and retained
failures, then compiles and reruns the native component in all six configurations
against the complete saved references. It does not rerun the original MALT
binary or JUnit. It creates a separate class directory and leaves the canonical
or released executable unchanged. The archive contains data, source and
receipts; no upstream JAR, container image or complete private engine bundle is
redistributed. SHA256 checks establish integrity, not a digital signature.

**A missing runtime skips its dependent replay; an error admits no validation.**
The verifier names the missing prerequisite or failed comparison and preserves
diagnostics. Supply the permitted JDK21/JDK25 runtimes and named Linux Java21
environment, or correct the named input, then retry a new directory. Do not
reinterpret a skip or failure as an assignment or biological result. Ordinary
library use needs Java17 or later and does not require upstream MALT.

For programmatic use, open the complete numeric tree with a buffered reader,
choose capacities large enough for the complete input, and supply the actual
upstream match fields, not alignment raw scores substituted for bit scores:

```java
try (var input = java.nio.file.Files.newBufferedReader(treePath)) {
    var tree = org.jlinalg.metagenomics.MaltTaxonomy.read(input,
        new org.jlinalg.metagenomics.MaltTaxonomy.Limits(100_000_000, 3_000_000, 1024));
    var matches = java.util.List.of(
        new org.jlinalg.metagenomics.MaltAssignment.Match(9606, 100f, 0.001f, 99f, false));
    var result = org.jlinalg.metagenomics.MaltAssignment.assign(tree, 1, matches,
        new org.jlinalg.metagenomics.MaltAssignment.Filter(50, 1, 1, 0f),
        java.util.Set.of(), 80f, true);
    System.out.println(result.taxon());
}
```

These classes are development source and are not present in an unchanged older
release JAR. Algorithm attribution is Daniel H. Huson, MEGAN's `ActiveMatches`,
`AssignmentUsingLCAForTaxonomy` and `LCAAddressing`, under GPL3-or-later. The
historical source selection is commit
`24e70bf6cd0a434ff40a62b98cf0b54d72954908`; the executed provider was the
May24,2018 MALT0.4.1/MEGAN6.11.5 source image. Proximity of checked-in version
strings or dates does not establish a binary's author commit. The archive
retains both source context and the actual original-binary references.
