"""Offline claim/source/depth contract, using a separately acquired primary paper.

run NEW_DIRECTORY --jar JAR --references NATIVE_REFERENCE_BUNDLE
verify DIRECTORY --jar JAR

Numerical results are synthetic. The published OAS1 paper is real literature;
this source-binding exercise is neither a novel finding nor an AI appraisal.
The acquisition must include DOI 10.1126/science.abj3624 with full_text: true.
"""
import argparse
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import yaml

DOI = "10.1126/science.abj3624"
CITE = f"[[cite:{DOI}]]"
FACETS = ["observation", "primary_evidence", "mechanism", "contrary_evidence",
          "alternatives", "relevance", "next_experiments", "limitations"]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def cli(jar, *args, failure=None):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, args)],
                            capture_output=True, text=True, encoding="utf-8")
    if failure:
        assert result.returncode != 0 and failure in result.stderr, result.stdout + result.stderr
    else:
        assert result.returncode == 0, result.stdout + result.stderr
    return result


def files(root):
    return {p.relative_to(root).as_posix(): sha(p) for p in sorted(root.rglob("*")) if p.is_file()}


def request_for(root, finding, inventory, reference):
    observation = "This synthetic exercise tested {{H.meta_analysis#/tested_features}} feature; the OAS1 label does not establish a patient association."
    literature = ("Wickenhagen and colleagues report that removing the p46 prenylation site abolished its antiviral effect, "
                  f"whereas adding a prenylation motif to p42 introduced antiviral activity in their cell experiments {CITE}.")
    mechanism = (f"Their perturbations support a role for subcellular targeting in the experimental system {CITE}; "
                 "extending that mechanism to this synthetic association would be an untested inference.")
    contrary = ("The engineered p42 construct retained a weaker antiviral effect than p46, constraining an explanation "
                f"in which the motif alone determines the entire response {CITE}.")
    alternative = "A linked variant or a distinct regulatory mechanism remains a hypothesis for any future real association at this locus."
    relevance = "This example motivates research questions through the experimental evidence; it cannot establish prediction, clinical benefit or a therapeutic effect."
    experiment = ("A proposed matched-expression, noninfectious localization experiment would compare p46, p42 and "
                  "their motif-modified counterparts to distinguish targeting from abundance effects; it has not been performed here.")
    limitation = ("One textual article projection, without independent appraisal of figures, supplements, related studies or "
                  "the study's human observational evidence, cannot establish a comprehensive mechanistic discussion; scientific review remains required.")
    texts = [observation, literature, mechanism, contrary, alternative, relevance, experiment, limitation]
    main = {"id": "principal", "section": "discussion", "discussion_role": "principal_finding",
            "impact_rank": 1, "impact_reason": "One explicitly synthetic test feature; no impact comparison or AI model is claimed.",
            "result_ids": ["result"], "finding_ids": [finding], "sources": ["H.meta_analysis"],
            "text": " ".join(texts[i] for i in (0, 2, 5, 7)), "citation_basis": "literature",
            "rationale": "Test prose and evidence distinctions with real published literature and synthetic numerical data."}
    supplement = {"id": "extended", "section": "supplementary", "title": "Source-binding exercise, not a complete OAS1 dossier",
                  "finding_ids": [finding], "sources": ["H.meta_analysis"], "citation_basis": "literature",
                  "text": "\n\n".join(texts[i] for i in (1, 3, 4, 6)), "rationale": "Retain evidence qualifications and proposed experiments in linked supplementary prose."}
    claims = []
    ids = ["obs", "source", "mechanism", "contrary", "alternative", "relevance", "experiment", "limitation"]
    kinds = ["observed_result", "literature_result", "inference", "literature_result", "hypothesis", "inference", "proposed_experiment", "limitation"]
    deps = [[], [], ["source"], [], ["obs", "source"], ["source", "mechanism"], ["mechanism", "alternative"], []]
    for i, (cid, kind) in enumerate(zip(ids, kinds)):
        claim = {"id": cid, "paragraph_id": "principal" if i in (0, 2, 5, 7) else "extended",
                 "text": texts[i], "kind": kind, "sources": ["H.meta_analysis"] if i == 0 else [], "depends_on": deps[i], "readings": []}
        if i in (1, 2, 3):
            claim["readings"] = [{"passage": "weaker" if i == 3 else "loss",
                                   "relationship": "contextualizes" if i == 2 else "supports",
                                   "assessment": "Attributed exercise reading of the Fig. 4 Results narrative; this literal excerpt alone does not prove the full interpretation."}]
        if kind == "literature_result":
            claim["study_context"] = {"design": "Reported isoform/motif perturbations in engineered human cells; Fig. 4 narrative only in this exercise",
                "species": "human cellular models", "tissue": "Engineered A549 lung carcinoma cell background",
                "molecular_trait": "OAS1 isoform and C-terminal prenylation motif",
                "phenotype": "Antiviral response in source experiments, distinct from this synthetic quantitative outcome",
                "limitations": "Overexpression/model context; images, external supplements and other studies not independently appraised in this exercise"}
        if kind == "proposed_experiment":
            claim["experiment"] = {"comparison": "Matched-expression p46/p42 and motif-modified controls in a noninfectious cellular model",
                "readout": "Prespecified localization and protein-abundance measurements", "discriminates": "Persistence of a localization contrast after matching abundance favors a targeting explanation over abundance alone",
                "limitations": "A localization experiment cannot establish antiviral activity or clinical efficacy"}
        claims.append(claim)
    source = deepcopy(reference)
    source["id"] = "wickenhagen"
    policy = {"mode": "strict", "finding_inventory_sha256": sha(inventory), "source_texts": [source],
              "passages": [{"id": "loss", "source_text": "wickenhagen", "quote": "completely ablated the antiviral activity of p46", "location": "Results, C-terminal prenylation, Fig. 4C narrative"},
                           {"id": "weaker", "source_text": "wickenhagen", "quote": "p42-CTIL instigated a weaker block", "location": "Results, C-terminal prenylation, Fig. 4C/E narrative"}],
              "dossiers": [{"finding_id": finding, "paragraph_ids": ["principal", "extended"], "claims": claims,
                "facets": {facet: {"status": "addressed", "claim_ids": [ids[i]], "reason": "Prose explicitly addresses the facet with its evidence limits; scientific sufficiency is not certified."} for i, facet in enumerate(FACETS)}}]}
    return {"schema_version": 1, "id": "source-binding-exercise", "title": "Synthetic results with real primary-literature source binding",
            "rationale": "Exercise immutable source/prose/result bindings, not a biological conclusion or AI appraisal.",
            "reference_bundles": [{"manifest": source["reference_bundle"]["manifest"], "sha256": source["reference_bundle"]["sha256"]}],
            "discussion_selection_note": "Only one synthetic eligible feature exists. This is not a 3–5 finding scientific manuscript.",
            "editorial_payload": {"mode": "generate"},
            "sections": [{"id": "result", "section": "results", "results_role": "meta_analysis", "finding_ids": [finding],
                          "text": "The synthetic analysis tested {{H.meta_analysis#/tested_features}} feature.", "sources": ["H.meta_analysis"], "rationale": "Use verified test count only."}, main, supplement],
            "finding_review": policy}


