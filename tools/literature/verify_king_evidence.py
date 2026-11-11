# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline full synthetic-bundle replay of the frozen KING2.3.2 adapter; never run original KING."""
import argparse
import hashlib
import json
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path, PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
HOME=ROOT/'docs/literature/king232-development'
BASE=ROOT/'build/literature-review/plink-relatedness-runtime'


def fingerprint(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def verify(out,java):
    proof=json.loads((HOME/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_FROZEN_KING232_ORIGINAL_EXECUTION_AND_NATIVE_TRANSPORT'
    assert proof['new_catalog_credit'] is False and proof['whole_goal_complete'] is False
    assert proof['original_programs_or_author_source_or_author_example_data_bundled'] is False
    for name,value in proof['source_pins'].items():assert fingerprint((ROOT/name).read_bytes())==value,('Changed frozen source',name)
    archive=HOME/'controls.zip';assert fingerprint(archive.read_bytes())==proof['archive']
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist())==len(set(z.namelist()))==len(proof['members']) and set(z.namelist())==set(proof['members'])
        total=0
        for item in z.infolist():
            name=item.filename;relative=PurePosixPath(name)
            assert not relative.is_absolute() and '..' not in relative.parts and not any(c in name for c in '\\:\0')
            assert name.startswith(('synthetic/','own-source/','summaries/')) and item.file_size<=10000000
            total+=item.file_size;assert total<=100000000
            raw=z.read(item);assert fingerprint(raw)==proof['members'][name]
            target=out/Path(*relative.parts);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    for name,value in proof['source_pins'].items():assert fingerprint((out/'own-source'/name).read_bytes())==value
    def summary(name):return json.loads((out/'summaries'/(name+'.json')).read_bytes())
    admitted=skips=failed=0
    for name in ['king-adapter-gates-4','king-adapter-gates-25-1']:
        gates=summary(name);assert gates['status']=='PASS_ORIGINAL_KING_ADAPTER_EXECUTION_AND_PORTABLE_REPLAY'
        assert len(gates['records'])==22
        for row in gates['records']:
            assert row['expected']==row['actual'] and row['portable_complete_verifications']==2
            admitted+=row['actual']==0;skips+=row['actual']==3;failed+=row['actual']==2
    assert (admitted,skips,failed)==(34,6,4)
    contracts=summary('king-contracts-1');assert len(contracts['records'])==24
    assert all(row['actual']==row['expected'] for row in contracts['records'])
    parity=summary('king-parity-1');assert len(parity['records'])==30 and all(r['complete_original_payload_equal'] for r in parity['records'])
    own=summary('king-own-controls-1');assert len(own['records'])==13 and all(r['exit_code']==0 for r in own['records'])
    assert summary('king-historical-intake-1')['analysis_performed'] is False
    jars=[]
    for name,value in proof['artifacts'].items():
        path=HOME/name;assert fingerprint(path.read_bytes())==value
        with zipfile.ZipFile(path) as z:
            assert all(n.startswith(('META-INF/','org/jlinalg/cli/','org/jlinalg/genetics/','org/jlinalg/omics/king/')) for n in z.namelist())
            assert all(n.endswith(('.class','.py')) or n.startswith('META-INF/') for n in z.namelist())
        jars.append(path)
    records=[]
    for bundle in proof['synthetic_bundles']:
        root=out/'synthetic'/bundle
        provenance=json.loads((root/'provenance.json').read_bytes())
        assert provenance['samples']==16 and provenance['variants']==8192 and provenance['complete_native_pairs']==120
        assert provenance['zero_smaller_heterozygosity_pairs_unassessable']==15
        for ext in ['bed','bim','fam']:assert (root/'inputs'/('cohort.'+ext)).read_bytes()==(out/'synthetic/own-inputs'/('cohort.'+ext)).read_bytes()
        for jar in jars:
            command=[str(java),'-Xmx512m','-jar',str(jar),'--verify',str(root)]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
            stem=bundle+'-'+jar.stem
            (out/(stem+'.stdout')).write_bytes(r.stdout);(out/(stem+'.stderr')).write_bytes(r.stderr)
            assert r.returncode==0,(bundle,jar.name,r.returncode,r.stderr[-2000:])
            records.append(dict(bundle=bundle,artifact=jar.name,exit_code=0))
    return dict(status='PASS_OFFLINE_FROZEN_KING232_COMPLETE_SYNTHETIC_REPLAY',records=records,
                original_KING_executed=False,new_catalog_credit=False,whole_goal_complete=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path);p.add_argument('--java',type=Path)
    a=p.parse_args();out=a.out.resolve() if a.out else None
    if out is None:
        i=1
        while (BASE/('king-offline-verify-'+str(i))).exists():i+=1
        out=BASE/('king-offline-verify-'+str(i))
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    java=a.java or shutil.which('java')
    state=dict(status='FAIL2_FROZEN_KING232_EVIDENCE',original_KING_executed=False,new_catalog_credit=False,whole_goal_complete=False)
    code=2
    try:
        if not java:
            state.update(status='SKIP3_JAVA_SAVED_VERIFICATION_UNAVAILABLE',required_action='Supply permitted Java17+ for saved verification; do not claim replay passed. No original KING runtime is required.')
            code=3
        else:
            state=verify(out,java);code=0
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    (out/'COMPLETED.json').write_text(json.dumps(state,indent=2)+'\n',encoding='utf8')
    print(state['status'],state.get('error','')[:2000],state.get('required_action',''),flush=True)
    return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen-evidence verification')
    raise SystemExit(main())
