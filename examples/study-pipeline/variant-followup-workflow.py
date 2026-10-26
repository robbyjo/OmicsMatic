"""Packaged whole-universe annotation and contextual-score acceptance fixture.

run NEW_DIRECTORY --jar JAR
verify DIRECTORY --jar JAR
Synthetic predictions exercise imports/provenance, not AlphaGenome accuracy.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def cli(jar, *arguments):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, arguments)], check=True, text=True, capture_output=True)
    print(result.stdout)
    return result.stdout


def generate(project):
    project.mkdir(parents=True, exist_ok=False)
    (project / "reference.fa").write_text(">1\n" + "A" * 2000 + "\n", encoding="utf-8")
    (project / "results.tsv").write_text("feature_id\tbeta\tse\tp_value\tN\n1:100:A:G\t.2\t.1\t.9\t1000\n1:110:A:T\t.3\t.1\t1e-8\t1000\n1:120:A:C\t-.1\t.1\t.01\t1000\n1:130:A:G\t.2\t0\t.5\t1000\n", encoding="utf-8")
    (project / "consequences.tsv").write_text("genome_build\tchrom\tpos\tref\talt\tgene\ttranscript\tconsequence\nGRCh38\t1\t100\tA\tG\tENSG1\tENST1\tmissense_variant\nGRCh38\t1\t100\tA\tG\tENSG1\tENST2\tsplice_region_variant\nGRCh38\t1\t110\tA\tT\tENSG2\tENST3\tstop_gained\nGRCh38\t1\t120\tA\tC\tENSG3\tENST4\tmissense_variant\nGRCh37\t1\t200\tA\tG\tOTHER\tOTHER\tmissense_variant\n", encoding="utf-8")
    fields = ["variant_id", "mode", "resource_version", "model_version", "scorer", "gene_id", "transcript_id", "ontology_curie", "track_id", "raw_score", "quantile_score", "interval", "source_record"]
    with (project / "scores.tsv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n"); writer.writerow(fields)
        writer.writerow(["GRCh38:1:100:A:G", "atlas", "synthetic-atlas-fixture", "synthetic-model", "RNA_SEQ", "ENSG1", "ENST1", "UBERON:0002107", "liver", "-.4", "-.95", "chr1:1-2000", "synthetic1"])
        writer.writerow(["GRCh38:1:100:A:G", "atlas", "synthetic-atlas-fixture", "synthetic-model", "SPLICE_SITES", "ENSG1", "ENST2", "UBERON:0000178", "blood", ".7", ".9", "chr1:1-2000", "synthetic2"])
        writer.writerow(["GRCh38:1:110:A:T", "atlas", "synthetic-atlas-fixture", "synthetic-model", "AVI_SCORE", "ENSG2", "NA", "NA", "aggregate", "2.3", "NA", "NA", "synthetic3"])
    study = {"schema_version": 1, "project": {"id": "variant-followup-fixture", "title": "Synthetic variant follow-up", "description": "Whole-universe coverage without biological prediction claims.", "data_policy": {"remote_transmission": "deny_all"}},
             "resources": {"network": "offline", "downloads": "deny"}, "datasets": [{"id": "a", "cohort": "synthetic", "type": "gwas", "stage": "association_results", "genome_build": "GRCh38", "files": {"results": "results.tsv"}}],
             "hypotheses": [{"id": "H", "description": "Synthetic GWAS import and functional context.", "outcome": "synthetic", "inputs": {"discovery": ["a"]},
                              "analysis": {"mode": "manual", "goals": ["variant_scoring"], "harmonization": {"invalid_rows": "exclude", "effect_outliers": {"enabled": False}},
                                           "variant_followup": {"reference_fasta": "reference.fa", "maximum_variants_per_batch": 1, "consequences": {"engine": "import", "annotations": "consequences.tsv", "format": "canonical", "release": "synthetic-v1"}, "alphagenome": {"engine": "import", "scores": "scores.tsv"}}}}]}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    inputs = {name: sha(project / name) for name in ("reference.fa", "results.tsv", "consequences.tsv", "scores.tsv", "study.yaml")}
    (project / "fixture-input-hashes.json").write_text(json.dumps(inputs, indent=2) + "\n", encoding="utf-8")


def verify(project, jar):
    import yaml
    manifests = [yaml.safe_load(path.read_text(encoding="utf-8")) | {"directory": path.parent} for path in (project / "runs").rglob("manifest.yaml")]
    followups = [manifest for manifest in manifests if manifest.get("operation_type") == "variant_scoring"]
    assert followups
    current_run = yaml.safe_load((project / "state/project-status.yaml").read_text(encoding="utf-8"))["run_id"]
    active = [manifest for manifest in followups if manifest["run_id"] == current_run]
    assert len(active) == 1
    current = active[0]
    assert current["state"] == "complete"
    folder = current["directory"]
    inventory = {row["feature_id"]: row for row in table(folder / "variant-inventory.tsv")}
    assert set(inventory) == {"1:100:A:G", "1:110:A:T", "1:120:A:C", "1:130:A:G"}
    assert {key for key, row in inventory.items() if row["eligible"] == "true"} == {"1:100:A:G", "1:110:A:T", "1:120:A:C"}
    assert all(inventory[key]["consequence_status"] == "annotated" for key in ("1:100:A:G", "1:110:A:T", "1:120:A:C"))
    assert inventory["1:130:A:G"]["disposition"] == "excluded_from_primary"
    assert inventory["1:120:A:C"]["alphagenome_status"] == "not_in_score_import"
    consequences = table(folder / "variant-consequences.tsv")
    assert len(consequences) == 4 and {row["transcript"] for row in consequences if row["variant_id"] == "GRCh38:1:100:A:G"} == {"ENST1", "ENST2"}
    scores = table(folder / "alphagenome-scores.tsv")
    assert len(scores) == 3
    signed = next(row for row in scores if row["scorer"] == "RNA_SEQ")
    assert float(signed["raw_score"]) == -.4 and float(signed["quantile_score"]) == -.95
    assert signed["ontology_curie"] == "UBERON:0002107"
    assert {row["variant_id"] for row in consequences} == {"GRCh38:1:100:A:G", "GRCh38:1:110:A:T", "GRCh38:1:120:A:C"}
    for name, expected in json.loads((project / "fixture-input-hashes.json").read_text(encoding="utf-8")).items():
        assert sha(project / name) == expected
    cli(jar, "verify", "--project", project)
    pinned = project / "software" / f"jlinalg-{sha(jar)}.jar"; pinned.parent.mkdir(exist_ok=True)
    if not pinned.exists(): shutil.copyfile(jar, pinned)
    verifier = project / "workflow-verifier-source.py"
    if verifier.exists(): assert sha(verifier) == sha(Path(__file__)), "Use the pinned verifier for a historical bundle"
    else: shutil.copyfile(Path(__file__), verifier)
    report = {"run_id": current["run_id"], "reused_from_cache": bool(current.get("reused_from_cache")), "jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(project)), "verifier_sha256": sha(verifier), "input_hashes_unchanged": True, "primary_variants": 3, "excluded_variants_retained": 1, "consequence_rows": 4, "contextual_score_rows": 3, "nonsignificant_variant_annotated": True, "signed_quantile_preserved": True, "missing_score_coverage_explicit": True, "external_requests": 0, "real_prediction_accuracy_tested": False, "full_pipeline_conformance": False}
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args(); project = args.project.resolve(); jar = args.jar.resolve()
    if args.action == "run":
        generate(project); cli(jar, "auto", project / "study.yaml", "--offline"); cli(jar, "run", "--project", project)
    verify(project, jar)
