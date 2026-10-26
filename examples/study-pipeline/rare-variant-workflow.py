"""Generate or verify synthetic native-score and P-only rare-set study workflows.

python rare-variant-workflow.py generate NEW_DIRECTORY [--kind native|unsigned]
python rare-variant-workflow.py verify DIRECTORY --jar EXECUTABLE_JAR [--run-id ID]
Uses only the Python standard library. Existing input directories are refused.
"""
import argparse
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tsv(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def generate(project, kind):
    project.mkdir(parents=True, exist_ok=False)
    datasets = []
    for index, cohort in enumerate(("a", "b", "v")):
        dataset = {"id": cohort, "cohort": f"cohort-{cohort}", "type": "gwas", "genome_build": "GRCh38"}
        if kind == "native":
            vcf = ["##fileformat=VCFv4.2", "##contig=<ID=1>",
                   '##INFO=<ID=R2,Number=1,Type=Float,Description="Quality">',
                   '##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">',
                   "\t".join(["#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"] + [f"{cohort}{i}" for i in range(1000)])]
            for position, mac, quality in ((100, 1, .99), (110, 19, .99), (120, 20, .99), (130, 100, .99), (140, 5, .1)):
                vcf.append("\t".join(["1", str(position), f"v{mac}", "A", "G", ".", "PASS", f"R2={quality}", "GT"] + ["0/1" if i < mac else "0/0" for i in range(1000)]))
            (project / f"{cohort}.vcf").write_text("\n".join(vcf) + "\n", encoding="utf-8")
            phenotype = ["id\ty\tage"]
            for i in range(1000):
                y = math.sin(i * .77 + index * .4) + 1.5 * (i < 19) + .5 * (i == 0)
                phenotype.append(f"{cohort}{i}\t{y:.14g}\t{30 + i % 73}")
            (project / f"{cohort}-pheno.tsv").write_text("\n".join(phenotype) + "\n", encoding="utf-8")
            (project / f"{cohort}-annotation.tsv").write_text(
                "variant_id\tset_id\tconsequence\n1:100:A:G\tGENE2\tlof\n1:110:A:G\tGENE1\tlof\n1:120:A:G\tGENE1\tlof\n1:130:A:G\tGENE1\tlof\n1:140:A:G\tGENE2\tlof\n", encoding="utf-8")
            dataset.update(stage="processed_matrix", annotation_release="synthetic-v1", units="synthetic",
                           files={"matrix": f"{cohort}.vcf", "phenotype": f"{cohort}-pheno.tsv", "variant_annotation": f"{cohort}-annotation.tsv"})
        else:
            values = (("G1", .001), ("G2", .02), ("G3", .3)) if cohort == "a" else (("G1", .002), ("G2", .03), ("G3", .4)) if cohort == "b" else (("G1", .004), ("G3", .5))
            (project / f"{cohort}.tsv").write_text("feature_id\tmask_id\ttest\tp_value\n" + "".join(f"{gene}\tlof\tskat\t{p}\n" for gene, p in values), encoding="utf-8")
            dataset.update(stage="set_association_results", files={"results": f"{cohort}.tsv"},
                           metadata={"complete_testing_family": True, "set_definition_id": "synthetic-lof-v1", "trait_id": "BMI"})
        datasets.append(dataset)
    analysis = {"mode": "manual", "goals": ["association", "rare_variant_analysis"] if kind == "native" else ["rare_variant_analysis"],
                "rare_variants": {"trait_units": "synthetic", "simulations": 1000, "masks": [{"id": "lof", "consequences": ["lof"], "maximum_maf": .01}]} if kind == "native" else
                    {"tests": ["skat"], "primary_test": "skat", "unsigned_pooling": "acat"}}
    hypothesis = {"id": "H", "description": "Synthetic independent eligibility, full-family correction and discovery/validation lineage.",
                  "inputs": {"discovery": ["a", "b"], "replication": ["v"]}, "analysis": analysis}
    if kind == "native":
        analysis["association"] = {"model": "ols", "minimum_info": .8}
        hypothesis.update(outcome="y", covariates=["age"])
    study = {"schema_version": 1, "project": {"id": f"rare-{kind}-example", "title": f"Synthetic {kind} rare-set workflow",
             "description": "Code verification fixture; no biological conclusions or external services."},
             "resources": {"network": "offline", "downloads": "deny"}, "datasets": datasets, "hypotheses": [hypothesis]}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    (project / "fixture-kind.txt").write_text(kind + "\n", encoding="utf-8")
    hashes = {path.name: sha(path) for path in project.iterdir() if path.is_file()}
    (project / "fixture-input-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    print(project.resolve())


def score_files(path):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        lines = [line for line in stream if not line.startswith("##")]
    lines[0] = lines[0].lstrip("#")
    return list(csv.DictReader(lines, delimiter="\t"))


def burden_from_summaries(scores, cohorts):
    u = variance = 0.0
    for cohort in cohorts:
        folder = scores[cohort]
        marginal = {int(row["POS"]): row for row in score_files(folder / "cohort.score.txt.gz")}
        covariance = {int(row["CURRENT_POS"]): row for row in score_files(folder / "cohort.cov.txt.gz")}
        n = int(marginal[110]["N_INFORMATIVE"])
        u += sum(float(marginal[position]["U_STAT"]) for position in (110, 120))
        for first, second, multiplier in ((110, 110, 1), (110, 120, 2), (120, 120, 1)):
            row = covariance[first]
            values = dict(zip(map(int, row["MARKERS_IN_WINDOW"].split(",")), map(float, row["COV_MATRICES"].split(","))))
            variance += multiplier * values[second] * n
    return u / variance, 1 / math.sqrt(variance), math.erfc(abs(u) / math.sqrt(2 * variance))


def verify_scope_adjustment(rows):
    """Reconstruct BH over every estimable mask/test row in each omitted scope."""
    for scope in sorted({row["scope"] for row in rows}):
        family = sorted((float(row["p_value"]), row["feature_id"], row["mask_id"], row["test"], row)
                        for row in rows if row["scope"] == scope and row["p_value"] != "NA")
        minimum = 1.0
        for rank in range(len(family), 0, -1):
            p, _, _, _, row = family[rank - 1]
            minimum = min(minimum, p * len(family) / rank)
            assert math.isclose(float(row["adjusted_p"]), minimum, rel_tol=1e-10, abs_tol=1e-290), (scope, row)


def verify(project, jar, run_id):
    project = project.resolve(); jar = jar.resolve()
    subprocess.run(["java", "-jar", str(jar), "study", "verify", "--project", str(project)], check=True)
    for relative, expected in json.loads((project / "fixture-input-sha256.json").read_text()).items():
        assert sha(project / relative) == expected, relative
    index = json.loads((project / "index/evidence-summary.json").read_text())
    entries = [entry for entry in index["entries"] if entry["run_id"] == run_id] if run_id else [entry for entry in index["entries"] if entry["current"]]
    assert entries and all(entry["state"] == "complete" for entry in entries)
    rare = next(entry for entry in entries if entry["operation_type"] == "rare_variant_analysis")
    folder = (project / rare["manifest"]).parent
    rows = tsv(folder / "rare-results.tsv")
    sensitivity = tsv(folder / "rare-sensitivity-results.tsv")
    influence = tsv(folder / "rare-influence.tsv")
    assert {row["scope"] for row in sensitivity} == {f"leave-one-out:{cohort}" for cohort in ("a", "b", "v")}
    assert rare["details"]["cohort_influence"]["primary_results_sha256"] == sha(folder / "rare-results.tsv")
    assert len(influence) == len(sensitivity)
    for row in influence:
        assert set(row["included_datasets"].split(",")) == {"a", "b", "v"} - {row["omitted_dataset"]}
        if row["primary_result_type"] == "unsigned_set" or row["sensitivity_result_type"] == "unsigned_set":
            assert row["effect_direction_changed"] == "NA"
    verify_scope_adjustment(sensitivity)
    kind = (project / "fixture-kind.txt").read_text().strip()
    primary = [row for row in rows if row["scope"] == "primary"]
    if kind == "native":
        assert len(rows) == 50 and len(primary) == 10
        assert {row["test"] for row in primary} == {"burden", "skat", "skat-o", "acat-v", "acat-o"}
        scores = {}
        for entry in entries:
            operation = (project / entry["manifest"]).parent
            if entry["operation_type"] == "association":
                for path in (operation / "standard-results").glob("*.tsv"):
                    assert {row["feature_id"] for row in tsv(path)} == {"v20", "v100"}
            if entry["operation_type"] == "rare_variant_scores":
                dataset = entry["details"]["dataset"]; scores[dataset] = operation
                assert entry["details"]["analyzed_samples"] == 1000
                qc = {row["variant_id"]: row for row in tsv(operation / "variant-qc.tsv")}
                for position in (100, 110, 120):
                    assert qc[f"1:{position}:A:G"]["retained"] == "true"
                assert qc["1:140:A:G"]["retained"] == "false"
        for scope, cohorts in (("discovery", ("a", "b")), ("primary", ("a", "b", "v"))):
            row = next(row for row in rows if row["scope"] == scope and row["feature_id"] == "GENE1" and row["test"] == "burden")
            expected = burden_from_summaries(scores, cohorts)
            for field, value in zip(("beta", "se", "p_value"), expected):
                assert math.isclose(float(row[field]), value, rel_tol=1e-9, abs_tol=1e-290), (scope, field, row[field], value)
            assert row["n_variants"] == "2", "Common and low-quality variants must not enter the rare mask."
        assert len(sensitivity) == 30
        for omitted in ("a", "b", "v"):
            row = next(row for row in sensitivity if row["scope"] == f"leave-one-out:{omitted}" and row["feature_id"] == "GENE1" and row["test"] == "burden")
            expected = burden_from_summaries(scores, [cohort for cohort in ("a", "b", "v") if cohort != omitted])
            for field, value in zip(("beta", "se", "p_value"), expected):
                assert math.isclose(float(row[field]), value, rel_tol=1e-9, abs_tol=1e-290), (omitted, field, row[field], value)
    else:
        assert len(primary) == 3
        row = next(row for row in rows if row["scope"] == "cohort:a" and row["feature_id"] == "G1")
        assert math.isclose(float(row["adjusted_p"]), .003, abs_tol=1e-12)
        missing = next(row for row in tsv(folder / "rare-replication.tsv") if row["feature_id"] == "G2")
        assert missing["coverage"] == "not_measured_or_unavailable" and missing["replicated"] == "false"
        original_p = {"a": .001, "b": .002, "v": .004}
        for omitted in original_p:
            component = [p for cohort, p in original_p.items() if cohort != omitted]
            expected = .5 - math.atan(sum(math.tan(math.pi * (.5 - p)) for p in component) / len(component)) / math.pi
            row = next(row for row in sensitivity if row["scope"] == f"leave-one-out:{omitted}" and row["feature_id"] == "G1")
            assert math.isclose(float(row["p_value"]), expected, rel_tol=1e-11, abs_tol=1e-14)
    for row in rows + sensitivity:
        if row["result_type"] == "unsigned_set":
            assert all(row[field] == "NA" for field in ("beta", "se", "direction"))
    assert rare["details"]["primary_identity"]["test"] == ("acat-o" if kind == "native" else "skat")
    software = project / "software"; software.mkdir(exist_ok=True)
    copied_jar = software / f"jlinalg-{sha(jar)}.jar"
    if not copied_jar.exists(): shutil.copyfile(jar, copied_jar)
    assert sha(copied_jar) == sha(jar)
    if Path(__file__).resolve() != (project / "workflow-verifier-source.py").resolve():
        shutil.copyfile(Path(__file__), project / "workflow-verifier-source.py")
    report = {"kind": kind, "run_id": rare["run_id"], "jar_sha256": sha(jar), "tested_jar": str(copied_jar.relative_to(project)),
              "source_hashes_unchanged": True, "typed_unsigned_results_verified": True, "primary_test_rows": len(primary),
              "cohort_influence_verified": True, "sensitivity_rows": len(sensitivity), "sensitivity_full_family_BH_verified": True,
              "native_burden_reconstructed_independently": kind == "native", "full_pipeline_conformance": False, "external_services_tested": False}
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "verify")); parser.add_argument("project", type=Path)
    parser.add_argument("--kind", choices=("native", "unsigned"), default="native")
    parser.add_argument("--jar", type=Path); parser.add_argument("--run-id")
    args = parser.parse_args()
    if args.action == "generate": generate(args.project, args.kind)
    elif args.jar is None: parser.error("verify requires --jar")
    else: verify(args.project, args.jar, args.run_id)
