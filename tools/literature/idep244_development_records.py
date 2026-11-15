#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve source snapshots and private original iDEP development evidence.

Default verification reads public metadata/own-source snapshots only. Private
readback hashes every saved payload; neither mode repeats an original fit or
authenticates the inputs/runtime. A historical source match is not silently
refreshed when current implementation files change.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=ROOT/"docs/literature/idep244-count-workflow-development"
MODULE=ROOT/"tools/literature/idep244"
VALIDATION=ROOT/"tools/literature/idep244_validation"
CORE_CASES="idep244-public-controls-2"
GUARDS="idep244-recording-guards-4"
PROVIDER="idep244-adopted-provider-4"
PRIVATE_ROOTS=[
    "idep244-adopted-provider-1","idep244-adopted-provider-2",
    "idep244-adopted-provider-3",PROVIDER,"idep244-control-inputs-1",
    "idep244-count-run-disabled-1","idep244-count-replay-disabled-1",
    "idep244-count-run-mapped-1","idep244-public-controls-1",CORE_CASES,
    "idep244-process-guards-1","idep244-process-guards-2",
    "idep244-recording-guards-1","idep244-recording-guards-2",
    "idep244-recording-guards-3",GUARDS,"idep244-failure-comparison-2",
    "idep244-failure-comparison-3","idep244-inspection-probe-3",
    "idep244-job-probe-20261115-1"]
NOTICE=(
    "DEVELOPMENT CHECKPOINT ONLY; zero catalog credit. Public-only verification "
    "has not read the original RDS payloads. Private readback checks saved file "
    "integrity, not a fresh fit, input authenticity, current runtime or biological "
    "accuracy. Original RDS/source-bearing Shiny traces remain private under the "
    "original terms. Raw failed attempts and the first failed comparison policy "
    "remain retained. Negative-control copies intentionally contain wrong results "
    "or invalidated completion markers; use only the named guard outcomes. "
    "The current headless profile can raise the original Shiny notification error. "
    "Full GUI/session coverage, wider transform/aggregation controls and the "
    "main-merge catalog-baseline integration remain unfinished. Read "
    "IDEP244_COUNTS_DEVELOPMENT.md and MAIN_MERGE_20261115_STATUS.md. "
    "SKIP3 skips a named unavailable prerequisite; FAIL2 admits no complete result. "
    "Retain original files/partials and retry NEW permitted paths after resolving "
    "the named cause. Never refresh historical hashes to accept a changed source "
    "or result. The full250-paper goal remains active and incomplete.")


def pin(path):
    h=hashlib.sha256();n=0
    with Path(path).open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""):
            h.update(b);n+=len(b)
    return dict(bytes=n,sha256=h.hexdigest())


def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def load(path):return json.loads(Path(path).read_bytes())
def save(path,value):
    with Path(path).open("xb") as f:
        f.write((json.dumps(value,indent=2,sort_keys=True,ensure_ascii=False)+"\n").encode("utf-8"))


def source_paths():
    result=[Path(__file__).resolve(),
        ROOT/"docs/literature/IDEP244_COUNTS_DEVELOPMENT.md",
        ROOT/"examples/idep244-workflow/request.json"]
    for folder in [MODULE,VALIDATION]:
        result += [p for p in folder.iterdir() if p.is_file() and
                   (p.suffix in [".py",".R",".json"] or p.name==".gitattributes")]
    return sorted(result)


def private_files(base):
    result={}
    for name in PRIVATE_ROOTS:
        root=base/name
        if not root.is_dir():raise FileNotFoundError("Missing retained private record: "+name)
        for parent,dirs,files in os.walk(root,followlinks=False):
            for n in dirs+files:
                p=Path(parent)/n
                if p.is_symlink() or (hasattr(p,"is_junction") and p.is_junction()):
                    raise ValueError("Private record links are not accepted: "+str(p))
            for n in files:
                p=Path(parent)/n;result[p.relative_to(base).as_posix()]=pin(p)
    return dict(sorted(result.items()))


