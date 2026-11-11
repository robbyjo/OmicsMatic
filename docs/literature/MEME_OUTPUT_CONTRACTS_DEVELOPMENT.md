# Native MEME XML and Tomtom output contracts

**Development library components are available; the new execution adapters remain
unfinished and uncredited.** The catalog still has 65 new scoped integrations,
387 named tools and 262 missing integrations. Cell Ranger remains excluded.
Use the [source-role checkpoint](MEME_DEVELOPMENT.md) for the distinct MEME,
MEME-ChIP, DREME, CentriMo, Tomtom, STREME, HyPhy and BWA-MEME roles.

The [frozen component proof](meme-native-output-development/VALIDATION.json)
records 73 focused actual-JAR checks per JVM on Windows Java 21/25 and Linux
Java 21, with no skips. Its 19 new class files match a fresh strict Java 17
source compilation byte for byte. Every pre-existing archive entry matches the
earlier original-tested development executable; the canonical executable is
unchanged. This is source-built component evidence, not a public release.

## What the native components check

`org.jlinalg.sequence.MemeXml` reads complete bounded UTF-8 XML. It retains
literal decimal strings, attributes and element order, verifies raw identity
again before returning, and refuses truncation, late junk, changed files,
external entities/resources, namespaces, mixed content and capacity/cancellation
failures. The original internal MEME element/attribute declaration DTD is accepted
without expansion. No producer path written inside XML is opened.

Its 31 tests include all 14 original retained XML documents from MEME, Tomtom
and STREME, plus malformed, capacity, cancellation and same-length/same-timestamp
mutation controls. Complete selected format fixtures retain the original terms
and explicitly identify omitted HTML/PNG/EPS assets; those assets remain in the
complete development captures.

`org.jlinalg.sequence.TomtomResults` audits original 5.5.9 DNA XML and TSV against
independently supplied complete model/database populations and expected settings.
All 30 retained complete contracts pass: **6,351 original reported rows**, every
printed model probability, exact source/database identities, requested distance,
background, cutoff, strand, overlap and containment, XML/TSV agreement and a
complete declared query-target pair ledger. The cases include all nine original
distances, unequal widths, fractional site counts, nonuniform/external
backgrounds, containment, minimum overlap, incomplete scores, pseudocount
choices, multiple databases, duplicate IDs across databases and thresholded or
empty output. Unreported pairs have unavailable numeric values, never imputed
zeros. XML retains all queries but only the union of reported targets; the full
independent input population stays in the pair ledger.

The 42 Tomtom tests include independent E-versus-P arithmetic even when both
files are altered consistently, altered matrices/consensus, missing rows, late
footer data, invalid values, capacities and cancellation. Its rounding envelopes
describe original formatting and float conversion; they are not confidence
intervals or biological calibration.

## Source behavior that must stay explicit

The following details were checked against the unchanged official 5.5.9 source
and fresh original controls, not inferred from a generic motif algorithm:

- Smoothing uses the integer truncation of positive fractional site counts.
  The original reported `nsites` value remains fractional. A site count below
  one triggers an original assertion; the native input model refuses it.
- Original TSV P/E/q values are cast to float32 and then printed at six
  significant digits. XML match values pass through an original six-digit
  temporary file and three-digit XML formatting. Keep both literal views.
- Consensus compares double matrix entries to a float32 running maximum.
  Source ties and reverse-target consensus are checked without rescanning a
  rounded XML point matrix.
- Original background preprocessing averages complementary bases, adds `5e-7`
  to each base and normalizes. The default query source passes through this
  operation twice; a freshly selected external source once. Following database
  readers process a copy once more. Raw supplied backgrounds remain separate.
- The original q-value family includes the tested orientation population.
  Recomputing q-values from reported best-orientation matches changes the family.
- Duplicate IDs across target files remain distinguishable through XML database
  indices. The original TSV alone omits that index. Duplicate IDs within one
  input file need their own explicit source-disposition contract.
- ALLR with zero probabilities and no requested pseudocount has source-dependent
  exclusion/failure behavior. A singleton zero-containing target caused a retained
  original crash; a multi-motif database excluded its zero-containing member.
  The current native contract skips this unsupported population boundary through
  `UnsupportedInput`; it never silently adds pseudocounts or removes motifs.
- The original query-width limit is 100. Wider queries need a separately
  admitted exclusion ledger. Tiny target databases retain original calibration
  warnings. Zero printed probabilities can reflect underflow/conversion.

The first broad edge-control attempt included a sub-unit site count in its query
population and failed before fitting in all 22 parameter cases. Those attempts
and their exact source are retained. A new population separated that invalid
motif into its own failure probe: 21 of 23 original processes completed; the
other two preserve the sub-unit assertion and singleton-zero ALLR crash. Five
additional independent original single-motif/background/exclusion probes all
completed. No failed family is counted among the 30 complete contracts.

See the official [Tomtom interface](https://meme-suite.org/meme/doc/tomtom.html)
and [output formats](https://meme-suite.org/meme/doc/tomtom-output-format.html).
Source archive SHA-256 is
`0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12`.
The native Java code is independently written; upstream fixture terms are
retained in each fixture archive. The confirmed intended use is
educational/research/nonprofit.

## Required actions and remaining gates

These native APIs need Java 17+ and captured files; they install nothing and
launch no upstream analysis. Callers must supply independently verified input
models/settings. The components do not certify that those records came from
the claimed input files. Native input/preprocessing admission, live runtime/host
admission, supervised original execution, complete report-family publication,
portable final bundles, the public command and documented examples remain open.
Original `-text` mode and source-exclusion ledgers also need separate contracts.

**Skip dependent analysis** if required Java, original software/helpers,
resources, installation/download permission or an admitted capability is
unavailable or forbidden. `UnsupportedInput` names a source boundary requiring
an additional contract; preserve it and the raw inputs. Install only where
permitted and do not change the scientific request to get a passing run.

**Admit no result** after an `IOException`, invalid-input exception or
cancellation. Preserve the original outputs, complete warnings, source receipts
and failed attempts. Correct the named cause and rerun into new paths. These
library components do not themselves provide a command-line exit status.

The first contract candidate compiled its main classes/Javadoc but its test
helper failed to compile; its untested JAR and source snapshot remain retained.
A new candidate passes all 73 actual-JAR cases on all three JVMs. No release,
push, deployment, whole-paper reproduction, biological accuracy certificate or
whole-goal completion is claimed.
