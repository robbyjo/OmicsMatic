"""Regenerate selected Markdown-backed vignettes and xWAS validation. Requires Python Markdown.

Run from any directory: python docs/render-xwas-pages.py
For selected pages only: python docs/render-xwas-pages.py --pages ai-no-ai ai-advisor ai-driver
After rendering, update citation panels: python docs/render-citations.py
The static output is checked in; website deployment needs no Python runtime.
"""
from pathlib import Path
import html
import re
import shutil
import markdown
import argparse

ROOT = Path(__file__).resolve().parents[1]
STUDY_GUIDES = ['ai-no-ai', 'ai-advisor', 'ai-driver', 'study-getting-started', 'study-yaml-reference', 'ai-connections', 'study-tools-literature']
PAGES = {
    'ai-no-ai': ('Mode 1: deterministic analysis without AI', 'Prepare study.yaml, run a complete or partial study, reuse completed work and add analyses through reviewed amendments.', 'Beginner guide · Current source build'),
    'ai-advisor': ('Mode 2: AI as a scientific advisor', 'Configure browser or API advice, complete an audited exchange, review literature and keep control of scientific decisions.', 'Beginner guide · Current source build'),
    'ai-driver': ('Mode 3: AI as an external workflow driver', 'Connect a supervised external agent, scope its tools, prepare a reviewable plan and coordinate approved execution.', 'Beginner guide · Current source build'),
    'study-getting-started': ('Start here: your first study.yaml', 'Understand the minimum study contract, place data or completed analyses, and run all, selected or remaining work.', 'Beginner guide · Current source build'),
    'study-yaml-reference': ('study.yaml field guide', 'Explore optional study fields, dataset metadata, goals, completed results, resources and disclosure policies.', 'Study configuration · Current source build'),
    'ai-connections': ('Connect an AI advisor: browser, API key or OAuth token', 'Complete study.yaml connection examples, environment credentials, purpose controls and troubleshooting.', 'AI setup · Current source build'),
    'study-tools-literature': ('Literature search, bioinformatics tools and important findings', 'Run native or AI-assisted searches, choose and restrict tools, and record evidence-based manuscript priorities.', 'Beginner guide · Current source build'),
    "bioinformatics-evidence": ("Bioinformatics evidence workflow", "Audited source discovery, regional genetic inference, protein and sequence investigation, literature search and local evidence packaging.", "CLI tutorial Â· Source build"),
    "single-cell": ("Replicated single-cell RNA analysis", "Sparse QC, pseudobulk, donor-aware state and abundance, functional scores and exploratory representations.", "CLI + Java/R Â· Source build"),
    "spatial-analysis": ("Physical spatial RNA analysis", "Tissue graphs, Moran and Geary tests, neighborhoods, sample-aware comparisons and distance gradients.", "CLI + Java Â· Source build"),
    "project-configuration": ("Project configuration", "Project-over-local YAML precedence and reproducible CLI runs.", "CLI Â· Source build"),
    "auditable-omics-pipeline": ("Auditable omics research pipeline", "A deterministic study contract, approval-gated execution, amendments, provenance, and guarded future LLM assistance.", "CLI Â· Source build"),
    "study-pipeline-tutorial": ("Auditable study pipeline tutorial", "Run a result-first study end to end, inspect its evidence chain, and design a defensible patient-data boundary for future LLM assistance.", "CLI tutorial Â· Source build"),
    "study-questions-and-decisions": ("Study questions and decisions subsystem design", "Implemented deterministic scientist questions, audited batch answers and amendment proposals, followed by the broader subsystem design.", "Source build Â· First tranche and roadmap"),
    "variant-followup": ("Variant annotation and scoring", "Local snapshots, POST/API lookup, VEP/ANNOVAR adapters and transparent evidence integration.", "CLI Â· Source build"),
    "network-followup": ("Networks and candidate regulators", "Reference networks, WGCNA, sparse and differential associations, GENIE3 and CARNIVAL.", "CLI + Java/R Â· Source build"),
    "censored-ordinal": ("Censored and ordinal regression", "Fit Tobit, parametric survival-time and ordered logit/probit likelihoods with validated covariance.", "CLI + Java Â· Source build"),
    "sampling-models": ("Rare-event and survey inference", "Separate coefficient and prevalence corrections, and model sampling weights, strata and PSUs explicitly.", "CLI + Java Â· Source build"),
    "acat-rare-variants": ("ACAT rare-variant tests", "Run ACAT-V and canonical six-component ACAT-O from pooled scores or participant-level null models.", "CLI + Java Â· v0.3.6"),
    "enrichment": ("Gene-set enrichment", "Define analysis-specific backgrounds, select defensible tests, and interpret overlapping gene, disease and phenotype sets.", "CLI + Java Â· v0.3.6"),
    "ldsc": ("LDSC genetic architecture", "Estimate heritability and shared genetic architecture, then carry sampling uncertainty into the next model.", "CLI + Java Â· v0.3.6"),
    "predicted-omics": ("Genetically predicted TWAS and PWAS", "Connect molecular prediction weights to GWAS evidence with explicit allele, scale and LD alignment.", "CLI + Java Â· v0.3.6"),
    "genomic-factor": ("Shared genetic factors", "Model shared genetic variation and inspect common-factor SNP effects alongside heterogeneity.", "CLI + Java Â· v0.3.6"),
    "prediction-scores": ("Prediction scores", "Train portable scores and evaluate frozen predictions in an independent cohort.", "CLI + Java Â· v0.3.6"),
    "predictions-and-contrasts": ("Predictions, scenarios, and marginal effects", "Report expected responses, standardized scenario contrasts, risk ratios, and marginal effects with covariance-aware uncertainty.", "CLI + Java Â· v0.3.6"),
    "instrumental-variable-regression": ("Individual-level instrumental-variable regression", "Fit linear 2SLS models with robust or clustered inference, instrument-strength diagnostics, and explicit identification limits.", "CLI + Java Â· v0.3.6"),
    "conditional-score-conditioning": ("Summary-only conditional score analysis", "Import compatible cohort score blocks, condition by Schur complement, and pool auditable aggregate-data results.", "CLI + Java Â· v0.3.6"),
    "latent-confounders-and-batch": ("Latent confounders and batch effects", "Estimate unknown sample factors with PCA, SVA, AutoSVA, or PEER, and adjust known batches with ComBat.", "CLI + Java Â· v0.3.6"),
    "differential-regions-testing-imputation": ("Differential, regional, multiple-testing, and imputation workflows", "Run moderated continuous or count differential analysis, spatial EWAS regions, prespecified adaptive or hierarchical testing, and uncertainty-aware multiple imputation.", "CLI + Java Â· v0.3.6"),
}

