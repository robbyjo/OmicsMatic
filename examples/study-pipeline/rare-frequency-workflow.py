"""Offline acceptance for fixed-panel external ALT-frequency rare-set eligibility.

run NEW_DIRECTORY --jar EXECUTABLE_JAR
verify DIRECTORY --jar EXECUTABLE_JAR

Synthetic count panels test contracts, not actual gnomAD releases, population
representativeness, model calibration, pathogenicity or scientific conclusions.
Uses the adjacent rare-variant-workflow.py fixture generator, Python stdlib and
PyYAML. No external resource requests or AI calls are made.
"""
import argparse
import csv
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import shutil
import subprocess
import yaml

HEADER = "variant_id\tpopulation\tallele_count\tallele_number\tfilter\tgenome_build\n"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dump(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def load(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def panel():
    text = HEADER
    for pos, ac, an, other, other_an in ((100, 0, 10000, 0, 10000), (110, 200, 10000, 0, 100000),
                                       (120, 100, 10000, 50, 10000), (130, 1, 10000, 1, 10000), (140, 1, 10000, 1, 10000)):
        for pop, count, number in (("afr", ac, an), ("nfe", other, other_an)):
            text += f"1:{pos}:A:G\t{pop}\t{count}\t{number}\t{'RF' if pos == 140 else 'PASS'}\tGRCh38\n"
    return text


def generate(project, kind="native"):
    spec = importlib.util.spec_from_file_location("rare_base", Path(__file__).with_name("rare-variant-workflow.py"))
    base = importlib.util.module_from_spec(spec); spec.loader.exec_module(base)
    base.generate(project, kind)
    study = load(project / "study.yaml")
    rare = study["hypotheses"][0]["analysis"]["rare_variants"]
    rare.update(tests=["burden"], primary_test="burden", weights="equal", simulations=32,
                external_frequency={"file": "external-frequency.tsv", "source": "synthetic-panel", "release": "v1",
                                    "genome_build": "GRCh38", "populations": ["afr", "nfe"], "minimum_allele_number": 1000,
                                    "rationale": "Fixed synthetic population panel, unrelated to phenotype results."})
    if kind == "unsigned":
        rare.update(tests=["skat"], primary_test="skat")
    (project / "external-frequency.tsv").write_text(panel(), encoding="utf-8")
    save(project, study)
    return study


def save(project, study):
    dump(project / "study.yaml", study)
    dump(project / "fixture-input-sha256.json", {p.name: sha(p) for p in project.iterdir()
                                               if p.is_file() and p.name != "fixture-input-sha256.json"})


def run(project, jar, label):
    study = load(project / "study.yaml")
    inputs = {"study.yaml", study["hypotheses"][0]["analysis"]["rare_variants"]["external_frequency"]["file"]}
    for dataset in study["datasets"]:
        inputs.update(dataset["files"].values())
    for result in study["hypotheses"][0]["analysis"].get("supplied_results",[]):inputs.update(result["files"].values())
    inputs = {name:sha(project/name) for name in sorted(inputs) if (project/name).is_file()}
    archive = project / "input-editions" / label; archive.mkdir(parents=True)
    for name in inputs:
        output = archive/name; output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(project/name,output)
    command = ["java", "-jar", str(jar), "study", "auto", str(project / "study.yaml"), "--project", str(project), "--offline"]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8")
    (project / f"{label}-stdout.yaml").write_text(result.stdout, encoding="utf-8")
    (project / f"{label}-stderr.txt").write_text(result.stderr, encoding="utf-8")
    output = yaml.safe_load(result.stdout) if result.stdout else {}
    assert all(sha(project/name)==digest for name,digest in inputs.items())
    return {"project": project.name, "label": label, "exit_code": result.returncode, "state": output.get("state"), "run_id": output.get("run_id"), "input_sha256": inputs}


def entries(project, run_id):
    index = json.loads((project / "index/evidence-summary.json").read_text(encoding="utf-8"))
    return [e for e in index["entries"] if e["run_id"] == run_id]


def operation(project, receipt, kind):
    return next(e for e in entries(project, receipt["run_id"]) if e["operation_type"] == kind)


def folder(project, entry):
    return (project / entry["manifest"]).parent


def score_rows(path):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        lines = [line for line in stream if not line.startswith("##")]
    lines[0] = lines[0].lstrip("#")
    return list(csv.DictReader(lines, delimiter="\t"))


def numerical_checks(project, receipt):
    records = entries(project, receipt["run_id"])
    scores = {e["details"]["dataset"]: folder(project, e) for e in records if e["operation_type"] == "rare_variant_scores"}
    rare = folder(project, operation(project, receipt, "rare_variant_analysis"))
    checks = 0
    for row in table(rare / "rare-results.tsv"):
        cohorts = row["included_datasets"].split(",") if "included_datasets" in row else None
        scope = row["scope"]
        cohorts = [scope.split(":", 1)[1]] if scope.startswith("cohort:") else ["a", "b"] if scope == "discovery" else ["a", "b", "v"]
        pos = 120 if row["feature_id"] == "GENE1" else 100
        u = variance = 0.
        for cohort in cohorts:
            marginal = {int(r["POS"]): r for r in score_rows(scores[cohort] / "cohort.score.txt.gz")}
            covariance = {int(r["CURRENT_POS"]): r for r in score_rows(scores[cohort] / "cohort.cov.txt.gz")}
            cov = covariance[pos]
            values = dict(zip(map(int, cov["MARKERS_IN_WINDOW"].split(",")), map(float, cov["COV_MATRICES"].split(","))))
            u += float(marginal[pos]["U_STAT"])
            variance += values[pos] * int(marginal[pos]["N_INFORMATIVE"])
        for key, expected in (("beta", u / variance), ("se", 1 / math.sqrt(variance)), ("p_value", math.erfc(abs(u) / math.sqrt(2 * variance)))):
            assert math.isclose(float(row[key]), expected, rel_tol=1e-9, abs_tol=1e-280), (row, key, expected)
            checks += 1
    return checks


def execute(root, jar):
    root.mkdir(parents=True, exist_ok=False)
    receipts = []
    native = root / "native"; study = generate(native)
    receipts.append(run(native, jar, "initial"))
    assert receipts[-1]["state"] == "completed", receipts[-1]
    receipts.append(run(native, jar, "replay"))
    assert receipts[-1]["state"] == "completed"
    # Compatible supplied score/covariance files still enter the missing native mask/pool stages.
    imported = root / "scores"; imported.mkdir(); source = receipts[0]
    imported_study = json.loads(json.dumps(study)); imported_study["project"]["id"] = "imported-frequency"
    imported_study["hypotheses"][0]["analysis"]["goals"] = ["rare_variant_analysis"]
    shutil.copyfile(native / "external-frequency.tsv", imported / "external-frequency.tsv")
    for dataset in imported_study["datasets"]:
        id_ = dataset["id"]; entry = next(e for e in entries(native,source["run_id"]) if e["operation_type"] == "rare_variant_scores" and e["details"]["dataset"] == id_)
        location = folder(native,entry)
        for suffix in ("score.txt.gz", "cov.txt.gz", "score.txt.gz.tbi", "cov.txt.gz.tbi"):
            shutil.copyfile(location / ("cohort." + suffix), imported / (id_ + "." + suffix))
        shutil.copyfile(native / (id_ + "-annotation.tsv"), imported / (id_ + "-annotation.tsv"))
        dataset.update(stage="rare_score_summary", files={"scores":id_+".score.txt.gz", "covariance":id_+".cov.txt.gz", "variant_annotation":id_+"-annotation.tsv"}, metadata={"minimum_info_applied":.8})
    save(imported,imported_study); receipts.append(run(imported,jar,"initial")); assert receipts[-1]["state"] == "completed",receipts[-1]
    # A missing population first holds the set branch, while single-variant association completes.
    missing = root / "missing"; generate(missing)
    (missing/"external-frequency.tsv").write_text(panel().replace("1:100:A:G\tnfe\t0\t10000\tPASS\tGRCh38\n", ""),encoding="utf-8")
    receipts.append(run(missing,jar,"missing")); assert receipts[-1]["state"] == "waiting_for_input",receipts[-1]
    original = {e["operation_id"]: (e["cache_key"],e["output_sha256"]) for e in entries(missing,receipts[-1]["run_id"]) if e["operation_type"] == "association"}
    old_outputs = {str(p.relative_to(missing)):sha(p) for p in (missing/"runs").rglob("*") if p.is_file()}
    (missing/"external-frequency.tsv").write_text(panel(),encoding="utf-8")
    receipts.append(run(missing,jar,"completed")); assert receipts[-1]["state"] == "completed",receipts[-1]
    for e in entries(missing,receipts[-1]["run_id"]):
        if e["operation_type"] == "association":
            assert (e["cache_key"],e["output_sha256"]) == original[e["operation_id"]]
            assert load(missing/e["manifest"])["reused_from_cache"] is True
    for name,digest in old_outputs.items(): assert sha(missing/name) == digest,name
    # A scientific source revision at the same path rebuilds masks/scores, preserving core fits.
    revised = root / "revision"; generate(revised)
    receipts.append(run(revised,jar,"before")); assert receipts[-1]["state"] == "completed"
    before = operation(revised,receipts[-1],"rare_variant_masks")
    (revised/"external-frequency.tsv").write_text(panel().replace("1:110:A:G\tafr\t200\t10000", "1:110:A:G\tafr\t1\t10000"),encoding="utf-8")
    receipts.append(run(revised,jar,"after")); assert receipts[-1]["state"] == "completed"
    assert before["cache_key"] != operation(revised,receipts[-1],"rare_variant_masks")["cache_key"]
    for name, replace in {
        "zero-coverage":lambda s:s.replace("1:100:A:G\tafr\t0\t10000", "1:100:A:G\tafr\t0\t0"),
        "allele-mismatch":lambda s:s.replace("1:100:A:G", "1:100:G:A"),
        "missing-count":lambda s:s.replace("1:100:A:G\tafr\t0\t10000", "1:100:A:G\tafr\tNA\t10000"),
        "invalid-count":lambda s:s.replace("1:100:A:G\tafr\t0\t10000", "1:100:A:G\tafr\t10001\t10000"),
        "row-build":lambda s:s.replace("GRCh38", "GRCh37"),
        "high-alt":lambda s:s.replace("1:120:A:G\tafr\t100\t10000", "1:120:A:G\tafr\t9900\t10000"),
    }.items():
        project=root/name;generate(project);(project/"external-frequency.tsv").write_text(replace(panel()),encoding="utf-8")
        receipt=run(project,jar,"initial");receipts.append(receipt)
        assert receipt["state"] == ("completed" if name=="high-alt" else "failed" if name in ("invalid-count","row-build") else "waiting_for_input"),receipt
    unsigned=root/"p-only";generate(unsigned,"unsigned");receipts.append(run(unsigned,jar,"initial"));assert receipts[-1]["state"]=="waiting_for_input",receipts[-1]
    for name in ("missing-file","row-budget","empty-masks"):
        project=root/name;s=generate(project)
        if name=="missing-file":(project/"external-frequency.tsv").rename(project/"deferred-frequency.tsv")
        elif name=="row-budget":s["hypotheses"][0]["analysis"]["rare_variants"]["external_frequency"]["maximum_rows"]=2;save(project,s)
        else:(project/"external-frequency.tsv").write_text(panel().replace("PASS","RF"),encoding="utf-8")
        receipts.append(run(project,jar,"initial"));assert receipts[-1]["state"]==("completed" if name=="empty-masks" else "waiting_for_input"),receipts[-1]
    supplied=root/"completed-primary";supplied.mkdir()
    (supplied/"results.tsv").write_text("feature_id\tmask_id\ttest\tp_value\tis_primary_test\nG1\tlof\tskat\t.001\ttrue\nG2\tlof\tskat\t.5\ttrue\n",encoding="utf-8")
    supplied_study={"schema_version":1,"project":{"id":"completed-primary","title":"Completed synthetic rare results","description":"Import without reproducing upstream frequency QC."},"resources":{"network":"offline","downloads":"deny"},"datasets":[],
                    "hypotheses":[{"id":"H","description":"Reuse completed set results","outcome":"y","analysis":{"mode":"manual","goals":["rare_variant_analysis"],"rare_variants":{"external_frequency":{"enabled":True,"file":"not-needed.tsv"}},
                    "supplied_results":[{"id":"upstream","kind":"rare_variant_analysis","files":{"results":"results.tsv"},"scope":{"phenotype":"y"},"family":{"id":"all","coverage":"complete","tested_count":2,"adjustment":"BH"},"method":{"name":"Synthetic completed upstream set analysis","parameters":{"frequency_policy":"reviewed-upstream-panel"}}}]}}]}
    save(supplied,supplied_study);receipts.append(run(supplied,jar,"initial"));assert receipts[-1]["state"]=="completed",receipts[-1]
    dump(root/"execution-receipts.json",receipts)
    dump(root/"software.json",{"jar_sha256":sha(jar),"verifier_sha256":sha(Path(__file__)),"base_generator_sha256":sha(Path(__file__).with_name("rare-variant-workflow.py"))})
    verify(root,jar)


def verify(root, jar):
    assert sha(jar)==load(root/"software.json")["jar_sha256"]
    receipts=load(root/"execution-receipts.json");checks=0
    for receipt in receipts:
        project=root/receipt["project"]
        assert all(sha(project/"input-editions"/receipt["label"]/name)==digest for name,digest in receipt["input_sha256"].items())
        subprocess.run(["java","-jar",str(jar),"study","verify","--project",str(project)],check=True,capture_output=True)
        masks=[e for e in entries(project,receipt["run_id"]) if e["operation_type"]=="rare_variant_masks"]
        if masks and masks[0]["state"]=="complete":
            location=folder(project,masks[0]);metadata=load(location/"external-frequency.yaml")
            assert metadata["source_sha256"]==sha(location/"external-frequency-source.tsv")
            assert metadata["populations"]==["afr","nfe"] and metadata["minimum_allele_number"]==1000
            assert not metadata["unresolved_variants"]
            study=load(project/"input-editions"/receipt["label"]/"study.yaml")
            declared=study["hypotheses"][0]["analysis"]["rare_variants"]
            observations=table(location/"external-frequency-source.tsv")
            eligible=set()
            for mask in declared["masks"]:
                threshold=mask.get("maximum_external_af",mask.get("maximum_maf",.01))
                for variant in {r["variant_id"] for r in table(location/"external-frequency-candidates.tsv") if r["mask_id"]==mask["id"]}:
                    rows=[r for r in observations if r["variant_id"]==variant and r["population"] in metadata["populations"]]
                    assert len(rows)==len(metadata["populations"])
                    if all(r["filter"]=="PASS" and int(r["allele_number"])>=metadata["minimum_allele_number"] for r in rows) and max(int(r["allele_count"])/int(r["allele_number"]) for r in rows)<=threshold:
                        eligible.add((mask["id"],variant))
                    checks+=1
            assert {(r["mask_id"],r["variant_id"]) for r in table(location/"mask-membership.tsv")}==eligible
        elif masks:
            assert not (folder(project,masks[0])/"mask-membership.tsv").exists()
    native=root/"native";first=receipts[0];masks=folder(native,operation(native,first,"rare_variant_masks"))
    assert {r["variant_id"] for r in table(masks/"mask-membership.tsv")}=={"1:100:A:G","1:120:A:G","1:130:A:G"}
    for entry in entries(native,first["run_id"]):
        if entry["operation_type"]=="association":
            files=list((folder(native,entry)/"standard-results").glob("*.tsv"));assert len(files)==1
            assert {row["feature_id"] for row in table(files[0])}=={"v20","v100"}
    checks+=numerical_checks(native,first)
    imported=root/"scores";r=next(r for r in receipts if r["project"]=="scores")
    source=folder(native,operation(native,first,"rare_variant_analysis"))/"rare-results.tsv"
    target=folder(imported,operation(imported,r,"rare_variant_analysis"))/"rare-results.tsv"
    assert source.read_bytes()==target.read_bytes()
    for e in entries(imported,r["run_id"]):assert e["operation_type"] not in ("association","rare_variant_scores")
    project=root/"empty-masks";r=next(r for r in receipts if r["project"]=="empty-masks")
    rare=operation(project,r,"rare_variant_analysis");assert rare["details"]["empty_mask_universe"] is True
    assert rare["details"]["method_report"]["performed"] is False and rare["details"]["families"]==[]
    assert not table(folder(project,rare)/"rare-results.tsv")
    for e in entries(project,r["run_id"]):
        if e["operation_type"]=="rare_variant_scores":assert e["details"]["method_report"]["performed"] is False
    project=root/"completed-primary";r=next(r for r in receipts if r["project"]=="completed-primary")
    assert not any(e["operation_type"] in ("rare_variant_masks","rare_variant_scores","association") for e in entries(project,r["run_id"]))
    supplied=operation(project,r,"rare_variant_analysis")
    assert supplied["details"]["analysis_executed_by_jlinalg"] is False and supplied["details"]["execution_origin"]=="externally_produced"
    assert {float(row["adjusted_p"]) for row in table(folder(project,supplied)/"rare-results.tsv")}=={.002,.5}
    report={"status":"passed","cases":len(receipts),"states":{state:sum(r["state"]==state for r in receipts) for state in sorted({r["state"] for r in receipts})},"independent_eligibility_and_burden_checks":checks,"scientific_claims":False}
    dump(root/"verification.json",report);print(json.dumps(report))


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("action",choices=["run","verify"]);parser.add_argument("directory",type=Path);parser.add_argument("--jar",type=Path,required=True);args=parser.parse_args()
    (execute if args.action=="run" else verify)(args.directory.resolve(),args.jar.resolve())
