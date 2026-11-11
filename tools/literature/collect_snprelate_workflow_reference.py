# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original SNPRelate LD pruning/GRM contracts before implementing their typed adapters."""
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
ACTION=('This collector admits no project SNPRelate analysis or catalog credit. If the exact private Windows R4.6.1/SNPRelate1.46.0 '
    'runtime cannot be supplied or installation is forbidden, skip dependent original analyses. Use setup_snprelate_reference.py with '
    'explicit permitted download/install flags, then retry into a new directory. Never substitute PLINK pruning/clumping, '
    'a different LD measure, GRM estimator, seed, version, chromosome set, or silently repair IDs/missing values.')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--setup',type=Path,default=BASE/'private-original-setup-4')
    p.add_argument('--bfile',type=Path,default=BASE/'own-edge-controls-3/own-inputs/mixed')
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_ORIGINAL_SNPRELATE_LD_GRM_REFERENCE',project_analysis_admitted=False,new_catalog_credit=False,
        whole_goal_complete=False,required_action=ACTION);protected={};code=2
    try:
        setup=a.setup.resolve();metadata=json.loads((setup/'COMPLETED.json').read_bytes())
        assert metadata['status']=='PASS_PRIVATE_ORIGINAL_SNPRELATE146_GDSFMT_RUNTIME_SETUP_ONLY'
        library=Path(metadata['private_library']);rscript=Path(metadata['rscript']['path'])
        assert pin(rscript)=={key:metadata['rscript'][key] for key in ['bytes','sha256']};protected[str(rscript)]=pin(rscript)
        for name,value in metadata['installed_files'].items():
            path=library/name;assert pin(path)==value;protected[str(path)]=value
        prefix=a.bfile.resolve();inputs={ext:prefix.with_suffix('.'+ext) for ext in ['bed','bim','fam']}
        original={ext:pin(path) for ext,path in inputs.items()};assert sum(v['bytes'] for v in original.values())<=10000000
        protected.update({str(path):original[ext] for ext,path in inputs.items()})
        n=len(inputs['fam'].read_bytes().splitlines());m=len(inputs['bim'].read_bytes().splitlines());assert 2<=n<=100 and 1<=m<=1000
        captured=out/'inputs';captured.mkdir()
        for ext,path in inputs.items():
            target=captured/('cohort.'+ext);shutil.copyfile(path,target);assert pin(target)==original[ext];protected[str(target)]=original[ext]
        data=inputs['bed'].read_bytes();width=(n+3)//4;assert data[:3]==b'\x6c\x1b\x01' and len(data)==3+width*m
        transport=out/'transport-calls.tsv'
        with transport.open('x',encoding='utf8',newline='\n') as stream:
            stream.write('input_variant_row\t'+'\t'.join(str(i+1) for i in range(n))+'\n')
            for v in range(m):
                calls=[{0:2,1:3,2:1,3:0}[(data[3+v*width+i//4]>>(2*(i%4)))&3] for i in range(n)]
                stream.write(str(v+1)+'\t'+'\t'.join(map(str,calls))+'\n')
        worker=ROOT/'src/main/resources/org/jlinalg/omics/snprelate/workflow_reference.R';protected[str(worker)]=pin(worker)
        shutil.copyfile(worker,out/'worker.R');results=out/'original-results';results.mkdir();temp=out/'tmp';temp.mkdir()
        command=[str(rscript),'--vanilla',str(out/'worker.R'),str(library),str(captured/'cohort'),str(results),str(transport)]
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_DEFAULT_PACKAGES='base',R_LIBS_USER=str(library),R_LIBS_SITE='',TMPDIR=str(temp))
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:
            result=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
        save(out/'process.json',dict(command=command,exit_code=result.returncode));assert result.returncode==0,('Preserve original error; no results admitted',result.returncode)
        assert b'ORIGINAL_LD_GRM_REFERENCE_ONLY_PROJECT_ANALYSIS_NOT_ADMITTED' in (out/'stdout').read_bytes()
        with (results/'original-case-dispositions.tsv').open(encoding='utf8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
        assert len(rows)==44 and len({r['case'] for r in rows})==44
        errors=[r['case'] for r in rows if r['status']=='ORIGINAL_ERROR']
        empty=[r['case'] for r in rows if r['status']=='ORIGINAL_SUCCESS' and int(r['selected_variants'])==0]
        state.update(status='PASS_ORIGINAL_SNPRELATE146_LD_GRM_REFERENCE_COLLECTION_ONLY',input_pins=original,
            original_analysis_executed=True,transport='explicit-row-index-with-original-ID-sidecars',samples=n,variants=m,cases=rows,
            original_source_errors=errors,original_empty_successes=empty,
            worker_pin=pin(worker),producer=pin(Path(__file__)),
            complete_original_files={f.relative_to(out).as_posix():pin(f) for f in sorted(results.rglob('*')) if f.is_file()})
        code=0
    except FileNotFoundError as error:
        state.update(status='SKIP3_ORIGINAL_SNPRELATE_LD_GRM_DEPENDENCY_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        changed=[]
        for name,value in protected.items():
            try:
                if pin(Path(name))!=value:changed.append(name)
            except OSError:changed.append(name)
        state['preservation_checks']=dict(files=len(protected),all_unchanged=not changed,changed_or_missing=changed)
        if changed:state.update(status='FAIL2_SNPRELATE_LD_GRM_PROTECTED_INPUT_OR_RUNTIME_CHANGED',cause='Protected source/input/runtime changed; admit no analysis');code=2
    save(out/'COMPLETED.json',state)
    warnings=('# '+state['status']+'\n\n'+state.get('cause','')+'\n\n'
        '**PROJECT ANALYSIS NOT ADMITTED.** This is an original-software reference collection.\n\n'
        'Original source errors: '+str(len(state.get('original_source_errors',[])))+'. '
        'Original successful calls with empty SNP populations: '+str(len(state.get('original_empty_successes',[])))+'.\n\n'
        'An upstream success with no SNPs is not a usable LD/GRM result. Preserve its raw outputs; skip dependent analysis. '
        'For real inputs, review the declared QC/sample/variant population and supply an informative permitted dataset before retrying; '
        'do not change filtering, fill missing genotypes, switch estimators or suppress errors to force a result. '
        'These synthetic negative controls intentionally retain the empty/error outcomes.\n\nREQUIRED ACTION: '+ACTION+'\n')
    (out/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n')
    print(state['status'],'source_errors='+str(len(state.get('original_source_errors',[]))),
          'empty_source_successes='+str(len(state.get('original_empty_successes',[]))),state.get('cause','')[:2400],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-input/runtime preservation or complete reference checks')
    raise SystemExit(main())
