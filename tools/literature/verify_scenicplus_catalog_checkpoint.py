"""Check current catalog bytes and saved source-bound evidence; no scientific refit."""
from pathlib import Path
import argparse,hashlib,json,re,sys,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)

def verify(root,jar=None,private_root=None,cache=None,author_root=None):
    root=root.resolve();folder=root/'docs/literature/scenicplus-catalog-checkpoint';state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='PASS_SCENICPLUS_ONE_UPSTREAM_FAMILY_SIX_ROLES_FIVE_NEW_REVIEWS_ONE_ENRICHMENT' and state['catalog_admitted'],'Wrong catalog scope')
    require((state['new_upstream_family_credit'],state['new_native_family_credit'],state['new_reviews'],state['existing_reviews_enriched'])==(1,0,5,1) and not state['whole_goal_complete'],'Expanded admission claim')
    for name,key in [('FILES.json','manifest'),('evidence.zip','archive'),('OMISSIONS.json','omissions')]:require(pin((folder/name).read_bytes())==state[key],'Changed evidence packet')
    manifest=json.loads((folder/'FILES.json').read_bytes());require(len(manifest)==state['logical_files'],'Changed logical population')
    with zipfile.ZipFile(folder/'evidence.zip') as z:
        names=z.namelist();require(len(names)==len(set(names))==state['unique_objects'] and set(names)=={'objects/'+x['sha256'] for x in manifest.values()},'Changed objects')
        require(sum(i.file_size for i in z.infolist())<100_000_000,'Oversized evidence packet');objects={n:z.read(n) for n in names}
    for name,identity in manifest.items():
        require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name,'Unsafe logical path')
        require(pin(objects['objects/'+identity['sha256']])==identity,'Changed saved evidence')
    def read(name):return objects['objects/'+manifest[name]['sha256']]
    def obj(name):return json.loads(read(name))
    for name,identity in state['source_code'].items():require(pin((root/name).read_bytes())==pin(read('source/'+name))==identity,'Changed source: '+name)
    require(state['tests_per_host']==16 and state['actual_CLI_refusals']==10 and state['complete_manifest_entries']==865,'Changed test/integrity scope')
    for host in ('windows','linux'):
        unit=obj('checks-'+host+'-2/RESULT.json');require(unit['exit_code']==0 and unit['actual_tests']==16,'Failed model/transaction tests')
        require(re.search(rb'Ran 16 tests in [0-9.]+s',read('checks-'+host+'-2/stderr')) and read('checks-'+host+'-2/stderr').rstrip().endswith(b'OK'),'Missing actual unit console')
        for mode in ('inspect','verify'):
            require(obj(mode+'-'+host+'-2/RESULT.json')['exit_code']==0,'Failed actual catalog command')
        measured=obj('verify-'+host+'-2/result/COMPLETED.json');require(measured['result']==state['counts'] and measured['actual']['fresh_source_audit']==dict(primary_XML_checked=250,additional_supporting_paragraphs=2,exact_author_blobs_checked=42,original_author_notebooks_executed=False),'Wrong fresh source scope')
        require(measured['actual']['archived_predecessor']['counts']['scoped_families']==155 and not measured['actual']['historical_readers_executed_on_current_changed_dispatcher'] and not measured['actual']['historical_hashes_refreshed'],'Historical context misrepresented')
    require(obj('apply-windows-2/RESULT.json')['exit_code']==0 and obj('apply-windows-2/result/COMPLETED.json')['canonical_modified'],'No guarded admission')
    controls=obj('controls-2/RESULT.json');require(controls['canonical_bytes_unchanged'] and len(controls['cases'])==10,'Incomplete refusals')
    for row in controls['cases']:require(row['exit_code']==row['expected']==(3 if row['case']=='missing-primary' else 2) and not row['complete_result'],'Wrong refusal outcome')
    for phase in ('ui-preview-3','ui-installed-1'):
        record=obj(phase+'/COMPLETED.json');require(record['unique_profile_processes_after']==0 and record['no_OS_process_termination'],'Browser owner not closed')
        page=record['page_result'];require((page['reviewed'],page['pending'],page['integrations'],page['native'],page['upstream'],page['missing'])==(1707,1016,156,29,127,256) and page['paper_roles_checked']==['P008','P021','P047','P061','P094','P153'] and page['page_errors']==[],'Incorrect rendered catalog')
    require(obj('ui-preview-1/RETAINED-FAILURE.json')['node_exit']==0 and obj('ui-preview-1/RETAINED-FAILURE.json')['owned_profile_processes_observed_now']==[],'Lost first helper failure')
    sys.path.insert(0,str(root/'tools/literature'))
    from scenicplus_catalog import catalog as c,model as m
    from verify_scenicplus_global_checkpoint import verify as workflow
    require(c.ROOT.resolve()==root and state['source_code']=={**c.sources(),'tools/literature/verify_scenicplus_catalog_checkpoint.py':pin((root/'tools/literature/verify_scenicplus_catalog_checkpoint.py').read_bytes())},'Changed installed reader source')
    proof=json.loads((root/m.PROOF).read_bytes());require(proof['source_code']==c.sources() and proof['base_commit']==c.BASE,'Changed admission proof')
    for name,identity in proof['evidence'].items():require(pin((root/name).read_bytes())==identity,'Changed admission evidence')
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before)
    require(proof['before']=={n:pin(raw) for n,raw in before.items()},'Changed predecessor archive')
    q=json.loads((root/m.QUALIFICATION).read_bytes());require(proof['qualification']==pin(m.encoded(q)) and (root/c.FOLDER/'QUALIFICATION.json').read_bytes()==m.encoded(q),'Changed workflow qualification')
    after={n:(root/n).read_bytes() for n in m.META};result=m.check(before,after,m.p.frozen(),q)
    require(result==state['counts']==proof['result'] and proof['after']=={n:pin(raw) for n,raw in after.items()},'Changed current catalog')
    require((root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and c.clean_manifest()['entries']==865,'Changed complete registry/manifest')
    current=workflow(root,jar,private_root)
    fresh=dict(primary_XML_checked=0,exact_author_blobs_checked=0)
    if cache is not None:
        _,fresh=m.p.audit(cache,before[m.META[0]],author_root)
    else:require(author_root is None,'Supply primary cache with author source tree')
    return dict(status=state['status'],counts=result,logical_files=len(manifest),complete_manifest_entries=865,current_catalog_and_source_checked=True,exact_JAR_checked=jar is not None,saved_private_identities_checked=current['private_evidence_checked'],fresh_primary_XML_checked=fresh['primary_XML_checked'],exact_author_blobs_checked=fresh['exact_author_blobs_checked'],original_scientific_analysis_reexecuted=False,tests_rerun=False,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    for name in ('jar','private-root','cache','author-root'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    try:print(json.dumps(verify(args.root,args.jar,args.private_root,args.cache,args.author_root),indent=2))
    except Exception as error:raise SystemExit('FAIL2: SCENIC+ catalog check failed: '+repr(error)+'. Retain evidence and restore the exact source context; never refresh historical hashes.') from error
