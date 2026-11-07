# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install one exact original SoupX edition into a NEW private library; preserve all existing libraries."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    source=base/('source-intake-1/1.6.2' if a.version=='1.6.2' else 'source-recovery-1/1.3.0');receipt=json.loads((source/'SOURCE.json').read_bytes());assert receipt['version']==a.version and all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items())
    prior=ROOT/'build/literature-review/monocle-runtime/cicero-reference-setup-1/STARTED.json';libraries=[Path(p) for p in json.loads(prior.read_bytes())['libraries']];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    script=ROOT/'tools/literature/install_soupx_reference.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,source/'SOURCE.json',prior]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    library=out/'R-lib';library.mkdir();action='Preserve this attempt. Inspect installer.stdout/stderr and R-RESULT.json. If permitted, provision the exact named dependency in a NEW private library and retry NEW paths; never alter shared libraries, patch original functions or silently substitute SoupX releases. Skip dependent analysis until an exact selected profile and complete typed controls pass. Namespace/tiny reference health alone admits no user analysis.'
    status=dict(status='SKIP3_SOUPX_REFERENCE_UNAVAILABLE',version=a.version,analysis_admitted=False,required_action=action,source_pins=pins)
    try:
        if not a.allow_install:raise RuntimeError('Installation not authorized; dependent analysis skipped')
        command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source/'source'),a.version,str(library),*map(str,libraries)];env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,[library,*libraries])),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out));save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libraries)),source_pins=pins,original_source=receipt))
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Original installation/health failed; inspect retained logs'
        report=json.loads((out/'R-RESULT.json').read_bytes());assert report['status']=='loaded-source-bound-original-SoupX-reference-only' and report['version']==a.version;status.update(status='installed-source-bound-original-SoupX-reference-only',profile=report,source_tree=source.relative_to(ROOT).as_posix())
    except Exception as error:status['detail']=str(error)
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);status.update(preexisting_libraries_unchanged=before==after,preexisting_files=sum(map(len,before.values())))
        assert all(pin(ROOT/name)==value for name,value in pins.items()) and all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items())
        if before!=after:status.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect before/after inventories')
        save(out/'COMPLETED.json',status);(out/'WARNINGS.md').write_text('# '+status['status']+'\n\n'+status.get('detail','')+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n')
    print(json.dumps({k:v for k,v in status.items() if k not in ['source_pins','profile']}));return 0 if status['status']=='installed-source-bound-original-SoupX-reference-only' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/library checks')
    raise SystemExit(main())
