#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual missing-provider/refusal, resealed science changes and recording faults."""
from pathlib import Path
import argparse
import json
import shutil
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"idep244"))
from Runtime import *

VALIDATION=Path(__file__).resolve().parent


def replace_json(path,value):
    # This is an explicitly adversarial reseal of an owned NEW copied fixture.
    path.write_bytes(encoded(value))


def reseal(bundle):
    run=load(bundle/"RUN.json")
    run["original_complete_return"]=pin(bundle/"original/complete-original-returns.rds")
    replace_json(bundle/"RUN.json",run)
    members=inventory(bundle)
    members.pop("MANIFEST.json");members.pop("COMPLETED.json")
    replace_json(bundle/"MANIFEST.json",members)
    done=load(bundle/"COMPLETED.json")
    done["manifest"]=pin(bundle/"MANIFEST.json");done["run"]=pin(bundle/"RUN.json")
    replace_json(bundle/"COMPLETED.json",done)


def public(out,label,command,expected):
    with (out/(label+".stdout")).open("xb") as so,(out/(label+".stderr")).open("xb") as se:
        result=subprocess.run(list(map(str,command)),cwd=ROOT,stdin=subprocess.DEVNULL,
            stdout=so,stderr=se,timeout=600)
    save(out/(label+".process.json"),dict(command=list(map(str,command)),
                                         exit_code=result.returncode,expected=expected))
    if result.returncode!=expected:
        raise AssertionError((label,result.returncode,(out/(label+".stdout")).read_text(errors="replace")[-2500:]))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime",required=True,type=Path);p.add_argument("--inputs",required=True,type=Path)
    p.add_argument("--bundle",required=True,type=Path);p.add_argument("--out",required=True,type=Path)
    p.add_argument("--resume-prerequisite-records",type=Path)
    a=p.parse_args();out=new_output(a.out,[HERE,a.runtime,a.inputs,a.bundle])
    before=inventory(a.bundle);runtime_before=pin(a.runtime/"READY.json")
    selected=load(a.runtime/"READY.json");results=[]
    assert load(a.bundle/"engine-sources.json")==engine_sources()
    reused=[]
    def reuse(label):
        if a.resume_prerequisite_records is None:return False
        previous=a.resume_prerequisite_records
        for suffix in [".stdout",".stderr",".process.json"]:
            shutil.copyfile(previous/(label+suffix),out/(label+suffix))
        shutil.copytree(previous/label,out/label)
        request=previous/(label+"-request.json")
        if request.exists():shutil.copyfile(request,out/request.name)
        reused.append(dict(case=label,original_records=str(previous/label),
                           source_inventory=inventory(previous/label)))
        return True
    # A genuinely missing prerequisite must skip before any original R call.
    output=out/"missing-runtime"
    if not reuse("missing-runtime"):
        public(out,"missing-runtime",[sys.executable,"-X","utf8","-B",HERE/"run.py",
            "--runtime",out/"absent-runtime","--request",a.inputs/"disabled.json","--out",output],3)
    assert load(out/"missing-runtime.process.json")["exit_code"]==3
    assert not (output/"worker.stdout").exists() and not (output/"COMPLETED.json").exists()
    assert load(output/"FAILURE.json")["status"]=="SKIP3_IDEP244_PREREQUISITE_UNAVAILABLE"
    results.append(dict(case="missing-runtime",exit_code=3,original_R_started=False))
    # Real public runs with restrictive caller budgets, without patched methods.
    for label in ["final-metadata-files","vector-heap"]:
        request=load(a.inputs/"disabled.json")
        if label=="final-metadata-files":
            request["limits"]["files"]=load(a.bundle/"worker.process.json")["observed_files"]+3
        else:
            request["limits"]["vector_heap_mib"]=16
        destination=out/label
        if not reuse(label):
            request_file=out/(label+"-request.json");save(request_file,request)
            public(out,label,[sys.executable,"-X","utf8","-B",HERE/"run.py",
                "--runtime",a.runtime,"--request",request_file,"--out",destination],2)
        assert load(out/(label+".process.json"))["exit_code"]==2
        failed=load(destination/"FAILURE.json")
        assert not (destination/"COMPLETED.json").exists()
        assert failed["status"]=="FAIL2_IDEP244_NO_COMPLETE_RESULT"
        if label=="final-metadata-files":
            assert "Complete final metadata exceeds" in failed["cause"]
            assert load(destination/"original/R-status.json")["exit_code"]==0
            assert (destination/"MANIFEST.json").is_file()
        else:
            status=load(destination/"original/R-status.json")
            assert status["phase"]=="READINESS" and not status["original_calls_started"]
            assert status["error"]["message"]=="Requested R vector-heap limit cannot be enforced"
            assert any(x["message"]=="a limit lower than current usage, so ignored"
                       for x in status["captured_R_condition_messages"])
        results.append(dict(case=label,exit_code=2,actual_public_run=True,
                            over_budget_or_unenforceable_budget_refused=True))
    for mode in ["scientific-value","script-method"]:
        altered=out/(mode+"-resealed")
        shutil.copytree(a.bundle,altered)
        mutate=out/(mode+"-mutation");mutate.mkdir()
        proc=launch([selected["rscript"],"--vanilla",VALIDATION/"Tamper.R",altered,mode],
                    mutate,"mutate",dict(wall_seconds=60,files=100,bundle_bytes=16*1024*1024))
        if proc["exit_code"]:raise AssertionError("Synthetic mutation failed")
        reseal(altered)
        destination=out/(mode+"-verify")
        public(out,mode+"-verify",[sys.executable,"-X","utf8","-B",HERE/"verify.py",
            "--bundle",altered,"--out",destination],2)
        failure=load(destination/"FAILURE.json")
        assert not (destination/"COMPLETED.json").exists()
        # Require a real recomputation; checksum rejection alone is insufficient.
        assert load(destination/"replay/COMPLETED.json")["analysis_admitted"]
        comparison=load(destination/"COMPARISON.json")
        assert not comparison["complete_values_equal"]
        assert "Complete original replay differs" in failure["cause"]
        results.append(dict(case=mode,exit_code=2,hashes_resealed=True,
                            original_R_recomputed=True,whole_value_comparison_refused=True))
    for mode in ["after-return","after-promotion"]:
        destination=out/mode
        public(out,mode,[sys.executable,"-X","utf8","-B",VALIDATION/"RecordingFault.py",
            "--fault",mode,"--runtime",a.runtime,"--request",a.inputs/"disabled.json","--out",destination],2)
        failure=load(destination/"FAILURE.json")
        assert failure["status"]=="FAIL2_IDEP244_NO_COMPLETE_RESULT"
        assert failure["original_process_execution_attempted"]
        assert (destination/"original/complete-original-returns.rds").is_file()
        assert load(destination/"original/R-status.json")["exit_code"]==0
        assert not (destination/"COMPLETED.json").exists()
        invalidated=list(destination.glob("COMPLETION-INVALIDATED-*.json"))
        assert bool(invalidated)==(mode=="after-promotion")
        if invalidated:assert load(invalidated[0])["analysis_admitted"]
        results.append(dict(case=mode,exit_code=2,original_return_and_raw_records_retained=True,
                            parent_writer_fault_injected=True,original_algorithms_patched=False,
                            promoted_marker_invalidated=bool(invalidated)))
    assert inventory(a.bundle)==before and pin(a.runtime/"READY.json")==runtime_before
    proof=dict(status="PASS_SEVEN_IDEP244_PREREQUISITE_RESEALED_SCIENCE_AND_RECORDING_CONTROLS",
        cases=results,reused_actual_prerequisite_records=reused,
        source_bundle_unchanged=True,catalog_credit_added=0,whole_tool_qualified=False)
    save(out/"COMPLETED.json",proof);print(proof["status"],flush=True)


if __name__=="__main__":
    if not __debug__:raise RuntimeError("Never disable guard assertions")
    main()
