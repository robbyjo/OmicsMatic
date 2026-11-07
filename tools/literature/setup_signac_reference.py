# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run permitted isolated original-package reference setup and verify the preexisting R library is unchanged."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-install',action='store_true');args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/signac-runtime').resolve()) and not out.exists();out.mkdir()
    base=ROOT/'build/omics-sctransform-r';before=inventory(base);save(out/'BASE-BEFORE.json',before)
    env=dict(os.environ)
    for key in list(env):
        if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_PROFILE_USER','R_ENVIRON_USER']:env.pop(key,None)
    env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1')
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/install_signac_reference.R'),str(out/'R-lib'),str(out/'report'),str(base),'allow' if args.allow_install else 'forbid']
    try:
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:
            process=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1800,check=False)
        save(out/'process.json',dict(command=command,exit_code=process.returncode,expected_scope='private-reference-package-setup-only'))
        after=inventory(base);save(out/'BASE-AFTER.json',after);assert after==before,'Preexisting scientific runtime library changed'
        result=json.loads((out/'report/RESULT.json').read_bytes());save(out/'RESULT.json',dict(status=result['status'],base_library_unchanged=True,base_files=len(before),setup_result=result,script=pin(ROOT/'tools/literature/install_signac_reference.R'),user_analysis_admitted=False))
        print(json.dumps(dict(status=result['status'],base_library_unchanged=True,base_files=len(before))))
        return process.returncode
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='SETUP_OR_VERIFICATION_FAILED_NO_ANALYSIS_ADMITTED',error=str(error),required_action='Read original setup logs. Preserve partials and the existing library, correct the named cause and retry into NEW paths. Skip dependent Signac execution until a permitted working runtime exists.'));raise

if __name__=='__main__':raise SystemExit(main())
