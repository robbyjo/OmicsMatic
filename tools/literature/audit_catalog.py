"""Verify unique eligible sources and every published evidence pointer offline."""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/literature/omics-2023-2026"
CACHE = ROOT / "build/literature-review"


def main():
    papers = json.loads((DATA / "papers.json").read_text(encoding="utf-8"))
    assert len(papers) == 250, "The catalog must contain exactly 250 papers"
    assert len({p["doi"].lower() for p in papers}) == 250, "Duplicate DOI"
    assert len({p["pmcid"] for p in papers}) == 250, "Duplicate full text"
    allowed = {"Nature", "Nature Genetics", "Nature genetics", "The American Journal of Human Genetics", "American Journal of Human Genetics", "American journal of human genetics"}
    registry = {t["id"]: t for t in json.loads((ROOT / "tools/literature/tool-registry.json").read_text())}
    observations = 0
    for i, paper in enumerate(papers, 1):
        assert paper["paper_id"] == f"P{i:03d}"
        assert "2023-10-23" <= paper["first_publication_date"] <= "2026-10-23"
        assert paper["journal"] in allowed
        assert paper["article_type"] in {"research-article", "methods-article"}
        assert paper["publisher_url"] == "https://doi.org/" + paper["doi"]
        assert paper["fulltext_url"] == "https://pmc.ncbi.nlm.nih.gov/articles/" + paper["pmcid"] + "/"
        assert paper["tools"], "A selected paper has no evidence observations"
        raw = gzip.decompress((CACHE / "xml" / (paper["pmcid"] + ".xml.gz")).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper["xml_sha256"]
        root = ET.fromstring(raw)
        fulltext_paragraphs = {p["paragraph_index"]: p for p in paragraphs(root)}
        doi = next(el.text for el in root.findall("front/article-meta/article-id") if el.get("pub-id-type") == "doi")
        assert doi.lower() == paper["doi"].lower(), "Full-text DOI mismatch"
        cached = json.loads((CACHE / "extracted" / (paper["pmcid"] + ".json")).read_text(encoding="utf-8"))
        by_tool = {t["tool_id"]: t for t in cached["tools"]}
        assert len({t["tool_id"] for t in paper["tools"]}) == len(paper["tools"])
        for tool in paper["tools"]:
            source = by_tool[tool["tool_id"]]
            assert tool["tool_id"] in registry
            assert "evidence_text" not in tool and "evidence_sentence" not in tool, "Long source excerpts must stay in the research cache"
            assert hashlib.sha256(source["evidence_text"].encode()).hexdigest() == tool["evidence_paragraph_sha256"]
            primary = fulltext_paragraphs[tool["evidence_paragraph_index"]]
            assert primary["text"] == source["evidence_text"], "Cached evidence differs from the indexed primary paragraph"
            assert re.search(registry[tool["tool_id"]]["pattern"], primary["match_text"], re.I)
            assert tool["evidence_paragraph_index"] == source["evidence_paragraph_index"]
            assert tool["use_summary"]
            observations += 1
    for tool in registry.values():
        if tool["existing_source"]:
            assert (ROOT / tool["existing_source"]).is_file(), "Invalid native coverage pointer"
    review_file = ROOT / "tools/literature/reviewed-uses.json"
    reviews = json.loads(review_file.read_text(encoding="utf-8"))["reviews"]
    by_pmcid = {p["pmcid"]: p for p in papers}
    assert len({(r["pmcid"], r["tool_id"]) for r in reviews}) == len(reviews), "Duplicate reviewed use"
    for review in reviews:
        paper = by_pmcid[review["pmcid"]]
        tool = next((t for t in paper["tools"] if t["tool_id"] == review["tool_id"]), None)
        assert tool is not None, "Reviewed tool is absent from published catalog: " + paper["paper_id"] + "/" + review["tool_id"]
        assert paper["doi"].lower() == review["doi"].lower()
        assert tool["evidence_paragraph_sha256"] == review["evidence_paragraph_sha256"]
        assert tool["use_summary"] == review["use_summary"]
        assert tool["implementation_scope_note"] == review["implementation_scope_note"]
        if "usage_context" in review:
            assert tool["usage_context"] == review["usage_context"]
        if "version_reported_near_name" in review:
            assert tool["version_reported_near_name"] == review["version_reported_near_name"]
        if "supporting_evidence" in review:
            assert tool["supporting_evidence"] == review["supporting_evidence"]
            raw = gzip.decompress((CACHE / "xml" / (paper["pmcid"] + ".xml.gz")).read_bytes())
            ps = paragraphs(ET.fromstring(raw))
            validate_supporting_evidence(ps, [review], registry)
        assert tool["review_status"] == "usage-summary-reviewed; paper-wide-completeness-review-open"
    result = {"status": "passed", "source_records": 250, "unique_dois": 250,
              "source_xml_hashes_verified": 250, "fulltext_dois_verified": 250,
              "tool_observation_pointers_verified": observations,
              "individually_reviewed_usage_summaries_verified": len(reviews),
              "supporting_paragraph_pointers_verified": sum(len(r.get("supporting_evidence", [])) for r in reviews),
              "linked_method_detail_pointers_verified": sum(p.get("evidence_role") == "method-details" for r in reviews for p in r.get("supporting_evidence", [])),
              "extractor_sha256": hashlib.sha256((ROOT / "tools/literature/collect_omics_review.py").read_bytes()).hexdigest(),
              "registry_sha256": hashlib.sha256((ROOT / "tools/literature/tool-registry.json").read_bytes()).hexdigest(),
              "auditor_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "usage_reviews_sha256": hashlib.sha256(review_file.read_bytes()).hexdigest(),
              "journals": dict(Counter(p["journal_abbreviation"] for p in papers)),
              "scope": "Identity, journal/date/article-type eligibility, primary-source hashes and evidence pointers verified. This does not establish exhaustive semantic software extraction or upstream numerical parity."}
    (DATA / "catalog-validation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
