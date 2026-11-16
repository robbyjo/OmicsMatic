#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual CSV symlink -> differently suffixed target, original calls and replay."""
from pathlib import Path
import argparse
import os
import secrets
import shutil
import subprocess
import sys

VALIDATION=Path(__file__).resolve().parent
MODULE=VALIDATION.parent / "idep244_session"
sys.path.insert(0,str(MODULE))
from Core import *
from Transport import free_port,launch_session,transport_check
from run import record_notifications
from verify import check_bundle


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime",required=True,type=Path)
    p.add_argument("--transport",required=True,type=Path)
    p.add_argument("--request",required=True,type=Path)
    p.add_argument("--out",required=True,type=Path)
    a=p.parse_args()
    ready=coherent_provider(a.runtime);ws=transport_check(a.transport)
    request=load(a.request);original=Path(request["expression"]);original_pin=pin(original)
    out=new_output(a.out,[MODULE,a.runtime,a.transport,a.request,original,ready["source"],*ready["libraries"]])
    shutil.copyfile(__file__,out / "path_alias_control.py")
    shutil.copyfile(VALIDATION / "DirectOriginalSession.R",out / "DirectOriginalSession.R")
    target=out / "counts-target.xlsx";alias=out / "counts-alias.csv"
    shutil.copyfile(original,target)
    try:os.symlink(target,alias)
    except OSError as e:
        note="SKIP3: Windows file-symlink creation is unavailable for this alias control. No alias-dependent analysis ran. Enable the permitted Windows symlink facility or supply a permitted test environment and retry a NEW path. Regular-file session controls remain separately scoped."
        save(out / "SKIPPED.json",dict(status="SKIP3_PATH_ALIAS_CONTROL_UNAVAILABLE",cause=repr(e),action=note))
        (out / "WARNINGS.md").write_text("# Warnings and required actions\n\n"+note+"\n",encoding="utf-8")
        print(note,flush=True);return 3
    if not alias.is_symlink() or alias.resolve()!=target.resolve() or pin(alias)!=original_pin:
        raise ValueError("Declared actual CSV symlink fixture was not created")
    request["expression"]=str(alias)
    save(out / "request.json",request)
    save(out / "FIXTURE.json",dict(original=str(original),original_pin=original_pin,
        alias=str(alias),resolved_target=str(alias.resolve()),alias_extension=".csv",target_extension=".xlsx",
        payload="Exact unchanged original CSV bytes, deliberately named .xlsx as a filename-alias control; no Excel-format validity is claimed"))
    def process(label,cmd):
        with (out / (label+".stdout")).open("xb") as so,(out / (label+".stderr")).open("xb") as se:
            r=subprocess.run(list(map(str,cmd)),stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
        save(out / (label+".process.json"),dict(command=list(map(str,cmd)),exit_code=r.returncode))
        if r.returncode:raise ValueError("Alias control command failed: "+label)
        print("PASS_ALIAS_CONTROL_COMMAND",label,flush=True)
    process("run",[sys.executable,"-X","utf8","-B",MODULE / "run.py","--runtime",a.runtime,
        "--transport",a.transport,"--request",out / "request.json","--out",out / "run"])
    run,effective,inputs,unused=check_bundle(out / "run")
    if effective["expression"]!="inputs/expression/counts-alias.csv" or Path(inputs[effective["expression"]]["source"])!=target.resolve():
        raise ValueError("Capture did not preserve the actual literal alias extension and resolved source identity")
    direct=out / "direct";direct.mkdir();warning(direct)
    (direct / "original/stages").mkdir(parents=True);(direct / "inputs/expression").mkdir(parents=True);(direct / "engine").mkdir()
    shutil.copyfile(VALIDATION / "DirectOriginalSession.R",direct / "engine/DirectOriginalSession.R")
    shutil.copyfile(LEGACY / "SourceAudit.R",direct / "engine/SourceAudit.R")
    # Read the actual original alias outside the owned output tree. The output
    # monitor deliberately refuses links in result directories; caller input
    # aliases remain separate inputs to unchanged original functions.
    direct_filename="../../../counts-alias.csv"
    direct_path=direct / "inputs/expression" / direct_filename
    if not direct_path.is_symlink() or direct_path.resolve()!=target.resolve():
        raise ValueError("Independent literal path does not access the actual original alias")
    port,token=free_port(),secrets.token_hex(32)
    save(direct / "control.json",dict(source=ready["source"],port=port,token=token,filename=direct_filename))
    (direct / "libraries.txt").write_text("\n".join(ready["libraries"])+"\n",encoding="utf-8")
    result=launch_session([ready["rscript"],"--vanilla","engine/DirectOriginalSession.R","control.json"],
        direct,dict(wall_seconds=180,files=20000,bundle_bytes=256*1024*1024),port,token,ws)
    record_notifications(direct)
    if result["exit_code"] or load(direct / "received-notifications.json"):
        raise ValueError("Independent original alias call failed or gave an unexpected notification")
    comparison=out / "comparison";comparison.mkdir()
    shutil.copyfile(MODULE / "CompareSession.R",comparison / "CompareSession.R")
    result=launch([ready["rscript"],"--vanilla",str(comparison / "CompareSession.R"),
        str(direct / "libraries.txt"),str(out / "run"),str(direct),"PAIRWISE.json","success"],
        comparison,"comparison",dict(wall_seconds=180,files=1000,bundle_bytes=64*1024*1024))
    if result["exit_code"]:raise ValueError("Complete original alias values or conditions differ")
    process("replay",[sys.executable,"-X","utf8","-B",MODULE / "verify.py","--bundle",out / "run","--out",out / "replay"])
    if pin(original)!=original_pin or pin(target)!=original_pin or pin(alias)!=original_pin:
        raise ValueError("Original alias control inputs changed")
    # This fixture intentionally includes file symlinks. Record actual literal
    # target paths separately; the scientific run/replay bundles contain regular
    # captured files and retain their own complete link-refusing inventories.
    save(out / "COMPLETED.json",dict(status="PASS_ACTUAL_DIFFERENT_EXTENSION_ALIAS_ORIGINAL_CALLS_AND_REPLAY",
        fixture=pin(out / "FIXTURE.json"),comparison=load(comparison / "PAIRWISE.json"),
        original_and_target_bytes_unchanged=True,original_alias_extension_retained=True,
        complete_captured_replay=True,catalog_credit_added=0,whole_tool_qualified=False))
    print("PASS_ACTUAL_DIFFERENT_EXTENSION_ALIAS_ORIGINAL_CALLS_AND_REPLAY",flush=True)
    return 0


if __name__=="__main__":raise SystemExit(main())
