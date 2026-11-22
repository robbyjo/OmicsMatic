#!/usr/bin/env python3
"""Freeze actual FLASH 1.2.11 output, not Java-derived expected sequences.

Requires the separately retained original reference campaigns under build/.
Does not run/install software or change the literature cohort.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def records(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    assert len(lines) % 4 == 0, path
    result = {}
    for i in range(0, len(lines), 4):
        key = lines[i][1:].split()[0].split("/")[0]
        assert key not in result, (path, key)
        result[key] = (lines[i + 1], lines[i + 3])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    cache = args.root / "build/literature-review/flash-runtime"
    output = args.root / "src/test/resources/org/jlinalg/sequence/flash"
    output.mkdir(parents=True, exist_ok=True)
    original_hash = "2dbac87cf2f04721ef29e67ede061d6c0f17eae83297836e46f47bd209977ce6"
    assert digest(cache / "original-windows/flash.exe") == original_hash
    source_files = {}
    rows = []
    specifications = [
        ("first-three", "native-draft/first-three-input.tsv", [cache / "first-original-goldens"], "golden"),
        ("randomized", "native-reference-campaign-1/all-cases.tsv", sorted((cache / "native-reference-campaign-1").glob("profile-*")), "actual"),
        ("boundary", "native-boundary-campaign-1/all-cases.tsv", sorted((cache / "native-boundary-campaign-1").glob("profile-*")), "actual"),
    ]
    for group, input_name, profiles, prefix in specifications:
        expected = {}; unmerged = {}
        input_path = cache / input_name; source_files[input_name] = digest(input_path)
        for profile in profiles:
            for suffix in ("extendedFrags", "notCombined_1", "notCombined_2"):
                path = profile / (prefix + "." + suffix + ".fastq")
                source_files[str(path.relative_to(cache)).replace("\\", "/")] = digest(path)
            expected.update(records(profile / (prefix + ".extendedFrags.fastq")))
            a = records(profile / (prefix + ".notCombined_1.fastq")); b = records(profile / (prefix + ".notCombined_2.fastq"))
            assert a.keys() == b.keys() and not expected.keys() & a.keys()
            unmerged.update({key: (a[key], b[key]) for key in a})
        inputs = input_path.read_text().splitlines()
        assert len(expected) + len(unmerged) == len(inputs)
        for line in inputs:
            fields = line.split("\t"); key = fields[0]
            assert len(fields) == 11
            if key in expected:
                sequence, quality = expected[key]
            else:
                assert unmerged[key] == ((fields[1], fields[2]), (fields[3], fields[4]))
                sequence = quality = "-"
            rows.append("\t".join([group + "/" + key] + fields[1:] + [sequence, quality]))
    assert len(rows) == 5171
    target = output / "original-1.2.11-cases.tsv.gz"
    assert not target.exists(), "Never silently overwrite frozen references"
    # Raw TAB separation is intentional: printable FASTQ quality characters can
    # contain quotes and backslashes, but cannot contain TAB or a newline.
    with target.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as packed:
            packed.write(("\n".join(rows) + "\n").encode("utf-8"))
    manifest = dict(schema_version=1, cases=len(rows), original_version="FLASH 1.2.11",
                    executable_sha256=original_hash,
                    source_archive_sha256="685ca6f7fedda07434d8ee03c536f4763385671c4509c5bb48beb3055fd236ac",
                    binary_archive_sha256="9bac50493584d5e974a87ad64536d268df6b8e2e639df7fb5b000423c7c67f7f",
                    upstream="https://ccb.jhu.edu/software/FLASH/index.shtml", original_independent_processes=41,
                    reference_basis="Actual independently launched original output; merged partition, consensus sequence/quality, and both unmerged mate streams. Not original per-overlap diagnostic scores, which FLASH does not publish.",
                    columns=["id", "sequence1", "quality1", "sequence2", "quality2", "min_overlap", "max_overlap", "max_density", "phred_offset", "outies", "cap_quals", "expected_sequence_or_dash", "expected_quality_or_dash"],
                    fixture_sha256=digest(target), producer_sha256=digest(Path(__file__)), original_capture_files=source_files)
    (output / "original-1.2.11-provenance.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(cases=len(rows), fixture_sha256=digest(target), bytes=target.stat().st_size)))


if __name__ == "__main__":
    main()
