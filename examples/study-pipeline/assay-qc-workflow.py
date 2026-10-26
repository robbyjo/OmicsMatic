"""Offline synthetic molecular-QC acceptance with independent NumPy/SciPy checks.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
No real participants, assay certification or biological findings are involved.
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
from scipy.stats import t, chi2
from scipy.optimize import brentq
from scipy.special import logsumexp
import yaml

MODES = ("audit", "filter-analysis", "filter-cox", "filter-interactions", "detection", "detection-precision", "detection-negative", "counts", "supplied", "supplied-count-overflow",
         "duplicate-feature", "bad-scale", "zero-retained", "incomplete-supplied",
         "detection-order", "cell-bound", "risk-base", "risk-evaluation-change", "risk-predictor-drop")
HOLDS = {"supplied-count-overflow", "duplicate-feature", "bad-scale", "zero-retained", "incomplete-supplied", "detection-order", "detection-negative", "cell-bound", "risk-predictor-drop"}

def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda:stream.read(1048576),b""):h.update(block)
    return h.hexdigest()

def save(path,value):path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def table(path,header,records):
    with path.open("w",encoding="utf-8",newline="") as stream:
        w=csv.writer(stream,delimiter="\t",lineterminator="\n");w.writerow(header);w.writerows(records)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    r=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True,encoding="utf-8",errors="replace")
    assert r.returncode==0,r.stdout+r.stderr
    return r.stdout
def artifacts(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    directory=project/"runs"/status["run_id"]/"operations"
    return status,[(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in sorted(directory.iterdir())]
def one(project,kind):
    found=[item for item in artifacts(project)[1] if item[1]["operation_type"]==kind]
    assert len(found)==1,(project,kind,len(found));return found[0]

def generate(root,mode):
    project=root/mode;project.mkdir();n=12;ids=[f"local-{i}" for i in range(n)]
    x=np.arange(n,dtype=float);g=1+x+.2*np.sin(x);h=x%5+.1*x
    data=np.array([g,h,np.ones(n)*2,np.where(x>=9,3+x,np.nan),np.full(n,np.nan)])
    data[1,0]=np.nan;names=["G","H","constant","sparse","empty"]
    policy={};goals=["assay_qc"];extra={};files={"matrix":"matrix.tsv"}
    if mode in ("filter-analysis","filter-cox","filter-interactions","zero-retained"):
        policy={"mode":"filter","maximum_feature_missing":.3,"maximum_sample_missing":.45}
    if mode=="filter-analysis":
        goals=["association","network_analysis","finding_inventory"];files["phenotype"]="phenotype.tsv"
        table(project/"phenotype.tsv",["id","y"],zip(ids,3+.7*g+.2*np.cos(x)))
        extra["network"]={"minimum_samples":4,"threshold":.1,"thresholds":[.1],"bootstraps":0,"preprocessing":"Synthetic continuous values; source QC only"}
    if mode in ("filter-cox","filter-interactions"):
        goals=["association"] if mode=="filter-cox" else ["subgroup_interactions"]
        files["phenotype"]="phenotype.tsv"
        if mode=="filter-cox":
            table(project/"phenotype.tsv",["id","time","event"],zip(ids,[8,2,10,5,11,1,9,3,7,12,4,6],np.ones(n,dtype=int)))
            extra["association"]={"model":"cox","survival":{"time_column":"time","event_column":"event","time_units":"synthetic units","event_definition":"Synthetic event","censoring_definition":"No censoring in generated data","rationale":"Generated independent baseline test","independent_censoring_rationale":"All generated events observed","proportional_hazards_rationale":"Numerical acceptance only; no biological PH claim"}}
        else:
            table(project/"phenotype.tsv",["id","y","group"],zip(ids,3+.7*g+.2*np.cos(x),(x.astype(int)%2).astype(str)))
            extra["subgroup_interactions"]={"enabled":True,"independent_rows":True,"method":"ols-classical","features":["G"],"minimum_group_samples":4,"groups":[{"id":"group","column":"group","levels":["0","1"],"rationale":"Prespecified synthetic groups"}]}
    if mode=="zero-retained":data=np.ones((1,n));names=["constant"]
    if mode=="duplicate-feature":names[-1]="G"
    if mode=="bad-scale":policy["value_scale"]="proportion"
    if mode=="cell-bound":policy["maximum_cells"]=n*len(names)-1
    if mode in ("detection","detection-order","detection-precision","detection-negative"):
        policy={"mode":"filter","detection_kind":"p_value","maximum_feature_missing":1,"maximum_sample_missing":1,"minimum_observed":1,"drop_constant":False}
        detection=np.zeros_like(data);detection[0,1]=.2;detection[1,2]=np.nan
        detection_names=names.copy()
        if mode=="detection-order":detection_names[0],detection_names[1]=detection_names[1],detection_names[0]
        table(project/"detect.tsv",["id",*ids],[[name,*["NA" if np.isnan(v) else v for v in values]] for name,values in zip(detection_names,detection)])
        if mode in ("detection-precision","detection-negative"):
            detection=np.full(data.shape,"0",dtype=object);detection[0,0]="1e-999" if mode=="detection-precision" else "-1e-999";policy["detection_threshold"]=0
            table(project/"detect.tsv",["id",*ids],[[name,*values] for name,values in zip(names,detection)])
        files["assay_detection"]="detect.tsv"
    if mode=="counts":
        policy={"value_scale":"counts"};data=np.array([[9007199254740991,1,2]*4,[9007199254740991,3,4]*4,[9007199254740991,5,6]*4],dtype=float);names=["C1","C2","C3"]
    if mode.startswith("risk-"):
        n=90;ids=[f"risk-local-{i}" for i in range(n)];x=np.arange(n);g=x%7-3+x*.03;h=x%5*1.1+x*.01
        data=np.array([g,h,np.ones(n)]);data[0,0]=np.nan;names=["G","H","constant"]
        if mode=="risk-evaluation-change":data[0,60:]=data[0,60:]*100+7;data[1,60:]=data[1,60:]*500-8
        if mode=="risk-predictor-drop":data[0,:]=2
        policy={"mode":"filter","maximum_feature_missing":.2,"maximum_sample_missing":.5}
        goals=["risk_score_evaluation"];files["phenotype"]="phenotype.tsv"
        table(project/"phenotype.tsv",["sample_id","partition","age","y"],[[ids[i],"training" if i<30 else "tuning" if i<60 else "evaluation",h[i],2+.4*h[i]+1.5*g[i]+.1*math.sin(i)] for i in range(n)])
        extra["risk_score"]={"outcome":"y","features":["G"],"covariates":["age"],"independent_rows":True,"lambdas":[.1],"alpha":0,"missing":"training_mean","bootstraps":0,"preprocessing_provenance":"Synthetic raw predictor; no global imputation or fitted transform"}
    table(project/"matrix.tsv",["feature_id",*ids],[[name,*["NA" if np.isnan(v) else format(v,".17g") for v in values]] for name,values in zip(names,data)])
    if mode in ("supplied","incomplete-supplied","supplied-count-overflow"):
        policy={"source":"supplied","supplied_report":"report.yaml","supplied_samples":"samples.tsv","supplied_features":"features.tsv"}
        save(project/"report.yaml",{"schema_version":1,"dataset_id":"a","matrix_sha256":sha(project/"matrix.tsv"),"input_state":"post_qc","sample_count":(1<<64)+n if mode=="supplied-count-overflow" else n,"feature_count":len(names),"method":{"name":"Synthetic lab QC","version":"1","parameters":{"upstream_rule":"source-declared"}}})
        table(project/"samples.tsv",["sample_id","decision","reason"],[[s,"exclude" if i==0 else "keep","private local audit reason"] for i,s in enumerate(ids)])
        selected=names[:-1] if mode=="incomplete-supplied" else names
        table(project/"features.tsv",["feature_id","decision"],[[name,"keep" if name in ("G","constant") else "exclude"] for name in selected])
    study={"schema_version":1,"project":{"id":"assay-fixture","title":"Synthetic assay QC acceptance","description":"Generated data only; no scientific evidence"},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[{"id":"a","type":"expression","stage":"processed_matrix","files":files,"columns":{"sample_id":"sample_id" if mode.startswith("risk-") else "id"}}],"hypotheses":[{"id":"H","description":"Prespecified synthetic assay source QC","outcome":"event" if mode=="filter-cox" else "y","inputs":{"discovery":["a"]},"analysis":{"mode":"manual","goals":goals,"association":{"model":"ols"},"assay_qc":policy,**extra}}]}
    save(project/"study.json",study);np.savez(project/"oracle.npz",data=data,names=np.array(names),samples=np.array(ids))
    save(project/"input-hashes.json",{p.name:sha(p) for p in project.iterdir() if p.is_file()})
    return project

def verify(root,jar):
    pins=json.loads((root/"execution-software.json").read_text(encoding="utf-8"));assert sha(jar)==pins["jar_sha256"];assert sha(root/"workflow-verifier-source.py")==pins["verifier_sha256"]
    checks=0;output_hashes=0
    for mode in MODES:
        project=root/mode
        for file,digest in json.loads((project/"input-hashes.json").read_text(encoding="utf-8")).items():assert sha(project/file)==digest,(mode,file)
        state,operations=artifacts(project);assert state["state"]==("waiting_for_input" if mode in HOLDS else "completed"),(mode,state)
        for folder,manifest in operations:
            for file,digest in manifest.get("output_sha256",{}).items():assert sha(folder/file)==digest;(output_hashes:=output_hashes+1)
        folder,manifest=one(project,"assay_qc");report=yaml.safe_load((folder/"assay-qc.yaml").read_text(encoding="utf-8"))
        if mode in HOLDS and mode!="risk-predictor-drop":assert report["complete"] is False;assert not (folder/"assay-matrix.tsv").exists();continue
        assert report["complete"] is True;assert report["imputation"]=="none"
        with np.load(project/"oracle.npz") as oracle:data=oracle["data"];names=list(oracle["names"]);samples=list(oracle["samples"])
        if mode=="supplied":
            actual=rows(folder/"assay-feature-qc.tsv");assert all(r["observed"]=="NA" for r in actual);assert "parameters" not in report
            with (folder/"assay-matrix.tsv").open(encoding="utf-8") as stream:matrix=list(csv.reader(stream,delimiter="\t"))
            assert matrix[0][1:]==samples[1:];assert [r[0] for r in matrix[1:]]==["G","constant"]
            np.testing.assert_array_equal(np.array([r[1:] for r in matrix[1:]],dtype=float),data[[0,2],1:]);checks+=2*(len(samples)-1)
        else:
            working=data.copy()
            if mode=="detection":working[0,1]=np.nan;working[1,2]=np.nan
            if mode=="detection-precision":working[0,0]=np.nan
            frows=rows(folder/"assay-feature-qc.tsv");srows=rows(folder/"assay-sample-qc.tsv")
            for i,r in enumerate(frows):
                finite=working[i,np.isfinite(working[i])];missing=len(samples)-len(finite)
                assert int(r["observed"])==len(finite);assert int(r["missing"])==missing;checks+=2
                if len(finite):np.testing.assert_allclose(float(r["mean"]),np.mean(finite),rtol=2e-14,atol=1e-14);checks+=1
                if len(finite)>1:np.testing.assert_allclose(float(r["sd"]),np.std(finite,ddof=1),rtol=2e-14,atol=1e-14);checks+=1
            for i,r in enumerate(srows):assert int(r["observed"])==np.isfinite(working[:,i]).sum();assert float(r["missing_fraction"])==np.isnan(working[:,i]).mean();checks+=2
            if mode=="counts":
                for i,r in enumerate(srows):assert int(r["library_size"])==sum(int(v) for v in data[:,i]);checks+=1
            if mode=="audit":assert report["analysis_matrix"]=="original_source";assert not (folder/"assay-matrix.tsv").exists()
            else:
                keepf=[i for i,r in enumerate(frows) if r["decision"]=="keep"];keeps=[i for i,r in enumerate(srows) if r["decision"]=="keep"]
                if mode=="counts":assert report["analysis_matrix"]=="original_source"
                else:
                    with (folder/"assay-matrix.tsv").open(encoding="utf-8") as stream:matrix=list(csv.reader(stream,delimiter="\t"))
                    assert matrix[0][1:]==[samples[i] for i in keeps];assert [r[0] for r in matrix[1:]]==[names[i] for i in keepf]
                    actual=np.array([[np.nan if v=="NA" else float(v) for v in r[1:]] for r in matrix[1:]])
                    np.testing.assert_array_equal(actual,working[np.ix_(keepf,keeps)]);checks+=actual.size
        if mode=="filter-analysis":
            association,_=one(project,"association");results=rows(association/"standard-results/a.tsv");assert {r["feature_id"] for r in results}=={"G","H"}
            pheno={r["id"]:float(r["y"]) for r in rows(project/"phenotype.tsv")};selected=[i for i,r in enumerate(srows) if r["decision"]=="keep"]
            for r in results:
                v=data[names.index(r["feature_id"]),selected];y=np.array([pheno[samples[i]] for i in selected]);design=np.column_stack([np.ones(len(v)),v]);beta=np.linalg.lstsq(design,y,rcond=None)[0];residual=y-design@beta;se=np.sqrt(np.sum(residual**2)/(len(v)-2)*np.linalg.inv(design.T@design)[1,1]);p=2*t.sf(abs(beta[1]/se),len(v)-2)
                np.testing.assert_allclose([float(r[k]) for k in ("beta","se","p_value")],[beta[1],se,p],rtol=2e-8,atol=1e-12);checks+=3
            network,nm=one(project,"network_analysis");assert nm["details"]["analysis_samples"]==11;assert nm["details"]["input_samples"]==11
        if mode=="filter-analysis":
            inventory,_=one(project,"finding_inventory");text=(inventory/"finding-inventory.jsonl").read_text(encoding="utf-8");assert "source_assay_qc" in text;assert "local-" not in text
            def contexts(value):
                if isinstance(value,dict):
                    if value.get("kind")=="source_assay_qc":yield value
                    for child in value.values():yield from contexts(child)
                elif isinstance(value,list):
                    for child in value:yield from contexts(child)
            observations=[q for line in text.splitlines() for q in contexts(json.loads(line))];assert observations
            for q in observations:assert q["source_features"]==5 and q["retained_features"]==2 and q["retained_samples"]==11;assert "supplied_method_report" not in q and "decision" not in q and "sample_id" not in q
        if mode=="filter-cox":
            association,_=one(project,"association");results=rows(association/"standard-results/a.tsv");assert {r["feature_id"] for r in results}=={"G","H"}
            times=np.array([8,2,10,5,11,1,9,3,7,12,4,6])[1:]
            for r in results:
                v=data[names.index(r["feature_id"]),1:]
                def terms(beta):
                    score=information=likelihood=0.
                    for i,time in enumerate(times):
                        risk=v[times>=time];eta=beta*risk;weights=np.exp(eta-logsumexp(eta));mean=weights@risk
                        score+=v[i]-mean;information+=weights@((risk-mean)**2);likelihood+=beta*v[i]-logsumexp(eta)
                    return score,information,likelihood
                beta=brentq(lambda b:terms(b)[0],-10,10,xtol=1e-13);_,info,ll=terms(beta);expected=[beta,1/math.sqrt(info),chi2.sf(2*(ll-terms(0)[2]),1)]
                np.testing.assert_allclose([float(r[k]) for k in ("beta","se","p_value")],expected,rtol=2e-7,atol=1e-10);checks+=3
        if mode=="filter-interactions":
            interaction,_=one(project,"subgroup_interactions");r=rows(interaction/"subgroup-interactions.tsv")[0];assert int(r["n"])==11
            ph=rows(project/"phenotype.tsv")[1:];g=data[0,1:];group=np.array([int(r["group"]) for r in ph]);y=np.array([float(r["y"]) for r in ph]);design=np.column_stack([np.ones(11),g,group,g*group]);beta=np.linalg.lstsq(design,y,rcond=None)[0];residual=y-design@beta;se=math.sqrt(np.sum(residual**2)/7*np.linalg.inv(design.T@design)[3,3]);p=2*t.sf(abs(beta[3]/se),7)
            np.testing.assert_allclose([float(r[k]) for k in ("beta","se","p_value")],[beta[3],se,p],rtol=2e-8,atol=1e-11);checks+=3
        if mode=="risk-predictor-drop":score,sm=one(project,"risk_score_evaluation");assert sm["state"]=="waiting_for_input";assert not (score/"score-model.yaml").exists()
        cli(jar,"verify","--project",project)
    mutation=json.loads((root/"cache-mutation.json").read_text(encoding="utf-8"));assert mutation["restored_reused"] is True;np.testing.assert_allclose(mutation["mutated_mean"],mutation["original_mean"]+.5/12,rtol=1e-14);checks+=1
    for run in (root/"audit/runs").iterdir():
        for folder in (run/"operations").iterdir():
            manifest=yaml.safe_load((folder/"manifest.yaml").read_text(encoding="utf-8"))
            for file,digest in manifest.get("output_sha256",{}).items():assert sha(folder/file)==digest;output_hashes+=1
    a,_=one(root/"risk-base","risk_score_evaluation");b,_=one(root/"risk-evaluation-change","risk_score_evaluation")
    # Evaluation values and a non-model feature change globally. Training means,
    # selection and refitted development weights must remain byte-for-byte equal.
    assert (a/"score-weights.tsv").read_bytes()==(b/"score-weights.tsv").read_bytes();checks+=1
    report={"status":"passed","cases":len(MODES),"complete_cases":len(MODES)-len(HOLDS),"deliberate_holds":len(HOLDS),"independent_numeric_checks":checks,"output_hashes_verified":output_hashes,"jar_sha256":sha(jar),"verifier_sha256":pins["verifier_sha256"],"evaluation_changes_do_not_change_development_weights":True,"source_hashes_unchanged":True,"external_requests":0,"biological_or_assay_certification":False,"full_pipeline_conformance":False}
    target=root/"packaged-verification.json"
    if target.exists():assert json.loads(target.read_text(encoding="utf-8"))==report
    else:save(target,report)
    print(json.dumps(report,indent=2))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False);software=root/"software";software.mkdir();pinned=software/"jlinalg.jar";shutil.copyfile(jar,pinned);assert sha(pinned)==sha(jar);jar=pinned
        shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py");save(root/"execution-software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(root/"workflow-verifier-source.py")})
        for mode in MODES:project=generate(root,mode);cli(jar,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
        project=root/"audit";folder,_=one(project,"assay_qc");original_mean=float(rows(folder/"assay-feature-qc.tsv")[0]["mean"]);original=(project/"matrix.tsv").read_bytes()
        text=original.decode("utf-8");lines=text.splitlines();cells=lines[1].split("\t");cells[1]=str(float(cells[1])+.5);lines[1]="\t".join(cells);(project/"matrix.tsv").write_text("\n".join(lines)+"\n",encoding="utf-8")
        cli(jar,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture");folder,manifest=one(project,"assay_qc");assert manifest.get("reused_from_cache") is not True;mutated_mean=float(rows(folder/"assay-feature-qc.tsv")[0]["mean"])
        (project/"matrix.tsv").write_bytes(original);cli(jar,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture");folder,manifest=one(project,"assay_qc");save(root/"cache-mutation.json",{"original_mean":original_mean,"mutated_mean":mutated_mean,"restored_reused":manifest.get("reused_from_cache") is True})
    verify(root,jar)
