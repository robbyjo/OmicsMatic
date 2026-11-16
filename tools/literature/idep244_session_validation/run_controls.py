#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual session counts, independent literal original calls and two full replays."""
from pathlib import Path
import argparse
import secrets
import shutil
import subprocess
import sys
import time

VALIDATION=Path(__file__).resolve().parent
MODULE=VALIDATION.parent / "idep244_session"
sys.path.insert(0,str(MODULE))
from Core import *
from Transport import free_port,launch_session,transport_check
from run import record_notifications


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime",type=Path,required=True)
    p.add_argument("--transport",type=Path,required=True)
    p.add_argument("--requests",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args()
    if a.out.exists():raise FileExistsError("Preserve prior controls; select a NEW path")
    a.out.mkdir();out=a.out.resolve()
    ready=coherent_provider(a.runtime);ws=transport_check(a.transport)
    sources=session_sources();save(out / "ENGINE-SOURCES.json",sources)
    (out / "validation-source").mkdir()
    for f in VALIDATION.iterdir():
        if f.is_file():shutil.copyfile(f,out / "validation-source" / f.name)
    def process(label,command,expected):
        start=time.monotonic()
        with (out / (label+".stdout")).open("xb") as so,(out / (label+".stderr")).open("xb") as se:
            r=subprocess.run(list(map(str,command)),stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=600)
        row=dict(case=label,exit_code=r.returncode,expected_exit_code=expected,elapsed_seconds=round(time.monotonic()-start,3))
        save(out / (label+".process.json"),row);print(row,flush=True)
        if r.returncode!=expected:
            print((out / (label+".stdout")).read_text(errors="replace")[-4000:],flush=True)
            print((out / (label+".stderr")).read_text(errors="replace")[-4000:],flush=True)
            raise ValueError("Unexpected control outcome: "+label)
        return row
    rows=[];pairs=[];replays=[]
    for case,expected in [("baseline",0),("one-zero",0),("all-zero",2)]:
        request_file=a.requests / (case+".json")
        rows.append(process(case,[sys.executable,"-X","utf8","-B",MODULE / "run.py",
            "--runtime",a.runtime,"--transport",a.transport,"--request",request_file,"--out",out / case],expected))
        bundle=out / case
        if expected:
            if (bundle / "COMPLETED.json").exists() or load(bundle / "FAILURE.json")["analysis_admitted"]:
                raise ValueError("Original input failure admitted a result")
            status=load(bundle / "original/R-status.json")
            if status["phase"]!="input_data" or status["error"]["message"]!="argument is of length zero":
                raise ValueError("Actual original all-zero error was changed or misidentified")
        else:
            done=load(bundle / "COMPLETED.json")
            if done["original_dimensions"]!=[200,9 if case=="baseline" else 8] or done["result_dimensions"]!=[200,3]:
                raise ValueError("Actual original dimensions differ from the declared source controls")
        direct=out / ("direct-"+case);direct.mkdir();warning(direct)
        (direct / "original/stages").mkdir(parents=True);(direct / "inputs/expression").mkdir(parents=True)
        (direct / "engine").mkdir()
        shutil.copyfile(VALIDATION / "DirectOriginalSession.R",direct / "engine/DirectOriginalSession.R")
        shutil.copyfile(LEGACY / "SourceAudit.R",direct / "engine/SourceAudit.R")
        request=load(request_file);expression=Path(request["expression"])
        shutil.copyfile(expression,direct / "inputs/expression" / expression.name)
        port,token=free_port(),secrets.token_hex(32)
        save(direct / "control.json",dict(port=port,token=token,source=ready["source"],filename=expression.name))
        (direct / "libraries.txt").write_text("\n".join(ready["libraries"])+"\n",encoding="utf-8")
        original=launch_session([ready["rscript"],"--vanilla","engine/DirectOriginalSession.R","control.json"],
            direct,dict(wall_seconds=180,files=20000,bundle_bytes=256*1024*1024),port,token,ws)
        record_notifications(direct)
        if original["exit_code"]!=expected:raise ValueError("Independent original exit differs")
        notifications=lambda folder:[x["notification"] for x in load(folder / "received-notifications.json")]
        if notifications(bundle)!=notifications(direct):raise ValueError("Complete original notification objects differ")
        if len(notifications(bundle))!=(0 if case=="baseline" else 1):raise ValueError("Original notification count changed")
        comparison=out / ("comparison-"+case);comparison.mkdir()
        shutil.copyfile(MODULE / "CompareSession.R",comparison / "CompareSession.R")
        mode="success" if not expected else "original-error-signature-only"
        result=launch([ready["rscript"],"--vanilla",str(comparison / "CompareSession.R"),
            str(direct / "libraries.txt"),str(bundle),str(direct),"PAIRWISE.json",mode],
            comparison,"comparison",dict(wall_seconds=180,files=1000,bundle_bytes=64*1024*1024))
        if result["exit_code"]:
            print((comparison / "comparison.stderr").read_text(errors="replace"),flush=True)
            raise ValueError("Independent whole original-value comparison failed: "+case)
        pairs.append(dict(case=case,**load(comparison / "PAIRWISE.json")));print("PASS_INDEPENDENT_ORIGINAL_COMPARISON",case,flush=True)
        if not expected:
            replays.append(process("replay-"+case,[sys.executable,"-X","utf8","-B",MODULE / "verify.py",
                "--bundle",bundle,"--out",out / ("replay-"+case)],0))
    if session_sources()!=sources:raise ValueError("Current session engine changed during controls")
    coherent_provider(a.runtime);transport_check(a.transport)
    save(out / "MANIFEST.json",inventory(out))
    save(out / "COMPLETED.json",dict(status="PASS_THREE_ACTUAL_SHINY_COUNT_CONTROLS_INDEPENDENT_ORIGINAL_CALLS_AND_TWO_REPLAYS",
        manifest=pin(out / "MANIFEST.json"),cases=rows,independent_original_comparisons=pairs,complete_replays=replays,
        full_failure_stack_comparison_claimed=False,source_algorithms_patched=False,catalog_credit_added=0,
        full_GUI_qualified=False,whole_tool_qualified=False,paper_reproduced=False,whole_goal_complete=False))
    print("PASS_THREE_ACTUAL_SHINY_COUNT_CONTROLS_INDEPENDENT_ORIGINAL_CALLS_AND_TWO_REPLAYS",flush=True)


if __name__=="__main__":
    try:main()
    except BaseException as e:
        print("FAIL2_SHINY_CONTROL_SUITE_UNQUALIFIED",repr(e),flush=True)
        raise
