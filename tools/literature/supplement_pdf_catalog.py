"""Admit source-reviewed supplementary PDF tools without synthetic JATS pointers.

Keeps all predecessor metadata in a byte-exact archive. The historical ASCAT
qualification is unchanged; this transition adds source evidence, not execution.
"""
import argparse
from collections import Counter
import copy
import hashlib
import html
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import parse_qs, urlsplit
import zipfile

from ascat_primary import audit as audit_primary
from catalog_fixed_cohort import verify_fixed_cohort
import supplementary_pdf_evidence as pdf_evidence

ROOT = Path(__file__).resolve().parents[2]
DATA = "docs/literature/omics-2023-2026"
FOLDER = "docs/literature/supplement-pdf-admission"
ADDENDUM = "docs/literature/ascat-source-audit-v2/SUPPLEMENTARY-TOOLS.json"
PRIOR_REGISTRY = "docs/literature/ascat-catalog-admission/REGISTRY.json"
GUIDE = "docs/literature/SUPPLEMENTARY_PDF_EVIDENCE.md"
META = [DATA + "/" + name for name in ("papers.json", "tool-inventory.json", "summary.json",
        "README.md", "WARNINGS.md", "catalog.md", "implementation-plan.md")] + [
        "tools/literature/reviewed-uses.json", "site/literature-review.html", "site/literature-warnings.html"]
REVIEWED = "usage-summary-reviewed; paper-wide-completeness-review-open"
IDS = {"construct-ascat-files", "preascat"}
NOTICE = ("250 papers, 453 named tool labels, 492 providers and 2,733 observations; "
          "1,821 reviewed / 912 pending uses. 171 scoped families remain: 34 native + 137 upstream. "
          "ConstructASCATFiles and PREASCAT are now canonical supplement-backed observations; "
          "neither receives implementation credit. 252 labels remain missing. The full goal remains active.")
ACTION = ("SKIP3: ConstructASCATFiles/PREASCAT runtime analyses are skipped pending applicable "
          "license or permission from their own analysis-code rights holders. Obtain that permission "
          "before preparing and validating a private runtime, or retain the skip. Neither was installed, "
          "executed or redistributed. A bundled third-party license does not cover the authors' own code. "
          "Paper versions are unreported; inspected repository commits are not asserted author versions. "
          "The PDF parser reports a cross-reference table correction; original PDF bytes are unchanged, "
          "and the diagnostic identities/counts are retained. Raw PDFs/page text remain private. "
          "Missing permitted PDF/XML/parser prerequisites skip source validation (exit 3); changed evidence "
          "fails (exit 2). Restore the exact source/parser or correct a new reviewed locator in NEW paths; "
          "do not refresh old seals. Cell Ranger remains excluded; MaxQuant runtime remains skipped/pending. "
          "Literature HTMLs remain withheld from Pages.")


def require(value, message):
    if not value:
        raise ValueError(message)


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode()


def read(path):
    return json.loads(Path(path).read_bytes())


def pin(path):
    return pdf_evidence.identity(Path(path).read_bytes())


def source_records(root, supplements):
    """Bind both actual page locators to the DOI/version in official PMC metadata."""
    addition = read(root / ADDENDUM)
    require({row["tool_id"] for row in addition["tools"]} == IDS, "Supplement candidate set differs")
    census = read(root / "docs/literature/ascat-source-audit-v2/CENSUS.json")
    source = next(item for item in census["supplements"] if item["paper_id"] == "P146")["supplements"][0]
    metadata_path = supplements / "P146-v1-METADATA.json"
    require(pin(metadata_path) == source["official_metadata"], "Official supplement metadata bytes differ")
    metadata = read(metadata_path)
    require((metadata["pmcid"], metadata["doi"], metadata["version"], metadata["is_retracted"], metadata["license_code"])
            == ("PMC10632143", "10.1038/s41588-023-01545-1", 1, False, "CC BY"), "Supplement DOI/version/license differs")
    url = source["http"]["final_url"]
    parsed = urlsplit(url)
    s3 = "s3://pmc-oa-opendata" + parsed.path + "?" + parsed.query
    require(parsed.hostname == "pmc-oa-opendata.s3.amazonaws.com" and s3 in metadata["media_urls"],
            "PDF URL absent from exact official article-version metadata")
    pdf = supplements / source["filename"]
    raw = pdf.read_bytes()
    require(pdf_evidence.identity(raw) == source["pdf"] and hashlib.md5(raw).hexdigest()
            == source["official_MD5"] == parse_qs(parsed.query)["md5"][0], "Original supplement identity/MD5 differs")
    records = []
    for row in addition["tools"]:
        require(row["paper_id"] == "P146" and row["supplement_PDF_page"] == 7 and row["version_reported"] is None
                and row["supplement"] == source["pdf"] and row["runtime_analysis_status"]
                == "SKIP3_DEPENDENT_PREPROCESSING_ANALYSIS_SKIPPED", "Reviewed supplement role differs")
        require(all(row[key] is False for key in ("installed", "executed", "redistributed", "implementation_available",
                "primary_registry_admitted", "paper_to_inspected_commit_equivalence")), "Historical runtime skip changed")
        locator = pdf_evidence.describe(pdf, url, 1, 7, row["name"])
        records.append(dict(paper_id="P146", pmcid=metadata["pmcid"], doi=metadata["doi"], tool_id=row["tool_id"],
                            use_summary=row["use_summary"], version_reported=None, evidence_pdf=locator))
    return dict(schema_version=2, records=records, source_metadata=source["official_metadata"],
                physical_page_numbering="one-based, including unnumbered front matter",
                raw_source_text_published=False, new_family_credit=0)


