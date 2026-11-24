# Cytometry catalog: supported workflows and source roles

250 papers, 446 labels, 485 provider definitions and 2,724 observations; 1,755 reviewed/969 pending uses. 158 scoped families (30 native + 128 upstream), 161 including three existing adapters; 254 missing and 19 method-overlap. One bounded native FlowJo family and 41 new reviews are admitted; three prior cytometry reviews are enriched. FACSDiva remains missing. The full goal is unfinished.

**Warnings and required actions:** SKIP3: skip the dependent paper analysis when original permitted FCS/WSP, explicit calibration/measurement settings or a required supported method are unavailable. Supply exact inputs/settings or implement/qualify the missing model/plugin; do not substitute native unbinned statistics for vendor-binned results or FlowJo 10 for version 9. FACSDiva acquisition/sorting remains unavailable here. FAIL2: no complete result; retain original/partial/failed evidence, fix the named integrity, numerical, capacity or saved-count cause and retry NEW output. Define MFI and measurement domain explicitly. Keep null/undefined statuses, all source events and numerical discrepancies; no clipping, zero snapping or nonpositive-event removal. Read WARNINGS.md and analysis.json. Preserve exact producer JARs and historical evidence. Cell Ranger remains excluded.

Native Java flowjo-tools GATE for FlowJo 10.x workspace format 20.0 with the explicit FlowKit 1.3.2 interpretation: complete sample-local gate memberships/counts, captured FCS/WSP and explicit biex tables; SUMMARIZE adds complete unweighted unbinned amplifier or saved-WSP-compensated measurement summaries with explicit R type-7 quantiles. Fresh memberships and saved-count metadata are checked. Exact producer replay and integrity-only verification are separate. No vendor runtime is installed or required. Saved vendor-count differences remain visible. No vendor-binned statistics/off-scale Robust SD equivalence, FlowJo 9, acquisition/sorting, cell-cycle fits, clustering plugins, image-event import, vendor figures, inferred MFI definition, whole-tool/paper or biological qualification.

FACSDiva source roles are reviewed; its instrument acquisition/sorting and unresolved jointly attributed analysis are not implemented. Offline native FCS/workspace computation is a separate capability and does not replace the instrument software. No native/upstream integration credit is assigned to FACSDiva.

Forty-four roles cover 27 FlowJo and 17 FACSDiva paper/tool pairs. P032/P084 intensity statistics remain distinct from unbinned summaries; P084 vendor binning/off-scale rules are unresolved. P051 FlowSOM, UMAP and ClusterExplorer plugins/image-event import remain gaps. P121 reports FlowJo 9; P122 Dean-Jett-Fox and P228 Watson Pragmatic fits are not gate counts. P031/P052 jointly attribute analysis without a package-specific split. P119 retains FlowJo 10 OR FCS Express 7. P082 FACSDiva is a software-only declaration; P064 literal FACSDiva 8.02 remains unchanged. Reported versions are source summaries; raw near-name captures remain unmodified. No paper cohort or original vendor analysis is reproduced.

## Use the native commands

Build current source using `.\gradlew.bat executableJar` on Windows or
`./gradlew executableJar` on Linux, with Java 21. The resulting executable is
`build/cli/omicsmatic-0.4.0.jar`. Preserve its exact bytes with your completed run.

