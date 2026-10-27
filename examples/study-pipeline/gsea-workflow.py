"""Offline preranked GSEA, source reuse and network/GO completion acceptance.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar JAR.
Independent full-rank Python scans and exhaustive membership nulls check the
Java hit-extrema implementation. All genes, effects and collections are synthetic.
"""
import argparse
import csv
import hashlib
import itertools
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import yaml

MODES=("exact","by-preserved","monte-carlo","primary-meta","primary-meta-auto","selected-ranking","ties","phenotype-null","no-eligible-terms")
WAITING={"selected-ranking","ties","phenotype-null"}
EPS=1e-12

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value): path.write_text(json.dumps(value,indent=2)+"\n",encoding="utf-8")
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    assert result.returncode==0,result.stdout+result.stderr
    return result.stdout

def pin_software(root,jar):
    software=root/"software";software.mkdir(exist_ok=True);digest=sha(jar);pinned=software/("jlinalg-"+digest+".jar")
    if not pinned.exists():shutil.copyfile(jar,pinned)
    assert sha(pinned)==digest and sha(jar)==digest,"Executable changed while pinning"
    verifier=root/"workflow-verifier-source.py"
    if verifier.exists():assert sha(verifier)==sha(Path(__file__)),"Use the pinned historical verifier"
    else:shutil.copyfile(Path(__file__),verifier)
    write(root/"execution-software.json",{"jar_sha256":digest,"jar":str(pinned.relative_to(root)),"verifier_sha256":sha(verifier)})
    return pinned

