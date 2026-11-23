"""Original Squidpy worker; the Java owner must verify the pre-call handshake.

Only prepared local inputs and explicitly captured interaction pairs are accepted.
This worker's raw outputs are not an admission decision. A completed Java audit is
required before a result can be marked admitted.
"""
from pathlib import Path
import hashlib
import importlib
import importlib.metadata as metadata
import inspect
import json
import os
import platform
import socket
import sys
import sysconfig
import time
import traceback
import warnings

from operations import (call, check_data_size, export_state, read_input, read_json,
                        require_keys, text, write_json)


class Unavailable(Exception):
    pass


def estimate_work(data, request):
    """Conservative admission bounds; never change any original call option."""
    operation, options, limits = request["operation"], request["options"], request["limits"]
    count = options.get("n_perms", 1000 if operation == "LIGREC" else None)
    if count is not None and (type(count) is not int or count < 1):
        raise ValueError("Permutation count must be a positive integer; None is supported for autocorrelation only")
    if operation == "LIGREC" and count is None:
        raise ValueError("Original ligand-receptor permutations require a positive integer")
    permutations = 1 if count is None else count + 1
    if operation == "SPATIAL_AUTOCORR":
        graph = data.obsp[options.get("connectivity_key", "spatial_connectivities")]
        entries = graph.nnz if hasattr(graph, "nnz") else graph.size
        genes = options.get("genes")
        features = len(genes) if isinstance(genes, list) else 1 if isinstance(genes, (str, int)) else max(data.n_vars, len(data.obs.columns))
        if options.get("attr") == "obsm" and genes is None:
            features = data.obsm[options["layer"]].shape[1]
        return entries * features * permutations
    if operation == "LIGREC":
        pairs = read_json(request["interactions_file"])
        columns = pairs["columns"]
        if not isinstance(columns, list) or len(set(columns)) != len(columns) or not {"source", "target"}.issubset(columns):
            raise ValueError("Captured interaction source/target columns are required")
        source, target = columns.index("source"), columns.index("target")
        pair_bound = 0
        for row in pairs["rows"]:
            if not isinstance(row, list) or len(row) != len(columns):
                raise ValueError("Captured interaction row width differs")
            pair_bound += (len(str(row[source]).split("_")) * len(str(row[target]).split("_"))
                           if options.get("complex_policy", "min") == "all" else 1)
        clusters = len(data.obs[options["cluster_key"]].unique())
        dense = pair_bound * clusters * clusters
        if pair_bound > limits["maximum_entries"] or dense > limits["maximum_dense_entries"] or dense * 32 > limits["maximum_working_bytes"]:
            raise ValueError("Original ligand-receptor pair/cluster output estimate exceeds declared capacity")
        return pair_bound * max(data.n_obs, clusters * clusters) * permutations
    return data.n_obs * data.n_obs


def identity(path):
    result = hashlib.sha256()
    total = 0
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
            total += len(block)
    return dict(bytes=total, sha256=result.hexdigest())


def network_policy(event, args):
    if event == "socket.connect":
        address = args[1]
        # Local IPC remains available to explicitly bounded upstream workers.
        if isinstance(address, tuple) and address[0] not in ("127.0.0.1", "::1", "localhost"):
            raise PermissionError("Outbound scientific/resource network access is disabled; provide captured local resources")


