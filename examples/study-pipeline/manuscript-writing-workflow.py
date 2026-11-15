"""Offline synthetic check of the packaged manuscript writing/rendering contract.

Creates NEW_DIRECTORY. No real biological claims, provider calls or literature appraisal.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import yaml


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def cli(jar, *args):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, args)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result


def run(root, jar):
    root.mkdir(parents=True, exist_ok=False)
    source = root / "source"; source.mkdir()
    for cohort in ("a", "b"):
        (source / f"{cohort}.tsv").write_text("feature_id\tbeta\tse\tp_value\nSYNTHETIC\t.2\t.1\t.04\n", encoding="utf-8")
    (source / "phenotype.csv").write_text("id,Age,Sex,BMI\na,30,1,25\nb,40,2,28\nc,50,2,31\nd,60,1,26\n", encoding="utf-8")
    study = {
        "schema_version": 1,
        "project": {"id": "writing-fixture", "title": "Synthetic writing fixture", "description": "Rendering validation only; no biological interpretation."},
        "resources": {"network": "offline", "downloads": "deny"},
        "datasets": [
            {"id": "a", "cohort": "a", "type": "generic", "stage": "association_results",
             "files": {"results": "a.tsv", "phenotype": "phenotype.csv"},
             "columns": {"characteristics": {"id": "id", "main_phenotype": "BMI", "male_value": 1, "female_value": 2}}},
            {"id": "b", "cohort": "b", "type": "generic", "stage": "association_results", "files": {"results": "b.tsv"}}
        ],
        "hypotheses": [{"id": "H", "description": "Evaluate a synthetic molecular association.",
            "analysis": {"mode": "manual", "goals": ["meta_analysis"], "harmonization": {"effect_outliers": {"enabled": False}}}}]
    }
    write(source / "study.json", study)
    cli(jar, "auto", source / "study.json", "--offline")
    preserved = {name: sha(source / name) for name in ("a.tsv", "b.tsv", "phenotype.csv", "study.json", "ledger/events.jsonl")}
    caveat = "The supplied population file has not been linked to the association sample; its summary remains provisional."
    sections = [
        {"id": "background", "section": "introduction", "text": "Readers need to understand a study's question before encountering its molecular results. This synthetic example exercises the presentation of a question, a population summary and one numerical evidence binding.\n\nThe example evaluates software behavior only. It cannot serve as a researched introduction to adiposity or transcript regulation.", "sources": [], "rationale": "Explain fixture scope."},
        {"id": "cohorts", "section": "methods", "methods_role": "cohorts", "cohort_ids": ["a", "b"], "text": "Two artificial association files were pooled for this software check. A separate four-row artificial phenotype file supplies provisional participant summaries.", "sources": [], "rationale": "Describe the synthetic inputs."},
        {"id": "result", "section": "results", "results_role": "meta_analysis", "text": "Table 1 describes the supplied artificial phenotype file. The pooled analysis included {{H.meta_analysis#/tested_features}} synthetic feature.", "sources": ["H.meta_analysis"], "rationale": "Bind the count to the executed fixture."},
        {"id": "notation", "section": "methods", "text": "Typography check only: P <= 0.05 and N >= 10. " + r"\[\hat{\beta}=\frac{\sum_{i=1}^{K}w_i\hat{\beta}_i}{\sum_{i=1}^{K}w_i},\quad \mathrm{SE}(\hat{\beta})=\sqrt{\frac{1}{\sum_{i=1}^{K}w_i}}.\]" + " Here K counts the synthetic inputs and i indexes them; this expression exercises rendering, not validation of a scientific method. " + r"Inline notation also includes \(P\le5\times10^{-8}\) and \(30\,\mathrm{kg}/\mathrm{m}^{2}\).", "sources": [], "rationale": "Exercise mathematical typography."},
        {"id": "interpretation", "section": "discussion", "text": "The fixture checks that the reading copy presents scientific text and a local table without interleaving editorial records.\n\n" + caveat, "sources": [], "rationale": "EDITORIAL_REASON_SENTINEL: retain this in the review copy."}
    ]
    # Author the summary after the body; the renderer inserts it at the front.
    summaries = {
        "background": "This artificial example evaluates manuscript presentation.",
        "methods": "Two synthetic association files and a four-row phenotype file were used.",
        "results": "The pooled analysis included {{H.meta_analysis#/tested_features}} synthetic feature; population membership remains provisional.",
        "conclusion": "The fixture supports checking presentation before applying the workflow to a scientific manuscript."
    }
    for role, text in summaries.items():
        sections.append({"id": f"abstract-{role}", "section": "abstract", "abstract_role": role, "text": text,
            "sources": ["H.meta_analysis"] if role == "results" else [], "rationale": "Summarize the completed fixture body."})
    sections.append({"id": "availability", "section": "availability", "text": "Artificial data accompany this example; its code is in examples/study-pipeline/manuscript-writing-workflow.py.", "sources": [], "rationale": "Describe actual fixture availability."})
    request = {"schema_version": 1, "id": "writing-revision", "title": "Synthetic manuscript: presentation check",
        "rationale": "Verify the packaged writing contract using artificial inputs.",
        "editorial_payload": {"mode": "omit", "rationale": "Offline fixture; no external editorial handoff."}, "sections": sections}
    write(root / "revision.json", request)
    bundle = root / "revision"
    cli(jar, "manuscript", root / "revision.json", "--project", source, "--out", bundle, "--actor-type", "external", "--actor-id", "synthetic-writing-fixture")
    cli(jar, "manuscript-verify", "--project", bundle)
    artifacts = bundle / "artifacts"
    main = (artifacts / "manuscript.html").read_text(encoding="utf-8")
    review = (artifacts / "manuscript-review.html").read_text(encoding="utf-8")
    assert caveat in main and caveat in review
    assert "EDITORIAL_REASON_SENTINEL" not in main and "EDITORIAL_REASON_SENTINEL" in review
    assert "Editorial rationale and evidence" not in main and "Editorial rationale and evidence" in review
    assert 'id="table-1"' in main and "<td>4</td>" in main
    assert "analysis-sample membership remains unverified" in main
    assert "included 1 synthetic feature" in main
    assert main.count('data-manuscript-abstract="true"') == 1
    assert main.index("<h2>Abstract</h2>") < main.index("<h2>Introduction</h2>")
    for role in summaries:
        assert f"<h3>{role.capitalize()}</h3>" in main
    assert "<nav>" not in main and "Template version" not in main
    assert "Data and code availability" in main
    supplement = (artifacts / "supplement.html").read_text(encoding="utf-8")
    assert "Verified source manifest" not in supplement and "SHA-256" not in supplement
    assert "Verified source manifest" in (artifacts / "evidence-review.html").read_text(encoding="utf-8")
    abstract_review = yaml.safe_load((artifacts / "abstract-review.yaml").read_text(encoding="utf-8"))
    assert abstract_review["status"] == "structure_complete_review_required"
    policy = yaml.safe_load((artifacts / "manuscript-writing-policy.yaml").read_text(encoding="utf-8"))
    assert policy["version"] == "2026-11-14.5"
    assert policy["prompt_sha256"] == sha(artifacts / "manuscript-writing-prompt.txt")
    assert "P ≤ 0.05 and N ≥ 10" in main
    assert 'data-tex="' in main and 'id="manuscript-math-renderer"' in main
    assert '<script src=' not in main
    writing_review = yaml.safe_load((artifacts / "manuscript-readability-review.yaml").read_text(encoding="utf-8"))
    assert writing_review["mathematics_review"]["equation_count"] == 3
    population = yaml.safe_load((artifacts / "participant-characteristics-review.yaml").read_text(encoding="utf-8"))
    assert population["status"] == "needs_input" and not population["analysis_population_verified"]
    assert all(sha(source / name) == digest for name, digest in preserved.items())
    cli(jar, "verify", "--project", source)
    result = {"jar_sha256": sha(jar), "template": policy, "source_and_ledger_unchanged": True,
        "verified_revision_bundles": 1, "local_table_rendered": True, "authored_caveat_retained": True,
        "annotated_copy_retained": True, "structured_abstract_once_at_front": True,
        "internal_provenance_outside_scientific_copies": True, "math_assets_embedded": True,
        "external_requests": 0, "live_model_writing_quality_tested": False}
    write(root / "verification.json", result)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    run(args.project.resolve(), args.jar.resolve())
