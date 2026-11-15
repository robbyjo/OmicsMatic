# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Session profile checks layered over the preserved headless implementation."""
from pathlib import Path, PurePosixPath
import importlib.util
import re
import sys

SESSION = Path(__file__).resolve().parent
LEGACY = SESSION / "legacy" if (SESSION / "legacy").is_dir() else SESSION.parent / "idep244"
sys.path.append(str(LEGACY))
import Runtime as legacy_runtime
if Path(legacy_runtime.__file__).resolve() != (LEGACY / "Runtime.py").resolve():
    raise RuntimeError("A different legacy Runtime module was imported")
from Runtime import *

spec = importlib.util.spec_from_file_location("idep244_preserved_schema", LEGACY / "Schema.py")
legacy_schema = importlib.util.module_from_spec(spec)
spec.loader.exec_module(legacy_schema)


def session_sources():
    sources = {p.name: pin(p) for p in SESSION.iterdir() if p.is_file() and
               (p.suffix in [".py", ".R", ".json"] or p.name == ".gitattributes")}
    sources.update({"legacy/" + name: identity for name, identity in engine_sources().items()})
    return dict(sorted(sources.items()))


def session_source_path(name):
    return LEGACY / name[len("legacy/"):] if name.startswith("legacy/") else SESSION / name


def description(path):
    fields = {}
    last = None
    for line in (Path(path) / "DESCRIPTION").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        if line[:1].isspace():
            if last is None:
                raise ValueError("Unexpected DESCRIPTION continuation")
            fields[last] += "\n" + line.strip()
        else:
            if ":" not in line:
                raise ValueError("Malformed DESCRIPTION field")
            name, value = line.split(":", 1)
            if name in fields:
                raise ValueError("Duplicate DESCRIPTION field")
            fields[name] = value.strip()
            last = name
    return fields


def package_version(text):
    # R packageVersion() prints numeric_version components separated by dots;
    # the unchanged DESCRIPTION may use a hyphen (e.g. abind 1.4-8 -> 1.4.8).
    # Compare that declared representation without rewriting either record.
    if not isinstance(text, str) or re.fullmatch(r"[0-9]+(?:[.-][0-9]+)*", text) is None:
        raise ValueError("Unsupported declared R package version")
    return ".".join(str(int(x)) for x in re.split(r"[.-]", text))


def coherent_provider(path, inspect_files=True):
    """Reject contradictory resealed metadata before trusting its declared roots.

    This establishes structural consistency, not upstream binary authenticity.
    Full current on-disk/source hashing is retained from the unchanged provider.
    """
    path = Path(path).resolve()
    if not (path / "COMPLETED.json").is_file():
        raise Unavailable("Complete original adopted runtime is unavailable: " + str(path))
    ready, done = load(path / "READY.json"), load(path / "COMPLETED.json")
    inspected = load(path / "INSPECTED.json")
    index = load(path / "runtime-files.json")
    if (ready["inspected"] != pin(path / "INSPECTED.json") or ready["source_profile"] != pin(LEGACY / "source-profile.json")
            or inspected["status"] != "PASS_INSPECTED_ORIGINAL_IDEP244_NAMESPACE_AND_RUNTIME_CLOSURE"
            or inspected["R_version"] != "4.6.1" or ready["libraries"] != inspected["libraries"]
            or ready["installed_packages"] != inspected["packages"]
            or done["analysis_admitted"] is not False or done["original_algorithms_patched"] is not False
            or done["catalog_credit_added"] != 0):
        raise ValueError("Provider INSPECTED/READY/source profile/completion contradiction")
    packages = inspected["packages"]
    if not isinstance(packages, list) or not packages or done["packages"] != len(packages):
        raise ValueError("Complete nonempty selected package membership is required")
    by_name, roots, descriptions = {}, set(), {}
    libs = [Path(x).resolve(strict=True) for x in ready["libraries"]]
    if not libs or len(set(libs)) != len(libs):
        raise ValueError("Distinct complete priority-ordered library roots are required")
    for row in packages:
        if set(row) != {"name", "version", "path"} or row["name"] in by_name:
            raise ValueError("Unique selected name/version/path package records are required")
        name = row["name"]
        if not isinstance(name, str) or re.fullmatch(r"[A-Za-z][A-Za-z0-9.]*", name) is None:
            raise ValueError("Malformed original R package name")
        selected = Path(row["path"]).resolve(strict=True)
        priority = next((p / name for p in libs if (p / name / "DESCRIPTION").is_file()), None)
        if priority is None or selected != priority.resolve() or selected in roots:
            raise ValueError("Selected package path contradicts library priority or membership: " + name)
        fields = description(selected)
        if fields.get("Package") != name or package_version(fields.get("Version")) != row["version"]:
            raise ValueError("Actual package DESCRIPTION contradicts selected package: " + name)
        by_name[name], descriptions[name] = row, fields
        roots.add(selected)
    for name, version in {"idepGolem": "2.4.4", "DESeq2": "1.52.0", "shiny": "1.14.0"}.items():
        if by_name.get(name, {}).get("version") != version:
            raise ValueError("Exact original session package is missing: " + name + " " + version)
    pending = set(["idepGolem", "BiocManager", "jsonlite", *inspected["loaded_namespaces"]])
    seen = set()
    while pending:
        name = pending.pop()
        if name in seen or name == "R":
            continue
        if name not in by_name:
            raise ValueError("Missing selected runtime dependency/namespace: " + name)
        seen.add(name)
        for field in ["Depends", "Imports"]:
            for text in descriptions[name].get(field, "").split(","):
                text = text.strip()
                if not text:
                    continue
                match = re.fullmatch(r"([A-Za-z][A-Za-z0-9.]*)\s*(?:\([^()]+\))?", text)
                if match is None:
                    raise ValueError("Unsupported dependency declaration in actual DESCRIPTION: " + name)
                pending.add(match.group(1))
    if seen != set(by_name):
        raise ValueError("Selected package set contradicts the exact Depends/Imports plus loaded-namespace closure")
    r_home = Path(inspected["R_home"]).resolve(strict=True)
    roots.update(r_home / n for n in ["bin", "etc", "modules"])
    rscript = Path(ready["rscript"]).resolve(strict=True)
    if not rscript.is_relative_to(r_home / "bin") or not isinstance(index, dict) or not index:
        raise ValueError("Inspected R home does not own Rscript or runtime index is empty")
    resolved = [Path(n).resolve(strict=True) for n in index]
    if len(set(resolved)) != len(resolved) or set(resolved) != roots:
        raise ValueError("Runtime index must cover exactly all selected package folders and R bin/etc/modules")
    count = 0
    for folder, files in index.items():
        if not isinstance(files, dict) or not files:
            raise ValueError("Every declared installed runtime folder requires its complete nonempty file inventory")
        for name, identity in files.items():
            relative = PurePosixPath(name)
            if (relative.is_absolute() or ".." in relative.parts or "\\" in name or ":" in name
                    or set(identity) != {"bytes", "sha256"} or type(identity["bytes"]) is not int
                    or identity["bytes"] < 0 or re.fullmatch(r"[a-f0-9]{64}", identity["sha256"]) is None):
                raise ValueError("Malformed runtime file identity or escaping inventory member")
        count += len(files)
    if type(done["locked_files"]) is not int or done["locked_files"] != count:
        raise ValueError("Complete locked-file count contradicts runtime inventory")
    if inspect_files:
        return provider_check(path)
    # Structural-only mode is used for negative controls, never analysis admission.
    return ready
