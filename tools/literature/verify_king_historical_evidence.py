# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline complete historical KING synthetic replay, including native rejection of promoted source discrepancies."""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path, PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/plink-relatedness-runtime'
HOME=ROOT/'docs/literature/king-historical-development'


def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')


def promote(source,target):
    """Deliberate negative control only; keep the original saved source-discrepant bundle unchanged."""
    shutil.copytree(source,target)
    first,last=target/'unadmitted-worker',target/'results'
    assert first.resolve().is_relative_to(target.resolve()) and last.resolve().is_relative_to(target.resolve())
    first.rename(last)
    request=json.loads((target/'source-request.json').read_bytes());profile=json.loads((target/'runtime-profile.json').read_bytes())
    steps=[json.loads((last/(name+'.process.json')).read_bytes()) for name in ['dependencies-before','analysis','dependencies-after']]
    original={request['bfile']+'.'+e:pin(target/'inputs'/('cohort.'+e)) for e in ['bed','bim','fam']}
    save(target/'execution.json',dict(platform='linux',bundle_at_execution=request['out'],executable_at_execution=profile['executable'],original_inputs=original,steps=steps))
    save(target/'provenance.json',json.loads((target/'scientific-audit.json').read_bytes()))
    for name in ['COMPLETED.json','analysis-status.json']:
        path=target/name;x=json.loads(path.read_bytes());x['status']='completed-original-historical-king-with-warnings';x['result_admitted']=True;save(path,x)
    path=target/'COMPLETED.json';done=json.loads(path.read_bytes())
    done['files']={p.relative_to(target).as_posix():dict(kind='file',pin=pin(p)) for p in sorted(target.rglob('*')) if p.is_file() and p!=path}
    save(path,done)


def verify(home,out,java):
    proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_FROZEN_HISTORICAL_KING_AND_EXACT_NATIVE_CUTOFF_EVIDENCE'
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete']
    assert proof['original_KING_programs_source_or_author_example_bundled'] is False
    for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value,('Changed source',name)
    archive=home/'controls.zip';assert pin(archive)==proof['archive']
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist())==len(set(z.namelist()))==len(proof['members']) and set(z.namelist())==set(proof['members'])
        total=0
        for item in z.infolist():
            name=item.filename;parts=PurePosixPath(name)
            assert not parts.is_absolute() and '..' not in parts.parts and not any(c in name for c in '\\:\0')
            assert name.startswith(('own-source/','summaries/','synthetic/','native-consumer/')) and item.file_size<=20000000
            total+=item.file_size;assert total<=200000000
            raw=z.read(item);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==proof['members'][name]
            target=out/Path(*parts.parts);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    for name,value in proof['source_pins'].items():assert pin(out/'own-source'/name)==value
    summary=lambda name:json.loads((out/'summaries'/(name+'.json')).read_bytes())
    gates=summary('historical-gates');assert len(gates['records'])==36 and gates['status'].startswith('PASS_')
    assert sum(r['actual']==0 for r in gates['records'])==26 and sum(r['actual']==2 for r in gates['records'])==10
    assert all(r['actual']==r['expected'] and r['portable_saved_checks']==2 for r in gates['records'])
    contracts=summary('historical-contracts');assert len(contracts['records'])==66 and contracts['status'].startswith('PASS_')
    assert contracts['source_discrepancy_promotion_rejected_by_native_scientific_audit'] and all(r['actual']==r['expected'] for r in contracts['records'])
    native=summary('native-contracts');assert (native['test_methods'],native['test_executions'],native['skipped'],native['original_reference_pair_checks'])==(19,57,0,2880)
    assert sum(r['tests'] for r in native['results'])==57
    runtime=summary('snprelate-runtime-setup-only');assert runtime['analysis_performed'] is False and runtime['status'].startswith('PASS_')
    artifacts={name:home/name for name in proof['artifacts']}
    for name,path in artifacts.items():
        assert pin(path)==proof['artifacts'][name]
        with zipfile.ZipFile(path) as z:
            assert all(n.startswith(('META-INF/','org/jlinalg/cli/','org/jlinalg/genetics/','org/jlinalg/omics/king-historical/')) for n in z.namelist())
            assert all(n.endswith(('.class','.py')) or n.startswith('META-INF/') for n in z.namelist())
    records=[]
    def run(label,command,expected,needle=None):
        r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
        (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr)
        assert r.returncode==expected,(label,r.returncode,r.stderr[-2500:])
        if needle:assert needle.encode() in r.stderr,(label,r.stderr[-2500:])
        records.append(dict(case=label,expected=expected,actual=r.returncode));return r
    assert len(proof['synthetic_bundles'])==28
    for row in proof['synthetic_bundles']:
        bundle=out/'synthetic/bundles'/row['bundle'];done=json.loads((bundle/'COMPLETED.json').read_bytes())
        assert done['result_admitted']==(row['expected']==0)
        for e in ['bed','bim','fam']:assert pin(bundle/'inputs'/('cohort.'+e))==row['input_pins'][e]
        if row['expected']:
            audit=json.loads((bundle/'scientific-audit.json').read_bytes());assert audit['scientific_discrepancies'] and not audit['all_scientific_fields_passed']
            assert audit['complete_native_pairs']==120
            # Full promoted replay reaches the scientific guard, checking all saved counts and source fields.
            altered=out/'deliberate-negative-promotions'/row['bundle'];altered.parent.mkdir(exist_ok=True)
            promote(bundle,altered)
        for name,jar in artifacts.items():
            if name.startswith('king-historical-'):
                checked=altered if row['expected'] else bundle
                run(row['bundle']+'-'+name,[str(java),'-Xmx512m','-jar',str(jar),'--verify',str(checked)],row['expected'],
                    'SCIENTIFIC DISCREPANCY' if row['expected'] else None)
    for name,jar in artifacts.items():
        if name.startswith('king-exact-threshold-'):
            cp=os.pathsep.join(map(str,[out/'native-consumer',jar]))
            r=run('external-'+name,[str(java),'-cp',cp,'KingThresholdExample'],0)
            assert r.stdout.strip()==b'221/2500 EQUAL strict=false inclusive=true'
    return dict(status='PASS_OFFLINE_HISTORICAL_KING_COMPLETE_SYNTHETIC_AND_EXACT_CUTOFF_REPLAY',records=records,
                original_KING_or_R_analysis_executed=False,promoted_source_discrepancies_rejected=True,new_catalog_credit=False,whole_goal_complete=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=HOME)
    p.add_argument('--out',type=Path);p.add_argument('--java',type=Path)
    a=p.parse_args();out=a.out.resolve() if a.out else None
    if out is None:
        i=1
        while (BASE/('historical-offline-'+str(i))).exists():i+=1
        out=BASE/('historical-offline-'+str(i))
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_HISTORICAL_KING_EVIDENCE',new_catalog_credit=False,whole_goal_complete=False);code=2
    try:
        java=a.java or shutil.which('java')
        if not java:
            state.update(status='SKIP3_JAVA_SAVED_VERIFICATION_UNAVAILABLE',required_action='Supply permitted Java17+ and retry new paths; do not claim replay passed. Original KING and R are not needed.');code=3
        else:state=verify(a.home.resolve(),out,java);code=0
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2400],state.get('required_action',''),flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full scientific rejection and frozen artifact checks')
    raise SystemExit(main())
