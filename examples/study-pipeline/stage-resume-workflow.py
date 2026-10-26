"""Offline packaged acceptance for deferred MR inputs and safe cohort-fit reuse.

run NEW_DIRECTORY --jar JAR
verify DIRECTORY --jar JAR

Synthetic cohorts and source-review flags test execution contracts, not scientific
source appraisal. No external requests, patient data, AI, or human attestations.
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
from scipy.stats import linregress

CORE = ("validate_harmonize", "participant_qc", "association")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def cli(jar, project, *args):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, args), "--project", str(project)], capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, (args, result.stdout, result.stderr)
    return yaml.safe_load(result.stdout)


def run_manifests(project, run_id):
    return {(m["hypothesis"], m["operation_type"]): (path, m)
            for path in sorted((project / "runs" / run_id / "operations").glob("*/manifest.yaml"))
            for m in (load(path),)}


def snapshot(project):
    return {str(p.relative_to(project)): sha(p) for p in (project / "runs").rglob("*") if p.is_file()}


def unchanged(project, before):
    for path, digest in before.items():
        assert sha(project / path) == digest, path


def generate(project):
    project.mkdir(parents=True, exist_ok=False)
    values = [[i + .07 * f * ((i % 3) - 1) for i in range(12)] for f in range(1, 5)]
    (project / "matrix.tsv").write_text("feature_id\t" + "\t".join(f"S{i}" for i in range(12)) + "\n" + "".join(f"g{f}\t" + "\t".join(map(str, x)) + "\n" for f, x in enumerate(values, 1)), encoding="utf-8")
    (project / "phenotype.tsv").write_text("id\tBMI\n" + "".join(f"S{i}\t{i + (.2 if i % 2 else -.2)}\n" for i in range(12)), encoding="utf-8")
    (project / "annotation.tsv").write_text("feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\n" + "".join(f"g{i}\tSYNTH{i}\tENSG{i}\t1\t100\t300\tGRCh38\n" for i in range(1, 5)), encoding="utf-8")
    (project / "ld.tsv").write_text("variant_id\trs1\trs2\trs3\nrs1\t1\t0\t0\nrs2\t0\t1\t0\nrs3\t0\t0\t1\n", encoding="utf-8")
    (project / "alleles.tsv").write_text("variant_id\teffect_allele\tother_allele\nrs1\tA\tG\nrs2\tA\tG\nrs3\tA\tG\n", encoding="utf-8")
    files = {"matrix": "matrix.tsv", "phenotype": "phenotype.tsv", "annotation": "annotation.tsv", "ld": "ld.tsv", "alleles": "alleles.tsv"}
    pairs = []
    for feature in range(1, 5):
        header = "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n"
        exposure, outcome = header, header
        for snp, bx in enumerate((.1, .2, .3), 1):
            exposure += f"rs{snp}\t1\t{snp * 100}\tA\tG\t{bx}\t.01\t1e-12\n"
            outcome += f"rs{snp}\t1\t{snp * 100}\tA\tG\t{bx * .5}\t.02\t.4\n"
        files[f"exposure{feature}"] = f"x{feature}.tsv"
        files[f"outcome{feature}"] = f"y{feature}.tsv"
        destination = project / ("deferred-x1.tsv" if feature == 1 else f"x{feature}.tsv")
        destination.write_text(exposure, encoding="utf-8")
        (project / f"y{feature}.tsv").write_text(outcome, encoding="utf-8")

        def source(role, trait):
            return {"dataset_id": "a", "file_role": role, "source_id": f"synthetic-{role}", "citation": "Synthetic acceptance input; no biological evidence.",
                    "genome_build": "GRCh38", "ancestry": "European", "sample_size": 10000, "complete_region": True,
                    "trait": trait, "feature_id": f"g{feature}", "tissue": "blood", "assay": "sQTL"}

        pairs.append({"id": f"pair-g{feature}", "feature_id": f"g{feature}", "approved": True, "rationale": "Synthetic software acceptance only.",
                      "sample_overlap": "none", "sample_overlap_basis": "Separate simulated summaries.", "instrument_validity_rationale": "Synthetic independent instruments without biological claims.",
                      "region": {"chromosome": "1", "start": 1, "end": 500}, "exposure": source(f"exposure{feature}", "splicing"),
                      "outcome": source(f"outcome{feature}", "BMI"), "ld": {**source("ld", "LD"), "alleles_file_role": "alleles"}})
    study = {"schema_version": 1, "project": {"id": "stage-resume", "title": "Synthetic stage resume", "description": "Deferred downstream inputs and immutable execution attempts."},
             "resources": {"network": "offline", "downloads": "deny"},
             "datasets": [{"id": "a", "type": "expression", "stage": "processed_matrix", "species": "human", "genome_build": "GRCh38",
                           "annotation_release": "synthetic-v1", "coordinate_system": "1-based-closed", "files": files}],
             "hypotheses": [{"id": "H1", "description": "Synthetic molecular associations.", "outcome": "BMI", "inputs": {"discovery": ["a"]},
                             "analysis": {"mode": "manual", "goals": ["association", "mendelian_randomization"], "association": {"model": "ols"},
                                          "harmonization": {"effect_outliers": {"enabled": False}}, "bioinformatics": {"genetic_pairs": pairs}}}]}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    return study


def execute(project, jar):
    generate(project)
    software = project / "software"; software.mkdir()
    pinned = software / f"jlinalg-{sha(jar)}.jar"; shutil.copyfile(jar, pinned)
    verifier = project / "workflow-verifier-source.py"; shutil.copyfile(Path(__file__), verifier)
    initial_inputs = {p.name: sha(p) for p in project.iterdir() if p.is_file()}
    records = []

    def run(label, auto=False, rerun=False):
        prior = snapshot(project)
        args = ["auto", project / "study.yaml", "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture"] if auto else ["run", "--allow-partial"]
        if rerun:
            args.append("--rerun")
        summary = cli(pinned, project, *args)
        unchanged(project, prior)
        records.append({"label": label, **summary})
        return run_manifests(project, summary["run_id"])

    run("missing-mr-source", auto=True)
    shutil.copyfile(project / "deferred-x1.tsv", project / "x1.tsv")
    run("mr-source-arrived")

    def amend(id_, changes):
        status = cli(pinned, project, "status")
        path = project / f"amendment-{id_}.yaml"
        path.write_text(json.dumps({"schema_version": 1, "amendment": {"id": id_, "base_study_hash": status["resolved_study_hash"]}, "changes": changes}, indent=2) + "\n", encoding="utf-8")
        cli(pinned, project, "amend", path, "--approved-by", "synthetic-protocol-fixture")
        cli(pinned, project, "plan", "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
        cli(pinned, project, "approve", "--approved-by", "synthetic-protocol-fixture", "--accept-assumptions")

    amend("reporting", {"configure": {"project": {"manuscript_policy": {"max_main_words": 10000, "word_limit_mode": "target"}}}})
    run("reporting-amendment")
    amend("hypothesis", {"add": {"hypotheses": [{"id": "H2", "description": "Independent synthetic hypothesis.", "outcome": "BMI", "inputs": {"discovery": ["a"]},
                                                   "analysis": {"mode": "manual", "goals": ["association"], "association": {"model": "ols"}, "harmonization": {"effect_outliers": {"enabled": False}}}}]}})
    run("unrelated-hypothesis")
    amend("model", {"update": {"hypotheses": [{"id": "H1", "analysis": {"association": {"threads": 2}}}]}})
    run("association-setting-amendment")
    # Deliberate source-data revision is retained as an explicit fixture event.
    original = project / "phenotype-original.tsv"; shutil.copyfile(project / "phenotype.tsv", original)
    (project / "phenotype.tsv").write_text(original.read_text(encoding="utf-8").replace("S11\t11.2", "S11\t12.2"), encoding="utf-8")
    run("phenotype-revision")
    run("fresh-rerun", rerun=True)
    receipt = {"jar_sha256": sha(pinned), "tested_jar": str(pinned.relative_to(project)), "verifier_sha256": sha(verifier), "runs": records,
               "initial_input_sha256": initial_inputs, "final_input_sha256": {p.name: sha(p) for p in project.iterdir() if p.name in initial_inputs},
               "intentional_input_revision": "phenotype.tsv; original retained in phenotype-original.tsv", "old_attempts_unchanged": True}
    (project / "execution-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")


def verify(project, jar):
    receipt = load(project / "execution-receipt.json")
    assert sha(jar) == receipt["jar_sha256"]
    assert sha(project / receipt["tested_jar"]) == receipt["jar_sha256"]
    assert sha(project / "workflow-verifier-source.py") == receipt["verifier_sha256"] == sha(Path(__file__))
    cli(jar, project, "verify")
    stages = {r["label"]: run_manifests(project, r["run_id"]) for r in receipt["runs"]}
    for label, stages_ in stages.items():
        for path, m in stages_.values():
            for output, digest in m["output_sha256"].items():
                assert sha(path.parent / output) == digest, (label, path, output)
    first = stages["missing-mr-source"]
    assert first[("H1", "mendelian_randomization")][1]["state"] == "waiting_for_input"
    first_hash = first[("H1", "association")][1]["execution_resolved_study_hash"]
    for label in ("mr-source-arrived", "reporting-amendment", "unrelated-hypothesis"):
        for type_ in CORE:
            _, m = stages[label][("H1", type_)]
            assert m["reused_from_cache"] is True and m["input_scope"] == "native_cohort_preparation_v1"
            assert m["output_sha256"] == first[("H1", type_)][1]["output_sha256"]
            assert m["execution_resolved_study_hash"] == first_hash
        assert stages[label][("H1", "mendelian_randomization")][1]["state"] == "complete"
    assert stages["reporting-amendment"][("H1", "association")][1]["current_resolved_study_hash"] != first_hash
    for type_ in CORE:
        assert not stages["association-setting-amendment"][("H1", type_)][1].get("reused_from_cache", False)
        assert stages["association-setting-amendment"][("H2", type_)][1]["reused_from_cache"] is True
        for h in ("H1", "H2"):
            assert not stages["phenotype-revision"][(h, type_)][1].get("reused_from_cache", False)
            assert not stages["fresh-rerun"][(h, type_)][1].get("reused_from_cache", False)
    checks = 0
    for label in ("phenotype-revision", "fresh-rerun"):
        y = [float(r["BMI"]) for r in table(project / "phenotype.tsv")]
        for h in ("H1", "H2"):
            path, m = stages[label][(h, "association")]
            rows = table(path.parent / "standard-results/a.tsv")
            with (project / "matrix.tsv").open(encoding="utf-8") as stream:
                matrix = list(csv.reader(stream, delimiter="\t"))[1:]
            assert len(rows) == len(matrix) == 4
            for row, source in zip(rows, matrix):
                fit = linregress(list(map(float, source[1:])), y)
                for column, value in (("beta", fit.slope), ("se", fit.stderr), ("p_value", fit.pvalue)):
                    assert math.isclose(float(row[column]), value, rel_tol=1e-8, abs_tol=1e-14), (label, h, column, row[column], value)
                    checks += 1
        mr_path, _ = stages[label][("H1", "mendelian_randomization")]
        assert len(table(mr_path.parent / "mr-primary-results.tsv")) == 4
    for name, digest in receipt["initial_input_sha256"].items():
        if name != "phenotype.tsv":
            assert sha(project / name) == digest, name
    report = {"jar_sha256": receipt["jar_sha256"], "verifier_sha256": receipt["verifier_sha256"], "runs": len(stages), "independent_OLS_checks": checks,
              "deferred_MR_completed": True, "upstream_reused_across_arrival_reporting_and_hypothesis_amendments": True,
              "scientific_changes_invalidated": True, "fresh_rerun_parity": True, "old_attempts_unchanged": receipt["old_attempts_unchanged"],
              "external_requests": 0, "human_attestations": 0, "full_pipeline_conformance": False}
    path = project / "packaged-verification.json"
    if path.exists():
        assert load(path) == report
    else:
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args(); project_path = args.project.resolve(); jar_path = args.jar.resolve()
    if args.action == "run":
        execute(project_path, jar_path)
    verify(project_path, jar_path)
