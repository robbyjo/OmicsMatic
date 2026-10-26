"""Independent offline multi-cohort acceptance: run NEW_DIR --jar JAR; verify DIR --jar PINNED_JAR.

Requires NumPy, SciPy and PyYAML. All inputs and identifiers are synthetic.
Reconstructs exact memberships, frozen validation BH, fixed/GLS pooling, Q/I2,
registered heterogeneity alerts and common-universe influence from raw source tables.
"""
import argparse
import csv
import hashlib
import itertools
import json
from pathlib import Path
import shutil
import subprocess
import sys
import scipy
import numpy as np
from scipy.stats import norm, chi2
import yaml

MODES=("four-way","three-way","two-way","union-coverage","reduced","common-influence","conditional-none","conditional-high","covariance","provided-overlap","provided-heterogeneity","partial-membership","namespace-mismatch")
WAITING={"partial-membership","namespace-mismatch"}
TOL=3e-8

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def table(path,columns,values):
    with path.open("w",encoding="utf-8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(columns);writer.writerows(values)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    assert result.returncode in (0,3),result.stdout+result.stderr
    return result.stdout+result.stderr
def artifacts(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"));folder=project/"runs"/status["run_id"]/"operations"
    return status,[(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in sorted(folder.iterdir())]
def one(project,kind,required=True):
    found=[(p,m) for p,m in artifacts(project)[1] if m["operation_type"]==kind];assert len(found)<=1,(project,kind)
    if required:assert len(found)==1,(project,kind)
    return found[0] if found else None
def pin(root,jar):
    folder=root/"software";folder.mkdir();target=folder/("jlinalg-"+sha(jar)+".jar");shutil.copyfile(jar,target);verifier=root/"workflow-verifier-source.py";shutil.copyfile(Path(__file__),verifier);write(root/"execution-software.json",{"jar":target.relative_to(root).as_posix(),"jar_sha256":sha(jar),"verifier_sha256":sha(verifier),"numpy_version":np.__version__,"scipy_version":scipy.__version__,"python_version":sys.version})
    return target
def adjust(p,method):
    if method=="NONE":return dict(p)
    ordered=sorted(p,key=lambda key:(p[key],key));out={};previous=1.;factor=sum(1/i for i in range(1,len(p)+1)) if method=="BY" else 1
    for i in range(len(ordered)-1,-1,-1):
        key=ordered[i];previous=min(previous,p[key]*len(p)*factor/(i+1));out[key]=min(1.,previous)
    return out
def close(actual,expected,label):
    assert actual not in ("NA","NaN",""),(label,actual,expected)
    assert abs(float(actual)-expected)<=TOL*max(1,abs(expected)),(label,actual,expected)

def generate(root,mode):
    project=root/mode;project.mkdir();k=3 if mode in ("three-way","reduced") else 2 if mode=="two-way" else 4;ids=list("ABCD"[:k]);cohorts=["cohort-"+x for x in ids]
    analysis={"mode":"manual","goals":["meta_analysis","discovery_replication"],"multiple_testing":{"method":"NONE","alpha":.05},"harmonization":{"effect_outliers":{"enabled":False}},"operations":{"meta_analysis":{"model":"fixed","min_cohorts":2,"inference":"normal","confidence_level":.95}},"heterogeneity":{"minimum_features":10},"cohort_overlap":{"plot_patterns":3}}
    if mode.startswith("conditional"):analysis["heterogeneity"]["leave_one_cohort_out"]="on_alert"
    if mode=="union-coverage":analysis["cohort_overlap"]["universe"]="union"
    data={}
    for j,id_value in enumerate(ids):
        values=[]
        for n in range(16):
            if mode=="union-coverage" and id_value==ids[-1] and n==15:continue
            hit=bool(n&(1<<(k-1-j)));p=.001 if hit else .7;beta=float(norm.isf(p/2)*.2)
            if j==k-1 and n==15:beta=-beta
            if mode=="conditional-none":beta=.2;p=float(2*norm.sf(1))
            values.append([f"f{n:02}",beta,.2,p,1000+100*j])
        if mode=="common-influence":
            values=[["low",.1,.2,float(2*norm.sf(.5)),1000+100*j]]
            if j<2:values.append(["high",1. if j==0 else -1.,.2,float(2*norm.sf(5)),1000+100*j])
            analysis["heterogeneity"]["minimum_features"]=1
        if mode=="reduced":
            probabilities=([.001,.001,.9,.9],[.03,.7,1e-12,1e-12],[.001,.01,.9,.9])[j];values=[[f"f{i+1:02}",float(norm.isf(p/2)*.2)*(-1 if j==2 and i==1 else 1),.2,p,1000+100*j] for i,p in enumerate(probabilities)]
            analysis["multiple_testing"]["method"]="BH";analysis["replication"]={"adjustment_scope":"discovery_subset","threshold_comparison":"less_than","require_same_sign":True};analysis["cohort_overlap"].update(validation_family="discovery_subset",direction="require_discovery")
        table(project/(id_value+".tsv"),["feature_id","beta","se","p_value","n"],values);data[id_value]=values
    relationships=[{"between":[a,b],"type":"independent"} for a,b in itertools.combinations(cohorts,2)]
    roles={"discovery":[ids[0]],"replication":ids[1:]}
    if mode=="covariance":
        analysis["goals"]=["meta_analysis"];roles={"discovery":ids};relationships=[{"between":[a,b],"type":"overlapping","description":"Synthetic source dependence represented by a registered sampling matrix"} for a,b in itertools.combinations(cohorts,2)]
        analysis["pooling"]={"effect_contract":{"estimand":"synthetic feature effect","orientation":"feature_to_phenotype","effect_scale":"linear","effect_units":"synthetic phenotype units per feature unit","phenotype_transformation":"none","feature_transformation":"none"},"dependence":{"method":"sampling_correlation","matrix":"sampling.tsv","rationale":"Synthetic known positive definite covariance; no estimate from overlap fractions","citation":"Synthetic independent NumPy GLS reference"}}
        table(project/"sampling.tsv",["feature_id","dataset_a","dataset_b","value"],[["*",a,b,.25**abs(i-j)] for i,a in enumerate(ids) for j,b in enumerate(ids)])
    datasets=[{"id":id_value,"cohort":cohort,"type":"generic","stage":"association_results","feature_id_type":"probe","species":"human","files":{"results":id_value+".tsv"}} for id_value,cohort in zip(ids,cohorts)]
    if mode=="namespace-mismatch":datasets[0]["feature_id_type"]="gene_symbol";datasets[1]["feature_id_type"]="ensembl_gene"
    if mode in ("provided-overlap","partial-membership"):
        supplied=[]
        for feature in range(16):
            for j,id_value in enumerate(ids):
                if mode=="partial-membership" and feature==15 and j==3:continue
                supplied.append([f"f{feature:02}",id_value,True,True,bool(feature&(1<<(k-1-j)))])
        table(project/"provided.tsv",["feature_id","set_id","in_universe","source_estimable","selected"],[[str(v).lower() if isinstance(v,bool) else v for v in row] for row in supplied]);analysis={"mode":"manual","goals":["cohort_overlap"],"supplied_results":[{"id":"provided","kind":"cohort_overlap","files":{"results":"provided.tsv"},"scope":{"datasets":ids,"feature_id_type":"probe"},"method":{"name":"Externally selected synthetic membership labels"}}]};datasets=[];roles={}
    if mode=="provided-heterogeneity":
        values=[[f"f{i:02}","i_squared",55 if i<8 else 10,"true","false"] for i in range(16)];table(project/"provided.tsv",["feature_id","metric","metric_value","in_alert_scope","high_heterogeneity"],values)
        rule={"metric":"i_squared","minimum_sources":2,"feature_scope":"all_estimable","minimum_features":10,"minimum_high_features":1,"widespread_fraction":.1,"fraction_comparison":"greater_than_or_equal","i_squared_threshold":60}
        analysis={"mode":"manual","goals":["heterogeneity_review"],"supplied_results":[{"id":"provided","kind":"heterogeneity_review","files":{"results":"provided.tsv"},"scope":{"datasets":ids},"method":{"name":"External synthetic I-squared assessment","parameters":{"alert_rule":rule}},"family":{"id":"heterogeneity-source","coverage":"complete","tested_count":16,"adjustment":"NONE"}}]};datasets=[];roles={}
    study={"schema_version":1,"project":{"id":"cohort-"+mode,"title":"Synthetic cohort diagnostics","description":"Independent software acceptance; no biological findings"},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"cohorts":[{"id":x} for x in cohorts],"cohort_relationships":relationships,"datasets":datasets,"hypotheses":[{"id":"H","description":"Synthetic prespecified cohort diagnostics","inputs":roles,"analysis":analysis}]}
    write(project/"study.yaml",study);write(project/"input-provenance.json",{p.name:sha(p) for p in project.glob("*.tsv")});return project

def raw(project):
    return {p.stem:{row["feature_id"]:row for row in rows(p)} for p in sorted(project.glob("[ABCD].tsv"))}
def fixed(data,feature,ids,covariance=False):
    active=[id_value for id_value in ids if feature in data[id_value]]
    if len(active)<2:return None
    beta=np.array([float(data[id_value][feature]["beta"]) for id_value in active]);se=np.array([float(data[id_value][feature]["se"]) for id_value in active]);indices=[list(data).index(id_value) for id_value in active];matrix=np.diag(se*se)
    if covariance:matrix=np.array([[se[i]*se[j]*(.25**abs(indices[i]-indices[j])) for j in range(len(active))] for i in range(len(active))])
    one=np.ones(len(active));solved=np.linalg.solve(matrix,one);variance=1/np.dot(one,solved);pooled=float(np.dot(solved,beta)*variance);stderr=float(np.sqrt(variance));residual=beta-pooled;q=float(residual@np.linalg.solve(matrix,residual));df=len(active)-1;p=float(2*norm.sf(abs(pooled/stderr)));i2=0. if q==0 else max(0,100*(q-df)/q)
    return {"beta":pooled,"se":stderr,"p_value":p,"ci_lower":pooled-norm.isf(.025)*stderr,"ci_upper":pooled+norm.isf(.025)*stderr,"q":q,"q_df":df,"q_p_value":float(chi2.sf(q,df)),"i_squared":i2,"n_sources":len(active)}
def verify_meta(path,data,ids,covariance):
    expected={f:fixed(data,f,ids,covariance) for f in set().union(*(set(data[id_value]) for id_value in ids))};expected={f:v for f,v in expected.items() if v is not None}
    seen={}
    for row in rows(path):
        if row.get("term","") not in ("","pooled") or row.get("status") not in ("ok","single_cohort"):continue
        feature=row["feature_id"];assert feature in expected;seen[feature]=row
        for key in ("beta","se","p_value","ci_lower","ci_upper","q","q_df","q_p_value"):close(row[key],expected[feature][key],(feature,key))
        if covariance:assert row["i_squared"]=="NA" and row["q_definition"]=="generalized_sampling_covariance"
        else:close(row["i_squared"],expected[feature]["i_squared"],(feature,"i_squared"))
    assert set(seen)==set(expected),(path,set(seen),set(expected))
    return expected

def verify_overlap(project,mode,folder):
    source=raw(project);ids=list(source);universe=set().union(*(set(t) for t in source.values())) if mode=="union-coverage" else set.intersection(*(set(t) for t in source.values()));method="BH" if mode=="reduced" else "NONE";q={id_value:adjust({f:float(row["p_value"]) for f,row in data.items()},method) for id_value,data in source.items()};frozen={f for f in universe if q[ids[0]][f]<.05}
    if mode=="reduced":
        for id_value in ids[1:]:q[id_value]=adjust({f:float(source[id_value][f]["p_value"]) for f in frozen},method)
    expected={};membership=rows(folder/"cohort-overlap-membership.tsv");assert len(membership)==len(set().union(*(set(t) for t in source.values())))*len(ids)
    for feature in universe:
        bits=""
        for id_value in ids:
            p=q[id_value].get(feature,float("nan"));hit=p<.05
            if mode=="reduced" and id_value!=ids[0]:hit&=float(source[ids[0]][feature]["beta"])*float(source[id_value][feature]["beta"])>0
            bits+="1" if hit else "0"
        expected[bits]=expected.get(bits,0)+1
    actual={row["selected_pattern"]:int(row["features"]) for row in rows(folder/"cohort-overlap-patterns.tsv")};assert actual==expected,(mode,actual,expected)
    degrees=rows(folder/"cohort-overlap-degree.tsv")
    for row in degrees:
        degree=int(row["selected_sets"]);assert int(row["exact_features"])==sum(size for bits,size in expected.items() if bits.count("1")==degree);assert int(row["at_least_features"])==sum(size for bits,size in expected.items() if bits.count("1")>=degree)
    for row in membership:
        id_value=row["set_id"];feature=row["feature_id"];expected_q=q[id_value].get(feature)
        if expected_q is None:assert row["adjusted_p"]=="NA"
        else:close(row["adjusted_p"],expected_q,(mode,id_value,feature,"adjusted_p"))
        if feature not in source[id_value]:assert row["source_estimable"]=="false" and row["status"]==("not_estimable_or_unobserved" if row["in_universe"]=="true" else "outside_overlap_universe")
    return {"patterns":len(expected),"universe":len(universe)}

def verify_case(project,mode):
    status,operations=artifacts(project);assert not any(m["state"]=="failed" for p,m in operations),(mode,operations)
    for name,expected in json.loads((project/"input-provenance.json").read_text()).items():assert sha(project/name)==expected
    for folder,manifest in operations:
        for file,digest in manifest["output_sha256"].items():assert sha(folder/file)==digest,(mode,file)
    if mode in ("provided-overlap","partial-membership"):
        folder,manifest=one(project,"cohort_overlap");assert manifest["state"]==("waiting_for_input" if mode in WAITING else "complete");assert manifest["details"]["analysis_executed_by_jlinalg"] is False;assert not one(project,"meta_analysis",False)
        if mode=="provided-overlap":assert sum(int(r["features"]) for r in rows(folder/"cohort-overlap-patterns.tsv"))==16;assert (folder/"cohort-overlap-upset.pdf").read_bytes().startswith(b"%PDF")
        else:assert not (folder/"cohort-overlap-upset.png").exists()
        return {"external_selection_reused":True,"state":manifest["state"]}
    if mode=="provided-heterogeneity":
        folder,manifest=one(project,"heterogeneity_review");summary=yaml.safe_load((folder/"heterogeneity-review.yaml").read_text());assert summary["source_alert_rule"]["i_squared_threshold"]==60;assert summary["high_features"]==0 and summary["widespread_alert"] is False;assert manifest["details"]["analysis_executed_by_jlinalg"] is False;return {"external_cutoff_preserved":60}
    overlap,manifest=one(project,"cohort_overlap")
    if mode=="namespace-mismatch":
        assert manifest["state"]=="waiting_for_input" and not (overlap/"cohort-overlap-membership.tsv").exists()
        for kind in ("meta_analysis","discovery_replication"):
            path,blocked=one(project,kind);assert blocked["state"]=="waiting_for_input";assert "incompatible_feature_id_type" in (path/"pooling-audit.yaml").read_text(encoding="utf-8")
        return {"known_namespace_mismatch_blocked_for_overlap_pooling_validation":True}
    overlap_result=verify_overlap(project,mode,overlap);assert manifest["state"]=="complete"
    source=raw(project);ids=list(source);covariance=mode=="covariance";meta,meta_manifest=one(project,"meta_analysis");assert meta_manifest["state"]=="complete";expected=verify_meta(meta/"meta-analysis.tsv",source,ids,covariance)
    heterogeneous,het_manifest=one(project,"heterogeneity_review");summary=yaml.safe_load((heterogeneous/"heterogeneity-review.yaml").read_text());metric="q_p_value" if covariance else "i_squared";assert summary["metric"]==metric
    metric_values=adjust({f:v["q_p_value"] for f,v in expected.items()},"BH") if covariance else {f:v["i_squared"] for f,v in expected.items()};high={f for f,v in metric_values.items() if v<.05} if covariance else {f for f,v in metric_values.items() if v>=50};assert summary["high_features"]==len(high);close(summary["high_fraction"],len(high)/len(expected),(mode,"high_fraction"))
    sensitivity=one(project,"sensitivity_analysis",False)
    if mode=="conditional-none":assert sensitivity and sensitivity[1]["details"]["omission_not_triggered"] is True;assert not (sensitivity[0]/"leave_one_cohort_out").exists()
    if sensitivity and (sensitivity[0]/"paired-meta-comparison.tsv").exists():
        folder,sens_manifest=sensitivity;decision=yaml.safe_load((folder/"decision.yaml").read_text());fits=[]
        for attempt in decision["analyses"]:
            if attempt.get("action")!="leave_one_cohort_out" or attempt["status"]!="complete":continue
            active=attempt["included_datasets"];fits.append((attempt,verify_meta(folder/attempt["result"],source,active,covariance)))
        common=set(expected)
        for attempt,fit in fits:common&=set(fit)
        for attempt,fit in fits:
            paired=attempt["paired_meta_comparison"];assert paired["common_features"]==len(common)
            if not covariance:
                close(paired["primary_mean_metric"],sum(expected[f]["i_squared"] for f in common)/len(common),(mode,"common primary mean"));close(paired["sensitivity_mean_metric"],sum(fit[f]["i_squared"] for f in common)/len(common),(mode,"common sensitivity mean"));close(attempt["mean_i_squared_delta"],sum(fit[f]["i_squared"]-expected[f]["i_squared"] for f in common)/len(common),(mode,"recorded paired I-squared delta"))
            assert paired["primary_unchanged"] is True
        assert decision["primary_artifacts"]["meta_analysis"]["sha256"]==sha(meta/"meta-analysis.tsv")
        if mode=="common-influence":assert common=={"low"} and not decision["odd_one_candidates"]
    return dict(overlap_result,independent_meta_features=len(expected),covariance=covariance)

def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text());assert sha(jar)==software["jar_sha256"]==sha(root/software["jar"]);assert sha(root/"workflow-verifier-source.py")==software["verifier_sha256"];assert sha(Path(__file__))==software["verifier_sha256"],"Invoke the exact pinned verifier source; do not relabel an old bundle using revised verification code"
    results=[{"case":mode,"evidence":verify_case(root/mode,mode)} for mode in MODES];write(root/"verification.json",{"passed":True,"jar_sha256":sha(jar),"verifier_sha256":software["verifier_sha256"],"cases":results,"scope":"Synthetic offline independent NumPy/SciPy arithmetic and source/primary preservation. No biological cohort exclusion, real AI appraisal or change to a prespecified primary result."});return results
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir();jar=pin(root,jar)
        for mode in MODES:
            project=generate(root,mode);(project/"execution.log").write_text(cli(jar,"auto",project/"study.yaml","--offline","--actor-type","external","--actor-id","synthetic-cohort-fixture"),encoding="utf-8")
            if mode not in WAITING:
                assert artifacts(project)[0]["state"]=="completed",[(m["operation_type"],m["state"],m.get("summary")) for p,m in artifacts(project)[1]]
                (project/"replay.log").write_text(cli(jar,"auto",project/"study.yaml","--offline","--actor-type","external","--actor-id","synthetic-cohort-fixture"),encoding="utf-8");assert all(m["reused_from_cache"] is True for p,m in artifacts(project)[1])
            (project/"verify.log").write_text(cli(jar,"verify","--project",project),encoding="utf-8")
    results=verify(root,jar);print(json.dumps({"passed":True,"cases":len(results),"directory":str(root),"jar_sha256":sha(jar)}))
if __name__=="__main__":main()
