"""Observe unmodified Trim Galore releases; no JLinAlg expected-output formula.

Private raw outputs are always retained. --publish-fixtures only creates a new
fixture directory. Without it, newly observed tables must match the frozen ones.
This is a reference generator, not an installer or a sequencing-data workflow.
"""
import argparse
import csv
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "src/test/resources/trim-galore"
SOURCES = {
    "0.6.7": "ac1e5d51846877ee1735c82825dc42ffd142fce32b79c70d1193122e3dff12d4",
    "0.6.10": "0ea7a35d1f949790ecd329db5c3c14237a9c0591f246735048baf3a777c3995a",
}
HEADER = ("case release mode clip5_1 clip3_1 clip5_2 clip3_2 min_length max_length max_n rescue orphan_min1 orphan_min2 hard_count "
          "header1 seq1 qual1 header2 seq2 qual2 paired_retained out1_seq out1_qual out2_seq out2_qual orphan1_seq orphan1_qual orphan2_seq orphan2_qual").split()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cygpath(path):
    value = str(path.resolve()).replace("\\", "/")
    if len(value) > 2 and value[1] == ":":
        return "/cygdrive/" + value[0].lower() + value[2:]
    return value


def read_fastq(path):
    lines = path.read_text(encoding="ascii").splitlines()
    assert len(lines) % 4 == 0, (path, "Incomplete original FASTQ")
    result = {}
    for index in range(0, len(lines), 4):
        header, seq, plus, qual = lines[index:index + 4]
        assert header.startswith("@") and plus.startswith("+") and len(seq) == len(qual)
        key = header.split()[0][1:].removesuffix("/1").removesuffix("/2")
        assert key not in result
        result[key] = (header, seq, qual)
    return result


