# GSEA research/build handover packet

**No GSEA statistical execution, project adapter or new catalog credit is
qualified by this packet.** Start with the
[current handover and remaining tasks](../HANDOVER_2026-11-20_GSEA.md).

The packet preserves source identities and hashes, candidate paragraph
locations, original upstream build logs/command/JAR member pins, runtime
profile, unsupported-help probes, six historical intake/build helpers and
fresh saved-proof/catalog readbacks. Primary full-text paragraphs, downloaded
upstream archives, dependencies and runtime JARs remain private. Provisional
provider findings are in [SOURCE-NOTES.md](SOURCE-NOTES.md); these do not append
canonical reviews or observations.

`CANDIDATE-ANCHORS.json` covers 250 exact paper identities and 50 lexical
candidates, including 19 existing observations and 31 possible additions.
Matched spans and paragraph hashes locate primary context; a lexical match is
not a positive Broad GSEA use. `SOURCE-*.json` inventories the unchanged two
official source archives. `OFFICIAL-TAGS.json` records immutable tag/commit
relationships. The 4.4 JAR is a local unchanged-source build, not a vendor
distribution; `qualified_statistical_execution` remains false.

`READBACK.json` records the handover checks. Saved edgeR proof reading is not
an R refit, and the cumulative owner runs unchanged from its exact retained
checkout. The first catalog check was refused for an output outside the
owner's build directory; logs remain private and the successful NEW owned
output is named in the handover. The two unsupported `-help` probes are
retained verbatim with exit 1 and are not statistical analysis attempts.

Python 3.11+ and the standard library suffice:

```text
python -B docs/literature/gsea-handover-20261120/verify.py
python -B docs/literature/gsea-handover-20261120/verify.py --primary-cache D:/projects/JLinAlg/build/literature-review/xml --local-intake build/gsea-high-impact-1
```

The reader verifies the complete packet manifest, candidate/source/build
relationships, and optionally exact raw XML and private source/runtime/JAR
identities. It does not execute the saved helper scripts, GSEA or any other
scientific process, and it does not restore missing files or authenticate
coordinated changes. SHA-256 provides integrity, not a signature.

**SKIP3:** a named requested local prerequisite is missing or inaccessible;
restore the exact permitted file/cache or retain the dependent skip.
**FAIL2:** saved/input identity or structure differs; preserve the affected
files/logs, investigate and retry separately. No complete analysis result is
admitted by either outcome. Never overwrite old attempts.

The six `original-helpers` files retain their exact historical bytes. They
have fixed absolute paths and create one-shot output directories: inspect and
adapt a COPY into NEW private paths before any future execution. Do not run
them blindly in an existing or different machine workspace.

No new release or site deployment is requested. The two literature report
pages remain excluded from Pages. Ignored runtimes, source/XML/PDF caches and
complete evidence require separate permitted machine transfer.
