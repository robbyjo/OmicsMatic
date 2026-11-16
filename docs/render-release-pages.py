"""Render public release guides and the frozen tool directory; never edit catalog evidence."""
from collections import Counter
from html import escape
from pathlib import Path
import hashlib
import json
import os
import re
import sys

import markdown
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"
VERSION = "0.4.0"
GITHUB = f"https://github.com/robbyjo/OmicsMatic/blob/v{VERSION}/"
GUIDES = ["release-0.4.0", "release-0.4.0-freeze", "native-tool-replacements",
          "manuscript-writing-template", "runtime-requirements"]


def shell(title, description, body, scripts=""):
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="{escape(description, quote=True)}"><title>{escape(title)} · OmicsMatic</title>
<link rel="stylesheet" href="assets/styles.css"><script src="assets/app.js" defer></script>{scripts}</head>
<body><site-header root="./"></site-header><main id="main">
<section class="page-hero"><div class="container"><div class="breadcrumbs"><a href="index.html">Home</a><span>/</span><span>v{VERSION}</span></div>
<span class="eyebrow">OmicsMatic v{VERSION}</span><h1>{escape(title)}</h1><p>{escape(description)}</p></div></section>
{body}</main><site-footer root="./"></site-footer></body></html>
'''


def render_guide(slug):
    source = ROOT / "docs" / f"{slug}.md"
    soup = BeautifulSoup(markdown.markdown(source.read_text(encoding="utf-8"),
                          extensions=["fenced_code", "tables", "toc"]), "html.parser")
    title = soup.h1.get_text()
    soup.h1.decompose()
    for anchor in soup.select("a[href]"):
        url = anchor["href"]
        if url.startswith(("http:", "https:", "mailto:", "#")):
            continue
        path, separator, fragment = url.partition("#")
        resolved = (source.parent / path).resolve()
        assert resolved.exists(), f"Missing guide link: {slug}: {url}"
        relative = resolved.relative_to(ROOT).as_posix()
        target = SITE / relative.removeprefix("docs/")
        target = target.with_suffix(".html") if target.suffix == ".md" else target
        if relative.startswith("docs/") and (target.is_file() or target.stem in GUIDES):
            anchor["href"] = target.relative_to(SITE).as_posix() + (separator + fragment if separator else "")
        else:
            base = GITHUB.replace("/blob/", "/tree/") if resolved.is_dir() else GITHUB
            anchor["href"] = base + relative + (separator + fragment if separator else "")
    for table in soup.select("table"):
        table.wrap(soup.new_tag("div", attrs={"class": "result-table"}))
    toc = "".join(f'<a href="#{escape(h["id"])}">{escape(h.get_text())}</a>' for h in soup.select("h2[id]"))
    description = {
        "release-0.4.0": "New tools, cross-omics adjustment, reusable study results and evidence-linked reporting.",
        "release-0.4.0-freeze": "The frozen tool boundary, software validation and known catalog qualification limitation.",
        "native-tool-replacements": "Choose supported Java-only bioinformatics operations and understand their limits.",
        "manuscript-writing-template": "Turn linked scientific evidence into a coherent, reviewable manuscript.",
        "runtime-requirements": "Prepare Java, optional numerical backends and separate upstream runtimes.",
    }[slug]
    body = f'<div class="container page-layout"><article class="prose release-guide">{soup}</article><aside class="toc"><strong>On this page</strong>{toc}</aside></div>'
    (SITE / f"{slug}.html").write_text(shell(title, description, body), encoding="utf-8", newline="\n")


def render_toolbox():
    frozen = json.loads((ROOT / "docs/releases/0.4.0/toolset-freeze.json").read_text(encoding="utf-8"))
    for name, expected in frozen["catalog_files"].items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected["sha256"], name
    tools = json.loads((ROOT / "docs/literature/omics-2023-2026/tool-inventory.json").read_text(encoding="utf-8"))
    counts = Counter(t["implementation_status"] for t in tools)
    assert dict(counts) == frozen["status_counts"]
    labels = {"native-subset-validated": "Native subset", "upstream-adapter-validated": "Upstream workflow",
              "existing-adapter": "Existing adapter", "existing-lookup": "Existing lookup",
              "existing-export": "Existing export", "method-overlap": "Related native method",
              "missing": "Not integrated", "excluded-by-user": "Excluded"}
    options = '<option value="available">Available and related methods (180)</option><option value="all">All cataloged families (446)</option>'
    options += "".join(f'<option value="{status}">{label} ({counts[status]})</option>' for status, label in labels.items())
    intro = f'''<section class="section"><div class="container">
