# SCENIC+ through the main command

Build the current source and use one complete executable JAR with Java 17 or newer:

```text
java -jar omicsmatic-0.4.0.jar scenicplus-tools --help
java -jar omicsmatic-0.4.0.jar scenicplus-tools --request /absolute/path/request.json
java -jar omicsmatic-0.4.0.jar scenicplus-tools --inspect /absolute/path/completed-capture
```

The source build is required. An older release download need not contain this
command. This is an upstream adapter to the original, privately installed
SCENIC+ v1.0a2 runtime. The Java router does not install software or select
references. RNA-only SCENIC has its separate `scenic-tools` command.

| Request profile | Operation | Inputs and complete contract |
| --- | --- | --- |
| `scenicplus-1.0a2-prepare-v1` | `UNPAIRED` | [Unpaired metacells](SCENICPLUS_PREPARE_WORKFLOWS.md): explicit RNA, a trusted genuinely fitted original cisTopic object and shared group labels |
| `scenicplus-1.0a2-motifs-v1` | `MOTIFS` | [cisTarget, DEM and cistromes](SCENICPLUS_MOTIF_WORKFLOWS.md): prepared data, region sets and explicit local motif databases/annotations |
| `scenicplus-1.0a2-r2g-v1` | `REGION_TO_GENE` | [Region-to-gene](SCENICPLUS_R2G_DEVELOPMENT.md): expression, accessibility and supplied search-space candidates |
| `scenicplus-1.0a2-network-v1` | `NETWORK` | [TF2G, R2G, eGRNs and activity](SCENICPLUS_NETWORK_WORKFLOWS.md): prepared matrices, search space, TFs, cistromes and ranking references |

Use each linked contract's JSON example, explicit local interpreter path and NEW
output directory. Pass that JSON to the main command shown above. The router
reads the exact `profile` for RUN and delegates the original arguments to the
corresponding component. The component still validates every field. Unknown
profiles, contradictory operations, extra fields and mixed main/router/component
JAR origins fail; no version or algorithm is inferred.

For a completed capture, create a new request file containing:

```json
{
  "schema_version": 1,
  "mode": "VERIFY",
  "analysis": "/absolute/path/completed-capture",
  "out": "/absolute/path/NEW-verify-witness"
}
```

Pass this file to `scenicplus-tools --request`. REPLAY uses the same structure
with `"mode": "REPLAY"` and another NEW output. The saved canonical profile
selects the route. Relative analysis paths resolve against the request's parent
directory. The component checks the complete parent closure and exact producer
before executing fresh original computation. The parent stays read-only.

`--inspect` checks saved seals, inventories and identities only. A user who edits
results can also recompute those hashes. Inspection is not scientific validation
or authentication: require fresh VERIFY for numerical comparison. Keep the exact
whole producer JAR with every capture. A later JAR cannot inspect an older
capture by refreshing saved hashes; use the retained original producer.

## Warnings and required actions

- **SKIP3 / exit 3: dependent analysis skipped.** Supply the named permitted
  exact runtime or input and retry a NEW output. If installation is unavailable
  or permission remains unresolved, retain the skip. No scientific result is
  supplied by a skipped analysis.
- **FAIL2 / exit 2: no complete result.** Preserve raw, partial and failed
  evidence. Fix the named source, input, capacity, deadline or integrity cause
  and retry a NEW output. Read `analysis-status.json`, `WARNINGS.md`, console
  warnings, reports and numerical eligibility ledgers.
- **Original masked-array and empty-cistrome failures remain failures.** A
  source empty-category merge can raise `set.union()`; supply biologically
  justified supported annotations with original returned TFs in both categories,
  or retain FAIL2. Do not manufacture empty matrices or zero correlations.
- **Original DEM may ignore an unbalanced background cap.** Inspect actual
  selected counts and `source-warnings.json`. The balanced option needs an
  explicit promoter annotation. Changing it changes the requested analysis.
- **ClusterBuster genome scanning remains SKIP3 pending confirmed runtime
  permission.** Prepared motif databases can be analyzed by the separate motif
  operation. Providing SCENIC+ permission does not establish ClusterBuster terms.
- **Undefined source values remain undefined.** Keep NaN/Inf, source omissions,
  per-field flags and warnings. Never replace them with a defined zero, repair
  identifiers silently or equate missing version/seed settings with defaults.
- **Keep the accepted runtime private and unmodified.** Its accepted terms cover
  internal academic research. Keep licensed source, caches, fitted objects and
  source-bearing traces out of public Git. Check permission for any transfer,
  including a complete WSL export. Loading an untrusted cisTopic pickle executes
  code; class/axis checks do not authenticate it.

Paired preparation, annotation/search-space generation and genome scanning are
outside these prepared workflows. The available profile does not establish
equivalence to an unknown paper edition, an author's environment or a whole
paper's biological findings. SEACells metacells and SCENIC+ same-label random
metacells remain distinct methods. Cell Ranger remains excluded by the user.

See the [dispatch checkpoint](SCENICPLUS_GLOBAL_CHECKPOINT_2026-11-23.md) for
the exact tested producer, retained historical contexts and outstanding catalog
admission work.
