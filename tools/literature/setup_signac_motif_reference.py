# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install permitted original motif tools into NEW paths and prove frozen R libraries were untouched."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import pin
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='private-original-motif-reference-setup-in-progress'))
    sources={name:pin(ROOT/name) for name in ['tools/literature/setup_signac_motif_reference.py','tools/literature/install_signac_motif_reference.R']};save(out/'SOURCE-PINS.json',sources)
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    old=BASE/'reference-setup-1/R-lib';base=ROOT/'build/omics-sctransform-r';before={name:inventory(root) for name,root in [('private',old),('base',base)]};save(out/'LIBRARIES-BEFORE.json',before);print('Frozen runtime inventories captured; starting isolated motif setup',flush=True)
    env=dict(os.environ)
    for key in list(env):
        if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
    env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/install_signac_motif_reference.R'),str(out/'R-lib'),str(out/'report'),str(old),str(base),'allow' if args.allow_install else 'forbid'];process=None
    try:
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:
            process=subprocess.Popen(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
            save(out/'RUNNING.json',dict(pid=process.pid,command=argv));code=process.wait(timeout=1800)
        save(out/'process.json',dict(command=argv,exit_code=code,owned_pid=process.pid));after={name:inventory(root) for name,root in [('private',old),('base',base)]};save(out/'LIBRARIES-AFTER.json',after);assert before==after,'Frozen runtime content changed'
        assert all(pin(ROOT/n)==v for n,v in sources.items());report=json.loads((out/'report/RESULT.json').read_bytes());assert code in [0,3]
        result=dict(status=report['status'],source_pins=sources,setup_result=report,frozen_libraries_unchanged=True,base_files=len(before['base']),private_files=len(before['private']),download_pins=inventory(out/'report/downloads') if (out/'report/downloads').is_dir() else {},new_private_file_pins=inventory(out/'R-lib') if (out/'R-lib').is_dir() else {},user_analysis_admitted=False)
        save(out/('COMPLETED.json' if code==0 else 'SKIPPED.json'),result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],frozen_libraries_unchanged=True,user_analysis_admitted=False)),flush=True);return code
    except BaseException as error:
        if process is not None and process.poll() is None:
            # Only this Popen-owned installation tree is targeted.
            subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=False,timeout=20);process.wait(timeout=20)
        save(out/'FAILURE.json',dict(status='FAILED_MOTIF_REFERENCE_SETUP_NO_ANALYSIS_ADMITTED',error=str(error),required_action='Preserve all attempts and skip dependent motif analysis; inspect setup logs, correct named runtime/network/library causes and retry only into NEW paths where permitted.'));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen-runtime content checks')
    raise SystemExit(main())