def render(source, destination, title, description, root, eyebrow="CLI + Java Â· v0.3.6"):
    text = source.read_text(encoding="utf-8").split("\n", 1)[1]
    md = markdown.Markdown(extensions=["fenced_code", "tables", "toc"])
    body = md.convert(text)
    def links(match):
        url = match.group(1)
        if url.endswith("TODO.md"):
            url = "https://github.com/robbyjo/OmicsMatic/blob/main/TODO.md"
        elif source.parent == ROOT / "docs" and url.startswith("../examples/"):
            url = "https://github.com/robbyjo/OmicsMatic/blob/main/" + url[3:]
        elif not url.startswith("http"):
            url = re.sub(r"(?i)CITATIONS\.md", "citations.html", url)
            url = url.replace(".md", ".html")
        return 'href="' + url + '"'
    body = re.sub(r'href="([^"]+)"', links, body)
    body = re.sub(r'<pre><code(?: class="language-([^\"]+)")?>([\s\S]*?)</code></pre>',
        lambda m: '<div class="code-window"><div class="code-bar"><span>'
        + html.escape(m.group(1) or "Input / output") + '</span></div><pre><code>'
        + m.group(2) + '</code></pre></div>', body)
    if destination.stem in STUDY_GUIDES:
        body = body.replace("<table>", '<div class="result-table"><table>').replace("</table>", "</table></div>")
    # The citation generator replaces the Markdown heading with a slugged panel.
    def toc_id(item):
        if destination.parent.name == "vignettes" and item["id"] == "scientific-citations":
            return "scientific-citations-" + destination.stem
        return item["id"]
    toc = ''.join(f'<a href="#{toc_id(item)}">{html.escape(item["name"])}</a>' for item in md.toc_tokens)
    guide_style = '<style>.study-guide .result-table table{min-width:540px;table-layout:fixed}.study-guide .result-table th,.study-guide .result-table td{white-space:normal;overflow-wrap:anywhere}</style>' if destination.stem in STUDY_GUIDES else ''
    article_class = "prose study-guide" if destination.stem in STUDY_GUIDES else "prose"
    page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="{html.escape(description)}"><title>{html.escape(title)} · OmicsMatic</title>
