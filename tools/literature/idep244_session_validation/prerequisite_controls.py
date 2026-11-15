#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Unavailable exact prerequisites skip session-dependent analysis before R."""
from pathlib import Path
import argparse
import shutil
import subprocess
import sys

VALIDATION=Path(__file__).resolve().parent
MODULE=VALIDATION.parent / "idep244_session"
sys.path.insert(0,str(MODULE))
from Core import *


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime",required=True,type=Path)
    p.add_argument("--transport",required=True,type=Path)
    p.add_argument("--request",required=True,type=Path)
    p.add_argument("--mapped-request",required=True,type=Path)
    p.add_argument("--out",required=True,type=Path)
    a=p.parse_args()
    ready=coherent_provider(a.runtime)
    out=new_output(a.out,[MODULE,a.runtime,a.transport,a.request,a.mapped_request,ready["source"],*ready["libraries"]])
    shutil.copyfile(__file__,out / "prerequisite_controls.py")
    request=load(a.mapped_request);request["schema"]=2;request["profile"]="original-idep244-shiny-r461-windows"
    request["mapping"]["organism_id"]="245"
    save(out / "missing-reference-request.json",request)
    results=[]
    for case,runtime,transport,request_file in [
        ("missing-runtime",out / "absent-runtime",a.transport,a.request),
        ("missing-transport",a.runtime,out / "absent-transport",a.request),
        ("missing-reference",a.runtime,a.transport,out / "missing-reference-request.json")]:
        cmd=[sys.executable,"-X","utf8","-B",MODULE / "run.py","--runtime",runtime,
             "--transport",transport,"--request",request_file,"--out",out / case]
        with (out / (case+".stdout")).open("xb") as so,(out / (case+".stderr")).open("xb") as se:
            r=subprocess.run(list(map(str,cmd)),stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=300)
        folder=out / case;failure=load(folder / "FAILURE.json")
        if (r.returncode!=3 or failure["analysis_admitted"] or failure["original_process_execution_attempted"]
                or (folder / "COMPLETED.json").exists() or (folder / "worker.process.json").exists()
                or not (folder / "WARNINGS.md").is_file() or "SKIP3" not in (out / (case+".stdout")).read_text(encoding="utf-8")):
            raise ValueError("Unavailable prerequisite was not prominently skipped before R: "+case)
        results.append(dict(case=case,exit_code=r.returncode,scientific_R_started=False,
                            cause=failure["cause"],warnings_and_required_action_recorded=True))
        print("PASS_UNAVAILABLE_PREREQUISITE_SKIPPED",case,flush=True)
    save(out / "MANIFEST.json",inventory(out))
    save(out / "COMPLETED.json",dict(status="PASS_THREE_SESSION_PREREQUISITE_SKIPS_BEFORE_R",
        manifest=pin(out / "MANIFEST.json"),cases=results,catalog_credit_added=0))


if __name__=="__main__":main()
