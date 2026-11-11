# nf-core engine foundation evidence

This checkpoint records the separate primary-config Java engine and native
literal input-path planner. Read [the guide](../NFCORE_FOUNDATION.md) before use.

`VALIDATION.json`, `controls.zip` and `SHA256SUMS` describe the recorded builds,
native controls, three-engine execution, cached resume, standalone verification,
failure admission and source-bound paper census. The archive retains failures
and later corrections. `nextflow-engine-dependency.jar` contains 67 unchanged
workflow helper classes/resources from the separately frozen Nextflow candidate,
plus GPL license/notice text; it contains no original Nextflow engine or JRE.

The two `nfcore-engine-javac*.jar` files are standalone development artifacts.
The canonical executable and live catalog remain unchanged. **nf-core remains
missing; tool-level credit requires the complete original pipeline contracts.**
These controls do not certify biological accuracy or reproduce a paper.

To verify the checkpoint from the repository root:

```text
python tools/literature/verify_nfcore_engine_checkpoint.py
```

To rebuild, choose a new directory under `build/literature-review/nfcore-runtime`:

```text
python tools/literature/build_nfcore_engine.py --compiler javac21 --out build/literature-review/nfcore-runtime/NEW_BUILD
```

The builder uses the pinned public helper dependency and preserves every entry
of the validated scDRS predecessor. Read all retained warnings and required
actions. Hashes establish integrity, not authenticity.
