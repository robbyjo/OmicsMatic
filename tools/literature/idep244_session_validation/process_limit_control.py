#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual session wall refusal kills owned Rscript/Rterm and preserves another job."""
from pathlib import Path
import argparse
import ctypes
from ctypes import wintypes
import shutil
import subprocess
import sys
import time

VALIDATION=Path(__file__).resolve().parent
MODULE=VALIDATION.parent / "idep244_session"
sys.path.insert(0,str(MODULE))
from Core import *


def live(pid):
    kernel=ctypes.WinDLL("kernel32",use_last_error=True)
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
    kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]
    kernel.WaitForSingleObject.restype=wintypes.DWORD
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    handle=kernel.OpenProcess(0x00100000,False,int(pid))
    if not handle:
        if ctypes.get_last_error()==87:return False
        raise ctypes.WinError(ctypes.get_last_error())
    try:return kernel.WaitForSingleObject(handle,0)==258
    finally:kernel.CloseHandle(handle)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime",required=True,type=Path)
    p.add_argument("--transport",required=True,type=Path)
    p.add_argument("--request",required=True,type=Path)
    p.add_argument("--out",required=True,type=Path)
    a=p.parse_args();ready=coherent_provider(a.runtime)
    out=new_output(a.out,[MODULE,a.runtime,a.transport,a.request,ready["source"],*ready["libraries"]])
    shutil.copyfile(__file__,out / "process_limit_control.py")
    request=load(a.request);request["limits"]["wall_seconds"]=2
    save(out / "request.json",request)
    unrelated=out / "unrelated-owned-test-job";unrelated.mkdir()
    script=unrelated / "Other.R"
    script.write_text('writeLines(as.character(Sys.getpid()),"native-r-pid.txt")\nstarted <- Sys.time()\nwhile(!file.exists("RELEASE.txt")) {\n if(as.numeric(difftime(Sys.time(),started,units="secs"))>180)stop("Control release deadline exceeded")\n Sys.sleep(.05)\n}\nwriteLines("completed normally","SURVIVED.txt")\n',encoding="utf-8")
    job=Job();process=None
    try:
        with (unrelated / "stdout").open("xb") as so,(unrelated / "stderr").open("xb") as se:
            process=subprocess.Popen([ready["rscript"],"--vanilla",str(script)],cwd=unrelated,
                env=environment(unrelated),stdin=subprocess.DEVNULL,stdout=so,stderr=se,
                creationflags=0x00000004 | 0x08000000)
            job.assign_and_resume(process)
            deadline=time.monotonic()+15
            while not (unrelated / "native-r-pid.txt").is_file() and time.monotonic()<deadline:
                if process.poll() is not None:raise ValueError("Independent test job stopped before the resource control")
                time.sleep(.05)
            other_native=int((unrelated / "native-r-pid.txt").read_text(encoding="ascii").strip())
            cmd=[sys.executable,"-X","utf8","-B",MODULE / "run.py","--runtime",a.runtime,
                 "--transport",a.transport,"--request",out / "request.json","--out",out / "limited-run"]
            with (out / "run.stdout").open("xb") as ro,(out / "run.stderr").open("xb") as re:
                result=subprocess.run(list(map(str,cmd)),stdin=subprocess.DEVNULL,stdout=ro,stderr=re,timeout=180)
            folder=out / "limited-run";record=load(folder / "worker.process.json")
            failure=load(folder / "FAILURE.json")
            if (result.returncode!=2 or record["limit_exceeded"]!="wall_seconds" or failure["analysis_admitted"]
                    or not record["native_execution_resumed"] or not record["descendant_job_closed"]
                    or not record["client_thread_stopped"] or (folder / "COMPLETED.json").exists()):
                raise ValueError("Actual session wall limit did not refuse the result and close its owned group")
            native=record["native_r_pid"]
            if not native or live(record["launcher_pid"]) or live(native):
                raise ValueError("Actual owned Rscript/Rterm descendant was absent from observation or survived")
            if process.poll() is not None or not live(other_native):
                raise ValueError("Another actual R test job did not remain alive across the session refusal")
            (unrelated / "RELEASE.txt").write_bytes(b"permitted synthetic control release\n")
            other_exit=process.wait(timeout=60)
            if other_exit or (unrelated / "SURVIVED.txt").read_text(encoding="utf-8").strip()!="completed normally":
                raise ValueError("The independent actual R test job did not finish normally")
        save(out / "COMPLETED.json",dict(status="PASS_ACTUAL_SESSION_WALL_REFUSAL_OWNED_DESCENDANTS_AND_OTHER_JOB_SURVIVAL",
            limited_launcher_pid=record["launcher_pid"],limited_native_r_pid=native,owned_Rscript_and_Rterm_dead=True,
            other_launcher_pid=process.pid,other_native_r_pid=other_native,other_R_job_alive_after_limit=True,
            other_R_job_completed_normally=True,limit_exceeded="wall_seconds",no_result_admitted=True,
            catalog_credit_added=0))
        print("PASS_ACTUAL_SESSION_WALL_REFUSAL_OWNED_DESCENDANTS_AND_OTHER_JOB_SURVIVAL",flush=True)
    finally:
        # Both groups are synthetic jobs created exclusively by this control.
        # Closing this OTHER job on failure never touches a user's existing R.
        job.close()


if __name__=="__main__":main()
