"""Exercise the built executable's new native and upstream commands end to end."""
import argparse
import csv
import datetime as dt
import hashlib
import gzip
import json
import math
from pathlib import Path
import random
import subprocess
import sys
import os

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/literature/omics-2023-2026"


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, default=ROOT / "build/cli/omicsmatic-0.3.7.jar")
    parser.add_argument("--python", type=Path, required=True)
    parser.add_argument("--sequence-python", type=Path, default=ROOT / "build/omics-sequence-tools-env" / ("Scripts/python.exe" if sys.platform == 'win32' else "bin/python"))
    parser.add_argument("--fastqc-home", type=Path, default=ROOT / "build/omics-fastqc/FastQC")
    parser.add_argument("--picard", type=Path, default=ROOT / "build/omics-picard/picard.jar")
    parser.add_argument("--harmony-rscript", default=os.environ.get("JLINALG_HARMONY_RSCRIPT", "Rscript"))
    parser.add_argument("--harmony-library", type=Path, default=ROOT / "build/omics-harmony-r")
    args = parser.parse_args()
    jar, python = args.jar.resolve(), args.python.resolve()
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out = ROOT / "build/literature-review" / ("packaged-smoke-" + stamp)
    out.mkdir(parents=True, exist_ok=False)
    records = []

    def run(name, arguments, required_file=None, expected_exit=0):
        destination = out / name
        command = ["java", "-jar", str(jar)] + arguments + ["--out", str(destination), "--no-config"]
        result = subprocess.run(command, capture_output=True, text=True, timeout=600, cwd=ROOT)
        (out / (name + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
        state = "passed" if result.returncode == expected_exit else "skipped-dependency" if result.returncode == 3 else "failed"
        record = {"name": name, "command": command, "exit_code": result.returncode, "status": state,
                  "output_directory": str(destination), "log": str(out / (name + ".log")), "expected_exit_code": expected_exit}
        if result.returncode == expected_exit:
            assert required_file and (destination / required_file).is_file(), name + " omitted results"
            assert (destination / "manifest.yaml").is_file()
            if (destination / "WARNINGS.md").is_file():
                record["qc_warnings"] = str(destination / "WARNINGS.md")
        elif result.returncode == 3:
            assert (destination / "WARNINGS.md").is_file()
            assert not (destination / "results.tsv").exists()
            record["required_action"] = str(destination / "WARNINGS.md")
        records.append(record)
        print(json.dumps(record), flush=True)
        return destination

    fixture = ROOT / "src/test/resources/signature-scores"
    reference = table(fixture / "reference.tsv")
    for method in ("ucell", "aucell", "ssgsea"):
        options = ["signature-score", "--method", method, "--matrix", str(fixture / "expression.tsv"),
                   "--gene-sets", str(fixture / "signatures.gmt")]
        if method != "ssgsea":
            options += ["--max-rank", "30"]
        result = run(method, options, "scores.tsv")
        scores = {row["sample_id"]: row for row in table(result / "scores.tsv")}
        for row in reference:
            if row["method"] == method and row["alpha"] == "0.25" and (method == "ssgsea" or row["max_rank"] == "30"):
                assert math.isclose(float(row["expected"]), float(scores[row["sample"]][row["signature"]]), abs_tol=2e-12)
    first, second = out / "interval-a.tsv", out / "interval-b.tsv"
    first.write_text("chrom\tstart\tend\tid\nchr1\t0\t100\tquery\n")
    second.write_text("chrom\tstart\tend\tid\nchr1\t10\t40\ta\nchr1\t20\t60\tb\n")
    for method in ("intersect", "merge", "subtract", "coverage"):
        options = ["intervals", "--method", method, "--input", str(first), "--genome-build", "GRCh38"]
        if method != "merge":
            options += ["--mask", str(second)]
        result = run("interval-" + method, options, "intervals.tsv")
        if method == "coverage":
            assert table(result / "intervals.tsv")[0]["covered_bases"] == "50"
    pca, metadata = out / "pca.tsv", out / "cells.tsv"
    rng = random.Random(91)
    with pca.open("w", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["obs_id", "PC1", "PC2", "PC3", "PC4"])
        for i in range(120):
            writer.writerow(["C" + str(i)] + [20 * (i // 40) + rng.gauss(0, 1) for _ in range(4)])
    metadata.write_text("obs_id\tbatch\n" + "".join("C" + str(i) + "\t" + ("A" if i % 2 else "B") + "\n" for i in range(120)))
    native = run("cell-tsne", ["cell-embed", "--method", "tsne", "--matrix", str(pca),
                               "--perplexity", "10", "--iterations", "750", "--seed", "17"], "results.tsv")
    assert [row["obs_id"] for row in table(native / "results.tsv")] == ["C" + str(i) for i in range(120)]
    assert (native / "session.yaml").is_file() and (native / "diagnostics.tsv").is_file()
    for row in table(native / "results.tsv"):
        assert math.isfinite(float(row["TSNE1"])) and math.isfinite(float(row["TSNE2"]))
    for method in ("umap", "leiden", "louvain", "harmony"):
        options = ["cell-embed", "--method", method, "--matrix", str(pca), "--python", str(python), "--neighbors", "10", "--seed", "1"]
        if method == "harmony":
            options += ["--metadata", str(metadata), "--batch", "batch"]
        result = run("cell-" + method, options, "results.tsv")
        if (result / "results.tsv").exists():
            assert [row["obs_id"] for row in table(result / "results.tsv")] == ["C" + str(i) for i in range(120)]
    harmony_matrix, harmony_meta = out / "harmony-pca.tsv", out / "harmony-cells.tsv"
    hrng = random.Random(707)
    with harmony_matrix.open("w", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["obs_id", "PC1", "PC2", "PC3", "PC4"])
        for i in range(200):
            writer.writerow(["H" + str(i), (-6 if i < 100 else 6) + hrng.gauss(0, 0.7), (-3 if i % 2 == 0 else 3) + hrng.gauss(0, 0.7), hrng.gauss(0, 0.7), hrng.gauss(0, 0.7)])
    harmony_meta.write_text("obs_id\tbatch\n" + "".join("H" + str(i) + "\t" + ("A" if i % 2 == 0 else "B") + "\n" for i in range(199, -1, -1)))
    harmony_r = run("cell-harmony-r", ["cell-embed", "--method", "harmony", "--engine", "r", "--rscript", args.harmony_rscript,
        "--r-library", str(args.harmony_library.resolve()), "--matrix", str(harmony_matrix), "--metadata", str(harmony_meta), "--batch", "batch", "--clusters", "2", "--iterations", "20", "--seed", "17"], "results.tsv")
    if (harmony_r / "results.tsv").is_file():
        assert [r["obs_id"] for r in table(harmony_r / "results.tsv")] == ["H" + str(i) for i in range(200)]
        before, after = table(harmony_matrix), table(harmony_r / "results.tsv")
        shift = lambda rows, key: abs(sum(float(r[key]) for i, r in enumerate(rows) if i % 2) / 100 - sum(float(r[key]) for i, r in enumerate(rows) if i % 2 == 0) / 100)
        assert shift(after, "Harmony2") < shift(before, "PC2") * 0.3
    native_reads = out / "adapter-free.fastq"
    native_reads.write_text("@A original comment\nACGT\n+\n!#$%\n@B\nT\n+\nI\n@C\nTTGCA\n+\nIIIII\n")
    clipped = run("native-fixed-trim", ["sequence-qc", "--method", "trim-fixed", "--reads", str(native_reads), "--first-base", "2"], "results.tsv")
    if (clipped / "results.tsv").is_file():
        with gzip.open(clipped / "trimmed_R1.fastq.gz", "rt") as stream:
            lines = stream.read().splitlines()
        assert lines == ["@A original comment", "CGT", "+", "#$%", "@C", "TGCA", "+", "IIII"]
    emptied = run("native-fixed-empty", ["sequence-qc", "--method", "trim-fixed", "--reads", str(native_reads), "--first-base", "20"], "results.tsv")
    if (emptied / "results.tsv").is_file():
        assert (emptied / "WARNINGS.md").is_file()
        assert {r["metric"]: r["value"] for r in table(emptied / "results.tsv")}["output_reads_or_pairs"] == "0"
    # Authentic sequence-tool runs; expected trimming is specified independently.
    raw1, raw2 = out / "raw_R1.fastq", out / "raw_R2.fastq"
    adapter, mate_adapter = "AGATCGGAAGAGC", "CTGTCTCTTATA"
    sequences = ["CGTACCGTTA" + adapter, "TGCCTACGTA", adapter, "CGTACCGTTAAGATCG", "GGCCTTACCATTTT"]
    raw1.write_text("".join(f"@read{i}/1\n{s}\n+\n" + ("IIIIIIIIII!!!!" if i == 4 else "I" * len(s)) + "\n" for i, s in enumerate(sequences)))
    mate = "TTGCAACGTC" + mate_adapter
    raw2.write_text("".join(f"@read{i}/2\n{mate}\n+\n{'I' * len(mate)}\n" for i in range(5)))
    common = ["sequence-qc", "--method", "cutadapt", "--python", str(args.sequence_python.resolve()), "--reads", str(raw1),
              "--adapter3", adapter, "--minimum-length", "6", "--quality-cutoff", "20", "--error-rate", "0"]
    single = run("sequence-cutadapt-single", common, "results.tsv")
    paired = run("sequence-cutadapt-paired", common + ["--reads2", str(raw2), "--adapter3-2", mate_adapter], "results.tsv")
    for trimmed in (single, paired):
        if (trimmed / "results.tsv").is_file():
            counts = {r["metric"]: r["value"] for r in table(trimmed / "results.tsv")}
            assert counts["input_reads_or_pairs"] == "5" and counts["output_reads_or_pairs"] == "4"
    qc = run("sequence-fastqc", ["sequence-qc", "--method", "fastqc", "--fastqc-home", str(args.fastqc_home.resolve()),
                                "--reads", str(raw1), "--reads2", str(raw2)], "results.tsv")
    if (qc / "results.tsv").is_file() and (paired / "cutadapt.json").is_file() and (paired / "results.tsv").is_file():
        reports = out / "qc-reports.tsv"
        reports.write_text("sample_id\tfastqc_zip\tcutadapt_json\nlibrary_R1\t" + str(qc / "raw_R1_fastqc.zip") + "\t" + str(paired / "cutadapt.json")
                           + "\nlibrary_R2\t" + str(qc / "raw_R2_fastqc.zip") + "\t\n")
        combined = run("sequence-multiqc", ["sequence-qc", "--method", "multiqc", "--python", str(args.sequence_python.resolve()),
                                            "--reports", str(reports)], "results.tsv")
        if (combined / "results.tsv").is_file():
            assert len(table(combined / "results.tsv")) == 3
            assert {r["input_reads_or_pairs"] for r in table(combined / "results.tsv")} == {"5"}
    else:
        dependent = out / "sequence-multiqc"; dependent.mkdir()
        warning = dependent / "WARNINGS.md"
        warning.write_text("# ANALYSIS SKIPPED - UPSTREAM QC UNAVAILABLE\n\nRequired action: resolve the FastQC/Cutadapt errors or dependency skips above, complete those analyses, then rerun MultiQC with their real reports. No MultiQC command was executed.\n")
        records.append({"name": "sequence-multiqc", "command": None, "exit_code": None, "status": "skipped-upstream-analysis", "required_action": str(warning)})
    # Supplied weights and isolates must survive without a substituted kNN graph.
    nodes, edges = out / "nodes.tsv", out / "edges.tsv"
    nodes.write_text("obs_id\nA\nB\nC\nD\nE\nF\nisolate\n")
    edges.write_text("source\ttarget\tweight\nB\tA\t7\nA\tC\t8\nB\tC\t9\nD\tE\t10\nD\tF\t11\nE\tF\t12\nC\tD\t0.001\n")
    for method in ("leiden", "louvain"):
        supplied = run("graph-" + method, ["cell-embed", "--method", method, "--nodes", str(nodes), "--graph", str(edges),
            "--python", str(python), "--seed", "17"], "results.tsv")
        if (supplied / "results.tsv").is_file():
            rows = table(supplied / "results.tsv"); memberships = {r["obs_id"]: r["cluster"] for r in rows}
            assert list(memberships) == ["A", "B", "C", "D", "E", "F", "isolate"]
            assert memberships["A"] == memberships["B"] == memberships["C"]
            assert memberships["D"] == memberships["E"] == memberships["F"]
            assert len({memberships["A"], memberships["D"], memberships["isolate"]}) == 3
            assert table(supplied / "graph.tsv")[0] == {"source": "A", "target": "B", "weight": "7.0"}
    # Known SAM records exercise the packaged Picard commands and distinct QC-error status.
    sam = out / "alignment.sam"
    header = "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg1\tSM:sample1\tLB:lib1\tPL:ILLUMINA\n"
    reads = "".join(f"INST:1:FC:1:1101:{i}:{i}\t0\tchr1\t{pos}\t60\t20M\t*\t0\t0\t{'A'*20}\t{'I'*20}\tRG:Z:rg1\tNM:i:0\n"
        for i, pos in [(100, 200), (200, 100), (300, 100)])
    reads += "unmapped\t4\t*\t0\t0\t*\t*\t0\t0\t" + "A"*20 + "\t" + "I"*20 + "\tRG:Z:rg1\n"
    sam.write_text(header + reads)
    def alignment(method, input_file, *extra):
        return ["alignment-tools", "--method", method, "--input", str(input_file), "--picard", str(args.picard.resolve()), "--memory-mb", "512", *extra]
    sorted_bam = run("picard-sort", alignment("sort", sam, "--create-index", "true"), "alignment.bam")
    run("picard-queryname", alignment("sort", sam, "--sort-order", "queryname"), "alignment.bam")
    if (sorted_bam / "alignment.bam").is_file():
        assert (sorted_bam / "alignment.bai").is_file()
        for remove in (False, True):
            marked = run("picard-" + ("remove" if remove else "mark"), alignment("mark-duplicates", sorted_bam / "alignment.bam",
                "--remove-duplicates", str(remove).lower()), "alignment.bam")
            if (marked / "results.tsv").is_file():
                assert (marked / "duplication_metrics.txt").is_file()
        validated = run("picard-validate", alignment("validate", sorted_bam / "alignment.bam"), "validation.txt")
        if (validated / "results.tsv").is_file():
            assert "No errors found" in (validated / "validation.txt").read_text()
        reference = out / "reference.fa"; reference.write_text(">chr1\n" + "A"*1000 + "\n", newline="\n")
        (out / "reference.fa.fai").write_text("chr1\t1000\t6\t1000\t1001\n")
        (out / "reference.dict").write_text("@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n")
        metrics = run("picard-metrics", alignment("alignment-summary", sorted_bam / "alignment.bam", "--reference", str(reference)), "alignment_metrics.txt")
        if (metrics / "results.tsv").is_file():
            lines = (metrics / "alignment_metrics.txt").read_text().splitlines()
            i = next(i for i, line in enumerate(lines) if line.startswith("CATEGORY\t"))
            columns = lines[i].split("\t"); row = lines[i+1].split("\t")
            assert row[columns.index("TOTAL_READS")] == "4" and row[columns.index("PF_READS_ALIGNED")] == "3"
    else:
        for name in ("picard-mark", "picard-remove", "picard-validate", "picard-metrics"):
            records.append({"name": name, "command": None, "exit_code": None, "status": "skipped-upstream-analysis",
                "required_action": str(sorted_bam / "WARNINGS.md")})
    bad_sam = out / "invalid-alignment.sam"; bad_sam.write_text(header + reads.replace("\t200\t60\t20M", "\t995\t60\t20M", 1))
    invalid = run("picard-validation-errors", alignment("validate", bad_sam), "validation.txt", expected_exit=4)
    if (invalid / "results.tsv").is_file():
        assert "CIGAR_MAPS_OFF_REFERENCE" in (invalid / "validation.txt").read_text()
        assert "Required action" in (invalid / "WARNINGS.md").read_text()
    result = {"jar": str(jar), "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
              "source_build_not_released": True, "records": records,
              "passed": sum(r["status"] == "passed" for r in records),
              "skipped": sum(r["status"].startswith("skipped") for r in records),
              "failed": sum(r["status"] == "failed" for r in records),
              "scope": "Packaged CLI small-fixture validation; no real study data or complete upstream pipeline parity claimed."}
    report_file = DATA / "packaged-validation.json"
    if report_file.exists():
        previous = json.loads(report_file.read_text(encoding="utf-8"))
        result["validation_history"] = previous.get("validation_history", []) + [{k: v for k, v in previous.items() if k != "validation_history"}]
    report_file.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in {"records", "validation_history"}}))
    return 2 if result["failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
