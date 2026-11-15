#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run real public iDEP requests and independent unchanged-original controls."""
from pathlib import Path
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import shutil
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "idep244"))
from Runtime import HERE, engine_sources, new_output, save, load, launch, pin, warning

ROOT = HERE.parents[2]
VALIDATION = Path(__file__).resolve().parent


def command(out, label, args):
    with (out / (label + ".stdout")).open("xb") as so, (out / (label + ".stderr")).open("xb") as se:
        p = subprocess.run(list(map(str, args)), cwd=ROOT, stdin=subprocess.DEVNULL,
                           stdout=so, stderr=se, timeout=900)
    save(out / (label + ".process.json"), dict(command=list(map(str,args)),exit_code=p.returncode))
    return p.returncode


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime", required=True, type=Path)
    p.add_argument("--inputs", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    a = p.parse_args()
    out = new_output(a.out)
    source_before = engine_sources()
    save(out / "ENGINE-SOURCES.json", source_before)
    validation_before = {p.name:pin(p) for p in VALIDATION.iterdir() if p.suffix in [".R",".py"]}
    save(out / "VALIDATION-SOURCES.json", validation_before)
    selected = load(a.runtime / "READY.json")
    cases = [("disabled",0),("mapped",0),("designed",0),
             ("original-rank-error",2),("missing-reference",3),("original-no-counts",2)]
    def one(entry):
        name, expected = entry
        result = command(out,name,[
            sys.executable,"-X","utf8","-B",HERE/"run.py","--runtime",a.runtime,
            "--request",a.inputs/(name+".json"),"--out",out/name])
        if result != expected:
            raise AssertionError((name,result,expected,(out/(name+".stdout")).read_text(errors="replace")[-3000:]))
        if expected == 0:
            assert (out/name/"COMPLETED.json").is_file()
        else:
            failure = load(out/name/"FAILURE.json")
            assert not (out/name/"COMPLETED.json").exists()
            assert not failure["analysis_admitted"]
            if expected == 3:
                assert not (out/name/"worker.stdout").exists()
            if name == "original-rank-error":
                status = load(out/name/"original/R-status.json")
                assert status["phase"] == "limma_value" and status["original_dispatcher_returned"]
                assert status["original_stages"][-1] == "limma_value"
        return dict(case=name,exit_code=result,expected=expected)
    # Only two independent children; each has its own home, temp, RNG and job.
    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(one,cases))
    save(out / "PUBLIC-CASES.json",outcomes)
    print("PASS_SIX_PUBLIC_CASE_OUTCOMES",flush=True)
    for name in ["disabled","mapped","designed"]:
        result = command(out,"replay-"+name,[
            sys.executable,"-X","utf8","-B",HERE/"verify.py",
            "--bundle",out/name,"--out",out/("replay-"+name)])
        if result:
            raise AssertionError(("public replay",name,result))
    print("PASS_THREE_COMPLETE_CAPTURED_INPUT_REPLAYS",flush=True)
    libraries = a.runtime / "libraries-requested.txt"
    for name in ["disabled","mapped","designed","original-rank-error","original-no-counts"]:
        direct = out / ("direct-"+name)
        direct.mkdir()
        result = launch([selected["rscript"],"--vanilla",VALIDATION/"DirectOriginal.R",
                         a.inputs,selected["source"],libraries,HERE/"SourceAudit.R",name],
                        direct,"direct",dict(wall_seconds=180,files=20000,bundle_bytes=256*1024*1024))
        if result["exit_code"]:
            raise AssertionError(("independent original",name))
        if name in ["disabled","mapped","designed"]:
            (direct/"libraries.txt").write_text("\n".join(selected["libraries"])+"\n",encoding="utf-8",newline="\n")
            compared = launch([selected["rscript"],"--vanilla",HERE/"Compare.R",
                out/name/"original/complete-original-returns.rds",
                direct/"original/complete-original-returns.rds","PAIRWISE.json","4096"],
                direct,"compare",dict(wall_seconds=180,files=20000,bundle_bytes=256*1024*1024))
            if compared["exit_code"] or not load(direct/"PAIRWISE.json")["complete_values_equal"]:
                raise AssertionError(("complete independent comparison",name))
        else:
            compared = launch([selected["rscript"],"--vanilla",VALIDATION/"CompareFailure.R",
                out/name,direct,"PAIRWISE-FAILURE.json",libraries],
                direct,"compare-failure",dict(wall_seconds=180,files=20000,bundle_bytes=256*1024*1024))
            if compared["exit_code"] or not load(direct/"PAIRWISE-FAILURE.json")["complete_original_values_equal"]:
                raise AssertionError(("complete independent failure comparison",name))
    if engine_sources() != source_before:
        raise AssertionError("Engine source changed during real controls")
    if {p.name:pin(p) for p in VALIDATION.iterdir() if p.suffix in [".R",".py"]} != validation_before:
        raise AssertionError("Independent control source changed during execution")
    result = dict(status="PASS_INITIAL_SIX_PUBLIC_IDEP244_CASES_THREE_REPLAYS_AND_INDEPENDENT_ORIGINAL_SUCCESS_RETURNS",
                  public_cases=outcomes,complete_replays=3,independent_original_success_comparisons=3,
                  independent_original_error_comparisons=2,
                  source_algorithms_patched=False,whole_tool_qualified=False,
                  catalog_credit_added=0,whole_goal_complete=False)
    save(out/"COMPLETED.json",result)
    print(result["status"],flush=True)


if __name__ == "__main__":
    if not __debug__:
        raise RuntimeError("Never disable control assertions")
    main()
