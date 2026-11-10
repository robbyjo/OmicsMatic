# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate actual root dispatch on all four SNPRelate workflows and preserve predecessor help behavior."""
import argparse
import json
import os
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_snprelate_tools import compare,rows
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--root21',type=Path,required=True);p.add_argument('--root25',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_ROOT_DISPATCH_VALIDATION',new_catalog_credit=False,whole_goal_complete=False);records=[]
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);artifacts={};builds={};source_pins={};producer=pin(Path(__file__))
        for compiler,folder in [(21,a.root21.resolve()),(25,a.root25.resolve())]:
            done=json.loads((folder/'COMPLETED.json').read_bytes());assert done['status']=='PASS_SNPRELATE_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED'
            artifact=ROOT/done['artifact']['path'];assert pin(artifact)=={k:done['artifact'][k] for k in ['bytes','sha256']}
            assert all(pin(ROOT/name)==value for name,value in done['dependencies'].items()) and all(pin(ROOT/name)==value for name,value in done['source_pins'].items())
            artifacts[compiler]=artifact;builds[compiler]=done;source_pins.update(done['source_pins'])
        def command(compiler,host,arguments):
            jar=artifacts[compiler]
            if host=='linux':return ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1g','-cp',linux(jar)+':'+linux(canonical),'org.jlinalg.cli.JLinAlgCli',*arguments]
            tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
            return [str(tools/'java.exe'),'-Xmx1g','-cp',str(jar)+';'+str(canonical),'org.jlinalg.cli.JLinAlgCli',*arguments]
        def run(label,cmd,expected=0,needle=None):
            r=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode))
            assert r.returncode==expected,(label,r.returncode,expected,r.stdout[-2500:],r.stderr[-3500:])
            if needle:assert needle.encode() in r.stdout+r.stderr,(label,needle)
            records.append(dict(case=label,expected=expected,actual=r.returncode));return r
        summary=json.loads((BASE/'adapter-census-1-SUMMARY.json').read_bytes());old=summary['records']
        names=['mixed-snp-first-ld-corr-last','mixed-snp-first-grm-IndivBeta','mixed-row-index-sample-first-king-default-families']
        targets=[]
        for name in names:
            row=next(r for r in old if r['case']==name)
            folder=next(BASE/('adapter-census-1-part'+str(i)) for i in range(4) if (BASE/('adapter-census-1-part'+str(i))/name).exists())
            targets.append(dict(name=name,request=folder/(name+'.request.json'),reference=ROOT/row['original_reference'],mode=row['mode']))
        name='rank-snp-first-randomized-FALSE-DSPEVX-3-iter1'
        folder=BASE/'adapter-PCA-census-1-part0';assert (folder/name/'COMPLETED.json').exists()
        targets.append(dict(name=name,request=folder/(name+'.request.json'),reference=BASE/'original-PCA-rank-1/original-results/snp-first/randomized-FALSE-DSPEVX-3-iter1',mode='pca'))
        for item in targets:
            request=json.loads(item['request'].read_bytes());bundle=out/item['name'];request['out']=str(bundle);file=out/(item['name']+'.request.json');save(file,request)
            run('run-'+item['mode'],command(21,'windows',['snprelate-tools','--request',str(file)]))
            done=json.loads((bundle/'COMPLETED.json').read_bytes());assert done['result_admitted']
            if item['mode']=='pca':
                for name in ['original-selected-variants.tsv','original-eigenvalues.tsv','original-eigenvectors.tsv','original-PCA-summary.tsv']:
                    assert rows(item['reference']/name)==rows(bundle/'worker'/name),(item['mode'],name)
            else:compare(item['reference'],bundle/'worker',item['mode'])
            for compiler in [21,25]:
                for host in ['windows','linux']:
                    path=linux(bundle) if host=='linux' else str(bundle)
                    run('verify-'+item['mode']+'-'+str(compiler)+'-'+host,command(compiler,host,['snprelate-tools','--verify',path]))
        for compiler in [21,25]:
            for host in ['windows','linux']:
                for tool in ['snprelate-tools','diamond-tools','cellchat-tools']:
                    run('help-'+tool+'-'+str(compiler)+'-'+host,command(compiler,host,[tool,'--help']),0,'SNPRelate1.46.0' if tool=='snprelate-tools' else None)
        # A root-routed dependency skip must remain explicit, rather than returning
        # a successful empty result or switching to another PCA implementation.
        request=json.loads(targets[0]['request'].read_bytes());request.update(out=str(out/'root-runtime-skip'),runtime_profile=str(out/'absent-profile.json'))
        file=out/'root-runtime-skip.request.json';save(file,request)
        run('root-runtime-skip',command(21,'windows',['snprelate-tools','--request',str(file)]),3,'SKIP3_EXACT_SNPRELATE_RUNTIME_UNAVAILABLE')
        done=json.loads((out/'root-runtime-skip/COMPLETED.json').read_bytes());assert not done['result_admitted'] and not done['original_analysis_executed']
        assert len(records)==33 and pin(canonical)==before and all(pin(ROOT/name)==value for name,value in source_pins.items()) and pin(Path(__file__))==producer
        state.update(status='PASS33_SNPRELATE_ROOT_COMMANDS4_ORIGINAL_ANALYSES16_SAVED_CHECKS12_HELPS1_SKIP',records=records,original_upstream_analyses=4,
            root_artifacts={str(c):dict(path=f.relative_to(ROOT).as_posix(),**pin(f)) for c,f in artifacts.items()},
            source_pins=source_pins,producer=producer,canonical_executable_unchanged=before)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:4000],flush=True)
    return 0 if state['status'].startswith('PASS33_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable root dispatch/original output/predecessor checks')
    raise SystemExit(main())
