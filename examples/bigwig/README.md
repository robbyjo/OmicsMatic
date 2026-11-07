# Native Java bedGraph to bigWig

`bigwig-tools` writes complete indexed bigWig v4 tracks using Java 17+.
No UCSC binary, Python package, download or external runtime is needed for
conversion or verification. Use a development JAR containing this command;
the canonical/released JAR has not yet been updated by this work.

From the repository checkout in PowerShell, replace the JAR path and use new
output directories. The examples are synthetic, with no biological meaning.

```powershell
$bigwigJar = 'C:/ABS/omicsmatic-development.jar'
java -Xmx1g -jar $bigwigJar bigwig-tools --input examples/bigwig/inputs/exact.bedGraph --sizes examples/bigwig/inputs/reference.sizes --genome-build synthetic-reference --units 'arbitrary supplied scores' --float32 exact --dictionary used --compression zlib --out build/bigwig-exact-NEW
java -Xmx1g -jar $bigwigJar bigwig-tools --verify build/bigwig-exact-NEW
java -Xmx1g -jar $bigwigJar bigwig-tools --input examples/bigwig/inputs/rounding.bedGraph --sizes examples/bigwig/inputs/reference.sizes --genome-build synthetic-reference --units 'arbitrary supplied scores' --float32 round --dictionary all --compression none --out build/bigwig-round-NEW
java -Xmx1g -jar $bigwigJar bigwig-tools --verify build/bigwig-round-NEW
```

On Linux, replace `$bigwigJar` with a shell variable or the literal JAR path;
the same Java arguments work. Source and output paths may contain Unicode or
spaces. The output parent must exist and be canonical. The first example has
three intervals covering eight bases and stores two used chromosomes; the unused
reference row remains in its ledger. The second has three intervals covering five
bases and explicitly retains all three reference chromosomes.

**Choose the precision policy deliberately.** `exact` requires each original
decimal token to equal its stored float32 value. It rejects `0.1`, as well as
information already lost when parsing to float64. `round` permits those changes
and emits prominent counts. `VALUE-LEDGER.tsv` preserves every original value and
coordinate token, its source line, exact float64/float32 hexadecimal values,
float32 bits and underflow stage. In the rounding example, `1e-400` underflows in
float64 and `-1e-50` in float32. Zero is not evidence of biological absence.

`REFERENCE-LEDGER.tsv` retains every supplied chromosome-size row, whether stored
or omitted, its binary chromosome ID and input record count. `used` omits only
reference chromosomes without signal; `all` stores the whole declared dictionary.
An empty signal requires `all` and remains distinct from measured zero signal.
No assembly or reference resource is guessed from the build label.

Inputs are UTF-8 bedGraph with four whitespace-separated fields and local
two-column chromosome sizes. Coordinates are zero-based, half-open and unsigned32.
Each chromosome must occur in one contiguous input block, with sorted,
nonoverlapping intervals. Chromosome blocks may occur in any order. The binary
dictionary/data use UTF-8 byte order. Comments and initial track/browser metadata
are retained raw and counted; their display settings do not normalize signal.
No aliases, clipping, automatic within-chromosome sorting, overlap averaging or
missing-base zero filling are performed.

For a gzip input, add `--input-format gzip`. Every concatenated member, optional
header, CRC and size must be valid; trailing non-gzip bytes are refused. Both raw
source files are captured unchanged. The gzip header capacity is 1 MiB per member.

**Require overall completion and verification.** Read `WARNINGS.md`,
`INPUT-AUDIT.json`, `OUTPUT-AUDIT.json`, both ledgers and `request.json`. A completed
bundle needs `COMPLETED.json`, no `INCOMPLETE.json`/`FAILURE.json`, and successful
`--verify`. Verification works on a copied complete Windows/Linux bundle without
opening recorded producer paths. Hashes prove integrity, not authenticity or
biological accuracy. A track file or partial marker alone is insufficient.

**Native summary and consumer limits are explicit.** Total summaries use stored
float32 data with double products and compensated double sums, differing from
UCSC 2.10 float arithmetic. No zoom approximations are stored; full-resolution
indexes/data remain complete, but large-range consumers may do more raw I/O.
Original summary bits, approximate statistics and rendering speed are not claimed.
The original reader controls retain nonfinite original summaries and a pinned
Cygwin pyBigWig header crash. The pinned reader also returned signed positions for
unsigned coordinates above 2^31; verify downstream consumer support for such ranges.
See [native reference evidence](../../docs/literature/BIGWIG_NATIVE_DEVELOPMENT.md).

Optional limits are recorded explicitly in `request.json`:

```text
--records 1000000 --line-chars 65536 --input-bytes 268435456 --decoded-bytes 268435456
--output-bytes 1073741824 --workspace-bytes 4294967296 --items-per-section 1024 --tree-fanout 256
```

Input and decoded byte limits each cover both complete source files/streams.
The requested maximum output and 16 MiB final metadata reserve must fit beside
all prepared evidence. Limits are never silently lowered and do not guarantee
JVM heap availability. Review them explicitly for complete large populations.

**SKIP3 — conversion skipped:** required input/output access is forbidden. Supply
the named permitted resource or leave that conversion skipped. No installation
is attempted. **FAIL2 — no result admitted:** input, precision, integrity, capacity,
I/O, cancellation or finalization failed. Read `FAILURE.json` and its named stage,
preserve the whole attempt and partial track, correct the cause and use new paths.
Cell Ranger remains excluded. This native conversion does not run upstream coverage,
normalization, peak calling or downstream visualization pipelines, or reproduce
unknown historical converter editions and chromosome resources.
