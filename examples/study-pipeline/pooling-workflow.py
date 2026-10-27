"""Offline dependent-source pooling with independent scalar numerical verification.

run NEW_DIRECTORY --jar JAR [--missing-pair]
verify DIRECTORY --jar JAR
All associations, participant counts and covariance declarations are synthetic.
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


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def cli(jar, *arguments):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, arguments)], check=True, text=True, capture_output=True)
    print(result.stdout)
    return result.stdout


def generate(project, missing):
    project.mkdir(parents=True, exist_ok=False)
    effects = {"g1": [3, 3.05, 3.02], "g2": [.01, .02, -.01], "g3": [.6, .9, .7], "g4": [.4, None, .42], "g5": [-3.2, -3.3, -3.25], "g6": [.3, -.3, .05], "g7": [4, 4.1, 4.05], "g8": [.02, -.01, .03]}
    datasets = []
    for index, cohort in enumerate(("a", "b", "c")):
        se = (.1, .12, .11)[index]
        with (project / f"{cohort}.tsv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream, delimiter="\t", lineterminator="\n"); writer.writerow(["feature_id", "beta", "se", "p_value", "n"])
            for feature, values in effects.items():
                if values[index] is not None:
                    beta = values[index]; writer.writerow([feature, beta, se, math.erfc(abs(beta / se) / math.sqrt(2)), 1000 + index * 100])
        datasets.append({"id": cohort, "cohort": f"cohort-{cohort}", "type": "transcriptomics", "stage": "association_results", "species": "human", "assay": "synthetic-RNA", "tissue": "blood", "genome_build": "GRCh38", "files": {"results": f"{cohort}.tsv"}})
    with (project / "sampling.tsv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n"); writer.writerow(["feature_id", "dataset_a", "dataset_b", "value"])
        for feature in effects:
            for i, left in enumerate(("a", "b", "c")):
                for j, right in enumerate(("a", "b", "c")):
                    if j < i or missing and feature == "g3" and (left, right) == ("a", "b"):
                        continue
                    writer.writerow([feature, left, right, 1 if i == j else (-.2 if feature == "g6" else .4) if (left, right) == ("a", "b") else 0])
    contract = {"estimand": "synthetic feature association with a common phenotype", "orientation": "feature_to_phenotype", "effect_scale": "linear", "effect_units": "phenotype units per common feature unit", "phenotype_transformation": "none", "feature_transformation": "none"}
    study = {"schema_version": 1, "project": {"id": "dependent-pooling-fixture", "title": "Synthetic dependent-source GLS", "description": "Independent numerical verification of conditional known-covariance inference."}, "resources": {"network": "offline", "downloads": "deny"}, "datasets": datasets,
             "cohort_relationships": [{"between": ["cohort-a", "cohort-b"], "type": "overlapping", "overlap_fraction": .3, "description": "Synthetic overlap; this fraction is deliberately not the sampling correlation."}],
             "hypotheses": [{"id": "H", "description": "Compatible dependent discovery effects with independent validation.", "outcome": "synthetic", "inputs": {"discovery": ["a", "b"], "replication": ["c"]}, "analysis": {"mode": "manual", "goals": ["meta_analysis", "discovery_replication"], "harmonization": {"effect_outliers": {"enabled": False}},
                 "pooling": {"require_complete_contract": True, "effect_contract": contract, "dependence": {"method": "sampling_correlation", "matrix": "sampling.tsv", "rationale": "Known synthetic sampling correlations, including a negative feature-specific correlation; no overlap-fraction substitution.", "citation": "Synthetic software fixture; not biological evidence."}},
                 "operations": {"meta_analysis": {"model": "random", "tau_squared_estimator": "pm", "inference": "modified-hk", "confidence_level": .95}}}}]}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    inputs = {name: sha(project / name) for name in ["a.tsv", "b.tsv", "c.tsv", "sampling.tsv", "study.yaml"]}
    (project / "fixture-input-hashes.json").write_text(json.dumps({"hashes": inputs, "missing_pair": missing}, indent=2) + "\n", encoding="utf-8")


def solve(matrix, rhs):
    n = len(rhs); a = [list(row) + [value] for row, value in zip(matrix, rhs)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda row: abs(a[row][col])); a[col], a[pivot] = a[pivot], a[col]
        divisor = a[col][col]; assert abs(divisor) > 1e-15
        a[col] = [value / divisor for value in a[col]]
        for row in range(n):
            if row != col:
                factor = a[row][col]; a[row] = [left - factor * right for left, right in zip(a[row], a[col])]
    return [row[-1] for row in a]


def reference(feature, ids, sources, correlations):
    ids = [cohort for cohort in ids if feature in sources[cohort]]
    if len(ids) < 2:
        return None
    y = [float(sources[cohort][feature]["beta"]) for cohort in ids]
    errors = [float(sources[cohort][feature]["se"]) for cohort in ids]
    covariance = [[correlations[(feature, *sorted((left, right)))] * errors[i] * errors[j] for j, right in enumerate(ids)] for i, left in enumerate(ids)]
    def fit(tau):
        v = [[value + (tau if i == j else 0) for j, value in enumerate(row)] for i, row in enumerate(covariance)]
        raw_weights = solve(v, [1] * len(ids)); total = sum(raw_weights); weights = [value / total for value in raw_weights]
        beta = sum(weight * value for weight, value in zip(weights, y)); residuals = [value - beta for value in y]
        q = sum(value * weighted for value, weighted in zip(residuals, solve(v, residuals)))
        return beta, 1 / total, q, weights
    q0 = fit(0)[2]; df = len(ids) - 1; tau = 0
    if q0 > df:
        lo, hi = 0, 1
        while fit(hi)[2] > df:
            hi *= 2
        for _ in range(100):
            mid = (lo + hi) / 2
            if fit(mid)[2] > df:
                lo = mid
            else:
                hi = mid
        tau = (lo + hi) / 2
    beta, variance, q, weights = fit(tau); se = math.sqrt(variance * max(1, q / df)); statistic = abs(beta / se)
    p = 2 / math.pi * math.atan(1 / statistic) if df == 1 and statistic else 1 - statistic / math.sqrt(statistic * statistic + 2) if df == 2 else 1
    critical = 1 / math.tan(math.pi * .05 / 2) if df == 1 else math.sqrt(2) * .95 / math.sqrt(.05 * 1.95)
    return {"beta": beta, "se": se, "tau_squared": tau, "q": q0, "p_value": p, "ci_lower": beta - critical * se, "ci_upper": beta + critical * se, "n_cohorts": len(ids), "weights": dict(zip(ids, weights)), "ids": ids}


def bh(values):
    ordered = sorted(values, key=values.get); q = {}; last = 1
    for rank in range(len(ordered), 0, -1):
        key = ordered[rank - 1]; last = min(last, values[key] * len(ordered) / rank); q[key] = last
    return q


def verify(project, jar):
    fixture = json.loads((project / "fixture-input-hashes.json").read_text(encoding="utf-8")); missing = fixture["missing_pair"]
    for name, digest in fixture["hashes"].items():
        assert sha(project / name) == digest
    status = yaml.safe_load((project / "state/project-status.yaml").read_text(encoding="utf-8")); run = status["run_id"]
    manifests = {data["operation_type"]: (path.parent, data) for path in (project / "runs" / run / "operations").glob("*/manifest.yaml") for data in [yaml.safe_load(path.read_text(encoding="utf-8"))]}
    sources = {cohort: {row["feature_id"]: row for row in table(project / f"{cohort}.tsv")} for cohort in ("a", "b", "c")}
    correlations = {(row["feature_id"], *sorted((row["dataset_a"], row["dataset_b"]))): float(row["value"]) for row in table(project / "sampling.tsv")}
    checks = 0; maximum_error = 0; omission_checks = 0; weight_checks = 0
    for operation, ids in [("meta_analysis", ["a", "b", "c"]), ("discovery_meta_analysis", ["a", "b"])]:
        folder, manifest = manifests[operation]
        assert manifest["state"] == ("waiting_for_input" if missing else "complete")
        rows = table(folder / "meta-analysis.tsv")
        assert len(rows) == 8
        for row in rows:
            feature = row["feature_id"]
            if missing and feature == "g3":
                assert row["status"] == "sampling_covariance_unresolved" and row["p_value"] == "NA"
                assert int(row["n_sources"]) == len(ids)
                continue
            expected = reference(feature, ids, sources, correlations)
            if expected is None:
                assert row["status"] == "below_min_cohorts"
                continue
            assert row["status"] == "ok" and row["i_squared"] == "NA" and row["q_definition"] == "generalized_sampling_covariance"
            for field in ("beta", "se", "tau_squared", "q", "p_value", "ci_lower", "ci_upper"):
                actual = float(row[field]); error = abs(actual - expected[field]); maximum_error = max(maximum_error, error)
                assert math.isclose(actual, expected[field], rel_tol=2e-6, abs_tol=2e-7), (operation, feature, field, actual, expected[field])
            assert float(row["reported_n_sum"]) == sum(float(sources[cohort][feature]["n"]) for cohort in expected["ids"])
            assert int(row["n_sources"]) == len(expected["ids"])
            checks += 1
        if not missing:
            assert "pooling_audit" in manifest["details"] and manifest["details"]["pooling_audit"]["compatibility_status"] == "declared_compatible"
    if missing:
        assert status["state"] == "waiting_for_input"
        decision = yaml.safe_load((manifests["sensitivity_analysis"][0] / "decision.yaml").read_text(encoding="utf-8"))
        assert decision["primary_artifacts"] == {}, "An incomplete pool cannot become a primary baseline"
        assert decision["primary_metrics"]["meta_tested_features"] == 0
    else:
        assert status["state"] == "completed"
        discovery = {feature: reference(feature, ["a", "b"], sources, correlations) for feature in sources["a"]}
        discovery_q = bh({feature: row["p_value"] for feature, row in discovery.items() if row is not None})
        validation_q = bh({feature: float(row["p_value"]) for feature, row in sources["c"].items()})
        for row in table(manifests["discovery_replication"][0] / "replication.tsv"):
            feature = row["feature_id"]
            assert math.isclose(float(row["discovery_adjusted_p"]), discovery_q[feature], rel_tol=2e-6, abs_tol=1e-8)
            assert math.isclose(float(row["replication_adjusted_p"]), validation_q[feature], rel_tol=1e-9, abs_tol=1e-12)
            assert (row["discovery_selected"] == "true") == (discovery_q[feature] < .05)
        sensitivity, sensitivity_manifest = manifests["sensitivity_analysis"]
        assert sensitivity_manifest["state"] == "complete"
        decision = yaml.safe_load((sensitivity / "decision.yaml").read_text(encoding="utf-8"))
        for operation, filename in (("meta_analysis", "meta-analysis.tsv"), ("discovery_replication", "replication.tsv")):
            artifact = manifests[operation][0] / filename
            identity = decision["primary_artifacts"][operation]
            assert identity["artifact"] == artifact.relative_to(project).as_posix()
            assert identity["sha256"] == sha(artifact) == manifests[operation][1]["output_sha256"][filename]
        primary_p = {feature: reference(feature, ["a", "b", "c"], sources, correlations)["p_value"] for feature in sources["a"]}
        primary_metrics = decision["primary_metrics"]
        assert primary_metrics["meta_tested_features"] == len(primary_p) == 8
        assert primary_metrics["meta_BH_FDR_0_05_features"] == sum(value <= .05 for value in bh(primary_p).values())
        assert primary_metrics["mean_i_squared"] == "NA"
        for analysis in decision["analyses"]:
            if analysis["status"] == "complete":
                baseline = analysis["primary_metrics"]
                assert set(baseline) == set(primary_metrics) | {"heterogeneity_scope"}
                assert baseline["heterogeneity_scope"] == "registered_common_features"
                assert {key: baseline[key] for key in primary_metrics} == primary_metrics
        for output_name in sensitivity_manifest["output_sha256"]:
            if not output_name.endswith("/meta-analysis.tsv"):
                continue
            path = sensitivity / output_name
            parameters = json.loads(Path(str(path) + ".log").read_text(encoding="utf-8"))
            ids = parameters["cohort_order"]
            assert len(ids) == 2 and set(ids) < {"a", "b", "c"}
            observed_weights = {}
            for row in table(path.parent / "pooling-weights.tsv"):
                observed_weights.setdefault(row["feature_id"], {})[row["dataset_id"]] = float(row["normalized_weight"])
            for row in table(path):
                feature = row["feature_id"]; expected = reference(feature, ids, sources, correlations)
                if expected is None:
                    assert row["status"] == "below_min_cohorts"
                    continue
                assert row["status"] == "ok"
                for field in ("beta", "se", "tau_squared", "q", "p_value", "ci_lower", "ci_upper"):
                    actual = float(row[field]); error = abs(actual - expected[field]); maximum_error = max(maximum_error, error)
                    assert math.isclose(actual, expected[field], rel_tol=2e-6, abs_tol=2e-7), (output_name, feature, field, actual, expected[field])
                assert set(observed_weights[feature]) == set(expected["weights"])
                for cohort, expected_weight in expected["weights"].items():
                    assert math.isclose(observed_weights[feature][cohort], expected_weight, rel_tol=2e-6, abs_tol=2e-7)
                    weight_checks += 1
                omission_checks += 1
        assert omission_checks == 22
    cli(jar, "verify", "--project", project)
    pinned = project / "software" / f"jlinalg-{sha(jar)}.jar"; pinned.parent.mkdir(exist_ok=True)
    if not pinned.exists():
        shutil.copyfile(jar, pinned)
    verifier = project / "workflow-verifier-source.py"
    if verifier.exists():
        assert sha(verifier) == sha(Path(__file__)), "Use the pinned verifier for a historical bundle"
    else:
        shutil.copyfile(Path(__file__), verifier)
    report = {"run_id": run, "jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(project)), "verifier_sha256": sha(verifier), "scalar_reference_rows_verified": checks, "omission_reference_rows_verified": omission_checks, "omission_weights_verified": weight_checks, "maximum_absolute_error": maximum_error, "missing_pair": missing, "input_hashes_unchanged": True, "discovery_inputs_exclude_validation": True, "independent_validation_executed": not missing, "overlap_fraction_not_used_as_correlation": True, "sampling_heterogeneity_separated": True, "primary_baseline_identity_verified": True, "completed_primary_required_for_sensitivities": True, "full_pipeline_conformance": False}
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True); parser.add_argument("--missing-pair", action="store_true")
    args = parser.parse_args(); project = args.project.resolve(); jar = args.jar.resolve()
    if args.action == "run":
        generate(project, args.missing_pair); cli(jar, "auto", project / "study.yaml", "--offline")
    verify(project, jar)
