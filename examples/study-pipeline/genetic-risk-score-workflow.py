"""Offline complete-model BED score acceptance with independent NumPy/SciPy checks.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
All participants, coefficients, alleles, coordinates and supplied results are synthetic.
Requires NumPy, SciPy and PyYAML; never downloads models or participant data.
"""
import argparse
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import numpy as np
from scipy.special import expit
from scipy.stats import rankdata, t
import yaml

CASES=("native", "large-model", "imputation", "participant-qc", "binary-raw", "binary-probability", "ratio-mapped", "missing-model", "ambiguous-locus", "source-loss", "shared-budget", "repeat-identity", "nonadditive", "supplied-evaluation", "all-supplied")
HOLDS={"missing-model", "ambiguous-locus", "source-loss", "shared-budget", "repeat-identity", "nonadditive"}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_text(json.dumps(v,indent=2)+"\n",encoding="utf-8")
def table(p,header,values):
    with p.open("w",encoding="utf-8",newline="") as f:
        w=csv.writer(f,delimiter="\t",lineterminator="\n");w.writerow(header);w.writerows(values)
def rows(p):
    with p.open(encoding="utf-8",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
def cli(jar,*args):
    r=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],capture_output=True,text=True)
    assert r.returncode==0,r.stdout+r.stderr
    return r.stdout+r.stderr
def operations(p):
    state=yaml.safe_load((p/"state/project-status.yaml").read_text(encoding="utf-8"))
    folder=p/"runs"/state["run_id"]/"operations"
    return state,[(f,yaml.safe_load((f/"manifest.yaml").read_text(encoding="utf-8"))) for f in sorted(folder.iterdir())]
def one(p,kind):
    found=[v for v in operations(p)[1] if v[1]["operation_type"]==kind];assert len(found)==1,(p,kind)
    return found[0]
