# Literature search, bioinformatics tools and important findings

This guide serves all three modes. Begin with
[a working study](study-getting-started.md), then decide which evidence to add.
**Automatic planning means registered, configured work with its required inputs.**
An AI suggestion is not permission to execute arbitrary software.

## Three different kinds of tool use

| Route | Who chooses and executes it | How to enable it |
| --- | --- | --- |
| Deterministic study operation | OmicsMatic compiles and runs a registered operation under the approved plan. | Choose goals and their method/input settings in the hypothesis. No AI account is required. |
| Built-in AI provider search | The configured provider searches during a supported assistance purpose. | Explicitly enable that purpose and its connection/disclosure policy. It does not receive local shell tools. |
| External driver tool | A separately operated agent uses tools exposed by its host. | Configure that host's credentials, allowed commands, filesystem and network permissions. `study.yaml` cannot enforce them. |

A model can propose a method or code text. The built-in proposal handler keeps
code as review material; it never executes model-generated code.
Preapproved registered sensitivity analyses are a separate deterministic route.

## Search the literature without AI

A basic search needs a meaningful public phenotype/outcome label, molecular-field
terms and an approved network environment. For gene-specific searches, supply
unambiguous gene annotation. Unknown probes are not guessed to be gene symbols.

Inside the existing hypothesis, use:

~~~yaml
analysis:
  mode: manual
  goals: [literature_search]
  bioinformatics:
    phenotype_terms: [body mass index, obesity]
    literature_terms: [RNA splicing, isoform ratio, transcript isoform]
    max_pages: 3
    literature_max_features: 20
    literature_max_queries: 1000
    literature_selection: p_value
    fdr: 0.05
~~~

This is a fragment for your own study, not a complete `study.yaml`.
Choose terms describing your actual assay and question. These particular
splicing terms are inappropriate for a study of unrelated measurements.

