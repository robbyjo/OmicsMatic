#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify a separately reviewed application transition without patching old gates."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import traceback
import uuid
import zipfile

sys.dont_write_bytecode=True
MODULE=Path(__file__).resolve().parent
ROOT=MODULE.parents[2]
sys.path.insert(0,str(MODULE.parent))
from catalog_source_transition.audit_source import BASELINE,TARGET,META,rawpin,save,tree
from catalog_source_transition.review_notes import JAVA,CATEGORIES

GUIDE="docs/literature/APPLICATION_SOURCE_TRANSITION_20261115.md"
EVIDENCE="docs/literature/application-source-transition-20261115"
MODULE_PATH="tools/literature/catalog_source_transition"
ACTION=("This is a separately reviewed application/source transition with zero new tool credit. "
        "The original TPMCalculator/SingleR verifier remains unchanged and still refuses the merged tree. "
        "It is run only in the exact clean historical checkout. No scientific source, Git result or verifier "
        "is overlaid or monkeypatched. Saved readback is not fresh original-tool computation. "
        "SKIP3 skips unavailable prerequisites; supply the named permitted checkout/cache/tool and retry NEW paths. "
        "FAIL2 admits no complete transition; retain all original records, read the named cause and retry NEW paths. "
        "Do not refresh old evidence/source hashes to suppress a refusal. Whole-tool, author-environment, "
        "biological and whole-paper validity remain unqualified; the full250-paper goal remains open.")
STATUS="PASS_REVIEWED_APPLICATION_SOURCE_TRANSITION_AND_UNCHANGED_PREDECESSOR_CATALOG"
COUNTS=dict(papers=250,named_tools=446,observations=2690,reviewed_uses=1197,unreviewed_uses=1493,
            new_scoped_integrations=122,native_integrations=21,upstream_integrations=101,
            including_existing_adapters=125,missing=265,registry_definitions=485)


def load(path):return json.loads(Path(path).read_bytes())
def pin(path):return rawpin(path.read_bytes()) # Preserve a declared metadata-only view.
def git(root,*args):return subprocess.check_output(["git",*args],cwd=os.fspath(root),stderr=subprocess.PIPE)