Follow the [workspace membership/count guide](../FLOWJO_WORKSPACES.md), then
the [explicit intensity guide](../CYTOMETRY_INTENSITY.md) for complete requests.
The installed source command dispatch includes:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools GATE --request gate-request.json
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools SUMMARIZE --request intensity-request.json
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools SUMMARIZE VERIFY --run intensity-result
java -jar build/cli/omicsmatic-0.4.0.jar flowjo-tools SUMMARIZE REPLAY --run intensity-result --out NEW-replay
```

VERIFY checks recorded bytes. REPLAY freshly computes with the exact original
producer. The default strict saved-count policies refuse disagreement; explicit
native-population alternatives preserve each discrepancy and establish no vendor
equivalence. All values/events remain present. Geometric mean is undefined when
any native value is nonpositive; other undefined statistics keep explicit reasons.

**Numerical qualifications:** raw R 4.6.1 SD/CV underflow at the artificial
`1e-200` scale; native nonzero values agree with a separately scaled R reference.
SVD versus FlowKit solver roundoff yields 513 selected sign-boundary comparisons
within `4.59e-13` of zero. Original values, counts and warnings remain retained;
there is no zero snapping. All 855 R formula checks on exact native values pass.

No FlowJo or FACSDiva vendor software is installed by these commands. Native
workspace gating needs supported saved gates, explicit source FCS bindings and,
where required, exact caller-supplied biex tables. The qualification uses public
fixtures, with all 23 packaged cases per host and 53,019,143 event/gate pairs per
pass. These are not the papers' cohort data. FlowJo 9, vendor-binned statistics,
Dean-Jett-Fox/Watson fitting, plugins, acquisition and vendor figures remain gaps.

## Audit and admission

The [original prepared records](cytometry-source-roles-2026-11-23.json) and
[native component checkpoint](CYTOMETRY_INTENSITY_CHECKPOINT_2026-11-23.md) remain
unchanged historical evidence. Their pre-admission flags refer to those stages;
the current canonical decision is recorded in
`omics-2023-2026/cytometry-catalog-validation.json` and the admission packet.
Forty-one reviews are added and three old identities enriched. No raw evidence
location, extraction score, matched name, near-name version or unrelated record
is rewritten. Source-name detection and semantic review are separate from execution.

The guarded `close_cytometry_catalog.py` command offers `--plan`, `--inspect`,
`--apply` and `--verify`. Each output must be NEW under the current checkout's
`build/`; inspect/apply bind a saved `--plan-dir`. Supply `--cache` with the frozen
XML, `--jar` with the exact qualified native producer, `--predecessor-root` with
the unchanged physical prior catalog context and `--predecessor-jar` with its exact
IMPUTE2 producer. Optional `--private-root` and `--predecessor-private-root` request
full private-file checks; omitted checks are reported as zero. These are evidence
checks, not reruns of the scientific fits. Do not use Python `-O` or refresh old
hashes to make an altered prerequisite pass. Preserve failed plans and partials.

The catalog now records one native family for the bounded FlowJo profile;
FACSDiva stays missing. Review the role-specific limits below before planning an
analysis. Obtain original permitted data/settings or qualify the named missing
capability; no paper analysis has been admitted by this source-review operation.
Literature Pages remain withheld. Continue the remaining high-impact families
and all pending source uses; the full 250-paper goal remains active.

## Individually read roles

| Paper | Software | Reported versions | Source role | Remaining requirement |
| --- | --- | --- | --- | --- |
| P005 | flowjo | unreported | Population gating and quantification for lipid-droplet measurements in primary microglia after CCN1 treatment. | Original gate definitions and acquisition inputs are required; no Sony acquisition replacement. |
| P010 | flowjo | 10 | Postacquisition flow-cytometry analysis with gating and threshold schemes shown in supplementary figures. | Figures alone do not supply an executable workspace or exact thresholds. |
| P011 | flowjo | 10.10.1 | Fucci4 cell-cycle population gating using G1 and S/G2/M controls after Aurora acquisition. | Control-based gates are distinct from fitted cell-cycle models. |
| P012 | facsdiva | 9.0 | LSRFortessa acquisition associated with erythrocyte/singlet/infection gates for parasite-growth assays. | FlowJo analysis and GraphPad tests are separately described. |
| P012 | flowjo | 10.9; 10 | Erythrocyte binding analysis uses scatter/singlet gates and AF488-positive fractions; parasite assays use sequential gates and infected-cell fractions. | Separate binding and parasitemia workflows; GraphPad comparisons and plots are separate. |
| P020 | flowjo | 10 | Immune-population gating using CD45, myeloid and antigen-presentation markers. | Aurora acquisition is separate; marker descriptions do not define exact gate boundaries. |
| P021 | facsdiva | 8.0.2 | Collection during fluorescence-activated sorting of frozen-brain nuclei for downstream profiling. | Instrument operation and sample collection are not offline FCS analysis. |
| P024 | facsdiva | 8.0.2 | FANS collection of frozen-brain nuclei for downstream profiling. | No downstream statistical computation is attributed to this acquisition step. |
| P027 | flowjo | 10.7.1 | Flow-cytometry figures after CytExpert collection, with a supplementary gating scheme. | Vendor figures and exact gates are not reproduced by counts alone. |
| P031 | facsdiva | 8.0.1 | Analysis and display jointly assigned with FlowJo in organoid flow-cytometry experiments. | Do not infer a package-specific division of the jointly assigned operations. |
| P031 | flowjo | 10.8.0 | Analysis and display jointly assigned to FlowJo and FACSDiva, with sequential gating in a supplementary figure. | The source does not allocate each analysis/display step to one package. |
| P032 | flowjo | 10.8.1 | Lymphocyte/singlet/live/CD4 gating and median IL2Ralpha/CTLA4 intensities; suppression analysis uses proliferating Teff counts and donor replicate summaries. | Vendor-binned medians, suppression denominator/control definitions and donor aggregation need separate qualification. |
| P038 | facsdiva | unreported | FACSAria II fluorescence-based selection of astrocyte/PDX stable cell lines. | Instrument sorting/selection is outside offline native computation. |
| P040 | facsdiva | 9 | FACSymphony A5 cytometry acquisition after staining thawed tumor-draining lymph-node cells. | The source spells the software name FACS Diva; the spaced-name anchor is retained. |
| P040 | flowjo | 10.0 | Analysis of stimulated T-cell populations from tumor-draining lymph nodes. | Original workspace, population controls and source FCS are unavailable here. |
| P044 | facsdiva | 8.0.3 | FACSAria III collection/sorting of human Lin-negative CD34-positive and mouse stem/progenitor populations. | Separate FlowJo downstream assays are not reassigned to acquisition software. |
| P044 | flowjo | 10 | Flow analysis of HSPC cell-cycle, DNA incorporation, Ki67 and RNA-labeling assays. | No particular fitted cell-cycle model is inferred; assay populations/settings remain required. |
| P048 | facsdiva | unreported | LSRFortessa X50 acquisition and FACSAria Fusion purification. | FlowJo 10.10.0 is explicitly assigned postacquisition data analysis. |
| P048 | flowjo | 10.10.0 | Postacquisition cytometry data analysis following FACSDiva collection. | Acquisition and FACSAria sorting remain separate operations. |
| P049 | facsdiva | 9.0.1 | FACSAria III sorting of NeuN/RBPMS-positive nuclei for RNA sequencing. | Separate immune-cell FlowJo analysis is a distinct role. |
| P049 | flowjo | 10.9 | Immune-cell cytometry analysis with dead-cell exclusion using Zombie Green. | FACSDiva sorting of neuronal nuclei is a separate source role. |
| P051 | flowjo | 10 | Neutrophil analysis includes FlowSOM, ClusterExplorer and UMAP plugins; segmented image-derived events are imported, thresholded and visualized. | Plugins, image-event import and plots are not supplied by the native gate/statistics operations. |
| P052 | facsdiva | 6.1.3 | Living-single-cell fluorescence analysis jointly assigned with FlowJo after FACSAria III measurements. | The source does not resolve the division of analysis between these tools. |
| P052 | flowjo | 10 | Analysis of mRNA-transfer fluorescence in living single cells, jointly assigned with FACSDiva. | The source does not specify the exact division of analysis steps. |
| P059 | facsdiva | 8 | FACSAria II live-cell/neuron single-cell sorting with a 130-micrometer nozzle. | Physical sorting is not replaced by an offline data adapter. |
| P063 | facsdiva | 9.2 | FACSymphony A5 acquisition for phenotypic and peptide-HLA tetramer cytometry. | FlowJo downstream analysis is separately attributed. |
| P063 | flowjo | unreported | Phenotypic and peptide-HLA tetramer flow-cytometry analysis following FACSDiva acquisition. | No exact FlowJo version or executable gate workspace is given in the paragraph. |
| P064 | facsdiva | 8.02 | Sorting NEUN/CHX10-positive nuclei for downstream profiling. | The literal reported version 8.02 is preserved rather than normalized to 8.0.2. |
| P080 | flowjo | 10.10.0 | Erythroid differentiation marker analysis following Attune NxT collection. | Attune acquisition and biological differentiation are separate from analysis. |
| P082 | facsdiva | unreported | Named in the code-availability software list; no local FACSDiva analysis step is specified there. | A software declaration does not justify inferring acquisition or analysis settings. |
| P082 | flowjo | 10.5.3 | Flow-cytometry data analysis; the code-availability section also lists this software. | R statistical analysis is separately described; the declaration is not an executable workspace. |
| P084 | flowjo | 10.8 | Gated blood-cell counts/percentages plus median, robust SD and robust CV measurements of scatter, fluorescence and cell properties. | Transform-dependent vendor binning, off-scale rules and percentile convention are unresolved; unbinned summaries do not close them. |
| P089 | facsdiva | unreported | SORP FACSAria instrument-associated live/singlet fluorescence analysis in overexpression experiments. | Original acquisition/gating settings and data remain required. |
| P119 | facsdiva | 9 | FACSDiva acquisition followed by FlowJo 10 or FCS Express 7 analysis. | Preserve the downstream alternative instead of assigning both analysis packages to every sample. |
| P119 | flowjo | 10 | Postacquisition analysis is described as FlowJo 10 or FCS Express 7. | The alternative is retained; both packages are not presumed to process the same cells. |
| P121 | flowjo | 9 | Flow-cytometry analysis after FACSCalibur/CellQuest Pro collection. | Version 9 is outside the native FlowJo 10 workspace profile. |
| P122 | flowjo | 10 | Dean-Jett-Fox cell-cycle fitting without constraints to propidium-iodide intensity data. | A fitted model is required; gate membership/counts or unbinned moments do not reproduce it. |
| P124 | flowjo | 10.8.1 | Input and tumor-infiltrating CAR T-cell flow analysis using sham and fluorescence-minus-one controls for gating/compensation. | Original gate workspace, compensation matrix and controls remain required. |
| P163 | facsdiva | 8 | BD LSRII cytometry acquisition before FlowJo marker analysis. | Log10 plots and downstream FlowJo processing are distinct. |
| P163 | flowjo | unreported; 10.6.1 | GFP quantification within scatter-gated DAPI-negative cells; separate marker analysis after LSRII acquisition includes log10 plots. | Plot scale does not establish the statistics measurement domain or MFI definition. |
| P215 | flowjo | 10.10 | Flow-cytometry analysis of BD Celesta measurements. | The paragraph does not provide an executable gate workspace or more specific operation. |
| P226 | flowjo | unreported | Analysis of Zombie Violet cell-death cytometry; percent summaries/ANOVA are separately attributed to GraphPad. | Do not assign the separate GraphPad analysis to FlowJo. |
| P228 | flowjo | unreported | Watson Pragmatic cell-cycle modeling in neural progenitor/organoid experiments with EdU measurements. | The fitted model and experiment structure are not gate-count replacements. |
| P232 | flowjo | unreported | Fluorescence histograms and conservative APC-high/low selection, excluding an intermediate range. | The source selection rule is explicit; native event preservation does not erase that biological gate. Vendor histograms are not qualified. |