Before first resolution, merge the fragment, validate, plan, review and approve.
For a frozen project, add the branch with an amendment as in the
[deterministic guide](ai-no-ai.md#add-an-analysis-later). Then run online:

~~~powershell
java -jar $jar study run --project . --actor-id scientist-1
java -jar $jar study status --project .
Start-Process index/index.html
~~~

Do not pass `--offline` when you intend a remote search. A denied remote policy,
disabled downloads or missing annotation can still block the operation.
No AI key is needed for this native Europe PMC search.

It retrieves four field-level strategies: background, hypothesis relevance,
similar studies and potentially conflicting findings. Those can run even when
no gene is significant. Eligible mapped genes also receive direct association,
mechanism and replication/contrary searches.

The signal selection threshold uses the complete valid tested family before
the feature cap. Do not prefilter your input to significant hits and describe
the remainder as the full family. `literature_max_features` controls the
gene-level reading budget, not the statistical testing family.
The contextual protein/sequence budget `max_features` is separate.

## Read the search outputs

Use the index to find the operation, then inspect:

| Artifact | What to do with it |
| --- | --- |
| `reference-catalog.html` and `reference-catalog.yaml` | Read identifiers, bibliographic records, available abstracts/excerpts and retrieval contexts. |
| `literature-selection.tsv` | Check eligible signals, full-family FDR, the review budget and features left outside it. |
| Query and response audits | Check exact terms, source, dates, pagination, limits and failed requests. |
| Coverage and input requests | Distinguish a completed bounded search from unavailable sources, denied access or exhausted budgets. |

Retrieved papers remain candidates. Read the relevant source text, check
species, tissue, phenotype, study design and actual effect direction. Separate
direct evidence from a general mechanism, and retain contradictory findings.
A title match or model citation is not evidence that the paper supports your
claim. A bounded search is not a systematic review or proof of novelty.

For deeper searches, change synonyms, page/query budgets or scope through a
reviewed amendment. Preserve the earlier search. The
[advanced literature guide](bioinformatics-evidence.md#literature-depth-and-excerpts)
explains query strategies, excerpts, deduplication and coverage.

For a bibliography assembled elsewhere, use
[supplied literature and missing searches](study-pipeline-tutorial.md#reuse-supplied-literature-and-complete-missing-searches)
to declare completed evidence and its search coverage. A bibliography alone does
not demonstrate that all required claim-level searches or source appraisals occurred.

## Add AI literature assistance

Configure a [browser or API connection](ai-connections.md), then enable
`assistance.literature: true`. Keep `literature_search` in the goals if you
also want the native, reproducible Europe PMC retrieval.

The two operations have different purposes: `literature_search` is native
retrieval; `literature_support` is optional AI assistance.
The OpenAI adapter requests hosted web search; Gemini requests search grounding.
Browser exchanges require the packet's answer and reference contract.
A generic compatible chat endpoint is not guaranteed to supply retrieval evidence.

Ask for the closest previous studies, conflicting evidence and the relevance of
each source. Verify DOI/PMID and source text yourself. Enabling AI literature
review does not authorize arbitrary bioinformatics programs or shell commands.

## Choose and limit the tools

For a first study, use `analysis.mode: manual`, then grow an explicit goal list.
For example:

~~~yaml
analysis:
  mode: manual
  goals: [discovery_replication, meta_analysis, literature_search]
~~~

This requests a bounded result-first workflow. It does not ask the planner to
fit new participant-level models, construct networks or run MR merely because
a model suggests them. Prerequisites and automatic diagnostics or configured
sensitivity operations can still be added; review the actual graph.

When you want a broader registered workflow, set `mode: auto` and inspect all
inferred goals before approval. It can identify missing work, but cannot create
absent regional statistics, ancestry, sample overlap or instrument validity.

| Task | Supported choices and where to configure them |
| --- | --- |
| Public source discovery | `source_discovery`: built-in GWAS Catalog, eQTL Catalogue/GTEx discovery with source-specific limitations. See [source behavior](bioinformatics-evidence.md#sources-and-scientific-relevance). |
| Protein and sequence context | `bioinformatics_investigation`: reviewed UniProt candidates, AlphaFold context and UCSC reference sequence, subject to annotation and policy. |
| GO/pathway analysis | `gene_ontology`, `pathway_enrichment`, `ranked_gsea` or `gwas_enrichment`, with task-specific gene sets/backgrounds/ranks. See [enrichment](enrichment.md). |
| Networks | The study's `analysis.network` configuration; standalone workflows also offer reference networks, WGCNA, sparse/differential networks, GENIE3 and CARNIVAL. See [network choices](network-followup.md) and the study tutorial; standalone choices are not all interchangeable study backends. |
| Variant consequences and scores | `analysis.variant_followup` declares its supported annotation/scoring route, inputs and provider. See [variant adapters](variant-followup.md) and [registered study examples](study-pipeline-tutorial.md). |
| MR/fine-mapping/colocalization | Relevant goals and reviewed `analysis.bioinformatics` source policies or custom genetic inputs. See [genetic prerequisites](bioinformatics-evidence.md#from-a-source-candidate-to-genetic-inference). |
| Extra approved sensitivity | `assistance.allow_analyses: [leave_one_cohort_out, fixed_random_comparison]`; these are registered local analyses, not permission for arbitrary code. |

Some methods use native Java, some use an installed external adapter, and others
are only available as standalone commands. A framework capability does not mean
that every tool is automatically invoked by `study auto`. Inspect
`study operations --project .`, the method's vignette and your compiled plan.

For example, a supported participant-matrix study can predeclare its native
correlation-network settings:

~~~yaml
analysis:
  mode: manual
  goals: [network_analysis]
  network:
    correlation: spearman
    threshold: 0.7
    independent_samples: true
    preprocessing: Log-transformed measurements with reviewed batch and covariate handling.
~~~

Choose `pearson` or `spearman` for the supported native network, and record
the actual preprocessing and independence basis. This fragment needs a suitable
participant matrix; the result-first rehearsal has no such matrix and cannot
construct this network. It does not select WGCNA or GENIE3 as a hidden backend.
For those standalone workflows, use their documented command and import a
supported completed graph when appropriate.

## Block unwanted work or access

There is **no general `allowed_tools`, `blocked_tools`, MCP-server list or
provider-tool JSON field in study.yaml**. Do not add invented configuration keys.
Use the control that corresponds to the action:

| Intention | Supported control |
| --- | --- |
| Avoid an optional scientific branch | Use manual mode, omit its goal and explicit triggering configuration, then inspect the plan. A dependency of another requested branch may still be required. |
| Keep AI off | Omit assistance or set `assistance.enabled: false`. |
| Disable a provider-search purpose | Leave `literature` and `related_phenotypes` disabled. Native literature retrieval is controlled separately by its goal. |
| Limit AI-requested sensitivity | Set `allow_analyses` only to approved registered choices, or `[]`. Other automatic study QC/sensitivity rules are separate. |
| Block built-in network work | Use `--offline` and the reviewed resource policy; preinstall required references. |
| Block remote aggregate assistance and study-related public queries | Set `project.data_policy.llm.remote_transmission: deny_all`. Generic reference installation has separate resource controls. |
| Block a specific service/domain or executable | Enforce it with the execution environment's firewall, gateway and executable permissions. Unsupported source calls may remain waiting. |
| Restrict an external agent | Configure its host's tool/command allowlist and data permissions, as in the [driver guide](ai-driver.md#give-the-driver-a-concrete-task). |

The built-in adapter does not expose all provider web-search filtering options in
the YAML. If a required domain restriction cannot be enforced by the configured
service, leave that remote route off or use a controlled environment/manual
review. `--offline` applies to built-in requests, including loopback HTTP;
it cannot control a separately running agent or all installed tools.

## Emphasize findings with an evidence trail

Separate **what to test**, **what to investigate**, and **what to discuss most
prominently**. A result can be scientifically important without having the smallest
P value. Changing a narrative priority must not silently change the testing family.

Before analysis, put the primary question, phenotype, cohort roles and goals in
the contract. Use field/phenotype/tissue search terms to define relevant follow-up.
Do not change discovery thresholds after seeing a favored gene without documenting
a new exploratory direction.

After analysis:

1. Review the complete findings and unresolved evidence. If needed, request
   `finding_inventory` for a deterministic study-wide inventory.
2. With approved AI assistance, enable `impact: true` for comparative impact
   proposals. These are scientific suggestions to review, not causal validation.
3. Check the proposed priorities against replication, effect magnitude and
   uncertainty, heterogeneity, novelty, biological relevance and contrary evidence.
4. Record accepted emphasis in a new manuscript revision, with actual source IDs,
   impact reasons and linked Results paragraphs. Retain other findings in the
   supplement and preserve null, discordant and incomplete results.

A manuscript revision is a **separate YAML request**, not an arbitrary prompt
field in `study.yaml`. A Discussion paragraph can use:

~~~yaml
- id: discussion-primary
  section: discussion
  discussion_role: principal_finding
  impact_rank: 1
  result_ids: [results-meta]
  impact_reason: Independent replication makes this finding a priority for discussion.
  text: >-
    Replace with the verified interpretation, uncertainty, relevant prior
    evidence and limitations of this specific finding.
  sources: [H1.meta_analysis]
  references: []
  rationale: Emphasize the replicated result while retaining contrary evidence.
~~~

This is an illustrative `sections` list item. `results-meta` must identify an
actual Results paragraph in the same revision; `H1.meta_analysis` must resolve
to your completed evidence. Replace the rationale if replication is absent.
Do not submit the placeholder prose as a finding.

Use the [complete revision request](bioinformatics-evidence.md#creating-a-manuscript-revision)
and [Results/Discussion writing model](bioinformatics-evidence.md#results-and-discussion-writing-model).
Run a new revision with a fresh bundle path:

~~~powershell
java -jar $jar study manuscript revision.yaml --project . --out manuscript-revision-01 --actor-id scientist-1
java -jar $jar study manuscript-verify --project manuscript-revision-01
~~~

For literature reading priority before a cap, `bioinformatics.literature_selection`
accepts `p_value`, `impact` or `auto`. Impact requires a complete provisional
comparison with configured assistance; failure does not silently fall back.
See [provisional impact selection](study-pipeline-tutorial.md#provisional-impact-before-the-literature-cap).
This reading priority is separate from FDR and from the final manuscript ranking.

If a finding needs a new experiment or analysis, record an observation and propose
an amendment. The [deterministic guide](ai-no-ai.md#record-an-unusual-finding)
shows the commands; an advisor or driver can help draft the proposal.

Continue with [no AI](ai-no-ai.md), [advisor](ai-advisor.md), [driver](ai-driver.md),
or the [YAML field reference](study-yaml-reference.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [Europe PMC (2026) — Europe PMC RESTful Web Service](../CITATIONS.md#europe-pmc-rest)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
