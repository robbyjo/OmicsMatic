"""Verify the installed five-role FastQTL catalog and saved qualification; no scientific refits."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,zipfile

def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def fp(path):
    with path.open('rb') as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def packet(folder,state):
    for name,key in [('FILES.json','manifest'),('evidence.zip','archive'),('PRIVATE-FILES.json','private_inventory'),('OMISSIONS.json','omissions')]:require(fp(folder/name)==state[key],'Changed saved packet '+name)
    files=json.loads((folder/'FILES.json').read_bytes())
    with zipfile.ZipFile(folder/'evidence.zip') as z:
        names=z.namelist();require(len(names)==len(set(names))==state['unique_objects'] and set(names)=={'objects/'+v['sha256'] for v in files.values()},'Changed archive objects');require(sum(x.file_size for x in z.infolist())<150000000,'Oversized evidence archive');objects={n:z.read(n) for n in names}
    require(len(files)==state['logical_files'],'Changed saved population')
    for name,identity in files.items():require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name and ':' not in name,'Unsafe saved name');require(pin(objects['objects/'+identity['sha256']])==identity,'Changed saved evidence '+name)
    return files,lambda name:objects['objects/'+files[name]['sha256']]

def verify(root,jar=None,private_root=None,workflow_source_root=None,historical_root=None,historical_jar=None,cache=None,workflow_private_root=None):
    root=root.resolve();folder=root/'docs/literature/fastqtl-catalog-checkpoint';state=json.loads((folder/'STATUS.json').read_bytes());require(state['status']=='PASS_FASTQTL_CATALOG_ONE_FAMILY_FIVE_ROLES' and state['catalog_admitted'] and not state['whole_goal_complete'],'Expanded catalog claim');require((state['new_upstream_family_credit'],state['new_reviews'],state['historical_2_165_qualified'],state['local_P212_FastQTL_run_established'])==(1,5,False,False),'Expanded edition/local-execution claim')
    files,raw=packet(folder,state);obj=lambda name:json.loads(raw(name))
    for name,identity in state['source_code'].items():require(fp(root/name)==pin(raw('source/'+name))==identity,'Changed qualified source '+name)
    for host in ('windows','linux'):
        tests=obj('checks-'+host+'-3/RESULT.json');require(tests['tests']==16 and tests['failures']==tests['errors']==0 and tests['canonical_unchanged'] and tests['source_unchanged'],'Failed model/transaction checks');require(tests['primary']==dict(primary_XML_checked=250,matched_papers=5,matched_paragraphs=10,reviewed_source_roles=5,extra_structures=0,contextual_2_165_declarations=2,original_paper_analyses_reexecuted=False),'Incomplete fresh source audit')
        require(obj('checks-'+host+'-3/SOURCE-INPUTS.json')==obj('plan-'+host+'-1/PLAN.json')['source_code'],'Different tested/planned sources')
        for mode in ('plan','inspect','verify'):
            result=obj(mode+'-'+host+'-1.RESULT.json');require(result['exit_code']==0 and not result['canonical_modified'],'Missing actual host CLI '+mode)
    require(obj('apply-windows-1.RESULT.json')['exit_code']==0 and obj('apply-windows-1/COMPLETED.json')['canonical_modified'],'No guarded canonical application')
    controls=obj('controls-1/RESULT.json');require(controls['canonical_bytes_unchanged'] and controls['source_unchanged'] and len(controls['cases'])==15,'Incomplete actual refusal controls')
    unavailable={'missing-primary','missing-producer','missing-workflow-context','missing-historical-context','missing-historical-producer','missing-plan'}
    for row in controls['cases']:require(row['exit_code']==row['expected']==(3 if row['case'] in unavailable else 2) and not row['complete_result'] and row['canonical_unchanged'],'Wrong refusal outcome')
    for phase in ('ui-preview-2','ui-installed-1'):
        result=obj(phase+'/COMPLETED.json');page=result['page_result'];require(result['unique_profile_processes_after']==0 and result['no_OS_process_termination'],'Owned browser not closed');require((page['integrations'],page['native'],page['upstream'],page['reviewed'],page['pending'],page['missing'],page['method_overlap'])==(159,30,129,1760,964,254,18),'Wrong rendered counts');require(page['paper_roles_checked']==['P019','P157','P170','P187','P212'] and not page['historical_2_165_qualified'] and not page['local_P212_FastQTL_run_established'] and page['raw_null_versions_preserved'] and not page['page_errors'],'Wrong rendered source limits')
    for phase in ('website-before-1','website-after-1'):
        website=obj(phase+'/VALIDATION.json');require(not website['full_checkWebsite_passed'] and len(website['preexisting_missing_citation_panels'])==33 and website['new_missing_citation_panels']==[] and website['literature_reports_withheld_by_unchanged_Pages_workflow'],'Website gate changed')
    require(obj('website-before-1/VALIDATION.json')['preexisting_missing_citation_panels']==obj('website-after-1/VALIDATION.json')['preexisting_missing_citation_panels'],'New citation failures')
    sys.path.insert(0,str(root/'tools/literature'))
    from fastqtl_catalog import catalog as c,model as m,primary as p
    expected={**c.sources(),'.gitattributes':fp(root/'.gitattributes'),'tools/literature/verify_fastqtl_catalog_checkpoint.py':fp(root/'tools/literature/verify_fastqtl_catalog_checkpoint.py')};require(c.ROOT.resolve()==root and state['source_code']==expected,'Changed source context')
    proof=json.loads((root/m.PROOF).read_bytes());require(proof['source_code']==c.sources() and proof['base_commit']==c.BASE and proof['status']==c.STATUS,'Changed admission proof')
    for name,identity in proof['evidence'].items():require(fp(root/name)==identity,'Changed installed admission evidence '+name)
    before=c.read_before(root/c.FOLDER/'metadata-before.zip');c.baseline(before);require(proof['before']=={n:p.pin(v) for n,v in before.items()},'Changed exact baseline archive');q=m.qualification();require(proof['qualification']==p.pin(m.encoded(q)) and (root/c.FOLDER/'QUALIFICATION.json').read_bytes()==m.encoded(q),'Changed immutable workflow qualification');after={n:(root/n).read_bytes() for n in m.META};counts=m.check(before,after,p.frozen(),q);require(counts==proof['result']==state['counts'] and proof['after']=={n:p.pin(v) for n,v in after.items()},'Changed installed source-bound catalog');require((root/c.FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and len(m.registry())==485 and c.clean_manifest()['entries']==871,'Changed complete registry/catalog manifest')
    # The archived old source/metadata context is immutable. Current catalog
    # edits intentionally do not pretend to satisfy its pre-admission hashes.
    context=obj('predecessor-context-1/FILES.json')
    for name,identity in context.items():require(pin(raw('predecessor-source/'+name))==identity,'Changed archived pre-admission source')
    old_state=json.loads(raw('predecessor-source/docs/literature/fastqtl-workflow-checkpoint/STATUS.json'))
    for name,identity in old_state['source_code'].items():require(pin(raw('predecessor-source/'+name))==identity,'Archived workflow source binding differs')
    for name,identity in old_state['canonical_unchanged'].items():require(pin(before[name])==identity,'Historical canonical state differs')
    if jar is not None:require(fp(jar.resolve())==state['producer'],'Restore exact complete producer JAR, never refresh its old hash')
    checked=0
    if private_root is not None:
        for name,identity in json.loads((folder/'PRIVATE-FILES.json').read_bytes()).items():require(fp(private_root/name)==identity,'Changed/missing retained catalog evidence '+name);checked+=1
    fresh=0
    if cache is not None:_,actual=p.audit(cache,before[m.META[0]]);fresh=actual['primary_XML_checked']
    workflow=None
    if workflow_source_root is not None:
        require(all(v is not None for v in (jar,historical_root,historical_jar,cache)),'Supply exact JAR, cache and historical source/producer for immutable-reader execution')
        physical=workflow_source_root.resolve()
        for name,identity in context.items():require(fp(physical/name)==identity,'Changed physical workflow context '+name)
        gitdir=c.git('rev-parse','--absolute-git-dir').decode().strip();env=dict(os.environ,GIT_DIR=gitdir,GIT_WORK_TREE=str(physical),PYTHONDONTWRITEBYTECODE='1');command=[sys.executable,'-B',str(physical/'tools/literature/verify_fastqtl_workflow_checkpoint.py'),'--root',str(physical),'--jar',str(jar.resolve()),'--cache',str(cache.resolve()),'--predecessor-root',str(historical_root.resolve()),'--predecessor-jar',str(historical_jar.resolve())]
        if workflow_private_root is not None:command+=['--private-root',str(workflow_private_root.resolve())]
        result=subprocess.run(command,cwd=physical,env=env,capture_output=True);require(result.returncode==0,'Unchanged immutable reader failed: '+result.stderr.decode(errors='replace'));workflow=json.loads(result.stdout)
    return dict(status=state['status'],counts=counts,complete_manifest_entries=871,current_catalog_and_source_checked=True,exact_JAR_checked=jar is not None,catalog_private_files_checked=checked,fresh_primary_XML_checked=fresh,archived_pre_admission_source_files_checked=len(context),immutable_workflow_reader_executed=workflow is not None,workflow_readback=workflow,scientific_analysis_reexecuted=False,catalog_admitted=True,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    for name in ('jar','private-root','workflow-source-root','historical-root','historical-jar','cache','workflow-private-root'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    try:
        require(__debug__,'Run without -O');print(json.dumps(verify(args.root,args.jar,args.private_root,args.workflow_source_root,args.historical_root,args.historical_jar,args.cache,args.workflow_private_root),indent=2))
    except FileNotFoundError as error:print('SKIP3: missing exact permitted prerequisite: '+str(error)+'. Restore the named source/input/runtime or leave dependent verification skipped.',file=sys.stderr);raise SystemExit(3)
    except Exception as error:print('FAIL2: FastQTL catalog checkpoint invalid: '+repr(error)+'. Preserve evidence; restore exact source/metadata/producer, fix the named cause and rerun. Never refresh historical hashes to force a pass.',file=sys.stderr);raise SystemExit(2)