def definition(row):
    return dict(id=row["tool_id"], name=row["name"], pattern=r"(?<![A-Za-z0-9_])" + re.escape(row["name"]) + r"(?![A-Za-z0-9_])",
                role=row["use_summary"], current_coverage="missing", existing_source=None,
                coverage_note=ACTION, discovery_source_kind="supplementary-pdf", upstream_repository=row["upstream_repository"])


def transition(before, prior_registry, addition, evidence):
    require(set(before) == set(META), "Predecessor metadata set differs")
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 7)]
    require((len(papers), len(inventory), sum(len(p["tools"]) for p in papers), len(ledger["reviews"]), len(prior_registry))
            == (250, 451, 2731, 1819, 490), "Predecessor counts differ")
    require(not IDS.intersection(row["id"] for row in inventory + prior_registry), "Supplement tools already admitted")
    require({row["tool_id"] for row in evidence["records"]} == IDS, "PDF evidence population differs")
    paper = next(p for p in papers if p["paper_id"] == "P146")
    registry = copy.deepcopy(prior_registry)
    additions = []
    for row in addition["tools"]:
        ev = next(item for item in evidence["records"] if item["tool_id"] == row["tool_id"])
        require((ev["pmcid"], ev["doi"]) == (paper["pmcid"], paper["doi"]) and ev["evidence_pdf"]["named_tool"] == row["name"],
                "New evidence/parent article differs")
        use = dict(tool_id=row["tool_id"], tool=row["name"], analysis_role=row["use_summary"],
                   evidence_source_kind="supplementary-pdf", evidence_pdf=copy.deepcopy(ev["evidence_pdf"]),
                   evidence_source_url=ev["evidence_pdf"]["source_url"], evidence_section="Supplementary Notes 1 and 2; physical PDF page 7",
                   evidence_locator=ev["evidence_pdf"]["source_url"] + "#page=7", evidence_selection="individually-reviewed-supplementary-PDF-page",
                   usage_context="analysis-method-description", review_status=REVIEWED,
                   version_reported_near_name=None, use_summary=row["use_summary"], implementation_scope_note=ACTION,
                   runtime_analysis_status="SKIP3_DEPENDENT_PREPROCESSING_ANALYSIS_SKIPPED", implementation_available=False)
        paper["tools"].append(use)
        ledger["reviews"].append(dict(pmcid=paper["pmcid"], doi=paper["doi"], tool_id=row["tool_id"],
            evidence_source_kind="supplementary-pdf", evidence_pdf=copy.deepcopy(ev["evidence_pdf"]),
            use_summary=use["use_summary"], implementation_scope_note=ACTION, usage_context=use["usage_context"], version_reported_near_name=None))
        provider = definition(row)
        registry.append(provider)
        inventory.append(dict(**provider, paper_count=1, papers=["P146"], implementation_status="missing", implementation_source=None,
            implementation_scope=ACTION, validation_note="Source usage reviewed; no runtime or numerical qualification.",
            runtime_analysis_status=use["runtime_analysis_status"], implementation_available=False, family_credit=0,
            inspected_commit=row["inspected_commit"], paper_to_inspected_commit_equivalence=False))
        additions.append(use)
    summary.update(distinct_named_tools=453, paper_tool_observations=2733,
                   implementation_status_counts=dict(Counter(row["implementation_status"] for row in inventory)),
                   reviewed_tool_uses=1821, pending_tool_use_reviews=912, supplementary_pdf_observations=2,
                   additional_supplementary_tool_candidates=0, supplementary_evidence_schema_version=2,
                   supplementary_candidates_catalog=FOLDER + "/EVIDENCE.json", current_effective_registry=FOLDER + "/REGISTRY.json")
    after = dict(before)
    for name, value in ((META[0], papers), (META[1], inventory), (META[2], summary), (META[7], ledger)):
        after[name] = encoded(value)
    note = ("# Current supplementary PDF evidence checkpoint\n\n" + NOTICE + "\n\n**Warnings and required actions:** " + ACTION
            + "\n\nSee [PDF evidence guide](../SUPPLEMENTARY_PDF_EVIDENCE.md). Earlier status checkpoints below are historical.\n\n---\n\n").encode()
    for name in META[3:7]:
        after[name] = note + before[name]
    table = "\n".join("| " + " | ".join((u["tool"], u["use_summary"], "Supplementary PDF, physical page 7; " + u["evidence_source_url"])) + " |" for u in additions)
    section = after[META[5]].decode()
    start = section.index("## P146:")
    boundary = re.search(r"\r?\n\r?\nXML SHA-256:", section[start:])
    require(boundary is not None, "P146 Markdown source footer missing")
    end = start + boundary.start()
    section = section[:end] + "\n" + table + section[end:]
    after[META[5]] = section.encode()
    plan = after[META[6]].decode()
    first = plan.index("\n---\n")
    plan = plan[:first] + "\n| Supplement-backed tool | Papers | Status | Required action |\n| --- | ---: | --- | --- |\n" + "\n".join(
        "| " + u["tool"] + " | 1 | missing; runtime SKIP3 | Obtain applicable code-use permission before runtime preparation. |" for u in additions) + "\n" + plan[first:]
    after[META[6]] = plan.encode()
    notice = '<aside id="supplement-pdf-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #986619;overflow-wrap:anywhere"><h2>Supplementary PDF evidence and missing tools</h2><p>' + html.escape(NOTICE) + '</p><p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION) + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE + '">PDF locator schema, validation and recovery</a></p></aside>'
    for name in META[8:]:
        page = before[name].decode()
        require('id="supplement-pdf-catalog-notice"' not in page, "Report already transitioned")
        if name == META[8]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(match and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), "Predecessor report payload differs")
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace("<", "\\u003c")
            page = page[:match.start(2)] + payload + page[match.end(2):]
            begin = page.index("<summary>Evidence locator</summary>") + len("<summary>Evidence locator</summary>")
            end = page.index("<br>${esc(t.usage_context)}", begin)
            old = page[begin:end]
            require(old.startswith("${t.evidence_source_kind==='supplementary-docx'?") and "evidence_paragraph_index" in old,
                    "Predecessor locator renderer differs")
            page = page[:begin] + "${primaryEvidence(t)}" + page[end:]
            helper = '''function primaryEvidence(t){if(t.evidence_source_kind==='supplementary-pdf'){const p=t.evidence_pdf;return '<strong>Supplementary PDF</strong>; physical page '+esc(p.page_number)+' of '+esc(p.page_count)+' (one-based, including front matter)<br><a data-link="'+esc(p.source_url+'#page='+p.page_number)+'">Source PDF</a><br>'+esc(t.evidence_section)+'<br>PDF SHA-256: <code>'+esc(p.source_identity.sha256)+'</code><br>Page text SHA-256: <code>'+esc(p.page_text_sha256)+'</code><br>Parser: '+esc(p.text_extractor.library)+' '+esc(p.text_extractor.version)+'; '+esc(p.named_occurrences.length)+' literal name occurrences; '+esc(p.parser_diagnostics.reduce((n,d)=>n+d.count,0))+' retained parser diagnostics';}return `''' + old + '''`;}\n'''
            require(page.count("function renderContent(){") == 1, "Report render boundary differs")
            page = page.replace("function renderContent(){", helper + "function renderContent(){", 1)
        body = re.search(r"<body[^>]*>", page, re.I)
        marker = page.find("<site-header ", body.end())
        require(marker >= body.end(), "Report body boundary differs")
        after[name] = (page[:body.end()] + notice + '<details id="supplement-pdf-prior-catalog"><summary>Earlier catalog checkpoints (historical)</summary>'
                       + page[body.end():marker] + "</details>" + page[marker:]).encode()
    return after, registry


