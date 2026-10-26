"""Offline native/shared-model-null GSEA acceptance using independent NumPy fits.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR.
Requires numpy and PyYAML. All participants, measurements and sets are synthetic.
No biological calibration, Broad pooled-NES FDR or production execution claimed.
"""
import argparse
import csv
import hashlib
import itertools
import json
import math
from pathlib import Path
import shutil
import subprocess
import numpy as np
import yaml

NATIVE=("native-ols","native-stratified","native-enumerated")
IMPORTED=("reused-ols","reused-enumerated")
HOLDS=("missing-exchangeability","work-limit","partial-null")
MODES=NATIVE+IMPORTED+HOLDS
EPS=1e-12
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_text(json.dumps(v,indent=2)+"\n",encoding="utf-8")
def rows(p):
    with p.open(encoding="utf-8",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
def cli(jar,*args):
    r=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    assert r.returncode==0,r.stdout+r.stderr
def records(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p.parent,yaml.safe_load(p.read_text(encoding="utf-8"))) for p in (project/"runs"/status["run_id"]/"operations").glob("*/manifest.yaml")]
def one(project,kind):
    found=[r for r in records(project) if r[1]["operation_type"]==kind];assert len(found)==1,(project,kind,len(found));return found[0]
def base(project,mode,gsea,datasets,extra=None):
    study={"schema_version":1,"project":{"id":"phenotype-gsea-"+mode,"title":"Synthetic phenotype-null GSEA","description":"Independent fit and complete-null reuse acceptance."},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":datasets,
           "hypotheses":[{"id":"H","description":"Complete the registered GSEA and pathway graph.","analysis":{"mode":"manual","goals":["ranked_gsea","pathway_network","network_modules"],"gsea":gsea,"network":{"term_alpha":1,"pathway_threshold":.2}}}]}
    if datasets:study["cohorts"]=[{"id":"A"}]
    if extra:study["hypotheses"][0].update(extra)
    write(project/"study.json",study)
    return study
