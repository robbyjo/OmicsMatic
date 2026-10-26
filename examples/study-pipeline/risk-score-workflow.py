"""Independent offline score-workflow acceptance. Requires NumPy, SciPy and PyYAML.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Every person, score, cohort and external result is synthetic.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import numpy as np
import scipy
from scipy.stats import t
import yaml

MODES=("native","matrix","model-reuse","weights","binary-raw","binary-probability","supplied-evaluation","group-overlap","limited-family","unsupported-training")
WAITING={"group-overlap","limited-family","unsupported-training"}
TOL=2e-8

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,obj):path.write_text(json.dumps(obj,indent=2)+"\n",encoding="utf-8")
def table(path,header,records):
    with path.open("w",encoding="utf-8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(header);writer.writerows(records)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    assert result.returncode==0,result.stdout+result.stderr
    return result.stdout
def pin(root,jar):
    directory=root/"software";directory.mkdir();digest=sha(jar);pinned=directory/("jlinalg-"+digest+".jar");shutil.copyfile(jar,pinned);assert sha(pinned)==digest==sha(jar)
    verifier=root/"workflow-verifier-source.py";shutil.copyfile(Path(__file__),verifier)
    write(root/"execution-software.json",{"jar_sha256":digest,"jar":str(pinned.relative_to(root)),"verifier_sha256":sha(verifier)})
    return pinned
def artifacts(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text());directory=project/"runs"/status["run_id"]/"operations"
    return status,[(folder,yaml.safe_load((folder/"manifest.yaml").read_text())) for folder in sorted(directory.iterdir())]
def one(project,kind):
    found=[item for item in artifacts(project)[1] if item[1]["operation_type"]==kind];assert len(found)==1,(project,kind,found);return found[0]

def generate(root,mode):
    project=root/mode;project.mkdir();records=[]
    for i in range(90):
        x=i%7-3+i*.03;age=i%5*1.1+i*.01;y=2+.4*age+1.5*x+.1*math.sin(i);z=-.7*x+.2*age+.2*math.cos(i);w=.5*x-.1*age+.3*math.sin(i*.7)
        records.append([f"private-person-{i}","training" if i<30 else "tuning" if i<60 else "evaluation",f"f{i//2}","NA" if i==0 else x,age,y,z,w,1,"F" if i%2==0 else "M"])
    header=["sample_id","partition","family","x","age","y","z","w","constant","sex"]
    table(project/"participants.tsv",header,records)
    policy={"file":"participants.tsv","outcome":"y","features":["x"],"covariates":["age","sex"],"categorical_covariates":["sex"],"group_columns":["family"],"resampling_group":"family","lambdas":[1,.01],"missing":"training_mean","bootstraps":40,"seed":7,"adjustment":"BY","subgroups":["sex"],"related_phenotypes":[{"column":"z"},{"column":"w"}],"preprocessing_provenance":"Synthetic raw features; no outcome-based feature selection or upstream fitted preprocessing"}
    supplied=[];datasets=[];goals=["risk_score_evaluation"]
    if mode=="matrix":
        policy.pop("file");table(project/"matrix.tsv",["feature_id"]+[row[0] for row in records],[["x"]+[row[3] for row in records]])
        datasets=[{"id":"source","type":"expression","stage":"processed_matrix","cohort":"Synthetic","files":{"matrix":"matrix.tsv","phenotype":"participants.tsv"},"columns":{"sample_id":"sample_id"}}]
    elif mode=="model-reuse":
        source,_=one(root/"native","risk_score_evaluation");directory=project/"model";directory.mkdir()
        for name in ("score-model.yaml","score-weights.tsv.training-ids.tsv"):shutil.copyfile(source/name,directory/name)
        policy.update(mode="model",model="model/score-model.yaml",external_independence="Repeat frozen application to the same synthetic held-out data; not independent replication")
    elif mode=="weights":
        policy={"file":"dosages.tsv","mode":"weights","weights":"weights.tsv","alleles":"alleles.tsv","outcome":"y","baseline_column":"base","independent_rows":True,"bootstraps":40,"seed":7,"external_independence":"Synthetic external weights, no development overlap; fixed diploid build and dosage convention"}
        table(project/"dosages.tsv",["sample_id","partition","dose","y","base"],[[f"target-{i}","evaluation",i%3,5-2*(i%3)+.1*math.sin(i),0] for i in range(24)])
        table(project/"weights.tsv",["variant","ea","oa","weight","intercept"],[["dose","A","C",2,1]])
        table(project/"alleles.tsv",["variant","ea","oa"],[["dose","C","A"]])
    elif mode.startswith("binary-"):
        record=[]
        for i in range(40):
            score=math.sin(i*.7)+i%3*.2;record.append([f"binary-{i}","evaluation",int(i%7 in (0,2,3)),score,1/(1+math.exp(-score)),.4])
        table(project/"binary.tsv",["sample_id","partition","y","score","probability","baseline"],record)
        policy={"file":"binary.tsv","mode":"scores","outcome":"y","family":"binary","score_column":"score","independent_rows":True,"bootstraps":40,"seed":7,"external_independence":"Synthetic frozen scores, disjoint external development"}
        if mode=="binary-probability":policy.update(prediction_column="probability",baseline_column="baseline",prediction_scale="probability")
    elif mode=="supplied-evaluation":
        policy.update(mode="scores",score_column="x",external_independence="Synthetic existing fixed score and source evaluation; no development overlap for related-trait sample")
        table(project/"supplied-metrics.tsv",["score_id","phenotype","evaluation_population","metric","estimate"],[["score","y","External synthetic holdout","rmse",.123],["score","y","External synthetic holdout","r_squared_score",.8]])
        supplied=[{"id":"existing-evaluation","kind":"risk_score_evaluation","files":{"results":"supplied-metrics.tsv"},"method":{"name":"Previously completed synthetic evaluation"},"scope":{"population":"Synthetic external held-out participants"},"limitations":["Performance is supplied rather than reproduced; this is a synthetic source"]}]
        goals.append("risk_score_related_phenotypes")
    elif mode=="group-overlap":
        records[60][2]="f0";table(project/"participants.tsv",header,records)
    elif mode=="limited-family":policy["related_phenotypes"].append({"column":"constant"})
    elif mode=="unsupported-training":policy["family"]="binary"
    analysis={"mode":"manual","goals":goals,"risk_score":policy}
    if supplied:analysis["supplied_results"]=supplied
    study={"schema_version":1,"project":{"id":"risk-score-"+mode,"title":"Synthetic score workflow "+mode,"description":"Offline independent numerical and source-reuse acceptance; no biological conclusion"},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":datasets,"hypotheses":[{"id":"H","description":"Registered synthetic held-out score analysis","analysis":analysis}]}
    write(project/"study.json",study)
    files={str(path.relative_to(project)):sha(path) for path in project.rglob("*") if path.is_file()}
    write(project/"input-hashes.json",files)
    return project

def arrays(records):
    x=np.array([[np.nan if row["x"] in ("NA","NaN","") else float(row["x"])] for row in records]);y=np.array([float(row["y"]) for row in records]);C=np.array([[1,float(row["age"]),int(row["sex"]=="M")] for row in records],dtype=float)
    return x,y,C
def fit(x,y,C,indices,penalty):
    means=np.nanmean(x[indices],axis=0);X=np.where(np.isnan(x),means,x);baseline=np.linalg.lstsq(C[indices],y[indices],rcond=None)[0];r=y[indices]-C[indices]@baseline;center=X[indices].mean(axis=0);scale=np.sqrt(((X[indices]-center)**2).mean(axis=0));Z=(X[indices]-center)/scale
    beta=np.linalg.solve(Z.T@Z/len(indices)+np.eye(Z.shape[1])*penalty,Z.T@(r-r.mean())/len(indices))/scale
    intercept=r.mean()-center@beta
    return beta,float(intercept),means,baseline
def expected_native(project):
    record=rows(project/"participants.tsv");x,y,C=arrays(record);training=np.array([i for i,row in enumerate(record) if row["partition"]=="training"]);tuning=np.array([i for i,row in enumerate(record) if row["partition"]=="tuning"])
    errors=[]
    for penalty in (1,.01):
        beta,intercept,means,baseline=fit(x,y,C,training,penalty);score=np.where(np.isnan(x),means,x)@beta+intercept;errors.append(float(np.mean((y[tuning]-C[tuning]@baseline-score[tuning])**2)))
    selected=(1,.01)[min(range(2),key=errors.__getitem__)];beta,intercept,means,baseline=fit(x,y,C,np.r_[training,tuning],selected);score=np.where(np.isnan(x),means,x)@beta+intercept;return record,y,C,score,C@baseline+score,C@baseline,(beta,intercept,means,baseline,selected,errors)
def metrics(y,score,prediction,baseline):
    result={"rmse":float(np.sqrt(np.mean((y-prediction)**2))),"baseline_rmse":float(np.sqrt(np.mean((y-baseline)**2))),"incremental_predictive_r2":float(1-np.sum((y-prediction)**2)/np.sum((y-baseline)**2))};calibration=np.linalg.lstsq(np.c_[np.ones(len(y)),prediction],y,rcond=None)[0];result.update(calibration_intercept=float(calibration[0]),calibration_slope=float(calibration[1]));return result
class JavaRandom:
    def __init__(self,seed):self.state=(seed^0x5deece66d)&((1<<48)-1)
    def next(self,bits):self.state=(self.state*0x5deece66d+11)&((1<<48)-1);return self.state>>(48-bits)
    def next_int(self,bound):
        if bound&(bound-1)==0:return (bound*self.next(31))>>31
        while True:
            bits=self.next(31);value=bits%bound
            if bits-value+(bound-1)<1<<31:return value
def check_native(project):
    folder,manifest=one(project,"risk_score_evaluation");record,y,C,score,prediction,baseline,model=expected_native(project);beta,intercept,means,baseline_beta,selected,errors=model;weights=rows(folder/"score-weights.tsv");recipe=yaml.safe_load((folder/"score-model.yaml").read_text())
    assert math.isclose(float(weights[0]["weight"]),beta[0],rel_tol=TOL,abs_tol=TOL);assert math.isclose(float(weights[0]["imputation_mean"]),means[0],abs_tol=TOL);assert math.isclose(recipe["score_intercept"],intercept,abs_tol=TOL);np.testing.assert_allclose(recipe["baseline_weights"],baseline_beta,rtol=TOL,atol=TOL);assert recipe["lambda"]==selected
    tuning=rows(folder/"tuning.tsv");np.testing.assert_allclose([float(row["tuning_mse"]) for row in tuning],errors,rtol=TOL,atol=TOL)
    applied=rows(folder/"participant-scores.tsv");np.testing.assert_allclose([float(row["score"]) for row in applied],score,rtol=TOL,atol=TOL);np.testing.assert_allclose([float(row["prediction"]) for row in applied],prediction,rtol=TOL,atol=TOL)
    indices=np.array([i for i,row in enumerate(record) if row["partition"]=="evaluation"]);expected=metrics(y[indices],score[indices],prediction[indices],baseline[indices]);actual=[row for row in rows(folder/"risk-score-evaluation.tsv") if row["subgroup"]=="all"]
    units=list(dict.fromkeys(record[i]["family"] for i in indices));by_group={group:[i for i in indices if record[i]["family"]==group] for group in units};rng=JavaRandom(7);draws={key:[] for key in expected}
    for _ in range(40):
        sampled=np.array([i for __ in units for i in by_group[units[rng.next_int(len(units))]]]);values=metrics(y[sampled],score[sampled],prediction[sampled],baseline[sampled])
        for key,value in values.items():draws[key].append(value)
    for row in actual:
        key=row["metric"];assert math.isclose(float(row["estimate"]),expected[key],rel_tol=TOL,abs_tol=TOL);bounds=np.percentile(draws[key],[2.5,97.5]);np.testing.assert_allclose([float(row["ci_lower"]),float(row["ci_upper"])],bounds,rtol=TOL,atol=TOL);assert int(row["bootstrap_valid"])==int(row["bootstrap_attempted"])==40
    for label in ("F","M"):assert any(row["subgroup"]==label for row in rows(folder/"risk-score-evaluation.tsv"))
    check_related(folder,record,C,score)
    assert manifest["details"]["evaluation_rows"]==30 and manifest["details"]["evaluation_independent_units"]==15
def check_related(folder,record,C,score):
    indices=np.array([i for i,row in enumerate(record) if row["partition"]=="evaluation"]);X=np.c_[C[indices],score[indices]];bread=np.linalg.inv(X.T@X);groups=list(dict.fromkeys(record[i]["family"] for i in indices));observed=rows(folder/"related-phenotypes.tsv");ps=[]
    for row in observed:
        y=np.array([float(record[i][row["phenotype"]]) for i in indices]);beta=np.linalg.lstsq(X,y,rcond=None)[0];residual=y-X@beta;meat=np.zeros_like(bread)
        for group in groups:
            keep=np.array([record[i]["family"]==group for i in indices]);u=X[keep].T@residual[keep];meat+=np.outer(u,u)
        variance=bread@meat@bread*len(groups)/(len(groups)-1)*(len(indices)-1)/(len(indices)-X.shape[1]);se=math.sqrt(variance[-1,-1]);p=2*t.sf(abs(beta[-1]/se),len(groups)-1);critical=t.ppf(.975,len(groups)-1);expected=[beta[-1],se,beta[-1]-critical*se,beta[-1]+critical*se,p]
        np.testing.assert_allclose([float(row[key]) for key in ("beta","se","ci_lower","ci_upper","p_value")],expected,rtol=2e-6,atol=1e-11);ps.append(p)
    harmonic=sum(1/i for i in range(1,len(ps)+1));order=sorted(range(len(ps)),key=ps.__getitem__);q=[0]*len(ps);minimum=1
    for rank in range(len(ps),0,-1):at=order[rank-1];minimum=min(minimum,ps[at]*len(ps)*harmonic/rank);q[at]=minimum
    np.testing.assert_allclose([float(row["adjusted_p"]) for row in observed],q,rtol=2e-6,atol=1e-11)

def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text());assert sha(jar)==software["jar_sha256"] and sha(root/"workflow-verifier-source.py")==software["verifier_sha256"]
    for mode in MODES:
        project=root/mode;assert (project/"fixture-resources").resolve().is_relative_to(project.resolve())
        for name,digest in json.loads((project/"input-hashes.json").read_text()).items():assert sha(project/name)==digest,(project,name)
        status,operations=artifacts(project);assert status["state"]==("waiting_for_input" if mode in WAITING else "completed"),(mode,status)
        assert not any(doc["operation_type"] in ("association","meta_analysis","participant_qc") for _,doc in operations)
        if mode in ("native","matrix"):check_native(project)
        elif mode=="model-reuse":
            folder,doc=one(project,"risk_score_evaluation");native,_=one(root/"native","risk_score_evaluation");assert (folder/"participant-scores.tsv").read_bytes()==(native/"participant-scores.tsv").read_bytes();assert doc["details"]["model_refitted"] is False;assert not (folder/"score-weights.tsv").exists()
        elif mode=="weights":
            folder,doc=one(project,"risk_score_evaluation");score=rows(folder/"participant-scores.tsv");assert [float(row["score"]) for row in score]==[5-2*(i%3) for i in range(24)];assert doc["details"]["model_refitted"] is False
        elif mode.startswith("binary-"):
            folder,_=one(project,"risk_score_evaluation");data=rows(project/"binary.tsv");y=np.array([float(row["y"]) for row in data]);score=np.array([float(row["score"]) for row in data]);prediction=np.array([float(row["probability"]) for row in data]) if mode=="binary-probability" else score;positive=prediction[y==1];negative=prediction[y==0];auc=sum(1 if a>b else .5 if a==b else 0 for a in positive for b in negative)/len(positive)/len(negative);metrics={row["metric"]:float(row["estimate"]) for row in rows(folder/"risk-score-evaluation.tsv")};assert math.isclose(metrics["auc"],auc,abs_tol=TOL)
            if mode=="binary-raw":assert "brier" not in metrics and "rmse" not in metrics and "calibration_slope" not in metrics
            else:assert math.isclose(metrics["brier"],float(np.mean((y-prediction)**2)),abs_tol=TOL);assert math.isclose(metrics["log_loss"],float(-np.mean(y*np.log(prediction)+(1-y)*np.log1p(-prediction))),abs_tol=TOL)
        elif mode=="supplied-evaluation":
            source,doc=one(project,"risk_score_evaluation");assert doc["details"]["analysis_executed_by_jlinalg"] is False;metrics=rows(source/"risk-score-evaluation.tsv");assert [float(row["estimate"]) for row in metrics]==[.123,.8];folder,follow=one(project,"risk_score_related_phenotypes");assert not (folder/"risk-score-evaluation.tsv").exists() and not (folder/"score-model.yaml").exists();assert follow["details"]["model_refitted"] is False;record=rows(project/"participants.tsv");x,y,C=arrays(record);check_related(folder,record,C,x[:,0])
        elif mode=="limited-family":
            folder,_=one(project,"risk_score_evaluation");assert (folder/"risk-score-evaluation.tsv").exists() and not (folder/"related-phenotypes.tsv").exists();assert all(row["adjusted_p"]=="NA" for row in rows(folder/"related-phenotypes-limited.tsv"))
        else:
            folder,_=one(project,"risk_score_evaluation");assert not (folder/"score-model.yaml").exists()
        if mode=="native":assert all(doc.get("reused_from_cache") is True for _,doc in operations)
        cli(jar,"verify","--project",project)
    report={"jar_sha256":sha(jar),"verifier_sha256":software["verifier_sha256"],"cases":len(MODES),"numpy_version":np.__version__,"scipy_version":scipy.__version__,"ridge_baseline_tuning_and_imputation_independently_reconstructed":True,"cluster_CR1_t_tests_and_complete_BY_family_independently_reconstructed":True,"cluster_bootstrap_percentile_intervals_independently_reconstructed":True,"matrix_input_matches_direct_participant_input":True,"frozen_model_reuses_preprocessing_baseline_and_predictions_without_refit":True,"diploid_allele_swap_preserves_constant":True,"raw_binary_and_probability_metrics_separated_and_verified":True,"supplied_performance_retained_only_missing_related_tests_executed":True,"group_leakage_unestimable_family_and_unsupported_model_gates_verified":True,"input_hashes_unchanged":True,"native_resume_reuses_completed_operations":True,"project_local_resources":True,"external_requests":0,"R_or_external_risk_tool_parity_tested":False,"real_biological_AI_or_clinical_acceptance":False,"full_pipeline_conformance":False}
    target=root/"packaged-verification.json"
    if target.exists():assert json.loads(target.read_text())==report,"Historical acceptance cannot be reassigned to different software"
    else:write(target,report)
    print(json.dumps(report,indent=2))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False);jar=pin(root,jar)
        for mode in MODES:project=generate(root,mode);cli(jar,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
        cli(jar,"auto",root/"native/study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
