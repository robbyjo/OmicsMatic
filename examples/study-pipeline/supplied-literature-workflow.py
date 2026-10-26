"""Supplied bibliography/manuscript and exact-query missing-work acceptance.

run NEW_DIRECTORY --jar JAR --references NATIVE_PUBLIC_REFERENCE_BUNDLE
    [--live-missing-query]
verify DIRECTORY --jar PINNED_JAR

Numerical results and external search declarations are synthetic. A real OAS1
paper supplies bibliographic metadata only, without supporting these fixtures.
The optional live case sends one public dummy query to Europe PMC; no study
files, private notes, participant data or LLM requests are transmitted.
"""
import argparse
from copy import deepcopy
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
from urllib.parse import parse_qs, urlsplit
import yaml

PHENOTYPE = "jlinalg public synthetic quantitative phenotype"
FIELD = "jlinalg public synthetic molecular field"
GENE = "JLINFIXTURE1"
DOI = "10.1126/science.abj3624"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def cli(jar, *args, allow_failure=False):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, args)], capture_output=True, text=True, encoding="utf-8")
    if not allow_failure:
        assert result.returncode == 0, result.stdout + result.stderr
    return result


def queries():
    base = f'(TITLE_ABS:"{FIELD}") AND TITLE_ABS:"{PHENOTYPE}"'
    gene = f'TITLE_ABS:"{GENE}"'
    phenotype = f'TITLE_ABS:"{PHENOTYPE}"'
    result = []
    for strategy, query in [("field_background", base), ("hypothesis_relevance", base + " AND (TITLE_ABS:mechanism OR TITLE_ABS:regulation OR TITLE_ABS:biomarker)"),
                            ("similar_studies", base + " AND (TITLE_ABS:association OR TITLE_ABS:cohort OR TITLE_ABS:replication OR TITLE_ABS:longitudinal)"),
                            ("field_conflicting_evidence", base + " AND (TITLE_ABS:null OR TITLE_ABS:negative OR TITLE_ABS:inconsistent OR TITLE_ABS:replication)"),
                            ("direct_gene_phenotype", gene + " AND " + phenotype),
                            ("molecular_mechanism", gene + " AND (TITLE_ABS:splicing OR TITLE_ABS:isoform OR TITLE_ABS:perturbation OR TITLE_ABS:knockout)"),
                            ("contradictions_and_replication", gene + " AND " + phenotype + " AND (TITLE_ABS:replication OR TITLE_ABS:null OR TITLE_ABS:negative OR TITLE_ABS:conflicting)")]:
        row = {"id": strategy, "provider": "Europe PMC", "query": query, "search_strategy": strategy, "phenotype": PHENOTYPE,
               "status": "complete", "coverage": "complete", "hit_count": 0, "references": [], "searched_at": "2026-10-01T00:00:00Z",
               "reuse_rationale": "Synthetic protocol declaration for this exercise, not a real completed external search or evidence of no scientific support."}
        if len(result) >= 4:
            row.update(feature_id="best", gene_symbol=GENE)
        result.append(row)
    return result


def current(project, kind):
    entries = [e for e in read(project / "index/evidence-summary.json")["entries"] if e["current"] and e["operation_type"] == kind]
    assert len(entries) == 1, (project, kind, entries)
    entry = entries[0]
    return entry, (project / entry["manifest"]).parent


def tree(path):
    return {p.relative_to(path).as_posix(): sha(p) for p in sorted(path.rglob("*")) if p.is_file()}