def generate(project,mode):
    project.mkdir()
    genes=[f"G{i+1}" for i in range(10)]
    statistics=[.95-.2*i for i in range(10)]
    sets={"top":genes[:3],"top-overlap":[genes[0],genes[1],genes[3]],"bottom":genes[-3:],"mixed":[genes[1],genes[4],genes[7]]}
    if mode=="by-preserved":
        genes=["A","B","C","D"];statistics=[4,3,-1,-2];sets={"top":genes[:2],"bottom":genes[2:]}
    if mode=="ties":statistics[1]=statistics[0]
    if mode=="no-eligible-terms":sets={"whole-ranking":genes}
    (project/"ranks.tsv").write_text("gene_id\tstatistic\n"+"".join(f"{g}\t{v}\n" for g,v in zip(genes,statistics)),encoding="utf-8")
    (project/"sets.gmt").write_text("".join(term+"\tSynthetic "+term+"\t"+"\t".join(members)+"\n" for term,members in sets.items()),encoding="utf-8")
    gsea={"ranking":{"file":"ranks.tsv","coverage":"complete","tested_genes":len(genes),"provenance":{"producer":"synthetic-protocol-fixture"}},
          "gene_sets":"sets.gmt","species":"human","gene_id_type":"symbol","minimum_size":2,"maximum_size":4,"maximum_exact_states":10000,"permutations":20000,"seed":31,
          "adjustment":"BY" if mode=="by-preserved" else "BH","rank_interpretation":"Synthetic signed source statistic; no biological mechanism or model calibration claimed.","curve_plots":3}
    if mode=="monte-carlo":gsea["maximum_exact_states"]=0
    if mode=="selected-ranking":gsea["ranking"].update({"coverage":"selected_only","tested_genes":1000})
    if mode=="phenotype-null":gsea["null_model"]="phenotype"
    supplied=[]
    if mode in ("primary-meta","primary-meta-auto"):
        gsea.pop("ranking")
        (project/"meta.tsv").write_text("feature_id\tbeta\tse\tp_value\n"+"".join(f"f{i+1}\t{v}\t1\t{math.erfc(abs(v)/math.sqrt(2))}\n" for i,v in enumerate(statistics)),encoding="utf-8")
        (project/"annotation.tsv").write_text("feature_id\tgene_symbol\n"+"".join(f"f{i+1}\t{g}\n" for i,g in enumerate(genes)),encoding="utf-8")
        supplied=[{"id":"external-meta","kind":"meta_analysis","files":{"results":"meta.tsv","feature_annotation":"annotation.tsv"},"scope":{"species":"human","feature_id_type":"gene_symbol"},
                   "method":{"name":"Synthetic external signed estimates"},"family":{"id":"all-ten-features","coverage":"complete","tested_count":10,"adjustment":"BH"}}]
    database=project/"fixture-resources/enrichment/go-human"
    assert database.resolve().is_relative_to(project.resolve());database.mkdir(parents=True)
    collection={"GO:1":genes[:len(genes)//2],"GO:2":genes[len(genes)//2:],"GO:3":genes[2:8]}
    (database/"data.gmt").write_text("".join(term+"\tSynthetic fixture\t"+"\t".join(members)+"\n" for term,members in collection.items() if members),encoding="utf-8")
    (database/"manifest.properties").write_text("format_version=1\nprovider=GO\nspecies=human\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+sha(database/"data.gmt")+"\n",encoding="utf-8")
    study={"schema_version":1,"project":{"id":"gsea-fixture-"+mode,"title":"Synthetic GSEA acceptance","description":"No biological validation, real AI appraisal or external scientific execution."},
           "resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[],
           "hypotheses":[{"id":"H","description":"Complete GSEA and missing pathway/module work.","analysis":{"mode":"manual","goals":["ranked_gsea","pathway_network","network_modules","module_gene_ontology"],
             "gsea":gsea,"network":{"term_alpha":.6 if mode=="by-preserved" else .2,"pathway_threshold":.4,"go_min_size":1},"supplied_results":supplied}}]}
    if mode=="primary-meta-auto":
        study["project"]["objective"]="exploration"
        study["hypotheses"][0]["analysis"]["mode"]="auto"
        study["hypotheses"][0]["analysis"].pop("goals")
    write(project/"study.json",study)
    write(project/"reference-input.json",{"genes":genes,"statistics":statistics,"sets":sets,"collection":collection,"adjustment":gsea["adjustment"],"minimum_size":2,"maximum_size":4})
    write(project/"original-input-hashes.json",{str(p.relative_to(project)):sha(p) for p in project.rglob("*") if p.is_file()})

def operations(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"))
    return [(p.parent,yaml.safe_load(p.read_text(encoding="utf-8"))) for p in (project/"runs"/status["run_id"]/"operations").glob("*/manifest.yaml")]
def one(project,kind):
    found=[(p,d) for p,d in operations(project) if d["operation_type"]==kind]
    assert len(found)==1,(kind,len(found));return found[0]

def scan(statistics,members):
    mass=sum(abs(statistics[i]) for i in members);running=0.;values=[]
    for i,v in enumerate(statistics):
        running+=abs(v)/mass if i in members else -1/(len(statistics)-len(members));values.append(running)
    upper=max([0.]+values);lower=min([0.]+values)
    return (upper if upper>=-lower-EPS else lower),values

def reference(data):
    stats=data["statistics"];genes=data["genes"];result={};nulls={}
    for term,raw in data["sets"].items():
        members={genes.index(g) for g in raw if g in genes};size=len(members)
        if size<data["minimum_size"] or size>data["maximum_size"] or size==len(genes):continue
        score,curve=scan(stats,members)
        if size not in nulls:nulls[size]=[scan(stats,set(c))[0] for c in itertools.combinations(range(len(genes)),size)]
        same=[v for v in nulls[size] if (v>=0 if score>=0 else v<0)]
        tail=sum(v>=score-EPS if score>=0 else v<=score+EPS for v in same)
        result[term]={"score":score,"nes":score/(sum(map(abs,same))/len(same)),"p":tail/len(same),"same":len(same),"tail":tail,"states":len(nulls[size]),"members":members,"curve":curve}
    return result

def adjust(values,method):
    factor=sum(1/i for i in range(1,len(values)+1)) if method=="BY" else 1
    if method=="NONE":return values.copy()
    ordered=sorted(values,key=values.get);running=1.;result={}
    for i in range(len(ordered)-1,-1,-1):
        term=ordered[i];running=min(running,values[term]*len(ordered)*factor/(i+1));result[term]=running
    return result

def choose(n,k):return math.comb(n,k) if 0<=k<=n else 0
def hypergeometric(N,K,n,k):return sum(choose(K,i)*choose(N-K,n-i) for i in range(k,min(K,n)+1))/choose(N,n)

def verify_go(project,folder):
    go,metadata=one(project,"module_gene_ontology")
    table=rows(go/"module-gene-ontology.tsv")
    if not table:
        assert metadata["details"]["method_report"]["performed"] is False
        return
    universe=set((go/"background-genes.txt").read_text(encoding="utf-8").splitlines())
    nodes={r["node_id"]:set(r["gene_symbol"].split(";")) for r in rows(go/"node-to-gene.tsv") if r["status"]=="mapped"}
    modules,_=one(project,"network_modules");membership={}
    for row in rows(modules/"network-modules.tsv"):membership.setdefault(row["module_id"],set()).update(nodes.get(row["node_id"],set()))
    collection=json.loads((project/"reference-input.json").read_text())["collection"]
    probabilities={}
    for row in table:
        genes=membership[row["module_id"]];term=set(collection[row["term_id"]])&universe
        expected=hypergeometric(len(universe),len(term),len(genes),len(term&genes))
        assert math.isclose(float(row["p_value"]),expected,abs_tol=1e-12,rel_tol=1e-9)
        probabilities[(row["module_id"],row["term_id"])]=expected
    q=adjust(probabilities,"BH")
    for row in table:assert math.isclose(float(row["adjusted_p"]),q[(row["module_id"],row["term_id"])],abs_tol=1e-12,rel_tol=1e-9)

def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text())
    assert sha(jar)==software["jar_sha256"],"Use this bundle's pinned executable"
    assert sha(Path(__file__))==software["verifier_sha256"],"Use this bundle's pinned verifier"
    for mode in MODES:
        project=root/mode
        for name,digest in json.loads((project/"original-input-hashes.json").read_text()).items():assert sha(project/name)==digest
        records=operations(project);assert not any(d["operation_type"] in ("association","participant_qc","network_analysis") for _,d in records)
        folder,metadata=one(project,"ranked_gsea")
        if mode in WAITING:
            assert metadata["state"]=="waiting_for_input" and not (folder/"ranked-gsea.tsv").exists()
            assert metadata["details"]["method_report"]["performed"] is False
            assert not (one(project,"pathway_network")[0]/"network-nodes.tsv").exists()
            cli(jar,"verify","--project",project);continue
        if mode=="primary-meta-auto":
            assert not any(d["state"]=="failed" for _,d in records),records
            assert all(one(project,kind)[1]["state"]=="complete" for kind in ("meta_analysis","ranked_gsea","pathway_network","network_modules","module_gene_ontology"))
            resolved=yaml.safe_load((project/"resolved-study.yaml").read_text());receipt=resolved["_resolution"]["followup_applicability"]["H"]
            assert receipt["source_id"]=="external-meta" and receipt["automatic_goal_merge"] is True
            assert "ranked_gsea" in resolved["hypotheses"][0]["analysis"]["goals"]
        else:assert all(d["state"]=="complete" for _,d in records),records
        data=json.loads((project/"reference-input.json").read_text());expected=reference(data);table=rows(folder/"ranked-gsea.tsv")
        assert len(table)==len(expected)
        probabilities={}
        for row in table:
            ref=expected[row["term_id"]];assert math.isclose(float(row["enrichment_score"]),ref["score"],abs_tol=EPS)
            p=float(row["p_value"]);probabilities[row["term_id"]]=p
            if mode=="monte-carlo":
                n=int(row["same_sign_states"]);tail=int(row["tail_states"])
                assert row["null_method"]=="monte_carlo_gene_set" and int(row["null_states"])==20000
                assert p==(tail+1)/(n+1) and p>0 and float(row["p_resolution"])==1/(n+1)
                allowance=max(.005,6*math.sqrt(ref["p"]*(1-ref["p"])/n))
                assert abs(p-ref["p"])<=allowance,(row,ref,allowance)
                assert abs(float(row["normalized_enrichment_score"])-ref["nes"])<.05
            else:
                assert row["null_method"]=="exact_gene_set_enumeration" and int(row["null_states"])==ref["states"]
                assert int(row["same_sign_states"])==ref["same"] and int(row["tail_states"])==ref["tail"]
                assert math.isclose(p,ref["p"],abs_tol=EPS) and math.isclose(float(row["normalized_enrichment_score"]),ref["nes"],abs_tol=EPS)
            leading=set(row["leading_edge_genes"].split(";"));peaks=[i for i,v in enumerate(ref["curve"]) if abs(v-ref["score"])<EPS]
            assert any(leading=={data["genes"][j] for j in ref["members"] if (j<=i if ref["score"]>=0 else j>i)} for i in peaks)
        q=adjust(probabilities,data["adjustment"])
        for row in table:assert math.isclose(float(row["adjusted_p"]),q[row["term_id"]],abs_tol=EPS)
        graph,gd=one(project,"pathway_network");selected={term for term,value in q.items() if value<(.6 if mode=="by-preserved" else .2)}
        assert {r["node_id"] for r in rows(graph/"network-nodes.tsv")}==selected
        edges={frozenset((r["source"],r["target"])):float(r["weight"]) for r in rows(graph/"network-edges.tsv")};expected_edges={}
        for a,b in itertools.combinations(sorted(selected),2):
            A=set(data["sets"][a]);B=set(data["sets"][b]);similarity=len(A&B)/len(A|B)
            if similarity>=.4:expected_edges[frozenset((a,b))]=similarity
        assert edges==expected_edges
        verify_go(project,folder)
        for curve in rows(folder/"curve-plots.tsv"):
            ref=expected[curve["term_id"]];points=rows(folder/curve["curve"]);assert len(points)==len(data["genes"])
            for row,value in zip(points,ref["curve"]):assert math.isclose(float(row["running_es"]),value,abs_tol=EPS)
            png=(folder/curve["png"]).read_bytes();pdf=(folder/curve["pdf"]).read_bytes();assert png[:8]==b"\x89PNG\r\n\x1a\n" and pdf.startswith(b"%PDF-")
            width,height=struct.unpack(">II",png[16:24]);assert width>=640 and height>=400 and len(pdf)>1000
        if mode=="exact":assert all(d.get("reused_from_cache") is True for _,d in records)
        if mode in ("primary-meta","primary-meta-auto"):
            assert metadata["details"]["source_FDR_filter_applied"] is False and metadata["details"]["ranked_genes"]==10
            _,meta=one(project,"meta_analysis");assert meta["details"]["analysis_executed_by_jlinalg"] is False
            assert all(float(r["p_value"])>.05 for r in rows(project/"meta.tsv"))
        if mode=="no-eligible-terms":assert metadata["details"]["method_report"]["performed"] is False
        cli(jar,"verify","--project",project)
    verifier=root/"workflow-verifier-source.py";assert sha(verifier)==software["verifier_sha256"]
    report={"jar_sha256":sha(jar),"verifier_sha256":sha(verifier),"exact_ES_NES_tails_leading_edges_independently_reconstructed":True,
            "Monte_Carlo_agrees_with_exact_reference_within_declared_six_SE_bound":True,"Monte_Carlo_counts_resolution_and_nonzero_P_verified":True,
            "complete_term_BH_BY_and_network_selection_preserved":True,"provided_meta_reused_all_nonsignificant_features_ranked":True,
            "automatic_meta_inference_completed_GSEA_and_pathway_modules":True,"scenarios":len(MODES),
            "gene_set_to_network_to_joint_module_GO_verified":True,"selected_rankings_ties_and_requested_phenotype_null_wait":True,
            "no_eligible_terms_complete_without_false_test_claim":True,"PNG_PDF_curves_match_numeric_reference":True,
            "input_hashes_unchanged":True,"native_resume_reuses_all_operations":True,"project_local_resources":True,"external_requests":0,
            "Broad_fgsea_or_R_numerical_parity_tested":False,"real_biological_or_AI_appraisal":False,"full_pipeline_conformance":False}
    report_file=root/"packaged-verification.json"
    if report_file.exists():assert json.loads(report_file.read_text())==report,"Historical acceptance must not be rewritten for different software"
    else:write(report_file,report)
    print(json.dumps(report,indent=2))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True)
    args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir(parents=True,exist_ok=False);jar=pin_software(root,jar)
        for mode in MODES:
            project=root/mode;generate(project,mode);cli(jar,"auto",project/"study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
        cli(jar,"auto",root/"exact/study.json","--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture")
    verify(root,jar)
