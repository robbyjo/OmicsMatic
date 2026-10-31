# FAVOR offline annotation

`favor-annotate` runs the pinned original offline FAVOR converter and annotation
scripts, then checks every joined annotation cell and the original GDS contents.
It exports an audited scalar table and a matching cell-state table. Each run
first repeats scientific health checks on fixed synthetic inputs.

**Use Linux x86_64, including Linux Java inside WSL on Windows.** Native Windows
execution skips this analysis with exit code 3. The current implementation is
an offline CSV annotation adapter. It does not reproduce an entire historical
paper, acquire annotation databases, calculate new annotation scores or provide
the FAVOR website, cloud or SQL services.

**The original full-table exporter is unsupported in this tested runtime.** Use
`analysis/export/values.tsv` together with `cell-states.tsv`, the retained raw
joined CSV and canonical files. Read `WARNINGS.md` before interpreting results.
Character missing values can become empty strings upstream; stored date/time
values are numeric and their original classes and units remain in provenance.

## Prerequisites

Work from this repository checkout. The setup helpers need Python 3.9+ on Linux.
The actual analysis needs a Java 21 runtime, a private R/Conda prefix matching
the bundled lock, and the pinned GenomeInfoDbData source archive. Preparation
also uses a Linux Java 21 **JDK** to execute its small source probe.

The pinned software includes R 4.4.3, gdsfmt 1.42.0, SeqArray 1.46.0,
readr 2.2.0, jsonlite 2.0.0, digest 0.6.39, GenomeInfoDbData 1.2.13 and xsv
0.13.0. The exact 154 Conda artifacts, URLs and SHA-256 values are in
[pixi.lock](../../../tools/literature/favor-runtime/pixi.lock). The Java and
preparation policies also check the local host libraries and kernel.

**If installation or required software is forbidden or unavailable, skip the
dependent analysis.** Do not bypass the operating environment's restrictions.
The analysis, inventory preparer and example generator never install software
or download study data. A missing, altered or forbidden runtime produces an
actionable `SKIP3`; invalid inputs and exceeded capacities produce `FAIL2`.

Where private installation is permitted, the bundled
[Pixi manifest](../../../tools/literature/favor-runtime/pixi.toml) and lock
describe the tested environment. Copy both into a **new private project** and
install from that lock with your permitted Pixi installation:

```sh
mkdir /path/to/NEW_FAVOR_HOME &&
cp tools/literature/favor-runtime/pixi.toml /path/to/NEW_FAVOR_HOME/ &&
cp tools/literature/favor-runtime/pixi.lock /path/to/NEW_FAVOR_HOME/ &&
pixi install --no-config --locked \
  --manifest-path /path/to/NEW_FAVOR_HOME/pixi.toml
```

Use `--offline` with an existing permitted Pixi cache when downloads are not
allowed. These are operator-run prerequisite installation commands; no general
installer is invoked by `favor-annotate`. Do not enable global post-link-script
execution. If the data package has not been materialized, install the pinned
archive explicitly into the new private R library:

```sh
printf '%s  %s\n' \
  '51a33c432e65261ef81a7c2039b17cbdf7a0b417376fdb7ada4d618566f7074e' \
  '/path/to/GenomeInfoDbData_1.2.13.tar.gz' | sha256sum --check &&
R_ENVIRON_USER=/dev/null R_PROFILE_USER=/dev/null \
  /path/to/NEW_FAVOR_HOME/.pixi/envs/default/bin/R CMD INSTALL \
  --library=/path/to/NEW_FAVOR_HOME/.pixi/envs/default/lib/R/library \
  /path/to/GenomeInfoDbData_1.2.13.tar.gz
```

