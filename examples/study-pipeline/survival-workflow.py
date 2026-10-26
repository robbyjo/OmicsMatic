"""Offline native/supplied Cox study acceptance with independent NumPy/SciPy checks.

run NEW_DIRECTORY --jar EXECUTABLE_JAR
verify DIRECTORY --jar EXECUTABLE_JAR

Requires PyYAML, NumPy and SciPy. Synthetic fixtures verify numerical/workflow
contracts, not censoring assumptions, rare-event calibration or real findings.
No external data, model requests or installed statistical programs are used.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import numpy as np
from scipy.optimize import minimize
from scipy.stats import chi2, norm
import yaml


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def load(path): return yaml.safe_load(path.read_text(encoding="utf-8"))
def dump(path, data): path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
def table(path):
    with path.open(encoding="utf-8", newline="") as stream: return list(csv.DictReader(stream, delimiter="\t"))


def generate(project, genotype=False, controls=None, adjust=True):
    project.mkdir(parents=True, exist_ok=False); datasets=[]
    for cohort,index in zip(("a","b","v"),range(3)):
        rows=[];features=[[],[],[]]
        for i in range(96):
            age=(i*7%31)/31; entry=i%3*.05; x=i%3 if genotype else math.sin(i*.53)
            u=((i*37+index*17)%101+.5)/101;death=-math.log(u)/math.exp(.9*x+.2*age+.1*math.cos(i*.31));censor=.8+(i*11+index*3)%19/19
            rows.append([f"{cohort}{i}",entry+min(death,censor),1 if death<=censor else 0,entry,age,"A" if i%2==0 else "B",.1*math.cos(i*.31),"PATIENT_ROW_SENTINEL"])
            for j,value in enumerate((x,1. if i<20 else 0.,1. if i<5 else 0.) if genotype else (x,math.cos(i*.31),1.)):features[j].append(value)
        with (project/f"{cohort}-phenotype.tsv").open("w",encoding="utf-8",newline="") as stream:
            writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(["id","time","event","entry","age","sex","offset","private_label"]);writer.writerows(rows)
        matrix=project/f"{cohort}-matrix.{('vcf' if genotype else 'tsv')}"
        # Reverse matrix sample order to exercise actual ID alignment.
        order=list(reversed(range(96)))
        if genotype:
            lines=["##fileformat=VCFv4.2","##contig=<ID=1>",'##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">',"\t".join(["#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT"]+[f"{cohort}{i}" for i in order])]
            for j,values in enumerate(features):lines.append("\t".join(["1",str(100+j*10),f"g{j}","A","G",".","PASS",".","GT"]+[("0/0","0/1","1/1")[int(values[i])] for i in order]))
            matrix.write_text("\n".join(lines)+"\n",encoding="utf-8")
        else:
            with matrix.open("w",encoding="utf-8",newline="") as stream:
                writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(["feature_id"]+[f"{cohort}{i}" for i in order]);writer.writerows([[f"g{j}"]+["NA" if j==1 and i==7 else values[i] for i in order] for j,values in enumerate(features)])
        datasets.append({"id":cohort,"cohort":"cohort-"+cohort,"type":"gwas" if genotype else "expression","stage":"processed_matrix","species":"human","genome_build":"GRCh38","units":"synthetic","files":{"matrix":matrix.name,"phenotype":f"{cohort}-phenotype.tsv"}})
    policy={"time_column":"time","event_column":"event","entry_column":"entry","strata_column":"sex","time_units":"years","event_definition":"Synthetic first event",
            "censoring_definition":"Synthetic administrative right censoring","rationale":"Generated software fixtures, no scientific assertion.","independent_censoring_rationale":"Generated fixed inputs; real assumptions require appraisal.","proportional_hazards_rationale":"Numerical acceptance only; no assertion that a diagnostic proves PH."}
    policy.update(controls or {})
    effect={"estimand":"synthetic event hazard association","orientation":"predictor_to_hazard","effect_scale":"log_hazard_ratio","effect_units":"per_ALT_allele" if genotype else "per_source_feature_unit","phenotype_transformation":"none","feature_transformation":"none"}
    analysis={"mode":"manual","goals":["association","meta_analysis","discovery_replication","finding_inventory"],"association":{"model":"cox","offset_column":"offset","survival":policy},
              "pooling":{"effect_contract":effect,"require_complete_contract":True},"harmonization":{"effect_outliers":{"enabled":False}},"operations":{"meta_analysis":{"model":"fixed"},"discovery_meta_analysis":{"model":"fixed"}}}
    study={"schema_version":1,"project":{"id":"survival-example","title":"Synthetic Cox study","description":"Independent numerical and source-contract checks only."},"resources":{"network":"offline","downloads":"deny"},"datasets":datasets,
           "hypotheses":[{"id":"H","description":"Explicit incident endpoint, model and source scope","outcome":"incident_endpoint","covariates":["age"] if adjust else [],"inputs":{"discovery":["a","b"],"replication":["v"]},"analysis":analysis}]}
    dump(project/"study.yaml",study);return study


def run(project,jar,label,resume=False):
    study=load(project/"study.yaml");names={"study.yaml"}
    for dataset in study["datasets"]:names.update(dataset["files"].values())
    names.update(path.name for path in project.glob("amendment-*.yaml"))
    hashes={name:sha(project/name) for name in names};archive=project/"input-editions"/label;archive.mkdir(parents=True)
    for name in names:output=archive/name;output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(project/name,output)
    action=["run"] if resume else ["auto",str(project/"study.yaml")]
    r=subprocess.run(["java","-jar",str(jar),"study",*action,"--project",str(project),"--offline"],capture_output=True,text=True,encoding="utf-8")
    (project/f"{label}-stdout.yaml").write_text(r.stdout,encoding="utf-8");(project/f"{label}-stderr.txt").write_text(r.stderr,encoding="utf-8")
    result=yaml.safe_load(r.stdout) if r.stdout else {};assert all(sha(project/name)==digest for name,digest in hashes.items())
    effective=project/"resolved-study.yaml"
    if effective.is_file():shutil.copyfile(effective,archive/"effective-study.yaml")
    return {"project":project.name,"label":label,"exit_code":r.returncode,"state":result.get("state"),"run_id":result.get("run_id"),"input_sha256":hashes,"effective_study_sha256":sha(effective) if effective.is_file() else None}


def entries(project,receipt): return [e for e in load(project/"index/evidence-summary.json")["entries"] if e["run_id"]==receipt["run_id"]]
def location(project,entry): return (project/entry["manifest"]).parent


def likelihood(beta,x,start,stop,event,strata,offset,ties,groups=False):
    ll=0.;score=np.zeros(x.shape[1]);information=np.zeros((x.shape[1],x.shape[1]));parts=[]
    for s in sorted(set(strata)):
        for time in sorted(set(stop[(strata==s)&event])):
            risk=(strata==s)&(start<time)&(stop>=time);dead=(strata==s)&(stop==time)&event
            eta=offset+x@beta;shift=max(eta[risk]);w=np.exp(eta-shift);d=int(sum(dead));u=x[dead].sum(axis=0);v=np.zeros_like(information)
            ll+=sum(eta[dead]-shift)
            for l in range(d):
                rw=w*risk-(l/d if ties=="efron" else 0.)*w*dead;den=sum(rw)
                mean=(rw[:,None]*x).sum(axis=0)/den
                cov=x.T@(rw[:,None]*x)/den-np.outer(mean,mean)
                ll-=math.log(den);u-=mean;v+=cov
            score+=u;information+=v;parts.append((time,d,u,v))
    return (ll,score,information,parts) if groups else (ll,score,information)


def independent_fit(x,start,stop,event,strata,offset,ties):
    if x.shape[1]==0:return np.zeros(0),likelihood(np.zeros(0),x,start,stop,event,strata,offset,ties)
    def fun(beta):ll,u,info=likelihood(beta,x,start,stop,event,strata,offset,ties);return -ll,-u
    fit=minimize(fun,np.zeros(x.shape[1]),jac=True,hess=lambda b:likelihood(b,x,start,stop,event,strata,offset,ties)[2],method="trust-exact",options={"gtol":1e-10,"maxiter":200})
    values=likelihood(fit.x,x,start,stop,event,strata,offset,ties)
    assert max(abs(values[1]))<2e-5,(fit.message,values[1]);return fit.x,values


def numerical(project,receipt):
    records=entries(project,receipt);checks=0;all_beta={};study=load(project/"input-editions"/receipt["label"]/"effective-study.yaml");h=study["hypotheses"][0];policy=h["analysis"]["association"]["survival"]
    for e in records:
        if e["operation_type"]!="association" or e["state"]!="complete":continue
        directory=location(project,e);id_=e["details"]["dataset"];qc=next(p for p in records if p["operation_type"]=="participant_qc" and p["details"]["dataset_id"]==id_)
        participants=table(location(project,qc)/"analysis-phenotype.tsv");start=np.array([float(r["entry"]) for r in participants]);stop=np.array([float(r["time"]) for r in participants]);event=np.array([r["event"]=="1" for r in participants]);strata=np.array([r["sex"] for r in participants]);offset=np.array([float(r["offset"]) for r in participants]);n=len(participants)
        x0=np.array([[float(r["age"])] for r in participants]) if h["covariates"] else np.zeros((n,0))
        original=load(project/"input-editions"/receipt["label"]/"study.yaml");dataset=next(d for d in original["datasets"] if d["id"]==id_);genotype=dataset["type"]=="gwas";matrix=project/"input-editions"/receipt["label"]/dataset["files"]["matrix"]
        if genotype:
            lines=[line.split("\t") for line in matrix.read_text().splitlines() if not line.startswith("##")];sample_ids=lines[0][9:];values={row[2]:dict(zip(sample_ids,[float(v[0])+float(v[2]) for v in row[9:]])) for row in lines[1:]}
        else:
            rows=table(matrix);values={row["feature_id"]:{key:float(v) if v!="NA" else float("nan") for key,v in row.items() if key!="feature_id"} for row in rows}
        _,null=independent_fit(x0,start,stop,event,strata,offset,policy.get("ties","efron"));ph_table={r["feature_id"]:r for r in table(directory/"survival-diagnostics.tsv")}
        for row in table(directory/f"standard-results/{id_}.tsv"):
            predictor=np.array([values[row["feature_id"]][r["id"]] for r in participants]);predictor=np.where(np.isfinite(predictor),predictor,np.nanmean(predictor));x=np.column_stack((x0,predictor));beta,fit=independent_fit(x,start,stop,event,strata,offset,policy.get("ties","efron"));se=math.sqrt(np.linalg.inv(fit[2])[-1,-1]);lrt=max(0,2*(fit[0]-null[0]));p=2*norm.sf(abs(beta[-1]/se)) if policy.get("test")=="wald" else chi2.sf(lrt,1)
            for field,expected in (("beta",beta[-1]),("se",se),("p_value",p)):
                assert math.isclose(float(row[field]),expected,rel_tol=3e-6,abs_tol=2e-8),(id_,row["feature_id"],field,row[field],expected);checks+=1
            assert int(row["n"])==n and int(row["n_events"])+int(row["n_censored"])==n and row["effect_scale"]=="log_hazard_ratio"
            ph=ph_table[row["feature_id"]]
            if ph["ph_p_value"]!="NA":
                _,_,_,groups=likelihood(beta,x,start,stop,event,strata,offset,policy.get("ties","efron"),True)
                center=sum(math.log(t)*d for t,d,_,_ in groups)/sum(d for _,d,_,_ in groups)
                u=sum((math.log(t)-center)*u for t,d,u,v in groups);cross=sum((math.log(t)-center)*v for t,d,u,v in groups);gg=sum((math.log(t)-center)**2*v for t,d,u,v in groups);efficient=gg-cross@np.linalg.solve(fit[2],cross.T)
                stat=u[-1]**2/efficient[-1,-1];glob=float(u@np.linalg.solve(efficient,u))
                for field,expected in (("ph_statistic",stat),("ph_p_value",chi2.sf(stat,1)),("global_ph_statistic",glob),("global_ph_p_value",chi2.sf(glob,x.shape[1]))):assert math.isclose(float(ph[field]),expected,rel_tol=1e-5,abs_tol=3e-7),(field,ph,expected);checks+=1
            all_beta[(id_,row["feature_id"])]=(float(row["beta"]),float(row["se"]))
        assert "PATIENT_ROW_SENTINEL" not in (directory/"survival-analysis.yaml").read_text()
    for e in records:
        if e["operation_type"] not in ("meta_analysis","discovery_meta_analysis") or e["state"]!="complete":continue
        ids=["a","b"] if e["operation_type"]=="discovery_meta_analysis" else ["a","b","v"]
        path=location(project,e)/("discovery-results.tsv" if e["operation_type"]=="discovery_meta_analysis" else "meta-analysis.tsv")
        for row in table(path):
            items=[all_beta[(i,row["feature_id"])] for i in ids];weight=sum(1/s**2 for b,s in items);expected=sum(b/s**2 for b,s in items)/weight
            for key,value in (("beta",expected),("se",math.sqrt(1/weight))):assert math.isclose(float(row[key]),value,rel_tol=1e-9,abs_tol=1e-10);checks+=1
    return checks


def execute(root,jar):
    root.mkdir(parents=True,exist_ok=False);receipts=[]
    def record(project,label,expected,resume=False):
        r=run(project,jar,label,resume);receipts.append(r);dump(root/"execution-receipts.json",receipts);assert r["state"]==expected,(r,(project/f"{label}-stderr.txt").read_text());return r
    native=root/"molecular";generate(native);first=record(native,"initial","completed");record(native,"replay","completed")
    for name,genotype,controls,adjust in [("unadjusted",False,{},False),("breslow-wald",False,{"ties":"breslow","test":"wald"},True),("genotype",True,{},True),("nominal-ph",False,{"ph_adjustment":"NONE","ph_alpha":1},True)]:
        p=root/name;s=generate(p,genotype,controls,adjust)
        if name=="nominal-ph":s["hypotheses"][0]["analysis"]["goals"].append("impact_assessment");dump(p/"study.yaml",s)
        record(p,"initial","waiting_for_input" if name=="nominal-ph" else "completed")
    # Complete cohort statistics are reused without participant-level endpoint refits or new PH tests.
    supplied=root/"supplied";supplied.mkdir();s=load(native/"study.yaml")
    for d in s["datasets"]:
        e=next(e for e in entries(native,first) if e["operation_type"]=="association" and e["details"]["dataset"]==d["id"]);name=d["id"]+".tsv";shutil.copyfile(location(native,e)/f"standard-results/{name}",supplied/name)
        d.update(stage="association_results",files={"results":name},columns={"result_metadata":{k:k for k in ("model","effect_scale","effect_units","test_method","estimator","standard_error_method","statistic_type")}})
    s["hypotheses"][0]["analysis"]["association"]={"model":"cox"};dump(supplied/"study.yaml",s);record(supplied,"initial","completed")
    for name in ["missing-definition","invalid-interval","invalid-code","no-events","work-bound","fit-failure","related","missing-phenotype","no-ph-time-variation"]:
        p=root/name;s=generate(p,genotype=name=="missing-phenotype",adjust=name!="fit-failure")
        analysis=s["hypotheses"][0]["analysis"]
        if name=="missing-definition":del analysis["association"]["survival"]["event_definition"]
        elif name=="work-bound":analysis["association"]["survival"]["maximum_fit_work"]=1
        elif name=="fit-failure":analysis["association"]["survival"].update(maximum_iterations=1,score_tolerance=1e-12)
        elif name=="related":
            for d in s["datasets"]:d["metadata"]={"related_participants":True}
        else:
            for d in s["datasets"]:
                file=p/d["files"]["phenotype"];rows=table(file);keys=list(rows[0])
                for row in rows:
                    if name=="invalid-interval":row["time"]=row["entry"]
                    elif name=="invalid-code":row["event"]="3"
                    elif name=="no-events":row["event"]="0"
                    elif name=="missing-phenotype" and row["id"].endswith("0") and row["id"][1:]=="0":row["age"]="NA"
                    elif name=="no-ph-time-variation":row["sex"]="A";row["time"]="1" if row["event"]=="1" else "2";row["entry"]="0"
                with file.open("w",encoding="utf-8",newline="") as stream:w=csv.DictWriter(stream,fieldnames=keys,delimiter="\t",lineterminator="\n");w.writeheader();w.writerows(rows)
        dump(p/"study.yaml",s);r=record(p,"initial","completed" if name in ("missing-phenotype","no-ph-time-variation") else "waiting_for_input")
        if name=="missing-definition":
            def command(*args):
                r=subprocess.run(["java","-jar",str(jar),"study",*map(str,args),"--project",str(p)],capture_output=True,text=True,encoding="utf-8");assert r.returncode==0,(args,r.stdout,r.stderr);return yaml.safe_load(r.stdout)
            status=command("status");amendment=p/"amendment-endpoint.yaml"
            dump(amendment,{"schema_version":1,"amendment":{"id":"endpoint","base_study_hash":status["resolved_study_hash"]},"changes":{"update":{"hypotheses":[{"id":"H","analysis":{"association":{"survival":{"event_definition":"Supplied missing synthetic definition"}}}}]}}})
            command("amend",amendment,"--approved-by","synthetic-protocol-fixture");command("plan","--actor-type","external","--actor-id","synthetic-protocol-fixture");command("approve","--approved-by","synthetic-protocol-fixture","--accept-assumptions");record(p,"completion","completed",True)
    # An explicit incompatible source effect scale must stop pooling, not inflate heterogeneity.
    # Generate a clean source-only project to avoid copying execution state.
    clean=root/"scale-source";clean.mkdir();s=load(supplied/"study.yaml")
    for d in s["datasets"]:shutil.copyfile(supplied/d["files"]["results"],clean/d["files"]["results"])
    file=clean/"v.tsv";file.write_text(file.read_text().replace("log_hazard_ratio","log_odds_ratio"),encoding="utf-8");dump(clean/"study.yaml",s);record(clean,"initial","waiting_for_input")
    dump(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))});verify(root,jar)


def verify(root,jar):
    software=load(root/"software.json");assert sha(jar)==software["jar_sha256"]
    receipts=load(root/"execution-receipts.json");checks=0
    for r in receipts:
        p=root/r["project"];assert all(sha(p/"input-editions"/r["label"]/name)==digest for name,digest in r["input_sha256"].items())
        assert sha(p/"input-editions"/r["label"]/"effective-study.yaml")==r["effective_study_sha256"]
        subprocess.run(["java","-jar",str(jar),"study","verify","--project",str(p)],capture_output=True,check=True)
        if (r["state"]=="completed" or r["project"]=="nominal-ph") and r["project"]!="supplied":checks+=numerical(p,r)
        inv=next((e for e in entries(p,r) if e["operation_type"]=="finding_inventory" and e["state"]=="complete"),None)
        assert inv is not None,(r,"requested inventory was not completed")
        if inv:
            text=(location(p,inv)/"finding-inventory.jsonl").read_text();assert "PATIENT_ROW_SENTINEL" not in text
            if (r["state"]=="completed" or r["project"]=="nominal-ph") and r["project"]!="supplied":assert "survival_model_context" in text and "ph_family_status" in text
    p=root/"supplied";r=next(r for r in receipts if r["project"]=="supplied");assert not any(e["operation_type"] in ("association","participant_qc") for e in entries(p,r))
    p=root/"nominal-ph";r=next(r for r in receipts if r["project"]=="nominal-ph")
    for e in entries(p,r):
        if e["operation_type"]=="association":
            rows=table(location(p,e)/"survival-diagnostics.tsv");assert any(row["ph_alert"]=="true" for row in rows);assert all(row["ph_adjustment"]=="NONE" and float(row["ph_p_value"])==float(row["ph_adjusted_p"]) for row in rows);assert "nominal" in e["details"]["required_manuscript_caveat"]
    def dossier_candidates(project,receipt):
        inv=next(e for e in entries(project,receipt) if e["operation_type"]=="finding_inventory");return {d["feature_id"] for line in (location(project,inv)/"finding-inventory.jsonl").read_text().splitlines() if (d:=json.loads(line))["candidate"]}
    base=next(r for r in receipts if r["project"]=="molecular");assert dossier_candidates(root/"molecular",base)==dossier_candidates(p,r)
    contexts=[]
    def walk(value):
        if isinstance(value,dict):
            if value.get("kind")=="survival_model_context":contexts.append(value)
            for child in value.values():walk(child)
        elif isinstance(value,list):
            for child in value:walk(child)
    for path in p.glob("runs/**/comparisons/**/evidence.yaml"):
        text=path.read_text();assert "PATIENT_ROW_SENTINEL" not in text;walk(yaml.safe_load(text))
    assert any(o["ph_adjustment"]=="NONE" and o["ph_alert"]=="true" and o["primary"] is False for o in contexts)
    request_metadata=list(p.glob("runs/**/comparisons/**/metadata.yaml"));assert request_metadata
    for path in request_metadata:
        metadata=load(path);assert metadata.get("request_sent") is False and metadata.get("request_attempted") is False
    p=root/"no-ph-time-variation";r=next(r for r in receipts if r["project"]=="no-ph-time-variation")
    for e in entries(p,r):
        if e["operation_type"]=="association":assert all(row["ph_p_value"]==row["ph_adjusted_p"]=="NA" and row["ph_alert"]=="false" for row in table(location(p,e)/"survival-diagnostics.tsv"))
    for name,expected in [("genotype",{"g0","g1"}),("missing-phenotype",{"g0"})]:
        p=root/name;r=next(r for r in receipts if r["project"]==name)
        for e in entries(p,r):
            if e["operation_type"]=="association":assert {row["feature_id"] for row in table(location(p,e)/f"standard-results/{e['details']['dataset']}.tsv")}==expected
    report={"status":"passed","cases":len(receipts),"states":{s:sum(r["state"]==s for r in receipts) for s in sorted({r["state"] for r in receipts})},"independent_numeric_checks":checks,"scientific_calibration_claimed":False,"jar_sha256":sha(jar)}
    dump(root/"verification.json",report);print(json.dumps(report))


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("action",choices=["run","verify"]);p.add_argument("directory",type=Path);p.add_argument("--jar",type=Path,required=True);a=p.parse_args();(execute if a.action=="run" else verify)(a.directory.resolve(),a.jar.resolve())
