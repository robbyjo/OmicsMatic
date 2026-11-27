# Scoped Popcorn qualification

`QUALIFICATION.json` records the exact producing JAR, source archive and checks.
`source-context.zip` contains this project's own implementation, synthetic
original-result oracles and hash-only original profiles/source locators. It
contains no original article text, upstream scientific source or private runtime.

The workflow has 31 native/contract tests per OS, nine complete captures and
fresh replays, independent original CLI comparisons for all nine cases
(13 original invocations), and 13 packaged negative controls. MLE point-only
cases deliberately retain undefined SE/Z/P with warnings. The native regression
stage receives no additional family credit.

Use [the command guide](../../popcorn-tools.md) for setup, source behavior and
SKIP3/FAIL2 recovery. The [five paper roles](../popcorn-source-audit/ROLES.json)
retain effect/impact ambiguity, version corrections and the benchmark's
null/resampling differences. Author recipes and supplementary-method completeness
remain unqualified. The full 250-paper goal remains active.

```sh
python -B tools/literature/verify_popcorn_workflows.py
python -B tools/literature/popcorn_source_audit.py --xml-cache PRIVATE_XML_CACHE
python -B tools/literature/popcorn_catalog.py
```

Pass `--evidence PRIVATE_POPCORN_DIRECTORY` to the qualification verifier for
complete retained private byte checks. A future feature can change shared files;
historical verification must restore this archived source context and its parent
commit, without refreshing original seals. Publication and cleanup completion
are recorded separately after those steps finish.
