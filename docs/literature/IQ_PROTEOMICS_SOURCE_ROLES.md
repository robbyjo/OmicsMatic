# Proteomics source roles and pending runtimes

The audit checked all250 frozen article XML files and classified40 broad name
matches plus two other structures. It reviewed16 software observations in12
papers. Five observations were previously uncataloged: iq in P168, Andromeda in
P014/P144, MaxDIA in P014, and MACS iQ View in P051. Cognitive IQ, iQ SYBR reagent
and IQ-TREE mentions do not become iq proteomics observations.

| Papers | Reported software and role | Implementation state |
| --- | --- | --- |
| P005 | DIA-NN1.8.1 library-free mouse search and protein-group matrix | Raw-search workflow pending; native iq does not implement it. |
| P020 | DIA-NN1.8, deep learning, Robust LC and MBR | Pending; Perseus preprocessing/testing is separate. |
| P029 | DIA-NN2.0.2 yeast predicted library, individual-run optimization and batch search | Pending; synthetic library generation does not qualify raw quantification. Source spelling `maxLGQ` is retained. |
| P097 | DIA-NN1.8.1 reference/PAV libraries, MBR and `PG.Normalised` readout | Pending; nanoparticle selection and study transformations are separate. |
| P168 | DIA-NN1.8.1 in Proteograph cloud; external PepCal normalization; iq `fast_MaxLFQ`; limma plate correction | Native iq prepared normalized-log2 aggregation is available. Other providers and the cloud pipeline remain separate. The iq edition and full call are unreported. |
| P014 | MaxQuant2.5.1.0 with Andromeda DDA search and embedded MaxDIA using DDA-derived libraries | Runtime analyses skipped at the user's request; keep pending. Engine/module editions are unreported and not copied from the parent version. |
| P026 | MaxQuant2.4.3.0 Astral search, LFQ/iBAQ, MBR, Fast LFQ disabled | Runtime analyses skipped; pFind and OpenMS are separate. MaxQuant's Fast LFQ setting is distinct from iq's `fast_MaxLFQ` function. |
| P032 | Literal MaxQuant2.4.7 human search/MBR and spectral-count export | Runtime analyses skipped; the version is not normalized to2.4.7.0. SAINTexpress and MSstats/artMS are separate. |
| P052 | MaxQuant2.4.9.0 STV-C8 label-free quantification with added protein sequences | Runtime analyses skipped; Perseus statistics remain separate. |
| P144 | MaxQuant1.6.14.0/Andromeda TMT10plex MS2 reporter quantification | Runtime analyses skipped; TMT is not replaced by native MaxLFQ. |
| P230 | MaxQuant2.2.0.0 human/HDAC3-variant search and iBAQ | Runtime analyses skipped; iBAQ is not replaced by MaxLFQ. |
| P051 | MACS iQ View1.3.2 cyclic-image processing and donut segmentation | Distinct pending imaging software; neither R iq nor IQ-TREE implements it. |

Paper DOIs, exact XML/paragraph hashes, reported settings and source boundaries
are in [CENSUS.json](iq-proteomics-source-audit/CENSUS.json) and
[ROLES.json](iq-proteomics-source-audit/ROLES.json). Article texts remain private.
Andromeda and MaxDIA are named components of the pending MaxQuant workflow;
neither receives additional implemented-family credit.

**MaxQuant: SKIP3, dependent runtime analyses skipped.** The
[official download](https://maxquant.org/download_asset/maxquant/latest)
requires personal/institution information, CAPTCHA and licence acceptance.
The user requested skipping these runtime analyses while retaining MaxQuant as
pending. No identity was fabricated, no CAPTCHA was bypassed, and no MaxQuant
runtime was installed or executed. If qualification is resumed later, supply an
appropriately licensed archive for the relevant edition and matching inputs.
Its bundled search/module analyses remain skipped with the parent prerequisite.

**DIA-NN remains pending.** Private unmodified1.8/1.8.1/2.0.2 pilots start on the
local Linux host, and1.8.1 generated a library from synthetic proteins. This is
not positive raw-data quantification or a qualified installation/workflow. The
selected distributions/licences are retained privately; no binaries or raw
traces are published. Academic/local-use and redistribution/attribution terms
must be followed for the selected edition. No current edition is silently
substituted for an author's version.

See the [native iq guide](IQ_MAXLFQ_WORKFLOWS.md) for the qualified prepared-input
scope, convergence checks, copyable commands and complete captured-result
verification. `FAIL2` means no complete result: retain the attempt, correct the
reported cause and use new paths. Missing/vendor/cloud prerequisites remain
explicit rather than being replaced with generic statistics. Cell Ranger
remains the only user-excluded tool. The full250-paper goal remains open.