def generate(project, reference, mode):
    project.mkdir()
    p = math.erfc(1 / (.1 * math.sqrt(2)))
    (project / "meta.tsv").write_text(f"feature_id\tbeta\tse\tp_value\nbest\t1\t.1\t{p:.16g}\nnull\t0\t.1\t1\n", encoding="utf-8")
    (project / "annotation.tsv").write_text(f"feature_id\tgene_id\tgene_symbol\tchromosome\tstart\tend\tgenome_build\nbest\tSyntheticGene\t{GENE}\t1\t100\t103\tGRCh38\n", encoding="utf-8")
    supplied = {"schema_version": 1, "public_metadata": True, "references": [reference], "queries": queries(), "notes": "PRIVATE_BIBLIOGRAPHY_NOTES"}
    role = "primary" if mode in ("primary", "unacknowledged", "bad-link") else "contribution"
    if mode == "primary":
        supplied.pop("queries")
    if mode == "unacknowledged":
        supplied["public_metadata"] = False
    if mode == "bad-link":
        supplied["queries"][0].update(hit_count=1, references=["absent-reference"])
    if mode in ("partial-offline", "one-live-query"):
        supplied["queries"][0].update(status="partial", coverage="unknown")
    if mode == "wrong-scope":
        supplied["queries"][0]["phenotype"] = "different public synthetic phenotype"
    if mode == "wrong-provider":
        supplied["queries"][0]["provider"] = "Other public database"
    write(project / "literature.json", supplied)
    meta = {"id": "meta", "kind": "meta_analysis", "files": {"results": "meta.tsv", "feature_annotation": "annotation.tsv"},
            "scope": {"phenotype": PHENOTYPE, "species": "human", "genome_build": "GRCh38", "coordinate_system": "1-based-closed"}, "method": {"name": "External synthetic estimate", "parameters": {"private_context": "PRIVATE_METHOD_NOTES"}},
            "family": {"id": "synthetic-family", "coverage": "complete", "adjustment": "BH", "tested_count": 2}}
    literature = {"id": "papers", "kind": "literature_search", "role": role, "reported_status": "partial" if role == "contribution" else "complete",
                  "files": {"results": "literature.json"}, "scope": {"phenotype": PHENOTYPE}, "method": {"name": "External synthetic search declarations"},
                  "provenance": {"results_sha256": sha(project / "literature.json"), "private_note": "PRIVATE_SOURCE_PROVENANCE"}}
    if mode == "bad-hash":
        literature["provenance"]["results_sha256"] = "0" * 64
    imports = [meta, literature]
    if mode == "ambiguous":
        other = deepcopy(literature); other["id"] = "other-papers"; imports.append(other)
    study = {"schema_version": 1, "project": {"id": mode, "title": "Synthetic bibliography reuse", "description": "PRIVATE_STUDY_NOTES"},
             "resources": {"directory": "local-resources", "network": "online" if mode == "one-live-query" else "offline", "downloads": "automatic" if mode == "one-live-query" else "deny"},
             "assistance": {"enabled": False}, "datasets": [], "hypotheses": [{"id": "H", "description": "Synthetic result and search protocol; no biological conclusion.", "outcome": PHENOTYPE,
             "analysis": {"mode": "manual", "goals": ["meta_analysis", "literature_search"], "bioinformatics": {"literature_terms": [FIELD], "max_pages": 1}, "supplied_results": imports}}]}
    write(project / "study.json", study)
    write(project / "original-inputs.json", {name: sha(project / name) for name in ("meta.tsv", "annotation.tsv", "literature.json", "study.json")})


