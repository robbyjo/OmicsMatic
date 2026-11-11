# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect exact/randomized original SNPRelate PCA references for the newly recovered P170 role."""
import argparse
import csv
import json
import os
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/snprelate-runtime'
ACTION=('No project PCA analysis or integration credit is admitted by reference collection. If the exact private SNPRelate1.46.0/R4.6.1 '
    'runtime is unavailable and cannot be installed where permitted, skip dependent PCA analysis and resolve the named prerequisite '
    'in a new private library. Do not substitute PLINK2 PCA, infer the unreported P170 solver/components/version, repair source values, '
    'change eigenvector signs or discard numerical failures to force agreement.')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--bfile',type=Path,default=BASE/'own-edge-controls-3/own-inputs/mixed');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_ORIGINAL_SNPRELATE_PCA_REFERENCE',project_analysis_admitted=False,new_catalog_credit=False,whole_goal_complete=False,required_action=ACTION);protected={};code=2
    try:
        setup=BASE/'private-original-setup-4';metadata=json.loads((setup/'COMPLETED.json').read_bytes())
        assert metadata['status']=='PASS_PRIVATE_ORIGINAL_SNPRELATE146_GDSFMT_RUNTIME_SETUP_ONLY'
        library=Path(metadata['private_library']);rscript=Path(metadata['rscript']['path'])
        assert pin(rscript)=={k:metadata['rscript'][k] for k in ['bytes','sha256']};protected[str(rscript)]=pin(rscript)
        for name,value in metadata['installed_files'].items():assert pin(library/name)==value;protected[str(library/name)]=value
        prefix=a.bfile.resolve();input_pins={e:pin(prefix.with_suffix('.'+e)) for e in ['bed','bim','fam']}
        n=len(prefix.with_suffix('.fam').read_bytes().splitlines());m=len(prefix.with_suffix('.bim').read_bytes().splitlines());assert 4<=n<=100 and 1<=m<=1000
        inputs=out/'inputs';inputs.mkdir()
        for ext,value in input_pins.items():
            source=prefix.with_suffix('.'+ext);target=inputs/('cohort.'+ext);shutil.copyfile(source,target);assert pin(target)==value;protected[str(source)]=value;protected[str(target)]=value
        bed=(inputs/'cohort.bed').read_bytes();width=(n+3)//4;assert bed[:3]==b'\x6c\x1b\x01' and len(bed)==3+width*m
        transport=out/'transport-calls.tsv'
        with transport.open('x',encoding='utf8',newline='\n') as stream:
            stream.write('input_variant_row\t'+'\t'.join(str(i+1) for i in range(n))+'\n')
            for v in range(m):stream.write(str(v+1)+'\t'+'\t'.join(str({0:2,1:3,2:1,3:0}[(bed[3+v*width+i//4]>>(2*(i%4)))&3]) for i in range(n))+'\n')
        worker=ROOT/'src/main/resources/org/jlinalg/omics/snprelate/pca_reference.R';protected[str(worker)]=pin(worker);shutil.copyfile(worker,out/'worker.R')
        results=out/'original-results';results.mkdir();temp=out/'tmp';temp.mkdir()
        command=[str(rscript),'--vanilla',str(out/'worker.R'),str(library),str(inputs/'cohort'),str(results),str(transport)]
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_LIBS_USER=str(library),R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp))
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:r=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,('Preserve original PCA stdout/stderr; no results admitted',r.returncode)
        assert b'ORIGINAL_PCA_REFERENCE_ONLY_NO_PROJECT_ANALYSIS_ADMITTED' in (out/'stdout').read_bytes()
        with (results/'original-case-dispositions.tsv').open(encoding='utf8',newline='') as stream:cases=list(csv.DictReader(stream,delimiter='\t'))
        assert len(cases)==20
        state.update(status='PASS_ORIGINAL_SNPRELATE146_PCA_REFERENCE_COLLECTION_ONLY',input_pins=input_pins,samples=n,variants=m,cases=cases,
            producer=pin(Path(__file__)),worker_pin=pin(worker),original_analysis_executed=True,
            complete_original_files={f.relative_to(out).as_posix():pin(f) for f in sorted(results.rglob('*')) if f.is_file()});code=0
    except FileNotFoundError as error:
        state.update(status='SKIP3_ORIGINAL_SNPRELATE_PCA_DEPENDENCY_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        changed=[]
        for name,value in protected.items():
            try:
                if pin(Path(name))!=value:changed.append(name)
            except OSError:changed.append(name)
        state['preservation_checks']=dict(files=len(protected),all_unchanged=not changed,changed_or_missing=changed)
        if changed:state.update(status='FAIL2_PCA_PROTECTED_SOURCE_INPUT_OR_RUNTIME_CHANGED',cause='Protected original files changed; admit no analysis');code=2
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('cause','')[:2200],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original PCA reference/input preservation checks')
    raise SystemExit(main())
