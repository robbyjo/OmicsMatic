#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate NEW original LDpred2 1.12.2 references; never run the Java implementation."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,tarfile,time,traceback,zipfile

HERE=Path(__file__).resolve().parent
EXPECTED='443a5529ae2596eb3cf8f19760893b96beba4f12a4decbf6bea645db4d2187f6'
URL='https://cran.r-project.org/src/contrib/Archive/bigsnpr/bigsnpr_1.12.2.tar.gz'
ACTION='Use the exact permitted CRAN 1.12.2 archive and a working original bigsnpr 1.12.2 R environment; alternatively retain these supplied references for offline Java tests. Install only where permitted. Missing/forbidden reference prerequisites require SKIP3; no dependent regeneration is accepted. Other failures require FAIL2, retained consoles and NEW output paths. Native analysis does not require R. This does not qualify LDpred2-auto or the complete paper workflows.'
def pin(path):
    raw=Path(path).read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--archive',type=Path,required=True);p.add_argument('--rscript',required=True)
    p.add_argument('--wsl-distro');p.add_argument('--out',type=Path,required=True);p.add_argument('--fixture',type=Path,required=True);a=p.parse_args();out=None
    try:
        target=a.out.resolve();target.mkdir(parents=True,exist_ok=False);out=target
        (out/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+ACTION+'\n').encode())
        if a.fixture.exists():raise FileExistsError('Retain existing fixtures; select a NEW fixture path')
        if pin(a.archive)['sha256']!=EXPECTED:raise ValueError('Exact original CRAN 1.12.2 archive differs')
        original=out/'original-source'
        with tarfile.open(a.archive,'r:gz') as tar:
            for name in ['DESCRIPTION','R/LDpred2.R','src/ldpred2.cpp','src/ldpred2-sampling.cpp']:
                member=tar.getmember('bigsnpr/'+name)
                if not member.isfile():raise ValueError('Original source member must be a regular file')
                dest=original/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(tar.extractfile(member).read())
        def runtime_path(path):
            s=Path(path).resolve().as_posix()
            if not a.wsl_distro:return s
            if len(s)<3 or s[1:3]!=':/':raise ValueError('WSL bridge expects local absolute Windows paths')
            return '/mnt/'+s[0].lower()+s[2:]
        args=[a.rscript,'--vanilla',runtime_path(HERE/'reference_models.R'),runtime_path(out/'results'),runtime_path(original)]
        command=['wsl.exe','-d',a.wsl_distro,'--',*args] if a.wsl_distro else args
        start=time.monotonic()
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
        save(out/'PROCESS.json',dict(command=command,exit_code=result.returncode,seconds=round(time.monotonic()-start,2)))
        if result.returncode==127:raise FileNotFoundError('Configured original R executable unavailable; inspect preserved consoles')
        if result.returncode:raise RuntimeError('Original R reference failed; read preserved consoles')
        result_dir=out/'results';cases=(result_dir/'case-index.txt').read_text().splitlines()
        if len(cases)!=49 or (result_dir/'COMPLETED.txt').read_text().splitlines()[0]!='PASS_UNCHANGED_ORIGINAL_BIGSNPR_1122_MODEL_REFERENCES':raise ValueError('Complete expected original reference population required')
        files={p.relative_to(result_dir).as_posix():pin(p) for p in sorted(result_dir.rglob('*')) if p.is_file() and not p.name.startswith('private-')}
        provenance=dict(status='PASS_UNCHANGED_ORIGINAL_BIGSNPR_1122_REFERENCE_GENERATION',archive=pin(a.archive),archive_url=URL,
            project_reference_sources={p.name:pin(p) for p in [HERE/'reference_models.R',Path(__file__)]},
            original_source_files={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()},
            cases=cases,original_core_divergences=[name for name in cases if (result_dir/name/'grid-core-status.txt').read_text().strip()=='DIVERGENCE'],
            actual_R_function_bodies_checked_against_CRAN=3,original_function_bodies_modified=False,native_code_replaced=False,
            reference_binary_independently_rebuilt_from_source=False,private_memory_mapped_files_included=False,
            full_author_environment_reproduced=False,whole_goal_complete=False,files=files)
        save(out/'PROVENANCE.json',provenance)
        a.fixture.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(a.fixture,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name in files:z.writestr(name,(result_dir/name).read_bytes())
            z.writestr('provenance.json',json.dumps(provenance,indent=2)+'\n')
        save(out/'COMPLETED.json',dict(status=provenance['status'],fixture=pin(a.fixture),cases=len(cases),original_divergences=len(provenance['original_core_divergences'])))
        print(json.dumps(dict(status=provenance['status'],fixture=str(a.fixture),cases=len(cases),original_divergences=provenance['original_core_divergences'])),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,FileNotFoundError) else 2
        status='SKIP3_ORIGINAL_LDPRED2_REFERENCE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_LDPRED2_REFERENCE_UNADMITTED'
        if out is not None:save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        print(status,repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
