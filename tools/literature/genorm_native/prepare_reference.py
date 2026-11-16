#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate NEW synthetic fixtures using the unchanged original NormqPCR matrix method."""
import argparse,datetime,hashlib,json,subprocess,sys,tarfile,zipfile
from pathlib import Path

EXPECTED="f556aab9ae67d7d02deda192df6d1adf65520b0c9125797582a5b74839864a35"
URL="https://bioconductor.org/packages/3.23/bioc/src/contrib/NormqPCR_1.58.0.tar.gz"
def pin(path):
    raw=Path(path).read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with Path(path).open("x",encoding="utf-8",newline="\n") as f:json.dump(value,f,indent=2);f.write("\n")
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--archive",type=Path,required=True);p.add_argument("--out",type=Path,required=True)
    p.add_argument("--rscript",type=Path,required=True);p.add_argument("--fixture",type=Path,required=True)
    a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
    if pin(a.archive)["sha256"]!=EXPECTED:raise ValueError("Exact original source archive differs")
    if a.fixture.exists():raise FileExistsError("Preserve the existing fixtures; use a NEW path")
    original=a.out/"original"
    with tarfile.open(a.archive) as tar:
        for name in ["DESCRIPTION","R/geomMean.R","R/stabMeasureM.R","R/selectHKs.R"]:
            raw=tar.extractfile("NormqPCR/"+name).read();dest=original/name
            dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    script=Path(__file__).with_name("reference.R");command=[str(a.rscript),"--vanilla",str(script.resolve()),str(original.resolve()),str((a.out/"results").resolve())]
    with (a.out/"reference.stdout").open("xb") as so,(a.out/"reference.stderr").open("xb") as se:
        run=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
    save(a.out/"process.json",dict(command=command,exit_code=run.returncode))
    if run.returncode:raise RuntimeError("Original reference failed; retain its consoles and partial outputs")
    results=a.out/"results";public={p.relative_to(results).as_posix():pin(p) for p in sorted(results.rglob("*")) if p.is_file() and not p.name.startswith("PRIVATE-")}
    private={p.relative_to(a.out).as_posix():pin(p) for p in sorted(a.out.rglob("*")) if p.is_file()}
    cases=(results/"case-index.txt").read_text().splitlines()
    assert len(cases)==45
    record=dict(status="PASS_ORIGINAL_NORMQPCR158_LOG_TRUE_MATRIX_METHOD_REFERENCE_GENERATION",archive=pin(a.archive),archive_url=URL,source_files={p.relative_to(original).as_posix():pin(p) for p in original.rglob("*") if p.is_file()},project_reference_code=pin(script),cases=cases,public_files=public,private_files=private,utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),package_installed=False,original_function_bodies_modified=False,original_matrix_definition_selected_from_parsed_setMethod=True,full_S4_package_qualified=False,literal_quantity_branch_equivalence_claimed=False,p040_Cq_interpretation_recovered=False)
    save(a.out/"PROVENANCE.json",record)
    a.fixture.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.fixture,"x",compression=zipfile.ZIP_DEFLATED) as z:
        for name in public:
            item=zipfile.ZipInfo(name,date_time=(2026,11,16,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(item,(results/name).read_bytes())
        z.writestr("provenance.json",json.dumps(record,indent=2)+"\n")
    print(json.dumps(dict(status=record["status"],cases=len(cases),fixture=str(a.fixture),identity=pin(a.fixture)),indent=2),flush=True)
if __name__=="__main__":
    try:main()
    except FileNotFoundError as e:
        print("SKIP3_REFERENCE_PREREQUISITE_UNAVAILABLE",repr(e),
            "Reference regeneration was skipped. Supply the exact permitted original archive and R executable, then retry NEW paths. Native Java analyses do not require R.",flush=True)
        raise SystemExit(3)
    except Exception as e:
        print("FAIL2_REFERENCE_NOT_QUALIFIED",repr(e),"Retain all original files; resolve the named cause and retry NEW paths.",flush=True)
        raise SystemExit(2)
