# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Typed count-workflow requests; leave biological identifiers and data untouched."""
from pathlib import Path, PurePosixPath
import copy
import math
import os
import shutil
import sqlite3
import stat
from Runtime import Unavailable, linked, pin, rawpin, walk_error

PROFILE = "original-idep244-r461-windows"


def keys(value, expected, label):
    if not isinstance(value, dict) or set(value) != set(expected.split()):
        raise ValueError("Exact typed keys required for " + label + ": " + expected)


def integer(value, low, high, label):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Integer outside the declared range for " + label)


def number(value, low, high, label):
    if type(value) not in [int, float] or not math.isfinite(value) or not low <= value <= high:
        raise ValueError("Finite numeric value outside the declared range for " + label)


def string(value, label):
    if not isinstance(value, str) or not value or "\0" in value:
        raise ValueError("Nonempty literal text required for " + label)


def vector(value, label):
    if value is not None:
        if not isinstance(value, list) or not value:
            raise ValueError("Use null or a nonempty literal string array for " + label)
        for v in value:
            string(v, label)


def validate(value):
    keys(value, "schema profile operation expression design load mapping preprocess analysis rng locale limits", "request")
    if type(value["schema"]) is not int or value["schema"] != 1 or value["profile"] != PROFILE:
        raise ValueError("Exact schema1/current iDEP2.4.4 Windows R4.6.1 profile required")
    if value["operation"] != "counts-differential-expression" or value["locale"] != "English_United States.utf8":
        raise ValueError("This workflow requires counts-DESeq2 and explicit Windows English_United States.utf8 locale")
    string(value["expression"], "expression")
    if value["design"] is not None:
        string(value["design"], "design")
    keys(value["load"], "max_group_name_length", "load")
    integer(value["load"]["max_group_name_length"], 1, 100000, "max_group_name_length")
    m = value["mapping"]
    if not isinstance(m, dict) or m.get("mode") not in ["disabled", "database"]:
        raise ValueError("Choose explicit disabled or database identifier mapping")
    expected = "mode multiple_map" if m["mode"] == "disabled" else "mode multiple_map reference_dir organism_id max_sample_ids"
    keys(m, expected, "mapping")
    if m["multiple_map"] not in ["mean", "median", "sum", "max", "max_sd"]:
        raise ValueError("Choose an unchanged original multiple_map method")
    if m["mode"] == "database":
        string(m["reference_dir"], "reference_dir")
        string(m["organism_id"], "organism_id")
        integer(m["max_sample_ids"], 1, 2147483647, "max_sample_ids")
        if not m["organism_id"].isascii() or not m["organism_id"].isdecimal() or not 0 < int(m["organism_id"]) <= 2147483647:
            raise ValueError("Use the explicit positive internal orgInfo id, not BestMatch/NEW or an inferred taxonomy id")
    p = value["preprocess"]
    keys(p, "missing_value min_counts n_min_samples_count counts_transform counts_log_start", "preprocess")
    if p["missing_value"] not in ["geneMedian", "treatAsZero", "geneMedianInGroup"]:
        raise ValueError("Choose an unchanged original missing-value method")
    number(p["min_counts"], 0, 1e15, "min_counts")
    integer(p["n_min_samples_count"], 1, 2147483647, "n_min_samples_count")
    integer(p["counts_transform"], 1, 3, "counts_transform")
    number(p["counts_log_start"], 0, 1e15, "counts_log_start")
    a = value["analysis"]
    keys(a, "fdr min_fold_change comparisons factors interactions block_factors reference_levels threshold_wald_test independent_filtering", "analysis")
    number(a["fdr"], 0, 1, "fdr")
    number(a["min_fold_change"], 1, 1e15, "min_fold_change")
    for n in ["comparisons", "factors", "interactions", "block_factors", "reference_levels"]:
        vector(a[n], n)
    for n in ["threshold_wald_test", "independent_filtering"]:
        if type(a[n]) is not bool:
            raise ValueError("Boolean required for " + n)
    keys(value["rng"], "kind seed", "rng")
    kinds = value["rng"]["kind"]
    if not isinstance(kinds, list) or len(kinds) != 3:
        raise ValueError("Specify all three original R RNG kinds")
    allowed = [
        ["Mersenne-Twister", "Wichmann-Hill", "Marsaglia-Multicarry", "Super-Duper",
         "Knuth-TAOCP", "Knuth-TAOCP-2002", "L'Ecuyer-CMRG"],
        ["Inversion", "Kinderman-Ramage", "Box-Muller", "Ahrens-Dieter", "Buggy Kinderman-Ramage"],
        ["Rejection", "Rounding"]]
    if any(v not in options for v, options in zip(kinds, allowed)):
        raise ValueError("Unsupported R RNG kind (user-supplied native callbacks are not accepted)")
    integer(value["rng"]["seed"], -2147483647, 2147483647, "rng.seed")
    l = value["limits"]
    keys(l, "input_bytes bundle_bytes files wall_seconds vector_heap_mib", "limits")
    integer(l["input_bytes"], 1, 2**50, "input_bytes")
    integer(l["bundle_bytes"], 1, 2**50, "bundle_bytes")
    integer(l["files"], 16, 10000000, "files")
    integer(l["wall_seconds"], 1, 604800, "wall_seconds")
    integer(l["vector_heap_mib"], 16, 1048576, "vector_heap_mib")
    return value


