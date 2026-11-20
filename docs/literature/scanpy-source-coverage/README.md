# Scanpy source roles and partial coverage

`COVERAGE.json` maps all 22 frozen Scanpy paper roles to useful tested current
function counterparts, native Pearson matrix profiles where applicable, and
the exact remaining author roles. It preserves the 250 primary identities,
53 named paragraphs, literal reported editions/operations and P061's previous
review identity. Twenty-one review candidates await cumulative admission.

**A current counterpart is not an attribution of that function, flavor,
default or edition to an author. No full reported role or complete author
pipeline is qualified by this crosswalk.** Unspecified Pegasus/scArches roles
have no assigned function. The provided-object role does not acquire a de novo
fit. Cell Ranger remains excluded; its namesake Scanpy HVG flavor is separate.

The deterministic owner is `tools/literature/scanpy_coverage.py`. To generate
a comparison in NEW output, from the repository root:

```powershell
python -B tools/literature/scanpy_coverage.py --out build/scanpy-coverage-NEW
```

The owner requires the exact frozen census and unchanged source decisions.
The [global-command proof](../scanpy-dispatch-checks/README.md) checks this
crosswalk against its source and evidence pins. The
[checkpoint](../SCANPY_DISPATCH_CHECKPOINT_2026-11-19.md) records tested scope
and remaining implementation/admission work. No canonical catalog metadata is
written; totals remain 136 scoped integrations and 1,241 pending reviews.

**SKIP3:** restore the exact named permitted source/evidence and skip its
dependent audit until then. **FAIL2:** preserve the changed/invalid evidence
and diagnostics, correct the named cause and retry NEW output. Do not rewrite
source statements or refresh historical hashes to obtain a passing result.
