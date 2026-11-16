#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adopt an existing permitted original Linux bigsnpr runtime without modifying it."""
from pathlib import Path
import argparse,hashlib,json,os,platform,subprocess,tarfile,time,traceback

HERE=Path(__file__).resolve().parent
ARCHIVES={'1.12.2':'443a5529ae2596eb3cf8f19760893b96beba4f12a4decbf6bea645db4d2187f6','1.12.4':'17d0b436315de24ccc638d0f817508e6ef14cefb066b4c2e60a2fcb0465f38d3'}
SOURCES={'1.12.2':'849d6cc30ca8b121c4492e6d7248ae51956dc6fa6c940a8f89ebcfc33afb931f','1.12.4':'7b69135fa732e11c63a259236a360a15cb72ae1eb94df7958369c71fea5f87a3'}
ACTION='Use a permitted Linux x86-64 R runtime, all original dependencies, and the exact selected 1.12.2 CRAN or 1.12.4 official development archive. Install only where allowed; otherwise skip dependent analysis. Fix the named cause and retry NEW paths. Never replace an existing runtime fingerprint to suppress refusal. This adoption runs no model and establishes no author-binary authenticity.'
def pin(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for b in iter(lambda:f.read(8*1024*1024),b''):h.update(b)
    return h.hexdigest()
def save(path,data):
    with Path(path).open('x',encoding='utf-8',newline='\n') as f:json.dump(data,f,indent=2);f.write('\n')
def entries(roots):
    found={}
    for root in roots:
        for directory,dirs,files in os.walk(root,followlinks=False):
            for name in dirs+files:
                p=Path(directory)/name
                if p.is_symlink():found[str(p)]=('link',os.readlink(p),pin(p) if p.is_file() else '-')
                elif p.is_file():found[str(p)]=('file','-',pin(p))
    return found
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--edition',choices=ARCHIVES,required=True);p.add_argument('--archive',type=Path,required=True);p.add_argument('--rscript',type=Path,required=True);p.add_argument('--r-library',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        if platform.system()!='Linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Run this original profile setup inside permitted Linux/WSL')
        target=a.out.absolute();target.mkdir(parents=True,exist_ok=False);out=target;(out/'WARNINGS.md').write_text('# Warnings and required actions\n\n'+ACTION+'\n',encoding='utf-8')
        binary=a.rscript.resolve(strict=True);library=a.r_library.resolve(strict=True);prefix=binary.parent.parent
        for path in [binary,library,prefix,out]:
            if any(x in str(path) for x in ['\t','\n','\r','\\']):raise ValueError('Runtime paths containing control characters or backslashes are unsupported')
        for root in [prefix,library]:
            if out==root or out.is_relative_to(root) or root.is_relative_to(out):raise ValueError('Use a NEW adoption directory outside runtime roots')
        if not os.access(binary,os.X_OK):raise FileNotFoundError('Configured Rscript is not executable')
        if pin(a.archive)!=ARCHIVES[a.edition]:raise ValueError('Exact selected original archive differs')
        with tarfile.open(a.archive,'r:gz') as tar:
            members=[x for x in tar.getmembers() if x.isfile() and x.name.endswith('/R/LDpred2.R')]
            if len(members)!=1:raise ValueError('Exactly one original R/LDpred2.R required')
            raw=tar.extractfile(members[0]).read()
        if hashlib.sha256(raw).hexdigest()!=SOURCES[a.edition]:raise ValueError('Selected original R source differs')
        (out/'original-LDpred2.R').write_bytes(raw)
        command=[str(binary),'--vanilla',str(HERE/'Probe.R'),str(library),a.edition,str(out)];start=time.monotonic()
        env=os.environ.copy();env.update(OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',R_LIBS_USER=str(out/'unused-user-library'))
        with (out/'probe.stdout').open('xb') as so,(out/'probe.stderr').open('xb') as se:result=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
        save(out/'PROCESS.json',dict(command=command,exit_code=result.returncode,seconds=round(time.monotonic()-start,2)))
        if result.returncode:raise RuntimeError('Original package readiness failed; inspect preserved probe.stderr')
        roots=[prefix] if library.is_relative_to(prefix) else [prefix,library]
        files=entries(roots)
        with (out/'runtime-files.tsv').open('x',encoding='utf-8',newline='\n') as f:
            f.write('kind\tpath\ttarget\tsha256\n')
            for name,(kind,target,sha) in sorted(files.items()):
                if any(c in name+target for c in ['\t','\n','\r']):raise ValueError('Unrepresentable runtime filename/link')
                f.write('\t'.join([kind,name,target,sha])+'\n')
        if entries(roots)!=files:raise ValueError('Runtime changed during adoption; preserve this attempt and retry NEW paths')
        values=dict(profile='original-bigsnpr-ldpred2-runtime-v1',edition=a.edition,rscript=str(binary),r_library=str(library),r_root=str(prefix),original_archive_sha256=ARCHIVES[a.edition],original_r_sha256=SOURCES[a.edition],manifest_sha256=pin(out/'runtime-files.tsv'))
        with (out/'runtime.properties').open('x',encoding='utf-8',newline='\n') as f:
            for k,v in sorted(values.items()):f.write(k+'='+v+'\n')
        save(out/'READY.json',dict(status='READY_ADOPTED_ORIGINAL_BIGSNPR_LDPRED2_RUNTIME_NOT_ANALYSIS',edition=a.edition,files=len(files),original_function_bodies_checked=3,runtime_not_modified=True,author_build_authenticity_established=False))
        print('READY_ADOPTED_ORIGINAL_BIGSNPR_LDPRED2_RUNTIME_NOT_ANALYSIS',a.edition,len(files),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,FileNotFoundError) else 2;label='SKIP3_ORIGINAL_LDPRED2_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_LDPRED2_RUNTIME_NOT_ADOPTED'
        if out is not None and out.exists() and not (out/'READY.json').exists():
            try:save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
            except Exception:pass
        print(label,repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
