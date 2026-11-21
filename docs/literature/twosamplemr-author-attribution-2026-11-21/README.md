# Static P157/P177 attribution extension

This is a proposal extending the frozen fourteen-role TwoSampleMR census.
**No author analysis was executed and no canonical review/family credit is
added.** ATTRIBUTION.json retains primary XML identities, source commits/file
hashes, active parse-token line numbers and explicit inference limits. Full
downloaded author source is private; metadata does not establish executed code.

P157 reports TwoSampleMR0.5.7. Its paper-linked Zenodo concept record resolves
to the [versioned source archive](https://zenodo.org/records/11169202).
The archived MR script matches the pinned
[GitHub script](https://github.com/QingboWang/japan_covid_taskforce_multi_omics/blob/d1ad9e5503cd3219ffc2f6b85af10e2d6564ccce/codes/supple/MR_analysis.R#L65)
byte-for-byte. It prepares eQTL/pQTL canonical tables, calls harmonise_data and
mr in both directions and supplies no explicit method list or harmonization
action in those calls. Selected-package defaults apply. Clumping is not
witnessed in that script; complete preprocessing and paper execution remain
unqualified. The archive's repository prefix does not prove an execution commit.

P177 reports R4.3.2 and lists both packages, with no TwoSampleMR edition.
All seven selected simulation scripts explicitly call
[MendelianRandomization::mr_input/mr_ivw](https://github.com/hhoulei/TEMR_Simul/blob/b4cccda71ff1d2848184473cfdb2394e8d1dcbf9/two%20ancestries/code202401200830.R#L164).
They also call unqualified simple/weighted/penalised median functions and
[mr_mode](https://github.com/hhoulei/TEMR_Simul/blob/b4cccda71ff1d2848184473cfdb2394e8d1dcbf9/two%20ancestries/code202401200830.R#L169).
Attributing those median/mode names to TwoSampleMR is a **static inference**
from imports/exports and no detected same-name local assignment/dynamic source
call in those scripts. Present-day export checks do not prove historical
dependency versions or actual runtime resolution. Egger calls are not witnessed
in these selected scripts. TEMR remains a separate provider; do not credit its
trans-ethnic model or the explicit MendelianRandomization IVW to TwoSampleMR.

The initial parser walk failed on R's missing-argument expressions; its raw
failure is retained. A subsequent pass omitted bare mr from its method filter;
the final pass includes it. All nine selected R files parse in that final pass.
The parser only traverses syntax; it never evaluates downloaded author code.
Keep unreported versions unknown and perform guarded catalog admission before
changing reviewed counts or the existing source-role records.
