"""Verify saved TwoSampleMR catalog bytes and pure catalog transition; no original analysis."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,hashlib,json,re,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def safe(name):
    path=PurePosixPath(name)
    require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe saved relative path')
    return path.parts

def verify(root,out,restore=False):
    folder=root/'docs/literature/twosamplemr-catalog-checkpoint'
    packet=json.loads((folder/'CHECKS.json').read_bytes())
    require(packet['status']=='PASS_TWOSAMPLEMR_CATALOG_CURRENT_OWNER_POSTCHECKS','Different saved checkpoint')
    for name,wanted in {**packet['source_inputs'],**packet['canonical_after']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes())==wanted,'Changed current source/evidence: '+name)
    require(pin((folder/'checks.zip').read_bytes())==packet['archive'] and pin((folder/'INVENTORY.json').read_bytes())==packet['inventory'],'Changed checkpoint archive/inventory')
    inventory=json.loads((folder/'INVENTORY.json').read_bytes());unique={row['sha256'] for row in inventory.values()}
    with zipfile.ZipFile(folder/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+sha for sha in unique},'Different archive population')
        total=0
        for sha in unique:
            require(re.fullmatch('[0-9a-f]{64}',sha) is not None,'Invalid blob identity')
            size=archive.getinfo('blobs/'+sha).file_size;total+=size
            require(size<=64*1024*1024 and total<=256*1024*1024,'Checkpoint capacity exceeded')
            require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed blob')
        for name,row in inventory.items():
            parts=safe(name);raw=archive.read('blobs/'+row['sha256']);require(pin(raw)==row,'Changed saved member')
            if restore:
                target=out/'evidence'/Path(*parts);target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw)
        def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
        for host,attempt in [('windows','1'),('linux','2')]:
            row=read('current/verify-'+host+'-'+attempt+'/COMPLETED.json')
            require(row['status']=='PASS_TWOSAMPLEMR_CUMULATIVE_ADMISSION_ONE_FAMILY_14_SOURCE_ROLES' and row['fresh_source_audit_performed'] and row['current_manifest']['entries']==838 and not row['fresh_scientific_model_execution'],'Different saved host verification')
        for mode in ['preview','installed']:
            row=read('current/'+mode+'-ui-1/browser/COMPLETED.json')
            require(row['status']=='PASS_LOCAL_TWOSAMPLEMR14_ROLES_COUNTS_DESKTOP_MOBILE_WARNINGS' and row['reviewed']==1630 and not row['page_errors'] and not row['website_published'],'Different saved UI checks')
        cli=read('current/cli-controls-1/COMPLETED.json')
        require(len(cli['results'])==6 and all(row['exit_code']==row['expected_exit'] for row in cli['results']),'Different saved CLI refusals')
        independent=read('current/independent-readback-1/RESULT.json')
        require(independent['old_reviews_exact']==1616 and independent['unrelated_registry_definitions_exact']==484 and independent['unrelated_inventory_rows_exact']==445 and independent['dispatcher_and_review_multi_step_inverses_passed'],'Different independent readback')
    # Pure metadata replay needs no Git, historical checkout, XML cache or R runtime.
    from twosamplemr_catalog import model as model,primary as primary
    data=root/model.DATA;owner=root/'docs/literature/twosamplemr-catalog-admission'
    proof=json.loads((data/'twosamplemr-catalog-validation.json').read_bytes())
    for name,wanted in {**proof['source_code'],**proof['evidence'],**proof['qualification']['proof_files']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes())==wanted,'Changed retained admission proof: '+name)
    with zipfile.ZipFile(owner/'metadata-before.zip') as archive:
        require(len(archive.namelist())==8 and set(archive.namelist())==set(model.META),'Different before metadata population')
        before={name:archive.read(name) for name in model.META}
    after={name:(root/name).read_bytes() for name in model.META}
    require(proof['before']=={n:pin(raw) for n,raw in before.items()} and proof['after']=={n:pin(raw) for n,raw in after.items()},'Changed before/after identities')
    qualification=json.loads((owner/'QUALIFICATION.json').read_bytes());require(qualification==proof['qualification'],'Different saved qualification')
    result=model.check(before,after,primary.frozen(),qualification['result']);require(result==proof['result']==packet['verified_result'],'Different pure catalog transition')
    controls=qualification['controls']
    require(controls['source_code']==proof['source_code'] and set(controls['results'])=={'windows','linux'},'Different exact-source controls')
    require(all(row['exit_code']==0 and row['tests']==30 and row['fresh_source_controls']==16 for row in controls['results'].values()),'Incomplete control hosts')
    control_path=root/'docs/literature/twosamplemr-catalog-controls/checks.zip'
    require(pin(control_path.read_bytes())==controls['archive'],'Changed control archive')
    with zipfile.ZipFile(control_path) as archive:
        require(len(archive.namelist())==len(set(archive.namelist()))==len(controls['files']) and set(archive.namelist())==set(controls['files']),'Different control members')
        for name,wanted in controls['files'].items():require(pin(archive.read(name))==wanted,'Changed control member')
    papers=json.loads((data/'papers.json').read_bytes());tools=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes())
    require(len(papers)==250 and len(tools)==446 and sum(len(row['tools']) for row in papers)==2722,'Different current populations')
    counts=Counter(row['implementation_status'] for row in tools)
    require(dict(counts)==summary['implementation_status_counts'] and counts['native-subset-validated']==26 and counts['upstream-adapter-validated']==118 and counts['validated-family-alias']==2,'Different family counts')
    members={}
    for line in (data/'SHA256SUMS').read_text().splitlines():
        sha,name=line.split('  ',1);path=(data/name).resolve()
        require(path.is_relative_to(root/'docs/literature') and name not in members and pin(path.read_bytes())['sha256']==sha,'Changed or unsafe manifest member: '+name);members[name]=sha
    require(len(members)==838,'Different catalog manifest population')
    return dict(status='PASS_SAVED_TWOSAMPLEMR_CATALOG_CHECKPOINT_PURE_METADATA_ONLY',logical_files=len(inventory),unique_blobs=len(unique),papers=250,integration_families=144,native=26,upstream=118,reviewed=1630,pending=1092,manifest_entries=838,old_reviews_exact=1616,model_transaction_tests_per_host=30,source_semantic_controls_per_host=16,actual_CLI_refusals=6,preview_and_installed_UI_pass=True,pure_catalog_transition_checked=True,Git_Java_R_primary_XML_or_scientific_models_executed=False,whole_goal_complete=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
    require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    root=Path(__file__).resolve().parents[2];out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    try:
        result=verify(root,out,args.restore);(out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
    except BaseException as error:
        (out/'FAILURE.txt').write_text(repr(error)+'\n',encoding='utf-8',newline='\n');raise

if __name__=='__main__':main()