def check_actual_records(base):
    sys.path.insert(0,str(MODULE))
    from Runtime import provider_check,engine_sources
    from verify import check_bundle
    provider=provider_check(base/PROVIDER)
    core=load(base/CORE_CASES/"COMPLETED.json")
    if (core["complete_replays"],core["independent_original_success_comparisons"],
        core["independent_original_error_comparisons"])!=(3,3,2):
        raise ValueError("Complete three-success/two-error original comparisons required")
    expected={"disabled":0,"mapped":0,"designed":0,"original-rank-error":2,
              "missing-reference":3,"original-no-counts":2}
    if {r["case"]:r["exit_code"] for r in core["public_cases"]}!=expected:
        raise ValueError("Every declared public case outcome is required")
    if load(base/CORE_CASES/"ENGINE-SOURCES.json")!=engine_sources():
        raise ValueError("Case suite does not cover this exact engine")
    for name in ["disabled","mapped","designed"]:
        bundle=base/CORE_CASES/name
        check_bundle(bundle)
        replay=load(base/CORE_CASES/("replay-"+name)/"COMPLETED.json")
        if not replay["complete_original_values_compared"] or not replay["saved_original_bundle_unchanged"]:
            raise ValueError("Full captured-input replay is required")
        if not load(base/CORE_CASES/("direct-"+name)/"PAIRWISE.json")["complete_values_equal"]:
            raise ValueError("Complete independent original return differs")
    for name in ["original-rank-error","original-no-counts"]:
        folder=base/CORE_CASES/name
        if (folder/"COMPLETED.json").exists() or load(folder/"FAILURE.json")["analysis_admitted"]:
            raise ValueError("An original failure was admitted")
        paired=load(base/CORE_CASES/("direct-"+name)/"PAIRWISE-FAILURE.json")
        if not paired["complete_original_values_equal"] or paired["result_admitted"]:
            raise ValueError("Original failure value/condition/RNG comparison is incomplete")
    no_counts=load(base/CORE_CASES/"direct-original-no-counts/PAIRWISE-FAILURE.json")
    stacks=no_counts["checked_native_stack_suffixes"]
    if len(stacks)!=1 or stacks[0]["native_frames_compared"]!=21 or not stacks[0]["native_frames_and_parent_graph_equal"]:
        raise ValueError("Actual checked Shiny native-stack suffix is required")
    guards=load(base/GUARDS/"COMPLETED.json")
    if len(guards["cases"])!=7 or not guards["source_bundle_unchanged"]:
        raise ValueError("Seven actual recording/refusal controls are required")
    for row in guards["cases"]:
        if row["case"] in ["scientific-value","script-method"] and not (
                row["hashes_resealed"] and row["original_R_recomputed"] and
                row["whole_value_comparison_refused"]):
            raise ValueError("Resealed scientific alterations must fail actual recomputation")
        if row["case"]=="after-promotion" and not row["promoted_marker_invalidated"]:
            raise ValueError("A recording fault left a promoted marker")
    ownership=load(base/"idep244-process-guards-2/COMPLETED.json")
    if len(ownership["controls"])!=3 or not ownership["unrelated_process_completed_normally"]:
        raise ValueError("Actual owned-R process limits and unrelated-process protection required")
    ready=load(base/PROVIDER/"COMPLETED.json")
    return dict(public_cases=6,complete_captured_input_replays=3,
        independent_original_success_comparisons=3,independent_original_failure_comparisons=2,
        recording_prerequisite_and_resealed_science_controls=7,actual_process_limit_controls=3,
        original_source_definitions=260,original_active_function_names=259,
        runtime_packages=ready["packages"],locked_runtime_files=ready["locked_files"],
        original_error_native_stack_frames_compared=21,
        original_errors_admit_no_result=True,zero_catalog_credit=True)


