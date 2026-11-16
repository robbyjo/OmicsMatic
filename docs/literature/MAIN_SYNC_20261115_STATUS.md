# Main synchronization after the v0.4.0 publication changes

**Warning: complete catalog source qualification remains pending for this
combined tree.** Do not report the historical source-transition pass as a pass
on current main, and do not change its saved hashes or historical baseline.

This merge preserves v0.4.0 publication commit
`abf19f30f177e6122966c2ca4e4c0d3e9d03aeea` and incorporates the iDEP genuine
Shiny-session checkpoint `fc6e0afcabb6144df9a3c161e3007e4d36423f2d` and separate
source-transition review `1a9ff82adbf53e7b36fad065a9b45b1ab1c018f6`.
Application Java, resources, tests, website, build settings and release workflow
remain identical to the v0.4.0 publication commit. No release tag or published
asset is changed by this synchronization.

Fresh merge checks passed: Python/JSON parsing, all five added R sources,
both iDEP public and complete saved private evidence readbacks, three session
entry-point help commands, all 15 source-guard controls, whitespace checks and
`checkWebsite` under JDK21. Scientific fits and the Java test suite were not
repeated for this source/documentation-only merge. The previously recorded
scientific controls retain their original scope and failures.

The separate source-transition command was also run against this combined tree.
It correctly returned `FAIL2_APPLICATION_SOURCE_TRANSITION_UNADMITTED`, first
identifying `docs/vignettes/auditable-omics-pipeline.md` as differing from its
reviewed source. The v0.4.0 publication changes lie outside the transition's
explicit `fc6e0afc` target. This refusal admits no complete current-tree catalog
qualification; it does not invalidate the retained historical pass on that
target. The original predecessor guard also remains unchanged.

Required action before claiming current-tree catalog qualification: retain the
failure record and historical evidence, separately review the complete release
source transition, and validate it in new output paths. Do not bypass a guard,
replace saved identities or reinterpret a refused check as successful. See the
[transition boundaries](APPLICATION_SOURCE_TRANSITION_20261115.md) and
[remaining iDEP review](IDEP244_INTEGRATION_REVIEW_TODO.md). This merge adds no
catalog integration credit; the 250-paper implementation objective remains open.
