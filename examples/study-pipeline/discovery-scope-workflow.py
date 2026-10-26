"""Offline supplied-pool scope, source-only cohorts and missing validation.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR
All data, contracts and approvals are synthetic software fixtures.
Requires numpy/scipy/PyYAML and the sibling frozen-discovery-workflow.py.
"""
import argparse
import importlib.util
import json
import math
from pathlib import Path
import shutil
import subprocess
import numpy as np
from scipy.stats import t as student_t
import yaml

helper_path=Path(__file__).with_name("frozen-discovery-workflow.py")
spec=importlib.util.spec_from_file_location("frozen_discovery_fixture",helper_path)
helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
sha,write,table,records,adjust=helper.sha,helper.write,helper.table,helper.records,helper.adjust
CASES=("references","cohort-only","native-validation","pool-includes-validation","wrong-cohorts","wrong-trait","missing-trait",
       "known-overlap","same-cohort","pending-independence","units-conflict","unit-case-conflict","strict-missing","strict-complete",
       "tissue-exception","alleles-match","alleles-conflict","build-conflict","all-meta-needs-cohort-estimates",
       "missing-import","reference-files","unbound-reference","missing-membership","duplicate-membership")
INVALID={"reference-files","unbound-reference","missing-membership","duplicate-membership"}
COMPLETE={"references","cohort-only","native-validation","strict-complete","tissue-exception","alleles-match","all-meta-needs-cohort-estimates"}


def contract(units="trait per feature unit"):
    return {"estimand":"adjusted association","orientation":"feature to trait","effect_scale":"linear beta","effect_units":units,
            "phenotype_transformation":"identity","feature_transformation":"identity"}


def cli(jar,project,*args,invalid=False):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    with (project/"commands.jsonl").open("a",encoding="utf-8") as out:
        out.write(json.dumps({"args":list(map(str,args)),"returncode":result.returncode,"stdout":result.stdout,"stderr":result.stderr})+"\n")
    assert (result.returncode!=0) if invalid else (result.returncode==0),(project,result.stdout,result.stderr)
    return result


