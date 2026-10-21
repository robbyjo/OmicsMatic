# JLinAlg 0.3.7 release validation

Validated on 2026-10-21 from the release checkout, using Java 21 to build
Java 17-compatible artifacts. The numerical implementation is unchanged by the
AI documentation and website update; this release also packages the changes
already committed since v0.3.6.

## Build and automated checks

~~~powershell
.\gradlew.bat clean check assemble --no-daemon --no-parallel
java -jar build/cli/jlinalg-0.3.7.jar --version
~~~

The full gate passed, including `checkWebsite`, executable/library packaging,
sources and Javadocs. The executable reported `0.3.7`. Gradle reused matching
cached compile/test outputs. The test report contains 1,166 tests: 1,161 passed,
zero failures/errors, and five skipped. Three skips require the optional CHOLMOD
native library; two are explicitly gated live public-genetics retrieval tests.

Website checks cover required pages, local paths and case, citation mappings,
CLI examples and theme support. Additional checks verified the new links and
section anchors. Desktop and narrow-screen previews were inspected in an
isolated headless browser.

## Packaged CLI mode checks

All rehearsals used copies of the checked-in synthetic two-cohort study.

| Rehearsal | Verified behavior |
| --- | --- |
| No AI | Validation, resolution, planning, rejection of execution before approval, scientist approval, offline execution, audit verification and reuse |
| AI advisor | Deterministic numerical results, local browser-packet preparation, explicit `waiting_for_input`, verified packet path, no model request sent |
| External driver | Existing CLI lifecycle with an `external` rehearsal actor, separate human approval, offline execution, question listing and audit verification |
| Denied disclosure | Enabled browser advice with `deny_all` remains policy-blocked, with `request_sent: false` and an intact local audit trail |

Waiting for a browser answer intentionally returns a nonzero run status while
retaining completed numerical results. This is documented in the advisor guide.
The external-driver rehearsal does not claim that a model ran: actor attribution
used `external`, not `llm`.

No live AI provider, browser submission, patient dataset or autonomous agent was
used in these checks. Simulated provider responses in the existing automated
suite and offline rehearsals establish application behavior; they do not certify
provider authenticity, scientific validity, regulatory compliance or whole-system
patient-data non-egress. Separate access and network enforcement is required for
an external agent.

## Publication verification

The tagged release workflow builds the exact version, publishes executable,
library, sources, Javadocs and `SHA256SUMS.txt`, and refuses to overwrite an
existing release. GitHub Pages deployment is a separate workflow. Public release
metadata, tag identity, hosted workflow completion, downloaded artifact checksums,
the executable version and the deployed mode pages are checked after publication.
