"""Offline supplied-analysis imports and completion of genuinely missing GO work.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR
All estimates, genes, terms, graphs and causal-analysis numbers are synthetic.
This verifies import/selection/orchestration, not external scientific validity.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import yaml


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def cli(jar, *arguments):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, arguments)], text=True, capture_output=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def artifact(kind, filename, count=None, identity=None):
    value = {"id": identity or "external-" + kind.replace("_", "-"), "kind": kind, "files": {"results": filename},
             "scope": {"phenotype": "synthetic", "cohorts": ["cohort-a", "cohort-b"]},
             "method": {"name": "External synthetic protocol; calibration not assessed", "software": "fixture", "version": "1"},
             "provenance": {"producer": "synthetic-protocol-fixture"}, "limitations": ["Synthetic software fixture; no scientific result or source appraisal is claimed."]}
    if count is not None:
        value["family"] = {"id": value["id"] + "-family", "coverage": "complete", "tested_count": count, "adjustment": "BH"}
    return value


def generate(project, mode):
    project.mkdir()
    table = "feature_id\tbeta\tse\tp_value\n" + "".join(f"g{i}\t.4\t.1\t{'.0001' if i <= 6 else '.8'}\n" for i in range(1, 31))
    (project / "meta.tsv").write_text(table, encoding="utf-8")
    (project / "annotation.tsv").write_text("feature_id\tgene_symbol\n" + "".join(f"g{i}\tA{i}\n" for i in range(1,31)), encoding="utf-8")
    meta = artifact("meta_analysis", "meta.tsv", 30, "external-meta")
    meta["files"]["feature_annotation"] = "annotation.tsv"
    artifacts = [meta]; goals = []
    if mode == "all-provided":
        (project / "go.tsv").write_text("term_id\tterm_name\tp_value\nGO:SYNTHETIC\tSynthetic supplied term\t.02\n", encoding="utf-8")
        (project / "mr.tsv").write_text("feature_id\tbeta\tse\tp_value\ng1\t.2\t.1\t.04\n", encoding="utf-8")
        (project / "coloc.tsv").write_text("feature_id\tregion_id\tpp_h4\ng1\tr1\t.95\n", encoding="utf-8")
        (project / "nodes.tsv").write_text("node_id\ng1\ng2\ng3\n", encoding="utf-8")
        (project / "edges.tsv").write_text("source\ttarget\tweight\ng1\tg2\t.8\ng2\tg3\t-.9\n", encoding="utf-8")
        (project / "modules.tsv").write_text("node_id\tmodule_id\ng1\tM1\ng2\tM1\ng2\tM2\ng3\tM2\n", encoding="utf-8")
        for kind, filename in (("gene_ontology","go.tsv"),("mendelian_randomization","mr.tsv"),("colocalization","coloc.tsv")):
            value = artifact(kind, filename, None if kind == "colocalization" else 1)
            value["depends_on"] = ["external-meta"]
            if kind == "colocalization":
                value["evidence_grade"] = "exploratory_partial_region_major_limitation"
                value["limitations"].append("The synthetic source exported only selected QTL variants.")
            artifacts.append(value)
        network = artifact("network_analysis", "unused")
        network["files"] = {"nodes":"nodes.tsv","edges":"edges.tsv","modules":"modules.tsv"}; network["depends_on"] = ["external-meta"]
        artifacts.append(network)
        (project / "rare.tsv").write_text("feature_id\tmask_id\ttest\tp_value\tis_primary_test\ng1\tlof\tSKAT-O\t.001\ttrue\ng1\tlof\tburden\t.00001\tfalse\ng1\tmissense\tSKAT-O\t.2\ttrue\ng1\tmissense\tburden\t.3\tfalse\n", encoding="utf-8")
        rare = artifact("rare_variant_analysis", "rare.tsv", 4)
        rare["family"]["adjustment"] = "BONFERRONI"
        artifacts.append(rare); goals.append("rare_variant_analysis")
    elif mode == "meta-to-go":
        goals = ["gene_ontology"]
        database = project / "fixture-resources/enrichment/go-human"; database.mkdir(parents=True)
        gmt = "".join("GO:" + str(term+1) + "\tSynthetic fixture term\t" + "\t".join(f"A{i}" for i in range(term*10+1,term*10+11)) + "\n" for term in range(3))
        (database / "data.gmt").write_text(gmt, encoding="utf-8")
        manifest = "#Synthetic local test resource, not biological GO annotations\nformat_version=1\nprovider=GO\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt=" + sha(database / "data.gmt") + "\n"
        (database / "manifest.properties").write_text(manifest, encoding="utf-8")
    else:
        (project / "meta.tsv").write_text("feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.000001\t.4\n", encoding="utf-8")
        meta["family"] = {"id":"full-source-family","coverage":"selected_only","tested_count":400000,"adjustment":"BH","adjusted_p_from_full_family":True}
        goals = ["impact_assessment"]
    study = {"schema_version":1,"project":{"id":"supplied-fixture-"+mode,"title":"Synthetic supplied analyses","description":"No biological or real-model scientific appraisal."},
             "resources":{"network":"offline","downloads":"deny","directory":"fixture-resources"},"datasets":[],
             "hypotheses":[{"id":"H","description":"Use supplied results and complete only missing requested work.","analysis":{"mode":"manual","goals":goals,"supplied_results":artifacts}}]}
    write(project / "study.json", study)
    originals = {str(path.relative_to(project)):sha(path) for path in project.rglob("*") if path.is_file()}
    write(project / "original-input-hashes.json", originals)


def operations(project):
    status = yaml.safe_load((project / "state/project-status.yaml").read_text(encoding="utf-8"))
    return [(path.parent,yaml.safe_load(path.read_text(encoding="utf-8"))) for path in (project / "runs" / status["run_id"] / "operations").glob("*/manifest.yaml")]


def one(project, kind):
    selected = [(path,data) for path,data in operations(project) if data["operation_type"] == kind]
    assert len(selected) == 1, (project,kind,len(selected))
    return selected[0]


def verify(root, jar):
    for mode in ("all-provided","meta-to-go","selected-only"):
        project=root/mode
        for name,digest in json.loads((project/"original-input-hashes.json").read_text(encoding="utf-8")).items():
            assert sha(project/name)==digest
        records=operations(project)
        assert all(data["state"]=="complete" for _,data in records),records
        assert not any(data["operation_type"] in ("association","participant_qc","sensitivity_analysis") for _,data in records)
        meta,metadata=one(project,"meta_analysis")
        assert metadata["details"]["analysis_executed_by_jlinalg"] is False
        assert sha(meta/"supplied/results.tsv")==sha(project/"meta.tsv")
        if mode=="all-provided":
            for kind in ("meta_analysis","gene_ontology","network_analysis","mendelian_randomization","colocalization","rare_variant_analysis"):
                folder,data=one(project,kind)
                assert data["details"]["execution_origin"]=="externally_produced"
                assert data["details"]["analysis_executed_by_jlinalg"] is False
                assert data.get("reused_from_cache") is True
            assert "a null result is not evidence against a shared causal variant" in one(project,"colocalization")[1]["details"]["required_manuscript_caveat"]
            network,_=one(project,"network_analysis")
            assert len(rows(network/"network-edges.tsv"))==2 and len(rows(network/"network-modules.tsv"))==4
            rare,_=one(project,"rare_variant_analysis")
            sets=rows(rare/"rare-results.tsv")
            assert len(sets)==4 and float(sets[0]["adjusted_p"])==.004
            assert all(row["result_type"]=="unsigned_set" and "beta" not in row and "direction" not in row for row in sets)
        elif mode=="meta-to-go":
            folder,data=one(project,"gene_ontology")
            assert data["details"].get("execution_origin")!="externally_produced"
            assert len((folder/"selected-genes.txt").read_text().splitlines())==6
            assert len((folder/"background-genes.txt").read_text().splitlines())==30
            expected=math.comb(10,6)/math.comb(30,6)
            result=next(row for row in rows(folder/"gene-ontology.tsv") if row["term_id"]=="GO:1")
            assert math.isclose(float(result["p_value"]),expected,rel_tol=1e-9,abs_tol=1e-12)
            assert math.isclose(float(result["FDR"]),3*expected,rel_tol=1e-9,abs_tol=1e-12)
        else:
            result=rows(meta/"meta-analysis.tsv")[0]
            assert float(result["adjusted_p"])==.4
            inventory,_=one(project,"finding_inventory")
            dossier=json.loads((inventory/"finding-inventory.jsonl").read_text(encoding="utf-8").strip())
            assert dossier["candidate"] is False
            observation=next(row for row in dossier["observations"] if row.get("primary"))
            assert observation["full_family_adjusted_p"]==.4 and observation["testing_denominator"]==400000
            assert one(project,"impact_assessment")[1]["details"]["selected_ids"]==[]
        cli(jar,"verify","--project",project)
    software=root/"software";software.mkdir(exist_ok=True); pinned=software/f"jlinalg-{sha(jar)}.jar"
    if not pinned.exists():shutil.copyfile(jar,pinned)
    assert sha(pinned)==sha(jar)
    verifier=root/"workflow-verifier-source.py"
    if verifier.exists():assert sha(verifier)==sha(Path(__file__)),"Use the pinned historical verifier"
    else:shutil.copyfile(Path(__file__),verifier)
    report={"jar_sha256":sha(jar),"tested_jar":str(pinned.relative_to(root)),"verifier_sha256":sha(verifier),
            "all_six_supplied_stages_reused":True,"missing_native_GO_completed":True,"hypergeometric_and_BH_reconstructed":True,
            "unsigned_rare_joint_mask_test_correction_preserved":True,
            "source_FDR_and_denominator_preserved":True,"no_candidate_invented_from_selected_rows":True,"no_association_refits":True,
            "input_hashes_unchanged":True,"project_local_resource_directory":True,"external_requests":0,
            "external_scientific_validity_reproduced":False,"real_AI_appraisal_tested":False,"full_pipeline_conformance":False}
    write(root/"packaged-verification.json",report);print(json.dumps(report,indent=2))


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("project",type=Path);parser.add_argument("--jar",type=Path,required=True)
    args=parser.parse_args();root=args.project.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False)
        for mode in ("all-provided","meta-to-go","selected-only"):
            project=root/mode;generate(project,mode)
            cli(jar,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
        cli(jar,"auto",root/"all-provided/study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