<link rel="stylesheet" href="{root}assets/styles.css"><script src="{root}assets/app.js" defer></script>{guide_style}</head>
<body><site-header root="{root}"></site-header><main id="main">
<section class="page-hero"><div class="container"><div class="breadcrumbs"><a href="{root}index.html">Home</a><span>/</span><a href="{root}vignettes/index.html">Vignettes</a><span>/</span><span>{html.escape(title)}</span></div>
<span class="eyebrow">{html.escape(eyebrow)}</span><h1>{html.escape(title)}</h1><p>{html.escape(description)}</p></div></section>
<div class="container page-layout"><article class="{article_class}">{body}</article>
<aside class="toc"><strong>On this page</strong>{toc}</aside></div>
</main><site-footer root="{root}"></site-footer></body></html>
'''
    destination.write_text(page, encoding="utf-8")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pages", nargs="+", choices=sorted(PAGES), help="Render only these vignettes, without other site outputs.")
    args = parser.parse_args()
    if args.pages:
        for slug in args.pages:
            title, description, eyebrow = PAGES[slug]
            render(ROOT / f"docs/vignettes/{slug}.md", ROOT / f"site/vignettes/{slug}.html", title, description, "../", eyebrow)
        return
    for slug, (title, description, eyebrow) in PAGES.items():
        render(ROOT / f"docs/vignettes/{slug}.md", ROOT / f"site/vignettes/{slug}.html", title, description, "../", eyebrow)

    schema_output = ROOT / "site/schemas"
    schema_output.mkdir(parents=True, exist_ok=True)
    for schema in (ROOT / "docs/schemas").glob("*.schema.json"):
        shutil.copyfile(schema, schema_output / schema.name)

    render(ROOT / "docs/xwas-followup-validation.md", ROOT / "site/xwas-followup-validation.html",
           "xWAS follow-up validation", "Independent numerical fixtures, reproducible checks and explicit estimator boundaries.", "./")
    render(ROOT / "docs/latent-confounder-validation.md", ROOT / "site/latent-confounder-validation.html",
           "Latent-confounder validation and source audit", "Pinned upstream sources, numerical reference gates, performance design, and explicit implementation boundaries.", "./",
           "Source audit Â· Reproducible validation")
    render(ROOT / "docs/regression-inference-validation.md", ROOT / "site/regression-inference-validation.html",
           "Regression inference validation", "Independent R fixtures, numerical checks, workload timings and estimator boundaries.", "./",
           "Source build Â· Reproducible validation")
    render(ROOT / "docs/inference-workflows-validation.md", ROOT / "site/inference-workflows-validation.html",
           "Modern inference workflow validation", "Frozen Bioconductor comparisons, independent formula checks, reproducible commands, and explicit estimator boundaries.", "./",
           "Source audit Â· Reproducible validation")
    render(ROOT / "docs/audit-fixes-validation.md", ROOT / "site/audit-fixes-validation.html",
           "Statistical audit fixes", "Regression checks, bootstrap imputation contracts and measured smoothing performance.", "./",
           "Source audit Â· Reproducible validation")
    render(ROOT / "docs/cell-spatial-validation.md", ROOT / "site/cell-spatial-validation.html",
           "Single-cell and spatial validation", "Independent RNA and tissue workflow fixtures, scientific units and explicit remaining roadmap.", "./",
           "Source build Â· Reproducible validation")

    render(ROOT / "docs/plotting.md", ROOT / "site/plotting.html",
           "Command-line plotting", "Create plots from CSV or TSV tables, with worked commands, chart mappings and export options.", "./",
           "CLI plotting")


if __name__ == "__main__":
    main()
