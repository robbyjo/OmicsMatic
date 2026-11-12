# Native bioinformatics example

These inputs are synthetic. Run the commands in the
[native tool guide](../../docs/native-tool-replacements.md) from the repository
root after building the current source. They need Java only.

Every destination is new under `build/native-tools-example`. For a retry, select
new output paths and update the report mapping and HTSeq JSON requests rather
than overwriting an earlier result.

Expected local-enrichment P values are `0.8` and `1/15`; the zero-overlap term is
excluded before BH correction. The paired trimming example retains two pairs:
the quality-trimmed third R1 is shorter than the declared minimum. Both synthetic
genes receive one count, and the unmapped SAM record contributes to
`__not_aligned`. QC output contains measurements with INFO, without a pass/fail
threshold policy. None of these controls establishes biological validity.
