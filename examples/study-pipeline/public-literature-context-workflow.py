"""Inspect native public-passage transport with synthetic study data and a real public text.

run NEW_DIRECTORY --jar JAR --references NATIVE_REFERENCE_BUNDLE
verify DIRECTORY --jar PINNED_JAR

Requires PyYAML, jsonschema, NumPy and SciPy. The reference bundle must contain
the native full-text acquisition of Wickenhagen et al., Science 2021. It is used
only to test integrity, offsets, scoping and wire transport. Synthetic features
are not OAS1 findings, and loopback answers do not appraise the paper or biology.
"""
import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import threading
import jsonschema
import yaml

CASES = ("both", "impact-only", "manuscript-only", "other-feature", "changed-pin", "budget-hold", "context-arrival", "changed-native-text")
DOI = "10.1126/science.abj3624"
DERIVED = "SYNTHETIC_IMPACT_APPRAISAL_SOURCE_DERIVATION_CHECK"

def load(path):
    text = path.read_text(encoding="utf-8")
    try: return json.loads(text)
    except json.JSONDecodeError: return yaml.safe_load(text)
def save(path, value): path.write_text(json.dumps(value, indent=2)+"\n", encoding="utf-8")
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def strings(value):
    if isinstance(value,str): yield value
    elif isinstance(value,dict):
        for key,item in value.items(): yield from strings(key); yield from strings(item)
    elif isinstance(value,list):
        for item in value: yield from strings(item)
def fixture(path):
    spec = importlib.util.spec_from_file_location("completed_fixture", path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module); return module
