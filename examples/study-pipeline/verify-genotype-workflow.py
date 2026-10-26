"""Check the synthetic fixture's numerical lineage; run `study verify` as well.

Usage: python verify-genotype-workflow.py PROJECT --jar PATH [--run-id ID]
This checks only the declared synthetic fixture, not full pipeline conformance.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re


def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--run-id")
    parser.add_argument("--effect-qc", action="store_true")
    args = parser.parse_args()
    project = args.project.resolve()
    runs = [project / "runs" / args.run_id] if args.run_id else list((project / "runs").iterdir())
    assert len(runs) == 1, "Specify --run-id when multiple attempts exist."
    operations = runs[0] / "operations"
    hashes = json.loads((project / "fixture-input-sha256.json").read_text(encoding="utf-8"))
    for relative, expected in hashes.items():
        assert hashlib.sha256((project / relative).read_bytes()).hexdigest() == expected, relative
    manifests = list(operations.glob("*/manifest.yaml"))
    assert len(manifests) == 14
    for manifest in manifests:
        assert re.search(r"^state: complete$", manifest.read_text(encoding="utf-8"), re.MULTILINE), manifest

    def operation(kind):
        paths = list(operations.glob(f"*-{kind}"))
        assert len(paths) == 1, (kind, paths)
        return paths[0]

    selected = {}
    for folder in operations.glob("*-participant_qc"):
        phenotype = rows(folder / "analysis-phenotype.tsv")
        cohort = phenotype[0]["id"].split("-")[0]
        assert {row["id"] for row in phenotype} == {f"{cohort}-S{i}" for i in range(62)}
        exclusions = rows(folder / "sample-exclusions.tsv")
        assert {row["reason"] for row in exclusions} == {"not_in_matrix", "missing_outcome_or_covariate", "sample_call_rate"}
        call_rate = next(row for row in exclusions if row["reason"] == "sample_call_rate")
        assert call_rate["sample_id"] == f"{cohort}-S63" and float(call_rate["observed_call_rate"]) == 0.75
        selected[cohort] = len(phenotype)
    assert selected == {"a": 62, "b": 62, "v": 62}

    estimates = {}; snapshots = {}
    for folder in operations.glob("*-association"):
        tables = list((folder / "standard-results").glob("*.tsv"))
        assert len(tables) == 1
        estimates[tables[0].stem] = {row["feature_id"]: row for row in rows(tables[0])}
        snapshots[tables[0].stem] = {row["feature_id"]: row for row in rows(folder / "pre-effect-qc-results" / tables[0].name)}
        assert set(snapshots[tables[0].stem]) == {"v20", "v40", "vMissing"}
        expected = {key for key, row in snapshots[tables[0].stem].items() if not args.effect_qc or abs(float(row["beta"])) <= 1.0}
        assert set(estimates[tables[0].stem]) == expected

    def pooled_beta(feature, cohorts):
        values = [estimates[cohort][feature] for cohort in cohorts]
        weights = [1 / float(row["se"]) ** 2 for row in values]
        return sum(float(row["beta"]) * weight for row, weight in zip(values, weights)) / sum(weights)

    discovery = {row["feature_id"]: row for row in rows(operation("discovery_meta_analysis") / "discovery-results.tsv")}
    primary = rows(operation("meta_analysis") / "meta-analysis.tsv")
    assert {row["feature_id"] for row in primary} == set(discovery)
    assert set(discovery) == ({"v20", "vMissing"} if args.effect_qc else {"v20", "v40", "vMissing"})
    for row in primary:
        feature = row["feature_id"]
        assert row["n_cohorts"] == "3" and row["term"] == "pooled"
        assert math.isclose(float(row["beta"]), pooled_beta(feature, ("a", "b", "v")), rel_tol=1e-12, abs_tol=1e-12)
        assert math.isclose(float(discovery[feature]["beta"]), pooled_beta(feature, ("a", "b")), rel_tol=1e-12, abs_tol=1e-12)
    validation = rows(operation("discovery_replication") / "replication.tsv")
    assert len(validation) == len(discovery)
    for row in validation:
        assert row["discovery_dataset"] == "__pooled_discovery__" and row["replication_dataset"] == "v"
        assert float(row["discovery_beta"]) == float(discovery[row["feature_id"]]["beta"])
        assert float(row["replication_beta"]) == float(estimates["v"][row["feature_id"]]["beta"])
    if args.effect_qc:
        sensitivity = operation("sensitivity_analysis") / "effect-qc-unfiltered"
        assert {row["feature_id"] for row in rows(sensitivity / "meta/meta-analysis.tsv")} == {"v20", "v40", "vMissing"}
        change = next(row for row in rows(sensitivity / "feature-sensitivity.tsv") if row["family"] == "all_cohort_meta" and row["feature_id"] == "v40")
        assert change["filtered_present"] == "false" and change["unfiltered_present"] == "true"
    graphics = list(operations.rglob("*.png")) + list(operations.rglob("*.pdf"))
    assert any(path.suffix == ".png" for path in graphics) and any(path.suffix == ".pdf" for path in graphics)
    for path in graphics:
        with path.open("rb") as stream:
            assert stream.read(8).startswith(b"\x89PNG" if path.suffix == ".png" else b"%PDF"), path
    report = {
        "scope": "synthetic genotype QC, discovery pooling and all-cohort numerical lineage only",
        "run_id": runs[0].name, "completed_operations": len(manifests), "selected_samples": selected,
        "source_hashes_unchanged": True, "mac_19_excluded_and_20_retained": True,
        "discovery_only_inverse_variance_pool_verified": True, "all_cohort_pool_without_duplicate_discovery_verified": True,
        "matrix_effect_qc_sensitivity_exercised": args.effect_qc,
        "graphics_with_valid_file_signatures": len(graphics), "jar_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        "full_workflow_conformance": False, "external_services_tested": False,
    }
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
