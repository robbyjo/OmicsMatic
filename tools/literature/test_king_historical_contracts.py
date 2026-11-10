# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test JDK25 historical execution/replay, resealed scientific mutations and exact-version skip/failure protocols."""
import argparse
import json
import shutil
import subprocess
import sys
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def reseal(bundle):
    path=bundle/'COMPLETED.json';done=json.loads(path.read_bytes())
    done['files']={p.relative_to(bundle).as_posix():dict(kind='file',pin=pin(p)) for p in sorted(bundle.rglob('*')) if p.is_file() and p!=path}
    save(path,done)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--jar',type=Path,required=True);p.add_argument('--reference',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();jar=a.jar.resolve();reference=a.reference.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[];before=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');artifact=pin(jar)
    java='C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'
    state=dict(status='FAIL2_HISTORICAL_KING_JDK25_AND_CONTRACTS',new_catalog_credit=False,whole_goal_complete=False)
    try:
        def invoke(name,arguments,expected,host='windows',contains=None):
            convert=linux if host=='linux' else lambda p:str(p.resolve())
            launcher=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if host=='linux' else [java]
            command=[*launcher,'-Xmx1g','-jar',convert(jar),*arguments]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==expected,(name,r.returncode,r.stderr[-2500:])
            if contains:assert contains.encode() in r.stderr,(name,r.stderr[-2500:])
            records.append(dict(name=name,expected=expected,actual=r.returncode,host=host));print(name,r.returncode,flush=True);return r
        completed=json.loads((reference/'COMPLETED.json').read_bytes());assert completed['status'].startswith('PASS_') and len(completed['records'])==36
        for row in completed['records']:
            bundle=reference/row['bundle'];invoke('replay25-'+bundle.stem,['--verify',str(bundle)],row['actual'])
        fresh=[('2.2.7-windows-own-kinship','windows',0),('2.2.7-windows-own-duplicate','windows',0),
               ('2.2.7-linux-own-kinship','linux',0),('2.0-linux-own-kinship','linux',0),
               ('2.0-linux-official-kinship','linux',2),('2.0-ubuntu-boundary-8193','linux',2)]
        for stem,host,expected in fresh:
            convert=linux if host=='linux' else lambda p:str(p.resolve())
            spec=json.loads((reference/(stem+'.request.json')).read_bytes());bundle=out/('fresh-'+stem+'.bundle');spec['out']=convert(bundle)
            path=out/('fresh-'+stem+'.request.json');save(path,spec)
            invoke('fresh-'+stem,['--request',convert(path)],expected,host)
            done=json.loads((bundle/'COMPLETED.json').read_bytes());assert done['result_admitted']==(expected==0)
            invoke('fresh-replay25-'+stem,['--verify',str(bundle)],expected)
        template=reference/'2.2.7-windows-own-kinship.bundle'
        def mutate(name,operation):
            bundle=out/(name+'.bundle');shutil.copytree(template,bundle);operation(bundle);reseal(bundle)
            invoke(name,['--verify',str(bundle)],2)
        def table_change(bundle,member,column,value):
            p=bundle/member;rows=p.read_text().splitlines();row=rows[1].split('\t');row[column]=value(row[column]);rows[1]='\t'.join(row);p.write_text('\n'.join(rows)+'\n',encoding='utf8')
        mutate('mutate-kinship',lambda b:table_change(b,'results/original.kin',8,lambda x:str(float(x)+.1)))
        def omit(b):
            p=b/'results/original.kin0';rows=p.read_bytes().splitlines(keepends=True);p.write_bytes(b''.join(rows[1:]))
        mutate('mutate-pair-population',omit)
        mutate('mutate-native-count',lambda b:table_change(b,'native-pair-counts.tsv',4,lambda x:str(int(x)+1)))
        def change_version(b):
            p=b/'source-request.json';x=json.loads(p.read_bytes());x['version']='2.0';save(p,x)
        mutate('mutate-requested-version',change_version)
        def command(b):
            p=b/'execution.json';x=json.loads(p.read_bytes());x['steps'][0]['command'][3]='--duplicate';save(p,x);save(b/'results/analysis.process.json',x['steps'][0])
        mutate('mutate-scientific-command',command)
        def warning(b):
            p=b/'WARNINGS.md';p.write_text('# completed-original-historical-king-with-warnings\n',encoding='utf8')
        mutate('mutate-required-warning',warning)
        def scientific(b):
            p=b/'scientific-audit.json';x=json.loads(p.read_bytes());x['variants']+=1;save(p,x)
        mutate('mutate-scientific-audit',scientific)
        def promote():
            source=reference/'2.0-linux-boundary-8193.bundle';bundle=out/'promote-source-discrepancy.bundle';shutil.copytree(source,bundle)
            first,target=bundle/'unadmitted-worker',bundle/'results';assert first.resolve().is_relative_to(out) and target.resolve().is_relative_to(out);first.rename(target)
            request=json.loads((bundle/'source-request.json').read_bytes());profile=json.loads((bundle/'runtime-profile.json').read_bytes())
            steps=[json.loads((target/(name+'.process.json')).read_bytes()) for name in ['dependencies-before','analysis','dependencies-after']]
            original={request['bfile']+'.'+e:pin(bundle/'inputs'/('cohort.'+e)) for e in ['bed','bim','fam']}
            save(bundle/'execution.json',dict(platform='linux',bundle_at_execution=request['out'],executable_at_execution=profile['executable'],original_inputs=original,steps=steps))
            audit=json.loads((bundle/'scientific-audit.json').read_bytes());save(bundle/'provenance.json',audit)
            for name in ['COMPLETED.json','analysis-status.json']:
                p=bundle/name;x=json.loads(p.read_bytes());x['result_admitted']=True;x['status']='completed-original-historical-king-with-warnings';save(p,x)
            reseal(bundle);invoke('promote-source-discrepancy',['--verify',str(bundle)],2,contains='SCIENTIFIC DISCREPANCY')
        promote()
        base=json.loads((reference/'2.2.7-windows-own-kinship.request.json').read_bytes())
        def request_case(name,change,expected):
            spec=json.loads(json.dumps(base));bundle=out/(name+'.bundle');spec['out']=str(bundle);change(spec)
            path=out/(name+'.request.json');save(path,spec);r=invoke(name,['--request',str(path)],expected)
            assert b'REQUIRED ACTION:' in r.stderr
            if bundle.exists():
                done=json.loads((bundle/'COMPLETED.json').read_bytes());assert not done['result_admitted'] and not (bundle/'results').exists()
        request_case('missing-profile',lambda x:x.update(runtime_profile=str(out/'absent.json')),3)
        request_case('wrong-host-profile',lambda x:x.update(version='2.0',runtime_profile=str(BASE/'king-historical-profile-20-linux-1/runtime-profile.json')),3)
        request_case('requested-runtime-version-mismatch',lambda x:x.update(version='2.0'),2)
        request_case('native-operation-capacity',lambda x:x['limits'].update(pair_variant_operations=1),2)
        request_case('native-working-capacity',lambda x:x['limits'].update(working_bytes=1),2)
        request_case('unknown-bit64-option',lambda x:x.update(bit64=True),2)
        request_case('unrequested-selection-mode',lambda x:x.update(mode='unrelated',degree=2),2)
        export=out/'exported-runtime-setup';invoke('export-runtime-helper',['--export-runtime-setup',str(export)],0)
        helper=export/'setup_king_historical_runtime.py'
        assert helper.read_bytes()==(ROOT/'src/main/resources/org/jlinalg/omics/king-historical/setup_king_historical_runtime.py').read_bytes()
        for name,profile in [('setup-no-download','2.2.7-windows'),('setup-wrong-host','2.0-linux')]:
            command=[sys.executable,str(helper),'--research-use','--profile',profile,'--out',str(out/name)]
            r=subprocess.run(command,capture_output=True,timeout=30);assert r.returncode==3 and b'REQUIRED ACTION:' in r.stderr
            records.append(dict(name=name,actual=3,expected=3,host='windows'))
        assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==before
        state.update(status='PASS_HISTORICAL_KING_JDK25_REPLAY_EXECUTION_MUTATIONS_AND_SKIP_CONTRACTS',records=records,artifact=artifact,
                     source_discrepancy_promotion_rejected_by_native_scientific_audit=True,canonical_executable_unchanged=before,producer=pin(Path(__file__)))
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],len(records),state.get('error','')[:2800],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-discrepancy, identity and skip/admission checks')
    raise SystemExit(main())