def bed(p,name,dose):
    n,m=dose.shape
    # Independent BED writer: 00=A1/A1, 10=heterozygous, 11=A2/A2, 01=missing.
    data=bytearray([0x6c,0x1b,1]);bim=[]
    for j in range(m):
        ea,oa=("A","C") if j%2==0 else ("T","G");a1,a2=(oa,ea) if j%3==0 else (ea,oa)
        bim.append([1+j%2,f"{name}-source-alias-{j}",0,100+j,a1,a2])
        d=2-dose[:,j] if a1!=ea else dose[:,j]
        block=bytearray((n+3)//4)
        for i,x in enumerate(d):block[i//4]|=(1 if np.isnan(x) else 0 if x==2 else 2 if x==1 else 3)<<(2*(i%4))
        data.extend(block)
    (p/f"{name}.bed").write_bytes(data)
    (p/f"{name}.bim").write_text("".join(" ".join(map(str,r))+"\n" for r in bim),encoding="utf-8")
    (p/f"{name}.fam").write_text("".join(f"F{i//4} I{i%4} 0 0 0 -9\n" for i in range(n)),encoding="utf-8")

def generate(root,case):
    p=root/case;p.mkdir();n=48;m=2101 if case=="large-model" else 24
    rng=np.random.default_rng(8127);beta=rng.normal(0,.06,m);beta[-1]=.8;frequency=np.linspace(.1,.7,m)
    dose=rng.binomial(2,.3,(2,n,m)).astype(float);dose[:,:,-1]=0;dose[0,0,-1]=1 # MAC=1, nonzero model member.
    if case in {"imputation","participant-qc","source-loss"}:
        dose[0,0,:3]=np.nan
        if case=="source-loss":dose[1,:,:]=np.nan
    effective=np.where(np.isnan(dose),2*frequency,dose);score=(effective@beta-1)/2
    age=np.array([[(i*7)%23+20 for i in range(n)] for _ in range(2)]);linear=.01*age
    prediction=1+score+linear;baseline=1+linear;y=prediction+.15*np.sin(np.arange(2*n).reshape(2,n)*.7)
    if case.startswith("binary"):
        y=((np.arange(2*n).reshape(2,n)*7)%11<5).astype(float);baseline=np.full_like(y,.45);prediction=expit(-.2+score+linear)
    related=.6*score+.25*np.cos(np.arange(2*n).reshape(2,n)*.9)
    records=[]
    for k,name in enumerate(("A","B")):
        bed(p,name,dose[k])
        for i in range(n):records.append([f"private-{name}-{i}","evaluation",f"F{i//4}",f"I{i%4}",name,float(y[k,i]),float(related[k,i]),float(baseline[k,i]),float(linear[k,i]),float(age[k,i])])
    table(p/"participants.tsv",["sample_id","partition","fid","iid","cohort","y","z","baseline","linear","age"],records)
    header=["variant","chromosome","position","ea","oa","weight","ea_frequency","genome_build"]
    weights=[[f"independent-model-label-{j}",1+j%2,100+j,*(('A','C') if j%2==0 else ('T','G')),float(beta[j]),float(frequency[j]),"GRCh38"] for j in range(m)]
    table(p/"weights.tsv",header,weights)
    g={"sources":[{"id":name,"bfile":name,"genome_build":"GRCh38"} for name in ("A","B")],"weights_genome_build":"GRCh38","source_column":"cohort","prediction_link":"identity","intercept":1,"prediction_linear_column":"linear","prediction_rationale":"Frozen synthetic source intercept and covariate linear component","score_center":1,"score_scale":2}
    policy={"mode":"weights","file":"participants.tsv","weights":"weights.tsv","outcome":"y","baseline_column":"baseline","independent_rows":True,"external_independence":"Synthetic frozen weights; discovery and target participants independent by construction; no real biological claim","bootstraps":0,"covariates":["age"],"related_phenotypes":[{"column":"z"}],"genotypes":g}
    if case in {"imputation","participant-qc","source-loss"}:g.update(missing="reference_mean",frequency_provenance="Independent synthetic EA frequencies fixed before target generation",minimum_observed_fraction=.8 if case=="imputation" else .98)
    if case.startswith("binary"):
        policy.update(family="binary",related_phenotypes=[])
        if case=="binary-raw":g["prediction_link"]="none";[g.pop(key) for key in ("intercept","prediction_linear_column","prediction_rationale")]
        else:policy["prediction_scale"]="probability";g.update(prediction_link="logit",intercept=-.2)
    if case=="ratio-mapped":
        mapped=["rsID","hm_chr","hm_pos","effect_allele","other_allele","OR","frequency","build"]
        g["weight_columns"]=dict(zip(header,mapped));g["coefficient_scale"]="odds_ratio";policy["weights"]="pgs.txt.gz"
        with gzip.open(p/"pgs.txt.gz","wt",encoding="utf-8",newline="") as f:
            f.write("### Synthetic PGS-style file; no real score accession\n#genome_build=GRCh38\n");w=csv.writer(f,delimiter="\t",lineterminator="\n");w.writerow(mapped)
            for r in weights:r=list(r);r[5]=math.exp(r[5]);w.writerow(r)
    if case=="missing-model":(p/"B.bim").write_text((p/"B.bim").read_text().replace(" 100 "," 999999 "),encoding="utf-8")
    if case=="ambiguous-locus":
        with (p/"B.bim").open("a",encoding="utf-8") as f:f.write("1 duplicate 0 100 A C\n")
    if case=="shared-budget":g["maximum_cells"]=n*m+1
    if case=="repeat-identity":records.append(["repeated-private-A-0",*records[0][1:]]);table(p/"participants.tsv",["sample_id","partition","fid","iid","cohort","y","z","baseline","linear","age"],records)
    if case=="nonadditive":table(p/"weights.tsv",header+["is_dominant"],[r+["true" if i==0 else "false"] for i,r in enumerate(weights)])
    supplied=[];goals=["risk_score_evaluation"]
    if case in {"supplied-evaluation","all-supplied"}:
        source,_=one(root/"native","risk_score_evaluation");shutil.copyfile(source/"risk-score-evaluation.tsv",p/"provided-evaluation.tsv")
        supplied.append({"id":"existing-performance","kind":"risk_score_evaluation","files":{"results":"provided-evaluation.tsv"},"method":{"name":"Already completed independent synthetic held-out evaluation"},"scope":{"population":"registered_evaluation_complete_cases"}});goals.append("risk_score_related_phenotypes")
        if case=="supplied-evaluation":
            policy.pop("genotypes");policy.update(mode="scores",score_column="frozen_score");table(p/"participants.tsv",["sample_id","partition","fid","iid","cohort","y","z","baseline","linear","age","frozen_score"],[r+[float(score.flat[i])] for i,r in enumerate(records)])
        else:
            shutil.copyfile(source/"related-phenotypes.tsv",p/"provided-related.tsv");supplied.append({"id":"existing-related","kind":"risk_score_related_phenotypes","files":{"results":"provided-related.tsv"},"method":{"name":"Already completed complete synthetic related family"},"scope":{"population":"registered_evaluation_complete_cases"},"family":{"id":"related","coverage":"complete","tested_count":1,"adjustment":"BH"}})
            # Missing genotype data must not be opened when both stages are supplied.
            for name in ("A","B"):(p/f"{name}.bed").unlink()
    study={"schema_version":1,"project":{"id":"frozen-genetic-score-"+case,"title":"Synthetic frozen genetic score","description":"Independent complete-model application and supplied-stage reuse acceptance"},"resources":{"directory":"resources","network":"offline","downloads":"deny"},"datasets":[],"hypotheses":[{"id":"H","description":"Synthetic held-out scoring","analysis":{"mode":"manual","goals":goals,"risk_score":policy,"supplied_results":supplied}}]}
    write(p/"study.json",study);np.savez(p/"oracle.npz",dose=dose,beta=beta,frequency=frequency,score=score,prediction=prediction,baseline=baseline,y=y,related=related,age=age)
    write(p/"input-sha256.json",{f.name:sha(f) for f in p.iterdir() if f.is_file()})
    return p

def verify_case(p,case,jar):
    for name,digest in json.loads((p/"input-sha256.json").read_text()).items():assert sha(p/name)==digest,(case,name)
    cli(jar,"verify","--project",p)
    state,ops=operations(p);assert state["state"]==("waiting_for_input" if case in HOLDS else "completed"),(case,state)
    if case in HOLDS:
        folder,manifest=one(p,"risk_score_evaluation");assert manifest["state"]=="waiting_for_input";assert not (folder/"risk-score-evaluation.tsv").exists();assert not (folder/"participant-scores.tsv").exists();assert (folder/"genetic-score-local-diagnostic.txt").is_file();return 0
    folder,manifest=one(p,"risk_score_evaluation")
    if case=="all-supplied":
        assert manifest["details"]["analysis_executed_by_jlinalg"] is False;assert not (folder/"genetic-score-application.yaml").exists();return 0
    related_folder=one(p,"risk_score_related_phenotypes")[0] if case=="supplied-evaluation" else folder
    oracle=np.load(p/"oracle.npz");count=0
    valid=np.ones(oracle["y"].size,dtype=bool)
    if case=="participant-qc":valid[0]=False
    if case!="supplied-evaluation":
        actual=rows(folder/"participant-scores.tsv");assert len(actual)==oracle["y"].size
        for i,r in enumerate(actual):
            assert r["sample_id"]==f"private-{'A' if i<48 else 'B'}-{i%48}"
            for col,key in (("score","score"),("prediction","prediction"),("baseline","baseline")):
                if not valid[i] or case=="binary-raw" and col=="prediction":assert r[col]=="NA";continue
                np.testing.assert_allclose(float(r[col]),oracle[key].flat[i],rtol=2e-11,atol=2e-12);count+=1
        report=yaml.safe_load((folder/"genetic-score-application.yaml").read_text(encoding="utf-8"));assert report["model_variants"]==oracle["beta"].size;assert report["model_coverage"]=="complete_in_every_registered_source";assert report["decoded_genotype_cells"]==oracle["dose"].size;assert manifest["details"]["model_refitted"] is False
        assert len(rows(folder/"genetic-variant-alignment.tsv"))==2*oracle["beta"].size
        y=oracle["y"].ravel()[valid];score=oracle["score"].ravel()[valid];pred=oracle["prediction"].ravel()[valid];base=oracle["baseline"].ravel()[valid]
        metrics={r["metric"]:float(r["estimate"]) for r in rows(folder/"risk-score-evaluation.tsv") if r["subgroup_column"]=="all"}
        if case.startswith("binary"):
            def auc(x):return (rankdata(x)[y==1].sum()-sum(y)*(sum(y)+1)/2)/(sum(y)*sum(y==0))
            expected={"auc":auc(score if case=="binary-raw" else pred)}
            if case=="binary-probability":expected.update(score_auc=auc(score),baseline_auc=auc(base),brier=np.mean((y-pred)**2),baseline_brier=np.mean((y-base)**2),log_loss=np.mean(-y*np.log(pred)-(1-y)*np.log1p(-pred)),baseline_log_loss=np.mean(-y*np.log(base)-(1-y)*np.log1p(-base)))
        else:expected={"rmse":np.sqrt(np.mean((y-pred)**2)),"baseline_rmse":np.sqrt(np.mean((y-base)**2)),"incremental_predictive_r2":1-np.sum((y-pred)**2)/np.sum((y-base)**2)}
        for k,v in expected.items():np.testing.assert_allclose(metrics[k],v,rtol=3e-10,atol=2e-12);count+=1
    if not case.startswith("binary"):
        score=oracle["score"].ravel()[valid];age=oracle["age"].ravel()[valid];z=oracle["related"].ravel()[valid];x=np.column_stack([np.ones(len(z)),age,score]);coef=np.linalg.lstsq(x,z,rcond=None)[0];res=z-x@coef;se=np.sqrt(np.linalg.inv(x.T@x)[-1,-1]*(res@res)/(len(z)-3));prob=2*t.sf(abs(coef[-1]/se),len(z)-3)
        r=rows(related_folder/"related-phenotypes.tsv")[0]
        for k,v in (("beta",coef[-1]),("se",se),("p_value",prob),("adjusted_p",prob)):np.testing.assert_allclose(float(r[k]),v,rtol=2e-8,atol=2e-12);count+=1
        if case=="supplied-evaluation":assert not (related_folder/"risk-score-evaluation.tsv").exists();assert not (related_folder/"genetic-score-application.yaml").exists()
    return count

def main():
    a=argparse.ArgumentParser();a.add_argument("action",choices=["run","verify"]);a.add_argument("directory",type=Path);a.add_argument("--jar",type=Path,required=True);args=a.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir();(root/"software").mkdir();pinned=root/"software"/f"jlinalg-{sha(jar)}.jar";shutil.copyfile(jar,pinned);shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py");write(root/"execution-software.json",{"jar_sha256":sha(jar),"jar":str(pinned.relative_to(root)),"verifier_sha256":sha(root/"workflow-verifier-source.py")});jar=pinned
        for case in CASES:
            p=generate(root,case);log=cli(jar,"auto",p/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture");(p/"execution.log").write_text(log,encoding="utf-8");print(case,operations(p)[0]["state"],flush=True)
        p=root/"native";folder,_=one(p,"risk_score_evaluation");previous={f.name:sha(f) for f in folder.iterdir() if f.is_file()};cli(jar,"auto",p/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture");assert all(m["reused_from_cache"] for _,m in operations(p)[1]);assert previous=={f.name:sha(f) for f in folder.iterdir() if f.is_file()}
        original=(p/"A.bed").read_bytes();(root/"cache-original-A.bed").write_bytes(original);oracle=np.load(p/"oracle.npz");old=oracle["dose"][0,0,0];new=(old+1)%3;source_a1=2-new;code=0 if source_a1==2 else 2 if source_a1==1 else 3;changed=bytearray(original);changed[3]=(changed[3]&~3)|code;(p/"A.bed").write_bytes(changed);(root/"cache-mutated-A.bed").write_bytes(changed)
        cli(jar,"auto",p/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture");mutation,manifest=one(p,"risk_score_evaluation");assert manifest.get("reused_from_cache",False) is False;actual=float(rows(mutation/"participant-scores.tsv")[0]["score"]);expected=oracle["score"][0,0]+(new-old)*oracle["beta"][0]/2;np.testing.assert_allclose(actual,expected,rtol=2e-11,atol=2e-12);assert previous=={f.name:sha(f) for f in folder.iterdir() if f.is_file()}
        (p/"A.bed").write_bytes(original);cli(jar,"auto",p/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture");assert all(m["reused_from_cache"] for _,m in operations(p)[1]);write(root/"cache-replay.json",{"all_operations_reused":True,"prior_artifact_files_unchanged":len(previous),"genotype_mutation_recomputed":True,"mutation_score_expected":float(expected),"mutation_score_observed":actual,"mutation_operation":str(mutation.relative_to(root)),"original_bed_sha256":sha(root/"cache-original-A.bed"),"mutated_bed_sha256":sha(root/"cache-mutated-A.bed")})
    receipt=json.loads((root/"execution-software.json").read_text());assert sha(jar)==receipt["jar_sha256"];assert sha(root/"workflow-verifier-source.py")==receipt["verifier_sha256"]==sha(Path(__file__))
    checks=sum(verify_case(root/case,case,jar) for case in CASES);write(root/"independent-verification.json",{"status":"passed","cases":len(CASES),"completed":len(CASES)-len(HOLDS),"deliberate_holds":len(HOLDS),"numerical_comparisons":checks,"jar_sha256":sha(jar),"oracle":"Independent synthetic effect-dosage generation, NumPy dot sums/fixed normalization/logistic links, rank AUC, direct MSE/Brier/log-loss, OLS t-test related family"});print(f"Verified {len(CASES)} cases and {checks} independent numerical comparisons")
if __name__=="__main__":main()
