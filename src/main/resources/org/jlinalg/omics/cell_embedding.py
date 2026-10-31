"""Pinned-upstream cell representation adapters, with persistent failure reports.

Inputs are supplied PCA coordinates or explicit weighted graphs. No normalization, biological inference,
cell annotation, or batch correction is inferred from an embedding.
"""
import argparse
import csv
import importlib
import importlib.metadata
import json
import math
import os
from pathlib import Path
import platform
import random
import sys
import traceback

PINS = {"umap": "umap-learn==0.5.12", "leiden": "leidenalg==0.12.0",
        "louvain": "igraph==1.0.0", "harmony": "harmonypy==2.1.0"}


def status(state, detail, action="None required."):
    record = {"status": state, "detail": detail, "required_action": action}
    Path("analysis-status.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    if state != "completed":
        Path("WARNINGS.md").write_text(
            "# " + ("SKIPPED ANALYSIS" if state.startswith("skipped") else "ANALYSIS FAILED") + "\n\n"
            + detail + "\n\n**Required action:** " + action + "\n\nNo successful analysis result is published for this attempt.\n", encoding="utf-8")
    return 0 if state == "completed" else 3 if state.startswith("skipped") else 2


def supplied_graph(nodes, edge_file):
    for path in (nodes, edge_file):
        if path.stat().st_size > 128 * 1024 * 1024:
            raise ValueError("Supplied graph tables must each be <=128 MiB")
    with nodes.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.reader(stream, delimiter="\t")
        if next(reader, None) != ["obs_id"]:
            raise ValueError("Node table must have exactly one obs_id column")
        ids, indices = [], {}
        for row in reader:
            if len(row) != 1 or not row[0].strip() or row[0] in indices:
                raise ValueError("Nodes require unique nonblank obs_id values")
            indices[row[0]] = len(ids)
            ids.append(row[0])
            if len(ids) > 100_000:
                raise ValueError("Supplied graph exceeds 100000 nodes")
    if len(ids) < 2:
        raise ValueError("Supplied graph needs at least two nodes")
    edges = {}
    with edge_file.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.reader(stream, delimiter="\t")
        if next(reader, None) != ["source", "target", "weight"]:
            raise ValueError("Edge table must have source, target, weight columns in that order")
        for row in reader:
            if len(row) != 3 or row[0] not in indices or row[1] not in indices:
                raise ValueError("Every edge endpoint must occur in nodes; edge width must be three")
            pair = tuple(sorted((indices[row[0]], indices[row[1]])))
            weight = float(row[2])
            if pair[0] == pair[1] or pair in edges:
                raise ValueError("Self-loops and duplicate/reversed undirected edges are not allowed")
            if not math.isfinite(weight) or weight <= 0:
                raise ValueError("Edge weights must be positive and finite")
            edges[pair] = weight
            if len(edges) > 1_000_000:
                raise ValueError("Supplied graph exceeds 1000000 edges")
    if not edges or not math.isfinite(math.fsum(edges.values())):
        raise ValueError("Supplied graph needs positive finite total edge weight")
    return ids, edges


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--method", choices=PINS, required=True)
    parser.add_argument("--matrix", type=Path)
    parser.add_argument("--nodes", type=Path)
    parser.add_argument("--graph", type=Path)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--batch")
    parser.add_argument("--neighbors", type=int, default=15)
    parser.add_argument("--components", type=int, default=2)
    parser.add_argument("--resolution", type=float, default=1.0)
    parser.add_argument("--min-dist", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()
    module = {"umap": "umap", "leiden": "leidenalg", "louvain": "igraph", "harmony": "harmonypy"}[args.method]
    try:
        engine = importlib.import_module(module)
        import numpy as np
        import sklearn
    except (ImportError, importlib.metadata.PackageNotFoundError) as error:
        return status("skipped-dependency-unavailable", f"{args.method} requires {PINS[args.method]} and its dependencies: {error}",
            f"Use tools/literature/install_cell_dependencies.py --allow-install in an approved environment, then supply its Python with --python. Install {PINS[args.method]} in a supported OS/Python environment. Harmony 2.x publishes Linux/macOS wheels; this Windows environment may require an approved C++ build or Linux/WSL execution. Inspect external.log.")
    package = PINS[args.method].split("==")[0]
    actual = importlib.metadata.version(package)
    expected = PINS[args.method].split("==")[1]
    if actual != expected:
        return status("skipped-unvalidated-dependency-version", f"{package} {actual} is outside the pinned adapter contract ({expected}).",
                      f"Install {PINS[args.method]} into a separate approved environment and supply its Python; retain other environments unchanged.")
    graph_input = args.nodes is not None or args.graph is not None
    if graph_input and (args.method not in {"leiden", "louvain"} or args.matrix is not None or args.nodes is None or args.graph is None):
        raise ValueError("Supplied graphs require Leiden/Louvain, nodes and graph, without a matrix")
    if not 0 <= args.seed <= 2147483647:
        raise ValueError("seed must be in [0,2147483647]")
    if graph_input:
        ids, edges = supplied_graph(args.nodes, args.graph)
        x, rows = None, None
    else:
        if args.matrix is None or args.matrix.stat().st_size > 128 * 1024 * 1024:
            raise ValueError("PCA matrix is required and must be <=128 MiB")
        with args.matrix.open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.reader(stream, delimiter="\t"))
        ids, x = read_matrix(rows, np)
    if not graph_input and not 2 <= args.neighbors < min(201, len(ids)):
        raise ValueError("neighbors must be in [2,min(200,observations-1)]")
    if args.method == "umap" and not 2 <= args.components <= min(20, len(ids) - 2):
        raise ValueError("components must be in [2,min(20,observations-2)]")
    if not math.isfinite(args.resolution) or not 0 < args.resolution <= 10:
        raise ValueError("resolution must be in (0,10]")
    if not math.isfinite(args.min_dist) or not 0 <= args.min_dist <= 1:
        raise ValueError("min-dist must be in [0,1]")
    packages = ["numpy", "scipy", "scikit-learn", package]
    if args.method in {"leiden", "louvain"}:
        packages.append("igraph")
    versions = {p: importlib.metadata.version(p) for p in sorted(set(packages))}
    session = {"python": sys.version, "platform": platform.platform(), "packages": versions,
               "options": {k: str(v) if isinstance(v, Path) else v for k, v in vars(args).items()},
               "input_space": "supplied weighted undirected graph" if graph_input else "supplied PCA coordinates; no automatic normalization", "observations": len(ids)}
    if args.method == "umap":
        result = engine.UMAP(n_neighbors=args.neighbors, n_components=args.components,
                             min_dist=args.min_dist, metric="euclidean", random_state=args.seed,
                             n_jobs=1, init="random").fit_transform(x)
        columns = [f"UMAP{i+1}" for i in range(args.components)]
        session["scope"] = "Exploratory Euclidean UMAP of supplied PCA coordinates; upstream umap-learn"
    elif args.method in {"leiden", "louvain"}:
        import igraph
        if not graph_input:
            edges, scale = knn_edges(x, ids, args.neighbors, np)
            session["weight_scale"] = scale
        ordered = sorted(edges)
        graph = igraph.Graph(n=len(ids), edges=ordered, directed=False)
        weights = [edges[e] for e in ordered]
        if args.method == "leiden":
            partition = engine.find_partition(graph, engine.RBConfigurationVertexPartition,
                weights=weights, resolution_parameter=args.resolution, seed=args.seed, n_iterations=2)
        else:
            igraph.set_random_number_generator(random.Random(args.seed))
            partition = graph.community_multilevel(weights=weights, resolution=args.resolution)
        result = np.asarray(partition.membership).reshape(-1, 1)
        columns = ["cluster"]
        with Path("graph.tsv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
            writer.writerow(["source", "target", "weight"])
            for first, second in ordered:
                writer.writerow([ids[first], ids[second], edges[(first, second)]])
        session["scope"] = ("Upstream partition of the supplied undirected graph; original positive edge weights, explicit isolates; no inferred neighborhood graph"
            if graph_input else "Upstream graph partition on an undirected union of exact Euclidean kNN edges, Gaussian weights with median-positive-distance scale; differs from Seurat SNN/Scanpy fuzzy graphs")
        session["graph_edges"] = len(ordered)
        session["isolated_nodes"] = len(ids) - len({node for edge in ordered for node in edge})
        session["objective"] = "RB configuration modularity" if args.method == "leiden" else "multilevel modularity"
    else:
        if args.metadata is None or not args.batch:
            raise ValueError("Harmony requires metadata and an explicit batch column")
        import pandas as pd
        metadata = pd.read_csv(args.metadata, sep="\t", dtype=str, keep_default_na=False)
        if "obs_id" not in metadata or args.batch not in metadata or metadata["obs_id"].duplicated().any():
            raise ValueError("Metadata requires unique obs_id and the declared batch column")
        if set(metadata["obs_id"]) != set(ids):
            raise ValueError("Metadata obs_id universe differs from the matrix")
        metadata = metadata.set_index("obs_id").loc[ids]
        if metadata[args.batch].eq("").any() or metadata[args.batch].nunique() < 2:
            raise ValueError("Harmony requires at least two explicitly labelled batches")
        fitted = engine.run_harmony(x, metadata, args.batch, random_state=args.seed, ncores=1)
        result = np.asarray(fitted.Z_corr)
        columns = rows[0][1:]
        session["scope"] = "Harmony 2.x batch adjustment of supplied PCA coordinates; no inference that batch is biologically ignorable"
    if result.shape != (len(ids), len(columns)) or not np.isfinite(result).all():
        raise ValueError("Upstream result has wrong dimensions or nonfinite values")
    with Path("results.tsv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["obs_id"] + columns)
        writer.writerows([id_] + [float(v) for v in row] for id_, row in zip(ids, result))
    Path("session.json").write_text(json.dumps(session, indent=2) + "\n", encoding="utf-8")
    return status("completed", session["scope"])


def read_matrix(rows, np):
    if len(rows) < 5 or len(rows[0]) < 3 or rows[0][0] != "obs_id":
        raise ValueError("PCA matrix requires obs_id and at least two component columns, with at least four observations")
    if len(set(rows[0])) != len(rows[0]):
        raise ValueError("Duplicate PCA component name")
    ids, values = [], []
    for row in rows[1:]:
        if len(row) != len(rows[0]) or not row[0]:
            raise ValueError("Matrix row width mismatch or blank obs_id")
        ids.append(row[0]); values.append([float(x) for x in row[1:]])
    x = np.asarray(values, dtype=np.float64)
    if len(set(ids)) != len(ids) or not np.isfinite(x).all() or x.shape[1] > 256 or x.shape[0] > 100_000:
        raise ValueError("Require unique IDs, finite coordinates, <=256 components and <=100000 observations")
    return ids, x


def knn_edges(x, ids, neighbors_count, np):
    if x.shape[0] ** 2 * x.shape[1] > 200_000_000:
        raise ValueError("Exact kNN graph construction exceeds the 200 million distance-work-unit bound; supply fewer observations/components. Use --nodes and --graph for a precomputed graph.")
    from sklearn.neighbors import NearestNeighbors
    distance, neighbors = NearestNeighbors(n_neighbors=neighbors_count + 1, n_jobs=1).fit(x).kneighbors(x)
    positive = distance[:, 1:][distance[:, 1:] > 0]
    scale = float(np.median(positive)) if positive.size else 1.0
    edges = {}
    for first in range(len(ids)):
        used = 0
        for dist, second in zip(distance[first], neighbors[first]):
            second = int(second)
            if first == second:
                continue
            pair = tuple(sorted((first, second)))
            weight = max(float(np.exp(-0.5 * (float(dist) / scale) ** 2)), np.finfo(float).tiny)
            edges[pair] = max(weight, edges.get(pair, 0))
            used += 1
            if used == neighbors_count:
                break
    return edges, scale


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as failure:
        traceback.print_exc()
        raise SystemExit(status("failed", str(failure), "Correct the documented input/options or resolve the recorded upstream error, then rerun to a new output directory. Inspect external.log; no successful result was published."))
