# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject resealed scientific/protocol mutations and test explicit missing-runtime/helper skips; preserve originals."""
import argparse
import csv
import hashlib
import json
import shutil
import subprocess
import sys
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')


def mutate_table(file,column,value,row=0):
    with file.open(encoding='utf8',newline='') as stream:rows=list(csv.reader(stream,delimiter='\t'))
    rows[row+1][rows[0].index(column)]=value
    with file.open('w',encoding='utf8',newline='') as stream:csv.writer(stream,delimiter='\t',lineterminator='\n').writerows(rows)


def reseal(bundle):
    done=json.loads((bundle/'COMPLETED.json').read_bytes())
    done['files']={f.relative_to(bundle).as_posix():pin(f) for f in sorted(bundle.rglob('*')) if f.is_file() and f.name!='COMPLETED.json'}
    save(bundle/'COMPLETED.json',done)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--build21',type=Path,required=True);p.add_argument('--build25',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_SEMANTIC_CONTRACTS',new_catalog_credit=False,whole_goal_complete=False);records=[]
    try:
        builds=[a.build21.resolve(),a.build25.resolve()];jars=[];source_pins={};producer=pin(Path(__file__))
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        for build in builds:
            metadata=json.loads((build/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_ISOLATED_SNPRELATE_ADAPTER_BUILD_ONLY'
            assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items());source_pins.update(metadata['source_pins'])
            jar=ROOT/metadata['artifact']['path'];assert pin(jar)=={key:metadata['artifact'][key] for key in ['bytes','sha256']};jars.append(jar)
        summary=json.loads((BASE/'adapter-census-1-SUMMARY.json').read_bytes())
        old=next(r for r in summary['records'] if r['mode']=='king-robust' and r['actual']==0)
        king=next((BASE/('adapter-census-1-part'+str(i))/old['case'] for i in range(4) if (BASE/('adapter-census-1-part'+str(i))/old['case']).exists()))
        pca=BASE/'adapter-PCA-census-1-part0/mixed-snp-first-exact-FALSE-DSPEVX-3'
        for source in [king,pca]:assert json.loads((source/'COMPLETED.json').read_bytes())['result_admitted']
        original_pins={str(f):pin(f) for source in [king,pca] for f in source.rglob('*') if f.is_file()}
        def run(label,command,expected,needle=None):
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
            (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr)
            save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==expected,(label,r.returncode,expected,r.stdout[-2000:],r.stderr[-3500:])
            if needle:assert needle.encode() in r.stderr,(label,needle,r.stderr[-3500:])
            records.append(dict(case=label,expected=expected,actual=r.returncode));return r
        mutations=[
            ('KING-coefficient',king,'worker/original-complete-matrix.tsv','value_hex','0x1p-2',0,'complete KING kinship coefficient'),
            ('genotype',king,'worker/original-genotypes.tsv','1','99',0,'complete original genotype transport changed'),
            ('sample-identity',king,'worker/original-sample-map.tsv','FID','changed-FID',0,'original FID/IID identity/order changed'),
            ('source-package',king,'worker/original-package-versions.tsv','version','0.0.0',0,'Exact original package versions differ'),
            ('QC-population',king,'worker/original-qc-population.tsv','input_variant_row','0',0,'Source input-row identity differs'),
            ('PCA-single-loading',pca,'worker/original-eigenvectors.tsv','value_hex','0x1p+0',0,'orthogonality bound'),
            ('PCA-uncomputed-placeholder',pca,'worker/original-eigenvalues.tsv','eigenvalue_hex','0x0p+0',3,'uncomputed DSPEVX placeholders differ')]
        folders=[]
        for name,source,member,column,value,row,needle in mutations:
            target=out/'mutated'/name;shutil.copytree(source,target);mutate_table(target/member,column,value,row);reseal(target);folders.append((name,target,needle))
        # This is deliberately fabricated admission, not a rerun of the source.
        # A coherent byte inventory must still reach and fail native empty-QC checks.
        target=out/'mutated/promoted-empty-QC';shutil.copytree(king,target)
        bed=target/'inputs/cohort.bed';raw=bed.read_bytes();bed.write_bytes(raw[:3]+b'\x55'*(len(raw)-3));reseal(target)
        folders.append(('promoted-empty-QC',target,'empty QC population admits no dependent analysis'))
        target=out/'mutated/typed-parameter-file';shutil.copytree(king,target)
        raw=(target/'parameters.tsv').read_text(encoding='utf8');assert '\ttrue\n' in raw
        (target/'parameters.tsv').write_text(raw.replace('\ttrue\n','\tfalse\n',1),encoding='utf8',newline='\n');reseal(target)
        folders.append(('typed-parameter-file',target,'Saved typed parameter file differs'))
        target=out/'mutated/process-record';shutil.copytree(king,target)
        file=target/'worker/analysis.process.json';process=json.loads(file.read_bytes());process['elapsed_milliseconds']+=1;save(file,process);reseal(target)
        folders.append(('process-record',target,'Original source process records disagree'))
        for name,bundle,needle in folders:
            for compiler,jar in zip([21,25],jars,strict=True):
                for host in ['windows','linux']:
                    label=name+'-'+str(compiler)+'-'+host
                    command=([str(JAVA),'-Xmx1g','-jar',str(jar),'--verify',str(bundle)] if host=='windows' else
                        ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1g','-jar',linux(jar),'--verify',linux(bundle)])
                    run(label,command,2,needle)
            print('PASS_REJECTED_RESEALED',name,flush=True)
        # Actual exported helper: missing setup causes a persistent, explicit skip;
        # no install/download/analysis is performed by this helper contract.
        helper=out/'exported-helper';run('export-helper',[str(JAVA),'-jar',str(jars[0]),'--export-runtime-helper',str(helper)],0)
        script=helper/'prepare_snprelate_runtime.py';script_pin=pin(script)
        run('helper-missing-setup',[sys.executable,str(script),'--setup',str(out/'absent-setup.json'),'--out',str(out/'helper-missing-setup')],3)
        done=json.loads((out/'helper-missing-setup/COMPLETED.json').read_bytes());assert not done['analysis_performed'] and not (out/'helper-missing-setup/runtime-profile.json').exists()
        assert 'REQUIRED ACTION' in (out/'helper-missing-setup/WARNINGS.md').read_text(encoding='utf8') and pin(script)==script_pin
        # Actual CLI missing profile, using a valid PCA request; this must not
        # become an empty/placeholder PCA result or invoke R.
        request=json.loads((pca/'source-request.json').read_bytes());request.update(runtime_profile=str(out/'absent-profile.json'),out=str(out/'runtime-skip'))
        file=out/'runtime-skip.request.json';save(file,request)
        run('runtime-missing-profile',[str(JAVA),'-jar',str(jars[0]),'--request',str(file)],3)
        done=json.loads((out/'runtime-skip/COMPLETED.json').read_bytes());assert not done['result_admitted'] and not done['original_analysis_executed']
        assert 'keep that analysis skipped' in (out/'runtime-skip/WARNINGS.md').read_text(encoding='utf8')
        # Existing evidence must be preserved, including an explicit refusal to overwrite it.
        run('existing-output-preserved',[str(JAVA),'-jar',str(jars[0]),'--request',str(BASE/'adapter-PCA-census-1-part0/mixed-snp-first-exact-FALSE-DSPEVX-3.request.json')],2,'Output exists')
        assert all(pin(Path(name))==value for name,value in original_pins.items()) and pin(canonical)==canonical_pin
        assert all(pin(ROOT/name)==value for name,value in source_pins.items()) and pin(Path(__file__))==producer
        state.update(status='PASS_SNPRELATE40_RESEALED_SCIENTIFIC_REFUSALS_AND4_HELPER_SKIP_PRESERVATION_CONTRACTS',records=records,
            resealed_semantic_mutations=10,mutation_checks=40,original_R_analysis_executed=False,original_bundles_unchanged=True,
            source_pins=source_pins,producer=producer,canonical_executable_unchanged=canonical_pin)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:4000],flush=True)
    return 0 if state['status'].startswith('PASS_SNPRELATE40_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable resealed scientific negative controls or original preservation')
    raise SystemExit(main())
