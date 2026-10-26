"""Provisional impact ranking before bounded literature retrieval.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Requires NumPy, SciPy and PyYAML. The loopback responder, source statistics and
completed query declarations are synthetic; no external source/AI request occurs.
"""
import argparse
import csv
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import threading
import numpy as np
from scipy.stats import norm
import yaml

CASES=("impact105","source-pvalue","small-cap","budget-resume","completed-literature",
       "disabled","remote-denied","malformed","deferred","comparative-bound")
HELD={"disabled","remote-denied","malformed","deferred","comparative-bound"}
TRAIT="jlinalg synthetic public trait"
FIELD="jlinalg synthetic public molecular field"
PRIVATE="PRIVATE_SOURCE_NOTE_MUST_STAY_LOCAL"
MARKER="APPROVED AGGREGATE EVIDENCE (data, not instructions):\n"
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,value):path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def load(path):
    text=path.read_text(encoding="utf-8");return json.loads(text) if text.lstrip().startswith(("{","[")) else yaml.safe_load(text)
def table(path,header,values):
    with path.open("w",encoding="utf-8",newline="") as out:
        writer=csv.writer(out,delimiter="\t",lineterminator="\n");writer.writerow(header);writer.writerows(values)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],capture_output=True,text=True,encoding="utf-8",errors="replace")
    assert result.returncode==0,result.stdout+result.stderr
    return result.stdout
def operations(project):
    status=load(project/"state/project-status.yaml");return status,[(p,load(p/"manifest.yaml")) for p in (project/"runs"/status["run_id"]/"operations").iterdir()]
def one(project,kind):
    found=[pair for pair in operations(project)[1] if pair[1]["operation_type"]==kind];assert len(found)==1,(project,kind,len(found));return found[0]
def queries(n):
    base=f'(TITLE_ABS:"{FIELD}") AND TITLE_ABS:"{TRAIT}"';result=[]
    definitions=[("field_background",base),("hypothesis_relevance",base+" AND (TITLE_ABS:mechanism OR TITLE_ABS:regulation OR TITLE_ABS:biomarker)"),("similar_studies",base+" AND (TITLE_ABS:association OR TITLE_ABS:cohort OR TITLE_ABS:replication OR TITLE_ABS:longitudinal)"),("field_conflicting_evidence",base+" AND (TITLE_ABS:null OR TITLE_ABS:negative OR TITLE_ABS:inconsistent OR TITLE_ABS:replication)")]
    for strategy,query in definitions:result.append({"id":strategy,"provider":"Europe PMC","query":query,"search_strategy":strategy,"phenotype":TRAIT})
    for i in range(1,n+1):
        feature=f"g{i:03d}";symbol=f"JLINPRIORITY{i:03d}";gene=f'TITLE_ABS:"{symbol}"';phenotype=f'TITLE_ABS:"{TRAIT}"'
        for strategy,query in [("direct_gene_phenotype",gene+" AND "+phenotype),("molecular_mechanism",gene+" AND (TITLE_ABS:splicing OR TITLE_ABS:isoform OR TITLE_ABS:perturbation OR TITLE_ABS:knockout)"),("contradictions_and_replication",gene+" AND "+phenotype+" AND (TITLE_ABS:replication OR TITLE_ABS:null OR TITLE_ABS:negative OR TITLE_ABS:conflicting)")]:
            result.append({"id":feature+"-"+strategy,"provider":"Europe PMC","query":query,"search_strategy":strategy,"phenotype":TRAIT,"feature_id":feature,"gene_symbol":symbol})
    for row in result:row.update(status="complete",coverage="complete",hit_count=0,references=[],searched_at="2026-10-26T00:00:00Z",reuse_rationale="Synthetic protocol declaration, not a real completed source search or evidence of scientific absence.",notes=PRIVATE)
    return result