<div class="note"><strong>122 newly integrated families, with explicit scopes.</strong>21 native subsets and 101 upstream workflows are recorded in the frozen catalog. Existing adapters, lookups, exports and related methods are listed separately. These are not counts of complete packages or installed runtimes. <a href="release-0.4.0.html">Release guide</a> · <a href="native-tool-replacements.html">Java-only workflows</a></div>
<p class="toolbox-intro">Choose an operation for a scientific question, check its inputs and runtime, then retain its outputs and provenance. Tools without a typed study importer can contribute linked external artifacts. <a href="vignettes/analysis-pipeline-specification.html#4a-useful-bioinformatics-tools-and-result-integration">Follow-up selection and result integration</a>.</p>
<div class="toolbox-controls"><label>Find a tool or method<input type="search" data-tool-search placeholder="Name, method, assay or supported operation"></label><label>Coverage<select data-tool-status>{options}</select></label></div>
<p data-tool-count role="status" aria-live="polite">446 cataloged families. Search and filters activate when JavaScript is available.</p>
<p class="toolbox-intro">The original qualifications are retained; the merged-source catalog-wide check remains unresolved. <a href="release-0.4.0-freeze.html">Validation details and limitations</a>. iDEP is not admitted; development records do not grant coverage.</p>
<p data-tool-empty hidden>No tools match these filters.</p><div class="toolbox-list">'''
    cards = []
    for tool in sorted(tools, key=lambda t: t["name"].casefold()):
        status = tool["implementation_status"]
        scope = tool.get("implementation_scope") or tool.get("coverage_note") or tool["role"]
        validation = tool.get("validation_note") or "No additional validation claim is made by this directory."
        search = " ".join(str(tool.get(k, "")) for k in ["id", "name", "role", "implementation_scope", "coverage_note"]).casefold()
        links = []
        prefix = tool["id"].upper().replace("-", "_")
        candidates = sorted((ROOT / "docs/literature").glob(prefix + "*_WORKFLOW*.md"))
        for guide in candidates[:2]:
            links.append(f'<a href="{GITHUB}{guide.relative_to(ROOT).as_posix()}">Workflow guide</a>')
        for key in ["implementation_source", "existing_source"]:
            source = tool.get(key)
            if isinstance(source, str) and (ROOT / source).is_file():
                links.append(f'<a href="{GITHUB}{source}">Implementation source</a>')
                break
        if not links:
            links.append(f'<a href="{GITHUB}docs/literature/omics-2023-2026/tool-inventory.json">Catalog record</a>')
        cards.append(f'''<article class="tool-entry" id="tool-{escape(tool['id'], quote=True)}" data-tool-entry data-status="{status}" data-search="{escape(search, quote=True)}">
<div class="tool-heading"><h2>{escape(tool['name'])}</h2><span class="tag">{labels[status]}</span></div><p>{escape(tool['role'])}</p>
<details><summary>Supported scope and validation</summary><p>{escape(scope)}</p><p>{escape(validation)}</p></details>
<p class="tool-links">{' · '.join(links)}</p></article>''')
    body = intro + "\n".join(cards) + '</div></div></section>'
    (SITE / "toolbox.html").write_text(shell("Bioinformatics toolbox", "Find supported operations, original-engine workflows and the remaining gaps in the frozen v0.4.0 catalog.", body, '<script src="assets/toolbox.js" defer></script>'), encoding="utf-8", newline="\n")
    assert len(BeautifulSoup(body, "html.parser").select("[data-tool-entry]")) == 446


if __name__ == "__main__":
    for slug in GUIDES:
        render_guide(slug)
    render_toolbox()
    print("Rendered five release guides and 446 exact-scope tool entries; frozen catalog hashes match.")
