# PAL2NAL native reference fixtures

`controls.zip` preserves 85 fresh runs of the original official Perl v14 source,
including every input, stdout, stderr and terminal process receipt. Its SHA-256 is
`3841d0fe4c0f8dc099dd76b9343fc84ff2952839f2303b65eb749d1b8fdfa686`.
The source archive was downloaded from
<https://www.bork.embl.de/pal2nal/distribution/pal2nal.v14.tar.gz> with SHA-256
`0e458d38571ab6d61078047a9e80d843bb2e733414eeff8b1144d1c6bc13848f`;
the extracted `pal2nal.pl` SHA-256 is
`2b419417e304e9a63129056d61fa513a4c5834257a7b162cc4389c3ac53e23f2`.
The fixture contains no upstream executable. Original errors may have exit status
zero and remain evidence, not admitted results.

The native replay checks 76 exact final sequence-row comparisons, two explicit
dot/underscore normalization departures, and seven refusals of malformed,
unsupported or undefined original paths. This covers only the documented native
API scope and synthetic controls. It does not certify every source CLI option,
alignment accuracy, v14.1 equivalence or paper reconstruction.

`ncbi-codes.zip` retains an independent official NCBI `gc.prt` capture, 17 normalized
code/initiator strings and provenance. Its SHA-256 is
`15134f83c2dae767dac2c074754e1ca14d923e009130254c04cf28bee91af48e`.
The raw capture SHA-256 is
`2aecbdd09ecc5e0d233c105090a593737298ef93d88f6bc0c1bef26df6051a04`.
All 1,088 codons and 1,088 initiator decisions are checked; the original table-10
TGA defect and five missing source initiators are retained explicitly.

See [development scope and recovery](../../../../../../../docs/literature/PAL2NAL_DEVELOPMENT.md)
and the bundled `org/jlinalg/omics/pal2nal-v14-MIT-NOTICE.txt` for original-pattern
attribution. Do not overwrite raw source outputs to make a native test pass.
