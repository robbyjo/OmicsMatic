"""Typed file boundaries around unchanged public Squidpy functions.

This module does not supervise a process or admit a captured analysis. The Java
workflow owns those operations. Every original frame is exported before any
native admission policy is considered.
"""
from pathlib import Path
import csv
import inspect
import json
import math


def read_json(path):
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result:
                raise ValueError("Duplicate JSON key: " + key)
            result[key] = value
        return result
    with Path(path).open(encoding="utf-8") as stream:
        return json.load(stream, object_pairs_hook=pairs,
                         parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonstandard JSON number: " + value)))


def write_json(path, value):
    with Path(path).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
        stream.write("\n")


def require_keys(value, allowed, required=()):
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise ValueError("Unexpected or missing fields; allowed=" + str(sorted(allowed)) + "; required=" + str(sorted(required)))


def text(value):
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ValueError("Require a nonempty literal identifier/path without NUL")
    return value


def number(value):
    if isinstance(value, bool):
        raise ValueError("Boolean is not a numeric matrix value")
    if isinstance(value, (int, float)):
        return value
    if value in ("NaN", "Inf", "-Inf"):
        return {"NaN": float("nan"), "Inf": float("inf"), "-Inf": -float("inf")}[value]
    raise ValueError("Require a number or an explicit NaN/Inf/-Inf token")


def scalar(value):
    """Typed values distinguish source missing objects from literal identifier text."""
    import numpy as np
    import pandas as pd
    if value is pd.NA:
        return {"kind": "pandas.NA"}
    if value is pd.NaT:
        return {"kind": "pandas.NaT"}
    if value is None:
        return {"kind": "None"}
    if isinstance(value, (bool, np.bool_)):
        return {"kind": "bool", "value": bool(value)}
    if isinstance(value, (int, np.integer)):
        return {"kind": "integer", "value": str(value)}
    if isinstance(value, (float, np.floating)):
        value = float(value)
        token = repr(value) if math.isfinite(value) else "NaN" if math.isnan(value) else "Inf" if value > 0 else "-Inf"
        return {"kind": "float", "value": token}
    if isinstance(value, str):
        return {"kind": "string", "value": value}
    if isinstance(value, tuple):
        return {"kind": "tuple", "value": [scalar(v) for v in value]}
    raise TypeError("Unsupported source scalar type; retain partials: " + type(value).__name__)


def export_frame(frame, directory):
    import pandas as pd
    directory = Path(directory)
    directory.mkdir()
    schema = {"schema_version": 1, "rows": len(frame), "columns": len(frame.columns),
              "index_levels": frame.index.nlevels, "column_levels": frame.columns.nlevels,
              "index_names": [scalar(v) for v in frame.index.names],
              "column_names": [scalar(v) for v in frame.columns.names],
              "column_labels": [scalar(v) for v in frame.columns],
              "dtypes": [str(v) for v in frame.dtypes]}
    schema["categories"] = {}
    for position, dtype in enumerate(frame.dtypes):
        if isinstance(dtype, pd.CategoricalDtype):
            schema["categories"][str(position)] = dict(values=[scalar(v) for v in dtype.categories], ordered=dtype.ordered)
    write_json(directory / "schema.json", schema)
    with (directory / "rows.jsonl").open("x", encoding="utf-8", newline="\n") as stream:
        # Positional column access avoids mixed-row coercion by DataFrame.iterrows().
        for i, index in enumerate(frame.index):
            row = dict(position=i, index=scalar(index), cells=[scalar(frame.iloc[i, j]) for j in range(frame.shape[1])])
            stream.write(json.dumps(row, ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n")
    return schema


def matrix(spec, limits):
    import numpy as np
    import scipy.sparse as sparse
    require_keys(spec, {"format", "shape", "dtype", "values", "indptr", "indices", "data"}, {"format", "shape", "dtype"})
    shape = spec["shape"]
    if not isinstance(shape, list) or len(shape) != 2 or any(type(v) is not int or v < 0 for v in shape):
        raise ValueError("Require a nonnegative two-dimensional matrix shape")
    if shape[0] > limits["maximum_cells"] or shape[1] > max(limits["maximum_cells"], limits["maximum_features"]):
        raise ValueError("Declared matrix dimension exceeds capacity")
    if spec["dtype"] not in ("float64", "float32", "int64", "int32"):
        raise ValueError("Declare float64/float32/int64/int32 matrix precision")
    dtype = np.dtype(spec["dtype"])
    def values(items):
        if not isinstance(items, list) or len(items) > limits["maximum_entries"]:
            raise ValueError("Matrix entry population exceeds capacity")
        converted = [number(v) for v in items]
        if dtype.kind == "i" and any(not isinstance(v, int) or not np.iinfo(dtype).min <= v <= np.iinfo(dtype).max for v in converted):
            raise ValueError("Integer matrix values must be exact integers in the declared range")
        result = np.asarray(converted, dtype=dtype)
        if any(isinstance(v, (int, float)) and math.isfinite(v) and not np.isfinite(a) for v, a in zip(converted, result)):
            raise ValueError("Finite input overflows declared matrix precision; no repair")
        return result
    if spec["format"] == "dense":
        require_keys(spec, {"format", "shape", "dtype", "values"}, {"values"})
        if shape[0] * shape[1] > limits["maximum_dense_entries"]:
            raise ValueError("Dense matrix capacity exceeded")
        if not isinstance(spec["values"], list) or len(spec["values"]) != shape[0] or any(not isinstance(r, list) or len(r) != shape[1] for r in spec["values"]):
            raise ValueError("Dense values differ from their declared shape")
        return values([v for row in spec["values"] for v in row]).reshape(shape)
    if spec["format"] != "csr":
        raise ValueError("Unknown matrix format")
    require_keys(spec, {"format", "shape", "dtype", "indptr", "indices", "data"}, {"indptr", "indices", "data"})
    pointers, indices = spec["indptr"], spec["indices"]
    if not isinstance(pointers, list) or not isinstance(indices, list) or len(pointers) != shape[0] + 1 or pointers[0] != 0 or pointers[-1] != len(indices) or len(spec["data"]) != len(indices):
        raise ValueError("Invalid CSR dimensions")
    if any(type(v) is not int for v in pointers + indices) or any(a < 0 or a > b or b > len(indices) for a, b in zip(pointers, pointers[1:])):
        raise ValueError("Invalid CSR row pointers/index type")
    for i in range(shape[0]):
        row = indices[pointers[i]:pointers[i + 1]]
        if any(j < 0 or j >= shape[1] for j in row) or any(a >= b for a, b in zip(row, row[1:])):
            raise ValueError("Prepared CSR must have sorted unique in-range columns; no sorting/deduplication")
    return sparse.csr_matrix((values(spec["data"]), np.asarray(indices, dtype=np.int64), np.asarray(pointers, dtype=np.int64)), shape=tuple(shape))


def metadata_frame(spec, names):
    import numpy as np
    import pandas as pd
    frame = pd.DataFrame(index=names)
    if not isinstance(spec, dict) or len(spec) > 256:
        raise ValueError("Metadata must declare at most256 typed columns")
    for name, column in spec.items():
        text(name)
        require_keys(column, {"kind", "values", "categories", "ordered"}, {"kind", "values"})
        values = column["values"]
        if not isinstance(values, list) or len(values) != len(names):
            raise ValueError("Metadata column length differs from observation/feature order")
        if column["kind"] == "categorical":
            categories = column["categories"]
            if not isinstance(categories, list) or len(set(categories)) != len(categories) or any(not isinstance(v, str) for v in categories):
                raise ValueError("Require ordered unique literal category labels")
            if any(v is not None and v not in categories for v in values) or type(column.get("ordered", False)) is not bool:
                raise ValueError("Metadata category value is undeclared; no conversion to missing")
            frame[name] = pd.Categorical(values, categories=categories, ordered=column.get("ordered", False))
        elif column["kind"] == "string":
            if any(v is not None and not isinstance(v, str) for v in values):
                raise ValueError("Literal string column contains a nonstring")
            frame[name] = pd.Series(values, index=frame.index, dtype=object)
        elif column["kind"] == "bool":
            if any(type(v) is not bool for v in values):
                raise ValueError("Boolean column contains a nonboolean")
            frame[name] = np.asarray(values, dtype=bool)
        elif column["kind"] in ("float64", "int64"):
            parsed = [number(v) for v in values]
            if column["kind"] == "int64" and any(type(v) is not int or not -(2 ** 63) <= v < 2 ** 63 for v in parsed):
                raise ValueError("Integer metadata must be exact int64")
            frame[name] = np.asarray(parsed, dtype=column["kind"])
        else:
            raise ValueError("Unknown metadata kind")
    return frame


def dataset(spec, limits, anndata_version):
    import anndata as ad
    import numpy as np
    require_keys(spec, {"schema_version", "obs_names", "var_names", "X", "obs", "var", "obsm", "obsp", "layers", "raw", "uns"}, {"schema_version", "obs_names", "var_names", "X"})
    if spec["schema_version"] != 1:
        raise ValueError("Unknown prepared-data schema")
    obs, var = spec["obs_names"], spec["var_names"]
    if not isinstance(obs, list) or not isinstance(var, list) or any(not isinstance(v, str) or not v or "\x00" in v for v in obs + var):
        raise ValueError("Observation and feature IDs must remain literal nonempty strings")
    if not obs or not var or len(obs) > limits["maximum_cells"] or len(var) > limits["maximum_features"]:
        raise ValueError("Prepared data dimensions exceed capacity or are empty")
    x = matrix(spec["X"], limits)
    if x.shape != (len(obs), len(var)):
        raise ValueError("Expression shape differs from declared IDs")
    kwargs = dict(X=x, obs=metadata_frame(spec.get("obs", {}), obs), var=metadata_frame(spec.get("var", {}), var), uns=spec.get("uns", {}))
    if anndata_version == "0.8.0":
        kwargs["dtype"] = x.dtype
    data = ad.AnnData(**kwargs)
    if data.X.dtype != x.dtype:
        raise ValueError("AnnData changed declared expression precision; no silent conversion")
    for field in ("obsm", "obsp", "layers"):
        values = spec.get(field, {})
        if not isinstance(values, dict) or len(values) > 64:
            raise ValueError("Excessive/untyped matrix slots")
        for name, definition in values.items():
            getattr(data, field)[text(name)] = matrix(definition, limits)
    if "raw" in spec:
        raw = spec["raw"]
        require_keys(raw, {"var_names", "var", "X"}, {"var_names", "X"})
        raw_spec = dict(schema_version=1, obs_names=obs, var_names=raw["var_names"], X=raw["X"], var=raw.get("var", {}))
        data.raw = dataset(raw_spec, limits, anndata_version)
    return data


def read_input(definition, limits, anndata_version):
    """Load explicitly declared self-contained data, without identifier repair."""
    import anndata as ad
    import numpy as np
    import scipy.sparse as sparse
    require_keys(definition, {"format", "file", "matrix", "cells", "features", "orientation", "dtype", "obs_columns", "var_columns"}, {"format"})
    if definition["format"] == "prepared_json":
        require_keys(definition, {"format", "file"}, {"file"})
        return dataset(read_json(definition["file"]), limits, anndata_version)
    if definition["format"] == "h5ad":
        require_keys(definition, {"format", "file"}, {"file"})
        import h5py
        # Reject hidden file dependencies before the original reader can follow them.
        with h5py.File(definition["file"], "r") as source:
            seen = set()
            estimate = [0]
            def visit(group):
                identity = h5py.h5o.get_info(group.id).addr
                if identity in seen:
                    return
                seen.add(identity)
                for name in group:
                    if not isinstance(group.get(name, getlink=True), h5py.HardLink):
                        raise ValueError("H5AD contains a soft/external link; provide a self-contained input and retain this file")
                    node = group[name]
                    if isinstance(node, h5py.Group):
                        visit(node)
                    else:
                        estimate[0] += math.prod(node.shape) * node.dtype.itemsize
                        if estimate[0] > limits["maximum_working_bytes"]:
                            raise ValueError("Declared H5AD array allocation estimate exceeds capacity")
            visit(source)
        data = ad.read_h5ad(definition["file"])
        check_data_size(data, limits)
        return data
    if definition["format"] != "matrix_market":
        raise ValueError("Unknown declared data input format")
    require_keys(definition, {"format", "matrix", "cells", "features", "orientation", "dtype", "obs_columns", "var_columns"}, {"matrix", "cells", "features", "orientation", "dtype"})
    import gzip
    from scipy.io import mmread
    opener = gzip.open if str(definition["matrix"]).endswith(".gz") else open
    with opener(definition["matrix"], "rt", encoding="ascii") as stream:
        header = stream.readline(4096).strip().lower().split()
        if len(header) != 5 or header[:3] != ["%%matrixmarket", "matrix", "coordinate"] or header[3] not in ("real", "integer") or header[4] != "general":
            raise ValueError("Declare a general coordinate real/integer Matrix Market input")
        line = stream.readline(4096)
        while line.startswith("%"):
            line = stream.readline(4096)
        dimensions = [int(v) for v in line.split()]
        if len(dimensions) != 3 or min(dimensions) < 0 or max(dimensions[:2]) > max(limits["maximum_cells"], limits["maximum_features"]) or dimensions[2] > limits["maximum_entries"]:
            raise ValueError("Matrix Market dimensions exceed capacity")
        if dimensions[2] * 32 > limits["maximum_working_bytes"]:
            raise ValueError("Sparse allocation estimate exceeds capacity")
    x = mmread(definition["matrix"])
    if not sparse.issparse(x):
        raise ValueError("Expected coordinate sparse data")
    original_entries = x.nnz
    x = x.tocsr()
    if x.nnz != original_entries:
        raise ValueError("CSR conversion would combine duplicate coordinates; preserve input and provide a justified prepared matrix")
    if definition["orientation"] == "features_by_cells":
        x = x.T.tocsr()
    elif definition["orientation"] != "cells_by_genes":
        raise ValueError("Declare the Matrix Market orientation explicitly")
    if definition["dtype"] not in ("float64", "float32", "int64", "int32"):
        raise ValueError("Declare matrix precision")
    dtype = np.dtype(definition["dtype"])
    if dtype.kind == "i" and (not np.isfinite(x.data).all() or np.any(x.data != np.trunc(x.data)) or np.any(x.data < np.iinfo(dtype).min) or np.any(x.data > np.iinfo(dtype).max)):
        raise ValueError("Requested integer storage would truncate or overflow original values")
    before_finite = np.isfinite(x.data)
    x = x.astype(dtype)
    if np.any(before_finite & ~np.isfinite(x.data)):
        raise ValueError("Requested precision overflows finite values")
    def table(path, first, definitions):
        with Path(path).open(encoding="utf-8", newline="") as stream:
            reader = csv.reader(stream, delimiter="\t")
            names = next(reader)
            if not names or names[0] != first or len(set(names)) != len(names) or set(names[1:]) != set(definitions):
                raise ValueError("Declare every metadata column and preserve its header")
            rows = list(reader)
        if any(len(row) != len(names) for row in rows):
            raise ValueError("Metadata row width differs from header")
        ids = [text(row[0]) for row in rows]
        columns = {}
        for j, name in enumerate(names[1:], 1):
            declaration = dict(definitions[name])
            values = [row[j] for row in rows]
            if declaration["kind"] == "bool":
                if any(v not in ("true", "false") for v in values):
                    raise ValueError("Boolean metadata must use literal true/false")
                values = [v == "true" for v in values]
            elif declaration["kind"] in ("float64", "int64"):
                values = [float(v) if declaration["kind"] == "float64" else int(v) for v in values]
            declaration["values"] = values
            columns[name] = declaration
        return ids, metadata_frame(columns, ids)
    cells, obs = table(definition["cells"], "cell_id", definition.get("obs_columns", {}))
    features, var = table(definition["features"], "feature_id", definition.get("var_columns", {}))
    if x.shape != (len(cells), len(features)):
        raise ValueError("Matrix orientation/dimensions disagree with original identifier files")
    kwargs = dict(X=x, obs=obs, var=var)
    if anndata_version == "0.8.0":
        kwargs["dtype"] = x.dtype
    data = ad.AnnData(**kwargs)
    if data.X.dtype != x.dtype:
        raise ValueError("AnnData changed the declared input precision")
    check_data_size(data, limits)
    return data


def check_data_size(data, limits):
    import scipy.sparse as sparse
    if data.n_obs < 1 or data.n_vars < 1 or data.n_obs > limits["maximum_cells"] or data.n_vars > limits["maximum_features"]:
        raise ValueError("AnnData dimensions are empty/exceed declared capacity")
    values = [data.X] + list(data.obsm.values()) + list(data.obsp.values()) + list(data.layers.values())
    if data.raw is not None:
        values.append(data.raw.X)
    total = 0
    for value in values:
        entries = value.nnz if sparse.issparse(value) else value.size
        if entries > limits["maximum_entries"] or not sparse.issparse(value) and entries > limits["maximum_dense_entries"]:
            raise ValueError("AnnData slot entry capacity exceeded")
        total += value.data.nbytes + value.indices.nbytes + value.indptr.nbytes if sparse.issparse(value) else value.nbytes
    if total > limits["maximum_working_bytes"]:
        raise ValueError("AnnData array allocation estimate exceeds capacity")


def export_matrix(value, directory, name):
    import numpy as np
    import scipy.sparse as sparse
    from scipy.io import mmwrite
    path = Path(directory) / (name + ".mtx")
    mmwrite(str(path), value, symmetry="general", precision=17)
    return dict(file=path.name, shape=list(value.shape), dtype=str(value.dtype),
                sparse_format=value.format if sparse.issparse(value) else None,
                stored_entries=int(value.nnz if sparse.issparse(value) else np.asarray(value).size))


def export_state(data, directory):
    import numpy as np
    directory = Path(directory)
    directory.mkdir()
    state = dict(schema_version=1, n_obs=data.n_obs, n_vars=data.n_vars,
                 X=export_matrix(data.X, directory, "X"), slots={})
    export_frame(data.obs, directory / "obs")
    export_frame(data.var, directory / "var")
    for field in ("obsm", "obsp", "layers"):
        state["slots"][field] = []
        for index, (key, value) in enumerate(getattr(data, field).items()):
            state["slots"][field].append(dict(key=key, **export_matrix(value, directory, field + "-" + str(index))))
    if data.raw is not None:
        state["raw"] = export_matrix(data.raw.X, directory, "raw-X")
        export_frame(data.raw.var, directory / "raw-var")
    arrays = []
    def uns(value):
        if isinstance(value, np.ndarray):
            if value.dtype.hasobject:
                return dict(kind="object_array", shape=list(value.shape), values=[uns(v) for v in value.flat])
            name = "uns-array-" + str(len(arrays)) + ".npy"
            with (directory / name).open("xb") as stream:
                np.save(stream, value, allow_pickle=False)
            arrays.append(name)
            return dict(kind="array", file=name, shape=list(value.shape), dtype=str(value.dtype))
        if isinstance(value, dict):
            return dict(kind="mapping", entries=[dict(key=scalar(k), value=uns(v)) for k, v in value.items()])
        if isinstance(value, (list, tuple)):
            return dict(kind=type(value).__name__, values=[uns(v) for v in value])
        return scalar(value)
    state["uns"] = uns(dict(data.uns))
    write_json(directory / "state.json", state)
    # Original AnnData serialization retains source arrays, image data and metadata.
    data.write_h5ad(directory / "original-state.h5ad")
    return state


def interactions(path):
    import pandas as pd
    spec = read_json(path)
    require_keys(spec, {"columns", "rows", "provenance"}, {"columns", "rows", "provenance"})
    columns, rows = spec["columns"], spec["rows"]
    if not isinstance(columns, list) or len(set(columns)) != len(columns) or not {"source", "target"}.issubset(columns):
        raise ValueError("Captured interactions require unique columns including source/target")
    if not isinstance(rows, list) or any(not isinstance(r, list) or len(r) != len(columns) for r in rows):
        raise ValueError("Interaction row width differs from its captured schema")
    for row in rows:
        if any(v is not None and not isinstance(v, (str, int, float, bool)) for v in row):
            raise ValueError("Interaction annotations must be literal JSON scalars")
    return pd.DataFrame(rows, columns=columns), spec["provenance"]


def autocorrelation_context(data, options, frame, output):
    """Diagnostics only: original point/probability cells remain untouched."""
    import numpy as np
    import scipy.sparse as sparse
    from sklearn.preprocessing import normalize
    graph = data.obsp[options.get("connectivity_key", "spatial_connectivities")].copy()
    if options.get("transformation", True):
        graph = normalize(graph, norm="l1", axis=1, copy=False)
    total = graph.sum()
    combined = graph.transpose() + graph
    squares = combined.multiply(combined) if sparse.issparse(combined) else combined * combined
    s1 = squares.sum() / 2.
    s2 = (np.array(graph.sum(1) + graph.sum(0).transpose()) ** 2).sum()
    epsilon = np.finfo(np.float64).eps
    for value in (graph, np.asarray(total), np.asarray(s1), np.asarray(s2)):
        if np.issubdtype(value.dtype, np.floating):
            epsilon = max(epsilon, np.finfo(value.dtype).eps)
    mode = options.get("mode", "moran")
    score_column = "I" if mode == "moran" else "C"
    variance = frame["var_norm"].iloc[0] if len(frame) else np.float64("nan")
    standard_error = variance ** (1 / 2.)
    expected = -1. / (graph.shape[0] - 1) if mode == "moran" else 1.
    standardized = (frame[score_column].to_numpy() - expected) / standard_error
    write_json(Path(output) / "normality-context.json", dict(n=graph.shape[0], graph_dtype=str(graph.dtype),
        score_dtype=str(frame[score_column].dtype), variance_dtype=str(frame["var_norm"].dtype),
        s0=scalar(total), s1=scalar(s1), s2=scalar(s2), moment_precision_epsilon=scalar(epsilon),
        source_derived_standard_error=scalar(standard_error),
        purpose="Reconstruction/precision diagnostics; original returned cells and source settings are unchanged"))
    with (Path(output) / "normality-z.jsonl").open("x", encoding="utf-8", newline="\n") as stream:
        for i, (name, z) in enumerate(zip(frame.index, standardized)):
            stream.write(json.dumps(dict(position=i, index=scalar(name), standardized=scalar(z)), ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n")


def call(data, request, output, squidpy, on_event=None):
    """Invoke a public API with declared options; preserve upstream defaults."""
    output = Path(output)
    output.mkdir()
    operation, options = request["operation"], dict(request["options"])
    version = request["expected_squidpy"]
    def invoke(function, positional, keywords):
        from collections.abc import Mapping
        def parameter(value):
            if isinstance(value, Mapping):
                return dict(kind="mapping", entries=[dict(key=str(k), value=parameter(v)) for k, v in value.items()])
            if isinstance(value, (tuple, list)):
                return dict(kind=type(value).__name__, values=[parameter(v) for v in value])
            return scalar(value)
        signature = inspect.signature(function)
        bound = signature.bind(*positional, **keywords)
        bound.apply_defaults()
        executed = {name:parameter(value) for name, value in bound.arguments.items() if name not in ("adata", "path", "interactions")}
        invocation = dict(symbol=function.__module__ + "." + function.__qualname__, signature=str(signature),
                          provided_options=options, executed_parameters=executed,
                          input_bindings={"interactions":request.get("interactions_file"),"nanostring_directory":request.get("nanostring_directory")})
        if on_event is not None:
            on_event("CALL", invocation)
        result = function(*positional, **keywords)
        if on_event is not None:
            on_event("RETURNED", dict(source_return_type=type(result).__name__))
        return result
    if operation == "READ_NANOSTRING":
        if version == "1.1.2":
            raise ValueError("Squidpy1.1.2 has no read.nanostring function; explicitly choose a supported edition")
        data = invoke(squidpy.read.nanostring, (request["nanostring_directory"],), options)
        if "limits" in request:
            check_data_size(data, request["limits"])
        return dict(operation=operation, state=export_state(data, output / "state"),
                    source_transformations="Original reader joins cell_ID/FOV, intersects metadata and expression IDs and loads available images; original input files remain captured.")
    if "copy" in options:
        raise ValueError("The typed adapter owns copy behavior; do not supply copy")
    if operation == "SPATIAL_NEIGHBORS":
        before = list(data.obs_names)
        invoke(squidpy.gr.spatial_neighbors, (data,), dict(copy=False, **options))
        if "limits" in request:
            check_data_size(data, request["limits"])
        return dict(operation=operation, state=export_state(data, output / "state"),
                    observation_name_changes=[dict(position=i, before=a, after=b) for i, (a, b) in enumerate(zip(before, data.obs_names)) if a != b])
    if operation == "SPATIAL_AUTOCORR":
        frame = invoke(squidpy.gr.spatial_autocorr, (data,), dict(copy=True, **options))
        export_frame(frame, output / "statistics")
        autocorrelation_context(data, options, frame, output)
        return dict(operation=operation, rows=len(frame), columns=list(frame.columns),
                    source_order=list(frame.index), raw_source_frame="statistics",
                    warning="Undefined original probability cells and adjusted families containing undefined inputs require explicit admission diagnostics; raw source output is never repaired.")
    if operation == "LIGREC":
        pairs, provenance = interactions(request["interactions_file"])
        frames = invoke(squidpy.gr.ligrec, (data,), dict(interactions=pairs, copy=True, **options))
        for name, frame in frames.items():
            if name not in ("means", "pvalues", "metadata"):
                raise ValueError("Unrecognized original ligand-receptor result member; retain partials: " + str(name))
            export_frame(frame, output / name)
        return dict(operation=operation, frames=list(frames), interaction_provenance=provenance,
                    source_transformations="Original Squidpy uppercases gene/pair symbols, handles complexes, filters/deduplicates genes/interactions and leaves threshold-related missing probabilities. Original labels/annotations remain captured; no adapter repair.")
    raise ValueError("Unknown original Squidpy operation: " + str(operation))
