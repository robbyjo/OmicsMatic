"""Offline missing-model review, explicit model resolution and immutable history.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Requires NumPy, SciPy and PyYAML. Synthetic endpoint/design declarations test
model proposals and gates; no public-provider or AI request occurs. Approval
actors in the explicit-model continuation are synthetic protocol events.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from urllib.parse import unquote_plus
import numpy as np
from scipy.stats import linregress
import yaml

CASES = ("quantitative", "related-quantitative", "binary-gwas", "count", "positive", "survival", "unknown", "categorical-unknown", "public-labels", "blank-model", "policy-denied")
MODELS = {"ols", "lmm", "glm", "glmm", "cox"}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def load(path):
    text = path.read_text(encoding="utf-8")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return yaml.safe_load(text)


def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def cli(jar, project, *args):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, args), "--project", str(project)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def current(project):
    status = load(project / "state/project-status.yaml")
    return status, project / "runs" / status["run_id"] / "operations"


def generate(root, case):
    project = root / case
    project.mkdir()
    n = 32
    i = np.arange(n)
    x = np.array([i * .13 + .04 * f * np.sin(i * (f + .5)) for f in range(1, 5)])
    outcome = i * .24 + .09 * np.cos(i * .6)
    if case in ("binary-gwas", "categorical-unknown"):
        outcome = i % 2
    if case == "count":
        outcome = i % 7
    if case == "positive":
        outcome += 1
    ids = [f"PRIVATE_SAMPLE_{j:03d}" for j in i]
    (project / "matrix.tsv").write_text("feature_id\t" + "\t".join(ids) + "\n" + "".join(f"g{j+1}\t" + "\t".join(map(str, value)) + "\n" for j, value in enumerate(x)), encoding="utf-8")
    (project / "phenotype.tsv").write_text("id\tPRIVATE_value\tPRIVATE_time\tPRIVATE_event\n" + "".join(f"{sample}\t{('case' if value else 'control') if case=='categorical-unknown' else value}\t{j+1}\t{j%2}\n" for j, (sample, value) in enumerate(zip(ids, outcome))), encoding="utf-8")
    files = {"matrix": "matrix.tsv", "phenotype": "phenotype.tsv"}
    if case in ("binary-gwas", "categorical-unknown"):
        text = "##fileformat=VCFv4.2\n##contig=<ID=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t" + "\t".join(ids) + "\n"
        for marker in range(4):
            text += f"1\t{100+marker}\trs{marker+1}\tA\tG\t.\tPASS\t.\tGT\t" + "\t".join(("0/0", "0/1", "1/1")[(j + marker) % 3] for j in i) + "\n"
        (project / "matrix.vcf").write_text(text, encoding="utf-8")
        files["matrix"] = "matrix.vcf"
    options = {} if case in ("unknown", "categorical-unknown") else {"family": "binomial" if case == "binary-gwas" else "poisson" if case == "count" else "gamma" if case == "positive" else "gaussian"}
    if case == "blank-model":
        options["model"] = ""
    if case == "survival":
        options = {"survival": {"time_column": "PRIVATE_time", "event_column": "PRIVATE_event", "time_units": "synthetic units", "event_definition": "synthetic event", "censoring_definition": "synthetic censoring", "rationale": "Synthetic endpoint declaration", "independent_censoring_rationale": "Synthetic software fixture only", "proportional_hazards_rationale": "Synthetic software fixture only"}}
    bio = {"phenotype_terms": ["Synthetic public model-review trait"]}
    if case == "public-labels":
        bio["literature_terms"] = ["alternative splicing", "isoform ratio"]
    analysis = {"mode": "manual", "goals": ["association"], "association": options, "harmonization": {"effect_outliers": {"enabled": False}}, "bioinformatics": bio}
    project_info = {"id": "model-review-fixture", "title": "Synthetic missing-model review", "description": "PRIVATE_DESCRIPTION"}
    if case == "policy-denied":
        project_info["data_policy"] = {"remote_transmission": "deny_all"}
    dataset = {"id": "d", "type": "gwas" if case in ("binary-gwas", "categorical-unknown") else "proteomics" if case == "positive" else "metabolomics" if case == "count" else "expression", "stage": "processed_matrix", "files": files, "metadata": {"related_participants": case in ("related-quantitative", "binary-gwas"), "notes": "PRIVATE_METADATA"}}
    if case in ("binary-gwas", "categorical-unknown"):
        dataset.update(species="human", genome_build="GRCh38")
    study = {"schema_version": 1, "project": project_info, "resources": {"network": "offline", "downloads": "deny"}, "assistance": {"enabled": False}, "datasets": [dataset], "hypotheses": [{"id": "H", "description": "PRIVATE_HYPOTHESIS", "outcome": "PRIVATE_value", "inputs": {"discovery": ["d"]}, "analysis": analysis}]}
    save(project / "study.json", study)
    save(project / "input-hashes.json", {p.name: sha(p) for p in project.iterdir() if p.is_file()})
    return project


def run(root, jar):
    root.mkdir(parents=True, exist_ok=False)
    software = root / "software"
    software.mkdir()
    shutil.copyfile(jar, software / "jlinalg.jar")
    shutil.copyfile(__file__, software / Path(__file__).name)
    save(root / "software.json", {"jar_sha256": sha(jar), "verifier_sha256": sha(Path(__file__))})
    records = []
    jar = software / "jlinalg.jar"
    for case in CASES:
        project = generate(root, case)
        (root / (case + ".log")).write_text(cli(jar, project, "auto", project / "study.json", "--actor-type", "external", "--actor-id", "synthetic-model-review-fixture"), encoding="utf-8")
        status, directory = current(project)
        assert status["state"] == "waiting_for_input", (case, status)
        reviews = list(directory.glob("*/model-review.yaml"))
        assert len(reviews) == 1, (case, reviews)
        prior = {p.relative_to(project).as_posix(): sha(p) for p in directory.rglob("*") if p.is_file()}
        record = {"case": case, "initial_run_id": status["run_id"], "initial_review": reviews[0].relative_to(project).as_posix(), "old_attempt_files_sha256": prior}
        if case == "quantitative":
            patch = project / "amendment-scientist-supplies-model.yaml"
            save(patch, {"schema_version": 1, "amendment": {"id": "scientist-supplies-model", "base_study_hash": status["resolved_study_hash"]}, "changes": {"update": {"hypotheses": [{"id": "H", "analysis": {"association": {"model": "ols"}}}]}}})
            cli(jar, project, "amend", patch, "--approved-by", "synthetic-model-review-fixture")
            cli(jar, project, "plan", "--actor-type", "external", "--actor-id", "synthetic-model-review-fixture")
            cli(jar, project, "approve", "--approved-by", "synthetic-model-review-fixture", "--accept-assumptions")
            cli(jar, project, "run", "--allow-partial", "--actor-type", "external", "--actor-id", "synthetic-model-review-fixture")
            final, _ = current(project)
            assert final["state"] == "completed", final
            record["resolved_run_id"] = final["run_id"]
        records.append(record)
    save(root / "execution-receipts.json", records)
    verify(root, jar)


def verify(root, jar):
    pins = load(root / "software.json")
    assert sha(jar) == pins["jar_sha256"] == sha(root / "software/jlinalg.jar")
    assert sha(root / "software/model-review-workflow.py") == pins["verifier_sha256"]
    checks = outputs = 0
    records = load(root / "execution-receipts.json")
    assert len(records) == len(CASES)
    for record in records:
        case = record["case"]
        project = root / case
        for name, digest in load(project / "input-hashes.json").items():
            assert sha(project / name) == digest
        for name, digest in record["old_attempt_files_sha256"].items():
            assert sha(project / name) == digest
        review = load(project / record["initial_review"])
        assert review["schema_version"] == 2 and review["selected_model"] == "none" and not review["association_executed"]
        context = review["review_context"]
        assert not context["family_default_applied"] and not context["independence_established"]
        assert "PRIVATE_" not in json.dumps(context)
        models = {m["model"]: m for m in review["candidate_models"]}
        expected = MODELS if case in ("unknown", "categorical-unknown") else {"glm", "glmm"} if case in ("binary-gwas", "count", "positive") else {"cox"} if case == "survival" else {"ols", "lmm"}
        assert set(models) == expected
        assert all(not m["automatically_selected"] and m["literature_support"] == "not_appraised" for m in models.values())
        assert review["search_status"] == ("policy_denied" if case == "policy-denied" else "offline_policy")
        assert len(review["searches"]) == 2
        for search in review["searches"]:
            assert "sort_date:y" in search["query"] and not search["exhaustive"]
            assert "PRIVATE_" not in search["query"]
        if case in ("related-quantitative", "binary-gwas"):
            assert "relatedness" in review["searches"][1]["query"]
            assert models["lmm" if case == "related-quantitative" else "glmm"]["context_status"] == "matches_declared_context_requires_review"
        if case == "public-labels":
            assert "isoform ratio" in review["searches"][1]["query"] and context["assay_term_basis"] == "explicit_public_literature_terms"
        for path in (project / "runs").rglob("manifest.yaml"):
            manifest = load(path)
            for name, digest in manifest.get("output_sha256", {}).items():
                assert sha(path.parent / name) == digest
                outputs += 1
        for path in (project / "runs").rglob("request.yaml"):
            request = load(path)
            assert request["request_attempted"] is False and "PRIVATE_" not in unquote_plus(request["url"])
        if case == "quantitative":
            status, directory = current(project)
            assert status["state"] == "completed"
            association = next(p for p in directory.iterdir() if load(p / "manifest.yaml")["operation_type"] == "association")
            values = rows(association / "association.tsv")
            assert len(values) == 4
            raw = {r["feature_id"]: [float(v) for k, v in r.items() if k != "feature_id"] for r in rows(project / "matrix.tsv")}
            normalized = {r["feature_id"]: r for r in rows(association / "standard-results/d.tsv")}
            phenotype = [float(r["PRIVATE_value"]) for r in rows(project / "phenotype.tsv")]
            estimates = {name: linregress(x, phenotype) for name, x in raw.items()}
            names = sorted(estimates, key=lambda name: estimates[name].pvalue)
            adjusted = np.minimum.accumulate(np.array([estimates[name].pvalue * len(names) / (i + 1) for i, name in enumerate(names)])[::-1])[::-1]
            adjusted = dict(zip(names, np.minimum(1, adjusted)))
            for row in values:
                estimate = estimates[row["id"]]
                for native, canonical in (("beta", "beta"), ("standard_error", "se"), ("p_value", "p_value")):
                    assert row[native] == normalized[row["id"]][canonical]
                for actual, expected_value in [(float(row["beta"]), estimate.slope), (float(row["standard_error"]), estimate.stderr), (float(row["p_value"]), estimate.pvalue), (float(row["fdr_bh"]), adjusted[row["id"]])]:
                    assert np.isclose(actual, expected_value, rtol=2e-8, atol=0), (row, actual, expected_value)
                    checks += 1
        else:
            status, directory = current(project)
            assert status["state"] == "waiting_for_input"
            assert not any(p.name == "association.tsv" for p in directory.rglob("*"))
        cli(jar, project, "verify")
    report = {"passed": True, "cases": len(records), "initial_missing_model_holds": len(records), "explicit_model_resolved": 1, "independent_numeric_checks": checks, "operation_output_hashes_verified": outputs, **pins, "verification_replay_sha256": sha(Path(__file__)), "old_attempts_unchanged": True, "fixture_protocol_approval_only": True, "public_or_AI_requests": 0, "literature_or_model_suitability_appraised": False, "full_pipeline_conformance": False}
    save(root / "verification.json", report)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    (run if args.action == "run" else verify)(args.directory.resolve(), args.jar.resolve())
