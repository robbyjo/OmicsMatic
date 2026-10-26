# FIMO validation status and retained failures

**Native DNA P-value scanning and the exact private original FIMO 5.5.9 P/q
adapter are validated in the canonical source build.** Read [FIMO_WORKFLOWS.md](FIMO_WORKFLOWS.md)
for commands, scientific boundaries, installation constraints and required actions.
The full gate passed 1,593 tests, zero failures/errors, five optional skips and
all 72 required suites without skips. Complete policy 197/0-skips and prior-tool
regressions passed. The wider implementation/semantic-review goal remains active.

Native `motif-scan` requires only Java. Original `fimo-tools` requires the exact
admitted Windows x86_64 Cygwin/GNU-regex profile. Its explicit helper reuses the
authenticated existing local reference build and verifies source archives;
alternate-host source bootstrap is not implemented or validated. If that profile
is absent, changed or installation is forbidden, skip the original analysis with
exit 3 and follow the precise recovery in WARNINGS.md. Do not infer that native
scanning needs the original runtime or that a version probe validates science.

The original stock Cygwin attempt is retained under
`build/literature-review/fimo-runtime/first-original-science/`. It compiled and
printed 5.5.9, then failed before any motif matches because its alphabet parser
rejected a regular expression with a trailing-backslash error. Missing generated
headers and a mistaken unsupported `--help` probe remain separate diagnostics.
Those failed attempts are not credited as scientific output.

A separate private build statically linked the authenticated GNU POSIX regex
backend under four renamed symbols, with LC_ALL=C. All 615 original scientific
C/header files remained byte-identical to the official archive. That engine and
one private Cygwin DLL passed actual P/q/cohort comparisons, exact-runtime sealing,
input/identity/coordinate/Unicode checks and timeout cleanup. It is a tested private
profile; the publisher's [supported installation guidance](https://meme-suite.org/meme/doc/install.html)
no longer supports Cygwin and recommends Docker/WSL on Windows. No supported
Linux/WSL/Docker host is claimed to have been validated here.

Source archive SHA-256:
`0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12`.
GNU regex archive SHA-256:
`7147b7f806ec3d007843b38e19f42a5b7c65894a57ffc297a76b0dcd5f675d76`.
Official HTTPS/source hashes record exact local bytes, not separately signed vendor
checksums. Original source/backend licenses and explicit intended-use declarations
remain retained. The user confirmed educational/research/nonprofit use under the
[MEME terms](https://meme-suite.org/meme/doc/copyright.html); this grants neither
commercial/redistribution permission nor unrelated VIPER eligibility.

**Do not interpret a capped empty table as absence of binding.** Six real original
cap=10 workflows reached the stored-hit limit; four emitted empty tables despite
uncapped strong matches. The adapter fails and quarantines these outputs. Full
`--no-qvalue` reports use zero q-value placeholders; the canonical table blanks them
with a warning and preserves raw reports. A legitimate complete headerless empty
scan is accepted only after original summary or loaded-motif/strand validation and
truncation checks. Original q-estimator and small-population warnings stay visible.

The initial uniform-background assumption was also retained as a failed reference:
FIMO 5.5.9 defaults to NRDB rather than automatically using the motif-file background.
Explicit uniform references passed. Native scanner background regularization and
forward U-text preservation were corrected against original evidence; earlier
failed comparisons and their executed-source snapshots remain retained. Native
core, original reference generation and packaged adapter evidence are distinct in
the linked validation records. No historical-version/full-format/full-paper parity
or replacement BH/custom empirical calibration is inferred.
