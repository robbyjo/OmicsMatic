"""Offline genotype QC acceptance; run NEW_DIRECTORY --jar JAR or verify DIRECTORY --jar PINNED_JAR.

Independent genotype counts, conditional HWE enumeration, SciPy Fisher tests and
quantitative OLS arithmetic validate software contracts. Synthetic binary cases
check QC and coding, not sparse-outcome association calibration or biological validity.
"""
import argparse
import csv
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import numpy as np
import scipy
from scipy import stats
import yaml

MODES=("default-mac","hwe-all","binary-controls","binary-cases","missingness","missingness-budget","dosage-hwe","dosage-valid","hard-call-mismatch","marker-missingness","info-quality","sample-call-rate","unsupported","reference-panel","unknown-chromosome")
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def table(path,header,data):
    with path.open("w",encoding="utf-8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(header);writer.writerows(data)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    process=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],capture_output=True,text=True)
    assert process.returncode in (0,3),process.stdout+process.stderr
    return process.stdout+process.stderr
def artifacts(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in sorted((project/"runs"/status["run_id"]/"operations").iterdir())]
def one(project,kind):
    found=[(p,m) for p,m in artifacts(project) if m["operation_type"]==kind];assert len(found)==1,(kind,found);return found[0]
def pin(root,jar):
    (root/"software").mkdir();target=root/"software"/("jlinalg-"+sha(jar)+".jar");shutil.copyfile(jar,target);shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py")
    write(root/"execution-software.json",{"jar":target.relative_to(root).as_posix(),"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"python":sys.version,"numpy":np.__version__,"scipy":scipy.__version__,"pyyaml":yaml.__version__});return target
def generate(root,mode):
    project=root/mode;project.mkdir();n=80;binary=mode in ("binary-controls","binary-cases","missingness","missingness-budget","reference-panel")
    ids=["a"+str(i) for i in range(n)];groups=[int(i%4==3) for i in range(n)];z=[i%7 for i in range(n)]
    phenotype=[[ids[i],"disease" if groups[i] else "healthy" if binary else 0,"NA" if i==0 else z[i]] for i in range(n)]
    for i,row in enumerate(phenotype):row[1]=("disease" if groups[i] else "healthy") if binary else 2+.4*z[i]+math.sin(i*1.3)
    table(project/"phenotype.tsv",["id","y","z"],reversed(phenotype))
    variants=[{"id":"mac19","chromosome":"1","position":100,"ref":"A","alt":"G","values":[int(1<=i<=19) for i in range(n)],"quality":.95},
        {"id":"mac20","chromosome":"1","position":200,"ref":"A","alt":"G","values":[int(1<=i<=20) for i in range(n)],"quality":.95},
        {"id":"lost-mac20","chromosome":"1","position":300,"ref":"A","alt":"G","values":[int(i<20) for i in range(n)],"quality":.95},
        {"id":"common","chromosome":"1","position":400,"ref":"A","alt":"G","values":[i%3 for i in range(n)],"quality":.95}]
    association={"model":"glm" if binary else "ols","threads":1};files={"matrix":"genotype.vcf","phenotype":"phenotype.tsv"}
    if binary:association.update(family="binomial",case_value="disease",control_value="healthy")
    if mode in ("hwe-all","binary-controls","binary-cases","reference-panel"):
        control_index=0;values=[]
        for group in groups:
            if group:values.append(1)
            else:values.append((0,1,1,2)[control_index%4]);control_index+=int(not group)
        variants.append({"id":"case-heterozygotes","chromosome":"1","position":500,"ref":"A","alt":"G","values":values,"quality":.95})
        association["minimum_hwe_p"]=.001
    if mode=="binary-cases":association["hwe_filter_scope"]="cases"
    if mode=="hwe-all":association["hwe_filter_scope"]="all"
    if mode=="reference-panel":
        files["hwe_samples"]="panel.ids";(project/"panel.ids").write_text("\n".join(id for id,g in zip(ids,groups) if not g)+"\n",encoding="utf-8");association["hwe_filter_scope"]="all"
    if mode in ("missingness","missingness-budget"):
        missing={i for i,g in enumerate(groups) if g and i<48}|{4};values=[None if i in missing else i%3 for i in range(n)]
        variants.append({"id":"differential-missingness","chromosome":"1","position":600,"ref":"A","alt":"G","values":values,"quality":.95})
        association.update(minimum_sample_call_rate=0,maximum_marker_missing=1,minimum_missingness_p=.001)
        if mode=="missingness-budget":association["missingness_max_states"]=1
    if mode in ("dosage-hwe","dosage-valid","hard-call-mismatch"):
        variants=[{"id":"dosage","chromosome":"1","position":700,"ref":"A","alt":"G","values":[.25+i%2 for i in range(n)],"quality":.95,"dosage":True}]
        association.update(genotype_kind="imputed",minimum_hwe_p=.001 if mode=="dosage-hwe" else 0)
        if mode=="hard-call-mismatch":association["genotype_kind"]="hard_calls"
    if mode=="marker-missingness":
        for count in (1,2):variants.append({"id":"missing-"+str(count),"chromosome":"1","position":1100+count,"ref":"A","alt":"G","values":[None if i in range(1,count+1) else i%3 for i in range(n)],"quality":.95})
        association["minimum_sample_call_rate"]=0
    if mode=="info-quality":
        for label,quality in [("low-info",.7),("missing-info",None)]:variants.append({"id":label,"chromosome":"1","position":1200+len(variants),"ref":"A","alt":"G","values":[i%3 for i in range(n)],"quality":quality})
        association["minimum_info"]=.8
    if mode=="sample-call-rate":
        variants=[{"id":"panel-"+str(k),"chromosome":"1","position":1300+k,"ref":"A","alt":"G","values":[None if i==1 and k==0 or i==2 and k<2 else (i+k)%3 for i in range(n)],"quality":.95} for k in range(50)]
    if mode=="unsupported":
        variants.append({"id":"haploid","chromosome":"1","position":800,"ref":"A","alt":"G","values":[i%3 for i in range(n)],"quality":.95,"haploid_index":1})
        variants[-1]["values"][1]=1
        variants.append({"id":"multi","chromosome":"1","position":900,"ref":"A","alt":"G,T","values":[i%3 for i in range(n)],"quality":[.7,.95],"multiallelic":True})
        variants.append({"id":"x-diploid","chromosome":"X","position":1000,"ref":"A","alt":"G","values":[i%3 for i in range(n)],"quality":.95})
        association.update(chromosome_scope="human_autosomes",minimum_info=.8)
    if mode=="unknown-chromosome":
        files["matrix"]="genotype.tsv";table(project/"genotype.tsv",["variant_id",*ids],[[v["id"],*v["values"]] for v in variants]);association["minimum_hwe_p"]=.001
        for v in variants:v["chromosome"]=""
    else:
        lines=["##fileformat=VCFv4.2",'##INFO=<ID=R2,Number=A,Type=Float,Description="Synthetic allele quality">','##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">','##FORMAT=<ID=DS,Number=A,Type=Float,Description="Dosage">',"\t".join(["#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT",*ids])]
        for v in variants:
            genotype=[]
            for i,value in enumerate(v["values"]):
                call="./." if value is None else ("0/0","0/1","1/1")[min(2,int(value))]
                if v.get("haploid_index")==i:call=str(int(value))
                if v.get("multiallelic"):call=("0/1","1/2","2/2")[i%3]
                if v.get("dosage"):call+=" :"+str(value);call=call.replace(" ","")
                genotype.append(call)
            quality=",".join(map(str,v["quality"])) if isinstance(v["quality"],list) else str(v["quality"])
            lines.append("\t".join([v["chromosome"],str(v["position"]),v["id"],v["ref"],v["alt"],".","PASS","." if v["quality"] is None else "R2="+quality,"GT:DS" if v.get("dosage") else "GT",*genotype]))
        (project/"genotype.vcf").write_text("\n".join(lines)+"\n",encoding="utf-8")
    analysis={"mode":"manual","goals":["association","candidate_prioritization"],"association":association,"harmonization":{"effect_outliers":{"enabled":False}}}
    study={"schema_version":1,"project":{"id":"genotype-qc-"+mode,"title":"Synthetic genotype QC "+mode,"description":"Offline input/QC/numerical verification only; no biological conclusion or binary-tail calibration claim."},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[{"id":"a","cohort":"synthetic","type":"gwas","stage":"processed_matrix","species":"human","genome_build":"GRCh38","feature_id_type":"variant","files":files,"columns":{"sample_id":"id"}}],"hypotheses":[{"id":"H","description":"Synthetic phenotype-scoped QC.","outcome":"y","covariates":["z"],"inputs":{"discovery":["a"]},"analysis":analysis}]}
    write(project/"study.yaml",study);write(project/"fixture-truth.json",{"n":n,"ids":ids,"groups":groups if binary else None,"phenotype":phenotype,"variants":variants,"binary":binary});return project
def hwe(values):
    called=[round(v) for v in values if v is not None];counts=[called.count(g) for g in (0,1,2)];n=len(called);a=2*counts[2]+counts[1]
    if not n:return None
    weights={}
    for h in range(a%2,min(a,2*n-a)+1,2):
        aa=(a-h)//2;rr=n-h-aa
        if aa>=0 and rr>=0:weights[h]=Fraction(2**h,math.factorial(h)*math.factorial(aa)*math.factorial(rr))
    observed=weights[counts[1]];return float(sum(w for w in weights.values() if w<=observed)/sum(weights.values()))
def close(actual,wanted,label):assert math.isclose(float(actual),float(wanted),rel_tol=2e-9,abs_tol=1e-11),(label,actual,wanted)
def expected_reasons(v,aligned,values,ploidies,mac,policy,truth,mode):
    reasons=set();called=sum(value is not None for value in values)
    if mac<policy.get("minimum_mac",20):reasons.add("BELOW_MINIMUM_MAC")
    if (len(values)-called)/len(values)>policy.get("maximum_marker_missing",.02):reasons.add("TOO_MANY_MISSING")
    if any(p!=2 for p in ploidies):reasons.add("NON_DIPLOID_GENOTYPES")
    if policy.get("genotype_kind")=="hard_calls" and any(value is not None and abs(value-round(value))>1e-8 for value in values):reasons.add("NON_HARD_CALL_VALUES")
    if policy.get("chromosome_scope")=="human_autosomes" and v["chromosome"] not in map(str,range(1,23)):reasons.add("OUTSIDE_AUTOSOMAL_SCOPE" if v["chromosome"] else "CHROMOSOME_UNAVAILABLE")
    info=policy.get("minimum_info",.8 if policy.get("genotype_kind")=="imputed" else None)
    if info is not None:
        if v["quality"] is None:reasons.add("IMPUTATION_QUALITY_UNAVAILABLE")
        elif v["quality"]<info:reasons.add("BELOW_IMPUTATION_QUALITY")
    if policy.get("minimum_hwe_p",0)>0:
        if v.get("dosage") or any(p!=2 for p in ploidies) or v["chromosome"] not in map(str,range(1,23)):reasons.add("HWE_UNAVAILABLE")
        else:
            scope=policy.get("hwe_filter_scope","controls" if truth["binary"] else "all")
            subset=[value for j,value in enumerate(values) if (mode!="reference-panel" or not truth["groups"][aligned[j]]) and (scope=="all" or truth["groups"][aligned[j]]==int(scope=="cases"))]
            p=hwe(subset)
            if p is None:reasons.add("HWE_UNAVAILABLE")
            elif p<policy["minimum_hwe_p"]:reasons.add("BELOW_MINIMUM_HWE_P")
    if policy.get("minimum_missingness_p",0)>0:
        matrix=[[sum(values[j] is None for j,i in enumerate(aligned) if truth["groups"][i]==g),sum(values[j] is not None for j,i in enumerate(aligned) if truth["groups"][i]==g)] for g in (1,0)]
        states=min(sum(matrix[0]),sum(r[0] for r in matrix))-max(0,sum(r[0] for r in matrix)-sum(matrix[1]))+1
        if states>policy.get("missingness_max_states",100000):reasons.add("DIFFERENTIAL_MISSINGNESS_UNAVAILABLE")
        elif stats.fisher_exact(matrix).pvalue<policy["minimum_missingness_p"]:reasons.add("BELOW_MINIMUM_MISSINGNESS_P")
    return reasons
def verify_case(project,mode):
    truth=json.loads((project/"fixture-truth.json").read_text(encoding="utf-8"));study=json.loads((project/"study.yaml").read_text(encoding="utf-8"));policy=study["hypotheses"][0]["analysis"]["association"]
    folder,manifest=one(project,"association");assert manifest["state"]=="complete",manifest
    qc=rows(folder/"association.tsv");standard=rows(folder/"standard-results/a.tsv");aligned=[i for i in range(1,truth["n"]) if mode!="sample-call-rate" or i!=2];assert len(rows(one(project,"participant_qc")[0]/"analysis-phenotype.tsv"))==len(aligned)
    by_id={v["id"]:v for v in truth["variants"]};checks=0
    for row in qc:
        if row["id"].startswith("multi:"):
            low=row["id"].endswith(":G");close(row["imputation_info"],.7 if low else .95,"allele-specific R2");assert set(row["filter_reason"].split(";"))==({"MULTIALLELIC_SPLIT_REQUIRES_MODEL","BELOW_IMPUTATION_QUALITY"} if low else {"MULTIALLELIC_SPLIT_REQUIRES_MODEL"});continue
        v=by_id[row["id"]];values=[v["values"][i] for i in aligned];ploidies=[1 if v.get("haploid_index")==i else 2 for i in aligned];called=[i for i,value in enumerate(values) if value is not None]
        ac=sum(values[i] for i in called);an=sum(ploidies[i] for i in called);minor_alt=ac/an<=.5;mac=ac if minor_alt else an-ac
        close(row["n_samples"],len(aligned),"aligned N");close(row["called"],len(called),"called N");close(row["allele_number"],an,"allele number");close(row["minor_allele_count"],mac,"MAC");checks+=4
        wanted=expected_reasons(v,aligned,values,ploidies,mac,policy,truth,mode);assert set(filter(None,row["filter_reason"].split(";")))==wanted,(mode,row["id"],row["filter_reason"],wanted);assert row["status"]==("filtered" if wanted else "ok")
        if truth["binary"]:
            for g,name in [(1,"cases"),(0,"controls")]:
                indexes=[j for j,i in enumerate(aligned) if truth["groups"][i]==g];observed=[j for j in indexes if values[j] is not None]
                close(row["n_"+name],len(indexes),name+" N");close(row["called_"+name],len(observed),name+" called");close(row["minor_allele_count_"+name],sum(values[j] if minor_alt else ploidies[j]-values[j] for j in observed),name+" same minor count");checks+=3
            case=[j for j,i in enumerate(aligned) if truth["groups"][i]==1];control=[j for j,i in enumerate(aligned) if truth["groups"][i]==0]
            matrix=[[sum(values[j] is None for j in group),sum(values[j] is not None for j in group)] for group in (case,control)]
            state_count=min(sum(matrix[0]),sum(r[0] for r in matrix))-max(0,sum(r[0] for r in matrix)-sum(matrix[1]))+1
            if state_count>policy.get("missingness_max_states",100000):assert row["missingness_method"]=="exact-state-budget-exceeded" and row["missingness_p_value"]==""
            else:close(row["missingness_p_value"],stats.fisher_exact(matrix).pvalue,"Fisher missingness");checks+=1
        if v.get("dosage"):
            assert row["hwe_p_all"]=="" and row["genotype_origin"]=="dosage"
            if policy.get("minimum_hwe_p",0)>0:assert "HWE_UNAVAILABLE" in row["filter_reason"]
            elif mode=="hard-call-mismatch":assert "NON_HARD_CALL_VALUES" in row["filter_reason"]
            else:assert row["status"]=="ok"
        if v.get("haploid_index") is not None:assert "NON_DIPLOID_GENOTYPES" in row["filter_reason"];continue
        if v["chromosome"]!="1":
            assert row["hwe_p_all"]==""
            if not v["chromosome"]:assert "HWE_UNAVAILABLE" in row["filter_reason"]
            else:assert "OUTSIDE_AUTOSOMAL_SCOPE" in row["filter_reason"]
            continue
        if not v.get("dosage"):
            panel=[j for j,i in enumerate(aligned) if mode!="reference-panel" or not truth["groups"][i]];all_values=[values[j] for j in panel]
            close(row["hwe_p_all"],hwe(all_values),"conditional HWE all");checks+=1
            if truth["binary"]:
                for g,name in [(1,"cases"),(0,"controls")]:
                    subgroup=[values[j] for j in panel if truth["groups"][aligned[j]]==g];p=hwe(subgroup)
                    if p is None:assert row["hwe_p_"+name]==""
                    else:close(row["hwe_p_"+name],p,"conditional HWE "+name);checks+=1
        if not truth["binary"] and row["status"]=="ok":
            y=np.array([truth["phenotype"][i][1] for i in aligned],float);g=np.array(values,float);g=np.where(np.isfinite(g),g,np.nanmean(g));x=np.column_stack([np.ones(len(aligned)),[truth["phenotype"][i][2] for i in aligned],g]);beta=np.linalg.lstsq(x,y,rcond=None)[0];df=len(y)-x.shape[1];variance=np.sum((y-x@beta)**2)/df;se=math.sqrt(np.linalg.inv(x.T@x)[-1,-1]*variance)
            close(row["beta"],beta[-1],"OLS beta");close(row["standard_error"],se,"OLS SE");close(row["p_value"],2*stats.t.sf(abs(beta[-1]/se),df),"OLS t P");checks+=3
    accepted={row["id"] for row in qc if row["status"]=="ok"};assert {row["feature_id"] for row in standard}==accepted
    tests=sorted((row for row in qc if row["status"]=="ok"),key=lambda row:(float(row["p_value"]),row["id"]));adjusted={};running=1
    for rank,row in reversed(list(enumerate(tests,1))):running=min(running,float(row["p_value"])*len(tests)/rank);adjusted[row["id"]]=running
    for row in tests:close(row["fdr_bh"],adjusted[row["id"]],"BH across retained tests only");checks+=1
    if mode=="default-mac":assert accepted=={"mac20","common"}
    if mode=="binary-controls":assert "case-heterozygotes" in accepted
    if mode=="binary-cases":assert "case-heterozygotes" not in accepted
    if mode in ("missingness","missingness-budget"):assert "differential-missingness" not in accepted
    if mode in ("dosage-hwe","unknown-chromosome","hard-call-mismatch"):assert not accepted and manifest["details"]["evidence_disposition"]=="not_estimable"
    if mode=="dosage-valid":assert accepted=={"dosage"}
    if mode=="marker-missingness":assert "missing-1" in accepted and "missing-2" not in accepted
    if mode=="sample-call-rate":
        assert len(accepted)==50
        excluded=rows(one(project,"participant_qc")[0]/"sample-exclusions.tsv");assert any(r["sample_id"]=="a2" and r["reason"]=="sample_call_rate" for r in excluded);assert all(r["sample_id"]!="a1" for r in excluded)
    if mode=="info-quality":
        lookup={r["id"]:r for r in qc};assert "BELOW_IMPUTATION_QUALITY" in lookup["low-info"]["filter_reason"];assert "IMPUTATION_QUALITY_UNAVAILABLE" in lookup["missing-info"]["filter_reason"]
    if mode=="reference-panel":assert "case-heterozygotes" in accepted;assert sha(project/"panel.ids") in (folder/"association.tsv.manifest.json").read_text(encoding="utf-8")
    return {"case":mode,"retained_variants":sorted(accepted),"qc_rows":len(qc),"independent_value_checks":checks}
def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text(encoding="utf-8"));assert sha(jar)==software["jar_sha256"]==sha(root/software["jar"]);assert sha(Path(__file__))==software["verifier_sha256"]==sha(root/"workflow-verifier-source.py")
    cases=[verify_case(root/mode,mode) for mode in MODES];write(root/"verification.json",{"passed":True,"jar_sha256":sha(jar),"verifier_sha256":software["verifier_sha256"],"cases":cases,"scope":"Synthetic allele/sample QC, exact HWE/Fisher and quantitative OLS arithmetic. Binary association tail calibration, relatedness correctness, ancestry, sex/PAR models and real scientific validity remain separate requirements."});return cases
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);a=parser.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=="run":
        root.mkdir();jar=pin(root,jar)
        for mode in MODES:
            project=generate(root,mode);(project/"execution.log").write_text(cli(jar,"auto",project/"study.yaml","--offline"),encoding="utf-8")
            assert all(m["state"] in ("complete","not_applicable") for p,m in artifacts(project)),[(m["operation_type"],m["state"],m.get("summary")) for p,m in artifacts(project)]
            (project/"replay.log").write_text(cli(jar,"auto",project/"study.yaml","--offline"),encoding="utf-8");assert all(m.get("reused_from_cache") is True for p,m in artifacts(project) if m["operation_type"]!="report")
            (project/"verify.log").write_text(cli(jar,"verify","--project",project),encoding="utf-8")
    cases=verify(root,jar);print(json.dumps({"passed":True,"cases":len(cases),"value_checks":sum(c["independent_value_checks"] for c in cases),"directory":str(root),"jar_sha256":sha(jar)}))
if __name__=="__main__":main()
