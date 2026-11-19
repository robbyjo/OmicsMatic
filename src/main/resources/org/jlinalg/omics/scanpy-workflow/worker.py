# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Closed original Scanpy CPU worker. No caller code, automatic recipe or fallback.

The Java owner captures inputs/settings/source and bounds the worker lifetime and
output. This worker uses current upstream calls, preserves their literal outputs
and warnings, and refuses admission when required numeric outputs are undefined.
"""
from pathlib import Path
import csv
import hashlib
import importlib.metadata as metadata
import inspect
import json
import math
import os
import sys
import traceback
import warnings

PROFILE = "Scanpy-1.13.0-CPU-v1"
ACTION = ("Read WARNINGS.md, worker/report.json, worker/status.json and worker logs. "
          "SKIP3: restore the named exact permitted Python/package dependency; skip its dependent analysis until then. "
          "FAIL2: no complete result is admitted; retain inputs, diagnostics and partials, correct the named cause "
          "and retry NEW output. Never repair IDs, nonfinite values, input populations, layers, editions or algorithms silently.")


class Unavailable(Exception):
    """Exact required permitted dependency is not available; no analysis admission."""


def keys(value, allowed, label):
    if not isinstance(value, dict) or set(value) - set(allowed):
        raise ValueError(f"Unknown/invalid {label} fields: {set(value) - set(allowed) if isinstance(value, dict) else type(value)}")


def identity(value):
    if not isinstance(value, str) or not value.strip() or len(value) > 4094 or any(ord(c) < 32 or ord(c) == 127 for c in value):
        raise ValueError("Require exact nonblank identities without control characters")
    return value


def write_json(path, value):
    with Path(path).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, allow_nan=False, indent=2)
        stream.write("\n")


def file_pin(path):
    with Path(path).open("rb") as stream:
        return dict(bytes=Path(path).stat().st_size, sha256=hashlib.file_digest(stream, "sha256").hexdigest())


def runtime(profile):
    if sys.version_info[:2] < tuple(profile["python_minimum"]):
        raise Unavailable("Python >=3.13 is required by this exact Scanpy profile")
    observed = {}
    for name, expected in profile["versions"].items():
        try:
            observed[name] = metadata.version(name)
        except metadata.PackageNotFoundError as error:
            raise Unavailable(f"Required {name}=={expected} is missing") from error
        if observed[name] != expected:
            raise Unavailable(f"Require {name}=={expected}; found {observed[name]}")
    distribution = metadata.distribution("scanpy")
    package = Path(distribution.locate_file("scanpy"))
    actual = {p.relative_to(package).as_posix(): file_pin(p) for p in package.rglob("*.py")}
    if actual != profile["scanpy_sources"]:
        raise ValueError("Installed Scanpy source file population/bytes differ from the packaged source profile")
    global np, pd, sp, ad, sc, h5py
    import numpy as np
    import pandas as pd
    import scipy.sparse as sp
    import anndata as ad
    import scanpy as sc
    import h5py
    sc.settings.preset = profile["preset"]
    sc.settings.n_jobs = 1
    sc.settings.verbosity = 1
    return dict(python=sys.version, executable=sys.executable, versions=observed,
                scanpy_source_files_checked=len(actual), preset=str(sc.settings.preset),
                source_admission=profile["source_admission"])


# Explicit allowlist: values are JSON data, never callables, import paths or code.
# Current 1.13 argument spellings are literal; historical names are not translated.
PARAMS = {
    "pp.calculate_qc_metrics": "expr_type var_type qc_vars percent_top log1p layer use_raw",
    "pp.filter_cells": "min_counts min_genes max_counts max_genes",
    "pp.filter_genes": "min_counts min_cells max_counts max_cells",
    "pp.normalize_total": "target_sum exclude_highly_expressed max_fraction key_added layer",
    "pp.log1p": "base layer",
    "pp.scale": "zero_center max_value layer mask_obs",
    "pp.highly_variable_genes": "layer n_top_genes min_disp max_disp min_mean max_mean span n_bins flavor subset batch_key check_values filter_unexpressed_genes",
    "experimental.pp.highly_variable_genes": "theta clip n_top_genes batch_key chunksize flavor check_values layer subset",
    "experimental.pp.normalize_pearson_residuals": "theta clip check_values layer",
    "pp.regress_out": "covariates layer n_jobs",
    "pp.pca": "n_comps zero_center svd_solver chunked chunk_size rng mask_var dtype key_added layer",
    "pp.neighbors": "n_neighbors n_pcs use_rep knn method transformer metric metric_kwds rng key_added",
    "tl.leiden": "resolution restrict_to rng directed use_weights n_iterations flavor n_jobs neighbors_key obsp key_added",
    "tl.umap": "min_dist spread n_components maxiter alpha gamma negative_sample_rate init_pos rng a b method compatibility_layout neighbors_key key_added",
    "tl.tsne": "n_pcs n_components perplexity metric early_exaggeration learning_rate rng n_jobs key_added use_rep",
    "tl.diffmap": "n_comps rng neighbors_key",
    "tl.paga": "groups use_rna_velocity model neighbors_key",
    "tl.draw_graph": "layout init_pos root rng n_jobs neighbors_key obsp key_added_ext maxiter",
    "tl.score_genes": "gene_list ctrl_as_ref ctrl_size gene_pool n_bins rng score_name use_raw layer",
    "tl.score_genes_cell_cycle": "s_genes g2m_genes rng use_raw layer ctrl_as_ref n_bins gene_pool",
    "tl.rank_genes_groups": "groupby mask_var use_raw groups reference n_genes rankby_abs pts method corr_method tie_correct mean_in_log_space key_added layer",
    "tl.embedding_density": "basis groupby components key_added",
    "pp.downsample_counts": "counts_per_cell total_counts rng replace",
    "pp.scrublet": "batch_key sim_doublet_ratio expected_doublet_rate stdev_doublet_rate synthetic_doublet_umi_subsampling knn_dist_metric normalize_variance log_transform mean_center n_prin_comps use_approx_neighbors get_doublet_neighbor_parents n_neighbors threshold verbose rng",
}
RANDOM = {"pp.pca", "pp.neighbors", "tl.leiden", "tl.umap", "tl.tsne", "tl.diffmap", "tl.draw_graph",
          "tl.score_genes", "tl.score_genes_cell_cycle", "pp.downsample_counts", "pp.scrublet"}
REQUIRED = {
    "pp.highly_variable_genes": {"flavor"},
    "experimental.pp.highly_variable_genes": {"flavor", "n_top_genes"},
    "tl.leiden": {"flavor", "directed"},
    "tl.score_genes": {"gene_list", "use_raw", "ctrl_as_ref"},
    "tl.score_genes_cell_cycle": {"s_genes", "g2m_genes", "use_raw", "ctrl_as_ref"},
    "tl.rank_genes_groups": {"groupby", "use_raw", "method", "tie_correct", "corr_method", "n_genes", "mean_in_log_space"},
    "pp.regress_out": {"covariates"},
}


def validate_step(step):
    keys(step, {"operation", "params", "input_kind"}, "step")
    operation = step.get("operation")
    params = step.get("params", {})
    specials = {"snapshot_raw": set(), "copy_layer": {"name"}, "set_X": {"layer"},
                "subset": {"axis", "field", "operator", "value", "predicate"}, "sqrt": {"layer"}}
    if operation not in PARAMS and operation not in specials:
        raise ValueError(f"Unsupported exact operation {operation!r}; no spelling/algorithm substitution")
    keys(params, PARAMS[operation].split() if operation in PARAMS else specials[operation], "operation parameters")
    required = set(REQUIRED.get(operation, ()))
    if operation in RANDOM:
        required.add("rng")
        seed = params.get("rng")
        if isinstance(seed, bool) or not isinstance(seed, int) or not 0 <= seed <= 2147483647:
            raise ValueError(f"{operation} requires explicit integer rng in [0,2147483647]")
    if required - set(params):
        raise ValueError(f"{operation} requires explicit {sorted(required - set(params))}")
    if "input_kind" in step and step["input_kind"] not in {"raw_counts", "log1p", "provided_transformed", "provided"}:
        raise ValueError("Unknown declared input_kind")
    if operation in {"pp.highly_variable_genes", "experimental.pp.highly_variable_genes", "tl.rank_genes_groups"} and "input_kind" not in step:
        raise ValueError(f"Declare input_kind for {operation}; no inferred transformation")
    if operation == "tl.rank_genes_groups":
        if params["method"] not in {"t-test", "t-test_overestim_var", "wilcoxon", "logreg"}:
            raise ValueError("Unsupported marker test; no fallback")
        if not isinstance(params["use_raw"], bool) or not isinstance(params["tie_correct"], bool) or not isinstance(params["mean_in_log_space"], bool):
            raise ValueError("Marker raw/tie/log-mean choices must be explicit booleans")
        if params["method"] != "wilcoxon" and params["tie_correct"]:
            raise ValueError("tie_correct=true is only used by Wilcoxon; unused setting refused")
    if operation in {"pp.filter_cells", "pp.filter_genes"} and sum(v is not None for v in params.values()) != 1:
        raise ValueError("Source cell/gene filtering requires exactly one non-null criterion")
    if params.get("n_jobs", 1) != 1:
        raise ValueError("This CPU profile requires n_jobs=1")
    if operation == "pp.neighbors" and params.get("transformer") not in {None, "sklearn", "pynndescent"}:
        raise ValueError("Require declared CPU neighbor transformer; no custom callable/backend")
    if operation == "tl.leiden" and params["flavor"] not in {"igraph", "leidenalg"}:
        raise ValueError("Unsupported explicit Leiden backend")
    if operation == "tl.draw_graph" and params.get("layout", "fa") == "fa":
        raise ValueError("ForceAtlas2 can trigger an upstream fallback when fa2 is absent; declare an installed supported layout such as fr")
    return operation, params


def typed_frame(path, first, columns, maximum):
    keys(columns, set(columns) if isinstance(columns, dict) else set(), "column declarations")
    with Path(path).open(encoding="utf-8", newline="") as stream:
        reader = csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE)
        header = next(reader, None)
        if header is None or not header or header[0] != first or len(header) != len(set(header)) or set(header[1:]) != set(columns):
            raise ValueError(f"Require {first} first and exact explicitly typed metadata columns")
        values = []
        for row in reader:
            if len(row) != len(header) or len(values) >= maximum:
                raise ValueError("Invalid metadata width or complete population exceeds capacity")
            for item in row:
                identity(item)
            values.append(row)
    identifiers = [r[0] for r in values]
    if not identifiers or len(set(identifiers)) != len(identifiers):
        raise ValueError("Empty/duplicate exact input identities")
    frame = pd.DataFrame([r[1:] for r in values], index=pd.Index(identifiers, name=first), columns=header[1:])
    for name, kind in columns.items():
        if kind == "float64":
            frame[name] = np.array([float(s) for s in frame[name]], dtype=np.float64)
            if not np.isfinite(frame[name].values).all():
                raise ValueError("Nonfinite numeric metadata")
        elif kind == "bool":
            if not frame[name].isin(["true", "false"]).all():
                raise ValueError("Boolean metadata must be literal true/false")
            frame[name] = frame[name].eq("true")
        elif kind == "string":
            frame[name] = frame[name].astype(object)
        elif isinstance(kind, dict):
            keys(kind, {"levels", "ordered"}, "categorical column")
            levels = kind.get("levels")
            if not isinstance(levels, list) or not levels or len(levels) != len(set(levels)) or any(identity(v) != v for v in levels):
                raise ValueError("Declare unique exact category levels")
            if not frame[name].isin(levels).all() or not isinstance(kind.get("ordered", False), bool):
                raise ValueError("Metadata category outside declared levels")
            frame[name] = pd.Categorical(frame[name], categories=levels, ordered=kind.get("ordered", False))
        else:
            raise ValueError(f"Unsupported explicit metadata kind {kind!r}")
    return frame


def read_market(path, shape, limits):
    rows, cols, values, seen = [], [], [], set()
    with Path(path).open(encoding="ascii") as stream:
        if stream.readline().strip() not in {"%%MatrixMarket matrix coordinate real general", "%%MatrixMarket matrix coordinate integer general"}:
            raise ValueError("Require general real/integer coordinate MatrixMarket, explicitly read as float64")
        line = next((line for line in stream if not line.startswith("%")), None)
        if line is None:
            raise ValueError("Missing MatrixMarket dimensions")
        dimensions = line.split()
        if len(dimensions) != 3:
            raise ValueError("Invalid MatrixMarket dimensions")
        nr, nc, count = map(int, dimensions)
        if (nr, nc) != shape or count < 0 or count > limits["maximum_entries"]:
            raise ValueError("MatrixMarket complete shape/stored population differs or exceeds capacity")
        if count * 192 + (nr + nc) * 64 > limits["maximum_working_bytes"]:
            raise ValueError("Complete coordinate intake estimate exceeds maximum_working_bytes")
        for line in stream:
            fields = line.split()
            if len(fields) != 3 or len(values) >= count:
                raise ValueError("Invalid/excess MatrixMarket entries")
            i, j = int(fields[0])-1, int(fields[1])-1
            value = float(fields[2])
            if not 0 <= i < nr or not 0 <= j < nc or not math.isfinite(value) or (i, j) in seen:
                raise ValueError("Invalid/nonfinite/duplicate coordinate; no summation or repair")
            seen.add((i, j));rows.append(i);cols.append(j);values.append(value)
        if len(values) != count:
            raise ValueError("Missing declared MatrixMarket entries")
    return sp.coo_matrix((np.array(values, dtype=np.float64), (rows, cols)), shape=shape).tocsr()


def read_input(root, request):
    data = request["input"];limits = request["limits"]
    if data["format"] == "matrix_market":
        obs = typed_frame(root/data["cells"], "cell", data.get("obs_columns", {}), limits["maximum_cells"])
        var = typed_frame(root/data["features"], "gene", data.get("var_columns", {}), limits["maximum_features"])
        if data.get("dtype") != "float64" or data["orientation"] not in {"cells_by_genes", "features_by_cells"}:
            raise ValueError("MatrixMarket requires declared float64 dtype and exact orientation")
        shape = (len(obs), len(var))
        matrix = read_market(root/data["matrix"], shape if data["orientation"] == "cells_by_genes" else shape[::-1], limits)
        if data["orientation"] == "features_by_cells":
            matrix = matrix.T.tocsr()
        return ad.AnnData(matrix, obs=obs, var=var)
    path = root/data["matrix"]
    # Bound decompressed datasets before the eager original AnnData reader.
    with h5py.File(path, "r") as handle:
        addresses = set()
        def links(group):
            for name in group:
                if not isinstance(group.get(name, getlink=True), h5py.HardLink):
                    raise ValueError("H5AD external/soft links are not self-contained captured inputs")
                node = group[name];address = h5py.h5o.get_info(node.id).addr
                if address in addresses:raise ValueError("H5AD aliased/cyclic hard links are outside this self-contained input contract")
                addresses.add(address)
                if isinstance(node, h5py.Group):links(node)
        links(handle)
        total = [0]
        def visit(name, node):
            if isinstance(node, h5py.Dataset):
                # AnnData encodes legitimate None-valued .uns parameters as
                # HDF5 null dataspaces. They allocate no elements; they are not
                # zero-valued expression or a repaired missing numeric array.
                size = 0 if node.shape is None else math.prod(node.shape)
                if size > max(limits["maximum_entries"], limits["maximum_dense_entries"]):
                    raise ValueError(f"H5AD decompressed dataset capacity exceeded: {name}")
                total[0] += size * max(node.dtype.itemsize, 16)
                if total[0] > limits["maximum_working_bytes"]:
                    raise ValueError("H5AD decompressed dataset workspace capacity exceeded")
        handle.visititems(visit)
    return ad.read_h5ad(path)


def numeric_check(value, label, limits):
    if sp.issparse(value):
        if not sp.isspmatrix_csr(value) and not sp.isspmatrix_csc(value):
            raise ValueError(f"Require explicit CSR/CSC sparse storage: {label}")
        if value.nnz > limits["maximum_entries"]:
            raise ValueError(f"Stored sparse capacity exceeded: {label}")
        pointers, indices = value.indptr, value.indices
        bound = value.shape[1] if sp.isspmatrix_csr(value) else value.shape[0]
        if pointers[0] != 0 or pointers[-1] != len(indices) or len(indices) != len(value.data) or (np.diff(pointers) < 0).any() or (indices < 0).any() or (indices >= bound).any():
            raise ValueError(f"Invalid sparse coordinates: {label}")
        for start, end in zip(pointers[:-1], pointers[1:]):
            block = indices[start:end]
            if len(set(block.tolist())) != len(block):raise ValueError(f"Duplicate sparse coordinates: {label}; no summation or repair")
        data = value.data
    else:
        data = np.asarray(value)
        if data.size > limits["maximum_dense_entries"]:
            raise ValueError(f"Complete dense population exceeds capacity: {label}")
    if data.dtype.kind in "fc" and not np.isfinite(data).all():
        count = int(np.count_nonzero(~np.isfinite(data)))
        raise ArithmeticError(f"{label} has {count} nonfinite source values; diagnostic partial retained, no complete result")
    if data.dtype.kind == "c":
        raise ValueError(f"Complex representation unsupported: {label}")


def matrix_bytes(value):
    return int(value.data.nbytes + value.indices.nbytes + value.indptr.nbytes) if sp.issparse(value) else int(np.asarray(value).nbytes)


def resident_bytes(data):
    total = matrix_bytes(data.X)
    for slot in ["layers", "obsm", "varm", "obsp", "varp"]:
        total += sum(matrix_bytes(value) for name, value in getattr(data, slot).items() if not (slot == "layers" and name is None))
    if data.raw is not None:total += matrix_bytes(data.raw.X)
    total += int(data.obs.memory_usage(index=True, deep=True).sum() + data.var.memory_usage(index=True, deep=True).sum())
    return total


def check_data(data, limits):
    if not 0 < data.n_obs <= limits["maximum_cells"] or not 0 < data.n_vars <= limits["maximum_features"]:
        raise ValueError("Empty or excessive complete cell/gene population; no downstream result admitted")
    for index in [data.obs_names, data.var_names]:
        if not index.is_unique:
            raise ValueError("Duplicate identities; no make_unique repair")
        for value in index:
            identity(value)
    if resident_bytes(data) > limits["maximum_working_bytes"]:
        raise ValueError("Complete resident matrix/metadata population exceeds maximum_working_bytes")
    numeric_check(data.X, "X", limits)
    for slot in ["layers", "obsm", "varm", "obsp", "varp"]:
        for name, value in getattr(data, slot).items():
            numeric_check(value, slot+"/"+str(name), limits)
    if data.raw is not None:
        if data.raw.n_vars > limits["maximum_features"] or not data.raw.var_names.is_unique:
            raise ValueError("Raw representation has excessive/duplicate features")
        for name in data.raw.var_names:identity(name)
        numeric_check(data.raw.X, "raw.X", limits)
    for slot in ["obs", "var"]:
        frame = getattr(data, slot)
        for name in frame:
            identity(name)
            if pd.api.types.is_numeric_dtype(frame[name].dtype):
                numeric_check(frame[name].to_numpy(), slot+"/"+str(name), limits)
            else:
                for value in frame[name]:
                    if isinstance(value, str) and any(ord(c) < 32 or ord(c) == 127 for c in value):
                        raise ValueError("Metadata control characters are unsupported by the exported one-record-per-line TSV contract")


def predicate(frame, declaration, depth=0):
    if depth > 12:raise ValueError("Subset predicate nesting capacity exceeded")
    if not isinstance(declaration, dict):raise ValueError("Require explicit subset predicate object")
    if set(declaration) == {"not"}:return ~predicate(frame, declaration["not"], depth+1)
    if set(declaration) in [{"all"}, {"any"}]:
        key = next(iter(declaration));parts = declaration[key]
        if not isinstance(parts, list) or not 1 <= len(parts) <= 64:raise ValueError("Require 1..64 explicit predicate clauses")
        masks = [predicate(frame, part, depth+1) for part in parts]
        result = masks[0]
        for mask in masks[1:]:result = result & mask if key == "all" else result | mask
        return result
    if set(declaration) != {"field", "operator", "value"}:raise ValueError("Require exact leaf predicate fields")
    column=frame[declaration["field"]];value=declaration["value"];operator=declaration["operator"]
    if column.isna().any():raise ValueError("Missing values in selected predicate field; no implicit missing-value exclusion")
    if operator == "in":return column.isin(value)
    if operator == "not_in":return ~column.isin(value)
    if operator == "eq":return column == value
    if operator == "lt":return column < value
    if operator == "le":return column <= value
    if operator == "gt":return column > value
    if operator == "ge":return column >= value
    raise ValueError("Unknown literal subset operator")


def matrix_summary(data):
    matrix = data.X
    digest = hashlib.sha256()
    digest.update(str(matrix.dtype).encode());digest.update(str(matrix.shape).encode())
    for index in [data.obs_names, data.var_names]:
        for item in index:
            raw = item.encode("utf-8");digest.update(len(raw).to_bytes(8, "little"));digest.update(raw)
    if sp.issparse(matrix):
        storage = matrix.format
        for array in [matrix.indptr, matrix.indices, matrix.data]:
            digest.update(str(array.dtype).encode());digest.update(np.ascontiguousarray(array).tobytes())
        stored = int(matrix.nnz)
    else:
        storage = "dense";stored = int(matrix.size);digest.update(np.ascontiguousarray(matrix).tobytes())
    return dict(cells=data.n_obs, features=data.n_vars, stored_values=stored, dtype=str(matrix.dtype), storage=storage,
                matrix_and_identity_sha256=digest.hexdigest(), raw_present=data.raw is not None, layers=list(data.layers))


def run_step(data, step, limits):
    operation, params = validate_step(step)
    dense = data.n_obs * data.n_vars
    if operation in {"pp.scale", "pp.regress_out", "experimental.pp.normalize_pearson_residuals", "pp.pca"} and dense > limits["maximum_dense_entries"]:
        raise ValueError("Declared operation may materialize a dense matrix exceeding maximum_dense_entries")
    workspace = 64*dense if operation in {"pp.scale", "pp.regress_out", "experimental.pp.normalize_pearson_residuals", "pp.pca", "tl.rank_genes_groups", "pp.scrublet"} else 8*matrix_bytes(data.X)
    if resident_bytes(data) + workspace > limits["maximum_working_bytes"]:
        raise ValueError("Estimated complete operation workspace exceeds maximum_working_bytes")
    work = dense
    if operation in {"pp.pca", "pp.scrublet"}:
        work *= params.get("n_comps", params.get("n_prin_comps", 50)) or 50
    if operation in {"pp.neighbors", "tl.tsne", "tl.embedding_density"}:
        work = data.n_obs**2 * (params.get("n_pcs", 50) or 50)
    if work > limits["maximum_work"]:
        raise ValueError("Estimated complete operation exceeds maximum_work; no subsampling/truncation")
    if operation == "snapshot_raw":
        if data.raw is not None:
            raise ValueError("Raw snapshot already exists; no silent overwrite")
        data.raw = data.copy();return data, None
    if operation == "copy_layer":
        name = identity(params.get("name"))
        if name in data.layers:
            raise ValueError("Layer already exists; no silent overwrite")
        data.layers[name] = data.X.copy();return data, None
    if operation == "set_X":
        name = identity(params.get("layer"))
        if name not in data.layers:
            raise ValueError("Named layer unavailable")
        data.X = data.layers[name].copy();return data, None
    if operation == "sqrt":
        layer = params.get("layer")
        value = data.X if layer is None else data.layers[layer]
        if sp.issparse(value):
            value = value.copy();value.data = np.sqrt(value.data)
        else:
            value = np.sqrt(value)
        if layer is None:data.X = value
        else:data.layers[layer] = value
        return data, None
    if operation == "subset":
        if set(params) not in [{"axis", "field", "operator", "value"}, {"axis", "predicate"}] or params["axis"] not in {"cells", "genes"}:
            raise ValueError("Subset requires explicit axis and a literal predicate")
        frame = data.obs if params["axis"] == "cells" else data.var
        tree = params.get("predicate", {key:value for key,value in params.items() if key != "axis"})
        mask = predicate(frame, tree)
        if mask.isna().any():raise ValueError("Undefined subset predicate")
        data = data[mask.to_numpy(), :].copy() if params["axis"] == "cells" else data[:, mask.to_numpy()].copy()
        return data, dict(retained=int(mask.sum()), excluded=int((~mask).sum()), literal_predicate=tree)
    function = sc
    for part in operation.split("."):
        function = getattr(function, part)
    if "layer" in params and params["layer"] is not None and params["layer"] not in data.layers:
        raise ValueError("Named layer unavailable")
    if params.get("use_raw") is True and data.raw is None:
        raise ValueError("Explicit use_raw requires an existing captured raw snapshot")
    # These package operations mutate AnnData under the selected v1 preset.
    executed = dict(params)
    if "out" in inspect.signature(function).parameters:
        executed["out"] = False
    result = function(data, **executed)
    if result is not None:
        raise ValueError(f"Unexpected original return contract for {operation}; no result discarded")
    details = dict(signature=str(inspect.signature(function)), provided_params=params, executed_params=executed,
                   worker_control="out=False explicitly writes original results into the captured AnnData; no source return object discarded",
                   omitted_parameters="Literal defaults of the pinned current source under scanpy-v1; not inferred author settings")
    return data, details


def metadata_schema(frame):
    return [dict(name=str(name), kind="boolean" if pd.api.types.is_bool_dtype(frame[name].dtype) else
                 "numeric" if pd.api.types.is_numeric_dtype(frame[name].dtype) else "text", dtype=str(frame[name].dtype)) for name in frame]


def export(data, destination, steps):
    from scipy.io import mmwrite
    destination.mkdir(exist_ok=False)
    data.write_h5ad(destination/"result.h5ad", compression="gzip")
    data.obs.to_csv(destination/"cells.tsv", sep="\t", index_label="cell", float_format="%.17g", lineterminator="\n")
    data.var.to_csv(destination/"features.tsv", sep="\t", index_label="gene", float_format="%.17g", lineterminator="\n")
    mmwrite(destination/"matrix.mtx", sp.coo_matrix(data.X.T), field="real", symmetry="general", precision=17)
    exports = []
    for slot in ["obsm", "varm", "obsp", "varp"]:
        for key, value in getattr(data, slot).items():
            number = len(exports);sparse = sp.issparse(value);name = f"array-{number:04d}."+("mtx" if sparse else "tsv")
            if sparse:mmwrite(destination/name, value, field="real", symmetry="general", precision=17)
            else:np.savetxt(destination/name, np.asarray(value), delimiter="\t", fmt="%.17g")
            exports.append(dict(slot=slot, key=key, path=name, shape=list(value.shape), storage="sparse" if sparse else "dense"))
    marker_keys = []
    for step in steps:
        if step["operation"] == "tl.rank_genes_groups":
            key = step.get("params", {}).get("key_added", "rank_genes_groups")
            if key not in marker_keys:marker_keys.append(key)
    for index, key in enumerate(marker_keys):
        table = sc.get.rank_genes_groups_df(data, group=None, key=key)
        for column in table:
            if pd.api.types.is_numeric_dtype(table[column].dtype) and not np.isfinite(table[column].to_numpy()).all():
                raise ArithmeticError(f"Marker table {key}/{column} contains nonfinite source values; no complete result")
        name = f"markers-{index:04d}.tsv"
        table.to_csv(destination/name, sep="\t", index=False, float_format="%.17g", lineterminator="\n")
        exports.append(dict(slot="uns", key=key, path=name, rows=len(table), source="scanpy.get.rank_genes_groups_df without filtering"))
    return exports


def main(root):
    root = Path(root).resolve();worker = root/"worker"
    if (worker/"status.json").exists():raise ValueError("Worker output already exists; use NEW analysis output")
    cache = worker/"cache";cache.mkdir(exist_ok=False)
    for key, name in [("HOME", "home"), ("TMPDIR", "tmp"), ("NUMBA_CACHE_DIR", "numba"), ("MPLCONFIGDIR", "mpl"), ("XDG_CACHE_HOME", "xdg"), ("XDG_CONFIG_HOME", "config")]:
        directory = cache/name;directory.mkdir();os.environ[key] = str(directory)
    for key in ["OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS", "NUMBA_NUM_THREADS", "VECLIB_MAXIMUM_THREADS", "NUMEXPR_NUM_THREADS"]:
        os.environ[key] = "1"
    report = dict(profile=PROFILE, results_admitted=False, warnings=[], steps=[], required_action=ACTION)
    code = 2;data = None
    try:
        request = json.loads((root/"effective-request.json").read_text(encoding="utf-8"))
        profile = json.loads((root/"source/SOURCE-PROFILE.json").read_text(encoding="utf-8"))
        if request["profile"] != PROFILE or profile["profile"] != PROFILE:raise ValueError("Unknown profile")
        for step in request["steps"]:validate_step(step)
        report["runtime"] = runtime(profile)
        data = read_input(root, request);check_data(data, request["limits"])
        report["input"] = matrix_summary(data)
        (worker/"steps").mkdir(exist_ok=False)
        for index, step in enumerate(request["steps"]):
            record = dict(index=index, operation=step["operation"], declaration=step, before=matrix_summary(data))
            with warnings.catch_warnings(record=True) as captured:
                warnings.simplefilter("always")
                try:
                    data, detail = run_step(data, step, request["limits"])
                    check_data(data, request["limits"])
                    record.update(status="completed", after=matrix_summary(data), details=detail)
                finally:
                    if "status" not in record:record["status"] = "failed-no-result"
                    record["warnings"] = [dict(category=w.category.__name__, message=str(w.message)) for w in captured]
                    report["warnings"].extend(record["warnings"])
                    report["steps"].append(record)
                    write_json(worker/"steps"/f"{index:04d}.json", record)
        report["exports"] = export(data, worker/"outputs", request["steps"])
        report["metadata"] = dict(cells=metadata_schema(data.obs), features=metadata_schema(data.var))
        report["output"] = matrix_summary(data);report["results_admitted"] = True;code = 0
    except (Unavailable, ModuleNotFoundError, ImportError) as error:
        code = 3;report["error"] = str(error);report["traceback"] = traceback.format_exc()
    except Exception as error:
        code = 2;report["error"] = str(error);report["traceback"] = traceback.format_exc()
    finally:
        if code and data is not None:
            try:
                data.write_h5ad(worker/"partial.h5ad", compression="gzip")
                report["partial"] = "partial.h5ad: diagnostic only, not an admitted result"
            except Exception as error:report["partial_capture_error"] = str(error)
        report["status"] = "completed-with-warnings" if code == 0 else "SKIP3_DEPENDENT_ANALYSIS_SKIPPED" if code == 3 else "FAIL2_NO_COMPLETE_RESULT"
        write_json(worker/"report.json", report)
        write_json(worker/"status.json", {key:report[key] for key in ["status", "results_admitted", "required_action"]})
    print(report["status"]+": "+report.get("error", "original ordered Scanpy calls completed"))
    return code


if __name__ == "__main__":
    if len(sys.argv) != 2:raise SystemExit("Require the captured analysis directory")
    raise SystemExit(main(sys.argv[1]))