The archive is available from the official
[Bioconductor 3.20 package location](https://bioconductor.org/packages/3.20/data/annotation/src/contrib/GenomeInfoDbData_1.2.13.tar.gz).
Only obtain it where downloads are permitted, or use a permitted offline cache.
Its required SHA-256 is
`51a33c432e65261ef81a7c2039b17cbdf7a0b417376fdb7ada4d618566f7074e`.
The preparer verifies this value; a missing or different archive skips setup.

The two unmodified offline scripts are bundled under
`tools/literature/favor-runtime/upstream`, with their upstream README, GPLv3
license and source manifest. Their pinned commit is
`dbd6ffcc11b01beab32a2d46e09a0798fd335544`. An alternative checkout is supported
only when the selected script bytes match the pinned policy. No upstream script
is patched to repair its original behavior.

## Prepare the current host

Create an existing ASCII work parent without whitespace, then choose a **new**
inventory directory. Replace the example paths below. `--runtime-root` points
to the installed Conda prefix, not the parent Pixi project.

```sh
mkdir -p /path/to/work
python3 tools/literature/prepare_favor_runtime.py \
  --runtime-root /path/to/NEW_FAVOR_HOME/.pixi/envs/default \
  --java /path/to/java21/bin/java \
  --data-archive /path/to/GenomeInfoDbData_1.2.13.tar.gz \
  --out /path/to/work/inventory-1
```

The helper checks the package/source pins, performs small synthetic R/Java
probes, hashes the complete runtime trees and records host files and loader
bindings. It writes `inventory.json`, `preparation.json`, `WARNINGS.md`, probe
logs and loader traces. Its result is explicitly **not scientific admission**.
The analysis command will verify the files again and run fresh scientific
health checks. Preserve the entire preparation directory.

After a host, kernel or recorded software update, use a new inventory directory
and repeat preparation. An old inventory is retained as evidence. Do not edit
its old hashes to make it pass.

## Run the synthetic example

This example uses artificial variants, samples, scores and gene labels. It is a
software check and contains no real biological build or study finding.

Build the current local executable from the repository root:

```sh
./gradlew executableJar
```

The output is `build/cli/omicsmatic-0.3.7.jar`. Historical published JARs may lack
this new command. The tested local candidate and exact hash are recorded in
[the development evidence](FAVOR_ANNOTATOR_DEVELOPMENT.md).

Use the SHA-256 printed by preparation to generate a new request. The generator
reads the repository fixtures and bundled original scripts, verifies their pins,
and prints the new request's SHA-256. It does not start analysis.

```sh
python3 tools/literature/make_favor_example.py \
  --inventory /path/to/work/inventory-1/inventory.json \
  --inventory-sha256 INVENTORY_SHA256 \
  --out /path/to/work/example-request-1.json \
  --analysis-out /path/to/work/example-analysis-1

/path/to/java21/bin/java -jar build/cli/omicsmatic-0.3.7.jar favor-annotate \
  --request /path/to/work/example-request-1.json \
  --request-sha256 REQUEST_SHA256
```

Replace both SHA-256 placeholders with the actual values. Do not reuse existing
request or analysis outputs. The example must finish with exit code 0,
`session.json` status `complete-audited-offline-analysis`, 8 requested variant
rows and 48 requested scalar values. Two additional fixed health profiles run
first. All 15 original R processes and 9 nested xsv commands must succeed.

Expected scalar output SHA-256 values for the requested example are:

| File | SHA-256 |
| --- | --- |
| `analysis/export/values.tsv` | `543463e383d6cd3e8ee1d6d81feba8d6dc534ff417aa6bcbdb249e47af1f7d0d` |
| `analysis/export/cell-states.tsv` | `a886659a392c87e3d11cad0628495ad7c8db638fbb1876ff8e37ae7fa570e7d5` |

Keep the entire output. It includes the original VCF and reference snapshots,
unmodified converted GDS, annotated working GDS, raw joined CSV, native row
ledgers, canonical scalar files, software/configuration snapshots, health
records and process logs. An inner scalar export or R exit zero alone does not
establish parent completion.

## Use study inputs

The complete request format and capacities are described in
[FAVOR_ANNOTATOR_DEVELOPMENT.md](FAVOR_ANNOTATOR_DEVELOPMENT.md). Supply protected
SHA-256 values for the VCF, original DBsplit CSV and every ordered selected
shard. All paths resolve relative to the request file.

Choose one autosome 1–22 and an explicit variant-only or diploid-GT profile.
Use at least two selected shards. Query and annotation builds must agree.
Variant keys, allele representations, source order, duplicates and no-match
provenance remain explicit. A source join that drops, multiplies or reorders
queries fails before R health or annotation; the workflow never repairs that
attachment silently. Input paths can contain Unicode. Work/output paths must
be new, canonical ASCII paths without whitespace.

## Warnings and recovery

| Result | Meaning | Required action |
| --- | --- | --- |
| Exit 0 and complete parent status | All declared checks passed | Retain the full bundle and interpret source warnings and cell states. |
| `SKIP3` | Missing, forbidden, unsupported or changed prelaunch dependency | Provide the permitted pinned software, archive or source; create a new inventory and output. Skip this analysis until admission succeeds. |
| `FAIL2` | Invalid request/data, unsafe join, execution, capacity or post-launch integrity failure | Inspect `WARNINGS.md`, `session.json` and original logs; preserve diagnostics, correct the named cause and rerun to new paths. |
| Interrupted parent or shutdown ledger | Analysis is incomplete | Inspect the recorded owned-process state and provisional session; confirm termination and rerun to new paths. |

Source `Date` values are stored numeric days since 1970-01-01; tested POSIXct
and hms values use seconds with retained source timezone/unit provenance. Empty
annotations do not prove absence of biological effect. Read the original joined
CSV, `cell-states.tsv` and export provenance together.

## Relation to the paper catalog

P130 reports FUMA and FAVOR jointly, positional transcription-site mapping and
separate MAGMA gene/gene-set analyses. It does not establish which FAVOR scoring
functions or historical annotator revision produced each result.
[Primary methods](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549054/).

This adapter supplies current pinned offline FAVOR annotation. The existing
[native transcript-site workflow](TRANSCRIPT_SITE_MAPPING.md) and
[MAGMA workflows](MAGMA_WORKFLOWS.md) have their own input and model contracts.
Do not infer that an annotation export reproduces gene association,
gene-set enrichment or the paper's entire jointly described pipeline.
