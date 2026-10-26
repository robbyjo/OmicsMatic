"""Create a new, local synthetic genotype workflow; no downloads or real data.

Usage: python examples/study-pipeline/generate-genotype-workflow.py NEW_DIRECTORY
The directory must not already exist. JSON is valid YAML and avoids dependencies.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys


def generate(directory, effect_qc=False):
    directory.mkdir(parents=True, exist_ok=False)
    data = directory / "data"
    data.mkdir()
    datasets = []
    for cohort, effects in (("a", (0.8, 1.4)), ("b", (1.2, 0.6)), ("v", (-0.4, 1.0))):
        ids = [f"{cohort}-S{i}" for i in range(64)]
        vcf = [
            "##fileformat=VCFv4.2",
            "##contig=<ID=1>",
            '##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">',
            "\t".join(["#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"] + ids),
        ]
        for position, name, carriers in ((19, "v19", 19), (20, "v20", 20), (40, "v40", 40), (50, "vMissing", 20)):
            calls = ["0/1" if i < carriers else "0/0" for i in range(64)]
            if name == "vMissing":
                calls[63] = "./."
            vcf.append("\t".join(["1", str(position), name, "A", "G", ".", "PASS", ".", "GT"] + calls))
        (data / f"{cohort}.vcf").write_text("\n".join(vcf) + "\n", encoding="utf-8")
        phenotype = ["id\ty\tage\tsex"]
        for i, sample in enumerate(ids):
            age = 32 + 0.6 * i + 0.1 * math.sin(i)
            y = effects[0] * (i < 20) + effects[1] * (i < 40) + math.sin(0.9 * i) + 0.005 * age + 0.3 * (i % 2)
            phenotype.append(f"{sample}\t{'NA' if i == 62 else format(y, '.12g')}\t{age:.12g}\t{i % 2}")
        phenotype.append(f"{cohort}-outside\t0\t50\t0")
        (data / f"{cohort}-phenotype.tsv").write_text("\n".join(phenotype) + "\n", encoding="utf-8")
        datasets.append({
            "id": cohort, "cohort": f"cohort-{cohort}", "type": "gwas", "stage": "processed_matrix",
            "genome_build": "GRCh38", "feature_id_type": "variant", "units": "synthetic phenotype units per ALT allele",
            "files": {"matrix": f"data/{cohort}.vcf", "phenotype": f"data/{cohort}-phenotype.tsv"},
            "columns": {"sample_id": "id"}, "metadata": {"synthetic": True},
        })
    study = {
        "schema_version": 1,
        "project": {
            "id": "synthetic-genotype-workflow", "title": "Synthetic genotype workflow verification",
            "description": "MAC 19/20 boundary, phenotype and sample call-rate exclusions, discovery-only pooling and independent validation. No biological conclusions.",
        },
        "cohorts": [{"id": f"cohort-{cohort}"} for cohort in ("a", "b", "v")],
        "cohort_relationships": [{"between": [f"cohort-{left}", f"cohort-{right}"], "type": "independent"}
                                 for left, right in (("a", "b"), ("a", "v"), ("b", "v"))],
        "datasets": datasets,
        "resources": {"network": "offline", "downloads": "deny"},
        "hypotheses": [{
            "id": "H", "description": "Synthetic three-cohort boundary and lineage checks.", "outcome": "y", "covariates": ["age", "sex"],
            "inputs": {"discovery": ["a", "b"], "replication": ["v"]},
            "analysis": {
                "mode": "manual", "goals": ["association", "discovery_replication", "meta_analysis"],
                "rare_variants": {"enabled": False},
                "association": {"model": "ols", "genotype_kind": "hard_calls"},
                "subgroups": [{"id": "age-over-50", "column": "age", "cutoff": 50}],
                "operations": {"meta_analysis": {"model": "fixed", "rationale": "Synthetic common-effect fixture; heterogeneity is retained as a diagnostic."}},
            },
        }],
    }
    if effect_qc:
        study["hypotheses"][0]["analysis"]["harmonization"] = {
            "common_universe": "intersection", "effect_outliers": {"max_abs_effect": 1.0},
        }
    (directory / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    hashes = {path.relative_to(directory).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
              for path in sorted(directory.rglob("*")) if path.is_file()}
    (directory / "fixture-input-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    print(directory.resolve())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--effect-qc", action="store_true", help="Exercise an explicit effect limit and common-universe sensitivity.")
    args = parser.parse_args()
    generate(args.directory, args.effect_qc)
