#!/usr/bin/env python3
"""Exercise the packaged typed GLM against original reports and independent OLS.

No downloads or installs. Synthetic public fixtures only. Original Firth comparisons
test adapter/reportability fidelity, not an independent Firth implementation.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import decimal
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "src/test/resources/org/jlinalg/genetics/plink-associations"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(path):
    with Path(path).open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def raw_table(path):
    lines = Path(path).read_text(encoding="utf-8").splitlines()
    names = lines[0].lstrip("#").split()
    return [dict(zip(names, line.split(), strict=True)) for line in lines[1:] if line.strip()]


def reference(name):
    lines = gzip.decompress((SOURCE / (name + ".tsv.gz")).read_bytes()).decode().splitlines()
    names = lines[0].lstrip("#").split()
    return [dict(zip(names, line.split(), strict=True)) for line in lines[1:] if line.strip()]


def write_table(path, rows):
    with Path(path).open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]), delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def check_printed(value, raw):
    """Accept the last printed decimal's rounding interval, plus arithmetic ULPs."""
    reported = float(raw)
    unit = float(decimal.Decimal(1).scaleb(decimal.Decimal(raw).as_tuple().exponent))
    assert abs(value - reported) <= .51 * unit + 16 * math.ulp(value), (value, raw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--java", action="append", required=True,
                        help="Actual JVM executable; repeat for multiple Java versions")
    args = parser.parse_args()
    jar = args.jar.resolve()
    home = args.home.resolve()
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    record = dict(status="running", producer_sha256=sha(__file__), jar_sha256=sha(jar),
                  started_utc=dt.datetime.now(dt.timezone.utc).isoformat(), steps=[],
                  workflows=0, negative_controls=0, canonical_rows=0,
                  frozen_report_rows=0, independent_OLS_rows=0, decimal_log_checks=0,
                  unreportable_rows=0, input_fixture_sha256={},
                  scope="Packaged original additive GLM on synthetic fixtures; original report fidelity, "
                        "independent ordinary OLS, exact cohort/allele mapping and error handling. "
                        "No whole-PLINK, independent Firth solver, paper reproduction, full-project gate or release claim.")
    proof = work / "verification.json"
    shutil.copy2(__file__, work / "producer-snapshot.py")
    assert sha(work / "producer-snapshot.py") == record["producer_sha256"]

    def save():
        proof.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")

    def run(command, label, expected=0):
        start = time.monotonic()
        result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, timeout=180)
        stdout, stderr = work / (label + ".stdout"), work / (label + ".stderr")
        stdout.write_bytes(result.stdout)
        stderr.write_bytes(result.stderr)
        record["steps"].append(dict(command=list(map(str, command)), exit_code=result.returncode,
                                    seconds=time.monotonic() - start,
                                    stdout_sha256=sha(stdout), stderr_sha256=sha(stderr)))
        save()
        assert result.returncode == expected, (label, result.returncode, result.stderr.decode(errors="replace"))
        return result

    try:
        try:
            import numpy as np
            from scipy import stats
        except ImportError as failure:
            record.update(status="skipped-test-dependency", error=repr(failure))
            (work / "WARNINGS.md").write_text(
                "# PLINK PACKAGED VALIDATION SKIPPED\n\n" + str(failure) +
                "\n\nRequired action: where permitted install NumPy and SciPy into a private Python "
                "environment, then rerun this verifier with a new --work directory. Otherwise skip "
                "this validation. No original scientific workflow was run. Java analysis does not "
                "require these Python validation packages.\n", encoding="utf-8")
            return 3
        record["validation_packages"] = {"numpy": np.__version__, "scipy": __import__("scipy").__version__}
        design = json.loads(gzip.decompress((SOURCE / "input-design.json.gz").read_bytes()))
        genotype = np.asarray(design["a1_genotypes"], dtype=float)
        genotype[genotype < 0] = np.nan
        example = ROOT / "examples/plink-tools"
        provenance = json.loads((example / "provenance.json").read_text())
        for name, digest in provenance["files"].items():
            assert sha(example / name) == digest, name
        record["input_fixture_sha256"] = {str(p.relative_to(ROOT)): sha(p)
                                            for p in [*example.iterdir(), *SOURCE.iterdir()] if p.is_file()}
        for name, digest in record["input_fixture_sha256"].items():
            target = work / "fixture-snapshot" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / name, target)
            assert sha(target) == digest
        inputs = work / "inputs"
        inputs.mkdir()
        base = inputs / "base"
        base.mkdir()
        for ext in ["bed", "bim", "fam"]:
            shutil.copy2(example / ("known." + ext), base / ("known." + ext))
        linear = table(example / "linear-cohort.tsv")
        binary01 = table(example / "logistic-cohort.tsv")
        binary12 = [dict(r, phenotype=str(int(r["phenotype"]) + 1)) for r in binary01]
        separated = [dict(r, phenotype="2" if genotype[0, i] > 0 else "1") for i, r in enumerate(binary12)]
        missing = [dict(r) for r in linear]
        missing[0].update(phenotype="-9", AGE="-9")
        missing[1]["phenotype"] = "NA"
        missing[2]["AGE"] = "NA"
        extreme = [dict(r) for r in linear]
        noise = np.random.default_rng(20261029).normal(0, .0001, len(linear))
        for i, row in enumerate(extreme):
            row["phenotype"] = repr(float(1.3 * genotype[0, i] + noise[i]))
        for name, rows in [("linear", linear), ("binary01", binary01), ("binary12", binary12),
                           ("separated", separated), ("missing", missing), ("extreme", extreme)]:
            write_table(inputs / (name + ".tsv"), rows)
        degenerate = inputs / "degenerate"
        shutil.copytree(base, degenerate)
        bed = bytearray((degenerate / "known.bed").read_bytes())
        stride = (len(linear) + 3) // 4
        bed[3:3 + stride] = bytes([255]) * stride
        bed[3 + stride:3 + 2 * stride] = bytes([85]) * stride
        # An additional eligible missing genotype must reduce just its own marker's n.
        offset = 3 + 7 * stride + 10 // 4
        bed[offset] = (bed[offset] & ~(3 << (2 * (10 % 4)))) | (1 << (2 * (10 % 4)))
        (degenerate / "known.bed").write_bytes(bed)
        degenerate_g = genotype.copy()
        degenerate_g[0, :] = 0
        degenerate_g[1, :] = np.nan
        degenerate_g[7, 10] = np.nan
        identity = inputs / "身份-🧬"
        shutil.copytree(base, identity)
        identities = [dict(r) for r in linear]
        fam = [line.split() for line in (identity / "known.fam").read_text().splitlines()]
        for i, (fid, iid) in enumerate([("A_B", "C"), ("A", "B_C"), ("家族", "成员")]):
            fam[i][0:2] = [fid, iid]
            identities[i].update(fid=fid, iid=iid)
        fam[2][2] = "未采样父亲"
        fam[2][4] = "0"
        (identity / "known.fam").write_text("".join(" ".join(row) + "\n" for row in fam), encoding="utf-8")
        bim = [line.split() for line in (identity / "known.bim").read_text().splitlines()]
        bim[0][1], bim[1][1] = "FID", "#变异α🧬"
        (identity / "known.bim").write_text("".join(" ".join(row) + "\n" for row in bim), encoding="utf-8")
        write_table(inputs / "identity.tsv", identities)
        no_covariates = [{k: v for k, v in row.items() if k in ["fid", "iid", "phenotype"]} for row in linear]
        write_table(inputs / "no-covariates.tsv", no_covariates)
        sealed = work / "运行环境"
        shutil.copytree(home, sealed)
        cases = []

        def case(name, engine, model, cohort, ref=None, prefix=base / "known", g=genotype,
                 coding=None, missing_policy="error", covariates="AGE,SEX", math_check=False):
            cases.append(dict(name=name, engine=engine, model=model, cohort=cohort, reference=ref,
                              prefix=prefix, g=g, coding=coding, missing_policy=missing_policy,
                              covariates=covariates, math_check=math_check))

        for engine in ["plink1", "plink2"]:
            case(engine + "-linear", engine, "linear", "linear", engine + "-linear", math_check=True)
            for coding in ["0/1", "1/2"]:
                case(engine + "-logistic-" + coding.replace("/", "-"), engine, "logistic",
                     "binary01" if coding == "0/1" else "binary12", engine + "-logistic", coding=coding)
            case(engine + "-missing", engine, "linear", "missing", missing_policy="exclude", math_check=True)
            case(engine + "-degenerate", engine, "linear", "linear", prefix=degenerate / "known", g=degenerate_g, math_check=True)
            case(engine + "-identity", engine, "linear", "identity", prefix=identity / "known", math_check=True)
            case(engine + "-no-covariates", engine, "linear", "no-covariates", covariates="none", math_check=True)
            case(engine + "-extreme", engine, "linear", "extreme")
        case("plink2-fallback", "plink2", "firth-fallback", "binary12", "plink2-firth-fallback", coding="1/2")
        for model, ref in [("logistic", "separated-no-firth"), ("firth-fallback", "separated-firth-fallback"), ("firth", "separated-firth")]:
            case("plink2-separated-" + model, "plink2", model, "separated", ref, coding="1/2")
        record["case_count"] = len(cases)

        for runtime, java in enumerate(args.java):
            run([java, "-version"], "java-" + str(runtime))
            for spec in cases:
                name = str(runtime) + "-" + spec["name"]
                out = work / ("结果-" + name)
                options = dict(task="glm", engine=spec["engine"], home=str(sealed), bfile=str(spec["prefix"]),
                               cohort=str(inputs / (spec["cohort"] + ".tsv")), model=spec["model"],
                               covariates=spec["covariates"], missing=spec["missing_policy"],
                               **{"genome-build": "unspecified", "timeout-seconds": "60"}, out=str(out))
                if spec["coding"]:
                    options["binary-coding"] = spec["coding"]
                recipe = work / (name + ".json")
                recipe.write_text(json.dumps(options, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                result = run([java, "-jar", jar, "plink-tools", "--options-file", recipe], name)
                assert b"WARNINGS" in result.stderr and (out / "WARNINGS.md").is_file()
                rows = table(out / "results.tsv")
                meta = table(out / "variant-metadata.tsv")
                ledger = table(out / "sample-ledger.tsv")
                assert len(rows) == len(meta) == 96 and len(ledger) == 128
                assert [r["variant"] for r in rows] == [r["variant"] for r in meta]
                assert len({(r["fid"], r["iid"]) for r in ledger}) == 128
                assert (out / "options-input.json").read_bytes() == recipe.read_bytes()
                assert len([r for r in ledger if r["status"] == "ELIGIBLE"]) == (126 if spec["cohort"] == "missing" else 128)
                suffix = spec["model"] if spec["model"] != "firth-fallback" else "logistic.hybrid"
                report = out / "upstream" / ("result.assoc." + suffix if spec["engine"] == "plink1" else "result.TRAIT.glm." + suffix)
                originals = raw_table(report)
                assert len(originals) == 96
                refs = reference(spec["reference"]) if spec["reference"] else None
                cohort_rows = table(inputs / (spec["cohort"] + ".tsv"))
                for ordinal, (row, raw) in enumerate(zip(rows, originals, strict=True)):
                    assert row['ci_status'] == 'NOT_REQUESTED'
                    assert all(row[key] == '' for key in ['confidence_level_requested', 'ci_scale', 'ci_lower', 'ci_upper'])
                    assert row["upstream_variant"] == raw["SNP" if spec["engine"] == "plink1" else "ID"]
                    assert row["ea"] == raw["A1"]
                    if refs is not None:
                        expected = refs[ordinal]
                        for key in ["A1", "NMISS" if spec["engine"] == "plink1" else "OBS_CT", "BETA" if spec["model"] == "linear" else "OR", "P"]:
                            assert raw[key] == expected[key], (name, ordinal, key, raw[key], expected[key])
                        if spec["engine"] == "plink2":
                            for key in ["OMITTED", "ERRCODE"]:
                                assert raw[key] == expected[key]
                        record["frozen_report_rows"] += 1
                    numeric = ["beta", "odds_ratio", "standard_error", "statistic", "p", "negative_log10_p"]
                    if raw.get("ERRCODE", ".") != "." or row["status"].startswith("UNREPORTABLE"):
                        assert row["status"].startswith("UNREPORTABLE") and all(row[k] == "" for k in numeric)
                        record["unreportable_rows"] += 1
                        continue
                    assert row["status"] in ["VALID", "VALID_WITHOUT_REPORTED_SE", "VALID_LOG_P_ONLY", "P_VALUE_OUT_OF_RESOLUTION"]
                    if spec["model"] == "linear":
                        assert float(row["beta"]) == float(raw["BETA"])
                    else:
                        expected_log = math.log(float(raw["OR"]))
                        # Java Math.log and Python libm.log can differ by one ULP;
                        # this is the same four-ULP contract as the public API check.
                        assert abs(float(row["beta"]) - expected_log) <= 4 * math.ulp(expected_log)
                    if raw["P"] == "0":
                        assert row["status"] == "P_VALUE_OUT_OF_RESOLUTION" and row["p"] == row["negative_log10_p"] == ""
                    elif float(raw["P"]) == 0:
                        with decimal.localcontext() as context:
                            context.prec = 70
                            value = float(-decimal.Decimal(raw["P"]).log10())
                        assert row["status"] == "VALID_LOG_P_ONLY" and row["p"] == ""
                        assert abs(float(row["negative_log10_p"]) - value) <= 2 * math.ulp(value)
                        record["decimal_log_checks"] += 1
                    else:
                        assert float(row["p"]) == float(raw["P"])
                    if spec["math_check"]:
                        cohort_values = cohort_rows
                        included = np.array([all(v != "NA" for k, v in r.items() if k not in ["fid", "iid"]) for r in cohort_values])
                        g = spec["g"][ordinal].copy()
                        if row["ea"] == meta[ordinal]["bim_a2"]:
                            g = 2 - g
                        else:
                            assert row["ea"] == meta[ordinal]["bim_a1"]
                        included &= np.isfinite(g)
                        y = np.array([float(r["phenotype"]) if r["phenotype"] != "NA" else np.nan for r in cohort_values])[included]
                        columns = [np.ones(included.sum()), g[included]]
                        if spec["covariates"] != "none":
                            for key in ["AGE", "SEX"]:
                                columns.append(np.array([float(r[key]) if r[key] != "NA" else np.nan for r in cohort_values])[included])
                        x = np.column_stack(columns)
                        coefficients, _, rank, _ = np.linalg.lstsq(x, y, rcond=None)
                        assert rank == x.shape[1] and int(row["n"]) == len(y)
                        residual = y - x @ coefficients
                        se = math.sqrt(float(residual @ residual) / (len(y) - rank) * np.linalg.inv(x.T @ x)[1, 1])
                        stat = coefficients[1] / se
                        p = 2 * stats.t.sf(abs(stat), len(y) - rank)
                        check_printed(coefficients[1], raw["BETA"])
                        check_printed(stat, raw["STAT" if spec["engine"] == "plink1" else "T_STAT"])
                        check_printed(p, raw["P"])
                        if "SE" in raw:
                            check_printed(se, raw["SE"])
                        record["independent_OLS_rows"] += 1
                if spec["cohort"] == "identity":
                    assert rows[0]["variant"] == "FID" and rows[1]["variant"] == "#变异α🧬"
                    assert ledger[0]["fid"] == "A_B" and ledger[1]["iid"] == "B_C"
                    assert ledger[2]["FAM_founder"] == "false" and ledger[2]["FAM_sex"] == "0"
                if spec["engine"] == "plink2" and spec["cohort"] == "linear" and spec["prefix"] == base / "known":
                    assert sum(r["effect_is_bim_a1"] == "false" for r in rows) == 40
                if spec["cohort"] == "extreme":
                    assert rows[0]["status"] == ("P_VALUE_OUT_OF_RESOLUTION" if spec["engine"] == "plink1" else "VALID_LOG_P_ONLY")
                record["workflows"] += 1
                record["canonical_rows"] += len(rows)
                save()
            # Retained packaged failures demonstrate the public status/recovery contract.
            for kind, expected in [("missing-home", 3), ("missing-policy", 2), ("no-reportable", 2), ("bad-coding", 2), ("overlapping-output", 2)]:
                options = dict(task="glm", engine="plink2", home=str(sealed), bfile=str(base / "known"),
                               cohort=str(inputs / "linear.tsv"), model="linear", covariates="AGE,SEX", missing="error",
                               **{"genome-build": "unspecified"}, out=str(work / (str(runtime) + "-control-" + kind)))
                if kind == "missing-home":
                    options["home"] = str(work / "absent-home")
                elif kind == "overlapping-output":
                    options["home"] = str(work / ("absent-overlap-home-" + str(runtime)))
                    options["out"] = str(Path(options["home"]) / "results")
                elif kind == "missing-policy":
                    options["cohort"] = str(inputs / "missing.tsv")
                elif kind == "bad-coding":
                    options.update(model="logistic", cohort=str(inputs / "binary12.tsv"), **{"binary-coding": "0/1"})
                else:
                    folder = inputs / ("all-missing-" + str(runtime))
                    shutil.copytree(base, folder)
                    payload = bytearray((folder / "known.bed").read_bytes())
                    payload[3:] = bytes([85]) * (len(payload) - 3)
                    (folder / "known.bed").write_bytes(payload)
                    options["bfile"] = str(folder / "known")
                recipe = work / (str(runtime) + "-control-" + kind + ".json")
                recipe.write_text(json.dumps(options, ensure_ascii=False), encoding="utf-8")
                result = run([java, "-jar", jar, "plink-tools", "--options-file", recipe], str(runtime) + "-control-" + kind, expected)
                out = Path(options["out"])
                if kind == "overlapping-output":
                    assert not out.exists() and not Path(options["home"]).exists()
                    assert b"outside the sealed runtime" in result.stderr and b"NOT STARTED" in result.stderr
                    record["negative_controls"] += 1
                    save()
                    continue
                assert not (out / "results.tsv").exists() and (out / "WARNINGS.md").is_file()
                assert "Required action" in (out / "WARNINGS.md").read_text(encoding="utf-8")
                assert b"WARNINGS.md" in result.stderr
                if kind == "missing-home":
                    assert b"SKIPPED" in result.stderr and not (out / "upstream/input.bed").exists()
                if kind == "no-reportable":
                    assert (out / "partial/results.tsv").is_file()
                    assert len(table(out / "partial/results.tsv")) == 96
                record["negative_controls"] += 1
                save()
        for name, digest in record["input_fixture_sha256"].items():
            assert sha(ROOT / name) == digest, name
        assert sha(jar) == record["jar_sha256"]
        assert sha(__file__) == record["producer_sha256"]
        assert record["workflows"] == 20 * len(args.java)
        assert record["canonical_rows"] == 1920 * len(args.java)
        assert record["negative_controls"] == 5 * len(args.java)
        assert record["decimal_log_checks"] >= len(args.java)
        record.update(status="passed", actual_JVMs=len(args.java), completed_utc=dt.datetime.now(dt.timezone.utc).isoformat())
        return 0
    except BaseException as failure:
        record.update(status="failed-retained", error=repr(failure))
        (work / "WARNINGS.md").write_text("# PLINK PACKAGED VALIDATION FAILED\n\n" + repr(failure) +
                                         "\n\nRequired action: inspect verification.json and retained per-call stdout/stderr "
                                         "and analysis warnings. Correct the cause, then rerun to a new --work directory. "
                                         "Failed validation is not evidence of a working adapter.\n", encoding="utf-8")
        raise
    finally:
        save()
        print(json.dumps({k: record[k] for k in ["status", "workflows", "canonical_rows", "frozen_report_rows", "independent_OLS_rows", "negative_controls"]}), flush=True)


if __name__ == "__main__":
    sys.exit(main())
