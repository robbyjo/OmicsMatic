"""Render the analysis specification and status using the static-site shell.

Run: python docs/render-analysis-pipeline.py
Requires Python Markdown and Beautiful Soup; static output is checked in.
"""
from html import escape
from pathlib import Path
import importlib.util
import json
import re
import sys

import markdown
from bs4 import BeautifulSoup

root = Path(__file__).resolve().parents[1]
slug = "analysis-pipeline-specification"
source_path = root / "docs" / "vignettes" / f"{slug}.md"
source = source_path.read_text(encoding="utf-8")
source_date = re.search(r"Scientific workflow specification, updated (\d{4}-\d{2}-\d{2})", source)
assert source_date is not None, "Specification update date is required"
html = markdown.markdown(source, extensions=["fenced_code", "tables", "toc"])
soup = BeautifulSoup(html, "html.parser")
title = soup.h1.get_text()
soup.h1.decompose()
for anchor in soup.find_all("a", href=True):
    anchor["href"] = re.sub(r"\.md(?=#|$)", ".html", anchor["href"])
    anchor["href"] = anchor["href"].replace("../CITATIONS.html", "../citations.html")
for table in soup.find_all("table"):
    wrapper = soup.new_tag("div", attrs={"class": "result-table"})
    table.wrap(wrapper)
toc = "".join(
    f'<a href="#{escape(heading["id"])}">{escape(heading.get_text())}</a>'
    for heading in soup.find_all("h2")
)
description = (
    "End-to-end workflow, GWAS QC and rare variants, functional follow-up, "
    "AI selection of 3–5 findings, an 8,000-word manuscript budget and implementation gaps."
)
page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="{escape(description)}"><title>{escape(title)} · OmicsMatic</title>
<link rel="stylesheet" href="../assets/styles.css"><script src="../assets/app.js" defer></script>
<style>.pipeline-spec .result-table table {{min-width: 580px; table-layout: fixed;}} .pipeline-spec .result-table th, .pipeline-spec .result-table td {{white-space: normal; overflow-wrap: anywhere;}}</style></head>
<body><site-header root="../"></site-header><main id="main">
<section class="page-hero"><div class="container"><div class="breadcrumbs"><a href="../index.html">Home</a><span>/</span><a href="index.html">Vignettes</a><span>/</span><span>{escape(title)}</span></div>
<span class="eyebrow">Scientific workflow specification · {source_date.group(1)}</span><h1>{escape(title)}</h1><p>{escape(description)}</p></div></section>
<div class="container page-layout"><article class="prose pipeline-spec">{soup}</article>
<aside class="toc"><strong>On this page</strong>{toc}</aside></div>
</main><site-footer root="../"></site-footer></body></html>
'''
target = root / "site" / "vignettes" / f"{slug}.html"
sys.dont_write_bytecode = True
citations_spec = importlib.util.spec_from_file_location("pipeline_citations", root / "docs" / "render-citations.py")
citations_module = importlib.util.module_from_spec(citations_spec)
citations_spec.loader.exec_module(citations_module)
references, page_citations, aliases = citations_module.load_registry()
page = citations_module.with_marked_block(page, citations_module.citation_block_html(slug, page_citations[slug],
    {reference["id"]: reference for reference in references}), "</main>")
page = page.replace('href="#scientific-citations"', f'href="#scientific-citations-{slug}"')
target.write_text(page, encoding="utf-8", newline="\n")
local_links = [
    link for link in re.findall(r"\]\(([^)]+)\)", source)
    if not link.startswith(("https:", "http:"))
]
for link in local_links:
    assert (source_path.parent / link.split("#")[0]).is_file(), link
fence = chr(96) * 3
pseudocode = source.split(fence + "text\n", 1)[1].split(fence, 1)[0]
assert pseudocode.strip() == soup.select_one("pre code").get_text().strip()
section_ids = [element["id"] for element in soup.select("[id]")]
assert len(section_ids) == len(set(section_ids))
citations = json.loads((root / "docs" / "citations.json").read_text(encoding="utf-8"))
for citation in citations["pages"][slug]:
    assert f"citations.html#{citation}" in page, citation
assert len(BeautifulSoup(page, "html.parser").select("h1")) == 1
print(f"Rendered {target.relative_to(root)} ({len(page):,} characters)")
print(f"Checked {len(local_links)} Markdown local links, unique section IDs and exact pseudocode rendering")

status_source = root / "docs" / "analysis-pipeline-implementation.md"
if status_source.is_file():
    status_soup = BeautifulSoup(markdown.markdown(status_source.read_text(encoding="utf-8"), extensions=["tables", "toc"]), "html.parser")
    status_title = status_soup.h1.get_text()
    status_soup.h1.decompose()
    for anchor in status_soup.find_all("a", href=True):
        anchor["href"] = re.sub(r"\.md(?=#|$)", ".html", anchor["href"])
    for table in status_soup.find_all("table"):
        table.wrap(status_soup.new_tag("div", attrs={"class": "result-table"}))
    status_page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{escape(status_title)} · OmicsMatic</title>
<link rel="stylesheet" href="assets/styles.css"><script src="assets/app.js" defer></script><style>.pipeline-spec .result-table th,.pipeline-spec .result-table td {{white-space:normal;overflow-wrap:anywhere;}}</style></head>
<body><site-header root=""></site-header><main id="main"><section class="page-hero"><div class="container"><h1>{escape(status_title)}</h1><p>Requirement-by-requirement implementation and verification status.</p></div></section>
<div class="container"><article class="prose pipeline-spec">{status_soup}</article></div></main><site-footer root=""></site-footer></body></html>'''
    (root / "site" / "analysis-pipeline-implementation.html").write_text(status_page, encoding="utf-8", newline="\n")
