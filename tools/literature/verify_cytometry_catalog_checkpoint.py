"""Verify the installed scoped cytometry catalog and saved checks without scientific refits."""
from pathlib import Path
import argparse,hashlib,json,sys,zipfile
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)
def verify(root,jar=None,private_root=None,cache=None):
    root=root.resolve();folder=root/'docs/literature/cytometry-catalog-checkpoint';state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='PASS_NATIVE_CYTOMETRY_FAMILY_44_SOURCE_ROLES' and state['catalog_admitted'],'Changed admission scope')
    require((state['new_native_family_credit'],state['new_upstream_family_credit'],state['new_reviews'],state['existing_reviews_enriched'],state['added_positive_observations'])==(1,0,41,3,0) and not state['whole_goal_complete'],'Expanded family/review claim')
    require(not state['FACSDiva_implemented'] and not state['vendor_statistics_qualified'],'Expanded instrument/vendor claim')
    for name,key in [('FILES.json','manifest'),('evidence.zip','archive'),('OMISSIONS.json','omissions')]:require(pin((folder/name).read_bytes())==state[key],'Changed evidence packet')
    manifest=json.loads((folder/'FILES.json').read_bytes())
    with zipfile.ZipFile(folder/'evidence.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==state['unique_objects'] and set(names)=={'objects/'+v['sha256'] for v in manifest.values()},'Changed archive objects');require(sum(x.file_size for x in archive.infolist())<150000000,'Oversized packet');objects={n:archive.read(n) for n in names}
    require(len(manifest)==state['logical_files'],'Changed evidence population')
    for name,identity in manifest.items():require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name and ':' not in name,'Unsafe evidence name');require(pin(objects['objects/'+identity['sha256']])==identity,'Changed saved evidence')
    def read(name):return objects['objects/'+manifest[name]['sha256']]
    def obj(name):return json.loads(read(name))
    for name,identity in state['source_code'].items():require(pin((root/name).read_bytes())==pin(read('source/'+name))==identity,'Changed source: '+name)
    for host in ('windows','linux'):
        tests=obj('checks-'+host+'-2/RESULT.json');require(tests['exit_code']==0 and tests['tests']==16 and tests['canonical_unchanged'],'Failed model/transaction checks');require(tests['primary']==dict(primary_XML_checked=250,matched_papers=33,matched_paragraphs=48,reviewed_source_roles=44,extra_structures=0,original_paper_analyses_reexecuted=False),'Incomplete full-source audit')
        require(obj('checks-'+host+'-2/SOURCE-INPUTS.json')==obj('plan-'+host+'-2/PLAN.json')['source_code'],'Different tested/planned source')
        for mode in ('plan','inspect','verify'):row=obj(mode+'-'+host+'-2.RESULT.json');require(row['exit_code']==0 and not row['canonical_modified'],'Missing actual host CLI stage')
    require(obj('apply-windows-2.RESULT.json')['exit_code']==0 and obj('apply-windows-2/COMPLETED.json')['canonical_modified'],'No guarded canonical application')
    controls=obj('controls-2/RESULT.json');require(controls['canonical_bytes_unchanged'] and len(controls['cases'])==12,'Incomplete actual refusals')
    missing={'missing-primary','missing-native-producer','missing-predecessor','missing-plan'}
    for row in controls['cases']:require(row['exit_code']==row['expected']==(3 if row['case'] in missing else 2) and not row['complete_result'],'Unexpected refusal outcome')
    for phase in ('ui-preview-3','ui-installed-1'):
        result=obj(phase+'/COMPLETED.json');require(result['unique_profile_processes_after']==0 and result['no_OS_process_termination'],'Owned browser left running');page=result['page_result'];require((page['reviewed'],page['pending'],page['integrations'],page['native'],page['upstream'],page['missing'])==(1755,969,158,30,128,254),'Wrong rendered coverage');require(len(page['paper_roles_checked'])==len(set(page['paper_roles_checked']))==44 and not page['FACSDiva_implemented'] and not page['vendor_statistics_qualified'] and not page['page_errors'],'Wrong rendered role limits')
    website=obj('website-1/VALIDATION.json');require(not website['full_checkWebsite_passed'] and len(website['preexisting_missing_citation_panels'])==33 and website['new_missing_citation_panels']==[] and website['literature_reports_withheld_by_unchanged_Pages_workflow'],'Changed preexisting website gate/hold')
    sys.path.insert(0,str(root/'tools/literature'))
    from cytometry_catalog import catalog as c,model as m
    from verify_cytometry_intensity_checkpoint import verify as workflow
    expected={**c.sources(),'tools/literature/verify_cytometry_catalog_checkpoint.py':pin((root/'tools/literature/verify_cytometry_catalog_checkpoint.py').read_bytes())}
    require(c.ROOT.resolve()==root and state['source_code']==expected,'Changed source reader context');proof=json.loads((root/m.PROOF).read_bytes());require(proof['source_code']==c.sources() and proof['base_commit']==c.BASE,'Changed admission source/base')
    for name,identity in proof['evidence'].items():require(pin((root/name).read_bytes())==identity,'Changed installed admission evidence')
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before);require(proof['before']=={n:pin(raw) for n,raw in before.items()},'Changed exact predecessor archive');q=m.qualification();require(proof['qualification']==pin(m.encoded(q)) and (root/c.FOLDER/'QUALIFICATION.json').read_bytes()==m.encoded(q),'Changed native qualification');after={n:(root/n).read_bytes() for n in m.META};result=m.check(before,after,m.p.frozen(),q)
    require(result==proof['result']==state['counts'] and proof['after']=={n:pin(raw) for n,raw in after.items()},'Changed installed catalog');require((root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and len(m.registry())==485 and c.clean_manifest()['entries']==869,'Changed provider registry or complete manifest')
    actual=workflow(root,jar,private_root);fresh=dict(primary_XML_checked=0)
    if cache is not None:_,fresh=m.p.audit(cache,before[m.META[0]])
    return dict(status=state['status'],counts=result,complete_manifest_entries=869,current_catalog_and_source_checked=True,exact_JAR_checked=jar is not None,private_files_checked=actual['private_files_checked'],fresh_primary_XML_checked=fresh['primary_XML_checked'],scientific_analysis_reexecuted=False,catalog_admitted=True,whole_goal_complete=False)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    for name in ('jar','private-root','cache'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    try:print(json.dumps(verify(args.root,args.jar,args.private_root,args.cache),indent=2))
    except Exception as error:
        print('FAIL2: cytometry catalog checkpoint invalid: '+repr(error)+'. Preserve evidence; restore the exact source, metadata, producer or permitted input. Never refresh historical hashes to force success.',file=sys.stderr)
        raise SystemExit(2) from error