def metadata_check(root):
    folder = root / FOLDER
    baseline = read(folder / "BASELINE.json")
    require(pin(folder / "metadata-before.zip") == baseline["archive"], "Historical metadata archive changed")
    require(pin(root / ADDENDUM) == baseline["addendum"] and pin(root / PRIOR_REGISTRY) == baseline["prior_registry"], "Historical source/registry changed")
    with zipfile.ZipFile(folder / "metadata-before.zip") as stream:
        require(len(stream.namelist()) == len(META) and set(stream.namelist()) == set(META), "Historical archive population differs")
        before = {name: stream.read(name) for name in META}
    require({name: pdf_evidence.identity(raw) for name, raw in before.items()} == baseline["metadata"], "Historical metadata bytes differ")
    evidence = read(folder / "EVIDENCE.json")
    require(pin(folder / "EVIDENCE.json") == baseline["evidence"], "Reviewed PDF locators changed")
    expected, registry = transition(before, read(root / PRIOR_REGISTRY), read(root / ADDENDUM), evidence)
    require(all((root / name).read_bytes() == raw for name, raw in expected.items()), "Current canonical metadata differs from exact PDF transition")
    require(read(folder / "REGISTRY.json") == registry and len(registry) == 492, "Effective provider registry differs")
    old, current = json.loads(before[META[0]]), json.loads(expected[META[0]])
    verify_fixed_cohort(root, root / DATA, current)
    for left, right in zip(old, current, strict=True):
        require({k: v for k, v in left.items() if k != "tools"} == {k: v for k, v in right.items() if k != "tools"}, "Frozen paper identity changed")
        require([u for u in right["tools"] if u["tool_id"] not in IDS] == left["tools"], "Unrelated observation changed")
    uses = [u for p in current for u in p["tools"]]
    reviews = json.loads(expected[META[7]])["reviews"]
    require(len(reviews) == len({(r["pmcid"], r["tool_id"]) for r in reviews}) == 1821, "Wrong/duplicate review population")
    require([r for r in reviews if r["tool_id"] not in IDS] == json.loads(before[META[7]])["reviews"], "Historical reviews changed")
    require(all(not any(k.startswith("evidence_paragraph") for k in u) for u in uses if u["tool_id"] in IDS), "PDF evidence misrepresented as JATS paragraph")
    require([row for row in json.loads(expected[META[1]]) if row["id"] not in IDS] == json.loads(before[META[1]]), "Unrelated inventory changed")
    require(sum(u["review_status"] in (REVIEWED, "individually-reviewed-primary-source-roles; whole-paper-method-review-open") for u in uses) == 1821, "Review markers differ")
    from ascat_catalog import checksums, verify_sums
    prior = checksums((folder / "PREVIOUS-SHA256SUMS").read_bytes())
    require(pin(folder / "PREVIOUS-SHA256SUMS") == baseline["previous_checksums"], "Historical checksums changed")
    sums = checksums((root / DATA / "SHA256SUMS").read_bytes())
    require(set(sums) == set(prior) | {"supplement-pdf-validation.json"}, "Checksum population differs")
    allowed = {Path(n).name for n in META if n.startswith(DATA + "/")}
    require(all(sums[name] == digest or name in allowed for name, digest in prior.items()), "Unrelated checksum refreshed")
    verify_sums(root, sums)
    result = dict(papers=250, named_labels=453, provider_definitions=492, observations=2733, reviewed_uses=1821,
                  pending_uses=912, scoped_families=171, native=34, upstream=137, missing_labels=252,
                  new_supplement_pdf_observations=2, new_source_reviews=2, new_family_credit=0,
                  unchanged_previous_observations=2731, unchanged_previous_reviews=1819,
                  raw_source_text_published=False, whole_goal_complete=False)
    require(read(root / DATA / "supplement-pdf-validation.json") == result, "Current PDF metadata proof differs")
    return result


