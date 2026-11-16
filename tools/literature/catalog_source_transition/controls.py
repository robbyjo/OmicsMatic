#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual synthetic Git/source/metadata refusals; never edit the real qualified tree."""
from pathlib import Path
import argparse
import shutil
import subprocess
import sys

sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from catalog_source_transition.bridge import *


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",required=True,type=Path)
    a=p.parse_args();out=a.out.resolve()
    if out.exists():raise FileExistsError("Preserve old controls and choose NEW output")
    if not out.is_relative_to(ROOT / "build"):raise ValueError("Use an owned build output")
    out.mkdir(parents=True);shutil.copyfile(__file__,out/"controls-source.py")
    mini=out / "synthetic-repository";mini.mkdir()
    def run(*args):return subprocess.check_output(["git",*args],cwd=mini,stderr=subprocess.PIPE)
    run("init","-q","-b","main")
    (mini/".gitattributes").write_bytes(b"*.txt text eol=lf\n")
    science=mini / "src/science.txt";science.parent.mkdir();science.write_bytes(b"original synthetic scientific source\n")
    helper=mini / MODULE_PATH / "bridge.py";helper.parent.mkdir(parents=True);helper.write_bytes(b"original synthetic verifier\n")
    for name in META:
        path=mini/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b"original synthetic metadata\n")
    run("add",".");run("-c","user.name=OmicsMatic synthetic control","-c","user.email=synthetic-control@example.invalid","commit","-q","-m","Synthetic baseline")
    anchor=run("rev-parse","HEAD").decode().strip();rows=[]
    science_raw=science.read_bytes();helper_raw=helper.read_bytes();meta=mini/META[0];meta_raw=meta.read_bytes()
    def control(name,call,expected=None):
        try:call()
        except (ValueError,FileNotFoundError) as e:
            if expected is None or not isinstance(e,expected):raise
            rows.append(dict(case=name,refused=True,exception=type(e).__name__,cause=str(e)));return
        if expected is not None:raise ValueError("A deliberately invalid control was accepted: "+name)
        rows.append(dict(case=name,accepted_with_declared_scope=True))
    guard=lambda:current_source_guard(mini,anchor=anchor)
    control("unchanged-source",guard)
    science.write_bytes(b"deliberately changed synthetic source\n")
    control("modified-scientific-source",guard,ValueError);science.write_bytes(science_raw)
    science.unlink()
    control("deleted-scientific-source",guard,ValueError);science.write_bytes(science_raw)
    moved=science.with_name("science-renamed.txt");science.rename(moved)
    control("moved-scientific-source",guard,ValueError);moved.rename(science)
    meta.write_bytes(b"deliberately changed synthetic metadata\n")
    control("metadata-excluded-from-source-diff-only",guard)
    control("metadata-still-requires-exact-pins",lambda:require_pins(mini,{META[0]:rawpin(meta_raw)}),ValueError)
    meta.write_bytes(meta_raw)
    helper.write_bytes(b"deliberately changed synthetic verifier\n")
    control("own-code-excluded-from-source-diff-only",guard)
    control("own-code-still-requires-exact-pins",lambda:require_pins(mini,{MODULE_PATH+"/bridge.py":rawpin(helper_raw)}),ValueError)
    helper.write_bytes(helper_raw)
    (mini / "new-unadmitted-source.txt").write_bytes(b"addition is not qualification\n")
    control("new-source-addition-does-not-promote-any-tool",guard)
    control("unavailable-anchor-is-named-prerequisite",lambda:current_source_guard(mini,anchor="0"*40),FileNotFoundError)
    control("exact-pins-restored",lambda:require_pins(mini,{"src/science.txt":rawpin(science_raw),META[0]:rawpin(meta_raw),MODULE_PATH+"/bridge.py":rawpin(helper_raw)}))
    def metadata_interface():
        from hicat109_catalog.bridge import metadata_view
        actual={n:(ROOT/n).read_bytes() for n in META};supplied=dict(actual);supplied[META[0]]+=b"\n"
        view=metadata_view(ROOT,supplied)
        if pin(view/META[0])!=rawpin(supplied[META[0]]) or (ROOT/META[0]).read_bytes()!=actual[META[0]]:
            raise ValueError("The real predecessor metadata-only view was lost or wrote a file")
    control("actual-predecessor-metadata-view-preserved-without-writing",metadata_interface)
    alternate=science_raw.replace(b"\n",b"\r\n")
    conversion={"src/science.txt":dict(git_object=run("rev-parse",anchor+":src/science.txt").decode().strip(),
        allowed_file_identities=[rawpin(science_raw),rawpin(alternate)])}
    control("exact-recorded-LF-checkout",lambda:require_checkout_pins(mini,conversion))
    science.write_bytes(alternate)
    control("exact-recorded-CRLF-checkout",lambda:require_checkout_pins(mini,conversion))
    science.write_bytes(b"unrecorded bytes\r\n")
    control("unrecorded-source-bytes-not-normalized",lambda:require_checkout_pins(mini,conversion),ValueError)
    science.write_bytes(science_raw)
    save(out / "COMPLETED.json",dict(status="PASS_FIFTEEN_SYNTHETIC_SOURCE_METADATA_VERIFIER_VIEW_AND_CHECKOUT_CONTROLS",
        cases=rows,actual_scientific_project_source_modified=False,scientific_calls_executed=False,new_scoped_integration_credit=0))
    print("PASS_FIFTEEN_SYNTHETIC_SOURCE_METADATA_VERIFIER_VIEW_AND_CHECKOUT_CONTROLS",flush=True)


if __name__=="__main__":main()
