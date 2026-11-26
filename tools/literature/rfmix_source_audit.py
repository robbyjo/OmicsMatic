"""Verify all frozen main-text RFMix contexts and an unmodified private v2 source.

Read-only source review. This does not prepare a runtime, run local ancestry,
review unprovided supplements, or award implementation/catalog-review credit.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort

ROOT = Path(__file__).resolve().parents[2]
FOLDER = "docs/literature/rfmix-source-audit"
PATTERN = re.compile(r"(?<![A-Za-z0-9])RFMix(?![A-Za-z])", re.I)
PAPERS = {"P041", "P142", "P184", "P229"}
MUTABLE = {"review_status", "use_summary", "implementation_scope_note", "evidence_selection", "supporting_evidence", "source_role_qualification"}


def require(value, message):
    if not value:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_bytes())


def identity(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def audit(root, cache, upstream_source):
    census = read(root / FOLDER / "CENSUS.json")
    roles = read(root / FOLDER / "ROLES.json")["roles"]
    upstream = read(root / FOLDER / "UPSTREAM.json")
    papers = read(root / "docs/literature/omics-2023-2026/papers.json")
    verify_fixed_cohort(root, root / "docs/literature/omics-2023-2026", papers)
    require(identity(Path(collector.__file__).read_bytes()) == census["collector"], "Original paragraph parser changed")
    require(census["pattern"] == PATTERN.pattern and census["frozen_interval"] == ["2023-10-23", "2026-10-23"], "Source census scope differs")
    require(len(papers) == len(census["XML"]) == 250 and [p["paper_id"] for p in papers] == list(census["XML"]), "Frozen source cohort differs")
    hits, other = [], []
    for paper in papers:
        expected = census["XML"][paper["paper_id"]]
        require((paper["pmcid"], paper["doi"], paper["xml_sha256"]) == (expected["pmcid"], expected["doi"], expected["sha256"]), "Paper source identity differs")
        require(re.fullmatch(r"PMC[0-9]+", paper["pmcid"]) and 0 < expected["bytes"] <= 33554432, "Invalid XML identity/capacity")
        with gzip.open(cache / (paper["pmcid"] + ".xml.gz"), "rb") as stream:
            raw = stream.read(expected["bytes"] + 1)
        require(identity(raw) == {k: expected[k] for k in ("bytes", "sha256")}, "Missing/changed original XML")
        tree = ET.fromstring(raw)
        require(any((el.text or "").strip() == paper["doi"] for el in tree.findall("front/article-meta/article-id")), "Original source DOI differs")
        for paragraph in collector.paragraphs(tree):
            if PATTERN.search(paragraph["match_text"]):
                hits.append(dict(paper_id=paper["paper_id"], pmcid=paper["pmcid"], doi=paper["doi"],
                    paragraph_index=paragraph["paragraph_index"], section=paragraph["section"],
                    paragraph_sha256=hashlib.sha256(paragraph["text"].encode()).hexdigest()))
        for location in ("front/article-meta/abstract", "back", "floats-group", "body//supplementary-material"):
            for element in tree.findall(location):
                if PATTERN.search(collector.matching_words(element)):
                    other.append(dict(paper_id=paper["paper_id"], location=location, sha256=hashlib.sha256(ET.tostring(element)).hexdigest()))
    require(hits == census["contexts"] and other == census["other_structures"] and len(hits) == 13 and not other
            and {h["paper_id"] for h in hits} == PAPERS, "Complete RFMix mention population differs")
    require(len(roles) == 4 and {r["paper_id"] for r in roles} == PAPERS, "Incomplete/duplicate source-role population")
    claimed = set()
    for role in roles:
        paper = next(p for p in papers if p["paper_id"] == role["paper_id"])
        observations = [u for u in paper["tools"] if u["tool_id"] == "rfmix"]
        require(len(observations) == 1 and (role["doi"], role["pmcid"]) == (paper["doi"], paper["pmcid"]), "Parent observation differs")
        require(all(observations[0].get(k) == v for k, v in role["raw_catalog_observation_preserved"].items() if k not in MUTABLE), "Raw source locator/version differs")
        require(role["implementation_available"] is False and role["catalog_review_applied"] is False and role["new_family_credit"] == 0,
                "Source review was represented as implementation/catalog admission")
        actual = [h for h in hits if h["paper_id"] == paper["paper_id"]]
        require(actual == role["primary_anchors"], "Role omits or changes a direct source context")
        claimed.update((h["paper_id"], h["paragraph_index"]) for h in actual)
    require(claimed == {(h["paper_id"], h["paragraph_index"]) for h in hits}, "Source context omitted")
    require({r["paper_id"]: r["literal_reported_version"] for r in roles}
            == dict(P041="1.5.4", P142=None, P184="2", P229="2"), "Author versions inferred or altered")
    require(upstream["commit"] == "09599c19c69f81571c7a7aab4cd2d847a9e0633a" and upstream["declared_version"] == "v2.03-r0"
            and upstream["source_modified"] is False and upstream["installed"] is False and upstream["executed"] is False,
            "Original source snapshot changed into a runtime qualification")
    upstream_source.stat()  # A missing/inaccessible exact source is SKIP3.
    require(upstream_source.is_dir() and not upstream_source.is_symlink(), "Original source root must be a real directory")
    actual_files = {}
    for path in upstream_source.rglob("*"):
        require(not path.is_symlink(), "Original source tree contains a symlink")
        if path.is_file():
            require(path.stat().st_size <= 16777216, "Original source file capacity exceeded")
            actual_files[path.relative_to(upstream_source).as_posix()] = identity(path.read_bytes())
    require(actual_files == upstream["files"] and len(actual_files) == 38, "Original v2 source population/bytes differ")
    return dict(status="PASS_FULL250_MAIN_TEXT_RFMIX_SOURCE_REVIEW_NO_RUNTIME_CREDIT", papers=250,
                main_text_contexts=13, reviewed_paper_roles=4, source_reviews_eligible=4, source_reviews_applied=0,
                original_v2_source_files=38, original_v2_commit=upstream["commit"], runtime_prepared=False,
                supplementary_method_details_review_complete=False, exact_author_recipes_qualified=False,
                new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--xml-cache", type=Path, required=True)
    parser.add_argument("--upstream-source", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = audit(args.root.resolve(), args.xml_cache.absolute(), args.upstream_source.absolute())
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(("SKIP3 DEPENDENT RFMIX SOURCE REVIEW SKIPPED: " if code == 3 else "FAIL2 NO COMPLETE RFMIX SOURCE REVIEW: ")
              + str(error) + ". Required action: restore the exact permitted original XMLs, source tree and parser context; "
              "retain failed attempts, and keep runtime analyses pending until an original private runtime and adapter are qualified. "
              "RFMix v1.5.4 and v2 are distinct; do not substitute versions or infer author settings.", file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