def inputs(paired):
    lengths = [0, 1, 2, 3, 4, 5, 7, 8, 14, 15, 19, 20, 21, 30, 34, 35, 36, 40, 49, 50, 51, 60, 61, 75, 99, 100, 101, 130]
    pairs = [(n, n) for n in lengths] + [(a, b) for a, b in [(2, 60), (60, 2), (34, 40), (40, 34), (40, 49), (49, 40), (60, 130), (130, 60), (0, 50), (50, 0)]]
    result = []
    for index, (a, b) in enumerate(pairs if paired else [(n, 0) for n in lengths]):
        seq1 = ("ACGT" * (a // 4 + 1))[:a]
        seq2 = ("TGCA" * (b // 4 + 1))[:b]
        result.append(("r%03d" % index, seq1, seq2))
    for index, seq in enumerate(["ACGNACGT", "ACGNNNNN", "nnnnACGT", "NNNN", "NACGTACGTACGTACGTACG", "ACGTNACGTN", "ACGTNNACGT", "nACGnACGnACG"]):
        result.append(("n%03d" % index, seq, "ACGT" * 10))
        if paired:
            result.append(("b%03d" % index, "ACGT" * 10, seq))
    return result


def write_inputs(directory, paired):
    reads = inputs(paired)
    for mate in range(1, 3 if paired else 2):
        with (directory / ("reads%d.fastq" % mate)).open("w", encoding="ascii", newline="\n") as stream:
            for key, a, b in reads:
                seq = a if mate == 1 else b
                qual = "".join(chr(33 + (i % 41)) for i in range(len(seq)))
                stream.write("@%s/%d original comment\n%s\n+\n%s\n" % (key, mate, seq, qual))
    return reads


def cases():
    common = dict(clip5_1=0, clip3_1=0, clip5_2=0, clip3_2=0, min_length=1, max_length=0, max_n=None,
                  rescue=False, orphan_min1=None, orphan_min2=None, hard_count=0)
    definitions = [
        ("hard5-4", "hard5", dict(hard_count=4)), ("hard3-4", "hard3", dict(hard_count=4)),
        ("hard5-50", "hard5", dict(hard_count=50)), ("hard3-50", "hard3", dict(hard_count=50)),
        ("single-neutral", "single", {}),
        ("single-clips", "single", dict(clip5_1=3, clip3_1=4, min_length=2)),
        ("single-n0", "single", dict(max_n=0)),
        ("single-n1-lengths", "single", dict(max_n=1, min_length=8, max_length=50)),
        ("single-min50-max40", "single", dict(min_length=50, max_length=40)),
        ("pair-neutral", "pair", {}),
        ("pair-clips", "pair", dict(clip5_1=3, clip3_1=4, clip5_2=4, clip3_2=3, min_length=2)),
        ("pair-n0-rescue", "pair", dict(max_n=0, min_length=20, rescue=True)),
        ("pair-rescue-before-max", "pair", dict(min_length=20, max_length=50, rescue=True)),
        ("pair-default-rescue-below-min", "pair", dict(min_length=50, max_length=60, rescue=True)),
        ("pair-default-rescue-no-max", "pair", dict(min_length=50, rescue=True)),
        ("pair-explicit-rescue", "pair", dict(min_length=20, rescue=True, orphan_min1=35, orphan_min2=50)),
        ("pair-n1-clips", "pair", dict(max_n=1, min_length=20, clip5_1=2, clip3_2=2)),
        ("pair-wgbs-fixed15", "pair", dict(min_length=20, clip3_1=15, clip5_2=15)),
        ("single-nfraction-half", "single", dict(max_n=0.5)),
        ("single-nfraction-tenth", "single", dict(max_n=0.1)),
        ("pair-nfraction-half", "pair", dict(max_n=0.5, min_length=20, rescue=True)),
        ("pair-nfraction-tenth", "pair", dict(max_n=0.1, min_length=1)),
    ]
    for name, mode, parameters in definitions:
        yield dict(common, **parameters, case=name, mode=mode)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--perl", type=Path, required=True)
    parser.add_argument("--cutadapt", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--publish-fixtures", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    steps, rows, sources = [], [], {}
    error, status = None, "failed"
    environment = dict(os.environ, LC_ALL="C", PYTHONNOUSERSITE="1", PYTHONDONTWRITEBYTECODE="1", PERL_HASH_SEED="0", PERL_PERTURB_KEYS="0")
    environment["PATH"] = str(args.perl.resolve().parent) + os.pathsep + str(args.cutadapt.resolve().parent) + os.pathsep + environment.get("PATH", "")

    def run(name, command, cwd, timeout=180):
        command = list(map(str, command))
        completed = subprocess.run(command, cwd=cwd, env=environment, capture_output=True, timeout=timeout)
        log = cwd / (name + ".log")
        log.write_bytes(completed.stdout + completed.stderr)
        steps.append(dict(name=name, command=command, cwd=str(cwd), exit_code=completed.returncode, log=str(log.relative_to(out)), log_sha256=sha(log)))
        assert completed.returncode == 0, (name, completed.returncode, completed.stderr[-1500:])
        return completed

    try:
        assert args.perl.is_file() and args.cutadapt.is_file(), "A permitted Perl and Cutadapt are required; analysis skipped until supplied"
        for version, digest in SOURCES.items():
            script = args.source_root.resolve() / version / "trim_galore"
            assert sha(script) == digest, (version, "Original source checksum differs")
            sources[version] = dict(script_sha256=digest, source_url="https://raw.githubusercontent.com/FelixKrueger/TrimGalore/" + version + "/trim_galore")
        perl_version = run("perl-version", [args.perl, "-v"], out).stdout.decode("ascii", errors="replace")
        cutadapt_version = run("cutadapt-version", [args.cutadapt, "--version"], out).stdout.decode("ascii").strip()
        transport = ROOT / "src/main/resources/org/jlinalg/omics/trim_galore_cutadapt.py"
        bridge = out / "cutadapt-transport"
        bridge_command = [cygpath(Path(sys.executable)), str(transport.resolve()), "--cutadapt", str(args.cutadapt.resolve()),
                          "--expected-sha256", sha(args.cutadapt), "--audit-dir", "transport-audit", "--"]
        bridge.write_text("#!/bin/sh\nexec " + " ".join(shlex.quote(value) for value in bridge_command) + ' "$@"\n', encoding="utf-8", newline="\n")
        run("bridge-executable", [args.perl.resolve().parent / "chmod.exe", "+x", cygpath(bridge)], out)
        for version in SOURCES:
            for case in cases():
                if version == "0.6.7" and case["max_n"] is not None and 0 < case["max_n"] < 1:
                    continue
                if version == "0.6.7" and case["mode"] == "pair" and case["max_length"] > 0:
                    continue
                directory = out / (version + "-" + case["case"])
                directory.mkdir()
                paired = case["mode"] == "pair"
                write_inputs(directory, paired)
                read1 = read_fastq(directory / "reads1.fastq")
                read2 = read_fastq(directory / "reads2.fastq") if paired else {}
                # Independently verify that the neutral upstream phase really is
                # an identity operation for every original record, including case.
                neutral = run("neutral-cutadapt", [args.cutadapt, "-j", "1", "-q", "0", "-a", "X", "reads1.fastq"], directory)
                (directory / "neutral.fastq").write_bytes(neutral.stdout)
                assert read_fastq(directory / "neutral.fastq") == read1
                if paired:
                    neutral2 = run("neutral-cutadapt-r2", [args.cutadapt, "-j", "1", "-q", "0", "-a", "X", "reads2.fastq"], directory)
                    (directory / "neutral2.fastq").write_bytes(neutral2.stdout)
                    assert read_fastq(directory / "neutral2.fastq") == read2
                command = [args.perl, cygpath(args.source_root / version / "trim_galore"), "--path_to_cutadapt", cygpath(bridge), "--dont_gzip", "--phred33"]
                if case["mode"].startswith("hard"):
                    command.extend(["--" + case["mode"].replace("hard", "hardtrim"), str(case["hard_count"])])
                else:
                    command.extend(["--quality", "0", "--length", str(case["min_length"]), "-a", "X"])
                    for key, flag in [("clip5_1", "--clip_R1"), ("clip3_1", "--three_prime_clip_R1"), ("clip5_2", "--clip_R2"), ("clip3_2", "--three_prime_clip_R2"), ("max_length", "--max_length")]:
                        if case[key]:
                            command.extend([flag, str(case[key])])
                    if case["max_n"] is not None:
                        command.extend(["--max_n", str(case["max_n"])])
                    if paired:
                        command.append("--paired")
                        if case["rescue"]:
                            command.append("--retain_unpaired")
                        for key, flag in [("orphan_min1", "--length_1"), ("orphan_min2", "--length_2")]:
                            if case[key] is not None:
                                command.extend([flag, str(case[key])])
                command.extend(["reads1.fastq"] + (["reads2.fastq"] if paired else []))
                run("original-trim-galore", command, directory)
                if case["mode"].startswith("hard"):
                    suffix = "5prime" if case["mode"] == "hard5" else "3prime"
                    emitted1 = read_fastq(directory / ("reads1.%dbp_%s.fq" % (case["hard_count"], suffix)))
                else:
                    emitted1 = read_fastq(directory / ("reads1_val_1.fq" if paired else "reads1_trimmed.fq"))
                emitted2 = read_fastq(directory / "reads2_val_2.fq") if paired else {}
                orphan1 = read_fastq(directory / "reads1_unpaired_1.fq") if paired and case["rescue"] else {}
                orphan2 = read_fastq(directory / "reads2_unpaired_2.fq") if paired and case["rescue"] else {}
                assert set(emitted1) == set(emitted2) if paired else True
                assert not (set(emitted1) & (set(orphan1) | set(orphan2)))
                assert all(set(group) <= set(read1) for group in [emitted1, emitted2, orphan1, orphan2])
                for key, first in read1.items():
                    second = read2.get(key, ("~", "~", "~"))
                    def values(group):
                        if key not in group:
                            return ("~", "~")
                        assert group[key][0] == (second[0] if group is emitted2 or group is orphan2 else first[0])
                        return group[key][1:]
                    parameters = [case[k] if case[k] is not None else "~" for k in HEADER[3:14]]
                    parameters[7] = int(case["rescue"])
                    rows.append([case["case"], version, case["mode"], *parameters, *first, *second, int(paired and key in emitted1), *values(emitted1), *values(emitted2), *values(orphan1), *values(orphan2)])
                print(json.dumps(dict(release=version, case=case["case"], observations=len(read1), original_exit=0)), flush=True)
        table = io.StringIO(newline="")
        writer = csv.writer(table, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_NONE, quotechar=None)
        writer.writerow(HEADER)
        writer.writerows(rows)
        reference = out / "reference.tsv"
        reference.write_text(table.getvalue(), encoding="utf-8", newline="\n")
        if args.publish_fixtures:
            assert not FIXTURES.exists(), "Refuse to replace existing reference fixtures"
            FIXTURES.mkdir()
            shutil.copy2(reference, FIXTURES / reference.name)
        else:
            assert sha(reference) == sha(FIXTURES / reference.name), "New original observations differ from frozen references"
        status = "passed"
    except Exception as failure:
        error = repr(failure)
    record = dict(status=status, error=error, observed_rows=len(rows), original_sources=sources,
                  perl_executable=str(args.perl.resolve()), perl_executable_sha256=sha(args.perl) if args.perl.is_file() else None,
                  cutadapt_executable=str(args.cutadapt.resolve()), cutadapt_executable_sha256=sha(args.cutadapt) if args.cutadapt.is_file() else None,
                  perl_version=locals().get("perl_version"), cutadapt_version=locals().get("cutadapt_version"),
                  reproducer_sha256=sha(Path(__file__)), steps=steps,
                  transport_sha256=sha(ROOT / "src/main/resources/org/jlinalg/omics/trim_galore_cutadapt.py"),
                  files={str(p.relative_to(out)): sha(p) for p in sorted(out.rglob("*")) if p.is_file()},
                  recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                  scope="Actual unmodified 0.6.7/0.6.10 Perl programs with actual Cutadapt. Neutral upstream phase verified record-for-record; final emissions and hardtrim output observed independently of Java. No RRBS, poly-A, adapter alignment or full historical-version parity claim.")
    (out / "reference-reproduction.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    if args.publish_fixtures and status == "passed":
        provenance = {k: v for k, v in record.items() if k not in ("steps", "files", "perl_version")}
        provenance["reference_sha256"] = sha(out / "reference.tsv")
        provenance["raw_record_sha256"] = sha(out / "reference-reproduction.json")
        (FIXTURES / "reference-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(status=status, error=error, observed_rows=len(rows))), flush=True)
    return 0 if status == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
