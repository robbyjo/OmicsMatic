"""JLinAlg bridge to the separately installed official AlphaGenome Python SDK.

No SDK installation, endpoint override, credential logging or implicit liftover.
Input is an approved canonical variant/scorer request, never study/participant data.
"""
import argparse
import csv
from datetime import datetime, timezone
import importlib.metadata
import json
import math
import os
from pathlib import Path
import sys


FIELDS = ["variant_id", "mode", "resource_version", "model_version", "scorer", "gene_id", "transcript_id", "ontology_curie", "track_id", "raw_score", "quantile_score", "interval", "source_record"]


def safe(value):
    if value is None or isinstance(value, (str, bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, dict):
        return {str(key): safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [safe(item) for item in value]
    if hasattr(value, "item"):
        try:
            return safe(value.item())
        except (ValueError, TypeError):
            pass
    return str(value)


def write_json(path, value):
    path.write_text(json.dumps(safe(value), allow_nan=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def describe():
    from alphagenome.atlas import atlas
    from alphagenome.models import dna_client, variant_scorers
    return {"sdk_version": importlib.metadata.version("alphagenome"), "atlas_available": callable(atlas.create),
            "prediction_available": callable(dna_client.create), "model_versions": [version.name for version in dna_client.ModelVersion],
            "prediction_scorers": list(variant_scorers.RECOMMENDED_VARIANT_SCORERS)}


def export_scores(scores, variant, request, folder, name, writer, remaining):
    import numpy as np
    target = folder / name
    target.mkdir()
    matrix = np.asarray(scores.X)
    if matrix.ndim != 2 or matrix.shape != (len(scores.obs), len(scores.var)):
        raise ValueError("Unexpected AlphaGenome score dimensions")
    for actual in ([scores.uns["variant"]] if "variant" in scores.uns else []):
        if str(actual) != str(variant):
            raise ValueError("SDK response variant differs from the requested allele identity")
    if "variant" in scores.obs:
        if any(str(actual) != str(variant) for actual in scores.obs["variant"]):
            raise ValueError("SDK response observation belongs to a different variant")
    np.save(target / "scores.npy", matrix, allow_pickle=False)
    layers = {}
    for index, (key, layer) in enumerate(scores.layers.items()):
        filename = f"layer-{index}.npy"
        np.save(target / filename, np.asarray(layer), allow_pickle=False)
        layers[str(key)] = filename
    observations = [safe(row) for row in scores.obs.to_dict(orient="records")]
    tracks = [safe(row) for row in scores.var.to_dict(orient="records")]
    write_json(target / "axes.json", {"observations": observations, "tracks": tracks, "track_indices": [str(value) for value in scores.var.index],
                                       "uns": safe(dict(scores.uns)), "layers": layers, "shape": list(matrix.shape)})
    count = matrix.size
    terms = set(request.get("ontology_terms", []))
    selected_tracks = [j for j, track in enumerate(tracks) if not terms or track.get("ontology_curie") in terms]
    selected_count = len(observations) * len(selected_tracks)
    if selected_count > remaining:
        return count, 0, "output_budget_exceeded"
    if not selected_count:
        return count, 0, "no_matching_tracks"
    quantiles = scores.layers.get("quantiles")
    for i, observation in enumerate(observations):
        for j in selected_tracks:
            track = tracks[j]
            raw = float(matrix[i, j])
            q = float(quantiles[i, j]) if quantiles is not None else float("nan")
            writer.writerow({"variant_id": request["canonical_variant_id"], "mode": request["mode"], "resource_version": request["resource_version"],
                             "model_version": request["model_version"] if request["mode"] == "predict" else "precomputed_atlas",
                             "scorer": name, "gene_id": observation.get("gene_id", "NA"), "transcript_id": observation.get("transcript_id", "NA"),
                             "ontology_curie": track.get("ontology_curie", "NA"), "track_id": str(scores.var.index[j]),
                             "raw_score": repr(raw) if math.isfinite(raw) else "NA", "quantile_score": repr(q) if math.isfinite(q) else "NA",
                             "interval": str(observation.get("interval", scores.uns.get("interval", "NA"))),
                             "source_record": str((target / "axes.json").relative_to(folder.parent)).replace("\\", "/")})
    return count, selected_count, "complete"


def run(request, output):
    from alphagenome.data import genome
    from alphagenome.atlas import atlas
    from alphagenome.models import dna_client, variant_scorers
    info = describe()
    if request.get("schema_version") != 1 or request.get("mode") not in ("atlas", "predict"):
        raise ValueError("Unsupported bridge request")
    key = os.environ.get(request["api_key_env"])
    if not key:
        return {**info, "status": "credential_unavailable", "variants": []}
    mode = request["mode"]
    if mode == "atlas":
        client = atlas.create(key, timeout=request["timeout_seconds"])
    else:
        client = dna_client.create(key, timeout=request["timeout_seconds"], model_version=dna_client.ModelVersion[request["model_version"]])
        unknown = set(request["scorers"]) - set(variant_scorers.RECOMMENDED_VARIANT_SCORERS)
        if unknown:
            raise ValueError("Requested scorer is unavailable for live prediction")
    statuses = []
    remaining = request["maximum_rows"]
    with (output / "scores.tsv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS, delimiter="\t", lineterminator="\n"); writer.writeheader()
        for index, record in enumerate(request["variants"]):
            if record["genome_build"] != "GRCh38":
                raise ValueError("Human AlphaGenome requests require GRCh38; no liftover is inferred")
            variant = genome.Variant(chromosome="chr" + record["chrom"], position=record["pos"], reference_bases=record["ref"], alternate_bases=record["alt"])
            folder = output / f"variant-{index:06d}"; folder.mkdir()
            one = {"variant_id": record["variant_id"], "status": "not_found", "scorers_returned": [], "raw_values": 0, "exported_values": 0}
            try:
                if mode == "atlas":
                    scores = client.query_variant(variant, requested_scorers=request["scorers"], ontology_terms=request["ontology_terms"] or None)
                else:
                    interval = variant.reference_interval.resize(request["sequence_length"])
                    result = client.score_variant(interval=interval, variant=variant, variant_scorers=[variant_scorers.RECOMMENDED_VARIANT_SCORERS[name] for name in request["scorers"]], organism=dna_client.Organism.HOMO_SAPIENS)
                    if len(result) != len(request["scorers"]):
                        raise ValueError("Prediction scorer count differs from the request")
                    scores = dict(zip(request["scorers"], result))
                for scorer, values in scores.items():
                    if scorer not in request["scorers"] or not scorer.replace("_", "").isalnum():
                        raise ValueError("Unexpected scorer identity")
                    detail = {**request, "canonical_variant_id": record["variant_id"]}
                    count, exported, status = export_scores(values, variant, detail, folder, scorer, writer, remaining)
                    one["raw_values"] += count; one["exported_values"] += exported; remaining -= exported
                    if exported:
                        one["scorers_returned"].append(scorer)
                        if one["status"] != "output_budget_exceeded":
                            one["status"] = status if status != "complete" else "complete" if set(one["scorers_returned"]) == set(request["scorers"]) else "partial_scorer_coverage"
                    elif status == "output_budget_exceeded":
                        one["status"] = status
                    elif not one["scorers_returned"] and one["status"] != "output_budget_exceeded":
                        one["status"] = "no_matching_tracks"
                # No REF/ALT prediction is substituted for a missing Atlas result.
            except Exception as error:
                import grpc
                cause = error
                for _ in range(4):
                    if isinstance(cause, grpc.RpcError) or cause.__cause__ is None:
                        break
                    cause = cause.__cause__
                if isinstance(cause, grpc.RpcError) and cause.code() in (grpc.StatusCode.NOT_FOUND, grpc.StatusCode.OUT_OF_RANGE):
                    one["status"] = "not_found"
                else:
                    one["status"] = "provider_or_serialization_error"; one["error_type"] = type(error).__name__
                    # Exception strings can contain provider headers; never copy credentials.
            statuses.append(one)
            write_json(folder / "status.json", one)
    return {**info, "status": "completed_with_dispositions", "mode": mode, "variants": statuses,
            "retrieved_at": datetime.now(timezone.utc).isoformat(), "raw_outputs_retained": True,
            "interpretation": "AlphaGenome predictions are molecular/contextual evidence, not association P values, causal posteriors or clinical certification."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--request", type=Path); parser.add_argument("--out", type=Path); parser.add_argument("--describe", action="store_true")
    args = parser.parse_args()
    try:
        if args.describe:
            print(json.dumps(describe(), sort_keys=True))
        else:
            if not args.out.is_dir():
                raise ValueError("An existing isolated output directory is required")
            response = run(json.loads(args.request.read_text(encoding="utf-8")), args.out)
            write_json(args.out / "response.json", response)
    except ModuleNotFoundError:
        if args.out:
            write_json(args.out / "response.json", {"status": "sdk_unavailable", "variants": []})
        else:
            print(json.dumps({"status": "sdk_unavailable"}))
        sys.exit(3)
    except Exception as error:
        if args.out:
            write_json(args.out / "response.json", {"status": "bridge_error", "error_type": type(error).__name__, "variants": []})
        else:
            print(json.dumps({"status": "bridge_error", "error_type": type(error).__name__}))
        sys.exit(2)