def run(root, jar, references):
    root.mkdir(parents=True, exist_ok=False)
    software = root / "software"; software.mkdir()
    pinned = software / f"jlinalg-{sha(jar)}.jar"; shutil.copyfile(jar, pinned); jar = pinned
    shutil.copyfile(Path(__file__), root / "workflow-verifier-source.py")
    original_manifest = references / "metadata/manifest.yaml"
    original_hashes = files(references)
    assert read(original_manifest)["kind"] == "reference_acquisition"
    reference = root / "reference-bundle"; shutil.copytree(references, reference)
    manifest = reference / "metadata/manifest.yaml"
    assert sha(manifest) == sha(original_manifest) and files(reference) == original_hashes
    members = [row for row in read(reference / "artifacts/full-text-sources.yaml")["sources"] if row.get("status") == "available" and row.get("reference") == DOI]
    assert len(members) == 1, "Acquire the real primary reference with full_text: true before running."
    member = members[0]; text = reference / member["text_file"]
    assert sha(text) == member["sha256"] and "CC BY" in member["license"]
    source_declaration = {"id": "wickenhagen", "reference": DOI, "path": str(text), "sha256": sha(text), "scope": "full_text", "public_literature": True,
                          "provenance": "Native Europe PMC JATS projection; retained identity, license and raw HTTP response. Figures and supplements need separate review.",
                          "reference_bundle": {"manifest": str(manifest), "sha256": sha(manifest)}}
    source = root / "source"; source.mkdir()
    for cohort, beta in (("a", 1), ("b", 1.1)):
        p = math.erfc(beta / (.1 * math.sqrt(2)))
        (source / f"{cohort}.tsv").write_text(f"feature_id\tbeta\tse\tp_value\nSYNTHETIC_OAS1_LABEL\t{beta}\t.1\t{p:.16g}\n", encoding="utf-8")
    study = {"schema_version": 1, "project": {"id": "source-binding-fixture", "title": "Synthetic results, real public literature", "description": "No patient analysis, novel biological finding or real AI appraisal."},
             "resources": {"network": "offline", "downloads": "deny"}, "assistance": {"enabled": False},
             "datasets": [{"id": c, "cohort": c, "type": "generic", "stage": "association_results", "files": {"results": f"{c}.tsv"}} for c in ("a", "b")],
             "hypotheses": [{"id": "H", "description": "Synthetic quantitative fixture", "analysis": {"mode": "manual", "goals": ["meta_analysis", "impact_assessment"], "harmonization": {"effect_outliers": {"enabled": False}}}}]}
    write(source / "study.json", study); cli(jar, "auto", source / "study.json", "--offline")
    inventory = next(source.glob("runs/**/finding-inventory.jsonl"))
    rows = [json.loads(line) for line in inventory.read_text(encoding="utf-8").splitlines()]
    candidates = [r for r in rows if r["candidate"] and r["estimable"]]
    assert len(candidates) == 1
    base = request_for(root, candidates[0]["finding_id"], inventory, source_declaration)
    source_hashes = files(source); write(root / "frozen-source-hashes.json", source_hashes)
    write(root / "reference-hashes.json", original_hashes)
    cases = []

    def case(name, request, failure=None):
        request["id"] = name
        path = root / f"{name}.json"; write(path, request)
        result = cli(jar, "manuscript", path, "--project", source, "--out", root / name,
                     "--actor-type", "external", "--actor-id", "synthetic-protocol-fixture", failure=failure)
        if failure:
            (root / f"{name}-error.txt").write_text(result.stderr, encoding="utf-8")
        else:
            cli(jar, "manuscript-verify", "--project", root / name)
        cases.append({"name": name, "failure": failure})

    case("complete", deepcopy(base))
    legacy = deepcopy(base); legacy.pop("finding_review"); case("legacy-needs", legacy)
    missing = deepcopy(base); missing["finding_review"]["dossiers"][0]["facets"].pop("mechanism")
    case("missing-facet", missing, "review depth facet mechanism")
    missing["finding_review"]["mode"] = "review"; case("review-needs", missing)
    stale = deepcopy(base); stale["sections"][1]["text"] += " An unbound assertion."
    case("uncovered-prose", stale, "outside the claim ledger")
    partial = deepcopy(base)
    claim = partial["finding_review"]["dossiers"][0]["claims"][0]
    claim["text"] = claim["text"][:-3]
    case("partial-token", partial, "outside the claim ledger")
    stale = deepcopy(base); stale["finding_review"]["finding_inventory_sha256"] = "0" * 64
    case("stale-results", stale, "current frozen finding_inventory_sha256")
    stale = deepcopy(base); stale["sections"][1]["text"] = stale["sections"][1]["text"].replace("untested inference", "proven conclusion")
    case("stale-claim", stale, "unique literal span")
    broken = deepcopy(base); broken["finding_review"]["passages"][0]["quote"] = "INVENTED_SOURCE_RESULT"
    case("invented-passage", broken, "unique literal substring")
    broken = deepcopy(base); broken["finding_review"]["dossiers"][0]["claims"][0]["depends_on"] = ["alternative"]
    case("cyclic-reasoning", broken, "Cyclic mechanistic claim dependencies")
    broken = deepcopy(base); broken["finding_review"]["source_texts"][0]["scope"] = "abstract"
    case("false-native-scope", broken, "does not match an available native full-text member")
    local = root / "caller-public-text.txt"; shutil.copyfile(text, local)
    caller = deepcopy(base); entry = caller["finding_review"]["source_texts"][0]
    entry.pop("reference_bundle"); entry["path"] = str(local); entry["provenance"] = "Caller-declared public text; exact bytes copied from the native example, authentication not inferred."
    case("caller-text", caller)
    local.write_text(local.read_text(encoding="utf-8") + "\nChanged original after revision.\n", encoding="utf-8")
    predecessor = root / "caller-text/metadata/manifest.yaml"
    successor = {"schema_version": 1, "id": "inherited", "title": base["title"], "rationale": "Reuse the sealed source snapshot despite a changed external original.",
                 "previous_revision": {"manifest": str(predecessor), "sha256": sha(predecessor)}, "sections": []}
    case("inherited", successor)
    case("changed-original", deepcopy(caller), "Discussion source hash mismatch")
    # A draft's sealed copies are independent of later edits to the acquisition.
    original_text = text.read_bytes(); text.write_bytes(original_text + b"\nTAMPER\n")
    case("tampered-native", deepcopy(base), "hash mismatch")
    predecessor = root / "complete/metadata/manifest.yaml"
    successor = {"schema_version": 1, "id": "inherited-native", "title": base["title"], "rationale": "Inherit the sealed native acquisition after its original directory changes.",
                 "previous_revision": {"manifest": str(predecessor), "sha256": sha(predecessor)}, "sections": []}
    case("inherited-native", successor)
    text.write_bytes(original_text)
    assert files(references) == original_hashes and files(reference) == original_hashes
    write(root / "cases.json", cases)
    verify(root, jar)