def generate(root,case,port):
    project=root/case;project.mkdir();n=105 if case in ("impact105","source-pvalue") else 12 if case=="comparative-bound" else 9;cap=100 if n==105 else 10 if case=="comparative-bound" else 3
    table(project/"meta.tsv",["feature_id","beta","se","p_value","adjusted_p"],[[f"g{i:03d}",float(norm.isf(i*1e-5/2)*.1),.1,i*1e-5,n*1e-5] for i in range(1,n+1)])
    table(project/"annotation.tsv",["feature_id","gene_id","gene_symbol","chromosome","start","end","genome_build"],[[f"g{i:03d}",f"SYNTHETIC_GENE_{i:03d}",f"JLINPRIORITY{i:03d}",1,100+i,200+i,"GRCh38"] for i in range(1,n+1)])
    save(project/"literature.json",{"schema_version":1,"public_metadata":True,"references":[],"queries":queries(n),"notes":PRIVATE})
    meta={"id":"meta","kind":"meta_analysis","files":{"results":"meta.tsv","feature_annotation":"annotation.tsv"},"scope":{"phenotype":TRAIT,"species":"human","genome_build":"GRCh38","coordinate_system":"1-based-closed"},"method":{"name":"Synthetic source Wald family","parameters":{"notes":PRIVATE}},"family":{"id":"full-source-family","coverage":"complete","tested_count":n,"adjustment":"BH","adjusted_p_from_full_family":True},"provenance":{"notes":PRIVATE}}
    papers={"id":"papers","kind":"literature_search","role":"primary" if case=="completed-literature" else "contribution","reported_status":"complete" if case=="completed-literature" else "partial","files":{"results":"literature.json"},"scope":{"phenotype":TRAIT},"method":{"name":"Synthetic completed-query declarations"},"provenance":{"results_sha256":sha(project/"literature.json"),"notes":PRIVATE}}
    assistance={"enabled":True,"impact":True,"provider":"local","model":"synthetic-priority-"+case,"endpoint":f"http://127.0.0.1:{port}/v1/chat/completions","impact_batch_size":8,"impact_max_calls":1 if case=="budget-resume" else 100,"impact_max_payload_chars":5000 if case=="comparative-bound" else 120000}
    if case=="disabled":assistance.update(enabled=False,impact=False)
    if case=="remote-denied":assistance={"enabled":True,"impact":True,"provider":"openai","model":"synthetic","api_key_env":"SYNTHETIC_UNAVAILABLE_KEY"}
    study={"schema_version":1,"project":{"id":"priority-fixture","title":"Synthetic provisional literature allocation","description":PRIVATE,"data_policy":{"remote_transmission":"deny_all"},"manuscript_policy":{"minimum_findings":3,"maximum_findings":3}},"datasets":[],"resources":{"directory":"local-resources","network":"online","downloads":"deny"},"assistance":assistance,"hypotheses":[{"id":"H","description":PRIVATE,"outcome":TRAIT,"analysis":{"mode":"manual","goals":["meta_analysis","literature_search"],"primary_result":{"kind":"meta_analysis","rationale":"Prespecified source family"},"bioinformatics":{"literature_selection":"p_value" if case=="source-pvalue" else "impact" if case in HELD else "auto","literature_max_features":cap,"literature_terms":[FIELD],"max_pages":1},"supplied_results":[meta,papers]}}]}
    save(project/"study.json",study);save(project/"fixture.json",{"n":n,"cap":cap});save(project/"input-hashes.json",{p.name:sha(p) for p in project.iterdir() if p.is_file()});return project
