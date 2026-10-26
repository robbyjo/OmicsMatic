"""Source-scoped literature coverage in final finding dossiers.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Requires literature-priority-workflow.py alongside this script, NumPy, SciPy
and PyYAML. All completed-query declarations are synthetic. No external search
or AI request occurs; biological source support is not tested.
"""
import argparse
from decimal import Decimal
import importlib.util
import json
from pathlib import Path
import shutil

helper = Path(__file__).with_name("literature-priority-workflow.py")
spec = importlib.util.spec_from_file_location("priority_fixture", helper)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
CASES = ("cap2", "prior-outside", "supplied-only", "pending")


def generate(root, case):
    project = fixture.generate(root, "completed-literature" if case == "supplied-only" else "small-cap", 12345)
    destination = root / case
    project.rename(destination)
    project = destination
    study = fixture.load(project / "study.json")
    study["assistance"] = {"enabled": False}
    analysis = study["hypotheses"][0]["analysis"]
    analysis["goals"].append("finding_inventory")
    analysis["bioinformatics"].update(literature_selection="impact" if case == "pending" else "p_value", literature_max_features=2)
    if case == "cap2":
        papers = fixture.load(project / "literature.json")
        papers["queries"] = [q for q in papers["queries"] if not q.get("feature_id") or q["feature_id"] in ("g001", "g002")]
        fixture.save(project / "literature.json", papers)
    analysis["supplied_results"][1]["provenance"]["results_sha256"] = fixture.sha(project / "literature.json")
    fixture.save(project / "study.json", study)
    fixture.save(project / "input-hashes.json", {p.name: fixture.sha(p) for p in project.iterdir() if p.is_file() and p.name != "input-hashes.json"})
    return project


def verify(root, jar):
    pins = fixture.load(root / "software.json")
    assert fixture.sha(jar) == pins["jar_sha256"] == fixture.sha(root / "software/jlinalg.jar")
    for name, digest in pins["scripts_sha256"].items():
        assert fixture.sha(root / "software" / name) == digest
    outputs = checks = 0
    receipts = []
    for case in CASES:
        project = root / case
        for name, digest in fixture.load(project / "input-hashes.json").items():
            assert fixture.sha(project / name) == digest
        status, operations = fixture.operations(project)
        assert status["state"] == ("waiting_for_input" if case == "pending" else "completed"), (case, status)
        for directory, manifest in operations:
            for name, digest in manifest.get("output_sha256", {}).items():
                assert fixture.sha(directory / name) == digest
                outputs += 1
        inventory, manifest = fixture.one(project, "finding_inventory")
        assert manifest["state"] == "complete"
        findings = [json.loads(line) for line in (inventory / "finding-inventory.jsonl").read_text(encoding="utf-8").splitlines()]
        assert len(findings) == 9
        source = {row["feature_id"]: row for row in fixture.rows(project / "meta.tsv")}
        for finding in findings:
            assert finding["candidate"] and finding["significant"]
            primary = [row for row in finding["observations"] if row.get("primary") and "p_value" in row]
            for field, aliases in [("p_value", ("p_value",)), ("adjusted_p", ("adjusted_p", "FDR"))]:
                assert any(Decimal(str(row[key])) == Decimal(source[finding["feature_id"]][field]) for row in primary for key in aliases if key in row)
                checks += 1
            coverage = finding["literature_coverage"]
            assert coverage["systematic_review_established"] is False and coverage["source_support_established"] is False
            assert any(c["text"] == coverage["required_manuscript_caveat"] and c["source_ids"] == coverage["source_ids"] for c in finding["caveats"])
            assert coverage["source_ids"] and all(s in fixture.load(inventory / "finding-inventory.yaml")["sources"] for s in coverage["source_ids"])
            assert fixture.PRIVATE not in json.dumps(coverage)
        target = next(f for f in findings if f["feature_id"] == "g003")["literature_coverage"]
        if case == "cap2":
            assert target["query_evidence_state"] == "no_feature_search_receipts" and target["unique_queries"] == 0
        else:
            assert target["query_evidence_state"] == "bounded_queries_report_no_matches"
            assert target["unique_queries"] == target["reported_complete_queries"] == target["source_reported_queries"] == 3
        expected = [] if case == "supplied-only" else ["provisional_priority_pending" if case == "pending" else "outside_review_budget"]
        assert [a["status"] for a in target["native_allocations"]] == expected
        if case in ("cap2", "prior-outside"):
            reviewed = next(f for f in findings if f["feature_id"] == "g001")["literature_coverage"]
            assert reviewed["unique_queries"] == reviewed["reported_complete_queries"] == reviewed["duplicate_reports_collapsed"] == 3
            assert all(len(q["source_ids"]) == 2 for q in reviewed["queries"])
        fixture.cli(jar, "verify", "--project", project)
        receipts.append({"case": case, "state": status["state"], "target_query_state": target["query_evidence_state"], "native_allocation_statuses": expected})
    report = {"passed": True, "cases": len(CASES), "completed": 3, "intentional_holds": 1, "independent_numeric_checks": checks, "output_hashes_verified": outputs, **pins, "receipts": receipts, "source_statistics_unchanged": True, "external_requests": 0, "scientific_source_appraisal_tested": False, "full_pipeline_conformance": False}
    fixture.save(root / "verification.json", report)
    print(json.dumps(report, indent=2))


def run(root, jar):
    root.mkdir(parents=True, exist_ok=False)
    software = root / "software"
    software.mkdir()
    shutil.copyfile(jar, software / "jlinalg.jar")
    scripts = [Path(__file__), helper]
    for script in scripts:
        shutil.copyfile(script, software / script.name)
    fixture.save(root / "software.json", {"jar_sha256": fixture.sha(jar), "scripts_sha256": {s.name: fixture.sha(s) for s in scripts}})
    for case in CASES:
        project = generate(root, case)
        (root / (case + ".log")).write_text(fixture.cli(software / "jlinalg.jar", "auto", project / "study.json", "--actor-type", "external", "--actor-id", "synthetic-coverage-fixture"), encoding="utf-8")
    verify(root, software / "jlinalg.jar")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    (run if args.action == "run" else verify)(args.directory.resolve(), args.jar.resolve())
