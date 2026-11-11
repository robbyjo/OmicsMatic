# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture full original single/multi SMR and both HEIDI modes with sparse/dense BESD preparation."""
import argparse,json,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_smr_cmake import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference-home',required=True,type=Path);p.add_argument('--inputs',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);reference=a.reference_home.resolve();record=json.loads((reference/'COMPLETED.json').read_bytes());assert record['status']=='verified-unchanged-original-SMR-build-no-analysis';program=ROOT/record['program']['path'];assert pin(program)=={k:record['program'][k] for k in ['bytes','sha256']};inputs=a.inputs.resolve();source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),reference/'COMPLETED.json',inputs/'GENERATED.json']};shutil.copytree(inputs,out/'inputs');reports=[];state=dict(status='FAIL2_ORIGINAL_SMR_PROBE_INCOMPLETE',version=record['version'],source_commit=record['source_commit'],program=record['program'],source_pins=source_pins,protocols=reports,analysis_admitted=False,adapter_validated=False,implementation_credit=False,whole_goal_complete=False)
    def run(identifier,args):
        home=out/identifier;home.mkdir();command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/env','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',linux(program),*args,'--out',linux(home/'original'),'--thread-num','1']
        with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(home/'process.json',dict(command=command,exit_code=r.returncode));row=dict(id=identifier,exit_code=r.returncode,files={p.relative_to(home).as_posix():pin(p) for p in home.rglob('*') if p.is_file()});reports.append(row);print(identifier,'exit',r.returncode,flush=True);assert r.returncode==0,'Inspect '+identifier+' original logs; no result admitted';return home/'original'
    try:
        prefixes={}
        for mode in ['sparse','dense']:
            prefixes[mode]=run('make-'+mode,['--qfile',linux(out/'inputs/qtl.qfile'),'--make-besd',*(['--make-besd-dense'] if mode=='dense' else [])]);assert all(prefixes[mode].with_suffix(s).exists() for s in ['.besd','.epi','.esi'])
        common=['--bfile',linux(out/'inputs/reference'),'--gwas-summary',linux(out/'inputs/gwas.ma'),'--peqtl-smr','5e-8','--peqtl-heidi','0.00157','--cis-wind','2000','--maf','0.01','--ld-upper-limit','0.9','--ld-lower-limit','0.05','--heidi-min-m','3','--heidi-max-m','20','--diff-freq','0.2','--diff-freq-prop','0.5']
        for identifier,mode,extra in [('single-new','sparse',['--heidi-mtd','1']),('single-old','sparse',['--heidi-mtd','0']),('single-off','sparse',['--heidi-off']),('single-dense','dense',['--heidi-mtd','1']),('multi','sparse',['--smr-multi','--smr-wind','500','--ld-multi-snp','0.1'])]:
            prefix=run(identifier,[*common,'--beqtl-summary',linux(prefixes[mode]),*extra]);assert any(p.suffix in ['.smr','.msmr'] and p.stat().st_size>0 for p in prefix.parent.iterdir()),'Original output table absent'
        state['status']='captured-full-original-SMR-workflows-not-adapter-validation'
    except Exception as error:state['detail']=str(error)
    finally:
        assert pin(program)=={k:record['program'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in source_pins.items());save(out/'COMPLETED.json',state)
    print(state['status'],state.get('detail',''));return 0 if state['status']=='captured-full-original-SMR-workflows-not-adapter-validation' else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original model/source checks')
    raise SystemExit(main())
