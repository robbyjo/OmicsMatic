Use the [bridge guide](../../docs/literature/NFCORE_VALIDATION_BRIDGE.md) to complete the exact permitted engine and plugin setups. Then export this synthetic control under Linux, including WSL:

```
python3 create_example.py --out NEW_EXAMPLE --engine NEW_ENGINE_SETUP/engine --plugins NEW_PLUGIN_SETUP/plugins
java -jar omicsmatic-0.4.1.jar nfcore-validation-tools --recipe NEW_EXAMPLE/recipe.json --home MANAGED_HOME --setup-report REPORT --out NEW_SESSION
java -jar omicsmatic-0.4.1.jar nfcore-validation-tools --verify NEW_SESSION
```

The unchanged original plugin validates the specimen identifier and threshold against the supplied schema, and its parameter-summary function is called before one local task writes `validated.tsv`. Compare the complete output with `expected.tsv`. A threshold above1 or a specimen outside the declared identifier pattern must fail validation and admit no result. The example performs no omics analysis and does not qualify an nf-core pipeline or author recipe.
