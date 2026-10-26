"""Offline acceptance for registered primary identity across downstream consumers.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Synthetic sources and gene sets validate software provenance and selection rules;
they do not establish biological validity, model calibration or AI appraisal.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import yaml

MODES=("single-go", "single-by", "meta-family", "selected-meta", "explicit-cohort", "ambiguous-cohorts", "incomplete-meta", "two-hypotheses")

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value): path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def table(path,header,data):
    with path.open("w",encoding="utf-8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(header);writer.writerows(data)
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    assert result.returncode in (0,3),result.stdout+result.stderr
    return result.stdout+result.stderr
def artifacts(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in sorted((project/"runs"/status["run_id"]/"operations").iterdir())]
def one(project,kind,hypothesis="H"):
    found=[(p,m) for p,m in artifacts(project) if m["operation_type"]==kind and m["hypothesis"]==hypothesis]
    assert len(found)==1,(kind,hypothesis,[(m["operation_type"],m["hypothesis"]) for p,m in artifacts(project)])
    return found[0]
def pin(root,jar):
    (root/"software").mkdir();target=root/"software"/("jlinalg-"+sha(jar)+".jar");shutil.copyfile(jar,target)
    shutil.copyfile(Path(__file__),root/"workflow-verifier-source.py")
    write(root/"execution-software.json",{"jar":target.relative_to(root).as_posix(),"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"python_version":sys.version,"pyyaml_version":yaml.__version__})
    return target
def generate(root,mode):
    project=root/mode;project.mkdir()
    n=20
    table(project/"a.tsv",["feature_id","beta","se","p_value"],[["f"+str(i),4 if i==1 else -1,1,1e-5 if i==1 else .8] for i in range(1,n+1)])
    table(project/"annotation.tsv",["feature_id","gene_symbol","gene_id","chromosome","start","end","genome_build"],[["f"+str(i),"G"+str(i),"ENSG"+str(i),"1",i*100,i*100+50,"GRCh38"] for i in range(1,n+1)])
    (project/"sets.gmt").write_text("top\tSynthetic top\t"+"\t".join("G"+str(i) for i in range(1,11))+"\nbottom\tSynthetic bottom\t"+"\t".join("G"+str(i) for i in range(11,21))+"\n",encoding="utf-8")
    database=project/"fixture-resources/enrichment/go-human";database.mkdir(parents=True)
    shutil.copyfile(project/"sets.gmt",database/"data.gmt")
    (database/"manifest.properties").write_text("format_version=1\nprovider=GO\nspecies=human\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+sha(database/"data.gmt")+"\n",encoding="utf-8")
    dataset={"id":"a","type":"expression","stage":"association_results","cohort":"A","species":"human","feature_id_type":"probe","genome_build":"GRCh38","files":{"results":"a.tsv","annotation":"annotation.tsv"}}
    analysis={"mode":"manual","goals":["gene_ontology","ranked_gsea","candidate_prioritization","impact_assessment"],"harmonization":{"effect_outliers":{"enabled":False}},"gsea":{"gene_sets":"sets.gmt","species":"human","gene_id_type":"symbol","minimum_size":1,"maximum_size":10,"maximum_exact_states":10,"permutations":1000,"tie_policy":"gene_id"},"multiple_testing":{"method":"BH","alpha":.05}}
    study={"schema_version":1,"project":{"id":"primary-"+mode,"title":"Synthetic primary source "+mode,"description":"Offline selection/provenance acceptance; no biological inference."},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[dataset],"hypotheses":[{"id":"H","description":"Synthetic primary identity.","inputs":{"discovery":["a"]},"analysis":analysis}]}
    if mode=="single-by":
        table(project/"a.tsv",["feature_id","beta","se","p_value"],[["f"+str(i),4 if i==1 else -1,1,.001 if i==1 else .8] for i in range(1,n+1)])
        analysis["multiple_testing"]["method"]="BY"
    if mode in ("meta-family","selected-meta","explicit-cohort","incomplete-meta"):
        table(project/"meta.tsv",["feature_id","beta","se","p_value","adjusted_p"],[["f"+str(i),1 if i==1 else -2,1,1e-12 if i==1 else .8,.6 if i==1 else 1] for i in range(1,n+1)])
        analysis["supplied_results"]=[{"id":"source-meta","kind":"meta_analysis","files":{"results":"meta.tsv","feature_annotation":"annotation.tsv"},"scope":{"species":"human","phenotype":"synthetic","coordinate_system":"1-based-closed"},"method":{"name":"Synthetic external fit; arithmetic not reconstructed"},"family":{"id":"external-full","coverage":"selected_only" if mode=="selected-meta" else "complete","tested_count":1000 if mode=="selected-meta" else n,"adjustment":"BH","adjusted_p_from_full_family":True}}]
    if mode=="incomplete-meta":
        analysis["supplied_results"][0]["reported_status"]="partial"
        table(project/"ranking.tsv",["gene_id","statistic"],[["G"+str(i),20-i] for i in range(1,n+1)])
        analysis["gsea"]["ranking"]={"file":"ranking.tsv","coverage":"complete","tested_genes":n}
    if mode=="explicit-cohort":analysis["primary_result"]={"kind":"association","dataset_id":"a","rationale":"Prespecified synthetic target cohort; supplied meta is contextual."}
    if mode in ("ambiguous-cohorts","two-hypotheses"):
        table(project/"b.tsv",["feature_id","beta","se","p_value"],[["f"+str(i),2 if i==2 else -3,1,1e-5 if i==2 else .8] for i in range(1,n+1)])
        study["datasets"].append(dict(dataset,id="b",cohort="B",files={"results":"b.tsv","annotation":"annotation.tsv"}))
        if mode=="ambiguous-cohorts":study["hypotheses"][0]["inputs"]={"discovery":["a"],"replication":["b"]}
        else:study["hypotheses"].append({"id":"H2","description":"Independent synthetic scope.","inputs":{"discovery":["b"]},"analysis":json.loads(json.dumps(analysis))})
    write(project/"study.yaml",study);return project
def selections(path):return yaml.safe_load((path/"primary-result-selection.yaml").read_text(encoding="utf-8"))["selections"]
def verify_case(project,mode):
    observed=[]
    for hypothesis in ("H","H2") if mode=="two-hypotheses" else ("H",):
        rank,rank_manifest=one(project,"candidate_prioritization",hypothesis)
        if mode in ("ambiguous-cohorts","incomplete-meta"):
            assert rank_manifest["state"]=="waiting_for_input"
            assert selections(rank)[hypothesis+":official"]["status"]==("incomplete_primary_meta_analysis" if mode=="incomplete-meta" else "ambiguous_primary_cohort")
            if mode=="incomplete-meta":
                gp,gm=one(project,"ranked_gsea",hypothesis)
                assert gm["state"]=="complete" and gm["details"]["ranking_origin"]=="externally_produced_rank_list_not_refitted"
                assert not (gp/"primary-result-selection.yaml").exists()
            continue
        assert rank_manifest["state"]=="complete",rank_manifest
        selected=selections(rank)[hypothesis+":official"]
        assert selected["official_primary"] is True and selected["hypothesis"]==hypothesis
        source=project/selected["source_artifact"];assert sha(source)==selected["source_sha256"]
        assert sha(rank/selected["producer_manifest_snapshot"])==selected["producer_manifest_sha256"]
        if selected["source_metadata_snapshot"]!="not_supplied":assert sha(rank/selected["source_metadata_snapshot"])==selected["source_metadata_sha256"]
        expected_meta=mode in ("meta-family","selected-meta")
        assert (selected["producer_type"]=="meta_analysis")==expected_meta
        ranked=rows(rank/"candidate-prioritization.tsv")
        expected_q=.6 if expected_meta else .001*20*sum(1/i for i in range(1,21)) if mode=="single-by" else 1e-5*20
        first=next(row for row in ranked if row["feature_id"]==("f2" if hypothesis=="H2" else "f1"))
        assert abs(float(first["FDR"])-expected_q)<1e-12,(mode,first)
        go,go_manifest=one(project,"gene_ontology",hypothesis)
        assert selections(go)[hypothesis+":official"]["primary_identity_sha256"]==selected["primary_identity_sha256"]
        if mode=="selected-meta":assert go_manifest["state"]=="waiting_for_input"
        else:
            assert go_manifest["state"]=="complete",go_manifest
            assert abs(float(next(row for row in rows(go/"feature-selection.tsv") if row["feature_id"]==first["feature_id"])["FDR"])-expected_q)<1e-12
        gs=[(p,m) for p,m in artifacts(project) if m["operation_type"]=="ranked_gsea" and m["hypothesis"]==hypothesis]
        assert len(gs)==1
        gp,gm=gs[0];ss=selections(gp)
        scoped=next(iter(ss.values()))
        assert scoped["source_sha256"]==selected["source_sha256"]
        if mode=="selected-meta":assert gm["state"]=="waiting_for_input"
        else:
            assert gm["state"]=="complete",gm
            assert len(rows(gp/"ranked-genes.tsv"))==20
        observed.append({"hypothesis":hypothesis,"primary_identity_sha256":selected["primary_identity_sha256"],"adjusted_p":expected_q,"gsea_official_primary":scoped["official_primary"]})
    inventory,manifest=one(project,"finding_inventory","__study__")
    assert manifest["state"]=="complete"
    dossiers=[json.loads(line) for line in (inventory/"finding-inventory.jsonl").read_text(encoding="utf-8").splitlines()]
    for item in observed:
        feature="f2" if item["hypothesis"]=="H2" else "f1"
        finding=next(d for d in dossiers if d["hypothesis"]==item["hypothesis"] and d["feature_id"]==feature)
        primary=[o for o in finding["observations"] if o.get("primary") is True and o["operation_type"] in ("association","validate_harmonize","meta_analysis")]
        assert len(primary)==1,primary
        assert primary[0]["primary_selection"]["primary_identity_sha256"]==item["primary_identity_sha256"]
        assert abs(primary[0]["full_family_adjusted_p"]-item["adjusted_p"])<1e-12
        assert finding["candidate"] is (item["adjusted_p"]<.05)
    if mode in ("ambiguous-cohorts","incomplete-meta"):
        assert manifest["details"]["unresolved_primary_hypotheses"]==["H"]
        assert all(not d["candidate"] for d in dossiers if d["feature_id"] in ("f1","f2"))
    return {"case":mode,"sources":observed,"inventory_findings":len(dossiers)}
def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text(encoding="utf-8"))
    assert sha(jar)==software["jar_sha256"]==sha(root/software["jar"])
    assert sha(Path(__file__))==software["verifier_sha256"]==sha(root/"workflow-verifier-source.py")
    result=[verify_case(root/mode,mode) for mode in MODES]
    write(root/"verification.json",{"passed":True,"jar_sha256":sha(jar),"verifier_sha256":software["verifier_sha256"],"cases":result,"scope":"Synthetic offline cross-consumer primary source, family and hypothesis identity checks; no scientific validation or AI ranking quality claim."})
    return result
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir();jar=pin(root,jar)
        for mode in MODES:
            project=generate(root,mode)
            (project/"execution.log").write_text(cli(jar,"auto",project/"study.yaml","--offline"),encoding="utf-8")
            (project/"replay.log").write_text(cli(jar,"auto",project/"study.yaml","--offline"),encoding="utf-8")
            cached_types={"validate_harmonize","meta_analysis","gene_ontology","ranked_gsea"}
            if mode not in ("selected-meta","ambiguous-cohorts","incomplete-meta"):cached_types.add("candidate_prioritization")
            assert all(m.get("reused_from_cache") is True for p,m in artifacts(project) if m["state"] in ("complete","not_applicable") and m["operation_type"] in cached_types)
            (project/"verify.log").write_text(cli(jar,"verify","--project",project),encoding="utf-8")
    results=verify(root,jar);print(json.dumps({"passed":True,"cases":len(results),"directory":str(root),"jar_sha256":sha(jar)}))
if __name__=="__main__":main()
