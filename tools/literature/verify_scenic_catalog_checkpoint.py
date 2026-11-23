"""Read saved SCENIC admission evidence and reconcile current catalog/source bytes."""
from pathlib import Path
import argparse,hashlib,io,json,re,sys,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)

def verify(root,jar=None,cache=None):
    require(__debug__,'Run Python without -O');root=root.resolve();folder=root/'docs/literature/scenic-catalog-checkpoint';state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='PASS_SCENIC_ONE_UPSTREAM_FAMILY_FIVE_REVIEWED_ROLES_HISTORICAL_EDITION_SKIPPED' and state['catalog_admitted'],'Wrong admission scope')
    require((state['new_upstream_family_credit'],state['new_native_family_credit'],state['new_reviews'])==(1,0,5) and not any(state[k] for k in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete','original_analysis_reexecuted','historical_verifier_source_or_hashes_changed')),'False broad completion or historical override')
    for name,key in [('FILES.json','manifest'),('OMISSIONS.json','omissions'),('evidence.zip','archive')]:require(pin((folder/name).read_bytes())==state[key],'Changed packet: '+name)
    manifest=json.loads((folder/'FILES.json').read_bytes());require(len(manifest)==state['logical_files'] and 100<len(manifest)<10000,'Wrong evidence population')
    with zipfile.ZipFile(io.BytesIO((folder/'evidence.zip').read_bytes())) as z:
        wanted={'objects/'+v['sha256'] for v in manifest.values()};names=z.namelist()
        require(len(names)==len(set(names))==state['unique_objects'] and set(names)==wanted,'Changed object set')
        require(sum(i.file_size for i in z.infolist())==state['unique_uncompressed_bytes']<200_000_000,'Oversized or changed evidence')
        objects={name:z.read(name) for name in names}
    for name,identity in manifest.items():
        require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name and 'private-altered-primary' not in name and 'owned-edge-profile' not in name,'Private or unsafe logical member')
        require(pin(objects['objects/'+identity['sha256']])==identity,'Changed evidence: '+name)
    def read(name):return objects['objects/'+manifest[name]['sha256']]
    def obj(name):return json.loads(read(name))
    for name,identity in state['source_code'].items():require(pin((root/name).read_bytes())==pin(read('source/'+name))==identity,'Changed current source: '+name)
    require(state['tests_per_host']==16 and state['actual_CLI_refusals']==10 and state['complete_manifest_entries']==863,'Changed qualification counts')
    tests=obj('checks-1/RESULT.json')['checks'];require({r['host'] for r in tests}=={'windows','linux'} and all(r['exit_code']==0 and r['actual_tests']==16 for r in tests),'Model/transaction tests failed')
    for host in ('windows','linux'):
        require(re.search(rb'Ran 16 tests in [0-9.]+s',read('checks-1/'+host+'.stderr')) and read('checks-1/'+host+'.stderr').rstrip().endswith(b'OK'),'Wrong actual test console')
        require(obj('inspect-'+host+'-2/RESULT.json')['exit_code']==0 and obj('verify-'+host+'-2/RESULT.json')['exit_code']==0,'Actual catalog command failed')
        measured=obj('verify-'+host+'-2/result/COMPLETED.json');require(measured['result']==state['counts'] and measured['actual']['fresh_primary_XML_checked']==250 and len(measured['actual']['historical_metadata_overrides'])==4,'Wrong current verification scope')
        require(measured['actual']['live_scientific_source_and_exact_producer_checked'] and not measured['actual']['historical_reader_changed'] and not measured['actual']['historical_hashes_refreshed'],'Historical evidence rewritten')
    require(obj('apply-windows-2/RESULT.json')['exit_code']==0 and obj('apply-windows-2/result/COMPLETED.json')['canonical_modified'],'No actual admission')
    controls=obj('controls-2/RESULT.json');require(len(controls['cases'])==10 and controls['canonical_bytes_unchanged'],'Incomplete negative controls')
    for row in controls['cases']:
        require(row['exit_code']==row['expected']==(3 if row['case']=='missing-primary' else 2) and not row['complete_result'],'Refusal control failed')
        require(obj('controls-2/'+row['host']+'-'+row['case']+'/PROCESS.json')['pid']>0,'No actual controlled command')
    for phase in ('ui-preview-2','ui-installed-1'):
        ui=obj(phase+'/COMPLETED.json');require(ui['status']=='PASS_SCENIC_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE' and ui['unique_profile_processes_after']==0 and ui['no_OS_process_termination'],'Owned browser not closed')
        page=ui['page_result'];require((page['reviewed'],page['pending'],page['integrations'],page['native'],page['upstream'],page['missing'])==(1702,1020,155,29,126,257) and page['paper_roles_checked']==['P058','P070','P100','P145','P225'] and page['page_errors']==[],'Wrong displayed catalog')
        require(page['prior_smartpca_squidpy_renderers_preserved'] and page['prior_excluded_software_rows']==['P108','P212'],'Prior UI context changed')
    failed=obj('ui-preview-1/RETAINED-FAILURE.json');require(failed['node_exit']==1 and failed['own_browser_close_exit']==failed['own_profile_processes_after']==0 and not failed['canonical_catalog_modified'],'First failure lost')
    require(b'0 !== 1' in read('ui-preview-1/stderr'),'Original browser failure console lost')
    website=obj('website-1/VALIDATION.json');require(not website['full_checkWebsite_passed'] and website['exit_code']!=0 and len(website['preexisting_missing_citation_panels'])==33 and not website['new_missing_citation_panels'],'Website failure scope differs')
    for row in website['preexisting_missing_citation_panels']:require(pin((root/row['path']).read_bytes())==row['identity'],'Existing citation failure source changed')
    sys.path.insert(0,str(root/'tools/literature'))
    from scenic_catalog import catalog as c,model as m
    require(c.ROOT.resolve()==root,'Use this checkout source');proof=json.loads((root/m.PROOF).read_bytes())
    require(proof['source_code']==c.sources()==state['source_code'] and proof['base_commit']==state['base']==c.BASE,'Changed installed proof source')
    for name,identity in proof['evidence'].items():require(pin((root/name).read_bytes())==identity,'Changed admission evidence: '+name)
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before)
    require((root/c.FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==c.git('show',c.BASE+':'+m.DATA+'/SHA256SUMS'),'Changed old manifest')
    q,live=c.qualified(argparse.Namespace(jar=jar,cache=cache),before);after={n:(root/n).read_bytes() for n in m.META};actual=m.check(before,after,m.p.frozen(),q)
    require(actual==state['counts']==proof['result'] and proof['after']=={n:pin(raw) for n,raw in after.items()} and proof['before']=={n:pin(raw) for n,raw in before.items()},'Catalog differs from admitted transition')
    require(proof['qualification']==pin(m.encoded(q)) and (root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()),'Changed qualification/registry')
    require(c.clean_manifest()['entries']==863,'Changed complete catalog inventory')
    coverage=json.loads((root/m.COVERAGE).read_bytes());require(all(r['source_role_review_applied'] for r in coverage['roles']) and len(coverage['roles'])==5,'Applied role review missing')
    require(coverage['roles'][1]['paper_id']=='P070' and 'SKIP3' in coverage['roles'][1]['historical_edition_analysis_status'],'Historical unsupported analysis hidden')
    return dict(status=state['status'],logical_files=len(manifest),counts=actual,tests_per_host=16,actual_CLI_refusals=10,complete_manifest_entries=863,current_catalog_and_scientific_source_checked=True,exact_JAR_checked=jar is not None,fresh_primary_XML_checked=live['fresh_primary_XML_checked'],fresh_scientific_fit_executed=False,full_checkWebsite_passed=False,existing_citation_failures=33,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--jar',type=Path);parser.add_argument('--cache',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(args.root,args.jar,args.cache),indent=2))
    except Exception as error:raise SystemExit('FAIL2: SCENIC catalog checkpoint check failed: '+repr(error)+'. Retain changed/failed evidence; restore the correct source context. Do not refresh historical hashes to bypass failure.') from error
