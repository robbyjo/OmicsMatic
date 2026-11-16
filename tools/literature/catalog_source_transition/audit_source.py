#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inventory the exact proposed source transition; this does NOT admit a catalog."""
from pathlib import Path
import argparse
import base64
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.parse

sys.dont_write_bytecode=True
MODULE=Path(__file__).resolve().parent
ROOT=MODULE.parents[2]
BASELINE="357c9301be726b3b6dc2a4e92cf71ff05ed053d2"
TARGET="fc6e0afcabb6144df9a3c161e3007e4d36423f2d"
META=["docs/literature/omics-2023-2026/README.md","docs/literature/omics-2023-2026/WARNINGS.md",
      "docs/literature/omics-2023-2026/papers.json","docs/literature/omics-2023-2026/summary.json",
      "docs/literature/omics-2023-2026/tool-inventory.json","site/literature-review.html",
      "site/literature-warnings.html","tools/literature/reviewed-uses.json"]


def git(*args,root=ROOT):return subprocess.check_output(["git",*args],cwd=root)
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(Path(path).read_bytes())
def save(path,value):
    with Path(path).open("xb") as f:f.write((json.dumps(value,indent=2,sort_keys=True)+"\n").encode("utf-8"))


def tree(commit):
    result={}
    for entry in git("ls-tree","-rz","--full-tree",commit).split(b"\0"):
        if not entry:continue
        info,name=entry.split(b"\t",1);mode,kind,oid=info.decode().split()
        result[name.decode("utf-8")]=dict(mode=mode,kind=kind,git_object=oid)
    return result


