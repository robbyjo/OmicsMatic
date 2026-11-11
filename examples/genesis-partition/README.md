# Complete Java API example

From the repository root, compile and run the project-owned matrix example:

```powershell
javac --release 17 -cp docs/literature/genesis-native-development/genesis-partition-api-javac21.jar -d build/literature-review/genesis-runtime/my-demo-1/classes examples/genesis-partition/GenesisPartitionDemo.java
java -cp "build/literature-review/genesis-runtime/my-demo-1/classes;docs/literature/genesis-native-development/genesis-partition-api-javac21.jar" GenesisPartitionDemo
```

Use `:` between classpath entries on Linux. Choose a new build path. Expected output ends with `Related: [A, C]` and `Unrelated: [B, D]`. The example requires a Java17+ JDK and no R runtime; it fits no PCA or association model.

**Missing/forbidden Java: skip the affected compilation or execution.** Install a suitable JDK only where allowed, or keep the example skipped. Native/source/input failures admit no dependent result. Read the [component guide](../../docs/literature/genesis-native-development/README.md), source warnings and the unrelated-set violation flag. Full GENESIS workflows remain unfinished.