def current_source_guard(root,anchor=TARGET,extra_excluded=()):
    try:git(root,"cat-file","-e",anchor+"^{commit}")
    except subprocess.CalledProcessError as e:
        raise FileNotFoundError("Reviewed Git commit is unavailable; supply the permitted complete Git history: "+anchor) from e
    excluded=[*META,MODULE_PATH,GUIDE,EVIDENCE,*extra_excluded]
    command=["git","diff","--exit-code","--diff-filter=CDMRTUXB",anchor,"--","."]+[":(exclude)"+p for p in excluded]
    r=subprocess.run(command,cwd=os.fspath(root),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if r.returncode:
        raise ValueError("Unreviewed preceding tracked source changed or reviewed anchor unavailable; retain it and create a separate reviewed transition")
    return dict(anchor=anchor,source_overlays=False,legacy_baseline_changed=False)


def require_pins(root,pins):
    for name,identity in pins.items():
        if pin(root/name)!=identity:raise ValueError("Reviewed source or evidence differs: "+name)


def require_checkout_pins(root,pins):
    for name,record in pins.items():
        if pin(root/name) not in record["allowed_file_identities"]:
            raise ValueError("Unrecorded checkout bytes differ: "+name)
        actual=git(root,"hash-object","--path="+name,"--",name).decode().strip()
        if actual!=record["git_object"]:
            raise ValueError("Declared exact checkout conversion no longer matches its Git blob: "+name)


def run_baseline(baseline,out,rescan):
    baseline=Path(baseline).resolve()
    if not baseline.is_dir():raise FileNotFoundError("Exact clean baseline checkout is missing: "+str(baseline))
    try:head=git(baseline,"rev-parse","HEAD").decode().strip()
    except subprocess.CalledProcessError as e:
        raise FileNotFoundError("Exact historical Git checkout is unavailable: "+str(baseline)) from e
    if head!=BASELINE:
        raise ValueError("Exact qualified historical commit required")
    if git(baseline,"status","--porcelain=v1","-z","--untracked-files=all"):
        raise ValueError("Historical checkout has tracked/untracked changes; preserve them and provide a separate clean checkout")
    destination=baseline / "build" / ("application-source-transition-"+uuid.uuid4().hex)
    command=[sys.executable,"-X","utf8","-B","tools/literature/tpmcalculator_catalog/catalog.py","--verify","--out",str(destination)]
    if rescan:command.append("--rescan-sources")
    with (out / "baseline.stdout").open("xb") as so,(out / "baseline.stderr").open("xb") as se:
        r=subprocess.run(command,cwd=baseline,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=3600)
    save(out / "baseline.process.json",dict(command=command,exit_code=r.returncode,baseline=BASELINE,
        original_verifier_unmodified=True,output=str(destination),primary_sources_rescanned=rescan))
    if r.returncode:
        if r.returncode==3:raise FileNotFoundError("Historical verifier skipped a prerequisite; read baseline.stdout and its retained FAILURE.json")
        raise ValueError("Unmodified historical catalog verifier refused; read baseline.stdout and its original retained records")
    result=load(destination / "COMPLETED.json")
    if result["status"]!="PASS_NATIVE_TPMCALCULATOR_AND_IDEP_PRIMARY_CATALOG_ADMISSION" or any(result[k]!=v for k,v in COUNTS.items()):
        raise ValueError("Complete historical predecessor qualification/scope differs")
    shutil.copyfile(destination / "COMPLETED.json",out / "BASELINE-COMPLETED.json")
    if git(baseline,"status","--porcelain=v1","-z","--untracked-files=all"):
        raise ValueError("Historical sources changed while verifying")
    return result


def load_catalog_registry(root=ROOT):
    from tpmcalculator_catalog.catalog import load_catalog_registry as previous
    return previous(root)


def verify(root=ROOT,baseline_checkout=None,out=None,rescan=False):
    if baseline_checkout is None or out is None:
        raise FileNotFoundError("Provide the exact clean 357c9301 checkout and a NEW owned output for the unmodified predecessor verifier")
    proof=load(root/EVIDENCE/"QUALIFICATION.json")
    if proof["status"]!=STATUS or proof["baseline"]!=BASELINE or proof["reviewed_target"]!=TARGET:
        raise ValueError("Exact reviewed transition qualification required")
    actual_own={p.relative_to(ROOT).as_posix() for p in MODULE.iterdir() if p.is_file() and
                (p.suffix in [".py",".java"] or p.name==".gitattributes")}
    expected_own={n for n in proof["source_pins"] if n.startswith(MODULE_PATH+"/")}
    if actual_own!=expected_own:
        raise ValueError("Complete reviewed transition implementation membership differs")
    require_pins(root,proof["source_pins"]);require_pins(root,proof["evidence_pins"])
    require_checkout_pins(root,proof["checkout_source_pins"])
    current_source_guard(root)
    if {n:pin(root/n) for n in META}!=proof["catalog_metadata_pins"]:
        raise ValueError("Eight complete predecessor metadata files differ from the reviewed scope")
    before=run_baseline(baseline_checkout,out,rescan)
    if {n:pin(Path(baseline_checkout)/n) for n in META}!=proof["catalog_metadata_pins"]:
        raise ValueError("Clean historical checkout metadata differs from the exact reviewed predecessor")
    registry=load_catalog_registry(root)
    if registry!=load(root/EVIDENCE/"REGISTRY.json") or len(registry)!=485:
        raise ValueError("Complete original registry definitions differ")
    # These are preserved application/source qualifications, not an assertion
    # that the old legacy command now passes on the merged worktree.
    return dict(status=STATUS,**COUNTS,baseline=BASELINE,reviewed_target=TARGET,
        predecessor=before["status"],historical_predecessor_reverified=True,primary_sources_rescanned=rescan,
        catalog_metadata_and_registry_unchanged=True,legacy_verifier_passes_merged_tree=False,
        preceding_scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False,
        fresh_original_tool_calls_repeated_by_this_gate=False,new_scoped_integration_credit=0,
        application_tests=proof["application_tests"],whole_tool_qualified=False,whole_paper_reproduced=False,
        whole_goal_complete=False)


def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument("--verify",required=True,action="store_true")
    p.add_argument("--baseline-checkout",type=Path,required=True)
    p.add_argument("--rescan-sources",action="store_true")
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args();out=None
    try:
        destination=a.out.resolve()
        if not destination.is_relative_to(ROOT / "build"):
            raise ValueError("Use a NEW output within this checkout's build directory")
        destination.mkdir(parents=True,exist_ok=False);out=destination
        (out / "WARNINGS.md").write_text("# Warnings and required actions\n\n"+ACTION+"\n",encoding="utf-8")
        result=verify(ROOT,a.baseline_checkout,out,a.rescan_sources)
        save(out / "COMPLETED.json",result);print(result["status"],flush=True);return 0
    except BaseException as e:
        code=3 if isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2
        label="SKIP3_APPLICATION_SOURCE_TRANSITION_UNAVAILABLE" if code==3 else "FAIL2_APPLICATION_SOURCE_TRANSITION_UNADMITTED"
        if out is not None:
            try:
                if (out / "COMPLETED.json").exists():(out / "COMPLETED.json").rename(out / ("COMPLETION-INVALIDATED-"+uuid.uuid4().hex+".json"))
                save(out / "FAILURE.json",dict(status=label,cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
            except BaseException as recording:print("FAIL2_RECORDING_UNAVAILABLE",repr(recording),flush=True);code=2
        print(label,repr(e),"\n"+ACTION,flush=True);return code


if __name__=="__main__":raise SystemExit(main())
