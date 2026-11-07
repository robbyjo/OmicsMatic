# Native MEME motif inputs and Tomtom preprocessing

**Development library components only. Public Tomtom execution is still
unfinished and uncredited.** The catalog remains at 65 new scoped integrations
and 262 missing integrations. Cell Ranger remains excluded. This checkpoint
extends the [output components](MEME_OUTPUT_CONTRACTS_DEVELOPMENT.md); their
original evidence ZIP and sources remain unchanged in that archive.

`org.jlinalg.sequence.MemeMotifInputs` reads complete raw DNA motif inputs from
minimal MEME text, learned MEME/STREME text, and learned MEME/STREME 5.5.9 XML.
It retains file SHA-256, every motif in file order, raw probability tokens,
declared metadata, missing-value defaults and normalization dispositions.
Producer paths and URLs remain strings. No input is rewritten or converted.
Its configurable whole-file/model/width/scalar capacities include a tested
1,201-motif population; exceeding a capacity fails rather than truncates.

The deliberately revised `TomtomResults` applies original 5.5.9 preprocessing
to independent input copies before checking complete XML/TSV outputs. Raw
rounded probability rows may differ from unit mass: the original text parser
permits mass error up to 0.1, and subsequent normalization acts only outside
inclusive `1 +/- 1e-5`. Closer rows retain their mass. Output rows are checked
against that independently resolved mass. Original six-digit probabilities
must not be silently normalized to satisfy a unit-sum assertion.

Zero and absent `nsites` remain different declared inputs with the same original
zero metadata. Original pseudocounts use twenty sites for either; original
PSSM conversion has its own hundred-site fallback. A positive fractional count
at least one truncates to an integer for pseudocounts. A positive subunit count
can abort the original program and remains unsupported. Missing E metadata in
text defaults to log10 E zero, hence E one; explicitly declared E zero retains
zero. Small E values remain bounded decimal values, without a double underflow
rewrite. Unicode IDs and equals signs follow the tested source handling;
original XML motif ordering uses unsigned UTF-8 bytes.

**STREME XML and text exports can yield different original Tomtom inputs.**
For the tested new-schema STREME 5.5.9 XML, the original reader consumes
`train_pos_count + test_pos_count` as sites. It uses held-out log10 P as its E
metadata when held-out counts exist, otherwise training log10 P. These are not
`total_sites` or `test_evalue`. Its background callback leaves the new-schema
background unset and selects uniform DNA. The text export instead supplies
its printed background and E. The raw XML remains unchanged; the component
records the source mapping and a prominent warning. This does not establish
that the original source choices are statistically preferable or interchangeable.

The [frozen proof](meme-native-input-development/VALIDATION.json) records 106
focused actual-JAR checks per JVM on Windows Java 21/25 and Linux Java 21, with
no skips. All 25 component class files match a fresh strict Java 17 source
compilation. Only the declared TomtomResults source family changes relative
to the previous component executable, with six new input-reader classes; every
other prior archive entry remains identical. The canonical executable is
unchanged. Gradle tests, strict compilation and Javadoc also pass.

The original input controls retain 24 complete attempts: 22 zero exits and two
invalid probability inputs rejected by the original. Twenty successful text/XML
cases are independently parsed and checked against every original model and
all 80 reported matches. Two successful original HTML cases remain explicit
unsupported native inputs. Thirty earlier full comparison contracts retain
all 6,351 reported matches as regressions. Complete raw evidence, omitted-asset
censuses, source copies, test logs and the failed first-pass reader/verifier
attempts remain in the new proof archive. A prior 103-check candidate remains
historical evidence, superseded by three additional format-integrity checks.

## Warnings and required actions

**Do not treat an input or output component receipt as a completed analysis.**
Runtime admission, typed requested settings, supervised process completion,
text-only output support, complete report-family capture, public CLI, runnable
example and catalog closure remain open. Preserve raw inputs and use a permitted
original tool until those execution gates exist. Neither component downloads
or installs anything. If Java or required upstream software, helpers, resources
or permissions are unavailable or installation is forbidden, prominently record
the affected analysis as skipped and name the dependency the user must supply
on a permitted host. Do not substitute a successful parser check for that run.

**Unsupported inputs require an explicit skip or correction.** HTML embedded
data, other learned XML editions, higher-order learned XML backgrounds, custom
alphabets, score-only inputs and source identifier truncation need separately
tested handling. Preserve the original files; explicitly supply an independently
retained supported export when appropriate, or skip the comparison. No silent
format conversion occurs. The original x86_64 MEME XML reader's eight-byte
alternate-ID copy limit is rejected when it would truncate an ID. Other alternate
IDs beyond the original 200-byte boundary are also rejected.

Duplicate IDs remain visible in the complete raw population. Original duplicate,
ALLR-zero and query-width exclusions require a complete disposition ledger;
this parser does not remove them. An unfiltered population is not an admitted
Tomtom database. Do not repair original failures by silently removing motifs or
adding an unrequested pseudocount.

Changed files, malformed UTF-8/XML, incomplete or extra probability rows,
inconsistent metadata, capacity failures and cancellation produce no complete
receipt. Preserve the failed attempt, correct its named cause and retry into a
new capture/output directory. Copied historical proofs establish integrity
and tested source behavior, not runtime authenticity or a current-host ticket.
There is no release, push, deployment, whole-paper reconstruction or whole-goal
completion claim in this checkpoint.
