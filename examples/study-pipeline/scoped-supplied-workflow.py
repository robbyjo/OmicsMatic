"""Offline scientific-scope and partial supplied-network acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR
Requires NumPy, SciPy and PyYAML. All source data and GO sets are synthetic.
No external analysis, AI judgment or biological validity is reproduced.
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
from scipy.linalg import hadamard
from scipy.stats import hypergeom
import yaml

CASES=("partial", "cohort-scope", "raw-covered", "pooled", "all-provided", "supplied-only",
       "unknown-coverage", "partial-source", "overlapping-graphs", "private-meta", "wrong-trait",
       "wrong-population", "wrong-units", "row-build", "parent-trait", "parent-row-build",
       "missing-parent", "external-mr", "related-score", "undeclared-related")
NETWORK=set(CASES[:9])
INVALID={"overlapping-graphs"}
HELD={"unknown-coverage", "partial-source", "wrong-trait", "wrong-population", "wrong-units",
      "row-build", "parent-trait", "parent-row-build", "undeclared-related"}
SECRET="PRIVATE_SOURCE_SENTINEL"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path,value):
    path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")

def table(path):
    with path.open(encoding="utf-8",newline="") as stream:
        return list(csv.DictReader(stream,delimiter="\t"))

def cli(jar,project,*args,invalid=False):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    with (project/"commands.jsonl").open("a",encoding="utf-8") as stream:
        stream.write(json.dumps({"args":list(map(str,args)),"returncode":result.returncode,"stdout":result.stdout,"stderr":result.stderr})+"\n")
    assert (result.returncode!=0) if invalid else (result.returncode==0),(project,result.stdout,result.stderr)
    return result

def supplied(identity,kind,files,scope,count=None):
    result={"id":identity,"kind":kind,"files":files,"scope":scope,"method":{"name":"External synthetic method"}}
    if count is not None:
        result["family"]={"id":identity+"-family","coverage":"complete","tested_count":count,"adjustment":"BH"}
    return result

def effect(units):
    return {"estimand":"adjusted association","orientation":"feature to trait","effect_scale":"linear beta",
            "effect_units":units,"phenotype_transformation":"identity","feature_transformation":"identity"}

def generate(project,case):
    project.mkdir();datasets=[];sources=[];goals=[];network={};covariates=[]
    annotation="feature_id\tgene_symbol\n"+"".join(f"g{i}\tA{i+1}\n" for i in range(30))
    (project/"annotation.tsv").write_text(annotation,encoding="utf-8")
    analysis={"mode":"manual","goals":goals,"supplied_results":sources}
    if case in NETWORK:
        resources=project/"fixture-resources/enrichment/go-human";resources.mkdir(parents=True)
        (resources/"data.gmt").write_text("".join(f"GO:{t+1}\tSynthetic term\t"+"\t".join(f"A{i+1}" for i in range(t*10,t*10+10))+"\n" for t in range(3)),encoding="utf-8")
        (resources/"manifest.properties").write_text("format_version=1\nprovider=GO\nspecies=human\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+sha(resources/"data.gmt")+"\n",encoding="utf-8")
        goals.extend(["network_modules","module_gene_ontology"]);covariates=["sex"]
        network.update(minimum_samples=8,correlation="pearson",threshold=.7,thresholds=[.6,.7,.8],categorical_covariates=["sex"],
                       preprocessing="Synthetic complete orthogonal vectors; no biological normalization claimed.",independent_samples=True,go_min_size=1)
        analysis["network"]=network
        basis=hadamard(32);z=basis[:,26];x=basis[:,1]
        vectors=np.array([10*z+(i+1)*(1 if i<3 else -1)*x if i<6 else 5*z+basis[:,i-4] for i in range(30)])
        (project/"matrix.tsv").write_text("feature_id\t"+"\t".join(f"s{i}" for i in range(32))+"\n"+"".join(f"g{i}\t"+"\t".join(map(str,v))+"\n" for i,v in enumerate(vectors)),encoding="utf-8")
        (project/"pheno.tsv").write_text("id\tsex\n"+"".join(f"s{i}\t{'F' if z[i]>0 else 'M'}\n" for i in range(32)),encoding="utf-8")
        (project/"nodes.tsv").write_text("node_id\n"+"".join(f"g{i}\n" for i in range(30)),encoding="utf-8")
        (project/"edges.tsv").write_text("source\ttarget\tweight\n"+"".join(f"g{i}\tg{j}\t{.8 if (i<3)==(j<3) else -.8}\n" for i,j in itertools.combinations(range(6),2)),encoding="utf-8")
        (project/"modules.tsv").write_text("node_id\tmodule_id\n"+"".join(f"g{i}\tM1\n" for i in range(6)),encoding="utf-8")
        scope={"phenotype":"index_trait","species":"human","datasets":["a"],"cohorts":["A"]}
        files={"nodes":"nodes.tsv","edges":"edges.tsv","modules":"modules.tsv","feature_annotation":"annotation.tsv"}
        sources.append(supplied("graph-a","network_analysis",files,scope))
        sources[0]["method"]["edge_direction"]="undirected"
        datasets=[{"id":"a","cohort":"A","type":"expression","stage":"analysis_reference","species":"human","supplied_result_id":"graph-a","files":{}},
                  {"id":"b","cohort":"B","type":"expression","stage":"processed_matrix","species":"human","files":{"matrix":"matrix.tsv","phenotype":"pheno.tsv","annotation":"annotation.tsv"}}]
        if case=="cohort-scope":scope.pop("datasets")
        if case=="raw-covered":datasets[0]={**datasets[1],"id":"a","cohort":"A"}
        if case=="pooled":
            scope["datasets"].append("c");scope["cohorts"].append("C");datasets.append({**datasets[0],"id":"c","cohort":"C"})
        if case in {"all-provided","overlapping-graphs"}:
            second_scope={**scope,"datasets":["b"],"cohorts":["B"]} if case=="all-provided" else dict(scope)
            sources.append(supplied("graph-b","network_analysis",dict(files),second_scope))
            sources[-1]["method"]["edge_direction"]="undirected"
            if case=="all-provided":datasets[1]={**datasets[0],"id":"b","cohort":"B","supplied_result_id":"graph-b"}
        if case=="supplied-only":network["complete_missing"]=False
        if case=="unknown-coverage":
            scope.pop("datasets");scope.pop("cohorts");datasets=datasets[1:]
        if case=="partial-source":sources[0]["reported_status"]="partial"
    else:
        (project/"meta.tsv").write_text("feature_id\tbeta\tse\tp_value\tgenome_build\ng0\t.4\t.1\t.001\tGRCh37\n",encoding="utf-8")
        meta=supplied("meta","meta_analysis",{"results":"meta.tsv","feature_annotation":"annotation.tsv"},{"phenotype":"index_trait"},1)
        sources.append(meta)
        if case=="private-meta":
            meta["method"]["parameters"]={"note":SECRET};meta["scope"]["population"]=SECRET
            meta["provenance"]={"note":SECRET};meta["limitations"]=[SECRET];meta["family"]["rationale"]=SECRET
            goals.append("impact_assessment")
        if case=="wrong-trait":meta["scope"]["phenotype"]="other_trait"
        if case in {"wrong-population","wrong-units"}:
            datasets=[{"id":i,"cohort":i.upper(),"type":"generic","stage":"analysis_reference","supplied_result_id":"meta","files":{}} for i in ("a","b")]
            meta["scope"].update(datasets=["a","b"],cohorts=["A"] if case=="wrong-population" else ["A","B"])
            if case=="wrong-units":analysis["pooling"]={"effect_contract":effect("MM")};meta["scope"]["effect_contract"]=effect("mM")
        if case in {"row-build","parent-row-build"}:meta["scope"]["genome_build"]="GRCh38"
        if case in {"parent-trait","parent-row-build","missing-parent"}:
            (project/"go.tsv").write_text("term_id\tp_value\nGO:1\t.01\n",encoding="utf-8")
            go=supplied("go","gene_ontology",{"results":"go.tsv"},{"phenotype":"index_trait"},1);go["depends_on"]=["meta"];sources.append(go)
            if case=="parent-trait":meta["scope"]["phenotype"]="other_trait"
            if case=="missing-parent":meta["files"]["results"]="unavailable-source.tsv"
        if case=="external-mr":
            sources[:]=[supplied("mr","mendelian_randomization",{"results":"meta.tsv"},{"outcome":"index_trait","cohorts":["EXTERNAL_GWAS"]},1)]
        if case in {"related-score","undeclared-related"}:
            (project/"related.tsv").write_text("score_id\tphenotype\tevaluation_population\tbeta\tse\tp_value\ns1\t"+("unknown_trait" if case=="undeclared-related" else "related_trait")+"\tEXTERNAL_SCORE\t.2\t.1\t.04\n",encoding="utf-8")
            sources[:]=[supplied("related","risk_score_related_phenotypes",{"results":"related.tsv"},{"phenotype":"related_trait","cohorts":["EXTERNAL_SCORE"]},1)]
            analysis["risk_score"]={"related_phenotypes":[{"column":"related_trait"}]}
    study={"schema_version":1,"project":{"id":"scope-"+case,"title":"Synthetic supplied scope","description":"Software fixture only; no scientific result.","data_policy":{"remote_transmission":"deny_all"}},
           "resources":{"network":"offline","downloads":"deny","directory":"fixture-resources"},"datasets":datasets,
           "cohorts":[{"id":i} for i in sorted({d["cohort"] for d in datasets})],
           "hypotheses":[{"id":"H","description":"Reuse scoped source artifacts and complete missing work.","outcome":"index_trait","covariates":covariates,"analysis":analysis}]}
    write(project/"study.json",study)
    write(project/"original-input-hashes.json",{p.relative_to(project).as_posix():sha(p) for p in project.rglob("*") if p.is_file()})

def records(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p.parent,yaml.safe_load(p.read_text(encoding="utf-8"))) for p in (project/"runs"/status["run_id"]/"operations").glob("*/manifest.yaml")]

def verify(root,jar):
    pins=json.loads((root/"software.json").read_text(encoding="utf-8"))
    assert pins=={"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))}
    assert sha(root/"software/jlinalg.jar")==sha(jar) and sha(root/"software/scoped-supplied-workflow.py")==sha(Path(__file__))
    comparisons=0;native_edges=0;go_rows=0;artifacts={};case_receipts=[]
    capabilities=yaml.safe_load((root/"capabilities.yaml").read_text(encoding="utf-8"))
    assert "uncovered dataset completion" in capabilities["network_analysis"]["diagnostics"]
    for case in CASES:
        project=root/case
        for name,digest in json.loads((project/"original-input-hashes.json").read_text(encoding="utf-8")).items():assert sha(project/name)==digest,(case,name)
        if case in INVALID:
            assert not (project/"state/project-status.yaml").exists();case_receipts.append({"case":case,"state":"invalid_contract"});continue
        ops=records(project);imports=[]
        for directory,m in ops:
            for name,digest in m.get("output_sha256",{}).items():
                path=directory/name;assert sha(path)==digest,(case,name);artifacts[path.relative_to(root).as_posix()]=digest
            if (directory/"supplied-analysis.yaml").exists():imports.append((directory,m))
        assert imports and not any(m["operation_type"] in {"association","participant_qc","rare_variant_scores"} for _,m in ops)
        assert all(m["details"]["analysis_executed_by_jlinalg"] is False for _,m in imports)
        if case in NETWORK:
            native=[(p,m) for p,m in ops if m["operation_type"]=="network_analysis" and not (p/"supplied-analysis.yaml").exists()]
            expected_native=case in {"partial","cohort-scope","raw-covered","pooled","partial-source"}
            assert len(native)==int(expected_native),(case,native)
            if native:
                folder,m=native[0];assert m["state"]=="complete" and m["details"]["dataset_id"]=="b"
                values=np.array([[float(r[f"s{i}"]) for i in range(32)] for r in table(project/"matrix.tsv")]);z=np.array([1 if r["sex"]=="F" else 0 for r in table(project/"pheno.tsv")])
                design=np.column_stack((np.ones(32),z));residuals=values.T-design@np.linalg.lstsq(design,values.T,rcond=None)[0];rho=np.corrcoef(residuals.T)
                expected={(f"g{i}",f"g{j}"):rho[i,j] for i,j in itertools.combinations(range(30),2) if abs(rho[i,j])>=.7}
                actual=table(folder/"network-edges.tsv");assert len(actual)==len(expected)==15
                for row in actual:
                    assert math.isclose(float(row["weight"]),expected[row["source"],row["target"]],abs_tol=1e-12);comparisons+=1;native_edges+=1
            for folder,m in imports:
                assert sha(folder/"supplied/nodes.tsv")==sha(project/"nodes.tsv")
                assert sha(folder/"supplied/edges.tsv")==sha(project/"edges.tsv")
                if case in HELD:
                    assert m["state"]=="waiting_for_input" and "canonical_results" not in m["details"]
                else:assert m["state"]=="complete"
            modules=[(p,m) for p,m in ops if m["operation_type"]=="network_modules"]
            assert len(modules)==len(imports)+len(native)
            for folder,m in modules:
                if m["state"]!="complete":continue
                if m["details"]["module_origin"]=="supplied_memberships_reused":assert m["details"]["memberships"]==6
                else:assert m["details"]["connected_components"]==25 and m["details"]["maximal_cliques"]==1
            for folder,m in ops:
                if m["operation_type"]!="module_gene_ontology" or m["state"]!="complete":continue
                actual=table(folder/"module-gene-ontology.tsv");assert len(actual) in {3,6},(case,actual)
                assert m["details"]["background_genes"]==30 and m["details"]["tested_module_term_pairs"]==len(actual)
                p=float(hypergeom.sf(5,30,10,6))
                for row in actual:
                    target=p if row["term_id"]=="GO:1" else 1
                    assert math.isclose(float(row["p_value"]),target,rel_tol=1e-9,abs_tol=1e-15)
                    assert math.isclose(float(row["adjusted_p"]),min(1,3*target),rel_tol=1e-9,abs_tol=1e-15);comparisons+=2;go_rows+=1
        else:
            by_id={m["details"]["artifact_id"]:(p,m) for p,m in imports}
            if case in HELD:
                held=[(p,m) for p,m in imports if m["state"]=="waiting_for_input"]
                assert held and all("canonical_results" not in m["details"] for _,m in held)
                assert all(m["details"]["scope_validation"]["status"]=="incompatible_or_unresolved" for _,m in held)
            elif case=="missing-parent":assert by_id["meta"][1]["state"]=="waiting_for_input" and by_id["go"][1]["state"]=="complete"
            else:assert all(m["state"]=="complete" for _,m in imports)
            if case=="private-meta":
                assert SECRET in (by_id["meta"][0]/"supplied-analysis.yaml").read_text(encoding="utf-8")
                dossiers=list(project.glob("runs/**/finding-inventory.jsonl"));assert dossiers
                payloads=list(project.glob("runs/**/comparisons/**/evidence.yaml"))
                assert payloads,"An unsent impact request must be retained"
                methods=list(project.glob("runs/**/methods-inventory.yaml"));assert methods
                for path in dossiers+payloads+methods:assert SECRET not in path.read_text(encoding="utf-8"),path
                assert all(not m.get("details",{}).get("remote_request_sent",False) for _,m in ops)
        if case in {"partial","cohort-scope","raw-covered","pooled","all-provided","supplied-only"}:
            assert all(m["state"]=="not_applicable" or m["state"]=="complete" and m.get("reused_from_cache") is True for _,m in ops),(case,[(m["operation_type"],m["state"]) for _,m in ops])
        cli(jar,project,"verify","--project",project)
        case_receipts.append({"case":case,"state":"held_scientific_scope" if case in HELD else "retained_missing_parent" if case=="missing-parent" else "completed_reuse"})
    write(root/"sealed-artifacts.json",artifacts)
    result={"passed":True,"cases":len(CASES),"case_receipts":case_receipts,"native_signed_edges":native_edges,"module_GO_rows":go_rows,"independent_comparisons":comparisons,
            "sealed_artifacts":len(artifacts),**pins,"external_requests":0,"real_AI_or_biological_validation":False,"full_pipeline_conformance":False}
    write(root/"verification.json",result);print(json.dumps(result,indent=2))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args()
    root,jar=args.directory.resolve(),args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False);(root/"software").mkdir();shutil.copyfile(jar,root/"software/jlinalg.jar");shutil.copyfile(__file__,root/"software/scoped-supplied-workflow.py")
        write(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__))})
        (root/"capabilities.yaml").write_text(cli(jar,root,"operations").stdout,encoding="utf-8")
        for case in CASES:
            project=root/case;generate(project,case)
            cli(jar,project,"validate",project/"study.json",invalid=case in INVALID)
            if case in INVALID:continue
            cli(jar,project,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
            if case in {"partial","cohort-scope","raw-covered","pooled","all-provided","supplied-only"}:
                cli(jar,project,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
