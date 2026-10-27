"""Verify native genotype aggregate metadata and unchanged pooled estimates.

Usage: python verify-result-metadata.py PROJECT --jar JAR
For the three-cohort generate-genotype-workflow.py fixture only.
Explicit native sample counts reach pooled source sums without becoming a
deduplicated pooled participant count. This fixture does not test missing N.
"""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def verify(project, jar):
    project = project.resolve(); jar = jar.resolve()
    subprocess.run(["java", "-jar", str(jar), "study", "verify", "--project", str(project)], check=True)
    for name, expected in json.loads((project / "fixture-input-sha256.json").read_text()).items():
        assert sha(project / name) == expected
    entries = [entry for entry in json.loads((project / "index/evidence-summary.json").read_text())["entries"] if entry["current"]]
    expected_operations = Counter({'validate_harmonize': 1, 'participant_qc': 3, 'association': 3,
        'effect_comparison': 1, 'discovery_meta_analysis': 1, 'discovery_replication': 1,
        'cohort_overlap': 1, 'meta_analysis': 1, 'heterogeneity_review': 1,
        'sensitivity_analysis': 1, 'diagnostic_review': 1, 'report': 1})
    assert Counter(entry['operation_type'] for entry in entries) == expected_operations
    assert all(entry["state"] == "complete" for entry in entries)
    fields = ("minor_allele_count", "minor_allele_frequency", "effect_allele_frequency", "called", "missing", "missing_rate", "imputation_info",
              "hwe_p_all", "hwe_n_all", "hwe_p_cases", "hwe_n_cases", "hwe_p_controls", "hwe_n_controls", "hwe_method", "df_numerator", "df_denominator", "partial_r2",
              "effect_allele", "other_allele", "reference_allele", "alternate_allele", "chromosome", "position", "n")
    compared = 0; sample_sizes = {}
    for entry in entries:
        if entry["operation_type"] != "association":
            continue
        folder = (project / entry["manifest"]).parent
        raw = {row["id"]: row for row in rows(folder / "association.tsv") if row["status"] == "ok"}
        snapshots = list((folder / "pre-effect-qc-results").glob("*.tsv")); assert len(snapshots) == 1
        sample_sizes[snapshots[0].stem] = {row['feature_id']: float(row['n']) for row in rows(snapshots[0])}
        for row in rows(snapshots[0]):
            native = raw[row["feature_id"]]
            for field in fields:
                expected = native['n_samples' if field == 'n' else field]
                if expected.lower() in ("nan", "na", "null", ""):
                    expected = "NA"
                assert row[field] == expected, (entry["operation_id"], row["feature_id"], field, row[field], expected)
                compared += 1
            assert int(row["called"]) + int(row["missing"]) == 62
    assert compared == 3 * 3 * len(fields)
    for entry in entries:
        if entry["operation_type"] not in ("meta_analysis", "discovery_meta_analysis"):
            continue
        folder = (project / entry["manifest"]).parent
        original = rows(folder / "meta-estimates.tsv"); augmented = rows(folder / "meta-analysis.tsv")
        assert len(original) == len(augmented)
        for raw, result in zip(original, augmented):
            assert {field: result[field] for field in raw} == raw
            ids = ['a', 'b'] if entry['operation_type'] == 'discovery_meta_analysis' else ['a', 'b', 'v']
            counts = [sample_sizes[identity][result['feature_id']] for identity in ids]
            assert float(result['reported_n_sum']) == sum(counts)
            assert float(result['reported_n_min']) == min(counts) and float(result['reported_n_max']) == max(counts)
            assert int(result['reported_n_known_cohorts']) == len(counts) == int(result['n_cohorts'])
            assert result['reported_n_missing_cohorts'] == '0'
            assert "n" not in result
    software = project / "software"; software.mkdir(exist_ok=True); pinned = software / f"jlinalg-{sha(jar)}.jar"
    if not pinned.exists():
        shutil.copyfile(jar, pinned)
    assert sha(pinned) == sha(jar)
    verifier = project / "metadata-verifier-source.py"
    if verifier.resolve() != Path(__file__).resolve():
        if verifier.exists():
            assert sha(verifier) == sha(Path(__file__))
        else:
            shutil.copyfile(Path(__file__), verifier)
    report = {"run_id": entries[0]["run_id"], "jar_sha256": sha(jar), "verifier_sha256": sha(verifier), "tested_jar": str(pinned.relative_to(project)),
              "completed_operations": len(entries), "native_metadata_fields_compared": compared, "native_HWE_method_preserved": True,
              "pooled_statistical_fields_unchanged": True, "explicit_source_sample_sizes_preserved": True,
              "pooled_sample_size_is_reported_source_sum": True, "input_hashes_unchanged": True,
              "full_pipeline_conformance": False, "external_services_tested": False}
    path = project / "metadata-verification.json"
    if path.exists():
        assert json.loads(path.read_text()) == report
    else:
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("project", type=Path); parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args(); verify(args.project, args.jar)
