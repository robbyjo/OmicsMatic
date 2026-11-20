#!/usr/bin/env python3
"""Verify saved GCTA development evidence only; never launch Java or a model."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,zipfile

def pin(path):
    with path.open('rb') as stream:
        return {'bytes':path.stat().st_size,'sha256':hashlib.file_digest(stream,'sha256').hexdigest()}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True,help='NEW directory for the verification receipt')
    parser.add_argument('--jar',type=Path,help='Optional exact captured normal producer; no substitution')
    args=parser.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    root=Path(__file__).resolve().parents[3];q=root/'docs/literature/gcta-workflow-development'
    try:
        profile=json.loads((q/'PROFILE.json').read_bytes())
        for name,wanted in profile['source_inputs'].items():
            assert not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts
            assert pin(root/name)==wanted,'Source differs: use the exact saved owner checkout: '+name
        assert pin(q/'evidence.zip')==profile['evidence_archive']
        assert pin(q/'INVENTORY.json')==profile['inventory']
        expected=json.loads((q/'INVENTORY.json').read_bytes())
        with zipfile.ZipFile(q/'evidence.zip') as archive:
            names=archive.namelist();assert len(names)==len(set(names))==len(expected)
            assert set(names)==set(expected)
            for name in names:
                path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts
                raw=archive.read(name);assert {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}==expected[name],name
            def read(name):return json.loads(archive.read(name))
            compositions=read('current/normal-workflow-runs-1/COMPLETED.json')
            for host in ['windows21','linux21']:
                rows=compositions['results'][host];assert len(rows)==33
                assert [sum(row['exit_code']==code for row in rows) for code in [0,2,3]]==[28,4,1]
                for row in rows:
                    if row['exit_code']!=0:continue
                    assert row['separate_JVM_verify_exit']==0
                    prefix='current/normal-workflow-runs-1/'+host+'/'+row['name']+'/'
                    seal=read(prefix+'capture-files.json');assert seal['authentication'] is False
                    actual={n[len(prefix):] for n in names if n.startswith(prefix)}-{'capture-files.json'}
                    assert actual==set(seal['files'])
                    for name,sha in seal['files'].items():assert expected[prefix+name]['sha256']==sha
            tests=read('current/packaged-handover-tests-1/COMPLETED.json')
            assert set(tests['results'])=={'windows21','windows25','linux21'}
            assert all(v['exit_code']==0 for v in tests['results'].values())
            build=read('current/normal-gradle-3/COMPLETED.json');assert build['exit_code']==0
            assert sum(v['tests'] for v in build['reports'].values())==16
            assert all(v['failures']==v['errors']==v['skipped']==0 for v in build['reports'].values())
            controls=read('current/handover-controls-1/COMPLETED.json')
            assert controls['parents_unchanged']
            assert sum(len(rows) for rows in controls['cross_host_verifications'].values())==56
            assert all(v['exit_code']==v['expected_exit'] for rows in controls['cross_host_verifications'].values() for v in rows)
            assert [v['exit_code'] for v in controls['replay_controls']]==[0,0,2,0,0]
            payloads=read('current/normal-workflow-runs-1/PRODUCER.json')
            assert payloads['prior_payloads_unchanged']==23530 and len(payloads['added_classes'])==11
        producer='SKIP3: exact private producer not supplied; saved receipts checked, no model rerun'
        if args.jar:
            assert pin(args.jar)==profile['normal_producer'],'Wrong producer: do not refresh old hashes'
            producer='PASS_EXACT_CAPTURED_PRODUCER_INTEGRITY_ONLY'
        result=dict(status='PASS_SAVED_DEVELOPMENT_CHECKPOINT_ONLY',source_files=len(profile['source_inputs']),
            archive_members=len(expected),saved_complete_requests=56,saved_expected_FAIL2=8,saved_expected_SKIP3=2,
            saved_packaged_tests_per_JVM=16,saved_packaged_JVMs=3,saved_cross_host_verifications=56,
            saved_actual_replays=2,producer_integrity=producer,model_reexecuted=False,
            qualification_complete=False,new_catalog_credit=0,whole_goal_complete=False)
        (out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(result))
    except Exception as error:
        (out/'ERROR.txt').write_text('FAIL2: NO COMPLETE VERIFICATION\n'+repr(error)+'\nPreserve evidence; use the exact owner and producer. Do not refresh saved hashes.\n',encoding='utf-8')
        raise

if __name__=='__main__':main()
