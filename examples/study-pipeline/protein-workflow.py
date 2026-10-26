"""Protein workflow acceptance using an independent PDB/PAE reader (stdlib + PyYAML).

run NEW_DIRECTORY --jar JAR [--live]; verify DIRECTORY --jar THE_PINNED_JAR.
Synthetic configured prediction is a process fixture, not AlphaFold execution.
--live additionally retrieves public human TP53 P04637 through the native CLI.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import yaml

MODES=("supplied","assessment","experimental","missing-pae","wrong-ref","wrong-sequence","partial","prediction","missing-seed","supplied-summary","auto-meta")
WAITING={"missing-pae","wrong-ref","wrong-sequence","missing-seed"}
AA=dict(zip("ALA CYS ASP GLU PHE GLY HIS ILE LYS LEU MET ASN PRO GLN ARG SER THR VAL TRP TYR".split(),"ACDEFGHIKLMNPQRSTVWY"))

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def digest(text):return hashlib.sha256(text.encode()).hexdigest()
def write(path,obj):path.write_text(json.dumps(obj,indent=2)+"\n",encoding="utf-8")
def rows(path):
    with path.open(encoding="utf-8",newline="") as stream:return list(csv.DictReader(stream,delimiter="\t"))
def table(path,columns,records):
    with path.open("w",encoding="utf-8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n");writer.writerow(columns);writer.writerows(records)
def cli(jar,*args):
    result=subprocess.run(["java","-jar",str(jar),"study",*map(str,args)],text=True,capture_output=True)
    assert result.returncode==0,result.stdout+result.stderr
    return result.stdout
def pdb():
    return "".join(f"ATOM  {i+1:5d}  CA  {name:3s} A{i+1:4d}    {i*2.:8.3f}{i*.5:8.3f}{-i*1.:8.3f}  1.00{score:6.2f}           C  \n" for i,(name,score) in enumerate(zip(("ALA","CYS","ASP","GLU"),(95,80,60,40))))
def operation(project):
    status=yaml.safe_load((project/"state/project-status.yaml").read_text(encoding="utf-8"));folder=project/"runs"/status["run_id"]/"operations"
    ops=[(p,yaml.safe_load((p/"manifest.yaml").read_text(encoding="utf-8"))) for p in folder.iterdir()]
    found=[(p,m) for p,m in ops if m["operation_type"].startswith("protein_structure")];assert len(found)==1,found
    assert not any(m["operation_type"] in ("association","participant_qc") or m["operation_type"]=="meta_analysis" and m.get("details",{}).get("analysis_executed_by_jlinalg") is not False for p,m in ops)
    return status,*found[0]
def pin(root,jar):
    (root/"software").mkdir();pinned=root/"software"/("jlinalg-"+sha(jar)+".jar");shutil.copyfile(jar,pinned);verifier=root/"workflow-verifier-source.py";shutil.copyfile(Path(__file__),verifier)
    write(root/"execution-software.json",{"jar":str(pinned.relative_to(root)),"jar_sha256":sha(jar),"verifier_sha256":sha(verifier)})
    return pinned

def generate(root,mode):
    project=root/mode;project.mkdir();(project/"model.pdb").write_text(pdb(),encoding="utf-8");(project/"sequences.fasta").write_text(">exact-sequence\nACDE\n",encoding="utf-8")
    matrix=[[0,1,2,3],[10,0,12,13],[20,21,0,23],[30,31,32,0]];write(project/"pae.json",[{"predicted_aligned_error":matrix}])
    model={"id":"synthetic-model","file":"model.pdb","format":"pdb","chain":"A","structure_kind":"other_prediction","confidence_source":"b_factor_plddt","confidence_provenance":"Synthetic supplied fixture","pae":"pae.json","pae_provenance":"Synthetic exact model/version matrix"}
    target={"feature_id":"assay-1","protein_id":"P04637","sequence_id":"exact-sequence","taxon_id":9606,"models":[model],"assay_mapping":{"status":"supplied_sequence","source":"Synthetic assay mapping","peptides":["AC","VV"]},"regions":[{"id":"N","start":1,"end":2},{"id":"C","start":3,"end":4}],"variants":[{"id":"D3N","position":3,"ref":"D","alt":"N"}]}
    policy={"manifest":"protein-manifest.yaml","sequences":"sequences.fasta","retrieve":False}
    goals=["protein_structure_assessment" if mode=="assessment" else "protein_structure"]
    if mode=="experimental":model.update(confidence_source="none",structure_kind="experimental");model.pop("pae");model.pop("pae_provenance")
    if mode=="missing-pae":model.pop("pae");policy["require_pae"]=True
    if mode=="wrong-ref":target["variants"][0]["ref"]="E"
    if mode=="wrong-sequence":(project/"sequences.fasta").write_text(">exact-sequence\nACDF\n",encoding="utf-8")
    if mode=="partial":
        (project/"model.pdb").write_text("".join(pdb().splitlines(keepends=True)[i] for i in (0,2)),encoding="utf-8");model.pop("pae");target["variants"]=[];policy["require_exact_sequence"]=False
    if mode in ("prediction","missing-seed"):
        target["models"]=[];(project/"predictor-fixture.py").write_text(PREDICTOR,encoding="utf-8")
        policy["prediction"]={"enabled":True,"command":[sys.executable,str(project/"predictor-fixture.py"),"{output_dir}","{request_json}",str(project/"model.pdb")],"input_files":["model.pdb"],"software":"synthetic-process-fixture-not-AlphaFold","software_version":"1","model_version":"synthetic-1","seeds":[0,1] if mode=="missing-seed" else [0],"msa_provenance":"None; synthetic fixture","template_provenance":"None; synthetic fixture"}
    write(project/"protein-manifest.yaml",{"schema_version":1,"targets":[target]})
    analysis={"mode":"manual","goals":goals,"protein_structure":policy}
    if mode=="supplied-summary":
        table(project/"external-protein.tsv",["feature_id","protein_id","sequence_sha256","model_id","mean_plddt","coverage"],[["assay-1","P04637",digest("ACDE"),"external-source",68.75,1]])
        analysis["supplied_results"]=[{"id":"provided","kind":"protein_structure","files":{"results":"external-protein.tsv"},"scope":{"species":"human","sequence_sha256":digest("ACDE")},"method":{"name":"Externally assessed synthetic protein model"},"limitations":["Source-declared model identity and confidence; not independently reproduced by import"]}]
    if mode=="auto-meta":
        table(project/"external-meta.tsv",["feature_id","beta","se","p_value","adjusted_p"],[["P04637",1,1,.001,.5]])
        analysis["protein_structure"]={"retrieve":False,"scope":"primary_significant","taxon_id":9606}
        analysis["supplied_results"]=[{"id":"pooled","kind":"meta_analysis","files":{"results":"external-meta.tsv"},"scope":{"species":"human","feature_id_type":"uniprot"},"method":{"name":"External synthetic selected-only effects"},"family":{"id":"source-family","coverage":"selected_only","tested_count":1000,"adjustment":"provided","adjusted_p_from_full_family":True}}]
    study={"schema_version":1,"project":{"id":"protein-"+mode,"title":"Protein workflow "+mode,"description":"Synthetic context acceptance; not a biological finding"},"resources":{"directory":"fixture-resources","network":"offline","downloads":"deny"},"datasets":[],"hypotheses":[{"id":"H","description":"Complete only missing protein context","analysis":analysis}]}
    write(project/"study.yaml",study);return project

PREDICTOR='''import hashlib,json,pathlib,shutil,sys
folder=pathlib.Path(sys.argv[1]);request=json.loads(pathlib.Path(sys.argv[2]).read_text());shutil.copyfile(sys.argv[3],folder/"predicted.pdb")
sequence="".join(line.strip() for line in (folder/"input.fasta").read_text().splitlines() if not line.startswith(">"))
assert hashlib.sha256(sequence.encode()).hexdigest()==request["sequence_sha256"]
(folder/"prediction-outputs.yaml").write_text(json.dumps({"schema_version":1,"sequence_sha256":request["sequence_sha256"],"models":[{"id":"synthetic-seed-0","file":"predicted.pdb","format":"pdb","chain":"A","seed":0,"confidence_source":"b_factor_plddt","confidence_provenance":"Synthetic fixture only"}]}))
'''

def verify_model(project,mode,folder,manifest):
    assert manifest["state"]==("waiting_for_input" if mode in WAITING else "complete"),(mode,manifest["summary"])
    if mode=="auto-meta":
        assert not rows(folder/"protein-structures.tsv");assert manifest["details"]["requested_targets"]==0;assert manifest["details"]["new_prediction_processes"]==0
        return {"provided_full_family_q_preserved":True,"source_q":.5,"raw_p":.001,"BH_not_recomputed_on_selected_row":True}
    if mode in ("wrong-ref","wrong-sequence"):
        assert not rows(folder/"protein-structures.tsv");assert (folder/"proteins/0001/models/001/file-model.pdb").is_file();return {"retained_incompatible_model":True}
    result=rows(folder/"protein-structures.tsv");assert len(result)==1
    row=result[0];assert row["sequence_sha256"]==digest("ACDE")
    if mode=="supplied-summary":assert manifest["details"]["analysis_executed_by_jlinalg"] is False;return {"external_summary_reused":True}
    model=folder/row["directory"];atoms=[]
    source=next(model.glob("file-*.pdb"))
    for line in source.read_text(encoding="utf-8").splitlines():
        if line.startswith(("ATOM  ","HETATM")) and line[12:16].strip()=="CA":atoms.append((int(line[22:26]),AA[line[17:20]],float(line[60:66]),tuple(float(line[a:b]) for a,b in ((30,38),(38,46),(46,54)))))
    coords=rows(model/"residues.tsv");assert len(coords)==len(atoms)==int(row["observed_residues"])
    for expected,actual in zip(atoms,coords):
        pos,aa,confidence,xyz=expected;assert int(actual["sequence_position"])==pos;assert actual["amino_acid"]==aa=="ACDE"[pos-1]
        assert tuple(float(actual[a]) for a in ("x","y","z"))==xyz
    assert abs(float(row["coverage"])-len(atoms)/4)<1e-12
    if mode=="experimental":assert row["mean_plddt"]=="NA" and row["confidence_residues"]=="0"
    else:
        expected=sum(a[2] for a in atoms)/len(atoms);assert abs(float(row["mean_plddt"])-expected)<1e-12
        for key,low,high in (("fraction_plddt_lt50",0,50),("fraction_plddt_50_70",50,70),("fraction_plddt_70_90",70,90),("fraction_plddt_ge90",90,101)):assert abs(float(row[key])-sum(low<=a[2]<high for a in atoms)/len(atoms))<1e-12
        assert (model/"plddt.png").read_bytes().startswith(b"\x89PNG") and (model/"plddt.pdf").read_bytes().startswith(b"%PDF")
    if (model/"region-pae.tsv").exists():
        matrix=json.loads((project/"pae.json").read_text())[0]["predicted_aligned_error"];regions={"N":range(2),"C":range(2,4)}
        for pair in rows(model/"region-pae.tsv"):
            values=[matrix[i][j] for i in regions[pair["aligned_region"]] for j in regions[pair["evaluated_region"]]]
            assert abs(float(pair["mean_pae_angstrom"])-sum(values)/len(values))<1e-12;assert float(pair["maximum_pae_angstrom"])==max(values)
    assert "https://" not in (model/"structure-view.html").read_text(encoding="utf-8")
    if mode in ("prediction","missing-seed"):
        assert manifest["details"]["new_prediction_processes"]==1
        assert (folder/"proteins/0001/prediction/batch-receipt.yaml").exists()==(mode=="prediction")
        request=json.loads((folder/"proteins/0001/prediction/request.json").read_text());assert request["software"].startswith("synthetic-")
    return {"independent_pdb_residues":len(atoms),"directional_pae_checked":(model/"region-pae.tsv").exists(),"functional_inference":False}

def live(root,jar):
    project=root/"live-public-TP53";project.mkdir();write(project/"protein-manifest.yaml",{"schema_version":1,"targets":[{"feature_id":"public-TP53-control","protein_id":"P04637","taxon_id":9606,"assay_mapping":{"status":"accession_declared","source":"Public sequence control; no assayed study participants"}}]})
    write(project/"study.yaml",{"schema_version":1,"project":{"id":"public-protein-control","title":"Public TP53 control","description":"Live retrieval validation; not a new prediction or a study association"},"resources":{"directory":"fixture-resources","network":"online","downloads":"automatic"},"datasets":[],"hypotheses":[{"id":"H","description":"Retrieve exact public canonical TP53","analysis":{"mode":"manual","goals":["protein_structure"],"protein_structure":{"manifest":"protein-manifest.yaml","retrieve":True,"require_pae":True,"require_plddt":True}}}]})
    (project/"execution.log").write_text(cli(jar,"auto",project/"study.yaml","--actor-type","external","--actor-id","public-control"),encoding="utf-8")
    status,folder,manifest=operation(project);assert status["state"]=="completed",manifest
    selected=rows(folder/"protein-structures.tsv");assert len(selected)==1;row=selected[0];assert row["model_source"]=="retrieved_AlphaFold_DB";assert manifest["details"]["new_prediction_processes"]==0
    sources=yaml.safe_load((folder/row["directory"]/"model-source.yaml").read_text(encoding="utf-8"));url=sources["declared_model"]["provenance"]["structure_url"];assert url.endswith(".cif")
    pdb_url=url[:-4]+".pdb";out=project/"independent-public-reference.pdb"
    with urllib.request.urlopen(pdb_url,timeout=60) as response:out.write_bytes(response.read(16000000))
    values=[];sequence=[]
    for line in out.read_text(encoding="utf-8").splitlines():
        if line.startswith("ATOM  ") and line[12:16].strip()=="CA":sequence.append(AA[line[17:20]]);values.append(float(line[60:66]))
    assert len(sequence)==int(row["target_length"])==393;assert digest("".join(sequence))==row["sequence_sha256"];assert abs(sum(values)/len(values)-float(row["mean_plddt"]))<.011
    (project/"verify.log").write_text(cli(jar,"verify","--project",project),encoding="utf-8")
    receipt={"reference_url":pdb_url,"reference_sha256":sha(out),"model_version":row["model_version"],"residues":len(values),"independent_pdb_mean_plddt":sum(values)/len(values),"native_cif_mean_plddt":float(row["mean_plddt"]),"new_prediction_executed":False,"source_sequence_sha256":row["sequence_sha256"]};write(project/"live-control-verification.json",receipt)

def verify(root,jar):
    software=json.loads((root/"execution-software.json").read_text());assert sha(jar)==software["jar_sha256"];assert sha(root/software["jar"])==sha(jar);assert sha(root/"workflow-verifier-source.py")==software["verifier_sha256"]
    results=[]
    for mode in MODES:
        project=root/mode;status,folder,manifest=operation(project);evidence=verify_model(project,mode,folder,manifest)
        for name,digest_value in manifest["output_sha256"].items():assert sha(folder/name)==digest_value,(mode,name)
        assert (project/"fixture-resources").resolve().is_relative_to(project.resolve())
        results.append({"case":mode,"state":manifest["state"],"evidence":evidence})
    if (root/"live-public-TP53").exists():
        project=root/"live-public-TP53";status,folder,manifest=operation(project)
        for name,digest_value in manifest["output_sha256"].items():assert sha(folder/name)==digest_value
        results.append({"case":"live-public-TP53","state":manifest["state"],"evidence":json.loads((project/"live-control-verification.json").read_text())})
    write(root/"verification.json",{"passed":True,"jar_sha256":sha(jar),"verifier_sha256":software["verifier_sha256"],"cases":results,"limits":"Independent stdlib PDB/matrix calculations; no Biopython parity, new AlphaFold inference, functional/pathogenic validation or private-data transmission was tested."});return results

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=("run","verify"));parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);parser.add_argument("--live",action="store_true");args=parser.parse_args();root=args.directory.resolve();jar=args.jar.resolve()
    if args.action=="run":
        root.mkdir();jar=pin(root,jar)
        for mode in MODES:
            project=generate(root,mode);(project/"execution.log").write_text(cli(jar,"auto",project/"study.yaml","--offline","--actor-type","external","--actor-id","synthetic-protein-fixture"),encoding="utf-8")
            if mode not in WAITING:
                (project/"replay.log").write_text(cli(jar,"auto",project/"study.yaml","--offline","--actor-type","external","--actor-id","synthetic-protein-fixture"),encoding="utf-8");assert operation(project)[2]["reused_from_cache"] is True
            (project/"verify.log").write_text(cli(jar,"verify","--project",project),encoding="utf-8")
        if args.live:live(root,jar)
    results=verify(root,jar);print(json.dumps({"passed":True,"cases":len(results),"directory":str(root),"jar_sha256":sha(jar)}))
if __name__=="__main__":main()
