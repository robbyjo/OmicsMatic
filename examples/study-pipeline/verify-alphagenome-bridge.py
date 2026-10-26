"""Exercise serialization with the installed official SDK; makes no API calls.

Run with an environment containing alphagenome, numpy, pandas and anndata:
  python -B examples/study-pipeline/verify-alphagenome-bridge.py --out build/alpha-bridge-check
This is synthetic SDK-format evidence, not model prediction validation.
"""
import argparse
import csv
import hashlib
import importlib.util
import io
import json
from pathlib import Path

import anndata
import numpy as np
import pandas as pd
from alphagenome.data import genome


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--bridge", type=Path, default=Path(__file__).resolve().parents[2] / "src/main/resources/org/jlinalg/cli/alphagenome_bridge.py")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    spec = importlib.util.spec_from_file_location("jlinalg_alphagenome_bridge", args.bridge)
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    variant = genome.Variant(chromosome="chr1", position=100, reference_bases="A", alternate_bases="G")
    obs = pd.DataFrame({"gene_id": ["ENSG1", "ENSG1"], "transcript_id": ["ENST1", "ENST2"], "variant": [variant, variant]})
    tracks = pd.DataFrame({"ontology_curie": ["UBERON:0002107", "UBERON:0000178"]}, index=["liver", "blood"])
    raw = np.array([[-0.4, 0.7], [-1.2, 0.0]], dtype=np.float32)
    quantiles = np.array([[-0.95, 0.9], [-0.99, 0.0]], dtype=np.float32)
    scores = anndata.AnnData(raw, obs=obs, var=tracks, layers={"quantiles": quantiles}, uns={"variant": variant})
    request = {"canonical_variant_id": "GRCh38:1:100:A:G", "mode": "atlas", "resource_version": "synthetic-format-fixture", "model_version": "ALL_FOLDS", "ontology_terms": ["UBERON:0002107"]}
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=bridge.FIELDS, delimiter="\t", lineterminator="\n")
    writer.writeheader()
    raw_count, exported, status = bridge.export_scores(scores, variant, request, args.out, "RNA_SEQ", writer, 10)
    rows = list(csv.DictReader(io.StringIO(buffer.getvalue()), delimiter="\t"))
    assert (raw_count, exported, status) == (4, 2, "complete")
    assert {row["transcript_id"] for row in rows} == {"ENST1", "ENST2"}
    assert all(row["ontology_curie"] == "UBERON:0002107" for row in rows)
    assert all(float(row["quantile_score"]) < 0 for row in rows)
    np.testing.assert_array_equal(np.load(args.out / "RNA_SEQ/scores.npy", allow_pickle=False), raw)
    np.testing.assert_array_equal(np.load(args.out / "RNA_SEQ/layer-0.npy", allow_pickle=False), quantiles)
    axes = json.loads((args.out / "RNA_SEQ/axes.json").read_text(encoding="utf-8"))
    assert axes["shape"] == [2, 2] and len(axes["tracks"]) == 2
    budget = args.out / "budget"; budget.mkdir()
    assert bridge.export_scores(scores, variant, request, budget, "RNA_SEQ", writer, 1) == (4, 0, "output_budget_exceeded")
    assert (budget / "RNA_SEQ/scores.npy").is_file()
    mismatch = args.out / "mismatch"; mismatch.mkdir()
    try:
        bridge.export_scores(scores, genome.Variant(chromosome="chr1", position=100, reference_bases="A", alternate_bases="T"), request, mismatch, "RNA_SEQ", writer, 10)
        raise AssertionError("Mismatched allele identity must be rejected")
    except ValueError:
        pass
    report = {"scope": "synthetic official-SDK AnnData serialization; no API requests or real predictions", "sdk": bridge.describe(), "bridge_sha256": hashlib.sha256(args.bridge.read_bytes()).hexdigest(), "verifier_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), "checks": ["all raw arrays retained", "signed quantiles retained", "per-transcript rows", "ontology-filtered exports", "row budget disposition", "allele mismatch rejected"]}
    (args.out / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