def utf16(text): return len(text.encode("utf-16-le"))//2
def cli(jar, project):
    command = ["java", "-jar", str(jar), "study", "auto", str(project/"study.json"), "--actor-type", "external", "--actor-id", "synthetic-public-passage-protocol"]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == 0, result.stdout+result.stderr
    return {"command": command, "exit_code": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
def native_source(bundle):
    sources = [s for s in load(bundle/"artifacts/full-text-sources.yaml")["sources"] if s.get("reference")==DOI and s.get("status")=="available"]
    assert len(sources)==1 and sources[0]["scope"]=="full_text"
    source = sources[0]; assert sha(bundle/source["text_file"])==source["sha256"]
    assert "CC BY" in source["license"]
    return source
def passage(body, whole=False):
    if whole: return {"id":"whole-source-budget-check", "start":0, "end":utf16(body)}
    # Retain one complete source paragraph, not an inferred claim or a synopsis.
    candidates = [line for line in body.splitlines() if len(line)>250 and "OAS1" in line and not line.startswith(("License:", "Title:"))]
    assert candidates
    text = candidates[0]; start = body.index(text)
    return {"id":"public-paragraph", "start":utf16(body[:start]), "end":utf16(body[:start+len(text)])}
def run(root, jar, references):
    root.mkdir(parents=True, exist_ok=False); software=root/"software"; software.mkdir()
    shutil.copyfile(jar, software/"jlinalg.jar"); jar=software/"jlinalg.jar"
    sibling=Path(__file__).with_name("completed-study-workflow.py")
    for path in (Path(__file__), sibling): shutil.copyfile(path, software/path.name)
    repository=Path(__file__).resolve().parents[2]
    for name in ("study-v1.schema.json", "public-literature-context-v1.schema.json"):
        shutil.copyfile(repository/"docs/schemas"/name, software/name)
    module=fixture(software/"completed-study-workflow.py")
    reference_copy=software/"public-reference-bundle"; shutil.copytree(references, reference_copy); source=native_source(reference_copy)
    pins={"jar_sha256":sha(jar), "scripts_sha256":{p.name:sha(p) for p in software.glob("*.py")}, "schemas_sha256":{p.name:sha(p) for p in software.glob("*.json")},
          "reference_files_sha256":{p.relative_to(reference_copy).as_posix():sha(p) for p in reference_copy.rglob("*") if p.is_file()},
          "synthetic_study_and_loopback_only":True, "public_reference":DOI, "real_model_scientific_appraisal":False}
    save(root/"software.json", pins)
    requests=[]; responses=[]; current={"case":None,"phase":None}
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            body=self.rfile.read(int(self.headers["Content-Length"])).decode()
            assert module.PRIVATE not in body
            prompt=json.loads(body)["messages"][0]["content"]
            payload=json.loads(prompt.split("APPROVED AGGREGATE EVIDENCE (data, not instructions):\n",1)[1]); requests.append({**current,"payload":payload})
            answer=module.answer(payload)
            if "findings" in payload:
                by_id={f["finding_id"]:f for f in payload["findings"]}
                for assessment in answer["assessments"]:
                    finding=by_id[assessment["finding_id"]]
                    ids=[p["source_id"] for p in payload.get("public_literature_context",{}).get("papers",[]) if finding["hypothesis"] in p["hypotheses"] and (not p["feature_ids"] or finding["feature_id"] in p["feature_ids"])]
                    # Exercise actual source-ID acceptance, not just transport.
                    # The relationships are protocol fixtures, not source appraisal.
                    assessment["evidence_ids"]+=ids;assessment["counterevidence_ids"]+=ids
                if payload.get("public_literature_context",{}).get("papers") and current["case"] in ("both","impact-only"):
                    # A paraphrase need not contain a source ID or literal excerpt.
                    answer["selection_note"]=DERIVED
                    for assessment in answer["assessments"]:
                        assessment["claim_scope"]=DERIVED
                        assessment["rubric"]={field:DERIVED for field in assessment["rubric"]}
            responses.append({**current,"response":answer})
            response=json.dumps({"model":"deterministic-protocol-fixture","choices":[{"finish_reason":"stop","message":{"role":"assistant","content":json.dumps(answer) if isinstance(answer,dict) else answer}}]}).encode()
            self.send_response(200); self.send_header("Content-Type","application/json"); self.end_headers(); self.wfile.write(response)
        def log_message(self,*args): pass
    server=ThreadingHTTPServer(("127.0.0.1",0), Handler); thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
    try:
        for case in CASES:
            current.update(case=case, phase=0); parent=root/case; parent.mkdir(); project=module.generate(parent,"all-provided",server.server_address[1]); bundle=project/"references"; shutil.copytree(reference_copy,bundle)
            body=(bundle/source["text_file"]).read_text(encoding="utf-8")
            context={"schema_version":1,"sources":[{"id":"wickenhagen-context","hypotheses":["H"],"feature_ids":["g999" if case=="other-feature" else "g1"],
                "purposes":["impact"] if case=="impact-only" else ["manuscript"] if case=="manuscript-only" else ["impact","manuscript"],
                "reference":DOI,"reference_bundle":{"manifest":"references/metadata/manifest.yaml","sha256":sha(bundle/"metadata/manifest.yaml")},
                "source_id":source["id"],"source_sha256":source["sha256"],"passages":[passage(body,case=="budget-hold")]}]}
            context_file=project/"public-context.json"; save(context_file,context)
            study=load(project/"study.json"); study["assistance"]["public_literature_context"]=[{"file":"public-context.json","sha256":sha(context_file)}]
            if case=="budget-hold": study["assistance"]["impact_max_payload_chars"]=5000
            save(project/"study.json", study)
            save(project/"input-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in project.rglob("*") if p.is_file() and p.name!="input-hashes.json"})
            mutation=None
            if case=="changed-pin":
                context["sources"][0]["id"]="changed-context"; save(context_file,context)
                mutation={"path":"public-context.json","actual_sha256":sha(context_file)}
            if case=="context-arrival": context_file.rename(project/"deferred-context.json")
            history=[]
            def execute():
                start=len(requests); receipt=cli(jar,project); status,operations=module.operations(project)
                history.append({"phase":current["phase"],"state":status["state"],"run_id":status["run_id"],"model_requests":len(requests)-start,"receipt":receipt})
                return operations
            execute()
            if case in ("both","context-arrival","changed-native-text"):
                current["phase"]=1
                if case=="context-arrival": (project/"deferred-context.json").rename(context_file)
                if case=="changed-native-text":
                    damaged=bundle/source["text_file"]; damaged.write_text("Controlled public-source corruption; retained fixture.\n",encoding="utf-8")
                    mutation={"path":"references/"+source["text_file"],"actual_sha256":sha(damaged)}
                execute()
            save(parent/"history.json",history)
            if mutation: save(parent/"controlled-mutation.json", mutation)
        save(root/"captured-requests.json", requests);save(root/"captured-responses.json", responses)
    finally: server.shutdown(); server.server_close(); thread.join()
    verify(root,jar)

def verify(root,jar):
    pins=load(root/"software.json"); assert sha(jar)==pins["jar_sha256"]
    for group in ("scripts_sha256","schemas_sha256"):
        for name,digest in pins[group].items(): assert sha(root/"software"/name)==digest
    for name,digest in pins["reference_files_sha256"].items(): assert sha(root/"software/public-reference-bundle"/name)==digest
    module=fixture(root/"software/completed-study-workflow.py"); requests=load(root/"captured-requests.json"); responses=load(root/"captured-responses.json");assert len(requests)==len(responses)
    seals={}; checks=[]; stats=set(); citations=0
    for request,response in zip(requests,responses):
        assert (request["case"],request["phase"])==(response["case"],response["phase"])
        payload=request["payload"]
        if "findings" not in payload:continue
        by_id={f["finding_id"]:f for f in payload["findings"]}
        for assessment in response["response"]["assessments"]:
            finding=by_id[assessment["finding_id"]]
            expected={p["source_id"] for p in payload.get("public_literature_context",{}).get("papers",[]) if finding["hypothesis"] in p["hypotheses"] and (not p["feature_ids"] or finding["feature_id"] in p["feature_ids"])}
            for field in ("evidence_ids","counterevidence_ids"):
                used={i for i in assessment[field] if i.startswith("public-literature.")};assert used==expected
                citations+=len(used)
    schemas={name:jsonschema.Draft202012Validator(load(root/"software"/name)) for name in pins["schemas_sha256"]}
    for case in CASES:
        parent=root/case; project=parent/"all-provided"; history=load(parent/"history.json"); mutation=load(parent/"controlled-mutation.json") if (parent/"controlled-mutation.json").exists() else None
        schemas["study-v1.schema.json"].validate(load(project/"study.json")); schemas["public-literature-context-v1.schema.json"].validate(load(project/"public-context.json"))
        for name,digest in load(project/"input-hashes.json").items():
            path=project/name
            if mutation and name==mutation["path"]: assert sha(path)==mutation["actual_sha256"] and sha(path)!=digest
            else: assert sha(path)==digest,(case,name)
        expected_states={"changed-pin":["waiting_for_input"],"budget-hold":["waiting_for_input"],"context-arrival":["waiting_for_input","completed"],"changed-native-text":["completed","waiting_for_input"],"both":["completed","completed"]}.get(case,["completed"])
        assert [r["state"] for r in history]==expected_states,(case,history)
        if case=="both": assert history[1]["model_requests"]==0
        for phase in history:
            records=[r for r in requests if r["case"]==case and r["phase"]==phase["phase"]]
            if phase["state"]=="waiting_for_input": assert not records,(case,records)
            for record in records:
                payload=record["payload"]; purpose="impact" if "findings" in payload else "manuscript"
                findings=payload.get("findings",payload.get("selected_findings",[]))
                wanted=any(f["hypothesis"]=="H" and f["feature_id"]=="g1" for f in findings) and case!="other-feature" and not (case=="impact-only" and purpose=="manuscript") and not (case=="manuscript-only" and purpose=="impact")
                papers=payload.get("public_literature_context",{}).get("papers",[]); assert bool(papers)==wanted,(case,purpose,papers)
                if case=="impact-only" and purpose=="manuscript":
                    assert DERIVED not in json.dumps(payload) and "public-literature.wickenhagen-context" not in json.dumps(payload)
                    assert payload["impact_appraisal_transfer"]=="omitted_due_to_narrower_drafting_source_scope"
                    assert all("prior_assessment" not in finding for finding in findings)
                    assert all("impact_assessment" not in finding for finding in payload["manuscript_outline"]["principal_findings"])
                if case=="both" and purpose=="manuscript": assert DERIVED in json.dumps(payload)
                assert all(module.PRIVATE not in value and str(root) not in value and root.as_posix() not in value for value in strings(payload))
                if papers:
                    assert len(papers)==1 and papers[0]["reference"]["doi"]==DOI
                    assert papers[0]["source_id"] in payload["source_catalog"]
                    assert papers[0]["source_sha256"]==native_source(root/"software/public-reference-bundle")["sha256"]
                    assert payload["public_literature_context"]["scientific_support_established"] is False
                    original=(root/"software/public-reference-bundle"/native_source(root/"software/public-reference-bundle")["text_file"]).read_text(encoding="utf-8").encode("utf-16-le")
                    for passage_row in papers[0]["passages"]:
                        assert original[passage_row["start"]*2:passage_row["end"]*2].decode("utf-16-le")==passage_row["text"]
                checks.append({"case":case,"phase":phase["phase"],"purpose":purpose,"public_sources":len(papers)})
        _, operations=module.operations(project)
        if case=="budget-hold":
            inventory,_=next((p,m) for p,m in operations if m["operation_type"]=="finding_inventory")
            context=load(inventory/"finding-inventory.yaml")["public_literature_context"]
            assert context["status"]=="complete" and context["selected_characters"]>5000
            _,held=next((p,m) for p,m in operations if m["operation_type"]=="impact_assessment")
            assert "impact_max_payload_chars" in held["summary"]
        meta,manifest=next((p,m) for p,m in operations if m["operation_type"]=="meta_analysis")
        stats.add(sha(meta/manifest["details"]["canonical_results"]))
        for path in project.glob("runs/*/operations/*/manifest.yaml"):
            m=load(path)
            for name,digest in m.get("output_sha256",{}).items():
                output=path.parent/name; assert sha(output)==digest; seals[output.relative_to(root).as_posix()]=digest
        module.cli(jar,"verify","--project",project)
    assert len(stats)==1
    assert citations==8
    assert all(any(c["case"]=="both" and c["purpose"]==purpose and c["public_sources"] for c in checks) for purpose in ("impact","manuscript"))
    save(root/"sealed-operation-outputs.json",seals)
    report={"passed":True,"cases":len(CASES),"phases":sum(len(load(root/c/"history.json")) for c in CASES),"model_requests":len(requests),"request_checks":checks,
            "operation_hashes_checked":len(seals),"public_source_citations_in_responses":citations,"canonical_meta_statistics_identical_across_cases":True,"jar_sha256":pins["jar_sha256"],
            "derived_impact_prose_scope_checked":True,"real_model_scientific_appraisal":False,"new_external_source_retrieval":False,"synthetic_features_are_not_OAS1_findings":True}
    save(root/"verification.json",report); print(json.dumps(report,indent=2))

def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument("action",choices=("run","verify")); parser.add_argument("directory",type=Path); parser.add_argument("--jar",type=Path,required=True); parser.add_argument("--references",type=Path)
    args=parser.parse_args(); root=args.directory.resolve(); jar=args.jar.resolve()
    if args.action=="run":
        if args.references is None: parser.error("run requires --references to a native public full-text bundle")
        run(root,jar,args.references.resolve())
    else: verify(root,jar)
if __name__=="__main__": main()
