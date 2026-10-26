"""Reproducible, resumable primary-full-text intake for the 250-paper review.

Python 3.11+, standard library only. Full texts remain in ignored build/ cache.
The checked-in catalog contains source hashes and targeted evidence paragraphs.
Extraction produces review candidates, never an assertion of software parity.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import gzip
import hashlib
import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / "docs/literature/omics-2023-2026"
CACHE = ROOT / "build/literature-review"
BASE = "https://www.ebi.ac.uk/europepmc/webservices/rest/"
START, END = "2023-10-23", "2026-10-23"
JOURNALS = {"Nature": 75, "Nat Genet": 100, "Am J Hum Genet": 75}
TOPIC = ("(GWAS OR genome-wide OR genomics OR transcriptomic OR proteomic OR "
         "metabolomic OR epigenomic OR multiomics OR multi-omics OR single-cell OR "
        "RNA-seq OR eQTL OR pQTL OR sequencing)")
TOPIC_EVIDENCE = r"GWAS|genome.wide|genom|transcript|proteom|metabolom|epigenom|multi.om|single.cell|single.nucle|RNA.seq|QTL|exome"
CONTEXT = {
    "dose": r"enrich|GSEA|pathway|package|clusterProfiler",
    "viper": r"package|protein.activ|regulon|regulat|transcription.factor|t.test",
    "area": r"enrich|rank.based|signature|protein.activ|conserv|aREA.test",
    "qnorm": r"quantile.{0,20}normali[sz]|(?:package|library).{0,30}qnorm|qnorm.{0,30}(?:package|library)",
    "flash-merge": r"\breads?\b|FASTQ|overlap|paired.end|reconstruct",
    "fusion": r"TWAS|transcriptome|predicted.{0,20}expression|prediction.{0,20}model|GWAS|cis.herita",
    "eagle": r"phas|imput|genotyp|haplotyp",
    "king": r"kinship|relate|genotyp|ancestr|IBD",
    "star": r"align|RNA.seq|RNA.sequenc|reads|mapping|STARsolo|STAR.solo|gene.level.quantif",
    "macs": r"peak|ATAC|ChIP|chromatin",
    "dss": r"methyl|CpG|bisulfi",
    "salmon": r"quantif|transcript|pseudoalign|RNA.seq|TPM",
    "homer": r"motif|peak|enrich|annotat",
    "apex": r"QTL|association|genetic|meta.analysis",
    "vireo": r"demultip|assign|multiplex|genotyp|donor|SNP",
    "igv": r"visual|browser|view|track|inspect|manual|Integrative Genomics Viewer",
    "nrsa": r"nascent|gene.body|paus|run.on|PRO.seq|RNA.sequenc",
    "pumice": r"predict|cis.regulat|functional.annotat|genotyp|transcript",
    "re2c": r"meta.analysis|overlap|Lin.Sullivan|GWAS",
    "fame": r"epistasis|marginal.epist|SME|stochastic|trace|genetic.interaction",
    "sme": r"epistasis|marginal.epist|FAME|stochastic|random.vector|genetic.interaction|mask",
    "guppy": r"base.?call|FAST5|POD5|Albacore|Dorado|Guppy\s*(?:\(|>)?\s*v?\d",
    "arriba": r"fusion|RNA.seq|STAR|version|v[0-9]",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def fetch(url, target):
    if target.exists():
        return gzip.decompress(target.read_bytes())
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={
                "User-Agent": "JLinAlg-literature-review/1.0 (public scientific literature research)"})
            with urllib.request.urlopen(request, timeout=75) as response:
                data = response.read()
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(gzip.compress(data, mtime=0))
            return data
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def words(element):
    return re.sub(r"\s+", " ", "".join(element.itertext())).strip()


def matching_words(element):
    """Remove bibliographic labels for matching, preserving raw evidence for hashes.

    JATS often places <xref>17</xref> immediately after a software name. Joining
    itertext gives STAR17 and hides the name from a boundary-aware registry.
    Only bibliographic labels are omitted; software release numbers stay intact.
    """
    def visit(node):
        parts = [node.text or ""]
        for child in node:
            parts.append(" " if child.tag.rsplit("}", 1)[-1] == "xref" and child.get("ref-type") == "bibr" else visit(child))
            parts.append(child.tail or "")
        return "".join(parts)
    text = re.sub(r"\(\s*refs?\.\s*(?:,\s*)*\)", " ", visit(element), flags=re.I)
    return re.sub(r"\s+", " ", text).strip()


def paragraphs(root):
    """Only analysis/method sections; exclude references, captions and supplements."""
    body = root.find("body")
    if body is None:
        return []
    result = []

    def visit(section, headings):
        title = section.find("title")
        if title is not None:
            headings = headings + [words(title)]
        for child in section:
            if child.tag == "sec":
                visit(child, headings)
            elif child.tag == "p":
                text = words(child)
                if len(text) > 40:
                    result.append({"section": " / ".join(headings), "text": text, "match_text": matching_words(child),
                                   "paragraph_index": len(result) + 1})
    visit(body, [])
    return result


def validate_supporting_evidence(paragraphs_, reviews, registry=None):
    """Guard paragraph identity and, with a registry, explicit named or linked method support."""
    by_index = {p["paragraph_index"]: p for p in paragraphs_}
    def source_for(pointer):
        index = pointer["paragraph_index"]
        if index not in by_index:
            raise ValueError("Missing supporting evidence paragraph: " + str(index))
        source = by_index[index]
        if pointer["section"] != source["section"] or pointer["paragraph_sha256"] != hashlib.sha256(source["text"].encode("utf-8")).hexdigest():
            raise ValueError("Supporting evidence has a stale section or paragraph hash: " + str(index))
        return source
    for review in reviews:
        seen = set()
        for pointer in review.get("supporting_evidence", []):
            index = pointer["paragraph_index"]
            if index in seen or index not in by_index:
                raise ValueError("Duplicate or missing supporting evidence paragraph: " + str(index))
            seen.add(index)
            source = source_for(pointer)
            role = pointer.get("evidence_role")
            if role is not None and role != "method-details":
                raise ValueError("Unknown supporting evidence role")
            if role == "method-details":
                if registry is None:
                    raise ValueError("Linked method details require the named-tool registry")
                anchor = pointer.get("named_anchor", {})
                if anchor.get("paragraph_index") == index:
                    raise ValueError("Method-detail support cannot anchor to itself")
                named = source_for(anchor)
                pattern = registry[review["tool_id"]]["pattern"]
                if not re.search(pattern, named.get("match_text", named["text"]), re.I):
                    raise ValueError("Method-detail anchor does not name the reviewed tool")
                terms = pointer.get("shared_terms", [])
                note = pointer.get("relation_note", "")
                generic = {"data", "analysis", "method", "methods", "test", "tests", "model", "models"}
                if not terms or not isinstance(note, str) or len(note.strip()) < 20:
                    raise ValueError("Linked method details require shared method terms and a review explanation")
                for term in terms:
                    if not isinstance(term, str) or len(term) < 3 or term.casefold() in generic or not all(re.search(r"(?<!\w)"+re.escape(term)+r"(?!\w)", p.get("match_text", p["text"]), re.I) for p in [source, named]):
                        raise ValueError("Method-detail shared term is absent or too generic")
            elif registry is not None and not re.search(registry[review["tool_id"]]["pattern"], source.get("match_text", source["text"]), re.I):
                raise ValueError("Supporting paragraph does not name the reviewed tool; a checked method-detail link is required")


def tool_sentence_context(tool_id, sentence):
    """Distinguish a named implementation from wrappers, data reuse and method homonyms."""
    if tool_id == "flash-merge":
        # FLASH read reconstruction is unrelated to flash-frozen samples,
        # laboratory instruments, or the flash matrix-factorization package.
        if re.search(r"\b(?:matrix[- ]factor|factorization|factorisation|flash[- ]frozen|flash[- ]freez)", sentence, re.I):
            return False
        return bool(re.search(r"\breads?\b.{0,160}\b(?:reconstruction|merg(?:e[ds]?|ing))\b|\b(?:merg(?:e[ds]?|ing)|reconstruct(?:ed|ion)?)\b.{0,160}\breads?\b", sentence, re.I))
    if tool_id in {"salmon", "kallisto"} and re.search(r"\bquantification\s+algorithm,\s*(?:like|such as)\s+(?:Salmon|kallisto)\b", sentence, re.I):
        # A mathematical formulation accepts compatible quantifiers; it does
        # not establish which executable was used to generate the study data.
        return False
    if tool_id != "ucsc-liftover":
        return True
    named_ucsc = re.search(r"\bUCSC\s+liftOver\b|University of California.{0,30}liftOver", sentence, re.I)
    if not named_ucsc and re.search(r"\b(?:rtracklayer|MungeSumstats|CrossMap)\b|\bR\s+package\s+liftOver\b", sentence, re.I):
        return False
    if re.search(r"\brelease\s+liftover\s+to\b|\bability\s+to\s+liftover\b", sentence, re.I):
        return False
    if not named_ucsc and re.search(r"\btwo[- ](?:step|stage)\s+liftover\b", sentence, re.I):
        return False
    return True


def named_tool_match(tool, text):
    """Find an actual name span; PLINK formats and URLs are not executions."""
    for match in re.finditer(tool["pattern"], text, re.I):
        if tool["id"] in {"plink", "plink2"}:
            if any(url.start() <= match.start() < url.end()
                   for url in re.finditer(r"https?://[^\s<>]+", text, re.I)):
                continue
            if re.match(r"[\s)-]+(?:(?:binary|BED|text|data|genotype|file)[\s-]+)?(?:format|files?)\b", text[match.end():], re.I):
                continue
        return match
    return None


def tool_matches(paragraphs_, registry, preferred_evidence=None):
    found = {}
    preferred_evidence = preferred_evidence or {}
    for paragraph in paragraphs_:
        section = paragraph["section"].lower()
        # Conservative intake: Methods-only evidence has far fewer references-only
        # and comparator false positives than article-wide keyword matching.
        if not re.search(r"method|analysis|analyses|processing|quantification|pipeline|quality control|genotyp|statistical|bioinformatic|sequenc|alignment|fine.mapping|colocali|heritability|association|\bGWASs?\b", section, re.I):
            continue
        for tool in registry:
            searchable = paragraph.get("match_text", paragraph["text"])
            match = named_tool_match(tool, searchable)
            if not match:
                continue
            sentences = re.split(r"(?<=[.!?])\s+(?=[A-Z])", searchable)
            eligible = [s for s in sentences if named_tool_match(tool, s)
                and tool_sentence_context(tool["id"], s)
                and (tool["id"] not in CONTEXT or re.search(CONTEXT[tool["id"]], s + " " + paragraph["section"], re.I))
                and not re.search(r"recent publications have|previous studies have|which is capable|has been released|was released", s, re.I)
                and not re.search(r"\b(?:many|several|various)\b.{0,90}\btools\b.{0,50}\bavailable\b", s, re.I)]
            if not eligible:
                continue
            def sentence_score(sentence):
                score = 5 * bool(re.search(r"\b(used?|using|performed|applied|implemented|run|ran|processed|calculated|estimated|computed|analysed|analyzed|aligned|quantified|tested|identified|generated|filtered|annotated|mapped|fitted|fit)\b", sentence, re.I))
                return score - 10 * bool(re.search(r"did not use|not used|instead of|unlike|compared (?:with|to)|benchmark", sentence, re.I))
            sentence = max(eligible, key=sentence_score)
            match = named_tool_match(tool, sentence)
            score = 10 * bool(re.search(r"method|processing|pipeline|quantification", section)) + sentence_score(sentence)
            candidate = {"tool_id": tool["id"], "tool": tool["name"],
                         "analysis_role": tool["role"],
                         "evidence_section": paragraph["section"],
                         "evidence_paragraph_index": paragraph["paragraph_index"],
                         "evidence_text": paragraph["text"],
                         "evidence_sentence": sentence,
                         "matched_text": match.group(), "extraction_score": score,
                         "usage_context": "benchmark-or-comparison" if re.search(r"benchmark|compared (?:with|to)|comparison of|evaluated.{0,30}(?:tools|methods)", sentence, re.I) else "analysis-method-description",
                         "review_status": "candidate-methods-evidence"}
            nearby = sentence[match.end():match.end() + 75]
            version = re.match(r"[\s,;(:]*(?:version\s*|v\.?\s*)([0-9][0-9A-Za-z._-]*)", nearby, re.I)
            candidate["version_reported_near_name"] = version.group(1).rstrip(".-") if version else None
            if tool["id"] in {"plink", "plink2"}:
                # These name patterns consume explicit release tokens so generic
                # PLINK cannot borrow an adjacent PLINK2 occurrence. Read the
                # version from the matched name, after JATS bibr labels were
                # removed for matching; keep the raw paragraph/hash unchanged.
                release = re.search(r"^PLINK[\s(]*(?:(?:version|v\.?)\s*)?([12](?:\.[0-9]+)*(?:[ab][0-9][A-Za-z0-9._-]*)?)", match.group(), re.I)
                if release and candidate["version_reported_near_name"] is None:
                    candidate["version_reported_near_name"] = release.group(1).rstrip(".-")
            if tool["id"] == "bowtie1":
                release = re.search(r"\b1(?:\.\d+){1,2}\b", match.group())
                candidate["version_reported_near_name"] = release.group() if release else "1"
            preferred = preferred_evidence.get(tool["id"]) == hashlib.sha256(paragraph["text"].encode("utf-8")).hexdigest()
            candidate["evidence_selection"] = "individually-reviewed-paragraph" if preferred else "highest-scored-eligible-method-sentence"
            ranking = (preferred, score)
            if tool["id"] not in found or found[tool["id"]][0] < ranking:
                found[tool["id"]] = ranking, candidate
    matches = [v[1] for v in sorted(found.values(), key=lambda x: x[1]["tool_id"])]
    # PLINK release-family patterns exclude overlapping name spans. Do not drop
    # a genuine PLINK1 use merely because PLINK2 occurs in the same sentence.
    return matches


def publication_record(paper, reviewed_uses=None):
    """Publish source pointers and paraphrased uses; keep long excerpts in cache.

    Bibliographic records and tool names are retained. Full method paragraphs
    are research inputs, not relicensed as part of JLinAlg's software license.
    """
    paper = dict(paper)
    paper["tools"] = [dict(t) for t in paper["tools"]]
    if reviewed_uses is None:
        review_file = Path(__file__).with_name("reviewed-uses.json")
        reviewed_uses = json.loads(review_file.read_text(encoding="utf-8"))["reviews"] if review_file.exists() else []
    usage_reviews = {}
    for review in reviewed_uses:
        key = (review["pmcid"], review["tool_id"])
        if key in usage_reviews:
            raise ValueError("Duplicate reviewed tool use: " + str(key))
        usage_reviews[key] = review
    expected = {tool_id for pmcid, tool_id in usage_reviews if pmcid == paper["pmcid"]}
    missing = expected - {t["tool_id"] for t in paper["tools"]}
    if missing:
        raise ValueError("Reviewed tools are absent from current extraction for " + paper["pmcid"] + ": " + ", ".join(sorted(missing)) + "; inspect registry spelling/context guards before publishing")
    for tool in paper["tools"]:
        text = tool.pop("evidence_text")
        sentence = tool.pop("evidence_sentence")
        tool["evidence_paragraph_sha256"] = hashlib.sha256(text.encode("utf-8")).hexdigest()
        tool["use_summary"] = tool["analysis_role"] + "."
        # Context-specific labels are derived from the actual evidence sentence,
        # rather than attributing every feature of a package to each paper.
        if tool["tool_id"] in {"plink", "plink2", "gcta"}:
            uses = [(r"quality|filter|MAF|HWE|missing|hardy", "genotype quality control"),
                    (r"clump|prun|linkage disequilibrium|LD", "LD-based variant selection"),
                    (r"principal|PCA", "genetic principal components"),
                    (r"associat|GWAS|--glm|regression", "genetic association analysis"),
                    (r"polygenic|--score|PRS", "polygenic score calculation"),
                    (r"condition|COJO|joint", "conditional or joint association analysis")]
            purposes = [label for pattern, label in uses if re.search(pattern, sentence, re.I)]
            if purposes:
                tool["use_summary"] = "Used for " + "; ".join(purposes) + "."
        elif tool["tool_id"] == "ldsc":
            uses = [(r"heritab|h2|h\u00b2", "SNP heritability estimation"),
                    (r"correl|covari", "cross-trait genetic correlation"),
                    (r"stratif|partition|annotation|enrich", "annotation-stratified genetic enrichment"),
                    (r"intercept|confound", "association inflation assessment")]
            purposes = [label for pattern, label in uses if re.search(pattern, sentence, re.I)]
            if purposes:
                tool["use_summary"] = "Used for " + "; ".join(purposes) + "."
        elif tool["tool_id"] in {"umap", "tsne"}:
            tool["use_summary"] = "Produced an exploratory low-dimensional representation of the measured profiles."
        elif tool["tool_id"] in {"leiden", "louvain"}:
            tool["use_summary"] = "Grouped observations into communities using a neighborhood graph."
        elif tool["tool_id"] == "alphafold":
            tool["use_summary"] = "Used predicted protein structures in the study's structural analysis."
        elif tool["tool_id"] in {"seurat", "scanpy"}:
            operations = [(r"normaliz", "expression normalization"), (r"cluster", "cell clustering"),
                          (r"integrat|batch", "dataset integration"), (r"marker|different", "marker or differential-expression analysis"),
                          (r"annotat|cell.type", "cell annotation"), (r"principal|PCA|UMAP", "exploratory representation")]
            purposes = [label for pattern, label in operations if re.search(pattern, sentence, re.I)]
            if purposes:
                tool["use_summary"] = "Used for " + "; ".join(purposes) + "."
        tool["evidence_locator"] = paper["fulltext_url"] + " (section: " + tool["evidence_section"] + ")"
        tool["review_status"] = "source-evidence-extracted; semantic-completeness-review-open"
        review = usage_reviews.get((paper["pmcid"], tool["tool_id"]))
        if review is not None:
            if review["doi"].lower() != paper["doi"].lower() or review["evidence_paragraph_sha256"] != tool["evidence_paragraph_sha256"]:
                raise ValueError("Reviewed usage has stale/mismatched source evidence: " + paper["pmcid"] + "/" + tool["tool_id"])
            tool["use_summary"] = review["use_summary"]
            tool["implementation_scope_note"] = review["implementation_scope_note"]
            if "usage_context" in review:
                tool["usage_context"] = review["usage_context"]
            if "version_reported_near_name" in review:
                tool["version_reported_near_name"] = review["version_reported_near_name"]
            if "supporting_evidence" in review:
                tool["supporting_evidence"] = review["supporting_evidence"]
            tool["review_status"] = "usage-summary-reviewed; paper-wide-completeness-review-open"
    paper["license"] = ["https://creativecommons.org/licenses/" + suffix + "/4.0/"
        for suffix in ("by-nc-nd", "by-nc", "by")
        if any(("licenses/" + suffix + "/") in license_.lower() for license_ in paper.get("license", []))]
    return paper


def process(record, registry, retry_failures=False):
    pmcid = record["pmcid"]
    previous = CACHE / "extracted" / (pmcid + ".json")
    if previous.exists() and not retry_failures:
        prior = json.loads(previous.read_text(encoding="utf-8"))
        if prior.get("status") == "fetch-or-parse-failed":
            return prior
    url = BASE + pmcid + "/fullTextXML"
    try:
        xml = fetch(url, CACHE / "xml" / (pmcid + ".xml.gz"))
        root = ET.fromstring(xml)
        article_type = root.get("article-type", "unknown")
        if article_type not in {"research-article", "methods-article"}:
            return {"pmcid": pmcid, "status": "excluded-article-type", "article_type": article_type}
        journal = root.findtext("front/journal-meta/journal-title-group/journal-title", "")
        if journal.lower() not in {"nature", "nature genetics", "the american journal of human genetics", "american journal of human genetics"}:
            return {"pmcid": pmcid, "status": "excluded-journal", "journal": journal}
        abstract = root.find("front/article-meta/abstract")
        title = root.find("front/article-meta/title-group/article-title")
        title_text = words(title) if title is not None else record["title"]
        abstract_text = words(abstract) if abstract is not None else ""
        if not re.search(TOPIC_EVIDENCE, title_text + " " + abstract_text, re.I):
            return {"pmcid": pmcid, "status": "excluded-topic"}
        ps = paragraphs(root)
        review_file = Path(__file__).with_name("reviewed-uses.json")
        reviews = json.loads(review_file.read_text(encoding="utf-8"))["reviews"] if review_file.exists() else []
        validate_supporting_evidence(ps, [r for r in reviews if r["pmcid"] == pmcid], {t["id"]: t for t in registry})
        preferred = {r["tool_id"]: r["evidence_paragraph_sha256"] for r in reviews if r["pmcid"] == pmcid}
        tools = tool_matches(ps, registry, preferred)
        licenses = [words(el) for el in root.findall("front/article-meta/permissions/license")]
        code_links = []
        for element in root.iter("ext-link"):
            url_ = element.get("{http://www.w3.org/1999/xlink}href", "")
            if re.search(r"github\.com|gitlab\.com|bioconductor\.org|pypi\.org|cran\.r-project\.org", url_):
                if url_ not in code_links:
                    code_links.append(url_)
        return {"pmcid": pmcid, "pmid": record.get("pmid", record.get("id")),
                "doi": record.get("doi", ""), "title": title_text,
                "authors": record.get("authorString", ""),
                "journal": journal, "journal_abbreviation": record["review_journal"],
                "first_publication_date": record["firstPublicationDate"],
                "publication_year": record["firstPublicationDate"][:4],
                "publisher_url": "https://doi.org/" + record.get("doi", ""),
                "fulltext_url": "https://pmc.ncbi.nlm.nih.gov/articles/" + pmcid + "/",
                "xml_url": url, "xml_sha256": hashlib.sha256(xml).hexdigest(),
                "license": licenses, "article_type": article_type,
                "topic_basis": "Title/abstract omics, sequencing or GWAS match; research article",
                "topic_terms": sorted({m.group().lower() for m in re.finditer(TOPIC_EVIDENCE, title_text + " " + abstract_text, re.I)}),
                "software_links_for_completeness_review": code_links,
                "tools": tools, "status": "extracted" if tools else "no-registry-tool-evidence"}
    except Exception as error:
        return {"pmcid": pmcid, "status": "fetch-or-parse-failed", "error": str(error)}


def search():
    records = []
    queries = []
    for journal in JOURNALS:
        query = f'JOURNAL:"{journal}" AND FIRST_PDATE:[{START} TO {END}] AND OPEN_ACCESS:Y AND {TOPIC}'
        url = BASE + "search?" + urllib.parse.urlencode({"query": query, "format": "json", "pageSize": 1000, "resultType": "core"})
        stem = re.sub(r"\W+", "-", journal)
        data = json.loads(fetch(url, CACHE / (stem + "-search.json.gz")))
        results = list(data["resultList"]["result"])
        pages = [{"url": url, "response_sha256": hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()}]
        cursor = data.get("nextCursorMark")
        while len(results) < data["hitCount"] and cursor:
            page_url = url + "&" + urllib.parse.urlencode({"cursorMark": cursor})
            page = json.loads(fetch(page_url, CACHE / (stem + f"-search-page-{len(pages) + 1}.json.gz")))
            more = page["resultList"]["result"]
            if not more:
                break
            results.extend(more)
            pages.append({"url": page_url, "response_sha256": hashlib.sha256(json.dumps(page, sort_keys=True).encode()).hexdigest()})
            next_cursor = page.get("nextCursorMark")
            if cursor == next_cursor:
                break
            cursor = next_cursor
        queries.append({"journal": journal, "query": query, "url": url, "hit_count": data["hitCount"],
                        "retrieved_count": len(results), "pages": pages})
        for record in results:
            if record.get("pmcid") and START <= record.get("firstPublicationDate", "") <= END:
                record["review_journal"] = journal
                records.append(record)
        print(json.dumps({"journal": journal, "hits": data["hitCount"], "records": len(results)}), flush=True)
    write_json(DEST / "search-provenance.json", {"window_start": START, "window_end": END,
               "retrieved_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(), "queries": queries,
               "selection": "Open-access full-text, exact journals, primary research, topic match, tools in methods/analysis sections; year-stratified then date/DOI order",
               "bias": "Purposive accessible-full-text sample, not a systematic or representative review; indexing and full-text availability may lag publication."})
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--limit-per-journal", type=int, default=0)
    parser.add_argument("--retry-failures", action="store_true")
    args = parser.parse_args()
    DEST.mkdir(parents=True, exist_ok=True)
    registry = json.loads((Path(__file__).with_name("tool-registry.json")).read_text(encoding="utf-8"))
    records = search()
    # Stable year interleave prevents filling the sample entirely with the latest year.
    ordered = []
    for journal in JOURNALS:
        groups = {}
        for record in records:
            if record["review_journal"] == journal:
                groups.setdefault(record["firstPublicationDate"][:4], []).append(record)
        for group in groups.values():
            group.sort(key=lambda r: (r["firstPublicationDate"], r.get("doi", "")), reverse=True)
        mixed = []
        while any(groups.values()):
            for year in sorted(groups):
                if groups[year]:
                    mixed.append(groups[year].pop(0))
        limit = args.limit_per_journal or (JOURNALS[journal] + 40)
        ordered.extend(mixed[:limit])
    extracted = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(process, record, registry, args.retry_failures): record for record in ordered}
        for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
            result = future.result()
            extracted.append(result)
            write_json(CACHE / "extracted" / (result["pmcid"] + ".json"), result)
            if i % 20 == 0:
                print(json.dumps({"processed": i, "total": len(ordered), "states": dict(Counter(r["status"] for r in extracted))}), flush=True)
    selected, audit, remainder = [], [], []
    for journal, count in JOURNALS.items():
        eligible = {r["pmcid"]: r for r in extracted if r["status"] == "extracted" and r["journal_abbreviation"] == journal}
        candidates = [eligible[r["pmcid"]] for r in ordered if r["review_journal"] == journal and r["pmcid"] in eligible]
        picks = candidates[:count]
        remainder.extend(candidates[count:])
        selected.extend(picks)
        print(json.dumps({"journal": journal, "selected": len(picks), "target": count}), flush=True)
    # Journal quotas are sampling preferences, not eligibility requirements.
    # Fill a short journal quota with additional eligible papers in the window
    # before considering a date extension authorized by the user.
    selected.extend(remainder[:max(0, 250 - len(selected))])
    for result in sorted(extracted, key=lambda r: r["pmcid"]):
        audit.append({k: v for k, v in result.items() if k in {"pmcid", "status", "error", "article_type", "journal"}})
    seen_doi = set()
    for i, paper in enumerate(selected, 1):
        if not paper["doi"] or paper["doi"].lower() in seen_doi:
            raise ValueError("Missing or duplicate DOI: " + paper["pmcid"])
        seen_doi.add(paper["doi"].lower())
        paper["paper_id"] = f"P{i:03d}"
    write_json(DEST / "papers.json", [publication_record(p) for p in selected])
    write_json(DEST / "intake-audit.json", audit)
    print(json.dumps({"selected": len(selected), "tool_observations": sum(len(p["tools"]) for p in selected),
                      "journals": dict(Counter(p["journal_abbreviation"] for p in selected)),
                      "years": dict(Counter(p["publication_year"] for p in selected))}), flush=True)
    if len(selected) != 250:
        raise SystemExit("Intake incomplete: expand --limit-per-journal; never pad with unverified citations")


if __name__ == "__main__":
    main()
