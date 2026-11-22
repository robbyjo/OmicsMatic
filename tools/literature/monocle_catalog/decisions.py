"""Reviewed source-role boundaries; no new review or whole-paper credit."""

ROLES = {'P021': {'analysis_role': 'Monocle3 lineage trajectories and modified covariate-aware gene testing',
          'binding': 'bounded-original100-and-native-prepared-components',
          'commands': ['GRAPH-1.0.0',
                       'NEIGHBORS-1.0.0',
                       'WEIGHTS-1.0.0',
                       'RESPONSES-1.0.0',
                       'MORAN-LM',
                       'ORDER'],
          'scope': 'The paper reports UMAT, not UMAP. Exact original Monocle3 package1.0.0 prepared-CDS '
                   'graph/neighbor stages and explicit native weight, response/design preparation, ordering '
                   'and residual-Moran primitives are now qualified in the source-built JAR. These require '
                   'the complete caller-supplied population, coordinates/factors, roots, rankings/weights, '
                   'size factors and numeric covariate coding. They do not reconstruct the author execution '
                   'commit, UMAT/raw-count preparation, selected shortest paths, metadata coding, metacell '
                   'smoothing, spline fitting or clustering. The privately inspected author fork was not run '
                   'or redistributed. Its per-gene FAIL/OK-only BH behavior is distinct from native '
                   'complete-family refusal for unresolved residuals or unsupported self-arcs. No stock '
                   'unadjusted Moran or generic preprocessing is credited as the modified paper test.',
          'paragraphs': [102],
          'reported_edition': '1.0.0',
          'usage_summary': 'Used Monocle3 1.0.0 on the reported UMAT embedding to reconstruct lineage '
                           'trajectories and select shortest paths; a modified graph_test with covariates '
                           'identified trajectory-associated genes.'},
 'P024': {'analysis_role': 'Separate py-monocle trajectory benchmark and Braak-selected roots',
          'binding': 'separate-py-monocle-provider-unqualified',
          'commands': [],
          'scope': 'This paragraph uses the separate py-monocle Python implementation, although the paper '
                   'calls it a Monocle3 wrapper. R/native Monocle controls do not qualify that provider, its '
                   'exact paper commit or settings. The recorded intended use is '
                   'educational/research/nonprofit; the earlier waiting-for-use-category note is stale. No '
                   'py-monocle runtime or analysis is qualified here, and its separate missing row is '
                   'unchanged. Preserve the microglia population, donor-root eligibility,200 trials and '
                   'retention of the highest Braak correlation. That selected correlation is not independent '
                   'predictive validation.',
          'paragraphs': [107],
          'reported_edition': None,
          'usage_summary': 'Compared pseudotime from py-monocle, described in the paper as a Monocle 3 '
                           'Python wrapper, with neural-network predictions and Palantir in microglia; tried '
                           '200 eligible starting cells and retained the highest Braak-stage correlation.'},
 'P043': {'analysis_role': 'Cicero co-accessibility with Monocle3 platform context',
          'binding': 'separate-cicero-platform-context',
          'commands': [],
          'scope': 'The paragraph establishes Cicero for Monocle3 co-accessibility, not a Monocle trajectory '
                   'run. Cicero already has its own scoped original co-accessibility and supplied-prediction '
                   'workflows; this admission adds no Cicero or trajectory credit for this context. Exact '
                   'paper patches, shuffled-background fitting, filtering direction, FDR threshold, TSS '
                   'annotation and distal/proximal population choices remain distinct. Preserve the reported '
                   'failed Hypendymal_NN subclass and the literal filtering wording; do not reverse its '
                   'meaning.',
          'paragraphs': [51],
          'reported_edition': None,
          'usage_summary': 'Used Cicero for Monocle 3 to infer cis-co-accessible cCRE pairs per cell '
                           'subclass, using mm10, default Cicero parameters and 500 kb genomic windows.'},
 'P051': {'analysis_role': 'Monocle3 and Batchelor integration followed by pseudotime',
          'binding': 'exact137-batchelor120-components-paper-settings-unreported',
          'commands': ['ALIGN-1.3.7', 'ORDER'],
          'scope': 'The paragraph explicitly reports Monocle3 1.3.7 and Batchelor1.20.0. The source-built '
                   'ALIGN-1.3.7 action now executes the original selected1.3.7/1.20 prepared pipeline with '
                   'complete saved models and native graph audits; these are no longer source-only '
                   'candidates. Exact paper preprocessing, integration/reduction/clustering/graph settings, '
                   'roots and transformations remain unstated and are not inferred. Batchelor already has '
                   'separate scoped credit covering P040/P051; no extra Batchelor/helper credit is added. '
                   'The separate reported Wilcoxon test used continuity correction and is not a Monocle '
                   'model.',
          'paragraphs': [86],
          'reported_edition': '1.3.7',
          'usage_summary': 'Applied the Monocle3 pipeline with Batchelor 1.20.0 integration, Monocle3 1.3.7 '
                           'dimensional reduction/clustering and pseudotime; a separate Wilcoxon test '
                           'compared immature control and Tgfbr2-mutant cells.'},
 'P085': {'analysis_role': 'Monocle3 spatial-expression gene GLMs with amyloid covariates',
          'binding': 'exact131-prepared-models-paper-formula-unreported',
          'commands': ['MODELS-1.3.1'],
          'scope': 'This is gene-wise GLM inference, not pseudotime. Original1.3.1 prepared '
                   'fit_models/coefficient workflows and optional native Pearson filtering are qualified; '
                   'the old grr/R4.6.1 skip is historical. Complete caller-specified units, family, '
                   'numeric/factor coding and testing population remain required. Original '
                   'quasi-Poisson/t-coefficient and grouped-Holm behavior is preserved; paper Wald/FDR '
                   'wording and documentation BH wording do not establish an unreported override. Human '
                   'grey-matter filtering, mouse regional fits, separate stains and positive Pearson '
                   'selection remain explicit separate population/selection steps. The exact author formula, '
                   'normalization and executed commit are not recovered.',
          'paragraphs': [55],
          'reported_edition': '1.3.1',
          'usage_summary': 'Used monocle3 1.3.1 fit_models GLMs to relate spatial gene expression to amyloid '
                           'Getis-Ord Gi* hotspot scores, adjusting for biological sample and UMI count; '
                           'followed significant Wald-test results with positive Pearson-correlation '
                           'filtering.'},
 'P099': {'analysis_role': 'Monocle2 differential gene, exon and accessibility testing',
          'binding': 'selected226-typed-differential-paper-patch-unreported',
          'commands': ['DIFFERENTIAL-2.26.0'],
          'scope': 'The source-built typed original Monocle2 2.26.0 adapter preserves complete '
                   'differential-test populations, size factors, dispersions and native BH audits. The paper '
                   'reports Monocle2 without an exact patch;2.26.0 is a selected qualified edition, not a '
                   'recovered paper patch. Supporting Methods retain distinct '
                   'subcluster/exon/accessibility/human populations, maximum5000 sampling, sex covariates '
                   'and RT-well/mouse replicate definitions. Exact family, full formulas and normalization '
                   'remain unresolved. TPM thresholds do not establish raw-count eligibility; cells are not '
                   'mouse replicates. Retained original free-NB/binomial failures remain unadmitted; do not '
                   'drop failed genes, replace failures with p/q=1 or substitute Monocle3 tests.',
          'paragraphs': [36, 37, 38, 46, 54],
          'reported_edition': None,
          'usage_summary': 'Used Monocle 2 differentialGeneTest for gene/exon and accessibility comparisons, '
                           'age/AD analyses with sex covariates, subtype fraction likelihood-ratio tests, '
                           'and human AD/control expression comparisons; populations and '
                           'significance/effect/expression filters differ by analysis.'},
 'P117': {'analysis_role': 'Monocle3 prepared-embedding graph learning and rooted ordering',
          'binding': 'exact133-six-reported-control-combinations',
          'commands': ['GRAPH-1.3.3', 'ORDER'],
          'scope': 'Original Monocle3 1.3.3 graph learning and optional rooted ordering are qualified for '
                   'complete caller-prepared UMAP/cluster/partition populations, including all six '
                   'ncenter130/140/150 by minimal_branch_len15/20 combinations with close_loop=FALSE. These '
                   'are no longer source-only candidates. Exact selected cells/embedding, mapping of '
                   'controls to transitions, executed commit and root identifiers remain unstated. The paper '
                   'uses an assumed transition direction; synthetic roots and native supplied-graph ordering '
                   'do not recover its biological direction. Never substitute1.4.27 or infer a root.',
          'paragraphs': [53],
          'reported_edition': '1.3.3',
          'usage_summary': 'Constructed trajectories on selected transition regions with monocle3 1.3.3 '
                           'learn_graph, varying ncenter across 130/140/150 and minimal_branch_len across '
                           '15/20, with close_loop=FALSE; ordered from a terminal node in the assumed '
                           'transition direction.'},
 'P134': {'analysis_role': 'Protein-activity pseudotrajectory in malignant pancreatic cells',
          'binding': 'unreported-edition-prepared-components-only',
          'commands': ['ORDER', 'RESPONSES-1.0.0'],
          'scope': 'The inputs are metaVIPER protein-activity profiles for1856 malignant cells, not raw RNA '
                   'counts. Monocle generation/patch, family, graph options and roots are unstated, so no '
                   'specific original edition or count workflow is credited as paper-equivalent. Only '
                   'explicitly caller-prepared graph ordering or explicitly declared finite-response '
                   'arithmetic is available as a general component. Do not infer a log/size-factor transform '
                   'or count-model eligibility. metaVIPER/ARACNe inference, clustering, cross-cohort '
                   'enrichment and biological state interpretation remain separate.',
          'paragraphs': [43, 10],
          'reported_edition': None,
          'usage_summary': 'Inferred Monocle pseudotrajectories from metaVIPER protein-activity profiles of '
                           '1,856 malignant pancreatic cells, alongside separate Seurat/Louvain state '
                           'clustering.'},
 'P233': {'analysis_role': 'Condition-separated Monocle3 stromal-cell trajectories',
          'binding': 'unreported-patch-condition-specific-prepared-components',
          'commands': ['ORDER', 'GRAPH-1.0.0'],
          'scope': 'The paper reports Monocle3 after separate FD/WT subsetting, Liger reclustering and '
                   'Seurat-wrapper cell_data_set conversion. The exact patch, graph controls, roots, '
                   'conversion layers and complete embedding population remain unstated. Qualified '
                   'prepared-CDS/graph components require those explicit inputs and are not assigned as the '
                   'unreported paper edition. Preserve condition-specific objects; do not pool conditions or '
                   'substitute the current1.4.27 raw-count probe as a paper reproduction. '
                   'Seurat/Harmony/Liger processing and wrapper conversion remain separate.',
          'paragraphs': [9, 23],
          'reported_edition': None,
          'usage_summary': 'Converted Seurat objects to cell_data_set objects through a wrapper and inferred '
                           'Monocle 3 stromal trajectories after condition-specific FD/WT subsetting and '
                           'reclustering.'}}
