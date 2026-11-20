# Saved native Pearson component proof

From the repository root, choose NEW output:

```powershell
python -B tools/literature/verify_scanpy_pearson.py --out build/scanpy-pearson-readback-NEW --restore-component
```

The reader checks current source/fixture pins, all evidence and component JAR
payloads, the 720 original-package cases, the three 735-test XML records and
before/after equality of both original runtime inventories. It performs no new
Java, Python-package, original-model, network or installation work. Optional
restoration writes only the exact small development library into NEW output.
SHA-256 checks integrity against saved evidence, not coordinated forgery or
authorship. Saved reference checks are not biological or paper reproduction.

The [example](../../../examples/scanpy-pearson/README.md) compiles/runs with that
JAR. It has no `java -jar` Main-Class. It contains native Pearson and unchanged
shared matrix classes plus notices, requires only `java.base`, and does not
launch an original runtime. The normal source build passed separately; the
public 0.4.0 release predates the new API.

Native versions retain different zero-count behavior: 1.10.4 NaNs remain
diagnostic/unreportable, while 1.13.0's zero convention is flagged. Inspect
`usable()` and warnings before downstream use. A complete matrix is refused
for nonfinite/undefined or lost positive model terms even if clipping looks
finite. Do not repair values, silently change profiles or add a variance floor.

**SKIP3:** restore the named exact permitted missing/unreadable dependency;
skip its dependent audit/capture until then. **FAIL2:** preserve changed/invalid
evidence, correct the cause and retry NEW output. Never refresh old hashes.
Original installed environments, full candidates, setup archives and large
private data need separate permitted transfer. Exact recovery/transfer and
ordered remaining work are in the
[checkpoint](../SCANPY_PEARSON_CHECKPOINT_2026-11-19.md) and
[guide](../../vignettes/scanpy-pearson-native.md). Scanpy catalog admission and
the wider goal remain open; this component adds no family credit.
