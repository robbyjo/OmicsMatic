#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve iDEP genuine-session development records; default is public metadata only.

Private readback hashes saved payloads and declared synthetic file symlinks.
Neither readback mode repeats a scientific fit or authenticates an upstream binary.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import sys
import zipfile

sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
MODULE=ROOT / "tools/literature/idep244_session"
VALIDATION=ROOT / "tools/literature/idep244_session_validation"
EVIDENCE=ROOT / "docs/literature/idep244-session-counts-development"
NOTICE=("DEVELOPMENT ONLY; zero catalog credit. Whole-catalog verification still requires the separate "
        "main-source baseline integration. Full GUI, whole-tool, biological and paper validity remain unqualified. "
        "Public verification checks metadata and own-source snapshots only; --private-base checks complete saved "
        "payload inventories and declared synthetic file aliases, not a fresh fit or current installed runtime. "
        "Original iDEP source/binaries and source-bearing failure RDS remain private under their original terms. "
        "Historical failed/incomplete attempts and intentionally invalid negative copies are retained. An original "
        "all-zero input error admits no result. SKIP3 skips unavailable prerequisites before scientific R; supply "
        "the named permitted prerequisite and retry NEW paths. FAIL2 admits no result; retain originals/partials, "
        "read FAILURE.json, WARNINGS.md and raw consoles, resolve the named cause and retry NEW paths. "
        "Never refresh old hashes to admit changed scientific values. Read IDEP244_SESSION_COUNTS_DEVELOPMENT.md. "
        "The full250-paper implementation objective remains active.")


def pin(path):
    h=hashlib.sha256();size=0
    with Path(path).open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""):
            h.update(b);size+=len(b)
    return dict(bytes=size,sha256=h.hexdigest())


def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def load(path):return json.loads(Path(path).read_bytes())
def save(path,value):
    with Path(path).open("xb") as f:f.write((json.dumps(value,indent=2,sort_keys=True,ensure_ascii=False)+"\n").encode("utf-8"))


def own_sources():
    paths=[Path(__file__).resolve(),ROOT / "docs/literature/IDEP244_SESSION_COUNTS_DEVELOPMENT.md",
           ROOT / "examples/idep244-session-workflow/request.json"]
    for folder in [MODULE,VALIDATION,ROOT / "tools/literature/idep244"]:
        paths.extend(p for p in folder.iterdir() if p.is_file() and
                     (p.suffix in [".py",".R",".json"] or p.name==".gitattributes"))
    return sorted(paths)


def private_inventory(base,roots):
    records={}
    def record(path):
        name=path.relative_to(base).as_posix()
        if hasattr(path,"is_junction") and path.is_junction():raise ValueError("Private directory reparse point refused")
        if path.is_symlink():
            target=path.resolve(strict=True)
            if not target.is_relative_to(base) or not target.is_file() or target.is_symlink():
                raise ValueError("Only declared synthetic file aliases inside the private base are accepted")
            records[name]=dict(kind="file-symlink",literal_target=os.readlink(path),
                               target_relative=target.relative_to(base).as_posix(),**pin(path))
        else:
            if not path.is_file():raise ValueError("Complete regular private payload is missing: "+name)
            records[name]=dict(kind="file",**pin(path))
    for name in roots:
        relative=PurePosixPath(name)
        if relative.is_absolute() or ".." in relative.parts or len(relative.parts)!=1:
            raise ValueError("Private retained roots must be literal immediate child names")
        root=base/name
        if root.is_symlink() or (hasattr(root,"is_junction") and root.is_junction()):
            raise ValueError("Retained private root cannot be a link")
        if root.is_file():record(root);continue
        if not root.is_dir():raise FileNotFoundError("Missing retained private root: "+name)
        def error(e):raise e
        for parent,dirs,files in os.walk(root,followlinks=False,onerror=error):
            for d in dirs:
                p=Path(parent)/d
                if p.is_symlink() or (hasattr(p,"is_junction") and p.is_junction()):
                    raise ValueError("Private directory links refused: "+str(p))
            for f in files:record(Path(parent)/f)
    return dict(sorted(records.items()))


