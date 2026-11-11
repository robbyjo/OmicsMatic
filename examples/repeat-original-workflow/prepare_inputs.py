#!/usr/bin/env python3
"""Prepare pinned synthetic inputs and closed JSON requests; never install or run analysis."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sys
import zipfile

HEALTH_LOCK_SHA256 = "915899f81eb0ac8b885d5a2be0dfb063bdd5dab31b70f0d551ede428d8d9d856"
PREFIX = "org/jlinalg/omics/repeat-health/"


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def member(archive: zipfile.ZipFile, name: str) -> bytes:
    entry = archive.getinfo(PREFIX + name)
    if entry.file_size > 1048576:
        raise ValueError("Synthetic fixture exceeds the fixed one-MiB input bound")
    return archive.read(entry)


def prepare(jar: Path, runtime: Path, output: Path, mapped: bool, compressed: bool) -> dict:
    jar = jar.resolve(strict=True)
    runtime = runtime.resolve(strict=True)
    output = output.absolute()
    if output.exists() or not output.parent.resolve(strict=True) == output.parent:
        raise ValueError("Choose a NEW output directory beneath a canonical existing parent")
    for path in [runtime, output]:
        if not path.as_posix().isascii() or any(ord(c) < 32 for c in str(path)):
            raise ValueError("Runtime and original-program workspace paths must be ASCII")
    if not (runtime / "ADMITTED.json").is_file():
        raise FileNotFoundError("Supply a permitted admitted runtime; a payload directory is insufficient")
    with zipfile.ZipFile(jar) as archive:
        lock_raw = member(archive, "lock.json")
        if digest(lock_raw) != HEALTH_LOCK_SHA256:
            raise ValueError("The JAR does not contain this example's pinned synthetic fixture lock")
        lock = json.loads(lock_raw)
        values = {}
        for name in ["query.fa", "library.fa", "recipe.json"]:
            relative = "current-custom-N/" + name
            raw = member(archive, relative)
            pin = lock["members"][relative]
            if digest(raw) != pin["sha256"] or len(raw) != pin["bytes"]:
                raise ValueError("Synthetic fixture bytes differ from the pinned lock: " + relative)
            values[name] = raw
    query = values["query.fa"]
    if mapped:
        lines = query.decode("utf-8").splitlines()
        query = ("\n".join(line + " β 日本 original" if line.startswith(">") else line.lower()
                           for line in lines) + "\n").encode("utf-8")
    query_name = "query.fa.gz" if compressed else "query.fa"
    if compressed:
        query = gzip.compress(query, mtime=0)
    recipe = json.loads(values["recipe.json"])
    recipe["resource_root"] = "."
    recipe["query"] = {"path": query_name, "encoding": "GZIP_UTF8" if compressed else "PLAIN_UTF8"}
    recipe["staging_policy"] = "ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA" if mapped else "EXACT_SOURCE"
    recipe["limits"].update(workspace_bytes=68719476736, file_count=100000)
    recipe["input_review"]["paper_settings_status"] = "Synthetic public workflow example; no paper reconstruction or biological accuracy claim"
    options = {"recipe": str(output / "recipe.json"), "out": str(output / "result"), "runtime": str(runtime)}
    provenance = {"scope": "synthetic-input-preparation-only", "analysis_completed": False,
                  "health_lock_sha256": HEALTH_LOCK_SHA256, "query_sha256": digest(query),
                  "library_sha256": digest(values["library.fa"]), "mapped_query_ids_and_uppercase": mapped,
                  "gzip_query": compressed, "runtime_admission_rechecked": False}
    output.mkdir()
    (output / query_name).write_bytes(query)
    (output / "library.fa").write_bytes(values["library.fa"])
    for name, value in [("recipe.json", recipe), ("search-options.json", options), ("input-provenance.json", provenance)]:
        (output / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    return options


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--map-query-ids", action="store_true")
    parser.add_argument("--gzip-query", action="store_true")
    args = parser.parse_args()
    if sys.platform != "linux":
        print("SKIP3: run this original-workflow example with Linux Python and Java inside WSL. "
              "No analysis, installation or download occurred.", file=sys.stderr)
        return 3
    try:
        options = prepare(args.jar, args.runtime, args.out, args.map_query_ids, args.gzip_query)
    except FileNotFoundError as error:
        print("SKIP3: missing prerequisite: " + str(error) + ". Supply the permitted JAR/runtime; "
              "if installation is forbidden, leave the analysis skipped.", file=sys.stderr)
        return 3
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print("FAIL2: " + str(error) + ". Retain partials if any, correct the named cause and use a NEW output.", file=sys.stderr)
        return 2
    print("Prepared synthetic inputs only: " + json.dumps(options, ensure_ascii=False))
    print("Required action: run repeat-tools with search-options.json, then verify the completed result. "
          "The analysis independently rechecks the runtime; this preparation did not admit it.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