def generate_native(project,mode):
    project.mkdir();rng=np.random.default_rng(719)
    n=8 if mode=="native-enumerated" else 24
    age=np.array([20+i+(i%3)*.7 for i in range(n)])
    labels=["S"+str(i//2) for i in range(n)] if mode=="native-enumerated" else ["F" if i%2==0 else "M" for i in range(n)]
    y=rng.normal(size=n)+age*.03
    x=rng.normal(size=(8,n));x[1]=x[0]*.7+x[1]*.3;x[2]=x[0]*.3+x[2]*.7;x[4,3]=np.nan
    names=["f"+str(i) for i in range(8)]+["constant"]
    genes=["A","A","B","C","D","E","F","G"]
    with (project/"matrix.tsv").open("w",encoding="utf-8") as f:
        f.write("feature_id\t"+"\t".join("s"+str(i) for i in range(n))+"\n")
        for name,values in zip(names,list(x)+[np.ones(n)]):f.write(name+"\t"+"\t".join("NA" if np.isnan(v) else repr(float(v)) for v in values)+"\n")
    (project/"pheno.tsv").write_text("id\ttrait\tage\tstratum\n"+"".join(f"s{i}\t{float(y[i])!r}\t{float(age[i])!r}\t{labels[i]}\n" for i in range(n)),encoding="utf-8")
    (project/"annotation.tsv").write_text("feature_id\tgene_symbol\n"+"".join(f"{f}\t{g}\n" for f,g in zip(names,genes+["Z"])),encoding="utf-8")
    (project/"sets.gmt").write_text("first\tFirst synthetic set\tA\tB\tC\nsecond\tSecond synthetic set\tD\tE\tF\noverlap\tOverlap synthetic set\tA\tB\tD\tE\n",encoding="utf-8")
    phenotype={"independent_samples":True,"exchangeable_errors":True,"rationale":"Synthetic iid residuals; nuisance calibration is approximate."}
    if mode in ("native-stratified","native-enumerated"):phenotype["strata_column"]="stratum"
    if n==8:phenotype["minimum_samples"]=4
    if mode=="missing-exchangeability":phenotype["exchangeable_errors"]=False
    gsea={"null_model":"phenotype","phenotype":phenotype,"gene_sets":"sets.gmt","gene_id_type":"symbol","collapse":"median" if mode=="native-stratified" else "mean","minimum_size":2,"maximum_size":4,"permutations":127,"maximum_exact_states":100 if mode=="native-enumerated" else 0,"seed":31,"curve_plots":2,"adjustment":"BY"}
    if mode=="work-limit":gsea["maximum_work"]=100
    dataset={"id":"d","type":"expression","cohort":"A","stage":"processed_matrix","species":"human","feature_id_type":"probe","files":{"matrix":"matrix.tsv","phenotype":"pheno.tsv","annotation":"annotation.tsv"}}
    covariates=["stratum"] if n==8 else ["age","stratum"]
    study=base(project,mode,gsea,[dataset],{"outcome":"trait","covariates":covariates})
    study["hypotheses"][0]["analysis"]["association"]={"model":"ols","family":"gaussian"};write(project/"study.json",study)
def generate_import(project,mode,source):
    project.mkdir();directory,manifest=one(source,"ranked_gsea")
    for a,b in [("native-observed-ranking.tsv","observed.tsv"),("null-rankings.tsv","null.tsv"),("gene-sets.gmt","sets.gmt")]:shutil.copyfile(directory/a,project/b)
    details=manifest["details"];sampling="exhaustive" if "exhaustive" in details["null_sampling"] else "monte_carlo"
    if mode=="partial-null":
        lines=(project/"null.tsv").read_text(encoding="utf-8").splitlines();(project/"null.tsv").write_text("\n".join(lines[:-1])+"\n",encoding="utf-8")
    gsea={"null_model":"phenotype","ranking":{"file":"observed.tsv","coverage":"complete","tested_genes":7,"provenance":{"producer":"retained synthetic native model-null fixture"}},"gene_sets":"sets.gmt","species":"human","minimum_size":2,"maximum_size":4,"adjustment":"BY","curve_plots":2,
        "null_rankings":{"file":"null.tsv","draws":details["null_draws"],"coverage":"complete","sampling":sampling,"observed_sha256":sha(project/"observed.tsv"),"method":"Shared Freedman-Lane residual ranks from retained source","model":details["native_model_formula"]+"; independent identity Gaussian OLS","exchangeability":"Fixed nuisance, source within-stratum residual protocol; approximate general nuisance calibration","provenance":{"source_operation":manifest["operation_id"],"source_observed_sha256":sha(directory/"native-observed-ranking.tsv"),"source_null_sha256":sha(directory/"null-rankings.tsv")}}}
    base(project,mode,gsea,[])
class JavaRandom:
    def __init__(self,seed):self.seed=(seed^0x5DEECE66D)&((1<<48)-1)
    def bits(self,k):self.seed=(self.seed*0x5DEECE66D+11)&((1<<48)-1);return self.seed>>(48-k)
    def integer(self,bound):
        if bound&(bound-1)==0:return (bound*self.bits(31))>>31
        while True:
            bits=self.bits(31);value=bits%bound
            if bits-value+bound-1<(1<<31):return value
def refit_statistics(y,z,x):
    result=[]
    for values in x:
        design=np.column_stack((z,values));coefficient=np.linalg.lstsq(design,y,rcond=None)[0];residual=y-design@coefficient;variance=float(residual@residual)/(len(y)-design.shape[1]);se=math.sqrt(variance*np.linalg.inv(design.T@design)[-1,-1]);result.append(coefficient[-1]/se)
    return result
def verify_native(project,directory,details):
    study=json.loads((project/"study.json").read_text());h=study["hypotheses"][0];p=h["analysis"]["gsea"];ids=[r["sample_id"] for r in rows(directory/"analysis-samples.tsv")];pheno={r["id"]:r for r in rows(project/"pheno.tsv")};n=len(ids)
    y=np.array([float(pheno[i]["trait"]) for i in ids]);z=[np.ones(n)]
    if "age" in h["covariates"]:z.append(np.array([float(pheno[i]["age"]) for i in ids]))
    labels=[pheno[i]["stratum"] for i in ids];levels=list(dict.fromkeys(labels))
    z.extend(np.array([int(label==level) for label in labels]) for level in levels[1:]);z=np.array(z).T
    raw=rows(project/"matrix.tsv");x=[]
    for row in raw[:-1]:
        values=np.array([float(row[i]) if row[i]!="NA" else np.nan for i in ids]);values[np.isnan(values)]=np.nanmean(values);x.append(values)
    x=np.array(x);mapping={g:[i for i,value in enumerate(["A","A","B","C","D","E","F","G"]) if value==g] for g in "ABCDEFG"}
    def collapse(statistics):return {g:float(np.median([statistics[i] for i in at]) if p["collapse"]=="median" else np.mean([statistics[i] for i in at])) for g,at in mapping.items()}
    expected=collapse(refit_statistics(y,z,x));observed={r["gene_id"]:float(r["statistic"]) for r in rows(directory/"native-observed-ranking.tsv")}
    for g,v in expected.items():assert math.isclose(v,observed[g],abs_tol=2e-9,rel_tol=2e-9),(g,v,observed[g])
    reduced=np.linalg.lstsq(z,y,rcond=None)[0];mu=z@reduced;residual=y-mu;groups=[]
    if "strata_column" in p["phenotype"]:
        groups=[[i for i,label in enumerate(labels) if label==level] for level in levels]
    else:groups=[list(range(n))]
    draws={}
    for row in rows(directory/"null-rankings.tsv"):draws.setdefault(int(row["draw_id"]),{})[row["gene_id"]]=float(row["statistic"])
    rng=JavaRandom(p["seed"]);exhaustive="exhaustive" in details["null_sampling"]
    if exhaustive:products=itertools.product(*[list(itertools.permutations(g)) for g in reversed(groups)])
    for index,actual in draws.items():
        order=list(range(n))
        if exhaustive:
            product=list(reversed(next(products)))
            for group,alternative in zip(groups,product):
                for i,value in zip(group,alternative):order[i]=value
        else:
            for group in groups:
                for j in range(len(group)-1,0,-1):
                    k=rng.integer(j+1);order[group[j]],order[group[k]]=order[group[k]],order[group[j]]
        expected=collapse(refit_statistics(mu+residual[order],z,x))
        for g,value in expected.items():assert math.isclose(value,actual[g],abs_tol=2e-8,rel_tol=2e-8),(project,index,g,value,actual[g])
    dispositions={r["feature_id"]:r["disposition"] for r in rows(directory/"phenotype-feature-qc.tsv")};assert dispositions["constant"]=="constant_or_nuisance_explained"
    assert details["source_FDR_filter_applied"] is False and details["analysis_samples"]==n
    return len(draws)*len(mapping)+len(mapping)
def score(ranking,members):
    ordered=sorted(ranking,key=lambda g:(-ranking[g],g));mass=sum(abs(ranking[g]) for g in members);running=0.;values=[]
    for g in ordered:running+=abs(ranking[g])/mass if g in members else -1/(len(ordered)-len(members));values.append(running)
    high=max([0.]+values);low=min([0.]+values);es=high if high>=-low-EPS else low;at=values.index(high if es>=0 else low)
    leading=[g for i,g in enumerate(ordered) if g in members and (i<=at if es>=0 else i>at)]
    return es,leading
def verify_enrichment(project,directory,details):
    observed={r["gene_id"]:float(r["statistic"]) for r in rows(directory/"ranked-genes.tsv")};draws={}
    source=directory/("null-rankings.tsv" if details["null_rankings_source"]=="native_refit" else "supplied-null-rankings.tsv")
    for row in rows(source):draws.setdefault(row["draw_id"],{})[row["gene_id"]]=float(row["statistic"])
    sets={};probabilities={};table=rows(directory/"ranked-gsea.tsv")
    for line in (directory/"tested-gene-sets.gmt").read_text(encoding="utf-8").splitlines():
        f=line.split("\t");sets[f[0]]=set(f[2:])
    exhaustive="exhaustive" in details["null_sampling"]
    for row in table:
        members=sets[row["term_id"]];es,leading=score(observed,members);null=[score(r,members)[0] for r in draws.values()];same=[v for v in null if (v>=0 if es>=0 else v<0)];tail=sum(v>=es-EPS if es>=0 else v<=es+EPS for v in same)
        p=tail/len(same) if exhaustive else (tail+1)/(len(same)+1);nes=es/(sum(map(abs,same))/len(same));probabilities[row["term_id"]]=p
        for key,value in [("enrichment_score",es),("normalized_enrichment_score",nes),("p_value",p),("same_sign_states",len(same)),("tail_states",tail),("null_states",len(draws))]:assert math.isclose(float(row[key]),value,abs_tol=1e-10,rel_tol=1e-9),(project,key,value,row[key])
        assert row["leading_edge_genes"].split(";")==leading
    m=len(table);ordered=sorted(probabilities,key=probabilities.get);running=1.;q={};factor=sum(1/i for i in range(1,m+1))
    for i in range(m-1,-1,-1):g=ordered[i];running=min(running,probabilities[g]*m*factor/(i+1));q[g]=running
    for row in table:assert math.isclose(float(row["adjusted_p"]),q[row["term_id"]],abs_tol=1e-10)
    return len(table)
def verify(root,jar):
    pins=json.loads((root/"execution-software.json").read_text());assert sha(jar)==pins["jar_sha256"] and sha(Path(__file__))==pins["verifier_sha256"]
    numerical=terms=0
    for mode in MODES:
        project=root/mode;directory,manifest=one(project,"ranked_gsea");records_here=records(project)
        for name,digest in json.loads((project/"original-input-hashes.json").read_text()).items():assert sha(project/name)==digest
        assert not any(m["operation_type"] in ("association","meta_analysis","network_analysis") for _,m in records_here)
        if mode in HOLDS:assert manifest["state"]=="waiting_for_input" and not (directory/"ranked-gsea.tsv").exists()
        else:
            assert all(m["state"]=="complete" and m.get("reused_from_cache") for _,m in records_here)
            terms+=verify_enrichment(project,directory,manifest["details"])
            if mode in NATIVE:numerical+=verify_native(project,directory,manifest["details"])
            else:assert not any(m["operation_type"]=="participant_qc" for _,m in records_here)
            history=json.loads((project/"first-run-output-hashes.json").read_text())
            for name,digest in history.items():assert sha(project/name)==digest
        cli(jar,"verify","--project",project)
    return {"passed":True,"modes":len(MODES),"independent_partial_t_checks":numerical,"independent_enrichment_terms":terms,"scientific_calibration_claimed":False}
def run(root,jar):
    root.mkdir();software=root/"software";software.mkdir();pinned=software/"jlinalg.jar";shutil.copyfile(jar,pinned);shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py");write(root/"execution-software.json",{"jar_sha256":sha(pinned),"verifier_sha256":sha(Path(__file__))})
    for mode in MODES:
        project=root/mode
        if mode in IMPORTED or mode=="partial-null":generate_import(project,mode,root/("native-enumerated" if mode=="reused-enumerated" else "native-ols"))
        else:generate_native(project,mode)
        write(project/"original-input-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in project.rglob("*") if p.is_file()})
        cli(pinned,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-null-fixture")
        if mode not in HOLDS:
            write(project/"first-run-output-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in (project/"runs").rglob("*") if p.is_file()})
            cli(pinned,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-null-fixture")
    result=verify(root,pinned);write(root/"acceptance.json",result);return result
if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("action",choices=["run","verify"]);parser.add_argument("directory",type=Path);parser.add_argument("--jar",required=True,type=Path);a=parser.parse_args();print(json.dumps(run(a.directory.resolve(),a.jar.resolve()) if a.action=="run" else verify(a.directory.resolve(),a.jar.resolve())))