def input_files(request):
    """Stat every input before copying/hashing large caller reference trees."""
    entries = []
    for key in ["expression", "design"]:
        if request[key] is not None:
            p = Path(request[key]).resolve()
            entries.append((p, "inputs/" + key + "/" + p.name))
    if request["mapping"]["mode"] == "database":
        root = Path(request["mapping"]["reference_dir"]).resolve()
        if not root.is_dir():
            raise Unavailable("Complete caller reference directory is missing: " + str(root))
        if linked(root):
            raise ValueError("Complete regular reference files are required")
        for parent, dirs, files in os.walk(root, followlinks=False, onerror=walk_error):
            for name in dirs + files:
                if linked(Path(parent) / name):
                    raise ValueError("Reference links/reparse directories are not accepted")
            for name in files:
                p = Path(parent) / name
                entries.append((p, "inputs/reference/" + p.relative_to(root).as_posix()))
                if len(entries) > request["limits"]["files"]:
                    raise ValueError("Complete input exceeds files limit; no input truncation is allowed")
    total = 0
    names = set()
    for p, name in entries:
        if not p.is_file():
            raise Unavailable("Required complete input file is missing: " + str(p))
        if linked(p) or not stat.S_ISREG(p.stat().st_mode):
            raise ValueError("Only complete regular input files are accepted")
        if name.casefold() in names:
            raise ValueError("Case-colliding input name")
        names.add(name.casefold())
        total += p.stat().st_size
        if total > request["limits"]["input_bytes"] or len(names) > request["limits"]["files"]:
            raise ValueError("Complete input exceeds the caller input_bytes/files limit; no truncation is allowed")
    return sorted(entries, key=lambda x: x[1])


def capture(request, out, entries):
    effective = copy.deepcopy(request)
    records = {}
    for source, name in entries:
        destination = out / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        with source.open("rb") as src, destination.open("xb") as dest:
            shutil.copyfileobj(src, dest, length=1024*1024)
        identity = pin(destination)
        if pin(source) != identity:
            raise ValueError("Original input changed during capture: " + str(source))
        records[name] = dict(source=str(source), **identity)
        if name.startswith("inputs/expression/"):
            effective["expression"] = name
        elif name.startswith("inputs/design/"):
            effective["design"] = name
    if effective["mapping"]["mode"] == "database":
        effective["mapping"]["reference_dir"] = "inputs/reference"
        reference_preflight(effective, out)
    return effective, records


def reference_preflight(effective, out):
    m = effective["mapping"]
    if m["mode"] == "disabled":
        return
    root = out / m["reference_dir"]
    info = root / "demo/orgInfo.db"
    demo = root / "demo/demo_data_info.csv"
    if not info.is_file() or not demo.is_file():
        raise Unavailable("Mapping requires captured demo/orgInfo.db and demo/demo_data_info.csv; automatic download is not permitted in analysis")
    try:
        with sqlite3.connect(info.resolve().as_uri() + "?mode=ro&immutable=1", uri=True) as db:
            rows = db.execute("SELECT file FROM orgInfo WHERE id = ?", (m["organism_id"],)).fetchall()
    except sqlite3.Error as error:
        raise ValueError("Supplied orgInfo database cannot be read: " + str(error)) from error
    if len(rows) != 1 or not isinstance(rows[0][0], str):
        raise Unavailable("Provide exactly one original orgInfo row for internal organism id " + m["organism_id"])
    name = rows[0][0].replace("\\", "/")
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or ":" in name or not name:
        raise ValueError("Selected reference database filename must stay inside captured db/")
    selected = root / "db" / path
    if not selected.is_file() or not selected.resolve().is_relative_to((root / "db").resolve()):
        raise Unavailable("Exact selected reference database is missing: db/" + name)
