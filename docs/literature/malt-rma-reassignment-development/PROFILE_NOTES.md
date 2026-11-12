# Qualified command profile and original standalone defaults

The command in [the usage guide](../MALT_RMA_REASSIGNMENT.md) uses the fixed eager software-control profile: top percent 1 and minimum-support percent 0.01, with explicit minimum score 50, maximum expected value 1, minimum identity 0 and naive LCA coverage 80. These defaults are parameters of this qualified component profile.

The original standalone MALT 0.4.1 `MaltOptions` defaults are top percent **10** and minimum-support percent **0.001**. They are distinct from the eager-control parameters. To request those two values explicitly in this command, add `--top-percent 10 --minimum-support-percent 0.001`. The complete standalone MALT alignment/assignment workflow is not qualified by this parameter selection.

The original `MaltRun` bytecode confirms that a positive integer minimum support sets minimum-support percent to zero; integer zero restores the base minimum of one while retaining percentage selection. Its help text describes a percentage of assigned reads, but the default serial `DataProcessor` implementation accumulates the denominator over all stored reads after the readCount calculator. The Java component follows the implementation, retaining warnings and the stored-read scope.

[The option-source audit](command-options-audit.zip) retains the complete official historical `MaltRun.java` and `MaltOptions.java`, source URLs/hashes and the actual pinned JAR's disassembly. The source context is [MALT commit 5984e067](https://github.com/husonlab/malt/tree/5984e0672000604e8ef0b5ebb2a41f0ad00fb67d). The JAR's exact bytes, rather than an assumed source-build identity, define the runtime comparison.

The 32 original-processor profiles and packaged-command controls in the validation record retain their explicitly declared values. This clarification changes no input, assignment, threshold or tool-credit count. It supplies no full MALT, nf-core or biological admission.