def category(name):
    if name.startswith("src/main/java/org/jlinalg/") and "/Study" in name:return "study-application-source"
    if name=="src/main/java/org/jlinalg/cli/HopsAssessmentWorkflowCli.java":return "unadmitted-hops-assessment-source"
    if name.startswith("src/test/java/org/jlinalg/"):return "application-test-source"
    if name.startswith("src/main/resources/org/jlinalg/study/"):return "study-writing-resources"
    if name.startswith(("docs/vignettes/","site/vignettes/")) or name=="docs/manuscript-writing-template.md":return "application-documentation"
    if name.startswith(("tools/literature/idep244","docs/literature/idep244-","examples/idep244")) or name.startswith("docs/literature/IDEP244"):return "unadmitted-idep-development"
    if name.startswith("docs/literature/velociraptor120-development/"):return "historical-unadmitted-velociraptor-dossier"
    if name.startswith("output/pdf/") or name.startswith("examples/study-pipeline/manuscript-"):return "historical-or-example-application-artifacts"
    if name=="docs/literature/HOPS_RMA_ASSESSMENT.md" or name.startswith("tools/literature/hops_assessment_workflow/"):return "unadmitted-hops-assessment-source"
    if name=="docs/literature/MAIN_MERGE_20261115_STATUS.md":return "retained-original-main-merge-refusal"
    return "UNREVIEWED"


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--baseline-checkout",type=Path,required=True)
    p.add_argument("--jdk",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args();baseline=a.baseline_checkout.resolve();out=a.out.resolve()
    if out.exists():raise FileExistsError("Keep the previous review; choose NEW output")
    if git("rev-parse","HEAD").decode().strip()!=TARGET or git("rev-parse","HEAD",root=baseline).decode().strip()!=BASELINE:
        raise ValueError("Exact declared before/after commits required")
    if git("diff","--name-only","HEAD"):raise ValueError("Current tracked source has uncommitted changes")
    if git("status","--porcelain=v1","-z","--untracked-files=all",root=baseline):raise ValueError("Baseline must stay clean")
    out.mkdir(parents=False)
    (out / "audit-engine").mkdir()
    for name in ["audit_source.py","SourceMembers.java"]:
        shutil.copyfile(MODULE/name,out/"audit-engine"/name)
    before,after=tree(BASELINE),tree(TARGET)
    changes=[];java=[]
    for name in sorted(set(before)|set(after)):
        old,new=before.get(name),after.get(name)
        if old==new:continue
        status="A" if old is None else "D" if new is None else "M"
        row=dict(path=name,status=status,category=category(name),before=old,after=new)
        for label,entry,root in [("before",old,baseline),("after",new,ROOT)]:
            if entry and entry["kind"]=="blob":
                raw=git("cat-file","blob",entry["git_object"])
                actual=(root/name).read_bytes()
                conversion=None
                if actual!=raw:
                    filtered=git("hash-object","--path="+name,"--",name,root=root).decode().strip()
                    if actual.replace(b"\r\n",b"\n")!=raw or filtered!=entry["git_object"]:
                        raise ValueError("Actual source has more than declared Git CRLF checkout conversion: "+name)
                    conversion="Exact CRLF-to-LF checkout filter only; both raw identities retained, no file rewritten"
                row[label]=dict(**entry,git_blob_identity=rawpin(raw),working_file_identity=rawpin(actual),checkout_conversion=conversion)
                if name.startswith("src/main/java/") and name.endswith(".java"):java.append((label,name,root/name))
        changes.append(row)
    for name in META:
        if before[name]!=after[name] or (baseline/name).read_bytes()!=(ROOT/name).read_bytes():
            raise ValueError("Exact eight catalog files must remain unchanged: "+name)
    (out / "complete-transition.patch").write_bytes(git("diff","--binary",BASELINE,TARGET))
    save(out / "CHANGE-INVENTORY.json",dict(status="UNADMITTED_SOURCE_TRANSITION_INVENTORY_ONLY",baseline=BASELINE,target=TARGET,
        catalog_metadata_unchanged=True,catalog_files={n:pin(ROOT/n) for n in META},changes=changes,
        changed_files=len(changes),unknown=[r["path"] for r in changes if r["category"]=="UNREVIEWED"]))
    classes=out / "parser-classes";classes.mkdir()
    compile_command=[str(a.jdk / "bin/javac.exe"),"--release","21","-d",str(classes),str(out / "audit-engine/SourceMembers.java")]
    with (out / "javac.stdout").open("xb") as so,(out / "javac.stderr").open("xb") as se:r=subprocess.run(compile_command,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=120)
    save(out / "javac.process.json",dict(command=compile_command,exit_code=r.returncode))
    if r.returncode:raise ValueError("JDK21 AST inventory compilation failed")
    command=[str(a.jdk / "bin/java.exe"),"-cp",str(classes),"SourceMembers",*[str(path) for label,name,path in java]]
    with (out / "members.tsv").open("xb") as so,(out / "parser.stderr").open("xb") as se:r=subprocess.run(command,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=120)
    save(out / "parser.process.json",dict(command=command,exit_code=r.returncode))
    if r.returncode:raise ValueError("Complete Java syntax inventory failed")
    lookup={path.resolve().as_uri():(label,name) for label,name,path in java};members={}
    for line in (out / "members.tsv").read_text(encoding="utf-8").splitlines():
        uri,kind,key,canonical=line.split("\t")
        label,name=lookup[uri]
        key=base64.b64decode(key).decode("utf-8");canonical=base64.b64decode(canonical).decode("utf-8")
        index=(name,kind,key)
        if label in members.setdefault(index,{}):raise ValueError("Duplicate AST member identity")
        members[index][label]=canonical
    delta=[]
    for (name,kind,key),values in sorted(members.items()):
        if values.get("before")==values.get("after"):continue
        delta.append(dict(path=name,kind=kind,key=key,**{label:dict(canonical=text,**rawpin(text.encode("utf-8"))) for label,text in values.items()}))
    save(out / "JAVA-MEMBER-DELTA.json",dict(status="UNADMITTED_JAVAC_SYNTAX_MEMBER_INVENTORY_NOT_SEMANTIC_EQUIVALENCE",members=delta,
        parser_source=pin(MODULE / "SourceMembers.java"),source_files=len(java),fresh_scientific_calls_repeated=False))
    brief={}
    for row in delta:
        if row["kind"] not in ["COMPLETE_UNIT","MEMBER_ORDER"]:brief.setdefault(row["path"],[]).append(row["kind"]+" "+row["key"])
    save(out / "JAVA-MEMBER-SUMMARY.json",brief)
    print(json.dumps(dict(status="UNADMITTED_SOURCE_REVIEW_INVENTORY_CREATED",changed_files=len(changes),
        changed_or_added_main_Java_files=len(brief),changed_AST_members=len(delta),
        unreviewed_file_categories=[r["path"] for r in changes if r["category"]=="UNREVIEWED"]),indent=2),flush=True)


if __name__=="__main__":main()
