"""Generate and verify a synthetic offline literature-selection workflow.

python literature-workflow.py generate NEW_DIRECTORY
python literature-workflow.py verify DIRECTORY --jar EXECUTABLE_JAR
Uses the standard library; no real public queries or biological claims.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def generate(project):
    project.mkdir(parents=True, exist_ok=False)
    results = ["feature_id\tbeta\tse\tp_value"]
    annotations = ["feature_id\tgene_id\tgene_symbol\tchromosome\tstart\tend\tgenome_build"]
    for i in range(225):
        beta = 1 + i / 25000 if i < 125 else 0
        p = math.erfc(abs(beta) / (.1 * math.sqrt(2)))
        results.append(f"f{i:03d}\t{beta:.14g}\t0.1\t{p:.14g}")
        annotations.append(f"f{i:03d}\tSyntheticGene{i}\tSYNTH{i}\t1\t{i * 10 + 100}\t{i * 10 + 104}\tGRCh38")
    (project / "results.tsv").write_text("\n".join(results) + "\n", encoding="utf-8")
    (project / "annotation.tsv").write_text("\n".join(annotations) + "\n", encoding="utf-8")
    study = {"schema_version": 1, "project": {"id": "synthetic-literature", "title": "Synthetic literature selection",
             "description": "Offline selection, full-family correction and provenance checks; no actual literature retrieved."},
             "resources": {"network": "offline", "downloads": "deny"},
             "datasets": [{"id": "a", "cohort": "synthetic-a", "type": "transcriptomics", "stage": "association_results", "species": "human",
                 "genome_build": "GRCh38", "annotation_release": "synthetic-v1", "coordinate_system": "1-based-closed",
                 "files": {"results": "results.tsv", "annotation": "annotation.tsv"}}],
             "hypotheses": [{"id": "H", "description": "Preserve 125 eligible features while reviewing at most 100.", "outcome": "synthetic phenotype",
                 "analysis": {"mode": "manual", "goals": ["literature_search"],
                     "harmonization": {"effect_outliers": {"enabled": False}}}}]}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    hashes = {path.name: sha(path) for path in project.iterdir() if path.is_file()}
    (project / "fixture-input-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    print(project.resolve())


def verify(project, jar):
    project = project.resolve(); jar = jar.resolve()
    subprocess.run(["java", "-jar", str(jar), "study", "verify", "--project", str(project)], check=True)
    for relative, expected in json.loads((project / "fixture-input-sha256.json").read_text()).items():
        assert sha(project / relative) == expected, relative
    entries = [entry for entry in json.loads((project / "index/evidence-summary.json").read_text())["entries"] if entry["current"]]
    assert all(entry["state"] in ("complete", "waiting_for_input") for entry in entries)
    literature = next(entry for entry in entries if entry["operation_type"] == "literature_search")
    assert literature["state"] == "waiting_for_input", "Offline retrieval must remain incomplete."
    folder = (project / literature["manifest"]).parent
    inventory = table(folder / "literature-selection.tsv")
    raw = sorted((float(row["p_value"]), row["feature_id"]) for row in table(project / "results.tsv"))
    adjusted = {}; minimum = 1.0
    for rank in range(len(raw), 0, -1):
        p, feature = raw[rank - 1]
        minimum = min(minimum, p * len(raw) / rank); adjusted[feature] = minimum
    expected = [feature for _, feature in raw if adjusted[feature] < .05]
    assert len(inventory) == len(expected) == 125
    assert [row["feature_id"] for row in inventory] == expected
    for row in inventory:
        assert math.isclose(float(row["FDR"]), adjusted[row["feature_id"]], rel_tol=1e-10, abs_tol=1e-290)
    selected = [row for row in inventory if row["selected_for_review"] == "true"]
    assert len(selected) == 100 and [row["feature_id"] for row in selected] == expected[:100]
    assert all(row["retrieval_status"] == "retrieval_incomplete" and row["appraisal_status"] == "scientific_appraisal_pending" for row in selected)
    assert all(row["retrieval_status"] == "outside_review_budget" for row in inventory[100:])
    details = literature["details"]
    assert details["feature_limit"] == 100 and details["literature_selection"]["max_queries"] == 1000
    assert details["queries"] and all(not query["request_attempted"] and query["status"] == "offline_policy" for query in details["queries"])
    software = project / "software"; software.mkdir(exist_ok=True)
    pinned = software / f"jlinalg-{sha(jar)}.jar"
    if not pinned.exists(): shutil.copyfile(jar, pinned)
    assert sha(pinned) == sha(jar)
    verifier = project / "workflow-verifier-source.py"
    if verifier.resolve() != Path(__file__).resolve(): shutil.copyfile(Path(__file__), verifier)
    report = {"run_id": literature["run_id"], "jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(project)),
              "verifier_sha256": sha(verifier), "source_hashes_unchanged": True, "eligible_mapped_features": 125,
              "selected_features": 100, "outside_review_budget": 25, "full_family_BH_reconstructed": True,
              "offline_gate_verified": True, "external_requests_sent": 0, "scientific_appraisal_complete": False,
              "full_pipeline_conformance": False}
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "verify")); parser.add_argument("project", type=Path)
    parser.add_argument("--jar", type=Path); args = parser.parse_args()
    if args.action == "generate": generate(args.project)
    elif args.jar is None: parser.error("verify requires --jar")
    else: verify(args.project, args.jar)
