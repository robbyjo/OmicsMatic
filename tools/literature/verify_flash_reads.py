#!/usr/bin/env python3
"""Compare a packaged native FLASH command with authenticated original FLASH.

All attempts and originals are retained in a new --work directory. No install,
network request, registry change or catalog publication is performed.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import random
import subprocess
import sys
import time

ORIGINAL_SHA = "2dbac87cf2f04721ef29e67ede061d6c0f17eae83297836e46f47bd209977ce6"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fastq(path):
    raw = path.read_bytes()
    if raw[:2] == b"\x1f\x8b": raw = gzip.decompress(raw)
    lines = raw.decode("utf-8").replace("\r\n", "\n").split("\n")
    if lines[-1] == "": lines.pop()
    assert len(lines) % 4 == 0, path
    return [tuple(lines[i:i + 4]) for i in range(0, len(lines), 4)]


def record(header, sequence, quality=None, plus="+"):
    return header + "\n" + sequence + "\n" + plus + "\n" + (quality if quality is not None else "I" * len(sequence)) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True); parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--original", type=Path, required=True); parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--additional-java", type=Path, action="append", default=[], help="Additional JVMs for Unicode recipe checks only")
    args = parser.parse_args(); args.work.mkdir(parents=True, exist_ok=False)
    missing = [dict(role=role, path=str(path)) for role, path in [("Java", args.java), ("JAR", args.jar), ("original FLASH validator", args.original)] if not path.is_file()]
    if missing:
        action = "Supply the named existing files. If downloads are permitted, obtain original FLASH 1.2.11 from https://ccb.jhu.edu/software/FLASH/index.shtml and authenticate its binary archive/member hashes; otherwise skip this original-program comparison. Native flash-reads itself needs no FLASH runtime. No installation was attempted."
        (args.work / "verification.json").write_text(json.dumps(dict(status="skipped-unavailable-validation-dependency", missing=missing, required_action=action), indent=2) + "\n")
        (args.work / "WARNINGS.md").write_text("# ORIGINAL FLASH VALIDATION SKIPPED\n\n" + repr(missing) + "\n\nRequired action: " + action + "\n")
        print("ORIGINAL FLASH VALIDATION SKIPPED. Required action: " + action, file=sys.stderr)
        raise SystemExit(3)
    report = dict(status="running", jar_sha256=sha(args.jar), original_sha256=sha(args.original), producer_sha256=sha(Path(__file__)),
                  native_calls=[], fresh_original_calls=[], reused_original_profiles=[], control_calls=[], unicode_recipe_calls=[])
    started = time.monotonic()

    def launch(command, directory, label, expected=0):
        process = subprocess.run(list(map(str, command)), capture_output=True, timeout=180)
        (directory / (label + ".stdout")).write_bytes(process.stdout); (directory / (label + ".stderr")).write_bytes(process.stderr)
        result = dict(command=list(map(str, command)), exit_code=process.returncode,
                      stdout_sha256=sha(directory / (label + ".stdout")), stderr_sha256=sha(directory / (label + ".stderr")))
        assert process.returncode == expected, (label, process.returncode, process.stderr.decode("utf-8", errors="replace"))
        return result

    def compare(name, r1, r2, original_directory, prefix, settings, extra=None, interleaved=False):
        work = args.work / name; work.mkdir()
        output = work / "native"
        command = [args.java, "-jar", args.jar, "flash-reads", "--reads", r1, "--phred-offset", settings[3], "--out", output,
                   "--min-overlap", settings[0], "--max-overlap", settings[1], "--max-mismatch-density", settings[2],
                   "--allow-outies", settings[4], "--cap-mismatch-quals", settings[5]]
        command += ["--interleaved", "true"] if interleaved else ["--reads2", r2]
        if extra: command += extra
        result = launch(command, work, "native"); result["name"] = name
        merged = fastq(output / "merged.fastq.gz"); u1 = fastq(output / "unmerged_R1.fastq.gz"); u2 = fastq(output / "unmerged_R2.fastq.gz")
        assert merged == fastq(original_directory / (prefix + ".extendedFrags.fastq")), (name, "merged header/sequence/quality")
        assert u1 == fastq(original_directory / (prefix + ".notCombined_1.fastq")), (name, "unmerged1")
        assert u2 == fastq(original_directory / (prefix + ".notCombined_2.fastq")), (name, "unmerged2")
        counters = dict(line.split("\t") for line in (output / "results.tsv").read_text().splitlines()[1:])
        assert int(counters["merged_pairs"]) == len(merged) and int(counters["unmerged_pairs"]) == len(u1) == len(u2)
        assert int(counters["input_pairs"]) == len(merged) + len(u1)
        expected_lengths = Counter(len(row[1]) for row in merged)
        actual_lengths = {int(fields[0]): int(fields[1]) for fields in (line.split("\t") for line in (output / "length-histogram.tsv").read_text().splitlines()[1:])}
        assert actual_lengths == dict(expected_lengths)
        rows = (output / "read-status.tsv").read_text().splitlines(); assert len(rows) == len(merged) + len(u1) + 1
        assert all(len(line.split("\t")) == 20 for line in rows)
        result.update(pairs=len(merged) + len(u1), merged=len(merged), unmerged=len(u1), original_files={suffix: sha(original_directory / (prefix + "." + suffix + ".fastq")) for suffix in ("extendedFrags", "notCombined_1", "notCombined_2")})
        report["native_calls"].append(result)
        return output

    try:
        assert report["original_sha256"] == ORIGINAL_SHA, "Wrong original reference executable"
        # These 41 original processes ran independently before the Java workflow
        # existed. Reference files, including headers/plus lines, are reused whole.
        specifications = [("first-golden", args.cache / "first-original-goldens", "golden", ["10", "200", "0.25", "33", "false", "false"])]
        for campaign in ("native-reference-campaign-1", "native-boundary-campaign-1"):
            rows = (args.cache / campaign / "all-cases.tsv").read_text().splitlines()
            for profile in sorted((args.cache / campaign).glob("profile-*")):
                index = int(profile.name.split("-")[1]); count = 160 if "reference" in campaign else 6
                f = rows[index * count].split("\t")
                specifications.append((campaign + "-" + profile.name, profile, "actual", [f[5], f[6], f[7], f[8], f[9], f[10]]))
        for name, profile, prefix, settings in specifications:
            compare(name, profile / "reads1.fastq", profile / "reads2.fastq", profile, prefix, settings)
            report["reused_original_profiles"].append(dict(name=name, directory=str(profile)))
        assert sum(call["pairs"] for call in report["native_calls"]) == 5171
        rng = random.Random(2026102519)
        dna = lambda n: "".join(rng.choice("ACGT") for _ in range(n))
        rc = lambda s: s.translate(str.maketrans("ACGTNacgt", "TGCANtgca"))[::-1]
        fragment = "CTGACTGCTACGTGTCACAGACGTGATGAACTGACC"; a, b = fragment[:25], rc(fragment[-25:])
        frontend = [
            (record("@lower/1 original comment", a.lower()), record("@lower/2 original comment", b.lower())),
            (record("@unknown/1", "acGTnRU?.", "IIIIIIIII", "+different"), record("@unknown/2", "NNNNNACGT")),
            (record("@barcode/1#BC comment", a), record("@barcode/2#BC comment", b)),
            (record("@barcode2#BC/1", a), record("@barcode2#BC/2", b)),
            (record("@comment comment/path/file", a), record("@comment reverse", b)),
            (record("@plain full comment", a), record("@plain reverse", b)),
            (record("@汉/1#条码 comment", a), record("@汉/2#条码 comment", b)),
            (record("@trim/1 \t", a + " \t", "I" * 25 + " \t"), record("@trim/2 \t", b)),
            (record("@allN/1", "N" * 25), record("@allN/2", "N" * 25)),
        ]
        long_cases = []
        for i, (length, outie) in enumerate([(1501, False), (4096, False), (2001, True)]):
            full = dna(length + length // 2)
            first, second = (full[:length], full[-length:]) if not outie else (full[-length:], full[:length])
            long_cases.append((record(f"@long{i}/1", first), record(f"@long{i}/2", rc(second))))
        fresh = [
            ("cleanup-headers", frontend, ["4", "200", ".25", "33", "false", "false"], False, []),
            ("long-innie-outie", long_cases, ["10", "200", ".25", "33", "true", "false"], False, []),
            ("interleaved-gzip-phred64", [(record("@phred/1", a, "@" + "~" * 24), record("@phred/2", b, "I" * 25))], ["10", "65", ".25", "64", "true", "true"], True, []),
            ("positional", [(record("@first/1", a), record("@different/2", b))], ["10", "65", ".25", "33", "false", "false"], False, ["--pair-policy", "positional"]),
        ]
        for name, pairs, settings, interleaved, extra in fresh:
            directory = args.work / ("original-" + name); directory.mkdir()
            r1 = directory / "reads1.fastq"; r2 = directory / "reads2.fastq"
            r1.write_text("".join(x for x, _ in pairs), encoding="utf-8"); r2.write_text("".join(y for _, y in pairs), encoding="utf-8")
            native_input = r1
            command = [args.original, r1, r2, "-m", settings[0], "-M", settings[1], "-x", settings[2], "-p", settings[3], "-t", "1", "-d", directory, "-o", "actual"]
            if settings[4] == "true": command.append("--allow-outies")
            if settings[5] == "true": command.append("--cap-mismatch-quals")
            if interleaved:
                native_input = directory / "interleaved.data"
                native_input.write_bytes(gzip.compress("".join(x + y for x, y in pairs).encode(), mtime=0))
                command[1:3] = ["--interleaved-input", native_input]
            call = launch(command, directory, "original"); call["name"] = name; report["fresh_original_calls"].append(call)
            output = compare(name, native_input, r2, directory, "actual", settings, extra, interleaved)
            if name == "cleanup-headers":
                warnings = (output / "WARNINGS.md").read_text()
                assert all(term in warnings for term in ["Required action", "converted", "last slash", "plus identifiers", "whitespace"])
            if name == "long-innie-outie":
                assert all(len(row[1]) > 1000 for row in fastq(output / "merged.fastq.gz"))
                counts = dict(line.split("\t") for line in (output / "results.tsv").read_text().splitlines()[1:]); assert counts["outie_merged"] == "1"
        # Packaged invalid-input controls must publish diagnostics, never success.
        directory = args.work / "controls"; directory.mkdir()
        for name, first, second, extra in [
            ("identity", record("@A/1", a), record("@wrong/2", b), []),
            ("extra-mate", record("@A/1", a), record("@A/2", b) + record("@B/2", b), []),
            ("malformed", "@A/1\nACGT\n+\nIII\n", record("@A/2", b), []),
            ("empty", "", "", []),
            ("bad-cap", record("@A/1", a), record("@A/2", b), ["--min-overlap", "11", "--max-overlap", "10"]),
            ("nonfinite", record("@A/1", a), record("@A/2", b), ["--max-mismatch-density", "NaN"]),
        ]:
            work = directory / name; work.mkdir(); r1 = work / "r1.fq"; r2 = work / "r2.fq"
            r1.write_text(first); r2.write_text(second); output = work / "output"
            command = [args.java, "-jar", args.jar, "flash-reads", "--reads", r1, "--reads2", r2, "--phred-offset", "33", "--out", output] + extra
            call = launch(command, work, "native", 2); call["name"] = name
            assert not (output / "results.tsv").exists() and json.loads((output / "analysis-status.json").read_text())["status"] == "failed"
            assert "Required action" in (output / "WARNINGS.md").read_text()
            report["control_calls"].append(call)
        # An ASCII recipe path avoids Windows native argv loss for Unicode data
        # paths. Expected bytes come from the independent original cleanup run.
        expected_unicode = [row for row in fastq(args.work / "original-cleanup-headers/actual.extendedFrags.fastq") if row[0].startswith("@汉")]
        assert len(expected_unicode) == 1
        for index, java in enumerate([args.java, *args.additional_java]):
            work = args.work / ("recipe-jvm-%d" % index); work.mkdir()
            r1 = work / "样本一.fastq"; r2 = work / "样本二.fastq"; output = work / "合并结果"
            r1.write_text(record("@汉/1#条码 comment", a), encoding="utf-8"); r2.write_text(record("@汉/2#条码 comment", b), encoding="utf-8")
            recipe = work / "options.json"
            recipe.write_text(json.dumps({"reads": str(r1.resolve()), "reads2": str(r2.resolve()), "out": str(output.resolve()), "phred-offset": "33", "min-overlap": "10", "max-overlap": "200", "gzip": "true"}, ensure_ascii=False), encoding="utf-8")
            call = launch([java, "-jar", args.jar, "flash-reads", "--options-file", recipe, "--gzip", "false"], work, "native")
            assert fastq(output / "merged.fastq") == expected_unicode and not (output / "merged.fastq.gz").exists()
            assert (output / "options-input.json").read_bytes() == recipe.read_bytes()
            call.update(java=str(java), recipe_sha256=sha(recipe), original_reference="original-cleanup-headers/actual.extendedFrags.fastq", pairs=1)
            report["unicode_recipe_calls"].append(call)
        assert sha(args.jar) == report["jar_sha256"] and sha(args.original) == ORIGINAL_SHA
        report.update(status="passed", pairs=sum(c["pairs"] for c in report["native_calls"]),
                      original_reference_processes=len(report["reused_original_profiles"]) + len(report["fresh_original_calls"]),
                      scope="Complete ordered FASTQ headers, sequence, quality, merged/unmerged partitions, counts, ledger cardinality and merged-length histogram across captured original workflows. New original cases cover cleanup/barcodes/comments/Unicode, >1000 bases/outies, gzip interleaved Phred64 and explicit positional pairing. Six packaged invalid-input controls plus actual Unicode input/output recipe checks under the named JVMs. No whole-paper or original threading/text-report parity.")
    except Exception as failure:
        report.update(status="failed", error=repr(failure), required_action="Inspect retained native/original stdout/stderr and correct the named mismatch before any catalog promotion.")
        raise
    finally:
        report["duration_seconds"] = round(time.monotonic() - started, 3)
        (args.work / "verification.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(json.dumps({key: report.get(key) for key in ["status", "pairs", "original_reference_processes", "duration_seconds", "error"]}))


if __name__ == "__main__":
    main()
