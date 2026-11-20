# Native GSEA workflow source-counterpart checks

The [workflow guide](../../../docs/vignettes/gsea-native-workflow.md) describes
the standalone RUN/VERIFY/REPLAY interface and explicit prepared-data contract.

`prepare_fixture_requests.py` verifies all 624 frozen original option/boundary
fixture members, selects the 423 source-admitted cases and generates explicit
prepared JSON and batch recipes. It retains source class/member order, options
and source reference rows. It does not infer paper settings or invoke a vendor
file parser. The existing original fixture contains 201 strict refusal cases;
the native engine regression retains those checks separately.

`check_results.py` independently decodes the BGS1 output objects and compares
their literal identities/order, score bits, double metric bits, membership,
observed profiles, hit positions, every raw null ES and family statistics with
the original API exports. It does not call native project arithmetic. The byte
entry points allow a saved-evidence reader to check frozen original counterpart
objects without extracting files or launching Java.

Read saved workflow qualification:

```powershell
python -B tools/literature/verify_broad_gsea_workflow.py --out build/gsea-workflow-readback-NEW
```

This saved reader checks integrity, original output fields and qualification
records. The workflow's semantic VERIFY additionally recomputes the captured raw
inputs using its exact compatible owned Java classes. Neither establishes
whole-tool, author-data, biological or deployment equivalence. The producer JAR
and complete private evidence require separate transfer; no release is created.
