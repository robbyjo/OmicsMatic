#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay a complete captured iDEP real-session count workflow in a NEW bundle."""
import argparse
import base64
import shutil
from Core import *
from Request import validate, coherent_requests, reference_preflight
from Transport import transport_check
from run import SUCCESS, execute


def check_bundle(bundle):
    bundle = Path(bundle).resolve()
    done = load(bundle / "COMPLETED.json")
    if done.get("status") != SUCCESS or done.get("analysis_admitted") is not True:
        raise ValueError("A complete recorded session count workflow is required")
    if done["manifest"] != pin(bundle / "MANIFEST.json") or done["run"] != pin(bundle / "RUN.json"):
        raise ValueError("Completion does not identify the exact manifest and run")
    actual = inventory(bundle)
    actual.pop("COMPLETED.json");actual.pop("MANIFEST.json")
    if actual != load(bundle / "MANIFEST.json"):
        raise ValueError("Complete bundle inventory differs, including added or missing files")
    run = load(bundle / "RUN.json")
    if run["schema"] != "omicsmatic-idep244-original-shiny-count-run-v2" or done["schema"] != run["schema"]:
        raise ValueError("Exact recorded original real-session schema required")
    sources = session_sources()
    if run["engine_sources"] != sources or load(bundle / "engine-sources.json") != sources or inventory(bundle / "engine") != sources:
        raise ValueError("Replay requires the complete exact installed and captured engine")
    for field, name in [("R_status","original/R-status.json"),
                        ("original_complete_return","original/complete-original-returns.rds"),
                        ("complete_original_conditions","original/complete-conditions.rds"),
                        ("session_receipt","SESSION-COMPLETED.json"),
                        ("complete_websocket_messages","websocket-messages.jsonl"),
                        ("original_request","original-request.json"),
                        ("effective_request","effective-request.json"),
                        ("captured_inputs","captured-inputs.json")]:
        if run[field] != pin(bundle / name):
            raise ValueError("Declared complete record differs: " + name)
    if pin(bundle / "SUPPLIED-REQUEST.json") != run["original_request"]:
        raise ValueError("Supplied and original request bytes contradict each other")
    original = validate(load(bundle / "original-request.json"))
    effective = validate(load(bundle / "effective-request.json"))
    inputs = load(bundle / "captured-inputs.json")
    coherent_requests(original, effective, inputs)
    if effective["profile"] != run["profile"] or effective["operation"] != run["operation"]:
        raise ValueError("Declared profile or operation contradicts the effective request")
    captured = {"inputs/"+name:identity for name,identity in inventory(bundle / "inputs").items()}
    if set(captured) != set(inputs):
        raise ValueError("Complete captured input membership differs")
    for name, identity in inputs.items():
        if captured[name] != {k:identity[k] for k in ["bytes","sha256"]}:
            raise ValueError("Complete captured input differs: " + name)
    reference_preflight(effective, bundle)
    runtime, transport = Path(run["provider"]), Path(run["transport_provider"])
    ready = coherent_provider(runtime)
    transport_check(transport)
    if (pin(runtime / "READY.json") != run["provider_ready"] or
            pin(runtime / "runtime-files.json") != run["provider_runtime_index"] or
            pin(transport / "READY.json") != run["transport_ready"]):
        raise ValueError("Exact original runtime and transport providers are required")
    for name in ["READY.json","COMPLETED.json","runtime-files.json","INSPECTED.json",
                 "adoption-source-pins.json","namespace-audit.rds"]:
        if pin(bundle / "runtime-evidence" / name) != pin(runtime / name):
            raise ValueError("Captured original runtime audit differs: " + name)
    for name in ["READY.json","COMPLETED.json","PYPI.json"]:
        if pin(bundle / "transport-evidence" / name) != pin(transport / name):
            raise ValueError("Captured transport provider audit differs: " + name)
    status = load(bundle / "original/R-status.json")
    if (status["status"] != "PASS_ORIGINAL_IDEP244_COMPLETE_COUNT_RETURN_RECORDED"
            or status["phase"] != "COMPLETE" or status["exit_code"] != 0
            or status["error"] is not None or status["complete_original_return_recorded"] is not True
            or status["original_dispatcher_returned"] is not True
            or status["generated_original_R_script_executed"] is not False):
        raise ValueError("Original full scientific return contradicts final admission")
    for field in ["result_dimensions","comparisons","original_condition_count"]:
        if done[field] != status[field]:
            raise ValueError("Completion contradicts the complete original return summary: " + field)
    if done["original_dimensions"] != status["original_input_dimensions"]:
        raise ValueError("Original input dimensions contradict final completion")
    control = load(bundle / "control.json")
    receipt = load(bundle / "SESSION-COMPLETED.json")
    process = load(bundle / "worker.process.json")
    if (receipt["token"] != control["token"] or receipt["acknowledged"] is not True
            or receipt["done"] is not True or receipt["genuine_session"] is not True
            or receipt["exit_code"] != 0 or receipt["full_GUI_qualified"] is not False
            or process["exit_code"] != 0 or process["host"] != "127.0.0.1"
            or process["port"] != control["port"] or process["requested_limits"] != effective["limits"]
            or process["limit_exceeded"] is not None or process["observer_or_launcher_error"] is not None
            or process["native_execution_resumed"] is not True or process["descendant_job_closed"] is not True
            or process["client_thread_stopped"] is not True or type(process["native_r_pid"]) is not int
            or process["native_r_pid"] <= 0
            or (bundle / "worker-rterm-pid.txt").read_text(encoding="ascii").strip() != str(process["native_r_pid"])):
        raise ValueError("Original session, process ownership, limits or client receipt contradiction")
    flags = process["transport"]
    if any(flags[n] is not True for n in ["connected","finished_received","acknowledgement_sent"]) or flags["error"] is not None:
        raise ValueError("Recorded transport did not complete the exact session exchange")
    init_count = done_count = ack_count = 0
    notifications = []
    for ordinal, line in enumerate((bundle / "websocket-messages.jsonl").read_bytes().splitlines()):
        row = json.loads(line)
        raw = base64.b64decode(row["payload_base64"],validate=True)
        if len(raw) != row["bytes"]:
            raise ValueError("Complete original WebSocket message length differs")
        if row["opcode"] != 1:
            continue
        message = json.loads(raw)
        if row["direction"] == "sent":
            if message == dict(method="init",data=dict(start_token=control["token"])) and not (init_count or done_count or ack_count):
                init_count += 1
            elif message == dict(method="update",data=dict(finish_ack=control["token"])) and (init_count,done_count,ack_count)==(1,1,0):
                ack_count += 1
            else:
                raise ValueError("Unexpected, reordered or duplicate client scientific-session message")
        elif row["direction"] == "received":
            if "notification" in message:
                notifications.append(dict(message_index=ordinal,notification=message["notification"]))
            if "omicsmatic_done" in message.get("custom",{}):
                if message["custom"]["omicsmatic_done"] != dict(token=control["token"],return_recorded=True) or (init_count,done_count,ack_count)!=(1,0,0):
                    raise ValueError("Original server completion message contradicts the current session")
                done_count += 1
        else:
            raise ValueError("Unknown WebSocket message direction")
    if (init_count,done_count,ack_count)!=(1,1,1) or load(bundle / "received-notifications.json") != notifications or flags["notifications"] != len(notifications):
        raise ValueError("Incomplete session exchange or contradictory original notification inventory")
    return run,effective,inputs,ready