def freeze(base):
    if EVIDENCE.exists():raise FileExistsError("Preserve the existing checkpoint; use a separately versioned evidence directory")
    outcomes=check_actual_records(base)
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in source_paths()}
    records=private_files(base)
    EVIDENCE.mkdir()
    (EVIDENCE/".gitattributes").write_bytes(b"* -text whitespace=cr-at-eol\n")
    save(EVIDENCE/"SOURCE-PINS.json",sources)
    save(EVIDENCE/"PRIVATE-RECORDS.json",records)
    with zipfile.ZipFile(EVIDENCE/"project-source-snapshot.zip","x",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in sources:
            raw=(ROOT/name).read_bytes()
            if rawpin(raw)!=sources[name]:raise ValueError("Source changed during freeze: "+name)
            z.writestr(name,raw)
        z.writestr("LICENSE-project.txt",(ROOT/"LICENSE").read_bytes())
    summary=dict(status="PASS_SELECTED_ORIGINAL_IDEP244_DEVELOPMENT_CONTROLS",
        **outcomes,selected_idep="2.4.4",selected_R="4.6.1",selected_DESeq2="1.52.0",
        selected_source_commit="60479c4375ea07bc930dc40b3c85230856f4ad50",
        author_iDEP96_deployment_identified=False,source_algorithms_patched=False,
        full_GUI_session_qualified=False,full_catalog_gate_passed=False,
        original_RDS_and_source_bearing_traces_publicly_redistributed=False,
        earliest_development_failures_have_logs_not_complete_helper_source_snapshots=True,
        original_first_failed_control_harness_retained=True,catalog_credit_added=0,
        whole_tool_qualified=False,paper_reproduced=False,whole_goal_complete=False)
    save(EVIDENCE/"SUMMARY.json",summary)
    (EVIDENCE/"WARNINGS.md").write_text("# Warnings and required actions\n\n"+NOTICE+"\n",encoding="utf-8",newline="\n")
    (EVIDENCE/"README.md").write_text(
        "# Selected original iDEP count-workflow development records\n\n"+NOTICE+"\n\n"
        "Run python -B tools/literature/idep244_development_records.py for PUBLIC METADATA ONLY. "
        "Add --private-base /canonical/private/monocle-runtime to hash every retained original payload. "
        "Neither mode repeats an original fit. No installed R library binaries or primary-paper texts "
        "are bundled. The own-project source snapshot is GPL-3.0-or-later; original iDEP sources "
        "and source-bearing error RDS remain private under their original terms.\n",
        encoding="utf-8",newline="\n")
    public=[p for p in EVIDENCE.iterdir() if p.is_file() and p.name!="STATUS.json"]
    proof=dict(status="PRESERVED_IDEP244_DEVELOPMENT_CHECKPOINT_NOT_CATALOG_ADMISSION",
        public_files={p.name:pin(p) for p in public},private_files=len(records),
        **outcomes,catalog_credit_added=0,whole_goal_complete=False)
    save(EVIDENCE/"STATUS.json",proof)


def verify(base=None):
    proof=load(EVIDENCE/"STATUS.json")
    if proof["status"]!="PRESERVED_IDEP244_DEVELOPMENT_CHECKPOINT_NOT_CATALOG_ADMISSION":
        raise ValueError("Unsupported development checkpoint")
    if {p.name for p in EVIDENCE.iterdir() if p.is_file()}!=set(proof["public_files"])|{"STATUS.json"}:
        raise ValueError("Complete public checkpoint inventory differs")
    for name,value in proof["public_files"].items():
        if pin(EVIDENCE/name)!=value:raise ValueError("Public record differs: "+name)
    sources=load(EVIDENCE/"SOURCE-PINS.json")
    with zipfile.ZipFile(EVIDENCE/"project-source-snapshot.zip") as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or set(names)!=set(sources)|{"LICENSE-project.txt"}:
            raise ValueError("Complete own-source snapshot is required")
        for name,value in sources.items():
            if rawpin(z.read(name))!=value:raise ValueError("Archived own-source differs: "+name)
    current=all((ROOT/n).is_file() and pin(ROOT/n)==v for n,v in sources.items())
    checked=0
    if base is not None:
        base=base.resolve()
        for name,value in load(EVIDENCE/"PRIVATE-RECORDS.json").items():
            rel=PurePosixPath(name)
            path=base/rel
            if rel.is_absolute() or ".." in rel.parts or not path.resolve().is_relative_to(base):
                raise ValueError("Private record escapes its declared base")
            if pin(path)!=value:raise ValueError("Complete retained private file differs: "+name)
            checked+=1
        if checked!=proof["private_files"]:raise ValueError("Incomplete private readback")
    return dict(status=("PASS_COMPLETE_SAVED_PRIVATE_IDEP244_DEVELOPMENT_RECORD_READBACK" if base else
                        "PASS_PUBLIC_IDEP244_DEVELOPMENT_METADATA_AND_SOURCE_SNAPSHOT_ONLY"),
        current_project_sources_match_snapshot=current,complete_original_private_files_read=checked,
        public_only=base is None,source_snapshot_historical_if_current_differs=not current,
        original_fits_repeated=False,installed_runtime_reaudited=False,
        evidence_authenticity_or_biological_validity_established=False,
        catalog_credit_added=0,whole_goal_complete=False)


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__,epilog=NOTICE)
    p.add_argument("--freeze",action="store_true");p.add_argument("--private-base",type=Path)
    a=p.parse_args()
    try:
        if a.freeze:
            if a.private_base is None:raise ValueError("--freeze requires the full existing private record base")
            freeze(a.private_base.resolve())
        print(json.dumps(verify(a.private_base),indent=2))
    except BaseException as error:
        print("FAIL2_IDEP244_DEVELOPMENT_RECORDS_UNVERIFIED",repr(error),"\n"+NOTICE,flush=True)
        raise SystemExit(2)
