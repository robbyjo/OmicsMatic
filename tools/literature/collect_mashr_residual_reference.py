#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fresh original E_V/vector-conditional references for native execution."""
import argparse,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
from collect_mashr_workflow_controls import ROOT,BASE,RHOME,RSCRIPT,pin,census,write


def main():
    if not __debug__:raise RuntimeError('Never disable reference assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--parent',required=True,type=Path);parser.add_argument('--runtime',required=True,type=Path);args=parser.parse_args()
    out,parent,runtime=args.out.resolve(),args.parent.resolve(),args.runtime.resolve()
    if not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists():raise ValueError('Use a NEW output under mashr-runtime')
    original=json.loads((parent/'ORIGINAL-CONTROLS.json').read_text());names=[p['name'] for p in original['protocols'] if p['exit_code']==0];assert len(names)==17
    assert pin(RSCRIPT)['sha256']=='d829bcf7e9fa1d7e3e828c565c3cdbb1ed416f551f4fa6fd4dfcdf231e33e5e8'
    out.mkdir(parents=True)
    try:
        (out/'scratch').mkdir();(out/'cases.txt').write_text('\n'.join(names)+'\n',encoding='utf-8',newline='\n')
        script=ROOT/'tools/literature/mashr-residual-reference.R';shutil.copyfile(script,out/'reference.R');shutil.copyfile(Path(__file__),out/'collector.py')
        before=dict(rhome=census(RHOME),library=census(runtime/'library'));write(out/'runtime-before.json',dict(files=before))
        command=[str(RSCRIPT),'--vanilla',str(out/'reference.R'),str(runtime/'library'),str(parent),str(out)]
        overrides=dict(LANG='C',LC_ALL='C',R_LIBS_USER=str(runtime/'library'),R_LIBS_SITE='',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out/'scratch'),TMP=str(out/'scratch'),TEMP=str(out/'scratch'))
        write(out/'command.json',dict(argv=command,environment_overrides=overrides))
        with (out/'process.stdout').open('xb') as stdout,(out/'process.stderr').open('xb') as stderr:process=subprocess.run(command,cwd=out,env=dict(os.environ,**overrides),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=600,check=False)
        write(out/'process.json',dict(exit_code=process.returncode));assert process.returncode==0,'Inspect complete original process evidence'
        after=dict(rhome=census(RHOME),library=census(runtime/'library'));write(out/'runtime-after.json',dict(files=after));assert before==after,'Reference runtime changed'
        write(out/'REFERENCE.json',dict(schema_version=1,status='captured-original-residual-and-singleton-primitives',source_workflows=17,parent_record=pin(parent/'ORIGINAL-CONTROLS.json'),
            parent_fixture=pin(ROOT/'src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),script]},
            native_comparison_pending=True,source_to_binary_rebuild_verified=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}))
        print('Captured original residual moments, singleton primitives and large-mean cancellation boundary. Native comparison remains required.')
        return 0
    except Exception as error:
        write(out/'FAILURE.json',dict(status='FAIL2_ORIGINAL_RESIDUAL_REFERENCE',message=str(error)))
        (out/'WARNINGS.md').write_text('# FAIL2: original reference incomplete\n\n'+str(error)+'\n\nRequired action: preserve this attempt, inspect source errors and repeat into a NEW directory after correction. No native or study result is admitted.\n',encoding='utf-8',newline='\n');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('MASHR REFERENCE FAIL2: '+str(failure),file=sys.stderr);sys.exit(2)