def generate(project,case):
    project.mkdir();native=case=="native-validation";genetic=case in {"alleles-match","alleles-conflict","build-conflict"}
    scope={"datasets":["a","b"],"cohorts":["A","B"],"phenotype":"y" if native else "synthetic"}
    # These two exported BH values are consistent with 399,998 omitted null P=1 tests.
    pool="feature_id\tbeta\tse\tp_value\tadjusted_p"+("\tchromosome\tposition\treference_allele\talternate_allele\teffect_allele\tother_allele\tgenome_build" if genetic else "")+"\n"
    for i,p,q in ((1,.000002,.4),(2,.0000001,.04)):
        pool+=f"g{i}\t.4\t.1\t{p}\t{q}"+(f"\t1\t{100+i}\tA\tG\tG\tA\tGRCh38" if genetic else "")+"\n"
    (project/"pool.tsv").write_text(pool,encoding="utf-8")
    header="feature_id\tbeta\tse\tp_value"+("\tchromosome\tposition\treference_allele\talternate_allele\teffect_allele\tother_allele\tgenome_build" if genetic else "")+"\n"
    validation=header
    for i,p in ((1,.001),(2,.002)):
        flipped=case=="alleles-conflict" and i==2
        validation+=f"g{i}\t{'-.3' if flipped else '.3'}\t.1\t{p}"+(f"\tchr1\t{100+i}\tA\tG\t{'A' if flipped else 'G'}\t{'G' if flipped else 'A'}\tGRCh38" if genetic else "")+"\n"
    (project/"validation.tsv").write_text(validation,encoding="utf-8")
    kind="gwas" if genetic else "generic"
    datasets=[{"id":id,"cohort":id.upper(),"type":kind,"stage":"analysis_reference","supplied_result_id":"pool","files":{}} for id in ("a","b")]
    datasets.append({"id":"v","cohort":"V","type":kind,"stage":"association_results","files":{"results":"validation.tsv"}})
    analysis={"mode":"manual","goals":["discovery_replication"],"replication":{"adjustment_scope":"discovery_subset"},
              "harmonization":{"effect_outliers":{"enabled":False}}}
    relationships=[{"between":["A","B","V"],"type":"independent"}]
    cohort_ids=["A","B","V"]
    if case=="cohort-only":del scope["datasets"]
    if case=="pool-includes-validation":scope.update(datasets=["a","b","v"],cohorts=["A","B","V"])
    if case=="wrong-cohorts":scope["cohorts"]=["A","V"]
    if case=="wrong-trait":scope["phenotype"]="other_trait"
    if case=="missing-trait":del scope["phenotype"]
    if case=="known-overlap":relationships=[{"between":["A","V"],"type":"overlapping","overlap_fraction":.1}]
    if case=="same-cohort":datasets[2]["cohort"]="A"
    if case=="pending-independence":relationships=[]
    if case in {"units-conflict","unit-case-conflict","strict-missing","strict-complete"}:
        registered=contract("MM" if case=="unit-case-conflict" else "trait per feature unit")
        analysis["pooling"]={"effect_contract":registered,"require_complete_contract":case.startswith("strict-")}
        if case!="strict-missing":scope["effect_contract"]=contract("mM" if case=="unit-case-conflict" else "incompatible units" if case=="units-conflict" else "trait per feature unit")
    if case=="tissue-exception":
        scope["tissue"]="liver";datasets[0]["tissue"]=datasets[1]["tissue"]="liver";datasets[2]["tissue"]="blood"
        analysis["pooling"]={"context_exceptions":{"tissue":"Synthetic registered cross-tissue validation context; no biological transport claim."}}
    if genetic:
        scope.update(modality="genomics",genome_build="GRCh38")
        for d in datasets:d["genome_build"]="GRCh38"
        if case=="build-conflict":scope["genome_build"]="GRCh37"
    if case=="all-meta-needs-cohort-estimates":
        (project/"validation2.tsv").write_text("feature_id\tbeta\tse\tp_value\ng1\t.2\t.1\t.003\ng2\t.2\t.1\t.004\n",encoding="utf-8")
        datasets.append({"id":"v2","cohort":"V2","type":"generic","stage":"association_results","files":{"results":"validation2.tsv"}})
        cohort_ids.append("V2");relationships=[{"between":cohort_ids,"type":"independent"}];analysis["goals"].append("meta_analysis")
    if case=="reference-files":datasets[0]["files"]={"results":"pool.tsv"}
    if case=="unbound-reference":datasets[0]["supplied_result_id"]="absent-pool"
    if case=="missing-membership":scope.pop("datasets");scope.pop("cohorts")
    if case=="duplicate-membership":scope["datasets"]=["a","b","a"]
    expected_native={}
    if native:
        analysis["goals"].insert(0,"association");analysis["association"]={"model":"ols"}
        n=48;i=np.arange(n,dtype=float);x1=np.sin(i*.37)+i*.01;x2=np.cos(i*.23)-i*.02;y=.5+.6*x1+.15*np.cos(i*.71)
        (project/"matrix.tsv").write_text("feature_id\t"+"\t".join(f"S{k}" for k in range(n))+"\n"+"".join(name+"\t"+"\t".join(map(str,x))+"\n" for name,x in (("g1",x1),("g2",x2))),encoding="utf-8")
        (project/"phenotype.tsv").write_text("id\ty\n"+"".join(f"S{k}\t{value}\n" for k,value in enumerate(y)),encoding="utf-8")
        datasets[2]={"id":"v","cohort":"V","type":"generic","stage":"processed_matrix","files":{"matrix":"matrix.tsv","phenotype":"phenotype.tsv"},"columns":{"sample_id":"id"}}
        for name,x in (("g1",x1),("g2",x2)):
            design=np.column_stack((np.ones(n),x));coefficient=np.linalg.lstsq(design,y,rcond=None)[0];residual=y-design@coefficient
            se=math.sqrt(float(residual@residual)/(n-2)*np.linalg.inv(design.T@design)[1,1]);statistic=coefficient[1]/se
            expected_native[name]={"beta":float(coefficient[1]),"se":se,"p_value":float(2*student_t.sf(abs(statistic),n-2)),"n":n}
    supplied={"id":"pool","kind":"discovery_meta_analysis","files":{"results":"missing.tsv" if case=="missing-import" else "pool.tsv"},"scope":scope,
              "method":{"name":"Synthetic external discovery pool"},"family":{"id":"external-family","coverage":"selected_only","tested_count":400000,"adjustment":"BH","adjusted_p_from_full_family":True},
              "limitations":["Synthetic software fixture; original scientific validity is not certified."]}
    analysis["supplied_results"]=[supplied]
    study={"schema_version":1,"project":{"id":case,"title":"Source-only discovery fixture","description":"Synthetic scope and missing-work acceptance.","objective":"validation"},
           "resources":{"network":"offline","downloads":"deny","directory":"fixture-resources"},"cohorts":[{"id":c} for c in cohort_ids],"cohort_relationships":relationships,"datasets":datasets,
           "hypotheses":[{"id":"H","description":"Validate frozen supplied discovery with source-only cohort metadata.","outcome":"y" if native else "synthetic",
                          "inputs":{"discovery":["a","b"],"replication":["v","v2"] if case=="all-meta-needs-cohort-estimates" else ["v"]},"analysis":analysis}]}
    write(project/"study.json",study);write(project/"expected-native.json",expected_native)
    write(project/"original-input-hashes.json",{p.name:sha(p) for p in project.iterdir() if p.is_file()})


