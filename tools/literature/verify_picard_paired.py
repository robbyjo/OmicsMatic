"""Retained real Picard paired-end duplicate/metrics reference fixture."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/literature/omics-2023-2026"


def metric_rows(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    result = []
    for i, line in enumerate(lines):
        if line.startswith("LIBRARY\t") or line.startswith("CATEGORY\t"):
            keys = line.split("\t")
            for row in lines[i+1:]:
                if not row or row.startswith("#"):
                    break
                values = row.split("\t")
                assert len(keys) == len(values), "Metrics row width mismatch"
                result.append(dict(zip(keys, values)))
    assert result, "Missing Picard metrics table"
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--picard", type=Path, default=ROOT / "build/omics-picard/picard.jar")
    args = parser.parse_args()
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out = ROOT / "build/literature-review" / ("picard-paired-" + stamp)
    out.mkdir(parents=True, exist_ok=False)
    sam = out / "paired.sam"
    header = "@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg1\tSM:sample1\tLB:lib1\tPL:ILLUMINA\n"
    records = []
    for flag, pos, mate, size in [(99, 100, 200, 120), (147, 200, 100, -120)]:
        for pixel, quality in [(100, "I"), (200, "H")]:
            records.append(f"INST:1:FC:1:1101:{pixel}:{pixel}\t{flag}\tchr1\t{pos}\t60\t20M\t=\t{mate}\t{size}\t{'A'*20}\t{quality*20}\tRG:Z:rg1\tNM:i:0\n")
    sam.write_text(header + "".join(records), encoding="utf-8")
    reference = out / "reference.fa"
    reference.write_text(">chr1\n" + "A"*1000 + "\n", encoding="utf-8", newline="\n")
    (out / "reference.fa.fai").write_text("chr1\t1000\t6\t1000\t1001\n", encoding="utf-8")
    (out / "reference.dict").write_text("@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n", encoding="utf-8")
    attempts = []

    def run(name, method, source, *extra):
        destination = out / name
        command = ["java", "-jar", str(args.jar.resolve()), "alignment-tools", "--method", method,
            "--input", str(source), "--picard", str(args.picard.resolve()), "--memory-mb", "512",
            "--out", str(destination), "--no-config", *extra]
        process = subprocess.run(command, capture_output=True, text=True, timeout=180, cwd=ROOT)
        (out / (name + ".log")).write_text(process.stdout + process.stderr, encoding="utf-8")
        attempts.append({"name": name, "command": command, "exit_code": process.returncode, "output": str(destination)})
        assert process.returncode == 0, process.stdout + process.stderr
        assert (destination / "results.tsv").is_file()
        return destination

    status, failure = "failed", None
    try:
        run("validate-input", "validate", sam)
        marked = run("mark", "mark-duplicates", sam)
        metrics = metric_rows(marked / "duplication_metrics.txt")
        assert len(metrics) == 1
        assert metrics[0]["READ_PAIRS_EXAMINED"] == "2"
        assert metrics[0]["READ_PAIR_DUPLICATES"] == "1"
        assert float(metrics[0]["PERCENT_DUPLICATION"]) == 0.5
        removed = run("remove", "mark-duplicates", sam, "--remove-duplicates", "true")
        run("validate-retained-pair", "validate", removed / "alignment.bam")
        summary = run("retained-pair-metrics", "alignment-summary", removed / "alignment.bam", "--reference", str(reference))
        rows = {r["CATEGORY"]: r for r in metric_rows(summary / "alignment_metrics.txt")}
        assert rows["PAIR"]["TOTAL_READS"] == "2" and rows["PAIR"]["PF_READS_ALIGNED"] == "2"
        assert rows["FIRST_OF_PAIR"]["TOTAL_READS"] == rows["SECOND_OF_PAIR"]["TOTAL_READS"] == "1"
        status = "passed"
    except Exception as error:
        failure = str(error)
    result = {"status": status, "error": failure, "recorded_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "jar": str(args.jar.resolve()), "jar_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        "picard_jar_sha256": hashlib.sha256(args.picard.read_bytes()).hexdigest(), "attempts": attempts,
        "source_fixture": str(sam), "expected": "Two complete pairs, one duplicate pair; removal retains one valid pair (two aligned records)",
        "scope": "Independent known paired-end SAM fixture, real official Picard 3.5.0. No old-version or complete paper-pipeline parity claim."}
    report = DATA / "picard-paired-validation.json"
    if report.exists():
        previous = json.loads(report.read_text(encoding="utf-8"))
        result["history"] = previous.get("history", []) + [{k: v for k, v in previous.items() if k != "history"}]
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))
    return 0 if status == "passed" else 2


if __name__ == "__main__":
    raise SystemExit(main())