def verify(root, jar):
    source = root / "source"
    assert files(source) == read(root / "frozen-source-hashes.json"), "Frozen analysis/ledger changed"
    assert files(root / "reference-bundle") == read(root / "reference-hashes.json"), "Acquisition changed"
    cases = read(root / "cases.json")
    outputs = 0
    for row in cases:
        name = row["name"]; bundle = root / name
        if row["failure"]:
            assert not bundle.exists() and row["failure"] in (root / f"{name}-error.txt").read_text(encoding="utf-8")
            continue
        cli(jar, "manuscript-verify", "--project", bundle)
        report = read(bundle / "artifacts/finding-discussion-review.yaml")
        assert report["scientific_review"] == "required"
        assert report["status"] == ("needs_input" if name in ("legacy-needs", "review-needs") else "source_bindings_complete_scientific_review_required")
        payload = read(bundle / "artifacts/editorial-payload.json")
        assert "finding_review" not in payload and "source_texts" not in payload
        assert "<article" not in json.dumps(payload)
        if name not in ("legacy-needs", "review-needs"):
            assert report["claim_count"] == 8 and not report["needs"]
            assert len(report["passages"]) == 2
            assert list(report["quoted_words_per_reference"].values()) == [12]
            assert len(report["dossiers"][0]["facets"]) == 8
            for p in report["dossiers"][0]["paragraph_coverage"]:
                assert p["uncovered_tokens"] == 0
            resolved = read(bundle / "metadata/resolved-revision.yaml")
            entry = resolved["finding_review"]["source_texts"][0]
            snapshot = bundle / "metadata" / entry["snapshot"]
            assert sha(snapshot) == entry["sha256"]
            source_text = snapshot.read_text(encoding="utf-8")
            for passage in report["passages"]:
                assert source_text[passage["source_start"]:passage["source_end"]] == passage["quote"], "Example has BMP-only prefixes for UTF-16 offsets"
            # Body text must stay out of the ordinary manuscript-only payload.
            assert "The bat connection" not in json.dumps(payload)
            assert "finding-discussion-review.html" in (bundle / "artifacts/index.html").read_text(encoding="utf-8")
        outputs += 1
    cli(jar, "verify", "--project", source)
    pinned = root / "software" / f"jlinalg-{sha(jar)}.jar"
    assert pinned.is_file() and sha(pinned) == sha(jar)
    assert sha(root / "workflow-verifier-source.py") == sha(Path(__file__))
    report = {"jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(root)), "verifier_sha256": sha(Path(__file__)),
              "cases": len(cases), "native_manuscript_bundles_verified": outputs, "failures_before_output": len(cases) - outputs,
              "frozen_source_unchanged": True, "native_public_reference_verified": True, "source_snapshot_inherited": True,
              "source_bodies_in_editorial_payload": False, "scientific_review_required": True,
              "real_AI_scientific_depth_tested": False, "new_OAS1_finding": False, "workflow_external_requests": 0,
              "full_pipeline_conformance": False, "files_sha256": files(root)}
    report["files_sha256"].pop("workflow-acceptance.json", None)
    write(root / "workflow-acceptance.json", report)
    print(json.dumps({k: v for k, v in report.items() if k != "files_sha256"}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("run", "verify")); parser.add_argument("directory", type=Path)
    parser.add_argument("--jar", required=True, type=Path); parser.add_argument("--references", type=Path)
    args = parser.parse_args()
    root = args.directory.resolve(); jar = args.jar.resolve()
    if args.mode == "run":
        assert args.references, "--references is required for run"
        run(root, jar, args.references.resolve())
    else:
        verify(root, jar)
