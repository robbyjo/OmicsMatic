# Small original MEME discovery example

**This is a validated scoped development workflow.** Read
[`MEME_DISCOVERY_WORKFLOW_DEVELOPMENT.md`](../../docs/literature/MEME_DISCOVERY_WORKFLOW_DEVELOPMENT.md)
for its current admission status, exact source profiles, limits and recovery
instructions. Released JARs do not contain this new command.

Prepare these requests with Linux Python before starting a long admission run:

```sh
python3 examples/meme-discovery/create_example.py \
  --profile MEME_5_5_9 \
  --build-evidence /absolute/path/to/completed-source-build-evidence \
  --out /absolute/path/to/new-meme-example
```

The helper installs and executes nothing. It writes unchanged copies of the
small synthetic DNA/background controls plus complete scientific and operation
JSON files. It reads the original compiled home from `COMPLETED.json`; the Java
adapter subsequently verifies that build and installation. Base 4.9.1 and patch2
require their own distinct profiles and build evidence.

Run it using the validated development JAR:

```sh
meme_jar="/absolute/path/to/meme-discovery-root-development.jar"
java -Xmx768m -jar "$meme_jar" meme-tools \
  --request /absolute/path/to/new-meme-example/discover.json
java -Xmx768m -jar "$meme_jar" meme-tools \
  --request /absolute/path/to/new-meme-example/verify.json
```

Execution uses the original exact-edition MEME program after a fresh scientific
and report health check. Java separately audits the declared raw-input and
scientific-output contracts. The bundled 60-sequence planted-motif population
is reused by health and this example; it does not establish biological accuracy
or reproduce a paper. Edit the recipe explicitly to declare your own inputs,
background, occurrence model, strand choice, motif widths and limits.

**Read `result/WARNINGS.md` and both original stderr files.** For downstream use,
require overall `COMPLETED.json`, no incomplete/failure marker and a successful
VERIFY. On Windows, copy the complete result bundle and write a VERIFY operation
whose `bundle` is a Windows absolute path, such as `D:/copied-meme/result`.
Verification opens captured files only and creates no new-host runtime ticket.

**SKIP3:** a required runtime, dependency or capability is unavailable or
forbidden. Supply it only on a permitted host; otherwise skip this analysis.
**FAIL2:** an input, integrity, capacity, process, cancellation or finalization
error admits no result. Preserve the native worker and partial bundle, follow
the named failure and required actions, and retry into NEW paths. Do not rewrite
old runtime prefixes or evidence hashes to make an installation appear valid.