def runtime(request, source_profile):
    expected = request["expected_squidpy"]
    if expected not in source_profile["editions"]:
        raise ValueError("Unsupported exact original Squidpy profile")
    if platform.python_version() != request["expected_python"]:
        raise Unavailable("Declared Python differs: expected " + request["expected_python"] + "; actual " + platform.python_version())
    required = {"squidpy": expected, **request["expected_packages"]}
    if required["squidpy"] != expected:
        raise ValueError("Contradictory Squidpy dependency edition")
    for package, version in required.items():
        try:
            actual = metadata.version(package)
        except metadata.PackageNotFoundError:
            raise Unavailable("Install permitted exact dependency " + package + "==" + version + "; analysis skipped")
        if actual != version:
            raise Unavailable("Restore exact dependency " + package + "==" + version + "; actual " + actual)
    distribution = metadata.distribution("squidpy")
    root = Path(distribution.locate_file("squidpy")).resolve()
    original = source_profile["editions"][expected]
    source_files = {}
    for name, declared in original["members"].items():
        source = root / name
        if not source.is_file() or identity(source) != declared:
            raise Unavailable("Installed original Squidpy file differs/missing: " + str(source) + "; restore the declared unchanged wheel")
        source_files[name] = declared
    actual_python = {p.relative_to(root).as_posix() for p in root.rglob("*.py")}
    if actual_python != {name for name in source_files if name.endswith(".py")}:
        raise Unavailable("Unexpected/missing original Squidpy Python source members; restore a clean private prefix")
    try:
        import squidpy as sq
        import numpy
        import pandas
        import anndata
        import scipy
        for package in ("scanpy", "numba", "sklearn", "statsmodels", "omnipath", "h5py", "PIL", "scipy.io"):
            importlib.import_module(package)
    except Exception as error:
        raise Unavailable("Exact original dependency cannot import; preserve error and restore compatible private runtime: " + str(error)) from error
    packages = []
    for dist in metadata.distributions():
        packages.append(dict(name=dist.metadata["Name"], version=dist.version,
                             location=str(Path(dist.locate_file("")).resolve())))
    module_files = sorted({str(Path(m.__file__).resolve()) for m in list(sys.modules.values())
                           if getattr(m, "__file__", None) and Path(m.__file__).is_file()})
    site_roots = {Path(p["location"]).resolve() for p in packages}
    closure = {str(Path(sys.executable).resolve()), str(root), sysconfig.get_paths()["stdlib"]}
    closure.update(str(path) for path in site_roots)
    info = dict(profile=source_profile["profile"], python=platform.python_version(),
                python_executable=str(Path(sys.executable).resolve()), prefix=sys.prefix, base_prefix=sys.base_prefix,
                python_flags=dict(isolated=sys.flags.isolated, no_user_site=sys.flags.no_user_site,
                                  dont_write_bytecode=sys.dont_write_bytecode, pycache_prefix=sys.pycache_prefix),
                python_hash_seed=os.environ.get("PYTHONHASHSEED"), squidpy=expected,
                packages=packages, squidpy_source_root=str(root), original_source_files=source_files, loaded_module_files=module_files,
                runtime_roots=sorted(closure),
                network_policy="Python-audited external socket connections refused; local IPC allowed. This is not an OS network namespace.")
    return sq, info


