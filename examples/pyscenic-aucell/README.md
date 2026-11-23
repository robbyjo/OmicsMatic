# Native pySCENIC AUCell component example

Build the complete current project JAR first, then compile and run this artificial
example from the repository root. Use a new output directory:

```powershell
New-Item -ItemType Directory build/pyscenic-aucell-example
javac --release 17 -cp build/cli/omicsmatic-0.4.0.jar -d build/pyscenic-aucell-example examples/pyscenic-aucell/PyScenicAucellExample.java
java -cp "build/pyscenic-aucell-example;build/cli/omicsmatic-0.4.0.jar" PyScenicAucellExample
```

On Linux/macOS, use `:` between classpath entries instead of `;`. Analysis needs
Java only. Existing release JARs may predate this component; build current source.

The example takes supplied gene signatures and computes cell activity scores;
it does not infer a network. The seed, weighted/unweighted option, cutoff,
normalization and source layout are explicit. Literal `NA` is a cell ID. Read the
diagnostics and each `eligible` flag. Low gene coverage retains source zero
placeholders; zero-column normalization preserves NaN. Neither is valid activity
inference. Negative weights are retained as diagnostics, never clamped.

Read the full component guide and source/version limits in
`docs/literature/PYSCENIC_AUCELL_COMPONENT.md`. Full SCENIC GRN/motif-pruning
workflows, capture/recomputation/replay and catalog admission remain pending.
