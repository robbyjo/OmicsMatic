"""Verify the saved MR-PRESSO catalog admission and its exact current source context."""
from pathlib import Path
import argparse,hashlib,io,json,re,sys,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root,jar=None):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    root=root.resolve();folder=root/'docs/literature/mrpresso-catalog-checkpoint';status=json.loads((folder/'STATUS.json').read_bytes())
    assert status['status']=='PASS_MRPRESSO_ONE_NATIVE_FAMILY_SIX_ROLES_WITH_RETAINED_WEBSITE_FAILURE'
    assert (status['tests_per_host'],status['actual_CLI_refusals'],status['complete_manifest_entries'])==(19,10,859),'Reported checkpoint counts differ from evidence'
    assert (status['scoped_families'],status['native'],status['upstream'],status['reviewed_uses'],status['pending_uses'])==(153,28,125,1691,1031),'Reported admission counts differ'
    assert not any(status[key] for key in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'))
    for name,wanted in status['source_code'].items():
        path=Path(name);assert not path.is_absolute() and '..' not in path.parts
        assert pin((root/path).read_bytes())==wanted,'Changed checkpoint source: '+name
    index_raw=(folder/'EVIDENCE.json').read_bytes();archive_raw=(folder/'evidence.zip').read_bytes()
    assert pin(index_raw)==status['evidence_index'] and pin(archive_raw)==status['evidence_archive'],'Changed checkpoint evidence archive/index'
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and set(names)=={'objects/'+value['sha256'] for value in index.values()}
        for info in archive.infolist():assert info.file_size<80_000_000 and hashlib.sha256(archive.read(info)).hexdigest()==info.filename[8:]
        def raw(name):
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name],name;return value
        def obj(name):return json.loads(raw(name))
        for host in ('windows','linux'):
            assert obj('checks-2/'+host+'-model-transaction.RESULT.json')['exit_code']==0
            text=raw('checks-2/'+host+'-model-transaction.stderr').decode();assert re.search(r'Ran 19 tests in [0-9.]+s',text) and text.rstrip().endswith('OK')
            assert obj('verify-'+host+'-1/RESULT.json')['exit_code']==0
            measured=obj('verify-'+host+'-1/result/COMPLETED.json')['actual']
            assert measured['fresh_primary_XML_checked']==250 and measured['workflow']['native_workflow_qualified'] is True
            bridge=measured['predecessor_dispatch_bridge'];assert bridge['exact_source_extension_lines']==3 and bridge['unchanged_previous_payloads']==22894
            assert bridge['historical_source_hashes_refreshed'] is False and bridge['historical_reader_reexecuted_now'] is False
        controls=obj('controls-2/RESULT.json');assert len(controls['cases'])==10 and controls['canonical_bytes_unchanged']
        assert all(row['exit_code']==row['expected'] and not row['complete_result'] for row in controls['cases'])
        assert sum(row['exit_code']==3 for row in controls['cases'])==2
        for name in ('ui-preview-2','ui-installed-1'):
            ui=obj(name+'/COMPLETED.json');assert ui['status']=='PASS_MRPRESSO_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE'
            assert ui['unique_profile_processes_after']==0 and ui['no_OS_process_termination']
            page=ui['page_result'];assert (page['reviewed'],page['pending'],page['integrations'],page['native'],page['upstream'])==(1691,1031,153,28,125)
            assert page['paper_roles_checked']==['P105','P125','P135','P150','P189','P224'] and page['page_errors']==[]
            assert page['discussion_only_not_added']==['P177'] and page['prior_squidpy_renderer_preserved']
        for name in ('plan-windows-2','inspect-windows-2','inspect-linux-2','apply-windows-1'):assert obj(name+'/RESULT.json')['exit_code']==0
        website=obj('website-2/VALIDATION.json');assert website['full_checkWebsite_passed'] is False and website['exit_code']!=0
        assert len(website['preexisting_missing_citation_panels'])==33 and not website['new_missing_citation_panels']
        for row in website['preexisting_missing_citation_panels']:assert pin((root/row['path']).read_bytes())==row['identity']
        assert obj('source-contexts/SOURCE-CONTEXTS.json')['original_native_scientific_source_changed'] is False
        screen=obj('source-contexts/SUPPLEMENTARY-ALIAS-SCREEN.json');assert screen['papers_checked']==250 and screen['public_api_identifier_candidates']==screen['additional_positive_papers_found']==0
        assert {row['paper_id'] for row in screen['expanded_name_candidates']}=={'P150','P189'}
    sys.path.insert(0,str(root/'tools/literature'))
    from mrpresso_catalog import catalog as c,model as m
    assert c.ROOT.resolve()==root,'Use this source checkout, not an already imported module from another root'
    proof=json.loads((root/m.PROOF).read_bytes());assert proof['source_code']==c.sources()
    for name,wanted in proof['evidence'].items():assert pin((root/name).read_bytes())==wanted,name
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before)
    q=json.loads((root/'docs/literature/mrpresso-workflow-qualification/STATUS.json').read_bytes());result=m.check(before,{name:(root/name).read_bytes() for name in m.META},m.frozen_primary(),q)
    assert proof['result']==result and proof['before']=={name:pin(value) for name,value in before.items()}
    assert proof['after']=={name:pin((root/name).read_bytes()) for name in m.META}
    assert (root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry())
    assert c.clean_manifest()['entries']==859
    workflow=c.workflow_verify(root,jar)
    from mrpresso_catalog.compatibility import verify as bridge_verify
    bridge=bridge_verify()
    return dict(status=status['status'],logical_files=len(index),tests_per_host=19,actual_CLI_refusals=10,complete_manifest_entries=859,
        counts=result,native_workflow=workflow,explicit_predecessor_bridge=bridge,styled_preview_and_installed_UI_passed=True,
        full_checkWebsite_passed=False,existing_missing_citation_panels=33,original_analysis_reexecuted=False,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(Path(__file__).resolve().parents[2],args.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved MR-PRESSO catalog checkpoint failed: '+str(error)) from error