def validate(request, profile):
    require_keys(request, {"schema_version", "operation", "expected_python", "expected_squidpy", "expected_packages",
                           "input", "options", "limits", "interactions_file", "nanostring_directory", "ack_timeout_seconds"},
                 {"schema_version", "operation", "expected_python", "expected_squidpy", "expected_packages", "options", "limits", "ack_timeout_seconds"})
    if request["schema_version"] != 1:
        raise ValueError("Unknown worker schema")
    edition = profile["editions"].get(request["expected_squidpy"])
    if edition is None:
        raise ValueError("Unknown original edition")
    function = edition["functions"].get(request["operation"])
    if function is None:
        raise Unavailable("Requested original function is absent in Squidpy" + request["expected_squidpy"] + "; explicitly choose a permitted supported edition")
    if not isinstance(request["options"], dict) or set(request["options"]) - set(function["parameters"]):
        raise ValueError("Unsupported/unknown parameter for the exact original function; no option is ignored or translated")
    for name in ("anndata", "numpy", "scipy", "pandas", "scanpy", "numba", "scikit-learn", "statsmodels", "omnipath"):
        if name not in request["expected_packages"]:
            raise ValueError("Declare the exact scientific dependency edition: " + name)
    if any(not isinstance(k, str) or not isinstance(v, str) or not k or not v for k, v in request["expected_packages"].items()):
        raise ValueError("Declare literal dependency names/editions")
    limits = request["limits"]
    keys = {"maximum_cells", "maximum_features", "maximum_entries", "maximum_dense_entries", "maximum_working_bytes", "maximum_work", "threads"}
    require_keys(limits, keys, keys)
    if any(type(v) is not int or v < 1 for v in limits.values()) or limits["threads"] > 32:
        raise ValueError("Require positive bounded integer capacities/threads")
    jobs = request["options"].get("n_jobs")
    if jobs is not None and (type(jobs) is not int or not 1 <= jobs <= limits["threads"]):
        raise ValueError("Explicit n_jobs must fit the declared thread budget")
    if request["operation"] == "LIGREC" and "interactions_file" not in request:
        raise ValueError("Captured local interaction pairs/provenance are required; no changing default database download")
    if request["operation"] == "READ_NANOSTRING":
        if "nanostring_directory" not in request:
            raise ValueError("Declare captured Nanostring directory")
        for name in ("counts_file", "meta_file", "fov_file"):
            if request["options"].get(name) is not None:
                relative = Path(text(request["options"][name]))
                if relative.is_absolute() or ".." in relative.parts:
                    raise ValueError("Nanostring file must stay inside the captured directory")
    elif "input" not in request:
        raise ValueError("Declare a prepared local dataset")


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Use worker.py NEW-CAPTURE-ROOT")
    root = Path(sys.argv[1]).resolve()
    worker = root / "worker"
    request = read_json(worker / "request.json")
    profile = read_json(Path(__file__).with_name("SOURCE-PROFILE.json"))
    report = dict(status="PREFLIGHT", original_call_started=False, original_call_returned=False,
                  results_admitted=False, warnings=[])
    recorded = []
    try:
        validate(request, profile)
        sys.addaudithook(network_policy)
        with warnings.catch_warnings(record=True) as recorded:
            warnings.simplefilter("always")
            sq, info = runtime(request, profile)
            write_json(worker / "runtime.json", info)
            data = None
            if request["operation"] != "READ_NANOSTRING":
                data = read_input(request["input"], request["limits"], metadata.version("anndata"))
                check_data_size(data, request["limits"])
                report["input_shape"] = [data.n_obs, data.n_vars]
                work = estimate_work(data, request)
                report["estimated_work"] = work
                if work > request["limits"]["maximum_work"]:
                    raise ValueError("Original operation work estimate exceeds declared maximum_work; change explicit resources/settings in a NEW request")
            if (worker / "READY").exists() or (worker / "ACK").exists():
                raise ValueError("Preexisting handshake marker; preserve capture and use NEW output")
            (worker / "READY.pending").write_text("SQUIDPY_PREFLIGHT_V1\n", encoding="ascii")
            (worker / "READY.pending").replace(worker / "READY")
            deadline = time.monotonic() + request["ack_timeout_seconds"]
            while not (worker / "ACK").is_file():
                if time.monotonic() > deadline:
                    raise TimeoutError("Java owner did not acknowledge the source/input/runtime capture; no analysis was started")
                time.sleep(.025)
            if (worker / "ACK").read_bytes() != b"SQUIDPY_ACK_V1\n":
                raise ValueError("Invalid Java pre-call acknowledgement")
            def event(kind, detail):
                if kind == "CALL":
                    write_json(worker / "invocation.json", detail)
                    report["original_call_started"] = True
                elif kind == "RETURNED":
                    report["original_call_returned"] = True
                    report["source_return"] = detail
            report["original_output"] = call(data, request, worker / "out", sq, event)
            _, after = runtime(request, profile)
            write_json(worker / "runtime-after-info.json", after)
            report["status"] = "ORIGINAL_RETURNED_PENDING_JAVA_AUDIT"
        code = 0
    except Unavailable as error:
        report.update(status="SKIP3_DEPENDENT_ANALYSIS_SKIPPED", cause=str(error), traceback=traceback.format_exc(),
                      required_action="Restore/install the exact named permitted private dependency or input; otherwise keep the skipped analysis. Retry in NEW output.")
        code = 3
    except Exception as error:
        report.update(status="FAIL2_NO_COMPLETE_RESULT", cause=str(error), traceback=traceback.format_exc(),
                      required_action="Preserve raw/partial/failed evidence, correct the stated source/input/protocol/resource error and retry in NEW output. No complete result is admitted.")
        code = 2
    report["warnings"] = [dict(category=w.category.__name__, message=str(w.message), filename=w.filename, line=w.lineno) for w in recorded]
    write_json(worker / "report.json", report)
    if code:
        print(report["status"] + ": " + report["cause"] + "\n" + report["required_action"], file=sys.stderr, flush=True)
    else:
        print("Original Squidpy returned; Java admission audit is still required", flush=True)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
