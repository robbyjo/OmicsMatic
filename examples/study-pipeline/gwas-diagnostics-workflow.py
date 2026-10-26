"""Offline GWAS result diagnostics and supplied-diagnostic reuse acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR
Requires NumPy, SciPy, PyYAML. All genotypes and results are synthetic.
No external requests, scientific model certification or real AI appraisal.
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
from scipy.stats import norm, chi2, t as student_t
import yaml

CASES=("complete","unknown","underflow","unresolved-zero","log-conflict","mixed-build","partial-coordinates","display-bounds",
       "native-ols","meta","selected-meta","complete-meta","provided-diagnostics","provided-sensitivity","unknown-build","invalid-supplied-lambda","row-bound","invalid-policy")
INVALID={"invalid-policy"}
SECRET="PRIVATE_DIAGNOSTIC_SENTINEL"
FIELDS=("source_id","contrast","stratum","testing_coverage","observed_tests","diagnostic_tests","unresolved_tests","lambda_p_chisq1","lambda_status","coordinate_tests","coordinate_status")

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def table(path):
    with path.open(encoding="utf-8",newline="") as f:return list(csv.DictReader(f,delimiter="\t"))
def save_table(path,records):
    with path.open("w",encoding="utf-8",newline="") as f:
        out=csv.DictWriter(f,fieldnames=list(records[0]),delimiter="\t",lineterminator="\n");out.writeheader();out.writerows(records)
def cli(jar,project,*args,invalid=False):
    r=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],capture_output=True,text=True)
    with (project/"commands.jsonl").open("a",encoding="utf-8") as out:out.write(json.dumps({"args":list(map(str,args)),"exit_code":r.returncode,"stdout":r.stdout,"stderr":r.stderr})+"\n")
    assert (r.returncode!=0) if invalid else r.returncode==0,(project,r.stdout,r.stderr)
    return r
def external(identity,kind,files,count=None,coverage="complete"):
    d={"id":identity,"kind":kind,"files":files,"scope":{"phenotype":"y","modality":"genomics"},"method":{"name":"External synthetic method","parameters":{"private_note":SECRET}},"limitations":[SECRET]}
    if count is not None:d["family"]={"id":identity+"-family","coverage":coverage,"tested_count":count,"adjustment":"BH"}
    return d
def generate(project,case):
    project.mkdir();n=64;records=[]
    for i in range(n):
        p=(i+1)/(n+1);records.append({"feature_id":f"v{i:03d}","beta":float(norm.isf(p/2)*.1),"se":.1,"p_value":p,"log_p_value":math.log(p),"chromosome":"chr1" if i<n//2 else "2","position":1000000+i*100000,"genome_build":"GRCh38","minor_allele_frequency":(.005,.02,.1)[i%3],"minor_allele_count":(10,50,200)[i%3],"imputation_info":(.7,.85,.99)[i%3]})
    if case in {"underflow","display-bounds"}:records[0].update(p_value=0,log_p_value=-2500 if case=="display-bounds" else -1000)
    if case=="unresolved-zero":records[0].update(p_value=0,log_p_value="NA")
    if case=="log-conflict":records[0]["log_p_value"]=-1000
    if case=="mixed-build":records[-1]["genome_build"]="GRCh37"
    if case=="unknown-build":
        for r in records:r["genome_build"]="unknown"
    if case=="partial-coordinates":records[0].update(chromosome="NA",position="NA")
    if case in {"selected-meta","complete-meta","provided-diagnostics","provided-sensitivity","invalid-supplied-lambda"}:
        records=records[:2]
        if case=="selected-meta":
            for r,p,q in zip(records,[.000002,.0000001],[.4,.04]):r.update(p_value=p,log_p_value=math.log(p),adjusted_p=q)
    save_table(project/"results.tsv",records)
    (project/"annotation.tsv").write_text("feature_id\tgene_symbol\n"+"".join(f"{r['feature_id']}\tSYNTH{i}\n" for i,r in enumerate(records)),encoding="utf-8")
    policy={"minimum_lambda_tests":2,"dataset_coverage":{"a":"complete"}}
    if case=="unknown":policy["dataset_coverage"]={}
    if case=="display-bounds":policy.update(maximum_plot_points=2,plot_y_limit=10)
    if case=="row-bound":policy["maximum_rows"]=50
    if case=="invalid-policy":policy["mac_breaks"]=[100,20]
    analysis={"mode":"manual","goals":["gwas_diagnostics","impact_assessment"],"gwas_diagnostics":policy,"harmonization":{"effect_outliers":{"enabled":False}}}
    datasets=[{"id":"a","cohort":"A","type":"gwas","stage":"association_results","files":{"results":"results.tsv","annotation":"annotation.tsv"}}]
    covariates=[]
    if case=="native-ols":
        rng=np.random.default_rng(321);g=rng.binomial(2,.3,size=(6,80));z=rng.normal(size=80);noise=rng.normal(size=80);y=.3*g[0]+.2*z+noise
        (project/"genotypes.vcf").write_text("##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t"+"\t".join(f"s{i}" for i in range(80))+"\n"+"".join(f"{1+i//3}\t{1000000+i*1000000}\tv{i:03d}\tA\tG\t.\tPASS\t.\tGT\t"+"\t".join(("0/0","0/1","1/1")[x] for x in v)+"\n" for i,v in enumerate(g)),encoding="utf-8")
        save_table(project/"phenotype.tsv",[{"id":f"s{i}","y":y[i],"z":z[i]} for i in range(80)])
        datasets=[{"id":"a","cohort":"A","type":"gwas","stage":"processed_matrix","genome_build":"GRCh38","files":{"matrix":"genotypes.vcf","phenotype":"phenotype.tsv","annotation":"annotation.tsv"}}]
        covariates=["z"];analysis["association"]={"model":"ols","genotype_kind":"hard_calls"};analysis["goals"].insert(0,"association");policy["dataset_coverage"]={}
        expected={}
        for i,values in enumerate(g):
            design=np.column_stack((np.ones(80),z,values));coef=np.linalg.lstsq(design,y,rcond=None)[0];residual=y-design@coef;se=math.sqrt(float(residual@residual)/77*np.linalg.inv(design.T@design)[-1,-1]);expected[f"v{i:03d}"]={"beta":coef[-1],"se":se,"p_value":float(2*student_t.sf(abs(coef[-1]/se),77)),"minor_allele_count":min(float(values.sum()),160-float(values.sum()))}
        write(project/"expected-native.json",expected)
    if case=="meta":
        second=[{**r,"beta":r["beta"]*.9,"se":.12,"p_value":float(2*norm.sf(abs(r["beta"]*.9/.12))),"log_p_value":float(math.log(2)+norm.logsf(abs(r["beta"]*.9/.12)))} for r in records];save_table(project/"second.tsv",second)
        datasets.append({"id":"b","cohort":"B","type":"gwas","stage":"association_results","files":{"results":"second.tsv","annotation":"annotation.tsv"}});policy["dataset_coverage"]["b"]="complete";analysis["goals"].insert(0,"meta_analysis")
    if case in {"selected-meta","complete-meta","provided-diagnostics","provided-sensitivity","invalid-supplied-lambda"}:
        datasets=[];policy["dataset_coverage"]={};meta=external("pool","meta_analysis",{"results":"results.tsv","feature_annotation":"annotation.tsv"},400000 if case=="selected-meta" else len(records),"selected_only" if case=="selected-meta" else "complete")
        if case=="selected-meta":meta["family"]["adjusted_p_from_full_family"]=True
        analysis["supplied_results"]=[meta]
        if case in {"provided-diagnostics","provided-sensitivity","invalid-supplied-lambda"}:
            value=float(np.median(norm.isf(np.array([r["p_value"] for r in records])/2)**2)/chi2.ppf(.5,1))
            supplied={k:v for k,v in zip(FIELDS,["external:pool","pooled","all","complete",2,2,0,value,"descriptive_P_transform",2,"observed_coordinates"])}
            if case=="invalid-supplied-lambda":supplied["testing_coverage"]="selected_only"
            save_table(project/"external-diagnostic.tsv",[supplied]);analysis["supplied_results"].append(external("external-diagnostic","gwas_diagnostics",{"results":"external-diagnostic.tsv"}))
            if case=="provided-sensitivity":analysis["supplied_results"][-1]["role"]="sensitivity"
    study={"schema_version":1,"project":{"id":"diagnostic-"+case,"title":"Synthetic GWAS diagnostics","description":"Software fixture; no biological or AI scientific judgment.","data_policy":{"remote_transmission":"deny_all"}},"resources":{"network":"offline","downloads":"deny","directory":"fixture-resources"},"datasets":datasets,"cohorts":[{"id":i} for i in sorted({d["cohort"] for d in datasets})],"cohort_relationships":[{"between":["A","B"],"type":"independent"}] if case=="meta" else [],"hypotheses":[{"id":"H","description":"Diagnose the original source P values.","outcome":"y","covariates":covariates,"analysis":analysis}]}
    write(project/"study.json",study);write(project/"original-input-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in project.rglob("*") if p.is_file()})
def operations(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p.parent,yaml.safe_load(p.read_text(encoding="utf-8"))) for p in (project/"runs"/status["run_id"]/"operations").glob("*/manifest.yaml")]
def logp(row):
    p=float(row["p_value"]);v=row.get("log_p_value","NA");value=float(v) if v not in {"NA","","NaN","."} else math.nan
    if math.isfinite(value):return value if p==0 or abs(math.log(p)-value)<=max(1e-8,abs(value)*1e-7) else math.nan
    return math.log(p) if p>0 else math.nan
def verify(root,jar):
    pins=json.loads((root/"software.json").read_text(encoding="utf-8"));assert pins=={"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))};assert sha(root/"software/jlinalg.jar")==sha(jar)
    artifacts={};comparisons=0;qq_rows=0;manhattan_rows=0;native_rows=0;receipts=[]
    for case in CASES:
        project=root/case
        for name,digest in json.loads((project/"original-input-hashes.json").read_text(encoding="utf-8")).items():assert sha(project/name)==digest,(case,name)
        if case in INVALID:assert not (project/"state/project-status.yaml").exists();receipts.append({"case":case,"state":"invalid_policy"});continue
        ops=operations(project);diagnostics=[(p,m) for p,m in ops if m["operation_type"]=="gwas_diagnostics"];assert len(diagnostics)==1
        folder,m=diagnostics[0]
        for p,data in ops:
            for name,digest in data.get("output_sha256",{}).items():assert sha(p/name)==digest;artifacts[(p/name).relative_to(root).as_posix()]=digest
        if case=="invalid-supplied-lambda":assert m["state"]=="failed" and not (folder/"gwas-diagnostic-summary.tsv").exists();receipts.append({"case":case,"state":"rejected_inconsistent_source"});continue
        assert m["state"]==("waiting_for_input" if case=="row-bound" else "complete"),(case,m)
        if case=="provided-diagnostics":
            assert m["details"]["analysis_executed_by_jlinalg"] is False;assert not list(folder.glob("*-qq.png"));assert table(folder/"gwas-diagnostic-summary.tsv")[0]["observed_tests"]=="2";receipts.append({"case":case,"state":"source_reused"});continue
        report=yaml.safe_load((folder/"gwas-diagnostics.yaml").read_text(encoding="utf-8"));assert report["source_count"]==(3 if case=="meta" else 1)
        summary=table(folder/"gwas-diagnostic-summary.tsv")
        if case=="row-bound":assert not summary and report["sources"][0]["status"]=="row_bound_exceeded" and m["details"]["method_report"]["performed"] is False;receipts.append({"case":case,"state":"bounded_without_subset"});continue
        for index,source in enumerate(report["sources"],1):
            original=table(folder/source["snapshot"]);valid={r["feature_id"]:r for r in original if math.isfinite(logp(r)) and r.get("status","ok") in {"ok","tested","complete","single_cohort"}}
            prefix=f"source-{index:03d}";source_summary=[r for r in summary if r["source_id"]==source["source_id"]]
            if case=="meta" and source["source_id"].startswith("meta_analysis:"):
                projection=table(folder/(prefix+"-coordinate-projection.tsv"));assert len(projection)==64
                assert all(r["contributing_sources"]=="2" and r["disposition"]=="contributing_coordinate_consensus" for r in projection)
                assert {"log_p_value","minor_allele_count","minor_allele_frequency","imputation_info"}.isdisjoint(projection[0])
                assert source["contributing_coordinate_metadata_sha256"]==sha(folder/source["contributing_coordinate_metadata_snapshot"])
            for group,row in enumerate(source_summary,1):
                stem=f"{prefix}-group-{group:03d}";qq=table(folder/(stem+"-qq.tsv"));n=len(qq)
                assert int(row["diagnostic_tests"])==n
                for rank,q in enumerate(qq,1):
                    assert math.isclose(float(q["expected_neg_log10_uniform_order_mean"]),-math.log10(rank/(n+1)),abs_tol=1e-13)
                    assert math.isclose(float(q["observed_neg_log10_p"]),-logp(valid[q["feature_id"]])/math.log(10),rel_tol=1e-12,abs_tol=1e-13);comparisons+=2;qq_rows+=1
                logs=np.array([logp(valid[q["feature_id"]]) for q in qq])
                if row["lambda_p_chisq1"]!="NA":
                    # Use SciPy's log-tail inverse for values that underflow exp.
                    from scipy.special import ndtri_exp
                    expected=float(np.median(ndtri_exp(logs-math.log(2))**2)/chi2.ppf(.5,1))
                    assert math.isclose(float(row["lambda_p_chisq1"]),expected,rel_tol=1e-9,abs_tol=1e-12),(case,row,expected);comparisons+=1
                if case in {"unknown","selected-meta","unresolved-zero","log-conflict"}:assert row["lambda_p_chisq1"]=="NA"
                if row["stratum"]!="all":continue
                if case=="mixed-build":assert row["coordinate_status"]=="held_mixed_genome_builds" and not (folder/(stem+"-manhattan.png")).exists();continue
                coords=table(folder/(stem+"-manhattan.tsv"));assert len(coords)==int(row["coordinate_tests"])
                offsets=source["coordinate_"+str(group)]["chromosome_offsets"]
                for point in coords:
                    assert float(point["cumulative_position"])==int(point["position"])+offsets[point["chromosome"]]
                    assert math.isclose(float(point["observed_neg_log10_p"]),-logp(valid[point["feature_id"]])/math.log(10),rel_tol=1e-12,abs_tol=1e-13);comparisons+=2;manhattan_rows+=1
                if case=="partial-coordinates":assert row["coordinate_status"]=="partial_coordinates" and len(coords)==63
                if case=="unknown-build":assert row["coordinate_status"]=="observed_coordinates_build_unknown" and source["coordinate_"+str(group)]["unknown_build_tests"]==64
                if case=="display-bounds":assert source["coordinate_"+str(group)]["plotted_points"]==2 and len(coords)==64 and any(r["display_capped"]=="true" for r in coords)
        if case=="native-ols":
            expected=json.loads((project/"expected-native.json").read_text(encoding="utf-8"));source=report["sources"][0];actual=table(folder/source["snapshot"]);assert len(actual)==len(expected)==6
            for row in actual:
                for field,value in expected[row["feature_id"]].items():assert math.isclose(float(row[field]),value,rel_tol=1e-7,abs_tol=1e-12);comparisons+=1
                native_rows+=1
        for p,data in ops:
            if data["operation_type"]=="finding_inventory":
                text=(p/"finding-inventory.jsonl").read_text(encoding="utf-8");assert SECRET not in text
                records=[json.loads(line) for line in text.splitlines()]
                assert records and all(any(o.get("kind")=="gwas_result_diagnostics" for o in r["observations"]) for r in records)
                if case=="provided-sensitivity":assert all(any(o.get("kind")=="gwas_result_diagnostics" and o.get("scientific_role")=="sensitivity" for o in r["observations"]) for r in records)
        assert m.get("reused_from_cache") is True,(case,m)
        cli(jar,project,"verify","--project",project);receipts.append({"case":case,"state":"diagnostics_verified"})
    write(root/"sealed-artifacts.json",artifacts)
    result={"passed":True,"cases":len(CASES),"case_receipts":receipts,"qq_rows":qq_rows,"manhattan_rows":manhattan_rows,"native_association_rows":native_rows,"independent_comparisons":comparisons,"sealed_artifacts":len(artifacts),**pins,"external_requests":0,"real_AI_or_biological_validation":False,"full_pipeline_conformance":False};write(root/"verification.json",result);print(json.dumps(result,indent=2))
if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);a=parser.parse_args();root,jar=a.directory.resolve(),a.jar.resolve()
    if a.action=="run":
        root.mkdir(parents=True,exist_ok=False);(root/"software").mkdir();shutil.copyfile(jar,root/"software/jlinalg.jar");shutil.copyfile(__file__,root/"software/gwas-diagnostics-workflow.py");write(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))});(root/"capabilities.yaml").write_text(cli(jar,root,"operations").stdout,encoding="utf-8")
        for case in CASES:
            project=root/case;generate(project,case);cli(jar,project,"validate",project/"study.json",invalid=case in INVALID)
            if case in INVALID:continue
            cli(jar,project,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture",invalid=case=="invalid-supplied-lambda")
            if case not in {"invalid-supplied-lambda","row-bound"}:cli(jar,project,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
