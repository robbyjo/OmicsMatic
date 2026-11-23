"""Verify saved smartpca catalog admission, reported contexts and current source."""
from pathlib import Path
import argparse,hashlib,io,json,re,sys,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root,jar=None):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    root=root.resolve();folder=root/'docs/literature/smartpca-catalog-checkpoint';status=json.loads((folder/'STATUS.json').read_bytes())
    assert status['status']=='PASS_SMARTPCA_ONE_NATIVE_FAMILY_SIX_CONTEXT_ROLES_WITH_RETAINED_WEBSITE_FAILURE'
    assert (status['tests_per_host'],status['actual_CLI_refusals'],status['complete_manifest_entries'])==(21,10,861),'Reported checkpoint counts differ from evidence'
    assert (status['scoped_families'],status['native'],status['upstream'],status['reviewed_uses'],status['pending_uses'])==(154,29,125,1697,1025)
    assert status['catalog_admitted'] and not any(status[k] for k in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'))
    for name,wanted in status['source_code'].items():
        path=Path(name);assert not path.is_absolute() and '..' not in path.parts
        assert pin((root/path).read_bytes())==wanted,'Changed checkpoint source: '+name
    index_raw=(folder/'EVIDENCE.json').read_bytes();archive_raw=(folder/'evidence.zip').read_bytes()
    assert pin(index_raw)==status['evidence_index'] and pin(archive_raw)==status['evidence_archive'],'Changed checkpoint evidence archive/index'
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and set(names)=={'objects/'+v['sha256'] for v in index.values()}
        assert sum(i.file_size for i in archive.infolist())<536_870_912
        for info in archive.infolist():assert info.file_size<80_000_000 and pin(archive.read(info))['sha256']==info.filename[8:]
        def raw(name):
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name],name;return value
        def obj(name):return json.loads(raw(name))
        for host in ('windows','linux'):
            assert obj('checks-1/'+host+'-model-transaction.RESULT.json')['exit_code']==0
            text=raw('checks-1/'+host+'-model-transaction.stderr').decode();assert re.search(r'Ran 21 tests in [0-9.]+s',text) and text.rstrip().endswith('OK')
            assert obj('verify-'+host+'-1/RESULT.json')['exit_code']==0
            measured=obj('verify-'+host+'-1/result/COMPLETED.json')['actual']
            assert measured['fresh_primary_XML_checked']==250 and measured['workflow']['native_fst_workflow_qualified'] is True
            assert measured['workflow']['packaged_protocol_commands']==362 and measured['prepared_PCA']['packaged_protocol_commands']==261
            assert measured['predecessor_dispatch_bridge_checked_by_fst_reader'] and not measured['historical_readers_reexecuted_on_changed_dispatcher']
        controls=obj('controls-1/RESULT.json');assert len(controls['cases'])==10 and controls['canonical_bytes_unchanged']
        assert all(r['exit_code']==r['expected'] and not r['complete_result'] for r in controls['cases']) and sum(r['exit_code']==3 for r in controls['cases'])==2
        for host in ('windows','linux'):
            for case in ('missing-primary','wrong-producer','changed-primary','changed-plan','changed-preview'):
                row=next(r for r in controls['cases'] if r['host']==host and r['case']==case)
                assert obj('controls-1/'+host+'-'+case+'/PROCESS.json')['pid']>0 and row['expected']==(3 if case=='missing-primary' else 2)
                assert not obj('controls-1/'+host+'-'+case+'/result/FAILURE.json')['complete_result']
        for name in ('ui-preview-2','ui-installed-1'):
            ui=obj(name+'/COMPLETED.json');assert ui['status']=='PASS_SMARTPCA_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE'
            assert ui['unique_profile_processes_after']==0 and ui['no_OS_process_termination']
            page=ui['page_result'];assert (page['reviewed'],page['pending'],page['integrations'],page['native'],page['upstream'])==(1697,1025,154,29,125)
            assert page['paper_roles_checked']==['P019','P041','P045','P071','P180','P229'] and page['page_errors']==[]
            assert page['prior_mrpresso_squidpy_renderers_preserved'] and page['prior_excluded_software_rows']==['P108','P212']
        for name in ('plan-windows-1','inspect-windows-1','inspect-linux-1','apply-windows-1'):assert obj(name+'/RESULT.json')['exit_code']==0
        website=obj('website-2/VALIDATION.json');assert not website['full_checkWebsite_passed'] and website['exit_code']!=0
        assert len(website['preexisting_missing_citation_panels'])==33 and not website['new_missing_citation_panels']
        for row in website['preexisting_missing_citation_panels']:assert pin((root/row['path']).read_bytes())==row['identity']
        assert obj('SOURCE-CONTEXTS.json')['original_native_scientific_source_changed'] is False
        screen=obj('EXPANDED-NAME-SCREEN.json');assert screen['papers_checked']==250 and not screen['additional_candidate_papers'] and not screen['additional_body_table_code_candidates']
        assert not screen['individual_classification_pending'] and set(screen['candidates_papers'])<=set(['P019','P041','P045','P071','P180','P229'])
        oldclose=obj('ui-preview-1/GUARD-RECOVERY.json');assert oldclose['initial_node_page_check_exit']==0 and oldclose['initial_close_guard_exit']!=0 and oldclose['corrected_exact_profile_close_exit']==0
        assert obj('ui-preview-1/EXACT-PROFILE-CHECK.json')['matching_processes']==[]
    sys.path.insert(0,str(root/'tools/literature'))
    from smartpca_catalog import catalog as c,model as m
    assert c.ROOT.resolve()==root,'Use this source checkout, not another imported root'
    proof=json.loads((root/m.PROOF).read_bytes());assert proof['source_code']==c.sources()
    for name,wanted in proof['evidence'].items():assert pin((root/name).read_bytes())==wanted,name
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before)
    assert (root/c.FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==c.git('show',c.BASE+':'+m.DATA+'/SHA256SUMS')
    q=json.loads((root/'docs/literature/smartpca-fst-qualification/STATUS.json').read_bytes());result=m.check(before,{n:(root/n).read_bytes() for n in m.META},m.frozen_primary(),q)
    assert proof['result']==result and proof['before']=={n:pin(raw) for n,raw in before.items()}
    assert proof['after']=={n:pin((root/n).read_bytes()) for n in m.META} and proof['qualification']==pin(m.encoded(q))
    assert (root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and c.clean_manifest()['entries']==861
    coverage=json.loads((root/m.COVERAGE).read_bytes());assert all(r['source_role_review_applied'] for r in coverage['roles']) and coverage['new_reviews']==6
    workflow=c.workflow_verify(root,jar)
    from verify_smartpca_workflow_checkpoint import verify as prepared_verify
    prepared=prepared_verify(root)
    return dict(status=status['status'],logical_files=len(index),tests_per_host=21,actual_CLI_refusals=10,complete_manifest_entries=861,
        counts=result,native_PCA_FST_workflow=workflow,prior_prepared_PCA=prepared,explicit_retained_predecessor_bridge_checked=True,
        styled_preview_and_installed_UI_passed=True,full_checkWebsite_passed=False,existing_missing_citation_panels=33,
        original_analysis_reexecuted=False,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(Path(__file__).resolve().parents[2],args.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved smartpca catalog checkpoint failed: '+repr(error)+'. Restore exact published source/evidence; preserve altered files. Do not refresh historical hashes to bypass failure.') from error
