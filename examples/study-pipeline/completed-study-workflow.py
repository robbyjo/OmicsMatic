"""Reuse completed study stages through impact selection and manuscript production.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Requires NumPy, SciPy and PyYAML. The loopback server is a deterministic protocol
fixture, not an AI model. All data, references and prose are explicitly synthetic.
"""
import argparse
import csv
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
from pathlib import Path
import shutil
import subprocess
import threading
import numpy as np
from scipy.stats import hypergeom
import yaml

CASES=("all-provided","missing-go","missing-nodes-arrival","go-only","memberships-only","missing-background",
       "partial-go","incomplete-family","wrong-trait","underflow","zero-without-log")
HELD={"missing-background","partial-go","incomplete-family","wrong-trait","zero-without-log"}
DOI="10.5555/synthetic-completed-study"
PRIVATE="PRIVATE_SOURCE_NOTES_MUST_REMAIN_LOCAL"

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,value):path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def table(path,header,records):
    with path.open("w",encoding="utf-8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(header);writer.writerows(records)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],capture_output=True,text=True,encoding="utf-8",errors="replace")
    assert result.returncode==0,result.stdout+result.stderr;return result.stdout
def operations(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"));directory=project/"runs"/status["run_id"]/"operations"
    return status,[(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in directory.iterdir()]
def one(project,kind):
    found=[pair for pair in operations(project)[1] if pair[1]["operation_type"]==kind];assert len(found)==1,(project,kind,len(found));return found[0]
def supplied(kind,file,count=None,identity=None):
    d={"id":identity or "provided-"+kind.replace("_","-"),"kind":kind,"files":{"results":file},"scope":{"phenotype":"synthetic","species":"human"},"method":{"name":"Completed synthetic source analysis","version":"1","parameters":{"private_note":PRIVATE}},"provenance":{"notes":PRIVATE},"limitations":["Synthetic protocol fixture; source analysis and biology are not reproduced."]}
    if count is not None:d["family"]={"id":d["id"]+"-family","coverage":"complete","tested_count":count,"adjustment":"BH"}
    return d

def generate(root,case,port):
    project=root/case;project.mkdir();defs=[];goals=["finding_inventory"]
    table(project/"meta.tsv",["feature_id","beta","se","p_value","adjusted_p"],[[f"g{i}",.4,.1,.0001 if i<=6 else .8,.0005 if i<=6 else .8] for i in range(1,31)])
    table(project/"annotation.tsv",["feature_id","gene_symbol"],[[f"g{i}",f"A{i}"] for i in range(1,31)])
    meta=supplied("meta_analysis","meta.tsv",30);meta["files"]["feature_annotation"]="annotation.tsv";defs.append(meta)
    table(project/"members.tsv",["node_id","module_id","module_type"],[["g1","M1","community"],["g2","M1","community"],["g3","M1","clique"],["g4","M1","clique"]])
    table(project/"nodes.tsv",["node_id"],[[f"g{i}"] for i in range(1,31)])
    members=supplied("network_modules","members.tsv");members["files"].update(nodes="nodes.tsv",feature_annotation="annotation.tsv")
    go=supplied("module_gene_ontology","module-go.tsv",2)
    table(project/"module-go.tsv",["module_id","module_type","term_id","term_name","universe_genes","module_genes","term_genes","overlap_genes","p_value","adjusted_p","hit_genes"],[["M1","community","GO:1","Synthetic term",30,2,5,2,.01,.35,"A1;A2"],["M1","clique","GO:1","Synthetic term",30,2,5,1,.03,.42,"A3"]])
    go["family"]={"id":"original-all-module-term-family","coverage":"selected_only","tested_count":1000,"adjustment":"BH","adjusted_p_from_full_family":True}
    if case!="go-only":defs.append(members);goals.append("network_modules")
    if case not in ("missing-go","missing-nodes-arrival","missing-background","memberships-only"):defs.append(go)
    if case!="memberships-only":goals.append("module_gene_ontology")
    if case=="missing-background":members["files"]={"results":"members.tsv"}
    if case=="partial-go":go["reported_status"]="partial"
    if case=="incomplete-family":
        table(project/"module-go.tsv",["module_id","module_type","term_id","p_value"],[["M1","community","GO:1",.01],["M1","clique","GO:1",.03]])
        go["family"]={"id":"incomplete-family","coverage":"complete","tested_count":3,"adjustment":"BH"}
    if case=="wrong-trait":go["scope"]["phenotype"]="known different phenotype"
    if case in ("underflow","zero-without-log"):
        header=["module_id","module_type","term_id","p_value"]
        records=[["M1","community","GO:1",0],["M1","clique","GO:1",0],["M2","community","GO:1",0]]
        if case=="underflow":
            header.append("log_p_value")
            for row,log in zip(records,[-1000,-1100,-900]):row.append(log)
        table(project/"module-go.tsv",header,records);go["family"]={"id":"complete-tiny-family","coverage":"complete","tested_count":3,"adjustment":"BH"}
    if case in ("missing-go","missing-nodes-arrival"):
        db=project/"fixture-resources/enrichment/go-human";db.mkdir(parents=True)
        tablegenes="GO:1\tSynthetic first\tA1\tA2\nGO:2\tSynthetic second\tA3\tA4\n";(db/"data.gmt").write_text(tablegenes,encoding="utf-8")
        (db/"manifest.properties").write_text("format_version=1\nprovider=GO\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+sha(db/"data.gmt")+"\n",encoding="utf-8")
    if case=="missing-nodes-arrival":(project/"nodes.tsv").rename(project/"deferred-nodes.tsv")
    assistance={"enabled":False}
    if case=="all-provided":
        table(project/"edges.tsv",["source","target","weight"],[["g1","g2",.8],["g2","g3",-.9],["g3","g4",.7]])
        graph=supplied("network_analysis","unused");graph["files"]={"nodes":"nodes.tsv","edges":"edges.tsv","feature_annotation":"annotation.tsv"};graph["method"]["edge_direction"]="undirected";defs.insert(1,graph);members["depends_on"]=[graph["id"]];go["depends_on"]=[members["id"]];goals.append("network_analysis")
        table(project/"go.tsv",["term_id","term_name","p_value","adjusted_p","hit_genes"],[["GO:1","Synthetic source GO",.02,.02,"A1;A2"]]);defs.append(supplied("gene_ontology","go.tsv",1))
        table(project/"mr.tsv",["feature_id","beta","se","p_value","adjusted_p"],[["g1",.2,.1,.04,.04]]);defs.append(supplied("mendelian_randomization","mr.tsv",1))
        table(project/"coloc.tsv",["feature_id","region_id","pp_h4"],[["g1","synthetic-region",.95]]);coloc=supplied("colocalization","coloc.tsv");coloc["evidence_grade"]="exploratory_partial_region_major_limitation";coloc["limitations"].append("Source QTL export was selected-only; posterior evidence is conditional on available variants.");defs.append(coloc)
        table(project/"rare.tsv",["feature_id","mask_id","test","p_value","is_primary_test"],[["g1","lof","SKAT-O",.001,True],["g1","lof","burden",.00001,False],["g1","missense","SKAT-O",.2,True],["g1","missense","burden",.3,False]]);defs.append(supplied("rare_variant_analysis","rare.tsv",4))
        table(project/"score.tsv",["score_id","phenotype","evaluation_population","metric","estimate"],[["frozen-score","synthetic","external synthetic holdout","r_squared_score",.2]]);score=supplied("risk_score_evaluation","score.tsv");score["scope"]["population"]="external synthetic holdout";defs.append(score)
        table(project/"protein.tsv",["feature_id","protein_id","sequence_sha256","model_id","mean_plddt","coverage"],[["g1","SYNTHETIC_PROTEIN",hashlib.sha256(b"SYNTHETIC_SEQUENCE").hexdigest(),"synthetic-model",90,1]]);defs.append(supplied("protein_structure","protein.tsv"))
        reference={"id":"paper","doi":DOI,"title":"Synthetic completed-study source for software verification","authors":"Synthetic protocol authors","year":2025,"journal":"Synthetic protocol journal","abstract_text":"Controlled perturbation changed the synthetic readout in the declared model. An independent control experiment found no change. "+PRIVATE,"notes":PRIVATE}
        queries=[{"id":strategy,"provider":"Europe PMC","query":"synthetic "+strategy,"search_strategy":strategy,"phenotype":"synthetic","status":"complete","coverage":"complete","hit_count":1,"references":["paper"],"searched_at":"2026-10-26T00:00:00Z","reuse_rationale":"Explicit synthetic reuse of a previously completed source search"} for strategy in ("field_background","hypothesis_relevance","similar_studies","field_conflicting_evidence")]
        save(project/"literature.json",{"schema_version":1,"public_metadata":True,"references":[reference],"queries":queries,"notes":PRIVATE});defs.append(supplied("literature_search","literature.json"))
        (project/"public-source.txt").write_text("DOI: "+DOI+"\nControlled perturbation changed the synthetic readout in the declared model. An independent control experiment found no change.\n"+PRIVATE,encoding="utf-8")
        goals+= [d["kind"] for d in defs if d["kind"] not in goals]
        assistance={"enabled":True,"impact":True,"manuscript":True,"provider":"local","model":"deterministic-protocol-fixture","endpoint":f"http://127.0.0.1:{port}/v1/chat/completions","impact_max_payload_chars":500000}
    study={"schema_version":1,"project":{"id":"completed-study-fixture","title":"Completed synthetic study","description":"No biological or real-AI source appraisal","data_policy":{"remote_transmission":"deny_all"}},"datasets":[],"resources":{"network":"online" if case=="all-provided" else "offline","downloads":"deny","directory":"fixture-resources"},"assistance":assistance,
           "hypotheses":[{"id":"H","description":"Reuse completed source analyses","outcome":"synthetic","analysis":{"mode":"manual","goals":goals,"primary_result":{"kind":"meta_analysis","rationale":"Prespecified supplied meta-analysis is the primary signed family"},"network":{"go_min_size":1,"go_max_size":10,"go_min_module_genes":1},"supplied_results":defs}}]}
    save(project/"study.json",study);save(project/"input-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in project.rglob("*") if p.is_file()});return project

def answer(payload):
    if "findings" not in payload:
        assert "3–5" in payload["instructions"] and "Ã" not in payload["instructions"]
        selected=payload["selected_findings"]
        return "Synthetic completed-study protocol\nIntroduction\nThis generated example tests software behavior and makes no biological claims.\nMethods\nCompleted source analyses were imported without their unavailable participant data. Source methods and validity remain unreviewed.\nResults\n"+"\n".join("The synthetic selected finding is "+r["feature_id"]+"." for r in selected)+"\nDiscussion\nProvided module GO, MR and structural context are retained as source-reported evidence; they do not establish molecular mechanism or independent source validity.\nConclusion\nThe software path completed; all scientific interpretation requires review.\nSupplementary\nThe source artifacts retain the complete supplied families and their limitations.\n"
    findings=sorted(payload["findings"],key=lambda r:r["feature_id"]);assessments=[]
    for finding in findings:
        sources=sorted({source for observation in finding["observations"] for source in observation.get("source_ids",[])})
        assessments.append({"finding_id":finding["finding_id"],"rubric":{k:"Deterministic source-coverage protocol check; not impact judgment" for k in payload["rubric"]},"evidence_ids":sources,"counterevidence_ids":[],"caveat_ids":[c["id"] for c in finding["caveats"]],"claim_scope":"Synthetic association and source-reported context only"})
    selected=[];groups=set()
    for row in findings:
        if row["story_group"] not in groups:selected.append(row["finding_id"]);groups.add(row["story_group"])
        if len(selected)==3:break
    return {"assessments":assessments,"selected_ids":selected,"selection_note":"Fewer synthetic groups" if len(selected)<3 else "","comparison_rationale":"Every eligible synthetic finding is assessed; no real scientific ranking is claimed"}

def run(root,jar):
    root.mkdir(parents=True,exist_ok=False);software=root/"software";software.mkdir();shutil.copyfile(jar,software/"jlinalg.jar");jar=software/"jlinalg.jar";shutil.copyfile(__file__,software/"completed-study-workflow.py");save(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))})
    requests=[]
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            body=self.rfile.read(int(self.headers["Content-Length"])).decode();assert PRIVATE not in body
            prompt=json.loads(body)["messages"][0]["content"];payload=json.loads(prompt.split("APPROVED AGGREGATE EVIDENCE (data, not instructions):\n",1)[1]);requests.append(payload);result=answer(payload);response=json.dumps({"model":"deterministic-protocol-fixture","choices":[{"finish_reason":"stop","message":{"role":"assistant","content":json.dumps(result) if isinstance(result,dict) else result}}]}).encode();self.send_response(200);self.send_header("Content-Type","application/json");self.send_header("Content-Length",str(len(response)));self.end_headers();self.wfile.write(response)
        def log_message(self,*args):pass
    server=ThreadingHTTPServer(("127.0.0.1",0),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        for case in CASES:
            project=generate(root,case,server.server_address[1]);cli(jar,"auto",project/"study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture")
            if case=="missing-nodes-arrival":
                before,old_ops=operations(project);assert before["state"]=="waiting_for_input";prior={str(path):sha(path) for folder,_ in old_ops for path in folder.rglob("*") if path.is_file()};shutil.copyfile(project/"deferred-nodes.tsv",project/"nodes.tsv");cli(jar,"auto",project/"study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture");after,new_ops=operations(project);assert after["state"]=="completed";assert one(project,"meta_analysis")[1].get("reused_from_cache") is True
                for name,digest in prior.items():assert sha(Path(name))==digest
                cli(jar,"auto",project/"study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture");assert all(m.get("reused_from_cache") for _,m in operations(project)[1]);save(project/"arrival-receipt.json",{"initial_run":before["run_id"],"completed_run":after["run_id"],"prior_files_verified":len(prior),"delivered_nodes_sha256":sha(project/"nodes.tsv")})
        before=len(requests);cli(jar,"auto",root/"all-provided/study.json","--actor-type","external","--actor-id","synthetic-protocol-fixture");assert len(requests)==before;save(root/"requests.json",requests)
    finally:server.shutdown();server.server_close();thread.join()
    authored_revision(root,jar)
    verify(root,jar)

def authored_revision(root,jar):
    project=root/"all-provided";impact,_=one(project,"impact_assessment");selected=yaml.safe_load((impact/"impact-assessment.yaml").read_text(encoding="utf-8"))["selected_findings"]
    inventory,_=one(project,"finding_inventory");source_hash=sha(inventory/"finding-inventory.jsonl");ledger_hash=sha(project/"ledger/events.jsonl")
    sections=[{"id":"intro","section":"introduction","text":"This synthetic example tests reuse of completed analyses. It contains no biological conclusions.","sources":[],"rationale":"Protocol context"},
              {"id":"methods","section":"methods","text":"The source statistical analyses were supplied. Local validation, source-family projection and editorial source binding do not reproduce the original models.","sources":[],"rationale":"Distinguish original and local work"},
              {"id":"results","section":"results","results_role":"meta_analysis","text":"The completed primary association family remains the source of the selected findings.","sources":["H.meta_analysis"],"finding_ids":[r["finding_id"] for r in selected],"rationale":"Frozen supplied primary source"}]
    dossiers=[]
    for rank,finding in enumerate(selected,1):
        feature=finding["feature_id"];main=f"main-{rank}";supp=f"supp-{rank}"
        texts=[f"The supplied primary result for {feature} is an association in the synthetic study.",
               f"A supplied synthetic source describes a controlled model perturbation, without testing {feature} directly [[cite:{DOI}]].",
               f"A relationship between that model and {feature} is an inference requiring direct validation [[cite:{DOI}]].",
               f"The source also reports a negative control that constrains the model interpretation [[cite:{DOI}]].",
               f"A linked feature or unmeasured source-design factor remains an alternative explanation for {feature}.",
               f"The {feature} association has research relevance in this fixture but does not establish clinical utility.",
               f"A matched perturbation and control experiment could distinguish the proposed {feature} explanations.",
               f"The source methods, mapping, measurement and selection processes for {feature} remain scientifically unreviewed."]
        sections.append({"id":main,"section":"discussion","discussion_role":"principal_finding","impact_rank":rank,"impact_reason":"Frozen protocol priority rather than a real scientific impact claim","finding_ids":[finding["finding_id"]],"result_ids":["results"],"sources":["H.meta_analysis","__study__.impact_assessment"],"text":" ".join(texts[i] for i in [0,2,5,7]),"rationale":"Main evidence, inference and limitations"})
        sections.append({"id":supp,"section":"supplementary","title":feature+" extended source review","finding_ids":[finding["finding_id"]],"sources":["H.meta_analysis"],"text":" ".join(texts[i] for i in [1,3,4,6]),"rationale":"Retain supporting and contrary source context, alternatives and next tests","citation_basis":"literature"})
        claims=[]
        kinds=["observed_result","literature_result","inference","literature_result","hypothesis","inference","proposed_experiment","limitation"]
        dependencies=[[],[],["c0","c1"],[],["c0","c1"],["c2"],["c2","c4"],[]]
        for i,text in enumerate(texts):
            claim={"id":f"f{rank}-c{i}","paragraph_id":main if i in [0,2,5,7] else supp,"text":text,"kind":kinds[i],"sources":["H.meta_analysis"] if i==0 else [],"depends_on":[f"f{rank}-"+c for c in dependencies[i]],"readings":[]}
            if i in (1,2,3):claim["readings"]=[{"passage":"negative" if i==3 else "positive","relationship":"contradicts" if i==3 else "supports" if i==1 else "contextualizes","assessment":"Synthetic attributed source judgment; scientific support is not certified"}]
            if kinds[i]=="literature_result":claim["study_context"]={"design":"Synthetic controlled model","species":"Synthetic system","tissue":"Synthetic model","molecular_trait":"Synthetic readout","phenotype":"Synthetic response","limitations":"Does not test the measured features or people"}
            if i==6:claim["experiment"]={"comparison":"Matched perturbation versus control","readout":"Prespecified synthetic response","discriminates":"Different predictions under linked-feature and direct-effect models","limitations":"A model comparison would not establish clinical effects"}
            claims.append(claim)
        facets=["observation","primary_evidence","mechanism","contrary_evidence","alternatives","relevance","next_experiments","limitations"]
        dossiers.append({"finding_id":finding["finding_id"],"paragraph_ids":[main,supp],"claims":claims,"facets":{name:{"status":"addressed","claim_ids":[f"f{rank}-c{i}"],"reason":"The authored text covers this facet without certifying its interpretation"} for i,name in enumerate(facets)}})
    caveats=sorted({c["text"] for row in selected for c in row["caveats"]})
    for role,text in [("future_potential","The fixture has no biological conclusion."),("next_steps","A real study needs source and design appraisal."),("limitations"," ".join(caveats)),("overall_conclusion","Only software and source-binding behavior were exercised.")]:sections.append({"id":role,"section":"conclusion" if role=="overall_conclusion" else "discussion","discussion_role":role,"text":text,"sources":[],"rationale":"Retain interpretation boundary"})
    review={"mode":"strict","finding_inventory_sha256":source_hash,"source_texts":[{"id":"paper","reference":DOI,"path":"public-source.txt","sha256":sha(project/"public-source.txt"),"scope":"full_text","public_literature":True,"provenance":"Synthetic public-source protocol text, not a real journal paper"}],"passages":[{"id":"positive","source_text":"paper","quote":"Controlled perturbation changed the synthetic readout in the declared model.","location":"Synthetic source result"},{"id":"negative","source_text":"paper","quote":"An independent control experiment found no change.","location":"Synthetic source control"}],"dossiers":dossiers}
    request={"schema_version":1,"id":"completed-study-paper","title":"Synthetic completed-study manuscript","rationale":"Bind principal findings and source readings across main and supplementary text","editorial_payload":{"mode":"omit","rationale":"No editorial transmission in this fixture"},"sections":sections,"finding_review":review}
    save(project/"revision.json",request);destination=root/"authored-manuscript";cli(jar,"manuscript",project/"revision.json","--project",project,"--out",destination,"--actor-type","external","--actor-id","synthetic-protocol-fixture");cli(jar,"manuscript-verify","--project",destination);assert sha(project/"ledger/events.jsonl")==ledger_hash

def verify(root,jar):
    pins=json.loads((root/"software.json").read_text(encoding="utf-8"));assert sha(jar)==pins["jar_sha256"] and sha(root/"software/completed-study-workflow.py")==pins["verifier_sha256"];checks=0;hashes=0
    for case in CASES:
        project=root/case
        for name,digest in json.loads((project/"input-hashes.json").read_text(encoding="utf-8")).items():assert sha(project/name)==digest
        status,ops=operations(project);assert status["state"]==("waiting_for_input" if case in HELD else "completed"),(case,status)
        for folder,manifest in ops:
            for name,digest in manifest.get("output_sha256",{}).items():assert sha(folder/name)==digest;hashes+=1
            kind=manifest["operation_type"]
            if kind not in ("validate_harmonize","finding_inventory","impact_assessment","manuscript_draft","diagnostic_review","report","rare_set_followup"):
                native=case in ("missing-go","missing-nodes-arrival","missing-background") and kind=="module_gene_ontology"
                assert manifest.get("details",{}).get("analysis_executed_by_jlinalg") is False or native,(case,kind,manifest)
            assert kind not in ("association","participant_qc","assay_qc","source_discovery")
        if case=="all-provided":
            # This is a source-preserving reporting projection, not a rerun of
            # the supplied rare-variant tests or a primary-only FDR fallback.
            projection,pm=one(project,"rare_set_followup");source,_=one(project,"rare_variant_analysis");original={(r["mask_id"],r["test"]):r for r in rows(source/"rare-results.tsv")}
            for row in rows(projection/"rare-followup-results.tsv"):
                expected=original[(row["mask_id"],row["test"])];assert float(row["p_value"])==float(expected["p_value"]);assert float(row["adjusted_p"])==float(expected["adjusted_p"])
            assert pm["details"]["source_testing_family"]["tested_count"]==4
        if case!="memberships-only":
            folder,m=one(project,"module_gene_ontology")
            if case in HELD:assert m["state"]=="waiting_for_input";assert not (folder/"module-gene-ontology.tsv").exists()
            elif case in ("missing-go","missing-nodes-arrival"):
                data=rows(folder/"module-gene-ontology.tsv");assert len(data)==4
                ps=[]
                for row in data:p=hypergeom.sf(int(row["overlap_genes"])-1,30,2,2);np.testing.assert_allclose(float(row["p_value"]),p,rtol=1e-12);ps.append(p);checks+=1
                order=np.argsort(ps);q=np.empty(4);last=1
                for rank in range(4,0,-1):at=order[rank-1];last=min(last,ps[at]*4/rank);q[at]=last
                np.testing.assert_allclose([float(row["adjusted_p"]) for row in data],q,rtol=1e-12);checks+=4
            elif case=="underflow":
                data=rows(folder/"module-gene-ontology.tsv");np.testing.assert_allclose([float(r["log_adjusted_p"]) for r in data],[-1000+math.log(1.5),-1100+math.log(3),-900],rtol=1e-13);checks+=3
            else:
                data=rows(folder/"module-gene-ontology.tsv");assert [float(r["adjusted_p"]) for r in data]==[.35,.42];checks+=2
        if case in ("go-only","memberships-only","missing-background","missing-go","missing-nodes-arrival"):assert not any(m["operation_type"]=="network_analysis" for _,m in ops)
        if case=="all-provided":
            graph,gm=one(project,"network_analysis");modules,mm=one(project,"network_modules");summary=yaml.safe_load((modules/"module-analysis.yaml").read_text(encoding="utf-8"))
            bound=[p for p in project.glob("runs/**/manifest.yaml") if sha(p)==summary["source_network_sha256"]]
            assert len(bound)==1 and yaml.safe_load(bound[0].read_text(encoding="utf-8"))["operation_type"]=="network_analysis"
            assert summary["source_network_output_sha256"]==gm["output_sha256"]
            assert summary["nodes"]==30 and summary["modules"]==2 and summary["module_ids_require_type_qualification"]
            assert {(r["module_id"],r["module_type"]) for r in rows(modules/"network-modules.tsv")}=={("M1","community"),("M1","clique")}
            inventory,_=one(project,"finding_inventory");findings=[json.loads(line) for line in (inventory/"finding-inventory.jsonl").read_text(encoding="utf-8").splitlines()];candidates=[r for r in findings if r["candidate"]];assert len(candidates)==7
            def walk(value):
                if isinstance(value,dict):
                    if value.get("kind")=="module_GO_context":yield value
                    for child in value.values():yield from walk(child)
                elif isinstance(value,list):
                    for child in value:yield from walk(child)
            contexts=[q for row in findings for q in walk(row)];assert contexts and all(q["family_size"]==1000 and q["family_coverage"]=="selected_only" for q in contexts)
            impact,_=one(project,"impact_assessment");assessment=yaml.safe_load((impact/"impact-assessment.yaml").read_text(encoding="utf-8"));assert assessment["assessed_candidate_count"]==7 and len(assessment["selected_findings"])==3;assert all(m.get("reused_from_cache") for _,m in ops)
            manuscript,_=one(project,"manuscript_draft");assert (manuscript/"manuscript.html").is_file();assert not (project/"fixture-resources").exists();assert "PRIVATE_SOURCE_NOTES" not in (root/"requests.json").read_text(encoding="utf-8")
        cli(jar,"verify","--project",project)
    authored=root/"authored-manuscript";cli(jar,"manuscript-verify","--project",authored)
    reviews=[]
    for path in [authored/"artifacts/finding-discussion-review.yaml"]:
        document=yaml.safe_load(path.read_text(encoding="utf-8"))
        if isinstance(document,dict) and document.get("status")=="source_bindings_complete_scientific_review_required":reviews.append(document)
    assert len(reviews)==1 and reviews[0]["claim_count"]==24 and len(reviews[0]["dossiers"])==3
    supplement=(authored/"artifacts/supplement.html").read_text(encoding="utf-8");assert all(f"g{i} extended source review" in supplement for i in range(1,4))
    word_counts=yaml.safe_load((authored/"artifacts/word-counts.yaml").read_text(encoding="utf-8"));assert word_counts["policy"]["max_main_words"]==8000 and word_counts["policy"]["word_limit_mode"]=="target" and not word_counts["limit_enforced"] and word_counts["supplement_words"]>0
    report={"status":"passed","cases":len(CASES),"completed":len(CASES)-len(HELD),"intentional_holds":len(HELD),"independent_numeric_checks":checks,"output_hashes_verified":hashes,**pins,"all_completed_stages_reused_without_raw_data":True,"native_impact_and_draft_completed":True,"authored_principal_findings":3,"bound_claims":24,"source_and_supplement_bindings_verified":True,"scientific_or_real_AI_quality_certified":False,"external_requests":0,"full_pipeline_conformance":False};save(root/"verification.json",report);print(json.dumps(report,indent=2))

if __name__=="__main__":
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument("action",choices=("run","verify"));ap.add_argument("directory",type=Path);ap.add_argument("--jar",type=Path,required=True);args=ap.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":run(root,jar)
    else:verify(root,jar)