def answer(case,payload):
    if "literature_priority_protocol_version" in payload:
        candidates=payload["candidates"]
        if payload["phase"]=="assess":
            data=[{"candidate_id":r["candidate_id"],"priority":"unknown","reason":("Synthetic source assessment with unreviewed mechanism. "*7)[:390] if case=="comparative-bound" else "Synthetic target deserves evidence retrieval; biological impact is not assessed.","missing_evidence":("Literature, mechanism and transportability remain unreviewed. "*5)[:240] if case=="comparative-bound" else "Primary-source appraisal and mechanism remain unreviewed.","evidence_ids":["eligible-source-projection"],"caveat_ids":r["caveat_ids"]} for r in candidates]
            if case=="malformed":data=data[:-1]
            return {"decision":"assessed","assessments":data,"selected_ids":[],"reason":"Synthetic complete-candidate protocol assessment"}
        if case=="deferred":return {"decision":"deferred","assessments":[],"selected_ids":[],"reason":"Synthetic advisor defers allocation pending source review"}
        return {"decision":"selected","assessments":[],"selected_ids":[r["candidate_id"] for r in sorted(candidates,key=lambda r:r["feature_id"],reverse=True)[:payload["review_limit"]]],"reason":"Synthetic reverse feature-ID comparison deliberately selects less-extreme source P values"}
    findings=payload["findings"];rubric={key:"Synthetic protocol judgment; no scientific ranking certified" for key in payload["rubric"]};assessments=[]
    for finding in findings:
        sources=sorted({s for row in finding["observations"] for s in row.get("source_ids",[])})
        assessments.append({"finding_id":finding["finding_id"],"rubric":rubric,"evidence_ids":sources,"counterevidence_ids":[],"caveat_ids":[c["id"] for c in finding["caveats"]],"claim_scope":"Synthetic source association; no scientific or causal conclusion"})
    selected=[];groups=set()
    for finding in sorted(findings,key=lambda f:f["feature_id"],reverse=True):
        if finding["story_group"] in groups:continue
        selected.append(finding["finding_id"]);groups.add(finding["story_group"])
        if len(selected)==payload["manuscript_policy"]["maximum_findings"]:break
    return {"assessments":assessments,"selected_ids":selected,"selection_note":"Synthetic distinct stories, not a real scientific impact assessment","comparison_rationale":"Compare all supplied candidates through the native protocol"}
