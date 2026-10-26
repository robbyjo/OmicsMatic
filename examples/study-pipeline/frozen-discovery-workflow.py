"""Offline frozen-discovery replication regression, with independent FDR checks.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR
All effects, cohorts and source declarations are synthetic software fixtures.
No external scientific validity, biological finding or real AI appraisal is claimed.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import yaml


CASES = ("selected-subset", "selected-all", "complete-by", "complete-bonferroni",
         "complete-none", "native-bh", "native-by", "opposite-direction", "unresolved-family")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def adjust(values, method):
    if method == "NONE":
        return dict(values)
    size = len(values)
    if method == "BONFERRONI":
        return {key: min(1, p * size) for key, p in values.items()}
    factor = sum(1 / i for i in range(1, size + 1)) if method == "BY" else 1
    result, previous = {}, 1
    for rank, (key, p) in reversed(list(enumerate(sorted(values.items(), key=lambda row: row[1]), 1))):
        previous = min(previous, p * size * factor / rank)
        result[key] = previous
    return result


def cli(jar, project, *arguments):
    command = ["java", "-jar", str(jar), "study", *map(str, arguments)]
    result = subprocess.run(command, text=True, capture_output=True)
    with (project / "commands.jsonl").open("a", encoding="utf-8") as stream:
        stream.write(json.dumps({"command": command, "returncode": result.returncode,
                                 "stdout": result.stdout, "stderr": result.stderr}) + "\n")
    if result.returncode != 0:
        raise RuntimeError(result.stderr + result.stdout)


def generate(project, case):
    project.mkdir()
    for dataset in ("a", "b"):
        (project / f"{dataset}.tsv").write_text(
            "feature_id\tbeta\tse\tp_value\ng1\t.1\t.1\t.31731050786291415\ng2\t.4\t.1\t.00006334248366623996\n", encoding="utf-8")
    opposite = case == "opposite-direction"
    (project / "v.tsv").write_text("feature_id\tbeta\tse\tp_value\n"
        + f"g1\t.3\t.1\t.001\ng2\t{'-.3' if opposite else '.3'}\t.1\t.002\n", encoding="utf-8")
    selected = case in ("selected-subset", "selected-all", "opposite-direction")
    method = {"complete-by": "BY", "complete-bonferroni": "BONFERRONI", "complete-none": "NONE"}.get(case, "BH")
    p = {"g1": .02, "g2": .8} if method in ("BY", "NONE") else {"g1": .001, "g2": .8}
    q = {"g1": .4, "g2": .02} if selected else adjust(p, method)
    if selected:
        p = {"g1": .000001, "g2": .000002}
    source = "feature_id\tbeta\tse\tp_value" + ("\tadjusted_p" if selected else "") + "\n"
    source += "".join(f"{key}\t.4\t.1\t{value}" + (f"\t{q[key]}" if selected else "") + "\n" for key, value in p.items())
    (project / "pool.tsv").write_text(source, encoding="utf-8")
    family = {"id": "external-source-family", "coverage": "selected_only" if selected or case == "unresolved-family" else "complete",
              "tested_count": 400000 if selected or case == "unresolved-family" else 2, "adjustment": method,
              "adjusted_p_from_full_family": selected}
    supplied = {"id": "frozen-pool", "kind": "discovery_meta_analysis", "files": {"results": "pool.tsv"},
                "scope": {"datasets": ["a", "b"], "cohorts": ["A", "B"], "phenotype": "synthetic"},
                "method": {"name": "Synthetic externally supplied discovery pool"}, "family": family,
                "limitations": ["Synthetic fixture; external scientific validity is not established."]}
    analysis = {"mode": "manual", "goals": ["discovery_replication"],
                "replication": {"adjustment_scope": "all_results" if case == "selected-all" else "discovery_subset"},
                "harmonization": {"effect_outliers": {"enabled": False}},
                "multiple_testing": {"method": "BY" if case == "native-by" else "BH", "alpha": .05}}
    if not case.startswith("native-"):
        analysis["supplied_results"] = [supplied]
    datasets = [{"id": name, "cohort": name.upper(), "type": "generic", "stage": "association_results",
                 "files": {"results": f"{name}.tsv"}} for name in ("a", "b", "v")]
    study = {"schema_version": 1, "project": {"id": case, "title": "Frozen discovery fixture", "description": "Synthetic regression."},
             "resources": {"network": "offline", "downloads": "deny", "directory": "fixture-resources"},
             "cohorts": [{"id": name} for name in ("A", "B", "V")], "datasets": datasets,
             "cohort_relationships": [{"between": ["A", "B", "V"], "type": "independent"}],
             "hypotheses": [{"id": "H", "description": "Frozen discovery validation.", "outcome": "synthetic",
                             "inputs": {"discovery": ["a", "b"], "replication": ["v"]}, "analysis": analysis}]}
    write(project / "study.json", study)
    write(project / "expected.json", {"source_q": q, "source_method": method, "source_family": family,
                                     "native": case.startswith("native-")})
    write(project / "original-input-hashes.json", {path.name: sha(path) for path in project.iterdir() if path.is_file()})


def records(project):
    state = yaml.safe_load((project / "state/project-status.yaml").read_text(encoding="utf-8"))
    return [(path.parent, yaml.safe_load(path.read_text(encoding="utf-8")))
            for path in (project / "runs" / state["run_id"] / "operations").glob("*/manifest.yaml")]


def verify(root, jar):
    software = json.loads((root / "software.json").read_text(encoding="utf-8"))
    assert software["jar_sha256"] == sha(jar) == sha(root / "software/jlinalg.jar")
    assert software["verifier_sha256"] == sha(Path(__file__)) == sha(root / "software/frozen-discovery-workflow.py")
    comparisons, rows_checked, artifacts = 0, 0, {}
    for case in CASES:
        project = root / case
        for name, digest in json.loads((project / "original-input-hashes.json").read_text(encoding="utf-8")).items():
            assert sha(project / name) == digest, (case, name)
        operations = records(project)
        assert not any(m["operation_type"] in ("association", "participant_qc", "meta_analysis") for _, m in operations)
        pools = [(p, m) for p, m in operations if m["operation_type"] == "discovery_meta_analysis"]
        assert len(pools) == 1
        pool, producer = pools[0]
        comparisons_found = [(p, m) for p, m in operations if m["operation_type"] == "discovery_replication"]
        assert len(comparisons_found) == 1
        folder, manifest = comparisons_found[0]
        for path, operation in operations:
            for name, digest in operation.get("output_sha256", {}).items():
                assert sha(path / name) == digest, (case, path, name)
                artifacts[str((path / name).relative_to(root))] = digest
        if case == "unresolved-family":
            assert producer["state"] == "waiting_for_input"
            assert manifest["state"] != "complete" and not (folder / "replication.tsv").exists()
            continue
        assert producer["state"] == manifest["state"] == "complete"
        expected = json.loads((project / "expected.json").read_text(encoding="utf-8"))
        native = expected["native"]
        if native:
            # Independent inverse-variance pool: two equal-SE discovery cohorts.
            p = {"g1": math.erfc(1), "g2": math.erfc(4)}
            q = adjust(p, "BY" if case == "native-by" else "BH")
        else:
            assert producer["details"]["analysis_executed_by_jlinalg"] is False
            assert producer.get("reused_from_cache") is True
            assert sha(pool / "supplied/results.tsv") == sha(project / "pool.tsv")
            q = expected["source_q"]
        selected = {key for key, value in q.items() if value < .05}
        validation_p = {"g1": .001, "g2": .002}
        family = validation_p if case == "selected-all" else {key: value for key, value in validation_p.items() if key in selected}
        validation_q = adjust(family, "BY" if case == "native-by" else "BH")
        observed = table(folder / "replication.tsv")
        assert {row["feature_id"] for row in observed} == {"g1", "g2"}
        for row in observed:
            key = row["feature_id"]
            assert math.isclose(float(row["discovery_adjusted_p"]), q[key], rel_tol=1e-10, abs_tol=1e-14), (case, row, q)
            actual_right = math.nan if row["replication_adjusted_p"] == "NA" else float(row["replication_adjusted_p"])
            target = validation_q.get(key, math.nan)
            assert math.isnan(actual_right) if math.isnan(target) else math.isclose(actual_right, target, rel_tol=1e-12)
            same_sign = not (case == "opposite-direction" and key == "g2")
            assert row["discovery_selected"] == str(key in selected).lower()
            assert row["same_sign"] == str(same_sign).lower()
            assert row["replicated"] == str(key in selected and validation_q[key] < .05 and same_sign).lower()
            comparisons += 5
            rows_checked += 1
        receipt = manifest["details"]["frozen_discovery_selection"]
        assert receipt["source_sha256"] == sha(pool / "discovery-results.tsv")
        assert receipt["official_primary"] is False
        assert receipt["testing_denominator"] == (2 if native else expected["source_family"]["tested_count"])
        assert receipt["adjusted_values_origin"] == ("computed_from_complete_observed_scoped_input" if native else "preserved_source_full_family")
        assert sha(folder / receipt["producer_manifest_snapshot"]) == receipt["producer_manifest_sha256"]
        if not native:
            assert sha(folder / receipt["source_metadata_snapshot"]) == receipt["source_metadata_sha256"]
            assert manifest.get("reused_from_cache") is True
        cli(jar, project, "verify", "--project", project)
    write(root / "sealed-artifacts.json", artifacts)
    report = {"passed": True, "cases": len(CASES), "replication_rows": rows_checked, "comparisons": comparisons,
              "sealed_artifacts": len(artifacts), "jar_sha256": sha(jar), "verifier_sha256": sha(Path(__file__)),
              "external_requests": 0, "association_refits": 0, "external_scientific_validity_reproduced": False,
              "full_pipeline_conformance": False}
    write(root / "verification.json", report)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    root, jar = args.directory.resolve(), args.jar.resolve()
    if args.action == "run":
        root.mkdir(parents=True, exist_ok=False)
        (root / "software").mkdir()
        shutil.copyfile(jar, root / "software/jlinalg.jar")
        shutil.copyfile(__file__, root / "software/frozen-discovery-workflow.py")
        write(root / "software.json", {"jar_sha256": sha(jar), "verifier_sha256": sha(Path(__file__))})
        for case in CASES:
            project = root / case
            generate(project, case)
            cli(jar, project, "auto", project / "study.json", "--offline", "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
            if not case.startswith("native-") and case != "unresolved-family":
                cli(jar, project, "auto", project / "study.json", "--offline", "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
    verify(root, jar)
