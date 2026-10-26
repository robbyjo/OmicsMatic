"""Offline reviewed-source MR family and metadata acceptance fixture.

run NEW_DIRECTORY --jar JAR [--missing-source]
verify DIRECTORY --jar JAR

Synthetic inputs and source approval flags exercise software contracts only.
No patient data, external requests, real AI or human attestations are used.
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


def cli(jar, *arguments):
    subprocess.run(["java", "-jar", str(jar), "study", *map(str, arguments)], check=True)


def generate(project, missing_source):
    project.mkdir(parents=True, exist_ok=False)
    files = {"results": "results.tsv", "annotation": "annotation.tsv", "ld": "ld.tsv", "alleles": "alleles.tsv"}
    (project / "results.tsv").write_text("feature_id\tbeta\tse\tp_value\tN\n" + "".join(f"g{i}\t.5\t.1\t.00001\t1000\n" for i in range(1, 5)), encoding="utf-8")
    (project / "annotation.tsv").write_text("feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\n" + "".join(f"g{i}\tSYNTH{i}\tENSG{i}\t1\t100\t300\tGRCh38\n" for i in range(1, 5)), encoding="utf-8")
    (project / "ld.tsv").write_text("variant_id\trs1\trs2\trs3\nrs1\t1\t0\t0\nrs2\t0\t1\t0\nrs3\t0\t0\t1\n", encoding="utf-8")
    (project / "alleles.tsv").write_text("variant_id\teffect_allele\tother_allele\nrs1\tA\tG\nrs2\tA\tG\nrs3\tA\tG\n", encoding="utf-8")
    pairs = []
    for feature in range(1, 5):
        exposure = "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n"
        outcome = exposure
        for snp, bx in enumerate((.1, .2, .3), 1):
            p = .5 if feature == 4 or feature == 2 and snp > 1 else 1e-12
            by = bx * (.5 if feature == 1 else .1) if feature < 3 else (.0002, -.0001, .0003)[snp - 1]
            exposure += f"rs{snp}\t1\t{snp * 100}\tA\tG\t{bx:.12g}\t.01\t{p:.12g}\n"
            ea, oa = ("G", "A") if snp == 2 else ("A", "G")
            outcome += f"rs{snp}\t1\t{snp * 100}\t{ea}\t{oa}\t{(-by if snp == 2 else by):.12g}\t.02\t.4\n"
        files[f"exposure{feature}"] = f"x{feature}.tsv"; files[f"outcome{feature}"] = f"y{feature}.tsv"
        (project / f"x{feature}.tsv").write_text(exposure, encoding="utf-8"); (project / f"y{feature}.tsv").write_text(outcome, encoding="utf-8")

        def source(role, trait):
            return {"dataset_id": "a", "file_role": role, "source_id": f"synthetic-{role}", "citation": "Synthetic protocol fixture; no biological evidence.",
                    "genome_build": "GRCh38", "ancestry": "European", "sample_size": 10000, "complete_region": True,
                    "trait": trait, "feature_id": f"g{feature}", "tissue": "blood", "assay": "sQTL"}

        pair = {"id": f"pair-g{feature}", "feature_id": f"g{feature}", "approved": True, "rationale": "Synthetic software acceptance only.",
                "sample_overlap": "none", "sample_overlap_basis": "Separate simulated summaries.", "instrument_validity_rationale": "Synthetic independent instruments, without biological claims.",
                "region": {"chromosome": "1", "start": 1, "end": 500}, "exposure": source(f"exposure{feature}", "splicing"),
                "outcome": source(f"outcome{feature}", "BMI"), "ld": {**source("ld", "LD"), "alleles_file_role": "alleles"}}
        if not missing_source or feature != 4:
            pairs.append(pair)
        if feature == 1:
            pairs.append({**pair, "id": "pair-g1-alias"})
    study = {"schema_version": 1, "project": {"id": "mr-family-fixture", "title": "Synthetic MR correction", "description": "Offline metadata, primary MR and coverage contracts."},
             "resources": {"network": "offline", "downloads": "deny"},
             "datasets": [{"id": "a", "cohort": "synthetic", "type": "transcriptomics", "stage": "association_results", "species": "human",
                           "genome_build": "GRCh38", "annotation_release": "synthetic-v1", "coordinate_system": "1-based-closed", "files": files}],
             "hypotheses": [{"id": "H1", "description": "Synthetic molecular associations with BMI.", "outcome": "BMI", "inputs": {"discovery": ["a"]},
                             "analysis": {"mode": "manual", "goals": ["mendelian_randomization"], "harmonization": {"effect_outliers": {"enabled": False}},
                                          "bioinformatics": {"genetic_pairs": pairs, "mr_testing": {"primary_estimator": "ivw-fixed", "adjustment": "BH", "alpha": .05}}}}]}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    (project / "fixture-input-sha256.json").write_text(json.dumps({path.name: sha(path) for path in project.iterdir() if path.is_file()}, indent=2) + "\n", encoding="utf-8")


def verify(project, jar):
    project = project.resolve(); jar = jar.resolve(); cli(jar, "verify", "--project", project)
    for name, expected in json.loads((project / "fixture-input-sha256.json").read_text()).items():
        assert sha(project / name) == expected, name
    config = json.loads((project / "study.yaml").read_text())
    missing_source = all(pair["feature_id"] != "g4" for pair in config["hypotheses"][0]["analysis"]["bioinformatics"]["genetic_pairs"])
    entries = [entry for entry in json.loads((project / "index/evidence-summary.json").read_text())["entries"] if entry["current"]]
    mr = next(entry for entry in entries if entry["operation_type"] == "mendelian_randomization")
    assert mr["state"] == ("waiting_for_input" if missing_source else "complete")
    folder = (project / mr["manifest"]).parent
    rows = {row["feature_id"]: row for row in table(folder / "mr-primary-results.tsv")}
    assert set(rows) == {"g1", "g2", "g3", "g4"}
    assert all(int(row["family_size"]) == 4 for row in rows.values())
    assert set(rows["g1"]["pair_ids"].split(";")) == {"pair-g1", "pair-g1-alias"}
    p_values = {}
    for feature in (1, 2, 3):
        exposures = table(project / f"x{feature}.tsv"); outcomes = {row["variant_id"]: row for row in table(project / f"y{feature}.tsv")}
        aligned = []
        for x in exposures:
            bx, sx = float(x["beta"]), float(x["se"])
            if float(x["p_value"]) > 5e-8 or (bx / sx) ** 2 < 10:
                continue
            y = outcomes[x["variant_id"]]
            sign = 1 if (x["effect_allele"], x["other_allele"]) == (y["effect_allele"], y["other_allele"]) else -1
            aligned.append((bx, float(y["beta"]) * sign, float(y["se"])))
        precision = sum(bx * bx / (sy * sy) for bx, by, sy in aligned)
        beta = sum(bx * by / (sy * sy) for bx, by, sy in aligned) / precision
        se = math.sqrt(1 / precision); p = math.erfc(abs(beta / se) / math.sqrt(2)); p_values[f"g{feature}"] = p
        row = rows[f"g{feature}"]
        assert row["primary_estimator"] == ("wald-ratio" if feature == 2 else "ivw-fixed")
        for key, expected in (("estimate", beta), ("standard_error", se), ("causal_p_value", p)):
            assert math.isclose(float(row[key]), expected, rel_tol=1e-10, abs_tol=1e-14), (feature, key, row[key], expected)
    adjusted = {}; previous = 1
    for rank, (feature, p) in reversed(list(enumerate(sorted(p_values.items(), key=lambda item: item[1]), 1))):
        previous = min(previous, p * 4 / rank); adjusted[feature] = previous
    for feature, q in adjusted.items():
        assert math.isclose(float(rows[feature]["adjusted_p"]), q, rel_tol=1e-10, abs_tol=1e-14)
        assert rows[feature]["significant_primary"] == str(not missing_source and q < .05).lower()
    assert rows["g4"]["causal_p_value"] == "NA" and rows["g4"]["adjusted_p"] == "NA"
    assert rows["g4"]["disposition"] == ("unresolved_primary_test" if missing_source else "not_estimable")
    dispositions = table(folder / "mr-feature-dispositions.tsv")
    assert {row["feature_id"] for row in dispositions} == set(rows)
    assert next(row for row in dispositions if row["feature_id"] == "g4")["disposition"] == ("waiting_for_input" if missing_source else "not_estimable")
    policy = mr["details"]["mr_testing"]
    assert policy["family_size"] == 4 and policy["estimable_primary_tests"] == 3 and policy["duplicate_pair_aliases"] == 1
    assert policy["unresolved_tests"] == int(missing_source) and policy["association_FDR_reused"] is False
    source = next(entry for entry in entries if entry["operation_type"] == "validate_harmonize")
    normalized = (project / source["manifest"]).parent / "standard-results/a.tsv"
    assert all(row["n"] == "1000" for row in table(normalized))
    software = project / "software"; software.mkdir(exist_ok=True); pinned = software / f"jlinalg-{sha(jar)}.jar"
    if not pinned.exists():
        shutil.copyfile(jar, pinned)
    assert sha(pinned) == sha(jar)
    verifier = project / "workflow-verifier-source.py"
    if verifier.resolve() != Path(__file__).resolve():
        if verifier.exists():
            assert sha(verifier) == sha(Path(__file__)), "Use the immutable pinned verifier for a historical bundle"
        else:
            shutil.copyfile(Path(__file__), verifier)
    report = {"run_id": mr["run_id"], "jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(project)), "verifier_sha256": sha(verifier),
              "eligible_features": 4, "family_size": 4, "estimable_primary_tests": 3, "duplicate_aliases": 1, "unresolved_tests": int(missing_source),
              "primary_Wald_IVW_reconstructed": True, "MR_BH_reconstructed": True, "sample_sizes_preserved": True, "input_hashes_unchanged": True,
              "external_requests": 0, "human_attestations": 0, "scientific_appraisal_complete": False, "full_pipeline_conformance": False}
    path = project / "packaged-verification.json"
    if path.exists():
        assert json.loads(path.read_text()) == report
    else:
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--missing-source", action="store_true")
    args = parser.parse_args(); root = args.project.resolve(); jar_path = args.jar.resolve()
    if args.action == "run":
        generate(root, args.missing_source)
        cli(jar_path, "auto", root / "study.yaml", "--project", root, "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture", "--offline")
    verify(root, jar_path)
