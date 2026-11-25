"""Recheck CrossMap source roles against every XML in the frozen 250-paper cohort.

Standard library only. Full texts remain private. This command never downloads,
changes catalog metadata, installs a runtime or admits a scientific conversion.
"""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET

from collect_omics_review import matching_words, paragraphs

ROOT = Path(__file__).resolve().parents[2]
FOLDER = "docs/literature/crossmap-source-audit"
PATTERN = re.compile(r"(?<![a-z0-9_])cross[\s_-]?map(?:\.py)?(?![a-z0-9_])", re.I)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode()


def audit(root, cache):
    root, cache = Path(root), Path(cache)
    census = json.loads((root / FOLDER / "CENSUS.json").read_bytes())
    roles = json.loads((root / FOLDER / "ROLES.json").read_bytes())
    papers = json.loads((root / "docs/literature/omics-2023-2026/papers.json").read_bytes())
    expected = census["XML"]
    require(len(papers) == len(expected) == 250, "Changed frozen cohort population")
    require([p["paper_id"] for p in papers] == list(expected), "Changed cohort order/identity")
    hits, other = [], []
    for paper in papers:
        identity = expected[paper["paper_id"]]
        require(paper["pmcid"] == identity["pmcid"] and paper["xml_sha256"] == identity["sha256"],
                "Changed frozen XML identity")
        source = cache / (paper["pmcid"] + ".xml.gz")
        if not source.is_file():
            raise FileNotFoundError("Restore exact permitted primary XML: " + str(source))
        raw = gzip.decompress(source.read_bytes())
        require(pin(raw) == {k: identity[k] for k in ("bytes", "sha256")}, "Changed primary XML")
        xml = ET.fromstring(raw)
        for paragraph in paragraphs(xml):
            if PATTERN.search(paragraph["match_text"]):
                hits.append(dict(paper_id=paper["paper_id"], doi=paper["doi"], pmcid=paper["pmcid"],
                                 xml_sha256=paper["xml_sha256"], paragraph_index=paragraph["paragraph_index"],
                                 section=paragraph["section"],
                                 paragraph_sha256=pin(paragraph["text"].encode())["sha256"]))
        for location in ("front/article-meta/abstract", "back", "floats-group", "body//supplementary-material"):
            for element in xml.findall(location):
                if PATTERN.search(matching_words(element)):
                    other.append(dict(paper_id=paper["paper_id"], location=location))
    excluded = census["excluded_broad_alias"]
    rejected = [{k: v for k, v in row.items() if k != "reason"} for row in excluded]
    require(hits == census["broad_hits"] and other == census["other_structures"],
            "Broad alias or other-structure census changed; inspect without refreshing old hashes")
    admitted = [row for row in hits if row not in rejected]
    require(admitted == census["anchors"] and len(admitted) == 5, "Changed eligible anchor population")
    require([r["paper_id"] for r in roles["roles"]] == ["P003", "P072", "P108", "P242"],
            "Changed source-role population")
    raw_fields = ("tool_id", "tool", "analysis_role", "matched_text", "extraction_score", "usage_context",
                  "evidence_section", "evidence_paragraph_index", "evidence_paragraph_sha256",
                  "evidence_locator", "version_reported_near_name")
    for role in roles["roles"]:
        paper = next(p for p in papers if p["paper_id"] == role["paper_id"])
        uses = [u for u in paper["tools"] if u["tool_id"] == "crossmap"]
        require(len(uses) == 1, "Missing/duplicate CrossMap observation")
        require({k: uses[0].get(k) for k in raw_fields} == role["raw_source_fields"],
                "Raw CrossMap source/version fields changed")
        require(role["primary_anchors"] == [a for a in admitted if a["paper_id"] == role["paper_id"]],
                "Changed source-role anchors")
    return dict(status="PASS_FULL250_CROSSMAP_PRIMARY_SOURCE_ROLES", primary_XML_checked=250,
                matched_papers=4, method_paragraphs=5, generic_verb_exclusions=1,
                raw_near_name_versions_preserved=True, author_unreported_editions_inferred=False,
                original_paper_analyses_reexecuted=False, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--cache", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.root, args.cache), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(("SKIP3 DEPENDENT AUDIT SKIPPED: " if code == 3 else "FAIL2 NO SOURCE AUDIT ADMITTED: ")
              + str(error) + ". Restore the named exact permitted source; preserve old hashes and attempts.",
              file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
