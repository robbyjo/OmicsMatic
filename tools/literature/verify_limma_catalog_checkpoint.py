"""Read saved limma admission bytes; no Git, Java, R, XML or model execution."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,hashlib,json,re,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def safe(name):
    p=PurePosixPath(name);require(not p.is_absolute() and '..' not in p.parts and '\\' not in name,'Unsafe saved path');return p.parts

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
    require(__debug__ and sys.version_info>=(3,11),'Use Python 3.11+ without -O')
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);root=Path(__file__).resolve().parents[2];q=root/'docs/literature/limma-catalog-checkpoint'
    try:
        packet=json.loads((q/'CHECKS.json').read_bytes());require(packet['status']=='PASS_LIMMA_ADMISSION_CURRENT_OWNER_POSTCHECKS','Different packet scope')
        for name,w in {**packet['source_inputs'],**packet['canonical_after']}.items():require(pin(root.joinpath(*safe(name)))==w,'Exact current owner/source/canonical bytes differ: '+name)
        require(pin(q/'checks.zip')==packet['archive'] and pin(q/'INVENTORY.json')==packet['inventory'],'Changed saved archive/inventory')
        inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
        with zipfile.ZipFile(q/'checks.zip') as archive:
            names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+sha for sha in unique},'Different saved member population')
            total=0
            for sha in unique:
                require(re.fullmatch('[0-9a-f]{64}',sha) is not None,'Invalid saved hash');size=archive.getinfo('blobs/'+sha).file_size;total+=size
                require(size<=64*1024*1024 and total<=256*1024*1024,'Saved packet exceeds capacity')
                require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed saved blob')
            for name,w in inventory.items():
                safe(name);raw=archive.read('blobs/'+w['sha256']);require(len(raw)==w['bytes'],'Different saved length')
                if args.restore:
                    target=out/'evidence'/name;target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as stream:stream.write(raw)
            def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
            verified=read('current/verify-fresh-1/COMPLETED.json')
            require(verified['status']=='PASS_LIMMA_CUMULATIVE_ADMISSION_ONE_FAMILY_ONE_ALIAS_23_SOURCE_ROLES' and verified['fresh_source_audit_performed'] and verified['current_manifest']['entries']==832,'Different saved admission')
            result=verified['result']
            require((result['integrations'],result['native_integrations'],result['upstream_integrations'],result['reviewed_uses'],result['pending_uses'])==(142,26,116,1598,1119) and result['alias_extra_integration_credit']==0 and result['existing_reviews_retained']==1575,'Different saved counts')
            for folder in ['preview-ui-1','installed-ui-1']:
                ui=read('current/'+folder+'/browser/COMPLETED.json')
                require(ui['status']=='PASS_LOCAL_LIMMA_ROLES_TWO_ALIASES_COUNTS_DESKTOP_MOBILE_WARNINGS' and ui['reviewed']==1598 and ui['validated_family_aliases']==2 and not ui['page_errors'] and not ui['website_published'],'Different saved UI result')
            cli=read('current/cli-controls-1/COMPLETED.json');require(len(cli['results'])==6 and all(r['exit_code']==r['expected_exit'] for r in cli['results']),'Different saved CLI controls')
            independent=read('current/independent-readback-1/RESULT.json')
            require(independent['registry_definitions']==485 and independent['unrelated_registry_definitions_exact']==483 and independent['old_reviews_exact']==1575 and independent['dispatcher_and_review_multi_step_inverses_passed'],'Different independent readback')
        data=root/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());tools=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes());reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];counts=Counter(t['implementation_status'] for t in tools)
        require(len(papers)==250 and len(tools)==446 and sum(len(p['tools']) for p in papers)==2717 and len(reviews)==1598,'Different current populations')
        require(counts['upstream-adapter-validated']==116 and counts['native-subset-validated']==26 and counts['validated-family-alias']==2 and counts['method-overlap']==31 and counts['missing']==259 and dict(counts)==summary['implementation_status_counts'],'Different current family counts')
        for label,family in [('voom','limma'),('cojo','gcta')]:
            row=next(t for t in tools if t['id']==label);require(row['family_alias_of']==family and row['new_scoped_integration_credit']==0,'Different alias ownership/credit')
        members={}
        for line in (data/'SHA256SUMS').read_text().splitlines():
            sha,name=line.split('  ',1);target=(data/name).resolve();require(target.is_relative_to(root/'docs/literature'),'Manifest path escapes literature evidence')
            require(name not in members and pin(target)['sha256']==sha,'Changed/duplicate catalog manifest member: '+name);members[name]=sha
        require(len(members)==832,'Different manifest population')
        output=dict(status='PASS_SAVED_LIMMA_CATALOG_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(unique),integration_families=142,validated_aliases_excluded_from_extra_credit=2,observations=2717,reviewed=1598,pending=1119,manifest_entries=832,model_transaction_tests_per_host=18,source_semantic_controls_per_host=15,control_hosts=2,actual_CLI_refusals=6,preview_and_installed_UI_pass=True,Git_Java_R_primary_XML_or_models_executed=False,whole_goal_complete=False)
        (out/'RESULT.json').write_text(json.dumps(output,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(output))
    except BaseException as error:
        (out/'FAILURE.txt').write_text(repr(error)+'\n',encoding='utf-8',newline='\n');raise

if __name__=='__main__':main()