def verify(root,jar):
    software=json.loads((root/"software.json").read_text(encoding="utf-8"))
    assert software["jar_sha256"]==sha(jar)==sha(root/"software/jlinalg.jar")
    assert software["verifier_sha256"]==sha(Path(__file__))==sha(root/"software/discovery-scope-workflow.py")
    assert software["helper_sha256"]==sha(helper_path)==sha(root/"software/frozen-discovery-workflow.py")
    capabilities=yaml.safe_load((root/"capabilities.yaml").read_text(encoding="utf-8"))
    assert "analysis_reference" in capabilities["validate_harmonize"]["accepted_stages"]
    assert "analysis_reference" in capabilities["discovery_replication"]["accepted_stages"]
    assert "analysis_reference" not in capabilities["meta_analysis"]["accepted_stages"]
    artifacts={};comparisons=replication_rows=association_rows=0;case_receipts=[]
    for case in CASES:
        project=root/case;study=json.loads((project/"study.json").read_text(encoding="utf-8"))
        for name,digest in json.loads((project/"original-input-hashes.json").read_text(encoding="utf-8")).items():assert sha(project/name)==digest
        assert not (project/"a.tsv").exists() and not (project/"b.tsv").exists()
        if case in INVALID:
            assert not list((project/"runs").glob("*/operations/*/manifest.yaml"))
            log=[json.loads(line) for line in (project/"commands.jsonl").read_text(encoding="utf-8").splitlines()]
            assert any(row["args"][0]=="validate" and row["returncode"]!=0 for row in log)
            case_receipts.append({"case":case,"disposition":"invalid_reference_or_scope"});continue
        operations=records(project)
        assert not any(m["operation_type"]=="association" and "standard-results/v.tsv" not in m.get("output_sha256",{}) for _,m in operations)
        for folder,m in operations:
            for name,digest in m.get("output_sha256",{}).items():assert sha(folder/name)==digest;artifacts[str((folder/name).relative_to(root))]=digest
        replication=[(p,m) for p,m in operations if m["operation_type"]=="discovery_replication"];assert len(replication)==1
        folder,m=replication[0]
        if case not in COMPLETE:
            assert m["state"]=="waiting_for_input" and not (folder/"replication.tsv").exists(),(case,m)
            case_receipts.append({"case":case,"disposition":"held_without_replication_selection"});continue
        assert m["state"]=="complete",(case,m)
        source=[(p,x) for p,x in operations if x["operation_type"]=="discovery_meta_analysis"];assert len(source)==1
        pool,imported=source[0];assert imported["details"]["analysis_executed_by_jlinalg"] is False and imported.get("reused_from_cache") is True
        assert sha(pool/"supplied/results.tsv")==sha(project/"pool.tsv")
        harmonize=[p for p,x in operations if x["operation_type"]=="validate_harmonize"];assert len(harmonize)==1
        references=table(harmonize[0]/"analysis-references.tsv");assert {r["dataset_id"] for r in references}=={"a","b"}
        assert not any((harmonize[0]/f"standard-results/{id}.tsv").exists() for id in ("a","b"))
        scope=yaml.safe_load((folder/"discovery-scope-audit.yaml").read_text(encoding="utf-8"));assert scope["external_scope_checked"] is True and scope["issues"]==[]
        assert scope["source_discovery_cohorts"]==["A","B"] and scope["validation_cohorts"]==(["V","V2"] if case=="all-meta-needs-cohort-estimates" else ["V"])
        expected_native=json.loads((project/"expected-native.json").read_text(encoding="utf-8"))
        if expected_native:
            associations=[(p,x) for p,x in operations if x["operation_type"]=="association"];assert len(associations)==1
            af,am=associations[0];assert am["state"]=="complete" and am.get("reused_from_cache") is True
            assert am["details"]["method_report"]["parameters"]["dataset"]=="v"
            prefit=[p for p,x in operations if x["operation_type"]=="participant_qc" and x["state"]=="complete"];assert len(prefit)==1
            assert len(table(prefit[0]/"analysis-phenotype.tsv"))==48;comparisons+=1
            actual=table(af/"standard-results/v.tsv");assert {r["feature_id"] for r in actual}==set(expected_native)
            for row in actual:
                for field,target in expected_native[row["feature_id"]].items():
                    observed=float(row["df_denominator"])+2 if field=="n" else float(row[field])
                    assert math.isclose(observed,target,rel_tol=1e-7,abs_tol=1e-12),(row,field,target);comparisons+=1
                association_rows+=1
            validation_p={key:value["p_value"] for key,value in expected_native.items()}
        else:assert not any(x["operation_type"] in ("association","participant_qc") for _,x in operations);validation_p={"g1":.001,"g2":.002}
        for row in table(folder/"replication.tsv"):
            key=row["feature_id"];selected=key=="g2";right=({"g1":.003,"g2":.004} if row["replication_dataset"]=="v2" else validation_p)
            target=right[key] if selected else math.nan;actual=math.nan if row["replication_adjusted_p"]=="NA" else float(row["replication_adjusted_p"])
            assert math.isclose(float(row["discovery_adjusted_p"]),.04 if selected else .4,rel_tol=0,abs_tol=1e-15)
            assert math.isnan(actual) if math.isnan(target) else math.isclose(actual,target,rel_tol=1e-7,abs_tol=1e-12)
            assert row["discovery_selected"]==str(selected).lower()
            same=expected_native[key]["beta"]>0 if expected_native else True
            assert row["same_sign"]==str(same).lower()
            assert row["replicated"]==str(selected and target<.05 and same).lower()
            comparisons+=5;replication_rows+=1
        assert m.get("reused_from_cache") is True
        method=m["details"]["method_report"];assert method["source_scope_checked"] is True and method["discovery_scope_status"]==scope["status"]
        if case=="all-meta-needs-cohort-estimates":
            metas=[(p,x) for p,x in operations if x["operation_type"]=="meta_analysis"];assert len(metas)==1
            assert metas[0][1]["state"]=="waiting_for_input" and not (metas[0][0]/"meta-analysis.tsv").exists()
        if case=="alleles-match":assert scope["shared_variant_rows"]==2 and scope["compared_variant_fields"]==14
        cli(jar,project,"verify","--project",project)
        case_receipts.append({"case":case,"disposition":"missing_validation_completed","scope_status":scope["status"]})
    assert association_rows==2 and replication_rows==16
    write(root/"sealed-artifacts.json",artifacts)
    result={"passed":True,"cases":len(CASES),"case_receipts":case_receipts,"completed_validation_cases":len(COMPLETE),"invalid_cases":len(INVALID),"held_cases":len(CASES)-len(COMPLETE)-len(INVALID),
            "replication_rows":replication_rows,"native_association_rows":association_rows,"independent_comparisons":comparisons,"sealed_artifacts":len(artifacts),
            "jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"helper_sha256":sha(helper_path),"external_requests":0,"scientific_appraisal_executed":False,"full_pipeline_conformance":False}
    write(root/"verification.json",result);print(json.dumps(result,indent=2))


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("action",choices=("run","verify"));p.add_argument("directory",type=Path);p.add_argument("--jar",type=Path,required=True);args=p.parse_args()
    root,jar=args.directory.resolve(),args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False);(root/"software").mkdir();shutil.copyfile(jar,root/"software/jlinalg.jar")
        shutil.copyfile(__file__,root/"software/discovery-scope-workflow.py");shutil.copyfile(helper_path,root/"software/frozen-discovery-workflow.py")
        write(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"helper_sha256":sha(helper_path)})
        capabilities=cli(jar,root,"operations")
        (root/"capabilities.yaml").write_text(capabilities.stdout,encoding="utf-8")
        for case in CASES:
            project=root/case;generate(project,case)
            cli(jar,project,"validate",project/"study.json","--check-files",invalid=case in INVALID)
            if case in INVALID:continue
            cli(jar,project,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
            if case in COMPLETE:cli(jar,project,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
