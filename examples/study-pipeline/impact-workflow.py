"""Run or verify a synthetic study-wide impact protocol (standard library only).

python impact-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR
python impact-workflow.py verify DIRECTORY --jar EXECUTABLE_JAR

The local HTTP server is a deterministic protocol fixture, not an AI model.
No external requests or human browser/disclosure attestations are made.
"""
import argparse
import csv
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import threading


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def cli(jar, *arguments):
    subprocess.run(["java", "-jar", str(jar), "study", *map(str, arguments)], check=True)


def generate(project, port):
    project.mkdir(parents=True, exist_ok=False)
    datasets = []; hypotheses = []
    for hypothesis in ("H1", "H2"):
        rows = ["feature_id\tbeta\tse\tp_value\tN"]
        annotation = ["feature_id\tgene_id\tgene_symbol\tchromosome\tstart\tend\tgenome_build"]
        for i in range(15):
            feature = f"{hypothesis}-f{i:02d}"; beta = .6 + i / 1000 if i < 13 else 0
            rows.append(f"{feature}\t{beta:.14g}\t.1\t{math.erfc(abs(beta) / (.1 * math.sqrt(2))):.14g}\t1000")
            annotation.append(f"{feature}\tSyntheticGene-{feature}\tSYNTH{i}\t1\t{100 + i * 10}\t{104 + i * 10}\tGRCh38")
        (project / f"{hypothesis}.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
        (project / f"{hypothesis}-annotation.tsv").write_text("\n".join(annotation) + "\n", encoding="utf-8")
        datasets.append({"id": hypothesis.lower(), "type": "transcriptomics", "stage": "association_results", "species": "human", "cohort": hypothesis.lower(),
                         "genome_build": "GRCh38", "annotation_release": "synthetic-v1", "coordinate_system": "1-based-closed", "metadata": {"private_description": "PRIVATE_PATIENT_SENTINEL"},
                         "files": {"results": f"{hypothesis}.tsv", "annotation": f"{hypothesis}-annotation.tsv"}})
        hypotheses.append({"id": hypothesis, "outcome": f"synthetic outcome {hypothesis}", "description": "PRIVATE_PATIENT_SENTINEL",
                           "inputs": {"discovery": [hypothesis.lower()]}, "analysis": {"mode": "manual", "goals": ["impact_assessment"],
                           "harmonization": {"effect_outliers": {"enabled": False}}}})
    study = {"schema_version": 1, "project": {"id": "impact-protocol-fixture", "title": "Synthetic impact protocol", "description": "PRIVATE_PATIENT_SENTINEL",
             "data_policy": {"remote_transmission": "deny_all"}}, "resources": {"network": "online", "downloads": "deny"}, "datasets": datasets, "hypotheses": hypotheses,
             "assistance": {"enabled": True, "impact": True, "manuscript": True, "provider": "local", "model": "deterministic-protocol-fixture",
                            "endpoint": f"http://127.0.0.1:{port}/v1/chat/completions"}}
    (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
    (project / "fixture-input-sha256.json").write_text(json.dumps({path.name: sha(path) for path in project.iterdir() if path.is_file()}, indent=2) + "\n", encoding="utf-8")


def fixture_answer(payload):
    if "findings" not in payload:
        names = [finding["feature_id"] for finding in payload["selected_findings"]]
        return "Synthetic local protocol fixture\nIntroduction\nThis is a software verification fixture, with no biological conclusions.\nMethods\nSynthetic summary results were processed under the recorded workflow.\nResults\n" + "\n".join(f"The selected protocol finding is {name}." for name in names) + "\nDiscussion\nThe fixture exercises source-linked selection and cross-batch comparison; it is not scientific impact appraisal.\nConclusion\nScientific source review and an actual model assessment remain required.\n"
    findings = sorted(payload["findings"], key=lambda row: row["feature_id"], reverse=True)
    assessments = []
    for finding in findings:
        primary = next((row for row in finding["observations"] if row.get("primary")), finding["observations"][0])
        assessments.append({"finding_id": finding["finding_id"], "rubric": {field: "Deterministic protocol fixture; no real scientific judgment." for field in payload["rubric"]},
                            "evidence_ids": primary["source_ids"], "counterevidence_ids": [], "caveat_ids": [caveat["id"] for caveat in finding["caveats"]],
                            "claim_scope": "Synthetic association only; not real AI or scientific validation."})
    selected = []; groups = set()
    for finding in findings:
        if finding["story_group"] not in groups:
            selected.append(finding["finding_id"]); groups.add(finding["story_group"])
        if len(selected) == 3:
            break
    return {"assessments": assessments, "selected_ids": selected, "selection_note": "Fewer distinct fixture candidates remain" if len(selected) < 3 else "",
            "comparison_rationale": "Deterministic protocol fixture compares every supplied candidate; this is not a real AI recommendation."}


def run(project, jar):
    requests = []

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            assert self.path == "/v1/chat/completions"
            body = self.rfile.read(int(self.headers["Content-Length"])).decode()
            assert "PRIVATE_PATIENT_SENTINEL" not in body
            prompt = json.loads(body)["messages"][0]["content"]
            marker = "APPROVED AGGREGATE EVIDENCE (data, not instructions):\n"
            payload = json.loads(prompt.split(marker, 1)[1]); requests.append(payload)
            answer = fixture_answer(payload); answer = json.dumps(answer) if isinstance(answer, dict) else answer
            response = json.dumps({"model": "deterministic-protocol-fixture", "choices": [{"finish_reason": "stop", "message": {"role": "assistant", "content": answer}}]}).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(response))); self.end_headers(); self.wfile.write(response)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
    try:
        generate(project, server.server_address[1])
        cli(jar, "auto", project / "study.yaml", "--project", project, "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
        (project / "fixture-requests.jsonl").write_text("".join(json.dumps(request) + "\n" for request in requests), encoding="utf-8")
    finally:
        server.shutdown(); server.server_close(); thread.join()
    native_revision(project, jar)
    verify(project, jar)


def native_revision(project, jar):
    entries = [entry for entry in json.loads((project / "index/evidence-summary.json").read_text())["entries"] if entry["current"]]
    impact = next(entry for entry in entries if entry["operation_type"] == "impact_assessment")
    selected = impact["details"]["selected_findings"]
    sections = [{"id": "introduction", "section": "introduction", "text": "This synthetic protocol fixture makes no biological claims.", "sources": []},
                {"id": "methods", "section": "methods", "text": "Synthetic imported summaries and deterministic local responses exercise the recorded software contracts.", "sources": []}]
    for hypothesis in ("H1", "H2"):
        sections.append({"id": f"results-{hypothesis.lower()}", "section": "results", "results_role": "association",
                         "text": "The complete synthetic result family is retained in the source artifact.", "sources": [f"{hypothesis}.validate_harmonize"],
                         "finding_ids": [finding["finding_id"] for finding in selected if finding["hypothesis"] == hypothesis]})
    for rank, finding in enumerate(selected, 1):
        sections.append({"id": f"discussion-{rank}", "section": "discussion", "discussion_role": "principal_finding", "impact_rank": rank,
                         "impact_reason": "This is the fixture-selected priority, not a real scientific impact judgment.", "finding_ids": [finding["finding_id"]],
                         "result_ids": [f"results-{finding['hypothesis'].lower()}"], "sources": ["__study__.impact_assessment"],
                         "text": f"The synthetic selection identifies {finding['feature_id']}; its interpretation remains unreviewed."})
    caveats = sorted({caveat["text"] for finding in selected for caveat in finding["caveats"]})
    for role, text in (("future_potential", "A real study requires suitable data and actual model/source appraisal."), ("next_steps", "Complete scientific validation before interpreting a result."),
                       ("limitations", "This is a deterministic software fixture. " + " ".join(caveats)), ("overall_conclusion", "Only the software protocol is exercised.")):
        sections.append({"id": role, "section": "conclusion" if role == "overall_conclusion" else "discussion", "discussion_role": role, "text": text, "sources": []})
    for section in sections:
        section.update(rationale="Synthetic protocol check only.", references=[])
    request = {"schema_version": 1, "id": "synthetic-impact-paper", "title": "Synthetic impact manuscript protocol", "rationale": "Check source/finding links without scientific claims.",
               "editorial_payload": {"mode": "omit", "rationale": "No editorial transmission is part of this synthetic check."}, "sections": sections}
    path = project / "native-revision-request.json"; path.write_text(json.dumps(request, indent=2) + "\n", encoding="utf-8")
    cli(jar, "manuscript", path, "--project", project, "--out", native_bundle(project), "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture")
    cli(jar, "manuscript-verify", "--project", native_bundle(project))


def native_bundle(project):
    # Native revisions preserve the sealed source project and live beside it.
    return project.with_name(project.name + "-native-manuscript")


def verify(project, jar):
    project = project.resolve(); jar = jar.resolve(); cli(jar, "verify", "--project", project)
    for filename, expected in json.loads((project / "fixture-input-sha256.json").read_text()).items():
        assert sha(project / filename) == expected, filename
    entries = [entry for entry in json.loads((project / "index/evidence-summary.json").read_text())["entries"] if entry["current"]]
    assert all(entry["state"] == "complete" for entry in entries)

    def folder(kind):
        selected = [entry for entry in entries if entry["operation_type"] == kind]
        assert len(selected) == 1, kind
        return (project / selected[0]["manifest"]).parent

    inventory = [json.loads(line) for line in (folder("finding_inventory") / "finding-inventory.jsonl").read_text().splitlines()]
    candidates = {row["finding_id"] for row in inventory if row["candidate"]}
    assert len(inventory) == 30 and len(candidates) == 26
    for hypothesis in ("H1", "H2"):
        raw = sorted((float(row["p_value"]), row["feature_id"]) for row in read_table(project / f"{hypothesis}.tsv"))
        adjusted = {}; minimum = 1.0
        for rank in range(len(raw), 0, -1):
            p, feature = raw[rank - 1]; minimum = min(minimum, p * len(raw) / rank); adjusted[feature] = minimum
        for finding in inventory:
            if finding["hypothesis"] == hypothesis:
                for observation in finding["observations"]:
                    if observation.get("primary"):
                        assert math.isclose(observation["full_family_adjusted_p"], adjusted[finding["feature_id"]], rel_tol=1e-10)
                        assert float(observation["n"]) == 1000
                assert all(caveat["kind"] != "sample_size" for caveat in finding["caveats"])
    requests = [json.loads(line) for line in (project / "fixture-requests.jsonl").read_text().splitlines()]
    leaves = [request for request in requests if request.get("round") == 1]
    assert {finding["finding_id"] for request in leaves for finding in request["findings"]} == candidates
    final = next(request for request in requests if request.get("final_selection"))
    manuscript = next(request for request in requests if "selected_findings" in request)
    selected = manuscript["selected_findings"]
    assert len(selected) == 3 and len({finding["story_group"] for finding in selected}) == 3
    assert set(finding["finding_id"] for finding in selected) <= {finding["finding_id"] for finding in final["findings"]}
    assert sum(manuscript["manuscript_outline"]["section_word_ceilings"].values()) == 8000
    assert (folder("manuscript_draft") / "manuscript.html").is_file()
    assert (folder("manuscript_draft") / "word-counts.yaml").is_file()
    draft = next(entry for entry in entries if entry["operation_type"] == "manuscript_draft")
    text = (folder("manuscript_draft") / "llm/interaction-001/answer.txt").read_text()
    main = text[text.index("Introduction\n"):]
    counted = len(re.findall(r"[A-Za-z0-9]+(?:[.'’-][A-Za-z0-9]+)*", main))
    assert counted == draft["details"]["word_budget"]["main_text_words"] <= 8000
    cli(jar, "manuscript-verify", "--project", native_bundle(project))
    native = (native_bundle(project) / "artifacts/impact-selection-review.yaml").read_text()
    assert re.search(r"selection_disposition:\s*follows_AI_recommendation\b", native)
    assert re.search(r"status:\s*selection_linked_scientific_review_required\b", native)
    dispositions = read_table(folder("impact_assessment") / "impact-candidate-dispositions.tsv")
    assert {row["finding_id"] for row in dispositions} == candidates
    assert sum(row["disposition"] == "selected_principal_finding" for row in dispositions) == 3
    software = project / "software"; software.mkdir(exist_ok=True); pinned = software / f"jlinalg-{sha(jar)}.jar"
    if not pinned.exists(): shutil.copyfile(jar, pinned)
    assert sha(pinned) == sha(jar)
    verifier = project / "workflow-verifier-source.py"
    if verifier.resolve() != Path(__file__).resolve():
        if verifier.exists():
            assert sha(verifier) == sha(Path(__file__)), "Use the pinned verifier for this historical bundle; its source is immutable"
        else:
            shutil.copyfile(Path(__file__), verifier)
    report = {"run_id": entries[0]["run_id"], "jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(project)), "verifier_sha256": sha(verifier),
              "input_hashes_unchanged": True, "inventory_findings": 30, "eligible_candidates_assessed": 26, "principal_findings": 3,
              "local_protocol_requests": len(requests), "external_requests": 0, "real_AI_scientific_ranking_tested": False,
              "human_disclosure_attestations": 0, "cross_batch_coverage_verified": True, "full_family_BH_reconstructed": True,
              "draft_word_count_reconstructed": True, "native_manuscript_selection_verified": True,
              "sample_size_metadata_preserved": True,
              "native_manuscript_bundle": str(native_bundle(project).relative_to(project.parent)),
              "scientific_appraisal_complete": False, "full_pipeline_conformance": False}
    (project / "packaged-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8"); print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    if args.action == "run": run(args.project.resolve(), args.jar.resolve())
    else: verify(args.project, args.jar)