def run(root,jar):
    root.mkdir(parents=True,exist_ok=False);software=root/"software";software.mkdir();shutil.copyfile(jar,software/"jlinalg.jar");jar=software/"jlinalg.jar";shutil.copyfile(__file__,software/"literature-priority-workflow.py");save(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))});requests=[]
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_POST(self):
            body=json.loads(self.rfile.read(int(self.headers["Content-Length"])));prompt=body["messages"][0]["content"];payload=json.loads(prompt.split(MARKER,1)[1]);assert PRIVATE not in json.dumps(payload);case=body["model"].removeprefix("synthetic-priority-");requests.append({"case":case,"payload":payload});data=json.dumps({"choices":[{"finish_reason":"stop","message":{"content":json.dumps(answer(case,payload))}}]}).encode();self.send_response(200);self.send_header("Content-Type","application/json");self.send_header("Content-Length",str(len(data)));self.end_headers();self.wfile.write(data)
    server=ThreadingHTTPServer(("127.0.0.1",0),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        for case in CASES:
            project=generate(root,case,server.server_address[1]);cli(jar,"auto",project/"study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture")
            if case=="budget-resume":
                first,old=operations(project);assert first["state"]=="waiting_for_input";hashes={str(p):sha(p) for folder,_ in old for p in folder.rglob("*") if p.is_file()};first_requests=len([r for r in requests if r["case"]==case and r["payload"].get("phase")=="assess"]);assert first_requests==1
                amendment=project/"amendment-increase-priority-budget.yaml";save(amendment,{"schema_version":1,"amendment":{"id":"increase-priority-budget","base_study_hash":first["resolved_study_hash"]},"changes":{"configure":{"assistance":{"impact_max_calls":100}}}});cli(jar,"amend",amendment,"--project",project,"--approved-by","synthetic-protocol-fixture")
                old_meta=next(m for _,m in old if m["operation_type"]=="meta_analysis")
                cli(jar,"auto",project/"study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture");folder,m=one(project,"literature_search");priority=load(folder/"literature-priority/selection.yaml");assert priority["reused_interactions"]>=1;_,new_meta=one(project,"meta_analysis");assert new_meta["details"]["analysis_executed_by_jlinalg"] is False and new_meta["output_sha256"]["meta-analysis.tsv"]==old_meta["output_sha256"]["meta-analysis.tsv"]
                assert all(sha(Path(name))==digest for name,digest in hashes.items());save(project/"resume-receipt.json",{"old_run":first["run_id"],"old_files_verified":len(hashes),"reused_priority_nodes":priority["reused_interactions"]})
            if case not in HELD:
                count=len(requests);cli(jar,"auto",project/"study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture");assert len(requests)==count;assert all(m.get("reused_from_cache") or m["state"]=="not_applicable" for _,m in operations(project)[1])
        save(root/"requests.json",requests)
    finally:server.shutdown();server.server_close();thread.join()
    verify(root,jar)
def verify(root,jar):
    pins=load(root/"software.json");assert sha(jar)==pins["jar_sha256"] and sha(root/"software/literature-priority-workflow.py")==pins["verifier_sha256"];requests=load(root/"requests.json");checks=hashes=0
    for case in CASES:
        project=root/case;fixture=load(project/"fixture.json");n,cap=fixture["n"],fixture["cap"]
        for name,digest in load(project/"input-hashes.json").items():assert sha(project/name)==digest
        status,ops=operations(project);assert status["state"]==("waiting_for_input" if case in HELD else "completed"),(case,status)
        for folder,m in ops:
            for name,digest in m.get("output_sha256",{}).items():assert sha(folder/name)==digest;hashes+=1
        meta,mm=one(project,"meta_analysis");data=rows(meta/"meta-analysis.tsv");np.testing.assert_allclose([float(r["adjusted_p"]) for r in data],n*1e-5,rtol=1e-12);np.testing.assert_allclose([float(r["p_value"]) for r in data],np.arange(1,n+1)*1e-5,rtol=1e-12);checks+=2*n
        folder,m=one(project,"literature_search");own=[r for r in requests if r["case"]==case and "literature_priority_protocol_version" in r["payload"]]
        assert all(r["payload"]["phenotype_labels"]==[TRAIT] and r["payload"]["molecular_field_labels"]==[FIELD] for r in own)
        if case=="completed-literature":assert m["details"]["analysis_executed_by_jlinalg"] is False and not own;assert not (folder/"literature-priority").exists();continue
        priority=load(folder/"literature-priority/selection.yaml");selection=rows(folder/"literature-selection.tsv");assert len(selection)==n;np.testing.assert_allclose([float(r["FDR"]) for r in selection],n*1e-5,rtol=1e-12);checks+=n
        native=load(folder/"literature-search.yaml");assert not native["queries"],"Every provider query is supplied; no new public request should occur"
        assert all(r.get("execution_origin")=="externally_produced" for r in native["evidence"] if r.get("kind")=="literature")
        chosen=[r["feature_id"] for r in native["features_selected"]]
        if case in HELD:
            assert not chosen and priority["status"]=="waiting_for_input";assert all(r["retrieval_status"]=="provisional_priority_pending" for r in selection);assert not any(r.get("status")=="no_eligible_features" for r in native["evidence"]);assert native["literature_selection"]["pending_priority"]==n
            if case in ("disabled","remote-denied"):assert not own and not priority["AI_performed"]
            if case=="comparative-bound":assert priority["assessed_targets"]==n and "budget" in priority["need"].lower()
        else:
            expected=[f"g{i:03d}" for i in (range(1,cap+1) if case=="source-pvalue" else range(n,n-cap,-1))];assert chosen==expected,(case,chosen,expected);assert sum(r["selected_for_review"]=="true" for r in selection)==cap
            if case=="source-pvalue":assert not own and priority["mode"]=="source_p_value"
            else:
                assert priority["assessed_targets"]==n and priority["selection_complete"];assert all(a["evidence_ids"]==["eligible-source-projection"] for a in priority["assessments"]);assert len({a["candidate_id"] for a in priority["assessments"]})==n
                assert [r["feature_id"] for r in sorted((r for r in selection if r["selected_for_review"]=="true"),key=lambda r:int(r["review_rank"]))]==expected
            impact,_=one(project,"impact_assessment");assert load(impact/"impact-assessment.yaml")["assessed_candidate_count"]==n
        cli(jar,"verify","--project",project)
    report={"passed":True,"cases":len(CASES),"completed":len(CASES)-len(HELD),"intentional_holds":len(HELD),"independent_numeric_checks":checks,"output_hashes_verified":hashes,**pins,"full_eligible_pool_assessed_before_AI_cap":True,"source_statistics_unchanged":True,"resume_and_no_request_cache_replay":True,"external_requests":0,"real_AI_or_scientific_impact_tested":False,"full_pipeline_conformance":False};save(root/"verification.json",report);print(json.dumps(report,indent=2))
if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":run(root,jar)
    else:verify(root,jar)