def actual_records(base):
    sys.path.insert(0,str(MODULE))
    from Core import coherent_provider,session_sources
    from Transport import transport_check
    from verify import check_bundle
    sources=session_sources()
    suite=base / "idep244-session-full-controls-2"
    complete=load(suite / "COMPLETED.json")
    if complete["status"]!="PASS_THREE_ACTUAL_SHINY_COUNT_CONTROLS_INDEPENDENT_ORIGINAL_CALLS_AND_TWO_REPLAYS":
        raise ValueError("Actual complete three-case session controls required")
    if load(suite / "ENGINE-SOURCES.json")!=sources:raise ValueError("Complete controls do not cover this exact engine")
    if {r["case"]:r["exit_code"] for r in complete["cases"]}!={"baseline":0,"one-zero":0,"all-zero":2}:
        raise ValueError("Both original successes and the actual all-zero failure required")
    notification_replays=[]
    for case in ["baseline","one-zero"]:
        check_bundle(suite/case)
        replay=load(suite/("replay-"+case)/"COMPLETED.json")
        if replay["status"]!="PASS_COMPLETE_CAPTURED_INPUT_ORIGINAL_SHINY_REPLAY" or not replay["source_bundle_unchanged"]:
            raise ValueError("Complete actual captured-input session replay required")
        pair=load(suite/("comparison-"+case)/"PAIRWISE.json")
        if not pair["complete_original_stage_values_equal"] or not pair["complete_original_condition_objects_equal"]:
            raise ValueError("Full independent original success values/conditions must match")
        notices=lambda folder:[x["notification"] for x in load(folder / "received-notifications.json")]
        if notices(suite/case)!=notices(suite/("replay-"+case)):
            raise ValueError("Complete actual replay notification objects differ")
        notification_replays.append(case)
    failed=suite / "all-zero"
    if (failed / "COMPLETED.json").exists() or load(failed / "FAILURE.json")["analysis_admitted"]:
        raise ValueError("Original all-zero error admitted a result")
    error_pair=load(suite / "comparison-all-zero/PAIRWISE.json")
    if not error_pair["primary_original_error_signature_equal"] or error_pair["full_failure_stack_comparison_claimed"]:
        raise ValueError("Named original error comparison scope required without a full-stack claim")
    aliases=base / "idep244-session-path-alias-control-2"
    if load(aliases / "COMPLETED.json")["status"]!="PASS_ACTUAL_DIFFERENT_EXTENSION_ALIAS_ORIGINAL_CALLS_AND_REPLAY":
        raise ValueError("Actual differently suffixed original alias control and replay required")
    check_bundle(aliases / "run")
    if load(aliases / "replay/COMPLETED.json")["status"]!="PASS_COMPLETE_CAPTURED_INPUT_ORIGINAL_SHINY_REPLAY":
        raise ValueError("Complete original alias replay required")
    guards=load(base / "idep244-session-coherence-controls-2/COMPLETED.json")
    if len(guards["cases"])!=12 or not guards["original_bundle_and_provider_unchanged"]:
        raise ValueError("Twelve actual resealed contradiction controls required")
    prerequisites=load(base / "idep244-session-prerequisite-controls-1/COMPLETED.json")
    if len(prerequisites["cases"])!=3 or any(r["scientific_R_started"] for r in prerequisites["cases"]):
        raise ValueError("Three actual unavailable-prerequisite skips before R required")
    limit=load(base / "idep244-session-process-limit-control-1/COMPLETED.json")
    if not all(limit[k] for k in ["owned_Rscript_and_Rterm_dead","other_R_job_alive_after_limit","other_R_job_completed_normally","no_result_admitted"]):
        raise ValueError("Actual owned Rscript/Rterm limit and other-job protection required")
    provider=coherent_provider(base / "idep244-adopted-provider-4")
    transport=base / "idep244-session-transport-setup-1"
    transport_check(transport)
    # The first installer identity is historical. Its exact own-project source
    # was captured by the first probe and must not be silently repinned.
    if load(transport / "READY.json")["installer"]!=pin(base / "idep244-session-loader-probe-1/engine/install_transport.py"):
        raise ValueError("Original transport installer identity lacks its preserved source")
    return dict(actual_scientific_success_cases=3,independent_complete_original_success_comparisons=3,
        independent_original_failure_signature_comparisons=1,complete_captured_input_replays=3,
        whole_notification_objects_rechecked_for_saved_replays=notification_replays,
        resealed_request_provider_and_input_extension_refusals=12,unavailable_prerequisite_skips_before_R=3,
        actual_owned_Rscript_Rterm_wall_refusal_controls=1,another_actual_R_job_survived_and_completed=True,
        runtime_packages=len(provider["installed_packages"]),locked_runtime_files=15604,
        original_active_function_names=259,original_source_definitions=260,
        full_GUI_qualified=False,whole_tool_qualified=False,full_failure_stack_equivalence_claimed=False,
        source_algorithms_patched=False,catalog_credit_added=0,whole_goal_complete=False)


