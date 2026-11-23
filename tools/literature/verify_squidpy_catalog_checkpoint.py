"""Verify saved Squidpy admission, original-workflow evidence and current catalog."""
from pathlib import Path
import argparse,hashlib,io,json,re,sys,zipfile


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def verify(root,jar=None):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    root=root.resolve();folder=root/'docs/literature/squidpy-catalog-checkpoint'
    status=json.loads((folder/'STATUS.json').read_bytes())
    assert status['status']=='PASS_SQUIDPY_ONE_FAMILY_SIX_ROLES_WITH_RETAINED_WEBSITE_FAILURE'
    assert (status['tests_per_host'],status['actual_CLI_refusals'],status['complete_manifest_entries'])==(16,10,857),'Reported checkpoint counts differ from retained evidence'
    assert not any(status[k] for k in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'))
    for name,wanted in status['source_code'].items():
        path=Path(name);assert not path.is_absolute() and '..' not in path.parts
        assert pin((root/path).read_bytes())==wanted,name
    index_raw=(folder/'EVIDENCE.json').read_bytes();archive_raw=(folder/'evidence.zip').read_bytes()
    assert pin(index_raw)==status['evidence_index'] and pin(archive_raw)==status['evidence_archive']
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names))
        assert set(names)=={'objects/'+v['sha256'] for v in index.values()}
        for info in archive.infolist():
            assert info.file_size<80_000_000 and hashlib.sha256(archive.read(info)).hexdigest()==info.filename[8:]
        def raw(name):
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name],name;return value
        def obj(name):return json.loads(raw(name))
        for host in ('windows','linux'):
            check=obj('checks-2/'+host+'-model-transaction.RESULT.json');assert check['exit_code']==0
            text=raw('checks-2/'+host+'-model-transaction.stderr').decode('utf-8')
            assert re.search(r'Ran 16 tests in [0-9.]+s',text) and text.rstrip().endswith('OK')
            assert obj('verify-'+host+'-1/RESULT.json')['exit_code']==0
            saved=obj('verify-'+host+'-1/result/COMPLETED.json')
            assert saved['actual']['fresh_primary_XML_checked']==250 and saved['actual']['workflow']['actual_producer_checked']
        controls=obj('controls-2/RESULT.json');assert len(controls['cases'])==10 and controls['canonical_bytes_unchanged']
        assert all(c['exit_code']==c['expected'] and not c['complete_result'] for c in controls['cases'])
        assert sum(c['exit_code']==3 for c in controls['cases'])==2
        for name in ('ui-preview-2','ui-installed-1'):
            ui=obj(name+'/COMPLETED.json');assert ui['status']=='PASS_SQUIDPY_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE'
            assert ui['unique_profile_processes_after']==0 and ui['no_OS_process_termination']
            page=ui['page_result'];assert (page['reviewed'],page['pending'],page['integrations'])==(1685,1037,152)
            assert page['paper_roles_checked']==['P008','P009','P010','P024','P094','P161'] and page['page_errors']==[]
        for name in ('plan-windows-2','inspect-windows-1','inspect-linux-1','apply-windows-1'):
            assert obj(name+'/RESULT.json')['exit_code']==0
        website=obj('website/VALIDATION.json');assert not website['full_checkWebsite_passed']
        assert len(website['preexisting_missing_citation_panels'])==33
        for row in website['preexisting_missing_citation_panels']:assert pin((root/row['path']).read_bytes())==row['identity']
        assert obj('fresh-primary/ROLES.json')==json.loads((root/'docs/literature/squidpy-catalog-sources/ROLES.json').read_bytes())
    sys.path.insert(0,str(root/'tools/literature'))
    from squidpy_catalog import catalog as c,model as m
    assert c.ROOT.resolve()==root
    proof=json.loads((root/m.PROOF).read_bytes());assert proof['source_code']==c.sources()
    for name,wanted in proof['evidence'].items():assert pin((root/name).read_bytes())==wanted
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before)
    result=m.check(before,{n:(root/n).read_bytes() for n in m.META},m.frozen_primary(),json.loads((root/'docs/literature/squidpy-workflow-qualification/STATUS.json').read_bytes()))
    assert proof['result']==result and proof['before']=={n:pin(raw) for n,raw in before.items()}
    assert proof['after']=={n:pin((root/n).read_bytes()) for n in m.META}
    assert c.clean_manifest()['entries']==857
    workflow=c.workflow_verify(root,jar)
    return dict(status=status['status'],logical_files=len(index),tests_per_host=16,actual_CLI_refusals=10,
        complete_manifest_entries=857,counts=result,original_workflow=workflow,styled_preview_and_installed_UI_passed=True,
        full_checkWebsite_passed=False,existing_missing_citation_panels=33,original_analysis_reexecuted=False,whole_goal_complete=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(Path(__file__).resolve().parents[2],args.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved Squidpy catalog checkpoint failed: '+str(error)) from error
