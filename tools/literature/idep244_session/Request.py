# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit real-session caller profile, exact scientific options and path capture."""
import copy
import json
from Core import *

PROFILE = "original-idep244-shiny-r461-windows"


def validate(value):
    clone = copy.deepcopy(value)
    if not isinstance(clone, dict) or clone.get("profile") != PROFILE or type(clone.get("schema")) is not int or clone["schema"] != 2:
        raise ValueError("Exact schema2 original iDEP2.4.4 / Shiny1.14.0 Windows session profile required")
    clone["schema"], clone["profile"] = 1, legacy_schema.PROFILE
    legacy_schema.validate(clone)
    return value


def input_files(request):
    validate(request)
    entries = []
    for key in ["expression", "design"]:
        if request[key] is not None:
            literal = Path(request[key])
            source = literal.resolve(strict=True)
            if not source.is_file() or not literal.name:
                raise Unavailable("Original regular expression/design input is missing")
            # Preserve the literal caller extension even for a differently suffixed
            # top-level symlink. Upstream selects its loader using that extension.
            entries.append((source, "inputs/" + key + "/" + literal.name))
    clone = copy.deepcopy(request)
    clone["expression"] = clone["design"] = None
    entries.extend(legacy_schema.input_files(clone))
    names, total = set(), 0
    for source, name in entries:
        if name.casefold() in names:
            raise ValueError("Case-colliding captured input")
        names.add(name.casefold())
        total += source.stat().st_size
    if total > request["limits"]["input_bytes"] or len(entries) > request["limits"]["files"]:
        raise ValueError("Complete input exceeds its recording limit; no truncation is allowed")
    return sorted(entries, key=lambda x: x[1])


def capture(request, out, entries):
    effective, inputs = legacy_schema.capture(request, out, entries)
    for key in ["expression", "design"]:
        if request[key] is not None:
            inputs[effective[key]]["requested_source"] = request[key]
    if request["mapping"]["mode"] == "database":
        for name, record in inputs.items():
            if name.startswith("inputs/reference/"):
                record["requested_reference_root"] = request["mapping"]["reference_dir"]
    coherent_requests(request, effective, inputs)
    return effective, inputs


def coherent_requests(original, effective, inputs):
    validate(original)
    validate(effective)
    expected = copy.deepcopy(original)
    for key in ["expression", "design"]:
        if original[key] is not None:
            expected[key] = "inputs/" + key + "/" + Path(original[key]).name
            if inputs.get(expected[key], {}).get("requested_source") != original[key]:
                raise ValueError("Captured input contradicts the exact requested path/name: " + key)
    if original["mapping"]["mode"] == "database":
        expected["mapping"]["reference_dir"] = "inputs/reference"
        records = [v for n, v in inputs.items() if n.startswith("inputs/reference/")]
        if not records or any(v.get("requested_reference_root") != original["mapping"]["reference_dir"] for v in records):
            raise ValueError("Captured reference root contradicts the original caller")
    if encoded(expected) != encoded(effective):
        raise ValueError("Original/effective request contradiction outside the exact declared path relocation")
    return True


reference_preflight = legacy_schema.reference_preflight