def run(root, jar, references, live):
    root.mkdir(parents=True, exist_ok=False)
    software = root / "software"; software.mkdir()
    pinned = software / f"jlinalg-{sha(jar)}.jar"; shutil.copyfile(jar, pinned); jar = pinned
    shutil.copyfile(Path(__file__), root / "workflow-verifier-source.py")
    records = read(references / "artifacts/reference-catalog.yaml")["references"]
    r = next(r for r in records if r.get("doi") == DOI)
    reference = {"id": "published", "doi": DOI, "title": r["title"], "authors": r["authorString"], "year": r["pubYear"], "journal": r["journal"], "notes": "PRIVATE_REFERENCE_NOTES"}
    for key in ("pmid", "pmcid"):
        if key in r: reference[key] = r[key]
    write(root / "reference-input.json", {"reference": reference, "source_manifest": str(references / "metadata/manifest.yaml"), "source_manifest_sha256": sha(references / "metadata/manifest.yaml")})
    modes = ["primary", "all-reused", "partial-offline", "wrong-scope", "wrong-provider", "bad-hash", "ambiguous", "unacknowledged", "bad-link"]
    if live: modes.append("one-live-query")
    execution = []
    for mode in modes:
        project = root / mode; generate(project, reference, mode)
        result = cli(jar, "auto", project / "study.json", *([] if mode == "one-live-query" else ["--offline"]), "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture", allow_failure=True)
        (root / f"{mode}-stdout.txt").write_text(result.stdout, encoding="utf-8"); (root / f"{mode}-stderr.txt").write_text(result.stderr, encoding="utf-8")
        execution.append({"case": mode, "exit_code": result.returncode})
        if mode not in ("ambiguous", "unacknowledged", "bad-link"):
            assert result.returncode == 0, result.stdout + result.stderr
    source = root / "primary"
    write(root / "primary-ledger-sha.json", {"sha256": sha(source / "ledger/events.jsonl")})
    request = {"schema_version": 1, "id": "citation-reuse", "title": "Synthetic manuscript citation reuse", "rationale": "Use supplied bibliographic metadata without repeating its search or implying biological support.",
               "sections": [{"id": "intro", "section": "introduction", "text": f"The supplied bibliography includes Wickenhagen and colleagues' OAS1 study [[cite:{DOI}]]. This synthetic exercise does not evaluate its biological findings.", "references": [DOI], "sources": ["H.literature_search"], "rationale": "Exercise actual supplied-reference citation only."},
                            {"id": "result", "section": "results", "results_role": "meta_analysis", "text": "The synthetic analysis tested {{H.meta_analysis#/tested_features}} features.", "sources": ["H.meta_analysis"], "rationale": "Bind the synthetic count to its supplied statistical family."}]}
    write(root / "manuscript.json", request); cli(jar, "manuscript", root / "manuscript.json", "--project", source, "--out", root / "manuscript", "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
    write(root / "execution.json", execution)
    verify(root, jar)


def verify(root, jar):
    execution = read(root / "execution.json"); actual_requests = 0
    for case in execution:
        mode = case["case"]; project = root / mode
        for name, digest in read(project / "original-inputs.json").items(): assert sha(project / name) == digest, (mode, name)
        cli(jar, "verify", "--project", project)
        entry, folder = current(project, "literature_search")
        if mode in ("unacknowledged", "bad-link", "ambiguous"):
            assert entry["state"] == "failed", (mode, entry["state"])
            assert not (folder / "queries").exists(), "Invalid imported declarations must fail before native requests"
            continue
        assert entry["state"] == ("complete" if mode in ("primary", "all-reused", "one-live-query") else "waiting_for_input"), (mode, entry["state"])
        catalog = read(folder / "reference-catalog.yaml")
        if mode != "bad-hash":
            ref = next(r for r in catalog["references"] if r.get("doi") == DOI)
            assert ref["metadata_origins"] == ["caller_supplied_bibliography"]
        if mode == "primary":
            assert "additional_search_or_input_required" == catalog["search_coverage"]["status"]
            assert not (folder / "queries").exists()
            assert "evidence" in entry["key_findings"]
        else:
            reuse = read(folder / "literature-reuse.yaml")
            assert reuse["reused_queries"] == (7 if mode == "all-reused" else 0 if mode == "bad-hash" else 6), (mode, reuse)
            supplied = read(project / "literature.json")
            originals = {q["id"]: q for q in supplied["queries"]}
            reused = [q for q in read(folder / "literature-search.yaml")["evidence"] if q.get("reuse_disposition") == "exact_scope_query_reused_without_native_request"]
            assert len(reused) == reuse["reused_queries"]
            for row in reused:
                original = originals[row["supplied_query_id"]]
                assert original["provider"] == "Europe PMC" and original["status"] == original["coverage"] == "complete"
                assert original["hit_count"] == len(original["references"])
                assert row["status"] == "supplied_no_matches" and not row["native_request_attempted"]
                for field in ("query", "search_strategy", "phenotype", "gene_symbol", "feature_id", "methods_role", "cohort_id", "cohort_name", "requested_reference"):
                    assert row.get(field, "") == original.get(field, ""), (mode, field)
            requests = [read(p) for p in sorted((folder / "queries").glob("*/request.yaml"))]
            if mode == "all-reused":
                assert not requests and catalog["search_coverage"]["status"] == "source_reported_coverage_complete_scientific_review_required"
                with (folder / "literature-selection.tsv").open(encoding="utf-8", newline="") as stream:
                    selections = list(csv.DictReader(stream, delimiter="\t"))
                assert selections[0]["retrieval_status"] == "source_reported_search_no_matches"
                assert selections[0]["source_reported_search_records"] == "3" and selections[0]["native_or_legacy_search_records"] == "0"
            if mode == "one-live-query":
                assert len(requests) == 1 and requests[0]["request_attempted"] and requests[0]["http_status"] == 200 and requests[0]["status"] == "retrieved"
                assert catalog["search_coverage"]["status"] == "bounded_coverage_complete_scientific_review_required"
                assert parse_qs(urlsplit(requests[0]["url"]).query)["query"] == [queries()[0]["query"]]
                assert read(folder / requests[0]["audit"] / "response.txt")["hitCount"] == 0
            else:
                assert all(not r["request_attempted"] for r in requests)
            actual_requests += sum(r["request_attempted"] for r in requests)
            assert all("PRIVATE_" not in r["url"] for r in requests)
        assert not list(project.glob("runs/**/llm/interaction-*"))
    cli(jar, "manuscript-verify", "--project", root / "manuscript")
    assert sha(root / "primary/ledger/events.jsonl") == read(root / "primary-ledger-sha.json")["sha256"]
    html = (root / "manuscript/artifacts/manuscript.html").read_text(encoding="utf-8")
    assert "10.1126/science.abj3624" in html and "A prenylated dsRNA sensor" in html
    payload = (root / "manuscript/artifacts/editorial-payload.json").read_text(encoding="utf-8")
    assert "PRIVATE_" not in payload and "caller_supplied_bibliography" in payload
    assert "The synthetic analysis tested 2 features." in html
    assert sha(root / "workflow-verifier-source.py") == sha(Path(__file__))
    pinned = root / "software" / f"jlinalg-{sha(jar)}.jar"; assert sha(pinned) == sha(jar)
    owned = read(root / "workflow-acceptance.json").get("files_sha256") if (root / "workflow-acceptance.json").exists() else None
    if owned:
        for name, digest in owned.items(): assert sha(root / name) == digest, name
    report = {"jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(root)), "verifier_sha256": sha(Path(__file__)), "cases": len(execution), "native_manuscript_bundles_verified": 1,
              "actual_public_requests": actual_requests, "actual_LLM_requests": 0, "supplied_reference_citation_verified": True, "exact_scope_missing_work_verified": True,
              "scientific_appraisal_complete": False, "external_search_declarations_are_synthetic": True, "new_OAS1_finding": False, "full_pipeline_conformance": False,
              "files_sha256": owned or {name: digest for name, digest in tree(root).items() if name != "workflow-acceptance.json"}}
    write(root / "workflow-acceptance.json", report)
    print(json.dumps({k: v for k, v in report.items() if k != "files_sha256"}, indent=2))


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__); p.add_argument("mode", choices=("run", "verify")); p.add_argument("directory", type=Path); p.add_argument("--jar", required=True, type=Path); p.add_argument("--references", type=Path); p.add_argument("--live-missing-query", action="store_true"); args = p.parse_args()
    if args.mode == "run":
        assert args.references, "--references is required for run"
        run(args.directory.resolve(), args.jar.resolve(), args.references.resolve(), args.live_missing_query)
    else: verify(args.directory.resolve(), args.jar.resolve())