def freeze(base):
    if EVIDENCE.exists():raise FileExistsError("Preserve the old checkpoint; choose a separately versioned evidence directory")
    summary=actual_records(base)
    roots=sorted(p.name for p in base.iterdir() if p.name.startswith("idep244-session-"))
    records=private_inventory(base,roots)
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in own_sources()}
    EVIDENCE.mkdir()
    (EVIDENCE / ".gitattributes").write_bytes(b"* -text whitespace=cr-at-eol\n")
    save(EVIDENCE / "SOURCE-PINS.json",sources)
    save(EVIDENCE / "PRIVATE-RECORDS.json",dict(retained_roots=roots,files=records))
    with zipfile.ZipFile(EVIDENCE / "project-source-snapshot.zip","x",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,identity in sources.items():
            raw=(ROOT/name).read_bytes()
            if rawpin(raw)!=identity:raise ValueError("Own source changed while preserving snapshot")
            z.writestr(name,raw)
        z.writestr("LICENSE-project.txt",(ROOT / "LICENSE").read_bytes())
    save(EVIDENCE / "SUMMARY.json",dict(status="PASS_SELECTED_GENUINE_SHINY_IDEP244_DEVELOPMENT_CONTROLS",
        **summary,current_source_and_provider_files_hashed_at_freeze=True,new_fits_repeated_by_record_reader=False,
        earliest_probe_parent_console_only_in_tool_transcript=True,
        original_RDS_source_bearing_failure_traces_publicly_redistributed=False,
        historical_failed_control_suite_and_file_alias_attempt_retained=True,
        generic_replay_notification_parity_gate_still_pending=True))
    (EVIDENCE / "WARNINGS.md").write_text("# Warnings and required actions\n\n"+NOTICE+"\n",encoding="utf-8")
    (EVIDENCE / "README.md").write_text("# Original iDEP genuine-session development checkpoint\n\n"+NOTICE+
        "\n\nUse python -B tools/literature/idep244_session_records.py for public metadata only. "
        "Add --private-base /canonical/private/monocle-runtime for complete saved-payload and declared-file-alias readback. "
        "Neither mode repeats scientific fits. The own-project snapshot is GPL-3.0-or-later and contains no original iDEP source or binaries.\n",encoding="utf-8")
    public={p.name:pin(p) for p in EVIDENCE.iterdir() if p.is_file()}
    save(EVIDENCE / "STATUS.json",dict(status="PRESERVED_GENUINE_SHINY_IDEP244_DEVELOPMENT_NOT_CATALOG_ADMISSION",
        public_files=public,private_files=len(records),private_file_aliases=sum(x["kind"]=="file-symlink" for x in records.values()),
        **summary))


def verify(base=None):
    proof=load(EVIDENCE / "STATUS.json")
    if proof["status"]!="PRESERVED_GENUINE_SHINY_IDEP244_DEVELOPMENT_NOT_CATALOG_ADMISSION":raise ValueError("Wrong checkpoint scope")
    if {p.name for p in EVIDENCE.iterdir() if p.is_file()}!=set(proof["public_files"])|{"STATUS.json"}:
        raise ValueError("Complete public checkpoint membership differs")
    for name,identity in proof["public_files"].items():
        if pin(EVIDENCE/name)!=identity:raise ValueError("Saved public record differs: "+name)
    sources=load(EVIDENCE / "SOURCE-PINS.json")
    with zipfile.ZipFile(EVIDENCE / "project-source-snapshot.zip") as z:
        if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(sources)|{"LICENSE-project.txt"}:
            raise ValueError("Complete own-source archive membership differs")
        for name,identity in sources.items():
            if rawpin(z.read(name))!=identity:raise ValueError("Saved own-source archive differs: "+name)
    current=all((ROOT/n).is_file() and pin(ROOT/n)==v for n,v in sources.items())
    count=0
    if base is not None:
        base=base.resolve(strict=True);private=load(EVIDENCE / "PRIVATE-RECORDS.json")
        if private_inventory(base,private["retained_roots"])!=private["files"]:
            raise ValueError("Complete saved private payload membership, values or literal aliases differ")
        count=len(private["files"])
        if count!=proof["private_files"]:raise ValueError("Private readback count differs")
    return dict(status="PASS_SAVED_PRIVATE_GENUINE_SHINY_RECORD_READBACK" if base else "PASS_PUBLIC_GENUINE_SHINY_METADATA_AND_OWN_SOURCE_ONLY",
        current_project_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,
        complete_private_files_read=count,public_only=base is None,scientific_fits_repeated=False,
        current_installed_runtime_reaudited=False,upstream_binary_authenticity_established=False,
        catalog_credit_added=0,whole_goal_complete=False)


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__,epilog=NOTICE)
    p.add_argument("--freeze",action="store_true");p.add_argument("--private-base",type=Path)
    a=p.parse_args()
    try:
        if a.freeze:
            if a.private_base is None:raise ValueError("--freeze requires complete retained private records")
            freeze(a.private_base.resolve(strict=True))
        print(json.dumps(verify(a.private_base),indent=2),flush=True)
    except BaseException as e:
        print("FAIL2_GENUINE_SHINY_RECORDS_UNVERIFIED",repr(e),"\n"+NOTICE,flush=True)
        raise SystemExit(2)
