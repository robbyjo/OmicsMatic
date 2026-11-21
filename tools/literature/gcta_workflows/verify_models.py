#!/usr/bin/env python3
"""Read saved GCTA model evidence; never execute Java, research scripts or a model."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,zipfile

def pin(path):
    with path.open('rb') as stream:return {'bytes':path.stat().st_size,'sha256':hashlib.file_digest(stream,'sha256').hexdigest()}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True,help='NEW receipt directory')
    parser.add_argument('--jar',type=Path,help='Optional exact captured producer; not a rebuilt substitute')
    parser.add_argument('--restore',action='store_true',help='Restore byte-exact logical evidence files into the NEW receipt directory')
    args=parser.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    root=Path(__file__).resolve().parents[3];q=root/'docs/literature/gcta-model-development'
    try:
        profile=json.loads((q/'PROFILE.json').read_bytes())
        for name,wanted in profile['source_inputs'].items():
            assert not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts
            assert pin(root/name)==wanted,'Source differs; use the exact saved owner: '+name
        assert pin(q/'evidence.zip')==profile['archive'] and pin(q/'INVENTORY.json')==profile['inventory']
        paths=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in paths.values()}
        with zipfile.ZipFile(q/'evidence.zip') as archive:
            names=archive.namelist();assert len(names)==len(set(names))==len(unique)
            assert set(names)=={'blobs/'+sha for sha in unique}
            for sha in unique:
                assert re.fullmatch('[0-9a-f]{64}',sha);raw=archive.read('blobs/'+sha);assert hashlib.sha256(raw).hexdigest()==sha
            for name,wanted in paths.items():
                path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts
                raw=archive.read('blobs/'+wanted['sha256']);assert len(raw)==wanted['bytes']
                if args.restore:
                    target=out/'evidence'/name;target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as stream:stream.write(raw)
            def read(name):return json.loads(archive.read('blobs/'+paths[name]['sha256']))
            math=read('current/independent-AI-covariance-2/RESULT.json');assert len(math['results'])==6
            assert all(r['within_AI_point_rounding_bound'] and r['AI_covariance_spectral_difference']<=r['previous_point_rounding_perturbation_spectral_bound']+r['source_covariance_printing_Frobenius_bound'] for r in math['results'])
            comparisons=read('current/original-export-comparisons-1/RESULT.json')['comparisons'];assert len(comparisons)==90 and all(r['equal'] for r in comparisons)
            extra=read('current/remaining-original-comparisons-1/COMPLETED.json')['results'];assert sum(len(v['comparisons']) for v in extra.values())==14
            assert all(r['equal'] for v in extra.values() for r in v['comparisons']) and all(r['exit_code']==0 for v in extra.values() for r in v['runs'])
            baseline=read('current/summary-red-baseline-2/COMPLETED.json');assert baseline['exit_code']==1 and baseline['expected_baseline_failure']
            build=read('current/normal-gradle-2/COMPLETED.json');assert build['exit_code']==0 and sum(r['tests'] for r in build['reports'].values())==18
            assert all(r['errors']==r['failures']==r['skipped']==0 for r in build['reports'].values())
            packaged=read('current/packaged-qualification-1/COMPLETED.json');assert packaged['payloads']['old_payloads_unchanged']==23540 and packaged['payloads']['changed_classes']==['org/jlinalg/cli/GctaWorkflowAudit.class']
            assert set(packaged['results'])=={'windows21','windows25','linux21'}
            for r in packaged['results'].values():assert r['packaged_tests']==18 and r['exit_code']==0 and len(r['existing_capture_checks'])==56 and all(c['exit_code']==0 for c in r['existing_capture_checks'])
            process=read('current/actual-process-controls-2/COMPLETED.json');assert process['owned_timeout_and_output_workers_observed_terminal']
            for host in ['windows21','windows25','linux21']:
                prior=read('current/inverse-models-2/'+host+'/PROGRESS.json');assert len(prior)==17 and all(r['exit_code']==2 for r in prior) and all(r['cause_seen'] for r in prior[:16])
                assert process['prior_17_controls'][host]['actual_original_exit_code']==0 and process['prior_17_controls'][host]['wrapper_exit_code']==2
                assert len(process['new_4_controls'][host])==4 and all(r['exit_code']==r['expected_exit'] and r['cause_seen'] for r in process['new_4_controls'][host])
                for control in ['original-timeout','original-output-cap']:
                    terminal=read('current/actual-process-controls-2/'+host+'/'+control+'.TERMINAL-PROBE.json');assert terminal['owned_process_observed_terminal']
        producer='SKIP3: exact private producer not supplied; only saved evidence checked'
        if args.jar:
            assert pin(args.jar)==profile['normal_producer'],'Wrong producer; preserve old hashes'
            producer='PASS_EXACT_CAPTURED_PRODUCER_INTEGRITY_ONLY'
        result=dict(status='PASS_SAVED_GCTA_MODEL_EVIDENCE_ONLY',source_files=len(profile['source_inputs']),logical_files=len(paths),unique_blobs=len(unique),saved_comparisons=104,saved_AI_covariance_checks=6,saved_packaged_tests_per_JVM=18,saved_JVMs=3,saved_existing_capture_checks=168,saved_inverse_process_controls_per_JVM=21,saved_terminal_probes=6,producer_integrity=producer,evidence_restored=args.restore,model_executed=False,independent_refit=False,new_catalog_credit=0,whole_goal_complete=False)
        (out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
    except Exception as error:
        (out/'ERROR.txt').write_text('FAIL2: NO COMPLETE VERIFICATION\n'+repr(error)+'\nPreserve all evidence; use the exact owner and producer, never refresh old hashes.\n',encoding='utf-8');raise

if __name__=='__main__':main()
