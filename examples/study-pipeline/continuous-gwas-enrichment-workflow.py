"""Mixed-annotation S-LDSC arithmetic, typed reuse and missing-work acceptance.

fixtures NEW_DIRECTORY [--size 144]; run NEW_DIRECTORY --jar JAR;
verify DIRECTORY --jar PINNED_JAR. Requires NumPy, SciPy and PyYAML.
Synthetic chromosome-local genotype correlations and reference annotation moments
are generated independently. GWAS statistics are numerical test inputs, not a
calibrated biological GWAS. Baseline-only fixed weights are an explicit extension,
not a claim of upstream LDSC estimator parity or real scientific validation.
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
import yaml
from scipy.stats import norm, t

IDS=("ALL","BIN","CONT","SIGNED","NUISANCE")
KINDS=("binary","binary","continuous","continuous","binary")
MODES=("mixed","fixed","affine","continuous-nuisance","weak","bad-moments","selected","work-limit","supplied")
HOLDS={"weak","bad-moments","selected","work-limit"}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def clean(x):
    if isinstance(x,np.ndarray):return clean(x.tolist())
    if isinstance(x,list):return [clean(v) for v in x]
    if isinstance(x,dict):return {k:clean(v) for k,v in x.items()}
    if isinstance(x,(float,np.floating)):return float(x) if math.isfinite(x) else None
    if isinstance(x,np.integer):return int(x)
    return x
def write(p,v):p.write_text(json.dumps(clean(v),indent=2,allow_nan=False)+"\n",encoding="utf-8")
def rows(p):
    with p.open(encoding="utf-8",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
def table(p,items):
    with p.open("w",encoding="utf-8",newline="") as f:
        out=csv.DictWriter(f,fieldnames=list(items[0]),delimiter="\t",lineterminator="\n");out.writeheader();out.writerows(items)
def reference(summary,ld,annotations,products,blocks=13,free=True):
    ids=[r["term_id"] for r in annotations];binary=np.array([r["annotation_type"]=="binary" for r in annotations]);s=np.array([float(r["sum"]) for r in annotations]);q=np.array([[float(r[j]) for j in ids] for r in products]);n=len(summary);c=len(ids)
    y=np.array([float(r["chi_square"]) for r in summary]);ns=np.array([float(r["n"]) for r in summary]);x=np.array([[float(r["ld_"+j]) for j in ids] for r in ld]);wld=np.array([float(r["weight_ld"]) for r in ld]);aggregate=s[0]*(np.mean(y)-1)/np.mean(ns*x[:,0]);h=min(1,max(0,aggregate));w=1/(2*(1+h*ns*np.maximum(1,x[:,0])/s[0])**2*np.maximum(1,wld))
    design=ns[:,None]*x/np.mean(ns);target=y.copy() if free else y-1
    if free:design=np.column_stack((design,np.ones(n)))
    block=np.empty(n,dtype=int)
    for b in range(blocks):block[b*n//blocks:(b+1)*n//blocks]=b
    def solve(keep):
        sw=np.sqrt(w[keep]);fit=np.linalg.lstsq(design[keep]*sw[:,None],target[keep]*sw,rcond=None)[0];fit[:c]/=np.mean(ns);return fit
    full=solve(np.ones(n,dtype=bool));deleted=np.array([solve(block!=b) for b in range(blocks)]);co=full[:c];dc=deleted[:,:c];dt=dc@s;total=float(co@s)
    def cov(a):a=np.array(a);a=a[:,None] if a.ndim==1 else a;centered=a-a.mean(axis=0);return (blocks-1)/blocks*(centered.T@centered)
    covariance=cov(dc);total_se=math.sqrt(float(cov(dt)[0,0]));sd=np.sqrt(np.maximum(0,np.diag(q)/s[0]-(s/s[0])**2));stars=co*sd*s[0]/total;star_deletes=dc*sd[None,:]*s[0]/dt[:,None];star_se=np.sqrt(np.diag(cov(star_deletes)));stars[0]=star_se[0]=np.nan;star_deletes[:,0]=np.nan
    category=q@co;prop=category/total;prop_deletes=dc@q.T/dt[:,None];pse=np.sqrt(np.diag(cov(prop_deletes)));enrichment=prop/(s/s[0]);ese=pse/(s/s[0]);contrast=np.full(c,np.nan);contrast_se=contrast.copy();probability=contrast.copy();log_probability=contrast.copy();coefficient_log_p=math.log(2)+norm.logsf(np.abs(co/np.sqrt(np.diag(covariance))))
    for j in range(c):
        if not binary[j]:
            category[j]=prop[j]=pse[j]=enrichment[j]=ese[j]=np.nan;probability[j]=2*norm.sf(abs(co[j]/math.sqrt(covariance[j,j])));log_probability[j]=coefficient_log_p[j]
        elif j>0:
            a=q[j]/s[j]-(s-q[j])/(s[0]-s[j]);contrast[j]=a@co;contrast_se[j]=math.sqrt(float(a@covariance@a));probability[j]=2*t.sf(abs(contrast[j]/contrast_se[j]),blocks);log_probability[j]=math.log(2)+t.logsf(abs(contrast[j]/contrast_se[j]),blocks)
    return {"coefficients":co,"coefficient_covariance":covariance.ravel(),"delete_coefficients":dc,"delete_totals":dt,"delete_intercepts":deleted[:,c] if free else np.ones(blocks),"intercept":float(full[c]) if free else 1.,"intercept_se":math.sqrt(float(cov(deleted[:,c])[0,0])) if free else 0.,"total":total,"total_se":total_se,"category":category,"proportions":prop,"proportion_se":pse,"enrichments":enrichment,"enrichment_se":ese,"contrasts":contrast,"contrast_se":contrast_se,"p_values":probability,"log_p_values":log_probability,"coefficient_log_p":coefficient_log_p,"annotation_sd":sd,"standardized_coefficients":stars,"standardized_se":star_se,"standardized_deletes":star_deletes,"weights":w,"mean_chi_square":float(np.mean(y)),"aggregate_weight_heritability":float(aggregate)}
def fixture(directory,size=144):
    directory.mkdir();rng=np.random.default_rng(29137);m=size*2;individuals=192;chr_ref=np.arange(m)*22//m+1
    genotype=rng.binomial(2,.3,size=(individuals,m)).astype(float)
    for chrom in range(1,23):
        indices=np.where(chr_ref==chrom)[0]
        for start in range(0,len(indices),8):
            at=indices[start:start+8];base=rng.binomial(2,rng.uniform(.15,.45),size=(individuals,1));copy=rng.random((individuals,len(at)))<.65;genotype[:,at]=np.where(copy,base,genotype[:,at])
    regress=np.arange(0,m,2);g=genotype-genotype.mean(axis=0);g/=np.sqrt((g*g).sum(axis=0));r2=(g[:,regress].T@g)**2;r2[chr_ref[regress,None]!=chr_ref[None,:]]=0
    binary=rng.random(m)<.28;cont=rng.normal(size=m)+.4*binary;center=rng.normal(size=m);center-=center.mean();nuisance=rng.random(m)<.4;a=np.column_stack((np.ones(m),binary,cont,center,nuisance));s=a.sum(axis=0);q=a.T@a;scores=r2@a;weight=r2[:,regress].sum(axis=1);ns=60000+rng.integers(0,50001,size=size);tau=np.array([1e-4,3e-5,2e-6,-4e-6,-5e-6]);y=1.03+ns*(scores@tau)+rng.normal(0,.07,size=size)
    assert np.all(y>0) and np.all(a@tau>0)
    summary=[];ld=[]
    for i,index in enumerate(regress):
        identity={"variant_id":"v"+str(i),"chromosome":int(chr_ref[index]),"position":int(index*100+1000),"ref":"A","alt":"G"};summary.append(dict(identity,chi_square=float(y[i]),n=int(ns[i])));ld.append(dict(identity,weight_ld=float(weight[i]),**{"ld_"+j:float(scores[i,k]) for k,j in enumerate(IDS)}))
    annotation=[{"term_id":j,"term_name":"Synthetic "+j,"role":"all" if k==0 else "target" if k in (1,2) else "nuisance","annotation_type":KINDS[k],"sum":float(s[k])} for k,j in enumerate(IDS)];products=[dict(term_id=j,**{key:float(q[k,l]) for l,key in enumerate(IDS)}) for k,j in enumerate(IDS)]
    for name,items in [("summary.tsv",summary),("ld-scores.tsv",ld),("annotations.tsv",annotation),("overlap.tsv",products)]:table(directory/name,items)
    table(directory/"reference-annotations.tsv",[dict(reference_snp=i,**{j:float(a[i,k]) for k,j in enumerate(IDS)}) for i in range(m)])
    write(directory/"reference.json",{"cases":[{"free_intercept":free,"expected":reference(summary,ld,annotation,products,13,free)} for free in (True,False)]})
    write(directory/"PROVENANCE.json",{"generator_sha256":sha(Path(__file__)),"seed":29137,"numpy":np.__version__,"reference_snps":m,"regression_snps":size,"reference_individuals":individuals,"LD_generation":"Squared centered sample-genotype correlations, same chromosome only; artificial genotype blocks","weighting":"Fixed full-data initial weights from first all-SNP LD column/reference count only","oracle":"Independent NumPy weighted least squares and delete-block ratio calculations; SciPy normal/t tails","scientific_calibration":False,"files_sha256":{p.name:sha(p) for p in directory.iterdir() if p.is_file()}})
def policy(size):
    return {"annotation_mode":"mixed","summary":{"file":"summary.tsv","coverage":"complete","tested_snps":size,"population":"synthetic","genome_build":"GRCh38","calibration":"one_df_chi_square","model":"Artificial numerical statistics; not a fitted real GWAS","qc":"Complete synthetic source for arithmetic checks","ascertainment":"quantitative"},"reference":{"population":"synthetic","genome_build":"GRCh38","release":"mixed-fixture-1","common_variant_definition":"Complete synthetic annotation/genotype reference","regression_snp_definition":"Every second synthetic reference SNP","annotation_model":"Binary and continuous targets with signed continuous and binary nuisance columns"},"ld_scores":"ld-scores.tsv","annotations":"annotations.tsv","overlap":"overlap.tsv","blocks":13,"minimum_snps":100,"minimum_chromosomes":20,"excluded_regions":[],"chi_square_max":10000,"adjustment":"BY"}
def cli(jar,*args):
    r=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True);assert r.returncode==0,r.stdout+r.stderr
def records(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"));return [(p.parent,yaml.safe_load(p.read_text(encoding="utf-8"))) for p in (project/"runs"/status["run_id"]/"operations").glob("*/manifest.yaml")]
def one(project,kind):
    found=[r for r in records(project) if r[1]["operation_type"]==kind];assert len(found)==1,(project,kind,len(found));return found[0]
def generate(project,mode,fixtures,root):
    project.mkdir();config=policy(len(rows(fixtures/"summary.tsv")));supplied=[]
    for name in ("summary.tsv","ld-scores.tsv","annotations.tsv","overlap.tsv"):shutil.copyfile(fixtures/name,project/name)
    if mode=="fixed":config["intercept"]="one"
    if mode=="affine":
        annotation=rows(project/"annotations.tsv");ld=rows(project/"ld-scores.tsv");a=rows(fixtures/"reference-annotations.tsv");v=np.array([[float(r[j]) for j in IDS] for r in a]);v[:,2]=2.5*v[:,2]+.7;s=v.sum(axis=0);q=v.T@v
        for k,r in enumerate(annotation):r["sum"]=float(s[k])
        for r in ld:r["ld_CONT"]=2.5*float(r["ld_CONT"])+.7*float(r["ld_ALL"])
        table(project/"annotations.tsv",annotation);table(project/"ld-scores.tsv",ld);table(project/"overlap.tsv",[dict(term_id=j,**{key:float(q[k,l]) for l,key in enumerate(IDS)}) for k,j in enumerate(IDS)])
    if mode=="continuous-nuisance":
        annotation=rows(project/"annotations.tsv");annotation[2]["role"]="nuisance";table(project/"annotations.tsv",annotation)
    if mode=="weak":
        summary=rows(project/"summary.tsv")
        for r in summary:r["chi_square"]="1"
        table(project/"summary.tsv",summary)
    if mode=="bad-moments":
        products=rows(project/"overlap.tsv");products[2]["CONT"]="-1";table(project/"overlap.tsv",products)
    if mode=="selected":config["summary"]["coverage"]="selected_only"
    if mode=="work-limit":config["maximum_work"]=1
    if mode=="supplied":
        native,_=one(root/"mixed","gwas_enrichment");shutil.copyfile(native/"gwas-enrichment.tsv",project/"provided.tsv");config={}
        supplied=[{"id":"complete-effects","kind":"gwas_enrichment","files":{"results":"provided.tsv"},"scope":{"species":"human","genome_build":"GRCh38","ancestry":"synthetic","modality":"gwas"},"method":{"name":"Retained synthetic mixed-annotation S-LDSC","rationale":"No raw GWAS/LD refit"},"family":{"id":"mixed-primary-targets","coverage":"complete","tested_count":2,"adjustment":"BY","adjusted_p_from_full_family":True}}]
    study={"schema_version":1,"project":{"id":"continuous-gwas-"+mode,"title":"Synthetic mixed-annotation GWAS enrichment","description":"Numerical and typed-source acceptance"},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[],"hypotheses":[{"id":"H","description":"Conditional functional annotation statistics","analysis":{"mode":"manual","goals":["gwas_enrichment","finding_inventory"],"gwas_enrichment":config,"supplied_results":supplied}}]};write(project/"study.json",study)
def near(actual,expected,label):
    if expected is None or not math.isfinite(float(expected)):assert actual in ("NA",None), (label,actual,expected)
    else:assert math.isclose(float(actual),float(expected),rel_tol=3e-7,abs_tol=max(1e-28,abs(float(expected))*3e-7)),(label,actual,expected)
def verify(root,jar):
    pins=json.loads((root/"execution-software.json").read_text());assert sha(jar)==pins["jar_sha256"] and sha(Path(__file__))==pins["verifier_sha256"];checks=0
    for mode in MODES:
        project=root/mode;directory,manifest=one(project,"gwas_enrichment")
        for name,digest in json.loads((project/"original-input-hashes.json").read_text()).items():assert sha(project/name)==digest
        assert not any(m["operation_type"] in ("association","meta_analysis","participant_qc") for _,m in records(project))
        if mode in HOLDS:
            assert manifest["state"]=="waiting_for_input"
            if (directory/"gwas-enrichment.tsv").exists():assert all(r["adjusted_p"]=="NA" for r in rows(directory/"gwas-enrichment.tsv"))
        else:
            assert manifest["state"]=="complete" and manifest.get("reused_from_cache") is True
            actual=rows(directory/"gwas-enrichment.tsv")
            if mode=="supplied":
                assert manifest["details"]["analysis_executed_by_jlinalg"] is False and not (directory/"ld-scores.tsv").exists();expected_rows=rows(project/"provided.tsv");assert actual==expected_rows
            else:
                a=rows(project/"annotations.tsv");expected=reference(rows(project/"summary.tsv"),rows(project/"ld-scores.tsv"),a,rows(project/"overlap.tsv"),13,mode!="fixed");log_probabilities={r["term_id"]:expected["log_p_values"][IDS.index(r["term_id"])] for r in actual};ordered=sorted(log_probabilities,key=log_probabilities.get);factor=sum(1/i for i in range(1,len(ordered)+1));q={};log_q={};running=0.
                for i in range(len(ordered)-1,-1,-1):key=ordered[i];running=min(running,log_probabilities[key]+math.log(len(ordered)*factor/(i+1)));log_q[key]=running;q[key]=math.exp(running)
                for row in actual:
                    j=IDS.index(row["term_id"])
                    for field,key in [("coefficient","coefficients"),("standardized_coefficient","standardized_coefficients"),("standardized_coefficient_se","standardized_se"),("annotation_sd","annotation_sd"),("p_value","p_values"),("log_p_value","log_p_values"),("coefficient_log_p","coefficient_log_p"),("enrichment","enrichments"),("enrichment_se","enrichment_se"),("category_heritability","category"),("per_snp_contrast","contrasts"),("per_snp_contrast_se","contrast_se")]:near(row[field],expected[key][j],(mode,row["term_id"],field));checks+=1
                    near(row["coefficient_se"],math.sqrt(expected["coefficient_covariance"][j*len(IDS)+j]),"coefficient SE");near(row["adjusted_p"],q[row["term_id"]],"target correction");near(row["log_adjusted_p"],log_q[row["term_id"]],"log target correction");checks+=3
                covariance=rows(directory/"sldsc-coefficient-covariance.tsv")
                for row in covariance:near(row["covariance"],expected["coefficient_covariance"][IDS.index(row["left"])*len(IDS)+IDS.index(row["right"])],"covariance");checks+=1
                for row in rows(directory/"sldsc-standardized-block-deletes.tsv"):near(row["standardized_coefficient"],expected["standardized_deletes"][int(row["block"])][IDS.index(row["term_id"])],"standardized delete");checks+=1
            inv,_=one(project,"finding_inventory");observations=[o for line in (inv/"finding-inventory.jsonl").read_text(encoding="utf-8").splitlines() for o in json.loads(line)["observations"]]
            if mode!="continuous-nuisance":
                continuous=[o for o in observations if o.get("annotation_type")=="continuous"];assert len(continuous)==1;assert continuous[0]["inference_target"]=="conditional_coefficient" and "not a binary category" in continuous[0]["required_manuscript_caveat"]
            for name,digest in json.loads((project/"first-run-hashes.json").read_text()).items():assert sha(project/name)==digest
        cli(jar,"verify","--project",project)
    baseline={r['term_id']:r for r in rows(one(root/'mixed','gwas_enrichment')[0]/'gwas-enrichment.tsv')};affine={r['term_id']:r for r in rows(one(root/'affine','gwas_enrichment')[0]/'gwas-enrichment.tsv')}
    for term,field in [('CONT','standardized_coefficient'),('CONT','standardized_coefficient_se'),('BIN','enrichment')]:near(affine[term][field],float(baseline[term][field]),'affine invariance');checks+=1
    return {"passed":True,"modes":len(MODES),"independent_numeric_checks":checks,"affine_standardized_effect_and_binary_enrichment_invariance":True,"log_tail_precision_verified":True,"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"scientific_calibration":False,"upstream_estimator_parity_claimed":False,"full_pipeline_conformance":False}
def run(root,jar):
    root.mkdir();software=root/"software";software.mkdir();shutil.copyfile(jar,software/"jlinalg.jar");shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py");fixture(software/"fixtures",600);pinned=software/"jlinalg.jar";write(root/"execution-software.json",{"jar_sha256":sha(pinned),"verifier_sha256":sha(Path(__file__))})
    for mode in MODES:
        project=root/mode;generate(project,mode,software/"fixtures",root);write(project/"original-input-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in project.rglob("*") if p.is_file()});cli(pinned,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-reference")
        if mode not in HOLDS:
            write(project/"first-run-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in (project/"runs").rglob("*") if p.is_file()});cli(pinned,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-reference")
    result=verify(root,pinned);write(root/"verification.json",result);return result
if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("action",choices=("fixtures","run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path);parser.add_argument("--size",type=int,default=144);a=parser.parse_args()
    if a.action=="fixtures":fixture(a.directory.resolve(),a.size)
    else:
        assert a.jar is not None,"--jar is required";print(json.dumps(run(a.directory.resolve(),a.jar.resolve()) if a.action=="run" else verify(a.directory.resolve(),a.jar.resolve())))
