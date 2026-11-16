#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve the exact reviewed transition and actual validation; never refresh old gates."""
from pathlib import Path
import argparse
import collections
import json
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from catalog_source_transition.bridge import *


def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument("--records",type=Path,required=True)
    a=p.parse_args();records=a.records.resolve();baseline=records / "baseline-checkout"
    evidence=ROOT/EVIDENCE
    if evidence.exists():raise FileExistsError("Preserve the previous transition; use a separately versioned review")
    if git(ROOT,"rev-parse","HEAD").decode().strip()!=TARGET:
        raise ValueError("Bootstrap review requires the exact declared application anchor")
    inventory=load(records / "source-inventory-2/CHANGE-INVENTORY.json")
    members=load(records / "source-inventory-2/JAVA-MEMBER-DELTA.json")
    if inventory["baseline"]!=BASELINE or inventory["target"]!=TARGET or inventory["unknown"]:
        raise ValueError("Complete exact source inventory with no uncategorized file required")
    before,after=tree(BASELINE),tree(TARGET)
    expected={name for name in set(before)|set(after) if before.get(name)!=after.get(name)}
    if {row["path"] for row in inventory["changes"]}!=expected or len(expected)!=174:
        raise ValueError("Every actual before/after source change must be reviewed")
    changes=[];java=set()
    for row in inventory["changes"]:
        name=row["path"]
        if row["category"] not in CATEGORIES or row["status"]=="D":
            raise ValueError("Unreviewed source category/deletion: "+name)
        for label,root,tree_value in [("before",baseline,before.get(name)),("after",ROOT,after.get(name))]:
            if tree_value is None:
                if row[label] is not None:raise ValueError("Invented source identity")
                continue
            if any(row[label][k]!=v for k,v in tree_value.items()) or pin(root/name)!=row[label]["working_file_identity"]:
                raise ValueError("Actual reviewed before/after source identity differs: "+name)
        note=CATEGORIES[row["category"]]
        if name.startswith("src/main/java/") and name.endswith(".java"):
            stem=Path(name).stem
            if stem not in JAVA:raise ValueError("Missing explicit Java-class review: "+name)
            java.add(stem);note=JAVA[stem]
        changes.append(dict(**row,review_disposition=note))
    if java!=set(JAVA) or len(java)!=27 or members["source_files"]!=48:
        raise ValueError("All 27 changed/added main Java classes and both sides of modifications required")
    if pin(MODULE/"SourceMembers.java")!=members["parser_source"]:
        raise ValueError("Actual JDK syntax inventory used a different parser")
    for name in META:
        if pin(ROOT/name)!=pin(baseline/name):raise ValueError("Catalog metadata differs")
    baseline_process=load(records / "baseline-catalog.process.json")
    baseline_result=load(baseline / "build/source-transition-baseline-check-1/COMPLETED.json")
    if baseline_process["exit_code"] or not baseline_process["original_verifier_unmodified"] or any(baseline_result[k]!=v for k,v in COUNTS.items()):
        raise ValueError("Actual unmodified complete historical catalog qualification required")
    if baseline_result["primary_sources_rescanned"] is not True:
        raise ValueError("Complete saved primary-source rescan required")
    if git(baseline,"status","--porcelain=v1","-z","--untracked-files=all"):
        raise ValueError("Historical source checkout changed")
    regression=load(records / "regression.process.json")
    report=load(records / "REGRESSION-SUMMARY.json")
    if regression["exit_code"] or report["counts"]!={"tests":276,"failures":0,"errors":0,"skipped":0} or len(report["suites"])!=13:
        raise ValueError("Complete actual 276-test / 13-suite current regression required")
    if b"> Task :test\r\n" not in (records / "regression.stdout").read_bytes() and b"> Task :test\n" not in (records / "regression.stdout").read_bytes():
        raise ValueError("An actual executed test task, rather than only restored test output, is required")
    if b"BUILD SUCCESSFUL" not in (records / "regression.stdout").read_bytes():raise ValueError("Current application build failed")
    dependency=load(records / "jdeps-actual-2.process.json")
    dependency_summary=load(records / "JDEPS-ACTUAL-SUMMARY.json")
    if dependency["exit_code"] or not dependency["actual_input_file_verified"] or dependency_summary["class_level_incoming_edges"]!=253:
        raise ValueError("Actual CLI artifact class dependency inventory unavailable")
    jar=records / "regression-build/root/cli/omicsmatic-0.3.7.jar"
    if pin(jar)!=dependency["input_file"]:raise ValueError("Actual analyzed executable JAR differs")
    unchanged_json=load(records / "UNCHANGED-SHARED-JSON-METHOD.json")
    if not unchanged_json["method_body_and_signature_equal"] or len(unchanged_json["observations"])!=2:
        raise ValueError("The shared JSON method must remain unchanged")
    for change in members["members"]:
        if change["path"].endswith("/StudyAssistance.java") and change["kind"] in ["FIELD","BLOCK","TYPE","UNIT"]:
            raise ValueError("Shared JSON class imports/fields/initialization changed outside the reviewed method-only scope")
    related=load(records / "variant-regression.process.json")
    variant_counts=collections.Counter();variant_suites=[]
    for result_file in (records / "variant-regression/test-results").glob("TEST-*.xml"):
        result=ET.parse(result_file).getroot();variant_suites.append(result.attrib["name"])
        for key in ["tests","failures","errors","skipped"]:variant_counts[key]+=int(result.attrib.get(key,0))
    if related["exit_code"] or variant_suites!=["org.jlinalg.cli.VariantToolsCliTest"] or variant_counts["tests"]<=0 or any(variant_counts[k] for k in ["failures","errors","skipped"]):
        raise ValueError("Actual non-study VariantToolsCli consumer regression must pass")
    controls=load(ROOT / "build/application-source-transition-controls-3/COMPLETED.json")
    if controls["status"]!="PASS_FIFTEEN_SYNTHETIC_SOURCE_METADATA_VERIFIER_VIEW_AND_CHECKOUT_CONTROLS" or len(controls["cases"])!=15:
        raise ValueError("All fifteen source/metadata/verifier/view/checkout controls required")
    # Prior application evidence is retained and its complete Java/resource/test
    # source set is compared independently with the reviewed application anchor.
    subprocess.run(["git","diff","--exit-code","b5e4c7fd4a47867d16e974ef446908b8d430ac67",TARGET,"--","src","build.gradle","settings.gradle","gradle","gradlew","gradlew.bat"],cwd=ROOT,check=True)
    prior=collections.Counter()
    with zipfile.ZipFile(records / "prior-1159-application-tests.zip") as z:
        for name in z.namelist():
            if name.startswith("build/test-results/test/TEST-") and name.endswith(".xml"):
                suite=ET.fromstring(z.read(name))
                for key in ["tests","failures","errors","skipped"]:prior[key]+=int(suite.attrib.get(key,0))
    if dict(prior)!={"tests":1159,"failures":0,"errors":0,"skipped":0}:raise ValueError("Complete retained prior application test record differs")
    registry=load(records / "registry-baseline.json")
    if (records / "registry-baseline.json").read_bytes()!=(records / "registry-current.json").read_bytes() or registry!=load_catalog_registry(ROOT) or len(registry)!=485:
        raise ValueError("All complete before/after registry definitions must agree")
    provenance=load(ROOT / "src/main/resources/org/jlinalg/study/math/katex/provenance.json")
    for name,digest in provenance["files"].items():
        if pin(ROOT / "src/main/resources/org/jlinalg/study/math/katex" / name)["sha256"]!=digest:
            raise ValueError("Declared KaTeX renderer/license identity differs")
    original=ROOT / "build/main-publication-catalog-3/FAILURE.json"
    if not original.is_file():
        raise FileNotFoundError("Original merged-main refusal is unavailable; retain it before preserving the transition")
    refusal=load(original)
    if refusal["status"]!="FAIL2_TPMCALCULATOR_CATALOG_UNADMITTED" or "Preceding tracked source changed" not in refusal["cause"]:
        raise ValueError("The original actual merged-main source refusal is required")
    current_source_guard(ROOT)
    evidence.mkdir()
    (evidence / ".gitattributes").write_bytes(b"* -text whitespace=cr-at-eol\n")
    save(evidence / "SOURCE-REVIEW.json",dict(baseline=BASELINE,reviewed_target=TARGET,
        reviewer="Codex source review; not a human approval or biological review",changes=changes,
        all_changed_files_accounted_for=True,semantic_equivalence_of_application_not_claimed=True,
        preceding_scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False))
    save(evidence / "REGISTRY.json",registry)
    save(evidence / "GUARD-CONTROLS.json",controls)
    save(evidence / "CLASS-DEPENDENCIES.json",dependency_summary)
    save(evidence / "UNCHANGED-SHARED-JSON-METHOD.json",unchanged_json)
    shutil.copyfile(records / "source-inventory-2/JAVA-MEMBER-DELTA.json",evidence / "JAVA-MEMBER-DELTA.json")
    shutil.copyfile(records / "source-inventory-2/JAVA-MEMBER-SUMMARY.json",evidence / "JAVA-MEMBER-SUMMARY.json")
    shutil.copyfile(baseline / "build/source-transition-baseline-check-1/COMPLETED.json",evidence / "BASELINE-COMPLETED.json")
    # Preserve the original merged-main refusal; do not relabel it a passing run.
    shutil.copyfile(original,evidence / "ORIGINAL-MERGED-MAIN-REFUSAL.json")
    validation=dict(current_focused=report,current_variant_consumer=dict(counts=dict(variant_counts),suites=variant_suites),prior_application=dict(prior),
        prior_Java_test_resource_and_build_sources_identical_to_reviewed_target=True,
        current_compile_tasks_used_Gradle_cache=True,current_test_task_executed=True,
        library_executable_and_website_checks_passed=True,
        actual_record_identities={name:pin(records/name) for name in ["baseline-catalog.process.json","baseline-catalog.stdout","baseline-catalog.stderr",
            "regression.process.json","regression.stdout","regression.stderr","prior-1159-application-tests.zip",
            "jdeps.process.json","jdeps.stdout","jdeps.stderr","JDEPS-INITIAL-INVALID-PATH.json",
            "jdeps-actual-2.process.json","jdeps-actual-2.stdout","jdeps-actual-2.stderr",
            "variant-regression.process.json","variant-regression.stdout","variant-regression.stderr"]},
        initial_dependency_attempt_invalid=True,initial_dependency_attempt_not_used_as_success_evidence=True,
        new_scoped_integration_credit=0,whole_goal_complete=False)
    save(evidence / "APPLICATION-VALIDATION.json",validation)
    own={p.relative_to(ROOT).as_posix():pin(p) for p in MODULE.iterdir() if p.is_file() and (p.suffix in [".py",".java"] or p.name==".gitattributes")}
    own[GUIDE]=pin(ROOT/GUIDE)
    with zipfile.ZipFile(evidence / "own-source-snapshot.zip","x",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in own:z.writestr(name,(ROOT/name).read_bytes())
        z.writestr("LICENSE-project.txt",(ROOT / "LICENSE").read_bytes())
    (evidence / "README.md").write_text("# Reviewed application-source transition\n\n"+ACTION+
        "\n\nThe original baseline qualification is preserved; ORIGINAL-MERGED-MAIN-REFUSAL.json remains a failure. "
        "The new route requires its own exact source/evidence pins, reviewed anchor, unchanged metadata/registry and actual original-baseline verification. "
        "Detailed raw application validation/failed source-audit records remain private under build/catalog-source-transition/20261115-review-1. "
        "No original upstream source, private scientific RDS or primary-paper text is redistributed here.\n",encoding="utf-8")
    (evidence / "WARNINGS.md").write_text("# Warnings and required actions\n\n"+ACTION+"\n",encoding="utf-8")
    evidence_pins={p.relative_to(ROOT).as_posix():pin(p) for p in evidence.iterdir() if p.is_file()}
    # Exact raw identities of the reviewed modifications/additions supplement
    # the Git guard; CRLF identities are retained rather than silently refreshed.
    reviewed={row["path"]:row["after"]["working_file_identity"] for row in changes if row["after"]["checkout_conversion"] is None}
    checkout={row["path"]:dict(git_object=row["after"]["git_object"],
        allowed_file_identities=[row["after"]["working_file_identity"],row["after"]["git_blob_identity"]],
        conversion=row["after"]["checkout_conversion"]) for row in changes if row["after"]["checkout_conversion"] is not None}
    if len(checkout)!=10:raise ValueError("The ten explicitly inventoried checkout forms are required")
    save(evidence / "QUALIFICATION.json",dict(status=STATUS,**COUNTS,baseline=BASELINE,reviewed_target=TARGET,
        catalog_metadata_pins={n:pin(ROOT/n) for n in META},source_pins={**own,**reviewed},checkout_source_pins=checkout,evidence_pins=evidence_pins,
        application_tests=report["counts"],historical_predecessor_reverified_at_preparation=True,
        legacy_verifier_passes_merged_tree=False,preceding_scientific_verifiers_changed=False,
        new_scoped_integration_credit=0,whole_goal_complete=False))
    print("PRESERVED_SEPARATELY_REVIEWED_APPLICATION_TRANSITION_REQUIRES_FRESH_BRIDGE_VERIFICATION",flush=True)


if __name__=="__main__":main()
