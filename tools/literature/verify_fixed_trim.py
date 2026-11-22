"""Freeze independent fixed-span references from actual pinned Cutadapt execution."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--python", type=Path, default=ROOT / "build/omics-sequence-tools-env" / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python"))
    args = parser.parse_args()
    python = str(args.python.resolve())
    version = subprocess.check_output([python, "-X", "utf8", "-I", "-m", "cutadapt", "--version"], text=True).strip()
    if version != "5.2":
        raise ValueError("Reference generation requires Cutadapt 5.2, found " + version)
    destination = ROOT / "src/test/resources/fixed-trim"
    destination.mkdir(parents=True, exist_ok=True)
    logs = ROOT / "build/literature-review/fixed-trim-reference"
    logs.mkdir(parents=True, exist_ok=True)
    sequence = "ACGTRYSWKMBDHVNACGTAC"
    lengths = [1, 2, 3, 4, 5, 6, 8, 10, 12, 20]
    records = [("read" + str(i), sequence[:length], "".join(chr(33 + j % 40) for j in range(length))) for i, length in enumerate(lengths)]
    fixture = destination / "input.fastq"
    fixture.write_text("".join("@" + id_ + " original comment\n" + s + "\n+\n" + q + "\n" for id_, s, q in records), encoding="utf-8")
    cases = [("identity", 1, 0, 1), ("post_adapter_first_nt", 2, 0, 1), ("span_2_6", 2, 6, 1), ("first_four", 1, 4, 1),
             ("start_8", 8, 0, 1), ("span_3_12", 3, 12, 1), ("minimum_8", 1, 0, 8), ("span_minimum", 2, 8, 5)]
    rows = ["case\tfirst_base\tlast_base\tminimum_length\tread_id\texpected_sequence\texpected_quality"]
    commands = []
    for name, first, last, minimum in cases:
        output = logs / (name + ".fastq")
        command = [python, "-X", "utf8", "-I", "-m", "cutadapt", "--cut", str(first - 1), "--minimum-length", str(minimum), "-o", str(output)]
        if last:
            command += ["--length", str(last - first + 1)]
        command.append(str(fixture))
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        (logs / (name + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
        lines = output.read_text(encoding="utf-8").splitlines()
        kept = {lines[i][1:].split()[0]: (lines[i + 1], lines[i + 3]) for i in range(0, len(lines), 4)}
        for id_, _, _ in records:
            s, q = kept.get(id_, ("-", "-"))
            rows.append("\t".join(map(str, [name, first, last, minimum, id_, s, q])))
        commands.append(command)
    (destination / "reference.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    metadata = {"reference": "Actual Cutadapt 5.2 fixed-cut/length/filter execution as an independent implementation of span clipping",
        "cases": len(cases), "observations": len(cases) * len(records), "commands": commands,
        "source_behavior": "https://github.com/agordon/fastx_toolkit/blob/master/src/fastx_trimmer/fastx_trimmer.c",
        "scope": "Positive one-based inclusive span and minimum retained length; no complete FASTX binary/FASTA/Phred64 parity claim",
        "files": {name: hashlib.sha256((destination / name).read_bytes()).hexdigest() for name in ("input.fastq", "reference.tsv")}}
    (destination / "provenance.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"cases": len(cases), "reference_observations": metadata["observations"], "cutadapt": version}))


if __name__ == "__main__":
    main()
