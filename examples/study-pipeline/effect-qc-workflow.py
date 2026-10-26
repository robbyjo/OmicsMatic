"""Generate or verify the isolated effect-QC sensitivity example (standard library).

python effect-qc-workflow.py generate NEW_PROJECT
python effect-qc-workflow.py verify PROJECT --jar EXECUTABLE_JAR [--run-id ID]
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import subprocess


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def generate(project):
    project.mkdir(parents=True, exist_ok=False)
    for cohort in ("a", "b", "c", "v"):
        rows = ["feature_id\tbeta\tse\tp_value"]
        rows.extend(f"g{i}\t{(i - 15) * 0.01:.8g}\t.1\t.5" for i in range(30))
        extreme = "600000\t200000" if cohort == "a" else ".1\t.1"
        rows.append(f"extreme\t{extreme}\t.0027")
        if cohort != "v":
            rows.append("discoveryOnly\t.4\t.1\t.0001")
        rows.append("invalid\t.4\t-1\t.001")
        (project / f"{cohort}.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    study = {
        "schema_version": 1,
        "project": {"id": "synthetic-effect-qc", "title": "Synthetic effect-QC sensitivity", "description": "Four-cohort synthetic boundary and lineage checks, without biological conclusions."},
        "resources": {"network": "offline", "downloads": "deny"},
        "datasets": [{"id": cohort, "cohort": f"cohort-{cohort}", "type": "generic", "stage": "association_results", "files": {"results": f"{cohort}.tsv"}} for cohort in ("a", "b", "c", "v")],
        "hypotheses": [{"id": "H", "description": "Compare filtered primary and unfiltered sensitivity results.",
            "inputs": {"discovery": ["a", "b", "c"], "replication": ["v"]},
            "analysis": {"mode": "manual", "goals": ["discovery_replication", "meta_analysis"],
                "harmonization": {"invalid_rows": "exclude", "common_universe": "intersection"}}}],
    }
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    hashes = {path.name: digest(path) for path in project.iterdir() if path.is_file()}
    (project / "fixture-input-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    print(project.resolve())


def verify(project, jar, run_id):
    project = project.resolve()
    subprocess.run(["java", "-jar", str(jar.resolve()), "study", "verify", "--project", str(project)], check=True)
    hashes = json.loads((project / "fixture-input-sha256.json").read_text())
    for relative, expected in hashes.items():
        assert digest(project / relative) == expected, relative
    runs = [project / "runs" / run_id] if run_id else list((project / "runs").iterdir())
    assert len(runs) == 1, "Specify --run-id after multiple attempts."
    operations = runs[0] / "operations"

    def operation(kind):
        paths = list(operations.glob(f"*-{kind}"))
        assert len(paths) == 1
        return paths[0]

    sensitivity = operation("sensitivity_analysis") / "effect-qc-unfiltered"
    primary = table(operation("meta_analysis") / "meta-analysis.tsv")
    alternative = table(sensitivity / "meta/meta-analysis.tsv")
    assert len(primary) == 30 and len(alternative) == 31
    assert {row["feature_id"] for row in alternative} - {row["feature_id"] for row in primary} == {"extreme"}
    assert all(row["n_cohorts"] == "4" for row in primary + alternative)
    assert "invalid" not in {row["feature_id"] for row in alternative}
    for folder in (operation("discovery_meta_analysis"), sensitivity / "discovery"):
        assert "discoveryOnly" in {row["feature_id"] for row in table(folder / "discovery-results.tsv")}
    for row in table(sensitivity / "replication/replication.tsv"):
        assert row["discovery_dataset"] == "__pooled_discovery__" and row["replication_dataset"] == "v"
    for replication in operation("sensitivity_analysis").glob("leave_one_cohort_out/*/replication.tsv"):
        assert all(row["discovery_dataset"] == "__pooled_discovery__" for row in table(replication))
    comparisons = table(sensitivity / "feature-sensitivity.tsv")
    changed = next(row for row in comparisons if row["family"] == "all_cohort_meta" and row["feature_id"] == "extreme")
    assert changed["filtered_present"] == "false" and changed["unfiltered_present"] == "true"
    entries = json.loads((project / "index/evidence-summary.json").read_text())["entries"]
    entry = next(row for row in entries if row["run_id"] == runs[0].name and row["operation_type"] == "sensitivity_analysis")
    decision = entry["details"]["effect_qc_unfiltered"]
    assert entry["state"] == "complete" and decision["primary_unchanged"] is True
    assert {family["family"] for family in decision["families"]} == {"a", "b", "c", "v", "all_cohort_meta"}
    meta_family = next(family for family in decision["families"] if family["family"] == "all_cohort_meta")
    assert meta_family["filtered_testing_denominator"] == 30 and meta_family["unfiltered_testing_denominator"] == 31
    report = {"run_id": runs[0].name, "jar_sha256": digest(jar), "primary_features": 30, "unfiltered_features": 31,
        "raw_hashes_unchanged": True, "study_verify_passed": True, "discovery_coverage_independent_of_validation": True,
        "remaining_discovery_cohorts_pooled_in_omission_sensitivities": True, "full_pipeline_conformance": False}
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "verify"))
    parser.add_argument("project", type=Path)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--run-id")
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.project)
    elif args.jar is None:
        parser.error("verify requires --jar")
    else:
        verify(args.project, args.jar, args.run_id)
