#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resealed copies must still fail scientific-request/provider contradictions."""
from pathlib import Path
import argparse
import copy
import shutil
import sys

VALIDATION=Path(__file__).resolve().parent
MODULE=VALIDATION.parent / "idep244_session"
sys.path.insert(0,str(MODULE))
from Core import *
from verify import check_bundle


def overwrite(path,value):
    # Deliberate corruption exists only in separately marked negative copies.
    Path(path).write_bytes(encoded(value))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--bundle",required=True,type=Path)
    p.add_argument("--runtime",required=True,type=Path)
    p.add_argument("--out",required=True,type=Path)
    a=p.parse_args()
    run,request,inputs,ready=check_bundle(a.bundle)
    out=new_output(a.out,[a.bundle,a.runtime,MODULE,ready["source"],*ready["libraries"]])
    before_bundle=inventory(a.bundle);before_runtime=inventory(a.runtime)
    (out / "WARNINGS.md").write_text("# Intentional invalid negative controls\n\nThese copies contain deliberately altered and resealed records. They are never valid scientific results or runtime providers. Only the named refusal outcomes are evidence. Original bundles and providers must remain unchanged. No scientific R process is launched by these structural checks.\n",encoding="utf-8")
    shutil.copyfile(__file__,out / "coherence_controls.py")
    results=[]
    def expect(label,folder,call,substring):
        try:call()
        except ValueError as e:
            if substring not in str(e):raise ValueError("Wrong refusal for "+label+": "+str(e)) from e
            row=dict(case=label,metadata_hashes_resealed=True,rejected=True,refusal=str(e),
                     scientific_R_process_started=False,negative_copy=str(folder))
            results.append(row);print(row,flush=True)
            return
        raise ValueError("Resealed contradictory records were accepted: "+label)
    def bundle_copy(label,edit,substring):
        folder=out/label;shutil.copytree(a.bundle,folder)
        save(folder / "NEGATIVE-CONTROL.json",dict(case=label,intentionally_invalid=True,result_admitted=False))
        original=load(folder / "original-request.json");effective=load(folder / "effective-request.json");captured=load(folder / "captured-inputs.json")
        edit(folder,original,effective,captured)
        overwrite(folder / "original-request.json",original);overwrite(folder / "effective-request.json",effective)
        overwrite(folder / "captured-inputs.json",captured)
        (folder / "SUPPLIED-REQUEST.json").write_bytes((folder / "original-request.json").read_bytes())
        r=load(folder / "RUN.json")
        for field,name in [("original_request","original-request.json"),("effective_request","effective-request.json"),("captured_inputs","captured-inputs.json")]:r[field]=pin(folder/name)
        overwrite(folder / "RUN.json",r)
        members=inventory(folder);members.pop("MANIFEST.json");members.pop("COMPLETED.json")
        overwrite(folder / "MANIFEST.json",members)
        done=load(folder / "COMPLETED.json");done["run"]=pin(folder / "RUN.json");done["manifest"]=pin(folder / "MANIFEST.json")
        overwrite(folder / "COMPLETED.json",done)
        now=inventory(folder);now.pop("MANIFEST.json");now.pop("COMPLETED.json")
        if now!=members:raise ValueError("Negative copy failed to reseal its complete inventory")
        expect(label,folder,lambda:check_bundle(folder),substring)
    def original_fc(folder,o,e,c):o["analysis"]["min_fold_change"]=3
    def effective_fc(folder,o,e,c):e["analysis"]["min_fold_change"]=3
    def extension(folder,o,e,c):
        old=e["expression"];new=str(PurePosixPath(old).with_suffix(".tsv"))
        (folder/old).rename(folder/new);c[new]=c.pop(old);e["expression"]=new
    bundle_copy("original-scientific-option",original_fc,"Original/effective request contradiction")
    bundle_copy("effective-scientific-option",effective_fc,"Original/effective request contradiction")
    bundle_copy("changed-loader-extension",extension,"Captured input contradicts the exact requested path/name")
    def provider_copy(label,edit,substring):
        folder=out/label;shutil.copytree(a.runtime,folder)
        save(folder / "NEGATIVE-CONTROL.json",dict(case=label,intentionally_invalid=True,runtime_admitted=False))
        r=load(folder / "READY.json");i=load(folder / "INSPECTED.json");d=load(folder / "COMPLETED.json");index=load(folder / "runtime-files.json")
        edit(folder,r,i,d,index)
        overwrite(folder / "INSPECTED.json",i);r["inspected"]=pin(folder / "INSPECTED.json")
        overwrite(folder / "runtime-files.json",index);r["runtime_index"]=pin(folder / "runtime-files.json")
        overwrite(folder / "READY.json",r);d["ready"]=pin(folder / "READY.json")
        overwrite(folder / "COMPLETED.json",d)
        expect(label,folder,lambda:coherent_provider(folder,inspect_files=False),substring)
    def re_count(done,index):done["locked_files"]=sum(len(x) for x in index.values())
    def package_root(r):return str(Path(next(p for p in r["installed_packages"] if p["name"]=="abind")["path"]).resolve())
    def empty(folder,r,i,d,index):index.clear();re_count(d,index)
    def missing_package(folder,r,i,d,index):
        root=package_root(r)
        r["installed_packages"]=[x for x in r["installed_packages"] if x["name"]!="abind"]
        i["packages"]=[x for x in i["packages"] if x["name"]!="abind"]
        index.pop(root);d["packages"]=len(i["packages"]);re_count(d,index)
    def wrong_version(folder,r,i,d,index):
        for rows in [r["installed_packages"],i["packages"]]:
            next(x for x in rows if x["name"]=="abind")["version"]="1.4.9"
    def wrong_libraries(folder,r,i,d,index):i["libraries"][0],i["libraries"][1]=i["libraries"][1],i["libraries"][0]
    def missing_root(folder,r,i,d,index):index.pop(package_root(r));re_count(d,index)
    def empty_folder(folder,r,i,d,index):index[package_root(r)]={};re_count(d,index)
    def escaping_member(folder,r,i,d,index):
        index[package_root(r)]["../escape"]=dict(bytes=0,sha256="0"*64);re_count(d,index)
    def wrong_count(folder,r,i,d,index):d["locked_files"]-=1
    def extra_root(folder,r,i,d,index):
        root=folder / "fabricated-root";root.mkdir();(root / "note.txt").write_bytes(b"invalid extra runtime root\n")
        index[str(root.resolve())]={"note.txt":pin(root / "note.txt")};re_count(d,index)
    for label,edit,expected in [
        ("empty-runtime-index",empty,"runtime index is empty"),
        ("missing-transitive-package",missing_package,"Missing selected runtime dependency/namespace"),
        ("wrong-installed-version",wrong_version,"Actual package DESCRIPTION contradicts"),
        ("contradictory-library-priority",wrong_libraries,"INSPECTED/READY/source profile/completion contradiction"),
        ("missing-selected-runtime-root",missing_root,"Runtime index must cover exactly"),
        ("extra-runtime-root",extra_root,"Runtime index must cover exactly"),
        ("empty-selected-folder",empty_folder,"complete nonempty file inventory"),
        ("escaping-runtime-member",escaping_member,"Malformed runtime file identity or escaping inventory member"),
        ("wrong-locked-file-count",wrong_count,"Complete locked-file count contradicts")]:
        provider_copy(label,edit,expected)
    if inventory(a.bundle)!=before_bundle or inventory(a.runtime)!=before_runtime:
        raise ValueError("Original runtime or scientific bundle changed during negative controls")
    save(out / "MANIFEST.json",inventory(out))
    save(out / "COMPLETED.json",dict(status="PASS_TWELVE_RESEALED_REQUEST_AND_RUNTIME_CONTRADICTION_REFUSALS",
        manifest=pin(out / "MANIFEST.json"),cases=results,original_bundle_and_provider_unchanged=True,
        full_provider_inventory_checked_before_controls=True,negative_provider_checks_structural_only=True,
        scientific_fits_repeated=False,catalog_credit_added=0,whole_goal_complete=False))
    print("PASS_TWELVE_RESEALED_REQUEST_AND_RUNTIME_CONTRADICTION_REFUSALS",flush=True)


if __name__=="__main__":main()
