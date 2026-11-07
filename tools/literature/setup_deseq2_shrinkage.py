# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install optional original shrinkage packages into a new private reference library."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, BASES, RSCRIPT, environment, pin, save


def inventory(roots):
    return {str(root): {p.relative_to(root).as_posix(): pin(p) for p in sorted(root.rglob('*')) if p.is_file()} for root in roots}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-install',action='store_true');a=parser.parse_args()
    out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/deseq2-runtime') and not out.exists();out.mkdir()
    roots=[ROOT/'build/literature-review/deseq2-runtime/reference-setup-1/R-lib',*BASES];before=inventory(roots);save(out/'BASES-BEFORE.json',before)
    result={'status':'SKIP3_OPTIONAL_SHRINKAGE_UNAVAILABLE','analysis_admitted':False,'required_action':'Read retained installer logs. Supply the named optional packages in a NEW private library only where permitted. Otherwise skip dependent shrinkage requests. Preserve existing libraries and all attempts.'}
    try:
        if not a.allow_install:raise RuntimeError('Installation/download forbidden; dependent analysis skipped')
        command=[str(RSCRIPT),'--vanilla',str(ROOT/'tools/literature/install_deseq2_shrinkage.R'),str(out/'R-lib'),str(out),*map(str,roots)]
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,env=environment(),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=900)
        save(out/'process.json',dict(command=command,exit_code=p.returncode));report=json.loads((out/'R-RESULT.json').read_bytes());result.update(report)
        if p.returncode!=0:raise RuntimeError(report.get('detail','Optional packages did not load'))
        result['download_pins']={p.name:pin(p) for p in (out/'downloads').glob('*') if p.is_file()}
    except Exception as error:result.update(status='SKIP3_OPTIONAL_SHRINKAGE_UNAVAILABLE',detail=str(error))
    finally:
        after=inventory(roots);save(out/'BASES-AFTER.json',after);result['preexisting_libraries_unchanged']=after==before;result['preexisting_files']=sum(map(len,before.values()))
        if before!=after:result.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED')
        save(out/'RESULT.json',result);(out/'WARNINGS.md').write_text('# '+result['status']+'\n\n'+result.get('detail','')+'\n\nRequired action: '+result['required_action']+'\n\nNo user analysis was admitted.\n',encoding='utf-8')
    print(json.dumps(result));return 0 if result['status']=='INSTALLED_OPTIONAL_ORIGINAL_SHRINKAGE_REFERENCE_ONLY' else 3


if __name__=='__main__':raise SystemExit(main())
