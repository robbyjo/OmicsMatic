"""Verify a saved development handover; does not qualify or rerun its workflow."""
from pathlib import Path
import argparse, hashlib, json, re, zipfile


def identity(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def verify(checkpoint):
    def local(name):
        p=Path(name)
        assert not p.is_absolute() and '..' not in p.parts and '\\' not in name
        return checkpoint/p
    manifest=json.loads(local('FILES.json').read_bytes())
    actual={p.relative_to(checkpoint).as_posix() for p in checkpoint.rglob('*') if p.is_file() and '__pycache__' not in p.parts}
    assert actual==set(manifest)|{'FILES.json'},'Checkpoint file closure differs'
    for name,expected in manifest.items():assert identity(local(name).read_bytes())==expected,name
    status=json.loads(local('STATUS.json').read_bytes())
    assert status['status']=='UNQUALIFIED_WORKFLOW_DEVELOPMENT_HANDOVER'
    assert status['new_family_credit']==status['new_reviews']==0
    assert not any(status[k] for k in ('whole_goal_complete','normal_project_build_run_for_draft','packaged_full_adapter_qualified','global_command_enabled','independent_direct_original_oracle_finished','full_private_machine_backup'))
    assert status['catalog']==dict(papers=250,scoped_families=151,native_families=27,upstream_families=124,reviewed_uses=1679,pending_uses=1043,positive_observations=2722)
    restore=json.loads(local('RESTORE.json').read_bytes())['files']
    assert len(restore)==status['draft_files']==14
    for name,entry in restore.items():
        assert entry['saved_as']=='drafts/'+name
        assert identity(local(entry['saved_as']).read_bytes())=={k:entry[k] for k in ('bytes','sha256')},name
    helpers=json.loads(local('HELPERS.json').read_bytes());assert len(helpers)==13
    for name,entry in helpers.items():assert identity(local('helpers/'+name).read_bytes())=={k:entry[k] for k in ('bytes','sha256')}
    assert identity(local('EVIDENCE.json').read_bytes())==status['evidence_index']
    assert identity(local('evidence.zip').read_bytes())==status['evidence_archive']
    index=json.loads(local('EVIDENCE.json').read_bytes())['files']
    with zipfile.ZipFile(local('evidence.zip')) as archive:
        names=archive.namelist();assert len(names)==len(set(names))
        assert set(names)=={'objects/'+e['sha256'] for e in index.values()}
        for info in archive.infolist():
            assert info.file_size<80_000_000
            raw=archive.read(info);assert hashlib.sha256(raw).hexdigest()==info.filename[8:]
        def raw(name):
            data=archive.read('objects/'+index[name]['sha256']);assert identity(data)==index[name],name
            return data
        def obj(name):return json.loads(raw(name))
        for name in index:assert archive.getinfo('objects/'+index[name]['sha256']).file_size==index[name]['bytes']
        prefix='private-evidence/workflow-compile-5/'
        compile_result=obj(prefix+'RESULT.json')
        assert compile_result==status['prototype_compile']
        assert compile_result['main_sources']==status['java_main_sources']==7
        assert not compile_result['normal_project_build_run'] and compile_result['new_family_credit']==0
        for label in ('main','tests-compile','tests-run'):assert obj(prefix+label+'.RESULT.json')['exit_code']==0
        output=raw(prefix+'tests-run.stdout').decode('utf-8')
        for count,label in ((13,'tests successful'),(0,'tests failed'),(0,'tests skipped')):
            assert re.search(r'\[\s*'+str(count)+r'\s+'+label+r'\s*\]',output)
        assert status['prototype_contract_tests']==13
        for name in restore:
            if name.endswith('.java'):
                assert raw(prefix+'source-context/'+Path(name).name)==local('drafts/'+name).read_bytes()
            elif name.startswith('tools/literature/squidpy_workflow/'):
                assert raw(prefix+'resources/org/jlinalg/omics/squidpy-workflow/'+Path(name).name)==local('drafts/'+name).read_bytes()
                other='drafts/src/main/resources/org/jlinalg/omics/squidpy-workflow/'+Path(name).name
                assert local(other).read_bytes()==local('drafts/'+name).read_bytes()
        probes={}
        for version in ('1.1.2','1.2.2','1.8.4'):
            p=obj('private-evidence/operations-probes-'+version+'-2/RESULT.json')
            completed=sum(x['status']=='ORIGINAL_OPERATION_RETURNED_AND_EXPORTED' for x in p['results'])
            assert completed==p['completed'] and len(p['results'])-completed==p['failed']
            probes[version]=dict(completed=p['completed'],failed=p['failed'])
        assert probes==status['source_operation_probes']=={'1.1.2':dict(completed=10,failed=2),'1.2.2':dict(completed=13,failed=1),'1.8.4':dict(completed=15,failed=0)}
        jobs=json.loads(local('JOBS-AT-SNAPSHOT.json').read_bytes())['jobs']
        count=mismatches=0
        for job in jobs:
            p=obj('private-evidence/workflow-cases-'+job['version']+'-1/PROGRESS.json')
            assert p==job['completed_cases'];count+=len(p)
            mismatches+=sum(x['exit_code']!=x['expected'] for x in p)
        assert count==status['workflow_cases_snapshotted']
        assert mismatches==status['workflow_case_expectation_mismatches']
        assert [j['version'] for j in jobs if not j['batch_finished']]==status['active_or_unreceipted_batches']
        catalog=obj('catalog/COMPLETED.json')
        assert catalog['current_manifest']['entries']==855
        for field in ('papers','scoped_families','reviewed_uses','pending_uses'):assert catalog['result'][field]==status['catalog'][field]
        final=json.loads(local('FINAL-STATUS.json').read_bytes())
        assert identity(local('FINAL-EVIDENCE.json').read_bytes())==final['final_evidence_index']
        assert identity(local('final-batch-evidence.zip').read_bytes())==final['additional_evidence_archive']
        final_index=json.loads(local('FINAL-EVIDENCE.json').read_bytes())['files']
        final_jobs=json.loads(local('FINAL-BATCH-RECEIPTS.json').read_bytes())
        assert final_jobs['all_three_launchers_ended']
        assert not final_jobs['process_check']['matching_owned_validation_processes']
        with zipfile.ZipFile(local('final-batch-evidence.zip')) as supplement:
            additional=supplement.namelist();assert len(additional)==len(set(additional))
            assert set(additional)=={'objects/'+e['sha256'] for e in final_index.values()}-set(names)
            for info in supplement.infolist():
                assert info.file_size<80_000_000
                assert hashlib.sha256(supplement.read(info)).hexdigest()==info.filename[8:]
            def final_raw(name):
                e=final_index[name];member='objects/'+e['sha256']
                data=(archive if member in names else supplement).read(member)
                assert identity(data)==e,name
                return data
            # Stream all logical members to check both size and fresh bytes.
            for name in final_index:final_raw(name)
            final_count=final_mismatches=0
            for job in final_jobs['jobs']:
                path='private-evidence/workflow-cases-'+job['version']+'-1/'
                progress=json.loads(final_raw(path+'PROGRESS.json'))
                assert progress==job['completed_cases'] and job['terminal']
                failures=[r for r in progress if r['exit_code']!=r['expected']]
                assert failures==job['unexpected_failures']
                assert len(progress)==(18 if job['version']=='1.1.2' else 12)
                assert [r['case'] for r in failures]==([] if job['version']=='1.1.2' else ['nanostring'])
                if failures:
                    failure=json.loads(final_raw(path+'nanostring/FAILURE.json'))
                    report=json.loads(final_raw(path+'nanostring/worker/report.json'))
                    assert failure==job['failure'] and failure['exit_code']==2 and not failure['results_admitted']
                    assert failure['cause']=='java.io.IOException: Scalar, vector or two-dimensional NPY required'
                    assert report['original_call_returned'] and not report['results_admitted']
                else:
                    assert json.loads(final_raw(path+'RESULT.json'))==job['result']
                    for case in ('knn-replay','moran-replay','ligrec-replay'):
                        assert next(r for r in progress if r['case']==case)['exit_code']==0
                final_count+=len(progress);final_mismatches+=len(failures)
            assert final_count==final['final_cases']==42
            assert final_mismatches==final['unexpected_failures']==2
            assert final_count-final_mismatches==final['matched_expected_outcome']==40
            assert final['active_owned_batch_processes']==0
            assert not any(final[k] for k in ('normal_project_build_run','full_adapter_qualified','new_family_credit','new_reviews','whole_goal_complete'))
    return dict(status='PASS_SAVED_UNQUALIFIED_SQUIDPY_DEVELOPMENT_HANDOVER',draft_files=14,
                helpers=13,evidence_files=len(index),prototype_contract_tests=13,
                snapshotted_cases=count,case_expectation_mismatches=mismatches,
                final_closed_cases=final_count,final_case_expectation_mismatches=final_mismatches,
                full_adapter_qualified=False,new_family_credit=0,new_reviews=0,
                original_or_Java_analysis_reexecuted=False,whole_goal_complete=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint',type=Path,default=Path(__file__).resolve().parent)
    args=parser.parse_args()
    try:print(json.dumps(verify(args.checkpoint),indent=2))
    except Exception as error:
        raise SystemExit('FAIL2: saved development handover verification failed: '+str(error)) from error
