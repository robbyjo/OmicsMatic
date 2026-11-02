#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real original/native bridge controls and independent original posterior primitives."""
import argparse
import concurrent.futures
import json
import math
from pathlib import Path
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mashr_preparation as bridge
from mashr_original_runtime import ProcessFailure,canonical,census,environment,pin,run_owned,write

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'
JAVA21=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')
JAVA25=Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe').resolve()


def table(path):return [[float(x) for x in line.split('\t')] for line in path.read_text(encoding='utf-8').splitlines()]


def change(root,filename,key,value):
    file=root/filename;lines=file.read_text(encoding='utf-8').splitlines();found=False
    for i,line in enumerate(lines):
        if line.startswith(key+'\t'):lines[i]=key+'\t'+str(value);found=True
    if not found:lines.append(key+'\t'+str(value))
    file.write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')


def mutate_numeric(path,amount=.25):
    rows=path.read_text(encoding='utf-8').splitlines();values=rows[0].split('\t');values[0]=str(float(values[0])+amount);rows[0]='\t'.join(values);path.write_text('\n'.join(rows)+'\n',encoding='utf-8',newline='\n')


def reseal(root):
    manifest=json.loads((root/'manifest.json').read_text());manifest['files']={n:v for n,v in census(root,536_870_912,20000).items() if n not in ['manifest.json','COMPLETED.json']};(root/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n');marker=json.loads((root/'COMPLETED.json').read_text());marker['manifest']=pin(root/'manifest.json');(root/'COMPLETED.json').write_text(json.dumps(marker,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    try:
        rt=bridge.runtime(args.runtime);rt.update(policy=dict(input_bytes=16_777_216,bundle_bytes=536_870_912,entries=20000,process=dict(bridge.DEFAULT_PROCESS)),process_limits=dict(bridge.DEFAULT_PROCESS),scratch_root=out,native_limits=None)
        app=bridge.native(None,JAVA25);native_proof=json.loads(bridge.NATIVE_PROOF.read_text());assert all(pin(ROOT/n)==v for n,v in native_proof['source_pins'].items())
        sources=[Path(__file__),bridge.SCRIPT,bridge.LOCK,ROOT/'tools/literature/mashr_original_runtime.py',ROOT/'tools/literature/mashr_preparation.py',ROOT/'tools/literature/mashr-preparation-oracle.R']
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
        write(out/'runtime-before.json',dict(Rhome=rt['base'],private=rt['installed']))
        template=BASE/'original-preparation-development-1/input-1';original=BASE/'original-workflow-controls-5/canonical_pca_ed-fixed-Rcpp/outputs'
        cases=[('canonical',0,None),('pca-ed',0,None),('alpha-one',0,None),('supplied-residual',0,None),('simple-null',0,None),('native-em-correlation',0,None),('singleton',0,None),('numeric-identities',0,None),('large-SE-target',0,None),('ed-limit',2,'ED_TERMINATION_UNPROVEN'),('work-limit',2,'work/storage plan exceeds'),('insufficient-strong',2,'enough real strong rows'),('zero-SE',2,'SE are nonpositive')]
        def execute(row):
            name,expected,detail=row;folder=out/name;folder.mkdir();inputs=folder/'input';shutil.copytree(template,inputs);change(inputs,'preparation.tsv','covariance_mode','canonical' if name=='canonical' else 'canonical_pca_ed')
            java=JAVA21 if name=='pca-ed' else JAVA25
            if name=='alpha-one':change(inputs,'settings.tsv','alpha',1)
            if name=='supplied-residual':
                change(inputs,'preparation.tsv','residual_initial','supplied');(inputs/'residual.tsv').write_text('1\t.25\t-.1\n.25\t1\t.15\n-.1\t.15\t1\n',encoding='utf-8',newline='\n')
            if name=='simple-null':change(inputs,'preparation.tsv','residual_initial','simple_null')
            if name=='native-em-correlation':change(inputs,'settings.tsv','residual','em_correlation')
            if name=='ed-limit':change(inputs,'preparation.tsv','ed_max_iterations',2)
            if name=='work-limit':change(inputs,'preparation.tsv','max_work',1)
            if name in ['singleton','insufficient-strong']:
                rows=(inputs/'populations.tsv').read_text().splitlines();flags=[line.split('\t') for line in rows[1:]]
                if name=='insufficient-strong':
                    seen=False
                    for fields in flags:
                        if fields[2]=='1':fields[2]='0' if seen else '1';seen=True
                    (inputs/'populations.tsv').write_text(rows[0]+'\n'+'\n'.join('\t'.join(fields) for fields in flags)+'\n',encoding='utf-8',newline='\n')
                else:
                    chosen=[i for i,f in enumerate(flags) if f[2]=='1' or i in [0,len(flags)-1]]
                    for file in ['effects.tsv','standard-errors.tsv']:
                        lines=(inputs/file).read_text().splitlines();selected=[lines[i+1].split('\t') for i in chosen]
                        if file=='effects.tsv':
                            for source,values in zip(chosen,selected):
                                if source==0:values[1:]=['10','8','-9']
                                if source==len(flags)-1:values[1:]=['7','5','-6']
                        (inputs/file).write_text(lines[0]+'\n'+'\n'.join('\t'.join(f) for f in selected)+'\n',encoding='utf-8',newline='\n')
                    selected_flags=['\t'.join([flags[i][0],str(int(i==0)),flags[i][2],str(int(i==len(flags)-1))]) for i in chosen]
                    (inputs/'populations.tsv').write_text(rows[0]+'\n'+'\n'.join(selected_flags)+'\n',encoding='utf-8',newline='\n')
            if name=='numeric-identities':
                for file in ['effects.tsv','standard-errors.tsv','populations.tsv']:
                    lines=(inputs/file).read_text().splitlines();columns=lines[0].split('\t')
                    if file!='populations.tsv':columns[1:]=['identity','equal_effects','simple_het_1']
                    converted=['\t'.join([f'{i+1:04d}',*line.split('\t')[1:]]) for i,line in enumerate(lines[1:])];(inputs/file).write_text('\t'.join(columns)+'\n'+'\n'.join(converted)+'\n',encoding='utf-8',newline='\n')
            if name in ['large-SE-target','zero-SE']:
                lines=(inputs/'standard-errors.tsv').read_text().splitlines();last=lines[-1].split('\t');last[1:]=['1e10']*3 if name=='large-SE-target' else ['0','1','1'];lines[-1]='\t'.join(last);(inputs/'standard-errors.tsv').write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
            result=folder/'result';command=[sys.executable,'-I','-B',ROOT/'tools/literature/mashr_preparation.py','--runtime',args.runtime.resolve(),'--java',java,'--input',inputs,'--out',result,'--scratch-root',out]
            try:process=run_owned(command,folder,'controller',environment(rt['rhome'],rt['home'],folder/'controller-scratch'),rt['monitor'])
            except ProcessFailure as failure:process=failure.result
            assert process['exit_code']==expected,(name,process['exit_code'])
            record=dict(name=name,expected_exit_code=expected,process=process)
            if expected:
                assert not (result/'COMPLETED.json').exists() and (result/'FAILURE.json').is_file();warning=(result/'WARNINGS.md').read_text(encoding='utf-8');assert 'FAIL2' in warning and 'Required action' in warning and detail in warning
                if name=='ed-limit':assert (result/'preparation/original/covariances/original-ed-candidates.rds').is_file()
            else:
                assert (result/'COMPLETED.json').is_file();execution=result/'native-analysis/execution';oracle=folder/'oracle';oracle.mkdir();shutil.copyfile(ROOT/'tools/literature/mashr-preparation-oracle.R',oracle/'reference.R')
                run_owned([rt['rhome']/'bin/Rscript.exe','--vanilla',oracle/'reference.R',rt['home']/'library',execution,oracle/'expected'],oracle,'original',environment(rt['rhome'],rt['home'],oracle/'scratch'),rt['monitor'])
                stats=dict(count=0,maximum_absolute_error=0.,maximum_scaled_error=0.)
                def near(a,b):
                    assert len(a)==len(b)
                    for left,right in zip(a,b):
                        assert len(left)==len(right)
                        for x,y in zip(left,right):
                            error=abs(x-y);assert math.isfinite(x) and math.isfinite(y) and error<=1e-8+2e-8*abs(x),(name,x,y)
                            stats['count']+=1;stats['maximum_absolute_error']=max(stats['maximum_absolute_error'],error);stats['maximum_scaled_error']=max(stats['maximum_scaled_error'],error/max(1,abs(x)))
                mapping={'mean':'mean','sd':'sd','negative':'negative','zero':'zero','lfsr':'lfsr','covariance':'covariance-column-major','weights':'weights','component':'component-loglik','likelihood':'loglik'}
                for source,destination in mapping.items():near(table(oracle/'expected'/('original-'+source+'.tsv')),table(execution/'posterior'/(destination+'.tsv')))
                near(table(oracle/'expected/original-training-component.tsv'),table(execution/'fit/final-training-component-loglik.tsv'))
                certificate=table(oracle/'expected/original-training-objective-gap.tsv')[0];assert certificate[2]<=3e-8,(name,certificate[2]);stats['training_independent_original_dual_gap']=certificate[2]
                if name=='pca-ed':
                    old=original/'covariances/fitting-names.tsv';names=(result/'preparation/original/prepared/basis/component-names.tsv').read_text().splitlines();assert names==old.read_text().splitlines()
                    for i in range(len(names)):near(table(original/f'covariances/fitting-{i}.tsv'),table(result/f'preparation/original/prepared/basis/component-{i+1}.tsv'))
                if name=='singleton':assert len((execution/'training-ids.tsv').read_text().splitlines())==1 and len((execution/'target-ids.tsv').read_text().splitlines())==1
                if name=='numeric-identities':assert (execution/'training-ids.tsv').read_text().splitlines()[0]=='0001'
                record['original_numeric_comparison']=stats
            write(folder/'CONTROL.json',record);print(name+': expected exit '+str(expected)+'; original/native evidence passed',flush=True);return record
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:controls=list(pool.map(execute,cases))
        bridge.checked_state(rt,app);write(out/'runtime-after.json',dict(Rhome=bridge.verify_rhome(rt['rhome'],rt['lock']),private=bridge.verify_home(rt['home'],rt['lock'])))
        assert json.loads((out/'runtime-before.json').read_text())==json.loads((out/'runtime-after.json').read_text()) and all(pin(ROOT/n)==v for n,v in bound.items())
        assert all(pin(ROOT/n)==v for n,v in native_proof['source_pins'].items())
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-original-preparation-and-native-inference-controls',controls=controls,source_pins=bound,protected_native_source_files_unchanged=len(native_proof['source_pins']),source_to_binary_reproduction_verified=False,whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:
        write(out/'FAILURE.json',dict(status='FAIL2_ORIGINAL_PREPARATION_CONTROLS',error=str(failure)));(out/'WARNINGS.md').write_text('# FAIL2: original/native bridge validation incomplete\n\n'+str(failure)+'\n\nRequired action: preserve this attempt, inspect all original and native logs, correct the named failure and retry into a NEW directory. No partial validation passed.\n',encoding='utf-8');raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_ORIGINAL_PREPARATION_CONTROLS: '+str(failure),file=sys.stderr);sys.exit(2)
