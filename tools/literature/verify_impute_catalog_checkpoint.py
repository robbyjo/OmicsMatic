"""Check the installed IMPUTE source-role catalog and saved evidence; no scientific refit."""
from pathlib import Path
import argparse,hashlib,json,sys,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)

def verify(root,jar=None,private_root=None,cache=None):
    root=root.resolve();folder=root/'docs/literature/impute-catalog-checkpoint';state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='PASS_IMPUTE2_SCOPED_FAMILY_SEVEN_SOURCE_ROLES_P116_DECLARATION' and state['catalog_admitted'],'Wrong admission scope')
    require((state['new_upstream_family_credit'],state['new_native_family_credit'],state['new_reviews'],state['added_positive_observations'])==(1,0,7,1) and not state['whole_goal_complete'],'Expanded admission claim')
    for name,key in [('FILES.json','manifest'),('evidence.zip','archive'),('OMISSIONS.json','omissions')]:require(pin((folder/name).read_bytes())==state[key],'Changed evidence packet')
    manifest=json.loads((folder/'FILES.json').read_bytes());require(len(manifest)==state['logical_files'],'Changed logical evidence population')
    with zipfile.ZipFile(folder/'evidence.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==state['unique_objects'] and set(names)=={'objects/'+x['sha256'] for x in manifest.values()},'Changed archive objects')
        require(sum(x.file_size for x in archive.infolist())<150_000_000,'Oversized evidence packet');objects={n:archive.read(n) for n in names}
    for name,identity in manifest.items():
        require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name and ':' not in name,'Unsafe logical evidence path')
        require(pin(objects['objects/'+identity['sha256']])==identity,'Changed saved evidence')
    def read(name):return objects['objects/'+manifest[name]['sha256']]
    def obj(name):return json.loads(read(name))
    for name,identity in state['source_code'].items():require(pin((root/name).read_bytes())==pin(read('source/'+name))==identity,'Changed source: '+name)
    require(state['tests_per_host']==15 and state['actual_CLI_refusals']==10 and state['complete_manifest_entries']==867,'Changed validation scope')
    for host in ('windows','linux'):
        checks=obj('checks-'+host+'-1/RESULT.json');require(checks['exit_code']==0 and checks['tests']==15 and checks['canonical_unchanged'],'Failed unit/source checks')
        require(checks['primary']==dict(primary_XML_checked=250,named_paragraphs_checked=13,nonpositive_or_resource_contexts_checked=46,new_missed_software_declaration='P116',original_scientific_analysis_reexecuted=False),'Incomplete primary audit')
        for mode in ('plan','inspect','verify'):
            step=obj(mode+'-'+host+'-1.RESULT.json');require(step['exit_code']==0 and not step['canonical_modified'],'Missing actual host CLI stage')
    require(obj('apply-windows-1.RESULT.json')['exit_code']==0 and obj('apply-windows-1/COMPLETED.json')['canonical_modified'],'No guarded canonical apply')
    controls=obj('controls-1/RESULT.json');require(controls['canonical_bytes_unchanged'] and len(controls['cases'])==10,'Incomplete CLI refusals')
    for row in controls['cases']:require(row['exit_code']==row['expected']==(3 if row['case']=='missing-primary' else 2) and not row['complete_result'],'Wrong refusal outcome')
    for phase in ('ui-preview-1','ui-installed-1'):
        check=obj(phase+'/COMPLETED.json');require(check['unique_profile_processes_after']==0 and check['no_OS_process_termination'],'Owned browser not closed')
        page=check['page_result'];require((page['reviewed'],page['pending'],page['integrations'],page['native'],page['upstream'],page['missing'])==(1714,1010,157,29,128,255) and page['paper_roles_checked']==['P076','P084','P116','P136','P140','P149','P180'] and page['page_errors']==[],'Incorrect rendered roles/counts')
    website=obj('website-1/VALIDATION.json');require(not website['full_checkWebsite_passed'] and len(website['preexisting_missing_citation_panels'])==33 and website['new_missing_citation_panels']==[] and website['literature_reports_withheld_by_unchanged_Pages_workflow'],'Changed existing website gate or publication policy')
    sys.path.insert(0,str(root/'tools/literature'))
    from impute_catalog import catalog as c,model as m
    from verify_impute2_checkpoint import verify as workflow
    require(c.ROOT.resolve()==root and state['source_code']=={**c.sources(),'tools/literature/verify_impute_catalog_checkpoint.py':pin((root/'tools/literature/verify_impute_catalog_checkpoint.py').read_bytes())},'Changed source reader context')
    proof=json.loads((root/m.PROOF).read_bytes());require(proof['source_code']==c.sources() and proof['base_commit']==c.BASE,'Changed admission proof source/base')
    for name,identity in proof['evidence'].items():require(pin((root/name).read_bytes())==identity,'Changed admission evidence')
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before);require(proof['before']=={n:pin(raw) for n,raw in before.items()},'Changed predecessor archive')
    q=m.qualification();require(proof['qualification']==pin(m.encoded(q)) and (root/c.FOLDER/'QUALIFICATION.json').read_bytes()==m.encoded(q),'Changed original workflow qualification')
    after={n:(root/n).read_bytes() for n in m.META};result=m.check(before,after,m.p.frozen(),q)
    require(result==state['counts']==proof['result'] and proof['after']=={n:pin(raw) for n,raw in after.items()},'Changed installed catalog')
    require((root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and c.clean_manifest()['entries']==867,'Changed qualified registry or complete manifest')
    current=workflow(root,jar,private_root);fresh=dict(primary_XML_checked=0)
    if cache is not None:_,fresh=m.p.audit(cache,before[m.META[0]])
    return dict(status=state['status'],counts=result,logical_files=len(manifest),complete_manifest_entries=867,current_catalog_and_source_checked=True,exact_JAR_checked=jar is not None,saved_private_identities_checked=current['private_evidence_checked'],fresh_primary_XML_checked=fresh['primary_XML_checked'],original_scientific_analysis_reexecuted=False,tests_rerun=False,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    for name in ('jar','private-root','cache'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    try:print(json.dumps(verify(args.root,args.jar,args.private_root,args.cache),indent=2))
    except Exception as error:raise SystemExit('FAIL2: IMPUTE catalog checkpoint invalid: '+repr(error)+'. Preserve evidence and restore the exact source, base commit, producer or permitted inputs; do not refresh historical hashes.') from error
