# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original SNPRelate KING-robust matrices/filtering/family contexts in a private R library; no adapter credit."""
import argparse
import json
import os
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE=ROOT/'build/literature-review/snprelate-runtime'
ACTION=('No project SNPRelate analysis is admitted by this reference collector. Require complete native checks before adapter/catalog credit. '
        'If the named exact private R runtime/packages cannot be supplied or installation is forbidden, skip dependent SNPRelate analysis. '
        'Use setup_snprelate_reference.py with explicit download/install flags only where permitted. Never substitute standalone KING, '
        'PLINK2-hosted KING or a different package version, infer paper defaults, repair IDs or silently remove samples/variants.')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path)
    p.add_argument('--setup',type=Path,default=BASE/'private-original-setup-4')
    p.add_argument('--bfile',type=Path,default=ROOT/'build/literature-review/plink-relatedness-runtime/king-own-controls-1/own-inputs/cohort')
    p.add_argument('--transport',choices=['both','bed-import','row-index'],default='both')
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_SNPRELATE_KING_REFERENCE',project_analysis_admitted=False,
        new_catalog_credit=False,whole_goal_complete=False,required_action=ACTION);code=2
    protected={}
    try:
        setup=a.setup.resolve()
        if not (setup/'COMPLETED.json').is_file():
            raise FileNotFoundError('Required exact private SNPRelate runtime setup is absent')
        metadata=json.loads((setup/'COMPLETED.json').read_bytes())
        assert metadata['status']=='PASS_PRIVATE_ORIGINAL_SNPRELATE146_GDSFMT_RUNTIME_SETUP_ONLY'
        library=Path(metadata['private_library']);rscript=Path(metadata['rscript']['path'])
        assert pin(rscript)=={k:metadata['rscript'][k] for k in ['bytes','sha256']}
        before={name:pin(library/name) for name in metadata['installed_files']};assert before==metadata['installed_files']
        protected.update({str(library/name):value for name,value in before.items()})
        protected[str(rscript)]=pin(rscript)
        prefix=a.bfile.resolve();inputs={e:prefix.with_suffix('.'+e) for e in ['bed','bim','fam']}
        original={e:pin(path) for e,path in inputs.items()};assert sum(v['bytes'] for v in original.values())<=100000000
        protected.update({str(path):original[e] for e,path in inputs.items()})
        n=len(inputs['fam'].read_bytes().splitlines());m=len(inputs['bim'].read_bytes().splitlines())
        assert 2<=n<=500 and 1<=m<=20000
        captured=out/'inputs';captured.mkdir()
        for e,path in inputs.items():
            target=captured/('cohort.'+e);shutil.copyfile(path,target);assert pin(target)==original[e];protected[str(target)]=original[e]
        data=inputs['bed'].read_bytes();width=(n+3)//4
        assert data[:3]==b'\x6c\x1b\x01' and len(data)==3+m*width,'Require exact complete variant-major BED'
        transport=out/'transport-calls.tsv'
        with transport.open('x',encoding='utf8',newline='\n') as output:
            output.write('input_variant_row\t'+'\t'.join(str(i+1) for i in range(n))+'\n')
            for v in range(m):
                calls=[{0:2,1:3,2:1,3:0}[(data[3+v*width+i//4]>>(2*(i%4)))&3] for i in range(n)]
                output.write(str(v+1)+'\t'+'\t'.join(map(str,calls))+'\n')
        worker=ROOT/'src/main/resources/org/jlinalg/omics/snprelate/king_reference.R';worker_pin=pin(worker)
        protected[str(worker)]=worker_pin
        shutil.copyfile(worker,out/'worker.R');results=out/'original-results';results.mkdir();temp=out/'tmp';temp.mkdir()
        command=[str(rscript),'--vanilla',str(out/'worker.R'),str(library),str(captured/'cohort'),str(results),str(transport),a.transport]
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_LIBS_USER=str(library),R_LIBS_SITE='',TMPDIR=str(temp),R_DEFAULT_PACKAGES='base')
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:
            run=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
        save(out/'process.json',dict(command=command,exit_code=run.returncode))
        assert run.returncode==0,('Original R reference failed; preserve source stdout/stderr',run.returncode)
        assert b'ORIGINAL_REFERENCE_ONLY_PROJECT_ANALYSIS_NOT_ADMITTED' in (out/'stdout').read_bytes()
        assert all(pin(path)==original[e] for e,path in inputs.items()) and all(pin(captured/('cohort.'+e))==original[e] for e in inputs)
        assert {name:pin(library/name) for name in before}==before and pin(worker)==worker_pin
        state.update(status='PASS_ORIGINAL_SNPRELATE146_KING_MATRIX_REFERENCE_COLLECTION_ONLY',original_analysis_executed=True,
            input_pins=original,worker_pin=worker_pin,producer=pin(Path(__file__)),private_package_pins=before,
            transport=a.transport,cases=sorted(str(f.parent.relative_to(results)) for f in results.rglob('original-case-options.tsv')),samples=n,variants=m,
            complete_original_files={f.relative_to(out).as_posix():pin(f) for f in sorted(results.rglob('*')) if f.is_file()})
        code=0
    except FileNotFoundError as error:
        state.update(status='SKIP3_ORIGINAL_SNPRELATE_DEPENDENCY_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        preserved={}
        for name,value in protected.items():
            try:preserved[name]=pin(Path(name))==value
            except OSError:preserved[name]=False
        state['preservation_checks']=dict(files=len(preserved),all_unchanged=all(preserved.values()),
                                         changed_or_missing=[name for name,unchanged in preserved.items() if not unchanged])
        if not all(preserved.values()):
            state.update(status='FAIL2_SNPRELATE_PROTECTED_INPUT_OR_RUNTIME_CHANGED',prior_cause=state.get('cause'),
                         cause='Protected original input, source or runtime changed during reference collection; admit no result');code=2
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8')
    print(state['status'],state.get('cause','')[:1800],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original input/package preservation checks')
    raise SystemExit(main())
