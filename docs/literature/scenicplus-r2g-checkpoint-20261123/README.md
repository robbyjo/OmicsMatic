# SCENIC+ R2G component saved-summary integrity

This public packet includes summaries and identities, not the licensed vendor
source/runtime, original traces or private captures. It claims no full SCENIC+
family, paper reproduction, new family credit or new reviewed use. The masked
Pearson source failure is retained and is not a qualified analysis.

From the current source checkout:

```powershell
python -X utf8 -B tools/literature/verify_scenicplus_r2g_checkpoint.py
```

Optionally supply the **exact** retained complete producer and local private
evidence root to check their hashes as well:

```powershell
python -X utf8 -B tools/literature/verify_scenicplus_r2g_checkpoint.py `
  --jar build/scenicplus-r2g-390/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar `
  --private-root build/scenicplus-r2g-390
```

This command checks current source and saved evidence integrity. **It does not
rerun tests or perform a new scientific fit.** For actual scientific recomputation,
run Java VERIFY against a private completed capture, using the exact producer
and permitted runtime, with a NEW disjoint witness output. The
[example](../../../examples/scenicplus-r2g/README.md) supplies copyable commands.

Read the [checkpoint](../SCENICPLUS_R2G_CHECKPOINT_2026-11-23.md) for exact scope,
warnings and remaining work. Do not refresh saved hashes to permit substitution.