def source_check(root, cache, supplements):
    result = metadata_check(root)
    audit_primary(root, cache, supplements)  # Complete frozen 250 XMLs and both original supplement bytes.
    actual = source_records(root, supplements)
    require(actual == read(root / FOLDER / "EVIDENCE.json"), "Current original PDF locators differ")
    for row in actual["records"]:
        pdf_evidence.verify(row["evidence_pdf"], supplements / "P146-v1-SUPPLEMENT.pdf")
    return dict(status="PASS_FULL250_SOURCE_AND_SUPPLEMENT_PDF_CATALOG", **result,
                original_XMLs_verified=250, original_supplements_verified=2, new_PDF_locators_verified=2,
                runtime_analysis="SKIP3_PENDING_APPLICABLE_CODE_PERMISSION")


def apply(root, cache, supplements, out):
    from ascat_catalog import check as prior_check, checksums, verify_sums
    require(not (root / FOLDER).exists() and not out.exists() and out.parent.resolve() == out.parent,
            "NEW admission/private output paths required")
    prior_check(root)
    audit_primary(root, cache, supplements)
    evidence = source_records(root, supplements)
    addition = read(root / ADDENDUM)
    before = {name: (root / name).read_bytes() for name in META}
    after, registry = transition(before, read(root / PRIOR_REGISTRY), addition, evidence)
    previous = (root / DATA / "SHA256SUMS").read_bytes()
    prior = checksums(previous)
    verify_sums(root, prior)
    require(len(prior) == 895, "Predecessor checksum population differs")
    out.mkdir()
    folder = root / FOLDER
    folder.mkdir()
    with zipfile.ZipFile(folder / "metadata-before.zip", "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as stream:
        for name, raw in before.items():
            stream.writestr(name, raw)
    (folder / "EVIDENCE.json").write_bytes(encoded(evidence))
    (folder / "REGISTRY.json").write_bytes(encoded(registry))
    (folder / "PREVIOUS-SHA256SUMS").write_bytes(previous)
    baseline = dict(schema_version=2, predecessor_commit="0a9541be29271b89453cf429c39a2c5ccf0ebcbb",
        archive=pin(folder / "metadata-before.zip"), metadata={name: pdf_evidence.identity(raw) for name, raw in before.items()},
        addendum=pin(root / ADDENDUM), prior_registry=pin(root / PRIOR_REGISTRY), evidence=pin(folder / "EVIDENCE.json"),
        previous_checksums=pin(folder / "PREVIOUS-SHA256SUMS"), historical_qualification_unchanged=True)
    (folder / "BASELINE.json").write_bytes(encoded(baseline))
    (out / "PLAN.json").write_bytes(encoded(dict(before=baseline["metadata"], after={name: pdf_evidence.identity(raw) for name, raw in after.items()}, new_family_credit=0)))
    for index, (name, raw) in enumerate(after.items()):
        require((root / name).read_bytes() == before[name], "Concurrent metadata change: " + name)
        stage = out / ("stage-" + str(index))
        stage.write_bytes(raw)
        os.replace(stage, root / name)
    proof = dict(papers=250, named_labels=453, provider_definitions=492, observations=2733, reviewed_uses=1821,
                 pending_uses=912, scoped_families=171, native=34, upstream=137, missing_labels=252,
                 new_supplement_pdf_observations=2, new_source_reviews=2, new_family_credit=0,
                 unchanged_previous_observations=2731, unchanged_previous_reviews=1819,
                 raw_source_text_published=False, whole_goal_complete=False)
    (root / DATA / "supplement-pdf-validation.json").write_bytes(encoded(proof))
    sums = dict(prior)
    for name in [n for n in META if n.startswith(DATA + "/")] + [DATA + "/supplement-pdf-validation.json"]:
        sums[Path(name).name] = pin(root / name)["sha256"]
    (root / DATA / "SHA256SUMS").write_bytes("".join(digest + "  " + name + "\n" for name, digest in sorted(sums.items())).encode())
    result = source_check(root, cache, supplements)
    (folder / "STATUS.json").write_bytes(encoded(result))
    (out / "COMPLETED.json").write_bytes(encoded(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--xml-cache", type=Path)
    parser.add_argument("--supplements", type=Path)
    parser.add_argument("--metadata-only", action="store_true", help="Check derivation/checksums only; does not verify original source bytes")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    try:
        root = args.root.resolve()
        if args.metadata_only:
            require(not args.apply, "Metadata-only cannot apply source evidence")
            result = dict(status="PASS_METADATA_DERIVATION_ONLY_ORIGINAL_SOURCES_NOT_VERIFIED", **metadata_check(root))
        else:
            if args.xml_cache is None or args.supplements is None:
                raise pdf_evidence.PrerequisiteUnavailable("Supply --xml-cache and --supplements with exact permitted retained sources")
            if args.apply:
                require(args.out is not None and args.out.resolve().is_relative_to(root / "build"), "Explicit NEW task-private --out under build required")
                result = apply(root, args.xml_cache.resolve(), args.supplements.resolve(), args.out.resolve())
            else:
                result = source_check(root, args.xml_cache.resolve(), args.supplements.resolve())
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError, pdf_evidence.PrerequisiteUnavailable)) else 2
        print(("SKIP3 DEPENDENT SOURCE VALIDATION SKIPPED: " if code == 3 else "FAIL2 NO COMPLETE SOURCE VALIDATION: ")
              + str(error) + ". Required action: restore exact permitted sources and the recorded parser, or retain the skip; "
              "preserve failed attempts and correct reviewed evidence in NEW paths. Runtime analyses remain skipped pending applicable permission.", file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
