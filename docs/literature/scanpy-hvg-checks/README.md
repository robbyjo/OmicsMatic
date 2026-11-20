# Saved native dispersion-HVG qualification

From the current source root, use Python 3.11+ and NEW output:

```powershell
python -B tools/literature/verify_scanpy_hvg.py --out build/scanpy-hvg-readback-NEW --restore-component
```

This checks source/evidence pins, every archive/component member, the two
unmodified original-reference populations, runtime before/after inventories
and 2,574 tests per JVM for both normal JAR and isolated component on Windows
Java 21/25 and Linux Java 21. The 2,560 reference cases include 1,632 original
returns and 928 source errors; error cases must remain failures, not repaired
or silently omitted inputs. Fourteen further controls cover admission,
missing annotations, capacities, immutability and cancellation.

The reader runs no Java, scientific model, installation or network request.
It restores only the exact development library when requested. Run the
[synthetic example](../../../examples/scanpy-hvg/README.md) with a JDK 17+;
native computation needs only `java.base`. Full normal JARs, runtime prefixes,
all original capture attempts and setup receipts need separate permitted
transfer. Published 0.4.0 predates this API.

**SKIP3:** restore the exact named permitted readable dependency and retry NEW
output, or retain the dependent skip. **FAIL2:** preserve changed/invalid
evidence and diagnostics, correct the cause and retry NEW output. Do not
refresh old hashes. Source-defined zero-variance missing annotations have an
explicit warning/flag; other invalid model arithmetic admits no selection.

Read the [API guide](../../vignettes/scanpy-hvg-native.md) and
[checkpoint](../SCANPY_HVG_CHECKPOINT_2026-11-19.md) for exact limits, source
differences and retained failures. Integrity hashes are not signatures or
proof against coordinated fabrication. No full package/paper or biological
claim is made, and cumulative Scanpy admission remains open.
