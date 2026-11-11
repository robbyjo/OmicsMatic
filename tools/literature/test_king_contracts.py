# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject resealed scientific/command mutations and exercise explicit KING skip/failure contracts."""
import argparse
import gzip
import json
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE


def reseal(bundle):
    path=bundle/'COMPLETED.json';done=json.loads(path.read_text())
    done['files']={f.relative_to(bundle).as_posix():dict(kind='file',pin=pin(f)) for f in sorted(bundle.rglob('*'))
                   if f.is_file() and f!=path}
    save(path,done)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--reference',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();jar=a.jar.resolve();reference=a.reference.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[]
    before=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');artifact=pin(jar)
    state=dict(status='FAIL2_KING_MUTATION_AND_FAILURE_CONTRACTS',new_catalog_credit=False,whole_goal_complete=False)
    java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
    try:
        def run(name,args,expected):
            command=[java,'-Xmx1g','-jar',str(jar),*args]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==expected,(name,r.returncode,r.stderr[-2000:])
            records.append(dict(name=name,expected=expected,actual=r.returncode));print(name,r.returncode,flush=True)
            return r
        def mutate(name,source,operation,seal=True):
            bundle=out/(name+'.bundle');shutil.copytree(reference/source,bundle)
            operation(bundle)
            if seal:reseal(bundle)
            run(name,['--verify',str(bundle)],2)
        def table_change(bundle,relative,column,change):
            path=bundle/relative;rows=path.read_text().splitlines();r=rows[1].split('\t');r[column]=change(r[column]);rows[1]='\t'.join(r)
            path.write_text('\n'.join(rows)+'\n',encoding='utf8')
        kin='windows-0-kinship-0.bundle';seg='linux-8-ibdseg-0.bundle';selection='windows-3-unrelated-2.bundle'
        mutate('raw-file-inventory',kin,lambda b:(b/'unexpected.txt').write_text('extra',encoding='utf8'),False)
        mutate('kinship-value-resealed',kin,lambda b:table_change(b,'results/original.kin',8,lambda x:str(float(x)+.1)))
        def omit_pair(b):
            p=b/'results/original.kin0';rows=p.read_bytes().splitlines(keepends=True);p.write_bytes(b''.join(rows[1:]))
        mutate('kinship-population-resealed',kin,omit_pair)
        mutate('native-count-resealed',kin,lambda b:table_change(b,'native-pair-counts.tsv',4,lambda x:str(int(x)+1)))
        def command_change(b):
            p=b/'execution.json';x=json.loads(p.read_text());x['steps'][0]['command'][3]='--duplicate';save(p,x)
            save(b/'results/analysis.process.json',x['steps'][0])
        mutate('scientific-command-resealed',kin,command_change)
        def input_binding(b):
            p=b/'execution.json';x=json.loads(p.read_text());next(iter(x['original_inputs'].values()))['sha256']='0'*64;save(p,x)
        mutate('input-binding-resealed',kin,input_binding)
        def unclosed(b):
            p=b/'execution.json';x=json.loads(p.read_text());x['steps'][0]['observed_owned_processes_terminal']=False;save(p,x);save(b/'results/analysis.process.json',x['steps'][0])
        mutate('process-not-terminal-resealed',kin,unclosed)
        def runtime_hash(b):
            p=b/'runtime-profile.json';x=json.loads(p.read_text());x['executable_pin']['sha256']='0'*64;save(p,x)
        mutate('runtime-profile-resealed',kin,runtime_hash)
        def coordinate_change(b):
            p=b/'results/original.segments.gz';rows=gzip.decompress(p.read_bytes()).decode().splitlines();r=rows[1].split('\t');r[6]=str(float(r[6])+.01);rows[1]='\t'.join(r);p.write_bytes(gzip.compress(('\n'.join(rows)+'\n').encode(),mtime=0))
        mutate('IBD-coordinate-resealed',seg,coordinate_change)
        mutate('IBD-proportion-resealed',seg,lambda b:table_change(b,'results/original.seg',4,lambda x:str(float(x)+.01)))
        def nonpartition(b):
            p=b/'results/originalunrelated.txt';p.write_bytes(p.read_bytes()+(b/'results/originalunrelated_toberemoved.txt').read_bytes().splitlines(keepends=True)[0])
        mutate('selection-partition-resealed',selection,nonpartition)
        mutate('material-warning-resealed',selection,lambda b:(b/'WARNINGS.md').write_text('# completed-original-king-with-warnings\n',encoding='utf8'))
        template=json.loads((reference/'windows-0-kinship-0.request.json').read_text())
        def request_case(name,operation,expected):
            request=json.loads(json.dumps(template));request['out']=str(out/(name+'.bundle'));operation(request)
            path=out/(name+'.request.json');save(path,request);r=run(name,['--request',str(path)],expected)
            assert b'REQUIRED ACTION:' in r.stderr
            bundle=Path(request['out'])
            if bundle.exists():
                done=json.loads((bundle/'COMPLETED.json').read_text());assert not done['result_admitted'] and not (bundle/'results').exists()
            return bundle
        request_case('missing-runtime-profile',lambda x:x.update(runtime_profile=str(out/'absent-profile.json')),3)
        profile=json.loads((BASE/'king-profile-windows-1/runtime-profile.json').read_text());profile['executable']=str(out/'missing-king.exe');missing=out/'missing-executable-profile.json';save(missing,profile)
        request_case('missing-runtime-executable',lambda x:x.update(runtime_profile=str(missing)),3)
        profile['executable']=str(out/'fake-king.exe');(out/'fake-king.exe').write_bytes(b'not the original binary');fake=out/'fake-executable-profile.json';save(fake,profile)
        request_case('wrong-runtime-executable',lambda x:x.update(runtime_profile=str(fake)),2)
        request_case('native-operation-capacity',lambda x:x['limits'].update(pair_variant_operations=1),2)
        request_case('native-working-capacity',lambda x:x['limits'].update(working_bytes=1),2)
        request_case('complete-input-capacity',lambda x:x['limits'].update(input_bytes=1),2)
        def variant_control(name,modify):
            prefix=out/name
            for ext in ['bed','bim','fam']:shutil.copyfile(Path(template['bfile']+'.'+ext),prefix.with_suffix('.'+ext))
            modify(prefix)
            request_case(name,lambda x:x.update(bfile=str(prefix)),2)
        def duplicate_id(prefix):
            p=prefix.with_suffix('.fam');r=p.read_bytes().splitlines(keepends=True);r[1]=r[0];p.write_bytes(b''.join(r))
        variant_control('duplicate-composite-ID',duplicate_id)
        def chrX(prefix):
            p=prefix.with_suffix('.bim');r=p.read_text().splitlines();f=r[0].split();f[0]='X';r[0]='\t'.join(f);p.write_text('\n'.join(r)+'\n',encoding='utf8')
        variant_control('unselected-X-chromosome',chrX)
        variant_control('truncated-BED',lambda prefix:prefix.with_suffix('.bed').write_bytes(prefix.with_suffix('.bed').read_bytes()[:-1]))
        setup=ROOT/'src/main/resources/org/jlinalg/omics/king/setup_king_runtime.py'
        import sys
        for name,flags in [('setup-no-download',['--research-use']),('setup-no-research',[])]:
            r=subprocess.run([sys.executable,str(setup),'--out',str(out/name),*flags],capture_output=True,timeout=30)
            assert r.returncode==3 and b'REQUIRED ACTION:' in r.stderr;records.append(dict(name=name,actual=3,expected=3))
        existing=out/'setup-existing';existing.mkdir();(existing/'sentinel').write_bytes(b'preserve')
        r=subprocess.run([sys.executable,str(setup),'--research-use','--out',str(existing)],capture_output=True,timeout=30)
        assert r.returncode==3 and [f.name for f in existing.iterdir()]==['sentinel'] and (existing/'sentinel').read_bytes()==b'preserve'
        records.append(dict(name='setup-existing-preserved',actual=3,expected=3))
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==before and pin(jar)==artifact
        state.update(status='PASS_KING_RESEALED_SCIENTIFIC_MUTATIONS_AND_SKIP_FAILURE_CONTRACTS',records=records,
                     artifact=artifact,canonical_executable_unchanged=before,producer=pin(Path(__file__)))
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2500],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable mutation, admission or preservation checks')
    raise SystemExit(main())
