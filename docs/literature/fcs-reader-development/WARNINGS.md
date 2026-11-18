# Warnings and required actions

**Stored channels only.** Gain, antilog, time scaling, compensation,
calibration, display transformation, gates and cell-cycle models are not
inferred. Original negative, over-range and nonfinite floating values remain
visible. The mandatory integer unused-bit mask is applied and counted.

**Skip unsupported dependent analyses.** `UnsupportedFormatException` covers
capabilities outside this profile, including packed/wide integer storage,
ASCII/histogram data, multiple data sets, mixed byte orders, legacy `$UNICODE`
and unsupported editions. Preserve the original file and obtain a separately
retained supported export or use a separately qualified reader. Do not take a
first data set, guess offsets or discard metadata to force success.

**Known original-parser difference:** original FlowIO accepts `data1.fcs`;
this native profile skips its ambiguous trailing legacy metadata token. This
control is a native refusal, not a successful value comparison. Two other
source files with contradictory HEADER/TEXT offsets are refused by both readers.
All original files and failed controls remain retained.

Choose legacy encoding and trailing-space-padding support explicitly. The
source fixtures demonstrate why UTF-8 cannot be silently assumed for every
legacy file and why vendor padding must not be confused with a scientific value.
FCS3.1 values always require UTF-8. Original segment offsets and bytes remain
unchanged, and pointer aliases/padding choices are reported in warnings.

Other I/O failures or exceeded capacities prevent a complete stream. Preserve
the failure, resolve its named cause and use new candidate/result paths. This
low-level API checks file length, not full input integrity or a CRC. The caller
must capture/fingerprint the complete original, keep it unchanged, consume all
events and finalize downstream results only after every stage succeeds.

The first source-intake attempt encountered a Windows Python certificate-chain
failure; Linux's normal verified TLS succeeded. No TLS verification was disabled.
A separate intake refused an unescaped-space fixture URL; the corrected helper
uses the exact encoded URL. No dependent reference comparison ran from either
failed intake. The public exact-reference intake completed successfully.

The first packaging harness expected class members alone and refused the normal
zero-byte package-directory entry. Its Gradle build and six tests passed; its
failed harness marker remains unchanged. A separate read-only qualification
checked the exact added directory/classes, all unchanged predecessor members
and the final Linux suite. No earlier failure was relabeled successful.
