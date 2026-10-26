"""Offline colocalization acceptance: run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar PINNED_JAR.

All data and source-approval flags are synthetic. Independently enumerates H0-H4
conditional on native SuSiE effect log Bayes factors, checks raw-source coverage,
harmonization, priors, explicit limitations, imports, cache reuse and study verify.
Does not independently validate SuSiE fitting, LD calibration or biological claims.
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
import yaml

MODES=("complete","partial","partial-complete-declarations","unknown-overlap","multi-signal","nonconvergent","coverage-budget","complete-filter-conflict","low-overlap","not-estimable","provided","provided-coverage-unknown","by-family","beyond-context-cap","explicit-feature-cap")
WAITING={"nonconvergent","coverage-budget","complete-filter-conflict","low-overlap","by-family","explicit-feature-cap"}

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
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"));directory=project/"runs"/status["run_id"]/"operations"
    return status,[(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in sorted(directory.iterdir())]
def coloc(project):
    found=[(p,m) for p,m in artifacts(project)[1] if m["operation_type"]=="colocalization"];assert len(found)==1
    return found[0]
def pin(root,jar):
    software=root/"software";software.mkdir();target=software/("jlinalg-"+sha(jar)+".jar");shutil.copyfile(jar,target);shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py")
    write(root/"execution-software.json",{"jar":target.relative_to(root).as_posix(),"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"python_version":sys.version,"pyyaml_version":yaml.__version__})
    return target

def generate(root,mode):
    project=root/mode;project.mkdir();policy={"maximum_effects":1,"shared_prior_sensitivity":[1e-8,1e-6,1e-5],"posterior_threshold":.8,"coverage_bins":10};analysis={"mode":"manual","goals":["colocalization"],"colocalization":policy,"harmonization":{"effect_outliers":{"enabled":False}}}
    study={"schema_version":1,"project":{"id":"coloc-"+mode,"title":"Synthetic colocalization "+mode,"description":"Offline software fixture; no scientific appraisal or real source approval."},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[],"hypotheses":[{"id":"H","description":"Synthetic molecular-GWAS regional evidence.","outcome":"BMI","analysis":analysis}]}
    if mode.startswith("provided"):
        table(project/"provided.tsv",["feature_id","region_id","pp_h4","pp_h3","exposure_variant_match_fraction","outcome_variant_match_fraction"],[["probe1","source-region",.95,.02,.5,.03]])
        definition={"id":"external-coloc","kind":"colocalization","files":{"results":"provided.tsv"},"scope":{"phenotype":"BMI","cohorts":["synthetic-exposure","synthetic-outcome"]},"method":{"name":"Synthetic external method","software":"fixture","version":"1"},"provenance":{"producer":"synthetic-protocol-fixture"},"limitations":["External statistical calculations have not been reconstructed."]}
        if mode=="provided":definition["evidence_grade"]="exploratory_partial_region_major_limitation";definition["limitations"].append("Only selected QTL variants were exported.")
        analysis["supplied_results"]=[definition]
    else:
        n=5 if mode in ("multi-signal","low-overlap") else 3
        features=[("probe1",.02 if mode=="by-family" else 1e-7)]
        if mode=="by-family":features.append(("other",.8));analysis["multiple_testing"]={"method":"BY","alpha":.05}
        if mode in ("beyond-context-cap","explicit-feature-cap"):features.extend(("other-"+str(i),1e-9) for i in range(15))
        if mode=="explicit-feature-cap":policy["max_features"]=10
        table(project/"results.tsv",["feature_id","beta","se","p_value","n"],[[f,.5,.1,p,1000] for f,p in features])
        table(project/"annotation.tsv",["feature_id","gene_symbol","gene_id","chromosome","start","end","genome_build"],[[f,"SYNTH"+str(i),"ENSG"+str(i),"1",100,n*100,"GRCh38"] for i,(f,p) in enumerate(features)])
        columns=["variant_id","chromosome","position","effect_allele","other_allele","beta","se","p_value"]
        x=[];y=[]
        for i in range(1,n+1):
            zx=9+i;zy=8+i;p1=math.erfc(zx/math.sqrt(2));p2=math.erfc(zy/math.sqrt(2));x.append(["rs"+str(i),"1",i*100,"A","G",zx*.01,.01,p1]);y.append(["rs"+str(i),"1",i*100,"G" if i==2 else "A","A" if i==2 else "G",-zy*.01 if i==2 else zy*.01,.01,p2])
        if mode=="partial":
            for i in range(5001):y.append(["extra"+str(i),"1",600,"A","G",.001,.01,.5])
        if mode=="coverage-budget":y.append(["extra","1",600,"A","G",.1,.01,math.erfc(10/math.sqrt(2))]);policy["maximum_regional_variants"]=3
        if mode=="low-overlap":y=y[:2]
        if mode=="not-estimable":y=y[:1]
        table(project/"x.tsv",columns,x);table(project/"y.tsv",columns,y)
        ids=["rs"+str(i) for i in range(1,n+1)]
        table(project/"ld.tsv",["variant_id",*ids],[[id_value,*[int(i==j) for j in range(n)]] for i,id_value in enumerate(ids)])
        table(project/"alleles.tsv",["variant_id","effect_allele","other_allele"],[[id_value,"A","G"] for id_value in ids])
        files={"results":"results.tsv","annotation":"annotation.tsv","exposure":"x.tsv","outcome":"y.tsv","ld":"ld.tsv","alleles":"alleles.tsv"}
        def source(role):return {"dataset_id":"a","file_role":role,"source_id":"synthetic-"+role,"citation":"Synthetic protocol fixture, not biological evidence.","genome_build":"GRCh38","ancestry":"European","sample_size":10000,"complete_region":True,"feature_id":"probe1","trait":"BMI" if role=="outcome" else "splicing","tissue":"blood","assay":"sQTL"}
        pair={"id":"pair","feature_id":"probe1","approved":True,"rationale":"Synthetic software acceptance only.","sample_overlap":"none","sample_overlap_basis":"Separate simulated summaries; no human attestation.","region":{"chromosome":"1","start":1,"end":1000},"exposure":source("exposure"),"outcome":source("outcome"),"ld":dict(source("ld"),alleles_file_role="alleles")}
        if mode in ("partial","not-estimable"):pair["partial_colocalization"]=True;pair["exposure"].update(complete_region=False,export_p_threshold=1e-7,selection_basis="synthetic_significance_filtered_export")
        if mode in ("partial-complete-declarations","unknown-overlap"):pair["partial_colocalization"]=True
        if mode=="unknown-overlap":pair["sample_overlap"]="unknown"
        if mode=="complete-filter-conflict":pair["exposure"]["export_p_threshold"]=1e-7
        if mode=="multi-signal":policy["maximum_effects"]=5
        if mode=="nonconvergent":policy.update(maximum_iterations=1,maximum_effects=3)
        study["datasets"]=[{"id":"a","cohort":"synthetic","type":"transcriptomics","stage":"association_results","species":"human","genome_build":"GRCh38","annotation_release":"synthetic-v1","coordinate_system":"1-based-closed","files":files}]
        study["hypotheses"][0]["inputs"]={"discovery":["a"]};analysis["bioinformatics"]={"genetic_pairs":[pair]}
    write(project/"study.yaml",study);write(project/"input-provenance.json",{p.name:sha(p) for p in project.iterdir() if p.is_file()});return project

def close(actual,expected,context):
    assert math.isclose(float(actual),expected,rel_tol=2e-9,abs_tol=2e-12),(context,actual,expected)
def lse(values):
    values=list(values)
    if not values:return -math.inf
    maximum=max(values);return maximum+math.log(sum(math.exp(v-maximum) for v in values))
def posterior(x,y,p1,p2,p12):
    ids=sorted(x);h=[0,math.log(p1)+lse(x.values()),math.log(p2)+lse(y.values()),math.log(p1*p2)+lse(x[a]+y[b] for a in ids for b in ids if a!=b),math.log(p12)+lse(x[a]+y[a] for a in ids)];denominator=lse(h);return [math.exp(v-denominator) for v in h]
def effects(path):
    result={}
    for row in rows(path):result.setdefault(row["effect_index"],{})[row["variant_id"]]=float(row["log_bayes_factor"])
    return result
def verify_case(project,mode):
    for name,digest in json.loads((project/"input-provenance.json").read_text()).items():assert sha(project/name)==digest
    for folder,manifest in artifacts(project)[1]:
        assert manifest["state"]!="failed",(mode,manifest)
        for name,digest in manifest["output_sha256"].items():assert sha(folder/name)==digest
    folder,manifest=coloc(project);assert manifest["state"]==("waiting_for_input" if mode in WAITING else "complete"),(mode,manifest["state"],manifest.get("summary"))
    if mode.startswith("provided"):
        assert manifest["details"]["analysis_executed_by_jlinalg"] is False
        values=rows(folder/"colocalization-results.tsv");assert len(values)==1;close(values[0]["pp_h4"],.95,mode);close(values[0]["outcome_variant_match_fraction"],.03,mode)
        assert not list(folder.rglob("*prior-grid*")) and not list(folder.rglob("*fine-mapping*"))
        summary=yaml.safe_load((folder/"genetic-evidence.yaml").read_text(encoding="utf-8"));assert summary["evidence_grade"]==("exploratory_partial_region_major_limitation" if mode=="provided" else "source_coverage_unverified")
        return {"supplied_result_reused":True,"source_coverage_grade":summary["evidence_grade"]}
    summary=yaml.safe_load((folder/"genetic-evidence.yaml").read_text(encoding="utf-8"));pair=summary["pairs"][0];canonical=rows(folder/"colocalization-results.tsv")
    if mode in WAITING:
        assert not canonical and not (folder/"pair/colocalization.tsv").exists()
        if mode=="nonconvergent":assert pair["effective_parameters"]["exposure_fine_mapping"]["converged"] is False;assert pair["effective_parameters"]["exposure_fine_mapping"]["attempts"]
        return {"posterior_withheld":True,"reason":pair["reason"]}
    x=rows(project/"x.tsv");y=rows(project/"y.tsv");coverage=pair["regional_coverage"];aligned={r["variant_id"] for r in x}&{r["variant_id"] for r in y};assert coverage["exposure"]["observed_regional_variants"]==len(x);assert coverage["outcome"]["observed_regional_variants"]==len(y)
    close(coverage["exposure_observed_match_fraction"],len(aligned)/len(x),mode);close(coverage["outcome_observed_match_fraction"],len(aligned)/len(y),mode)
    assert len(rows(folder/"pair/outcome-regional-variants.tsv"))==len(y)
    if mode=="not-estimable":assert pair["status"]=="not_estimable" and not canonical;return {"not_estimable_not_null":True,"aligned":len(aligned)}
    assert pair["status"]=="complete" and canonical
    if mode=="partial":assert pair["outcome_variants"]==5004 and pair["outcome_retained_for_alignment"]==3;close(pair["outcome_candidate_match_fraction"],1,mode)
    audit=rows(folder/"pair/harmonization.tsv");expected_x={r["variant_id"]:float(r["beta"])/float(r["se"]) for r in x};expected_y={r["variant_id"]:(-1 if r["effect_allele"]=="G" else 1)*float(r["beta"])/float(r["se"]) for r in y}
    for row in audit:assert row["right_sign"]==("-1" if row["variant_id"]=="rs2" else "1")
    for source,expected in (("exposure",expected_x),("outcome",expected_y)):
        for row in rows(folder/("pair/"+source+"-z.tsv")):close(row["z"],expected[row["variant_id"]],(mode,source,"aligned z"))
    fx=effects(folder/"pair/exposure.effects.tsv");fy=effects(folder/"pair/outcome.effects.tsv");policy=summary["colocalization_review"]["registered_policy"];grid=rows(folder/"pair/colocalization-prior-grid.tsv");ranges={}
    for row in grid:
        a=row["trait1_effect_index"];b=row["trait2_effect_index"];expected=posterior(fx[a],fy[b],policy["trait1_prior"],policy["trait2_prior"],float(row["shared_prior"]))
        for i in range(5):close(row["pp_h"+str(i)],expected[i],(mode,a,b,i))
        ranges.setdefault((a,b),[]).append(float(row["pp_h4"]))
    for row in canonical:
        key=(row["trait1_effect_index"],row["trait2_effect_index"]);values=ranges[key];assert len(values)==4;close(row["pp_h4_min"],min(values),mode);close(row["pp_h4_max"],max(values),mode);close(row["shared_prior"],5e-6,mode)
        classification="above_registered_threshold_all_priors" if min(values)>=.8 else "below_registered_threshold_all_priors" if max(values)<.8 else "crosses_registered_threshold";assert row["prior_stability"]==classification
        close(row["outcome_variant_match_fraction"],len(aligned)/len(y),mode);assert row["exposure_converged"]==row["outcome_converged"]=="true"
        partial=mode in ("partial","partial-complete-declarations","unknown-overlap");assert row["evidence_grade"]==("exploratory_partial_region_major_limitation" if partial else "regional_analysis_with_declared_complete_sources")
        assert "does not establish causal direction" in row["required_manuscript_caveat"]
        if partial:assert "not evidence against a shared causal variant" in row["required_manuscript_caveat"]
    if mode=="multi-signal":assert len(canonical)==25 and len(pair["colocalization"])==20
    if mode=="beyond-context-cap":assert summary["colocalization_review"]["eligible_mapped_features"]==16 and summary["colocalization_review"]["unrequested_features"]==15
    return {"signal_pairs":len(canonical),"independent_prior_rows":len(grid),"observed_source_counts":[len(x),len(y)],"partial":mode in ("partial","partial-complete-declarations","unknown-overlap")}

def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text());assert sha(jar)==software["jar_sha256"]==sha(root/software["jar"]);assert sha(Path(__file__))==software["verifier_sha256"]==sha(root/"workflow-verifier-source.py"),"Invoke the exact pinned verifier source"
    results=[{"case":mode,"evidence":verify_case(root/mode,mode)} for mode in MODES];report={"passed":True,"cases":results,"jar_sha256":sha(jar),"verifier_sha256":software["verifier_sha256"],"scope":"Synthetic offline workflow. Independent H0-H4 enumeration conditions on native SuSiE effect log Bayes factors; source coverage, harmonization and limitations are checked. SuSiE parity, LD calibration, real public acquisition, AI appraisal and biological causality are not verified."};write(root/"verification.json",report);return results
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir();jar=pin(root,jar)
        for mode in MODES:
            project=generate(root,mode);(project/"execution.log").write_text(cli(jar,"auto",project/"study.yaml","--offline","--actor-type","external","--actor-id","synthetic-colocalization-fixture"),encoding="utf-8")
            if mode not in WAITING:
                assert artifacts(project)[0]["state"]=="completed",(mode,artifacts(project)[0])
                (project/"replay.log").write_text(cli(jar,"auto",project/"study.yaml","--offline","--actor-type","external","--actor-id","synthetic-colocalization-fixture"),encoding="utf-8");assert all(m["reused_from_cache"] is True for p,m in artifacts(project)[1])
            (project/"verify.log").write_text(cli(jar,"verify","--project",project),encoding="utf-8")
    results=verify(root,jar);print(json.dumps({"passed":True,"cases":len(results),"directory":str(root),"jar_sha256":sha(jar)}))
if __name__=="__main__":main()
