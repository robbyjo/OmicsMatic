"""Offline native/supplied network, bounded-clique and joint-module-GO acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR.
Synthetic features/graphs/GO terms; no real-model or biological validation.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import yaml

MODES = ("native", "supplied-modules", "supplied-pathway", "clique-limit", "empty-pathway")

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")

def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))

def cli(jar, *args):
    result = subprocess.run(["java", "-jar", str(jar), "study", *map(str, args)], text=True, capture_output=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout

def generate(project, mode):
    project.mkdir()
    database = project / "fixture-resources/enrichment/go-human"
    assert database.resolve().is_relative_to(project.resolve())
    database.mkdir(parents=True)
    (database / "data.gmt").write_text("".join("GO:" + str(t+1) + "\tSynthetic fixture\t" + "\t".join(f"A{i}" for i in range(t*10+1,t*10+11)) + "\n" for t in range(3)), encoding="utf-8")
    (database / "manifest.properties").write_text("format_version=1\nprovider=GO\nspecies=human\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt=" + sha(database / "data.gmt") + "\n", encoding="utf-8")
    network = {"minimum_samples":8,"correlation":"pearson","threshold":.7,"thresholds":[.6,.7,.8],"categorical_covariates":["sex"],
               "preprocessing":"Synthetic orthogonal vectors; no biological normalization claimed.","bootstraps":10,"independent_samples":True,"seed":7}
    datasets = []
    supplied = []
    covariates = []
    goals = ["network_analysis","network_modules","module_gene_ontology"]
    if mode == "native":
        z = [1,1,1,1,-1,-1,-1,-1]; x = [1,1,-1,-1,1,1,-1,-1]; y = [1,-1,1,-1,1,-1,1,-1]
        vectors = [[10*a+b for a,b in zip(z,x)], [5*a+2*b for a,b in zip(z,x)], [-4*a-b for a,b in zip(z,x)], [2*a+b for a,b in zip(z,y)], [3]*8]
        (project / "matrix.tsv").write_text("feature_id\t" + "\t".join(f"s{i}" for i in range(8)) + "\n" + "".join(f"g{j}\t" + "\t".join(map(str,v)) + "\n" for j,v in enumerate(vectors)), encoding="utf-8")
        (project / "pheno.tsv").write_text("id\tsex\n" + "".join(f"s{i}\t{'F' if a>0 else 'M'}\n" for i,a in enumerate(z)), encoding="utf-8")
        (project / "annotation.tsv").write_text("feature_id\tgene_symbol\n" + "".join(f"g{i}\tA{i+1}\n" for i in range(5)), encoding="utf-8")
        datasets = [{"id":"molecular","cohort":"A","type":"expression","stage":"processed_matrix","species":"human","files":{"matrix":"matrix.tsv","phenotype":"pheno.tsv","annotation":"annotation.tsv"}}]
        covariates = ["sex"]
    elif mode in ("supplied-modules", "clique-limit"):
        (project / "nodes.tsv").write_text("node_id\n" + "".join(f"g{i}\n" for i in range(1,31)), encoding="utf-8")
        (project / "edges.tsv").write_text("source\ttarget\tweight\n" + "".join(pair + "\t.8\n" for pair in ("g1\tg2","g1\tg3","g2\tg3","g2\tg4","g3\tg4")), encoding="utf-8")
        (project / "annotation.tsv").write_text("feature_id\tgene_symbol\n" + "".join(f"g{i}\tA{i}\n" for i in range(1,31)), encoding="utf-8")
        files = {"nodes":"nodes.tsv","edges":"edges.tsv","feature_annotation":"annotation.tsv"}
        if mode == "supplied-modules":
            (project / "modules.tsv").write_text("node_id\tmodule_id\n" + "".join(f"g{i}\tM1\n" for i in range(1,7)), encoding="utf-8")
            files["modules"] = "modules.tsv"
        else:
            network["max_cliques"] = 1
        supplied = [{"id":"external-network","kind":"network_analysis","files":files,"scope":{"species":"human","phenotype":"synthetic"},
                     "method":{"name":"External synthetic graph","edge_direction":"undirected"},"limitations":["Synthetic supplied graph; original network estimation not reproduced."]}]
    else:
        (project / "gsea.tsv").write_text("term_id\tp_value\tadjusted_p\nT1\t.00001\t.01\nT2\t.00001\t.02\nT3\t.00001\t.4\n", encoding="utf-8")
        if mode=="empty-pathway":
            (project / "gsea.tsv").write_text("term_id\tp_value\tadjusted_p\nT1\t.00001\t.4\nT2\t.00001\t.4\nT3\t.00001\t.4\n", encoding="utf-8")
        (project / "sets.gmt").write_text("T1\tOne\tA1\tA2\tA3\nT2\tTwo\tA1\tA2\tA4\nT3\tExcluded\tA1\tA2\tA3\n", encoding="utf-8")
        supplied = [{"id":"external-gsea","kind":"ranked_gsea","files":{"results":"gsea.tsv","gene_sets":"sets.gmt"},"scope":{"species":"human","phenotype":"synthetic"},
                     "method":{"name":"External synthetic ranked enrichment"},"family":{"id":"all-source-terms","coverage":"selected_only","tested_count":100,"adjustment":"BH","adjusted_p_from_full_family":True}}]
        goals = ["pathway_network","network_modules","module_gene_ontology"]
        network.update({"pathway_threshold":.5,"membership_gene_id_type":"symbol","go_min_size":1})
    study = {"schema_version":1,"project":{"id":"network-fixture-"+mode,"title":"Synthetic networks","description":"Software acceptance; no biological result or real AI appraisal."},
             "resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":datasets,
             "hypotheses":[{"id":"H","description":"Complete scoped network/module work.","covariates":covariates,"analysis":{"mode":"manual","goals":goals,"network":network,"supplied_results":supplied}}]}
    write(project / "study.json", study)
    write(project / "original-input-hashes.json", {str(path.relative_to(project)):sha(path) for path in project.rglob("*") if path.is_file()})

def operations(project):
    state = yaml.safe_load((project / "state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p.parent,yaml.safe_load(p.read_text(encoding="utf-8"))) for p in (project / "runs" / state["run_id"] / "operations").glob("*/manifest.yaml")]

def one(project, kind):
    found = [(p,d) for p,d in operations(project) if d["operation_type"]==kind]
    assert len(found)==1, (kind,len(found))
    return found[0]

def verify(root, jar):
    for mode in MODES:
        project = root / mode
        for name,digest in json.loads((project / "original-input-hashes.json").read_text(encoding="utf-8")).items():
            assert sha(project / name)==digest
        records = operations(project)
        assert not any(d["operation_type"] in ("association","participant_qc","meta_analysis") for _,d in records)
        modules,md = one(project,"network_modules")
        go,gd = one(project,"module_gene_ontology")
        if mode=="clique-limit":
            assert md["state"]==gd["state"]=="waiting_for_input"
            assert md["details"]["clique_search_complete"] is False and md["details"]["search_limit"]=="max_cliques"
            assert not (go / "module-gene-ontology.tsv").exists()
            assert gd["details"]["method_report"]["performed"] is False
        else:
            assert all(d["state"]=="complete" for _,d in records), records
            if mode=="native":
                graph,nd = one(project,"network_analysis")
                assert nd["details"]["analysis_samples"]==8 and nd["details"]["eligible_features"]==4
                edges=rows(graph / "network-edges.tsv")
                assert len(edges)==3
                for edge in edges:
                    expected=1 if edge["source"]=="g0" and edge["target"]=="g1" else -1
                    assert math.isclose(float(edge["weight"]),expected,abs_tol=1e-12)
                assert md["details"]["connected_components"]==2 and md["details"]["maximal_cliques"]==1
                assert gd["details"]["background_genes"]==4
                assert gd["details"]["tested_module_term_pairs"]==0 and gd["details"]["method_report"]["performed"] is False
                assert all(d.get("reused_from_cache") is True for _,d in records)
                tree=ET.parse(graph / "network.graphml");ns={"g":"http://graphml.graphdrawing.org/xmlns"}
                assert len(tree.findall(".//g:node",ns))==4 and len(tree.findall(".//g:edge",ns))==3
            elif mode=="supplied-modules":
                assert md["details"]["module_origin"]=="supplied_memberships_reused" and md["details"]["memberships"]==6
                assert gd["details"]["background_genes"]==30 and gd["details"]["tested_module_term_pairs"]==3
                term=next(r for r in rows(go / "module-gene-ontology.tsv") if r["term_id"]=="GO:1")
                expected=math.comb(10,6)/math.comb(30,6)
                assert math.isclose(float(term["p_value"]),expected,rel_tol=1e-9)
                assert math.isclose(float(term["adjusted_p"]),3*expected,rel_tol=1e-9)
                assert "not independently reproduced" in gd["details"]["required_manuscript_caveat"]
                source=one(project,"network_analysis")[0]
                assert "original network estimation not reproduced" in (source / "supplied-analysis.yaml").read_text(encoding="utf-8")
            elif mode=="empty-pathway":
                _,nd=one(project,"pathway_network")
                assert nd["details"]["nodes"]==0 and nd["details"]["edges"]==0
                assert gd["details"]["tested_module_term_pairs"]==0 and gd["details"]["method_report"]["performed"] is False
                assert not rows(go / "module-gene-ontology.tsv") and "needs" not in gd["details"]
            else:
                graph,nd = one(project,"pathway_network")
                assert nd["details"]["nodes"]==2 and nd["details"]["edges"]==1
                assert float(rows(graph / "network-edges.tsv")[0]["weight"])==.5
                assert gd["details"]["background_genes"]==4
                assert one(project,"ranked_gsea")[1]["details"]["analysis_executed_by_jlinalg"] is False
        cli(jar,"verify","--project",project)
    software=root / "software";software.mkdir(exist_ok=True);pinned=software / ("jlinalg-"+sha(jar)+".jar")
    if not pinned.exists():shutil.copyfile(jar,pinned)
    assert sha(pinned)==sha(jar)
    verifier=root / "workflow-verifier-source.py"
    if verifier.exists():assert sha(verifier)==sha(Path(__file__)),"Use the historical pinned verifier"
    else:shutil.copyfile(Path(__file__),verifier)
    report={"jar_sha256":sha(jar),"verifier_sha256":sha(verifier),"native_signed_covariate_adjusted_edges":True,"components_and_maximal_cliques":True,
            "supplied_modules_reused":True,"module_GO_hypergeometric_and_joint_BH_reconstructed":True,"full_mapped_universe_including_isolates":True,
            "source_GSEA_adjustment_preserved":True,"pathway_Jaccard_reconstructed":True,"bounded_partial_cliques_block_GO_family":True,
            "source_caveats_propagated":True,"input_hashes_unchanged":True,"native_resume_reuses_all_operations":True,"GraphML_matches_tables":True,
            "empty_pathways_complete_without_missing_mapping_or_GO_claim":True,
            "zero_eligible_GO_terms_marked_not_performed":True,
            "project_local_resources":True,"external_requests":0,"real_AI_or_biological_validation":False,"full_pipeline_conformance":False}
    write(root / "packaged-verification.json", report);print(json.dumps(report,indent=2))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True)
    args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False)
        for mode in MODES:
            project=root / mode;generate(project,mode);cli(jar,"auto",project / "study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
        cli(jar,"auto",root / "native/study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