def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument("--bundle",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args();out=None
    try:
        bundle=a.bundle.resolve()
        run,effective,inputs,ready=check_bundle(bundle)
        before=inventory(bundle)
        runtime=Path(run["provider"]);transport=Path(run["transport_provider"])
        out=new_output(a.out,[SESSION,LEGACY,bundle,runtime,transport,ready["source"],*ready["libraries"]])
        warning(out)
        raw=(bundle / "original-request.json").read_bytes()
        (out / "SUPPLIED-REQUEST.json").write_bytes(raw)
        for name,identity in inputs.items():
            destination=out/name;destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(bundle/name,destination)
            if pin(destination)!={k:identity[k] for k in ["bytes","sha256"]}:
                raise ValueError("Complete replay input copy differs")
        execute(runtime,transport,out,effective,inputs,raw,ready)
        comparison=out / "verification-comparison"
        comparison.mkdir()
        shutil.copyfile(SESSION / "CompareSession.R",comparison / "CompareSession.R")
        result=launch([ready["rscript"],"--vanilla",str(comparison / "CompareSession.R"),
            str(out / "libraries.txt"),str(bundle),str(out),"PAIRWISE.json","success"],
            comparison,"comparison",dict(wall_seconds=180,files=1000,bundle_bytes=64*1024*1024))
        if result["exit_code"] or not load(comparison / "PAIRWISE.json")["complete_original_condition_objects_equal"]:
            raise ValueError("Fresh complete original-value/RNG/condition/session-context comparison failed")
        if inventory(bundle)!=before:
            raise ValueError("Saved original bundle changed during replay")
        # Verification records must be inside the final manifest. Invalidate the
        # intermediate run marker, retain it, and promote a NEW complete marker.
        (out / "COMPLETED.json").rename(out / "RECORDED-RUN-COMPLETION.json")
        (out / "MANIFEST.json").rename(out / "RECORDED-RUN-MANIFEST.json")
        members=inventory(out);save(out / "MANIFEST.json",members)
        completion=dict(status="PASS_COMPLETE_CAPTURED_INPUT_ORIGINAL_SHINY_REPLAY",
            manifest=pin(out / "MANIFEST.json"),source_bundle=str(bundle),source_bundle_unchanged=True,
            original_values_RNG_conditions_and_declared_context_compared=True,
            timestamp_and_transport_identifier_policies=load(comparison / "PAIRWISE.json"),
            catalog_credit_added=0,whole_tool_qualified=False,paper_reproduced=False)
        count,size=footprint(out)
        if count+1>effective["limits"]["files"] or size+len(encoded(completion))>effective["limits"]["bundle_bytes"]:
            raise ValueError("Complete replay and verification metadata exceeds the caller limit")
        save(out / "COMPLETED.json",completion)
        print(completion["status"],flush=True);return 0
    except BaseException as error:
        return failure(out,error,"VERIFY_ORIGINAL_SHINY_COUNT_WORKFLOW",attempted(out,"worker"))


if __name__=="__main__":
    if not __debug__:raise RuntimeError("Never disable evidence assertions")
    raise SystemExit(main())
