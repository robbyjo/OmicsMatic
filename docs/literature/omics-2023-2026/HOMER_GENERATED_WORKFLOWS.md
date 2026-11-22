# HOMER generated genomic workflows

The development `homer-tools genome` command now supports original generated
backgrounds as well as supplied contrasts. `generated-old` uses the original
Perl preparser and stratified selection for 5.1, 4.11 and 4.11.1.
`generated-new` uses the original 5.1 `homer2 bg` selector. Native Java audits
the actual populations and original statistics. The broader HOMER badge remains
missing and the 250-paper goal remains active; 269 integrations remain open.

**Read the warnings before using results.** Missing, forbidden, unsupported or
changed dependencies skip the affected analysis, exit 3. Input/capacity/audit
failures exit 2 and quarantine partial outputs. Required action: inspect the
named cause, `WARNINGS.md`, `session.json`, original diagnostics and ledgers;
correct it where permitted and rerun to new paths. Preserve failed output.
Analysis never installs, downloads genome data, patches scientific code or
substitutes a different version/contrast. Cell Ranger remains excluded.

Use a separately admitted Linux x86_64 genomic home, Linux Java inside WSL on
Windows, and an additional admission per generated algorithm/version.
[Runnable recipes and commands](../../../examples/homer-generated-workflow/README.md)
explain admission, five recipes per case, the public verifier and all required
choices. [Genomic setup](../../../tools/literature/HOMER_GENOMIC_RUNTIME.md)
documents explicitly permitted installation/download or verified offline input.
Old generation additionally binds `chmod` and its libraries. Missing admission
does not prevent an independently admitted supplied-background analysis.

## Choices that change the scientific contrast

| Choice | Required interpretation |
| --- | --- |
| Original unseeded randomness | Retain actual candidate/selected populations. Identical inputs do not promise identical draws. No wrapper seed is invented. |
| Old N versus new N | Old `background-total` counts targets plus backgrounds before unsupported-bin exclusions. New `background-count` requests backgrounds. NN is a screening request, not a hard cap. |
| Count mismatch | `background-count-policy exact` fails. Explicit `retain-original` retains the count with a prominent requested/actual warning. In the public new positional example only5 of256 requested backgrounds were selected. This does not establish a suitable contrast. |
| Old target overlap | 5.1 requires `background-overlap remove-original`: literal inclusive intervals, same strand under forward selection. 4.11/4.11.1 requires `retain`: those wrappers do not filter generated backgrounds against targets. Old self-overlaps remain allowed. |
| Old boundaries | `background-boundary-policy error` rejects selected boundary regions. Explicit `retain-original` preserves the original left padding/body extension and right truncation. Review `generated-pool.tsv`; raw interval width can differ from output DNA length. |
| New fixed choices | N fraction0.5, no forwarded lowercase mask, initial unit weights. Choose mask `no` and weighting `none`; GC stratification and optional overall/positional kmer matching still apply. |
| New identities | Original IDs are discarded and reindexed. Join geometry/multiplicity; `generated-target-mapping.tsv` retains all compatible source aliases. Same-start/same-strand targets with differing lengths are unsupported because final IDs lose end coordinates. |
| New positional matching | Require equal retained target lengths; original mean-length arrays can be unsafe otherwise. Choose an appropriate explicit size, overall matching, or retain SKIP. |
| Memory and iteration safety | Old pool/sort capacities, new resident/address-space estimates, unsafe 400m contig buffer bounds,32-bit iteration limits and Java profile capacity are checked separately. NN does not remove genome-sized pointer reservations. Periodic monitors are not OS quotas. |

Old pool auditing checks every original position, produced DNA SHA256 and
membership, quality disposition, frequency/bin fields, eligible availability and
deterministic selection quotas. Native new auditing checks every eligible target
and selected background, exact coordinates/DNA, full-precision GC bins and
complete canonical kmer frequencies. It retains all input quality exclusions.
The unretained adaptive new candidate stream, matching optimizer and sampling
probabilities are not reconstructed. `ProfileScore` is a matching score, not
P/q/E. Review the shared weighted-population flooring, original forward q-value
algorithm, normalization replay and learned-model bounds in the
[supplied genomic guide](HOMER_GENOMIC_WORKFLOWS.md).

## Validation and its limits

[Exact validation record](homer-generated-validation.json) identifies development
candidate SHA256 `fb768edc465bffe3fc392a30c92f554f0348029314228a72dcb49eb3f520a00c`. All 844 focused tests in 62 suites and 844 exact-JAR
tests on each Windows Java 21/25 pass without skips; strict Java 17 compilation,
Javadoc and packaging pass. Twenty public generated recipes pass native audits,
48 fixed-population original known/discovery/comparison replay calls and 20
separate fresh original generated draws. Independent unseeded draws are not
asserted equal. Their actual selected counts remain in the validation record.

Nine additional complete generated protocols cover all four version/algorithm
cases, unweighted known analysis on each old version, Unicode/gzip/N filtering,
duplicate geometry, and explicit boundary/count retention. One retained run kept
18 selected boundary records with 8151 versus 29936 requested backgrounds; this
demonstrates preservation/warnings, not biological appropriateness. Sixteen
prelaunch controls, six mutated-admission controls, two actual post-launch
rejections and eight Windows host skips pass. Native numerical methods need
only Java; original generated analysis requires its admitted Linux environment.

Twenty-eight supplied-background phase workflows still match 28 fresh originals;
two further legacy report failures are retained and confirmed by two fresh
failing originals. The current candidate also corrects a successful-output
warning sentence: classfile inspection proves that this one UTF8 constant-pool
replacement is its only change from the candidate used for the 20 public
generated recipes. All other 15755 packaged entries and all instructions remain
identical. Exact scopes, candidate hashes, proof hashes and retained failed
attempts are recorded separately.

An earlier100bp-default overlap assumption was wrong and has been removed.
Direct original probes show literal overlap; only5.1 invokes that added mask
for old generated backgrounds. Failed initial attempts remain retained. Named
genome/repeat/TE resources, peak/coverage, RNA and GO operations and the broader
toolbox work remain unfinished. These are synthetic technical checks; no
paper-wide equivalence, biological calibration, public deployment or release is
claimed.
