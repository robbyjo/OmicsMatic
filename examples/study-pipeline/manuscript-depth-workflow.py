"""Offline manuscript target/strict-limit and complete-supplement protocol fixture.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR
The repeated prose tests retention and counting, not scientific discussion quality.
"""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import yaml


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cli(jar, *arguments, expect_failure=False):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, arguments)], capture_output=True, text=True)
    if expect_failure:
        assert result.returncode != 0 and "exceeds 8000 words" in result.stderr, result.stdout + result.stderr
    else:
        assert result.returncode == 0, result.stdout + result.stderr
    return result


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def run(root, jar):
    root.mkdir(parents=True, exist_ok=False)
    source = root / "source"; source.mkdir()
    for cohort, beta in (("a", 1), ("b", 1.1)):
        (source / f"{cohort}.tsv").write_text(f"feature_id\tbeta\tse\tp_value\nSYNTHETIC\t{beta}\t.1\t.000001\n", encoding="utf-8")
    study = {"schema_version": 1, "project": {"id": "manuscript-depth-fixture", "title": "Synthetic manuscript protocol", "description": "No biological claims or real AI appraisal."},
             "resources": {"network": "offline", "downloads": "deny"},
             "datasets": [{"id": cohort, "cohort": cohort, "type": "generic", "stage": "association_results", "files": {"results": f"{cohort}.tsv"}} for cohort in ("a", "b")],
             "hypotheses": [{"id": "H", "description": "Synthetic quantitative association.", "analysis": {"mode": "manual", "goals": ["meta_analysis"], "harmonization": {"effect_outliers": {"enabled": False}}}}]}
    write(source / "study.json", study)
    cli(jar, "auto", source / "study.json", "--offline")
    originals = {name: sha(source / name) for name in ("a.tsv", "b.tsv", "study.json", "ledger/events.jsonl")}
    write(root / "source-hashes.json", originals)
    request = {"schema_version": 1, "id": "supplement", "title": "Synthetic manuscript", "rationale": "Test complete supplement retention with exact source bindings.",
               "editorial_payload": {"mode": "omit", "rationale": "Retain extensive synthetic prose locally without an external handoff."},
               "manuscript_policy": {"max_main_words": 8000, "word_limit_mode": "strict"},
               "sections": [{"id": "result", "section": "results", "results_role": "meta_analysis", "text": "Tested {{H.meta_analysis#/tested_features}} synthetic features.", "sources": ["H.meta_analysis"], "rationale": "Use a verified scalar."},
                            {"id": "extended", "section": "supplementary", "title": "Extended synthetic finding", "text": "Mechanistic detail " * 4500 + "\n\nTested {{H.meta_analysis#/tested_features}} synthetic features.", "sources": ["H.meta_analysis"], "rationale": "Length fixture only; not a scientific mechanistic interpretation."}]}
    for name, current in (("strict-supplement", request), ("target-overage", deepcopy(request))):
        if name == "target-overage":
            current["manuscript_policy"] = {"word_limit_mode": "target"}
            current["sections"].insert(0, {"id": "intro", "section": "introduction", "text": "Evidence " * 8500, "sources": [], "rationale": "Length fixture only."})
        write(root / f"{name}.json", current)
        cli(jar, "manuscript", root / f"{name}.json", "--project", source, "--out", root / name, "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
    rejected = json.loads((root / "target-overage.json").read_text(encoding="utf-8"))
    rejected["manuscript_policy"] = {"max_main_words": 8000, "word_limit_mode": "strict"}
    write(root / "strict-overage.json", rejected)
    result = cli(jar, "manuscript", root / "strict-overage.json", "--project", source, "--out", root / "strict-overage", "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture", expect_failure=True)
    (root / "strict-overage-error.txt").write_text(result.stderr, encoding="utf-8")
    verify(root, jar)


def verify(root, jar):
    source = root / "source"
    for name, digest in json.loads((root / "source-hashes.json").read_text(encoding="utf-8")).items():
        assert sha(source / name) == digest, name
    for name in ("strict-supplement", "target-overage"):
        bundle = root / name
        cli(jar, "manuscript-verify", "--project", bundle)
        counts = yaml.safe_load((bundle / "artifacts/word-counts.yaml").read_text(encoding="utf-8"))
        main = (bundle / "artifacts/manuscript.html").read_text(encoding="utf-8")
        supplement = (bundle / "artifacts/supplement.html").read_text(encoding="utf-8")
        assert "supplement.html#supplement-note-extended" in main
        assert "Mechanistic detail " not in main
        assert supplement.count("Mechanistic detail ") == 4500
        assert "Tested 1 synthetic features." in supplement and "{{H.meta_analysis" not in supplement
        assert counts["supplement_words"] > 9000 and not counts["blocks_publication"]
        assert counts["supplement_section_words"]["extended"] == counts["supplement_words"]
        if name == "strict-supplement":
            assert counts["main_text_words"] < 8000 and counts["limit_enforced"]
        else:
            assert counts["main_text_words"] > 8500 and not counts["within_limit"] and not counts["limit_enforced"]
        assert "extended" in (bundle / "artifacts/publication-claims.yaml").read_text(encoding="utf-8")
    assert not (root / "strict-overage").exists()
    assert "exceeds 8000 words" in (root / "strict-overage-error.txt").read_text(encoding="utf-8")
    cli(jar, "verify", "--project", source)
    software = root / "software"; software.mkdir(exist_ok=True)
    pinned = software / f"jlinalg-{sha(jar)}.jar"
    if not pinned.exists(): shutil.copyfile(jar, pinned)
    assert sha(pinned) == sha(jar)
    verifier = root / "workflow-verifier-source.py"
    if verifier.exists(): assert sha(verifier) == sha(Path(__file__)), "Use the pinned verifier for historical bundles"
    else: shutil.copyfile(Path(__file__), verifier)
    report = {"jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(root)), "verifier_sha256": sha(verifier),
              "source_and_ledger_hashes_unchanged": True, "complete_supplement_retained": True, "independent_supplement_repetition_count": 4500,
              "supplement_words_separate": True, "over_target_draft_retained": True, "strict_limit_rejected_without_output": True,
              "source_bindings_expanded": True, "native_manuscript_bundles_verified": 2, "real_AI_scientific_depth_tested": False,
              "typed_later_stage_imports_tested": False, "external_requests": 0, "full_pipeline_conformance": False}
    write(root / "packaged-verification.json", report); print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args(); root = args.project.resolve(); jar = args.jar.resolve()
    if args.action == "run": run(root, jar)
    else: verify(root, jar)
