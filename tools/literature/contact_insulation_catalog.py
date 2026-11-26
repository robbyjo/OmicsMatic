"""Apply/check qualified native insulation coverage; never execute a scientific analysis."""
from pathlib import Path
import argparse,json,os,sys,zipfile
import contact_insulation_model as model
from contact_primary import audit as primary_audit
from gemma_primary import encoded,pin,require
from gemma_catalog import checksum_rows,verify_checksums

ROOT=Path(__file__).resolve().parents[2]
ROLES='docs/literature/contact-insulation-source-audit/ROLES.json'
CENSUS='docs/literature/contact-insulation-source-audit/CENSUS.json'
COVERAGE=model.DATA+'/contact-insulation-source-role-coverage.json'
PROOF=model.DATA+'/contact-insulation-catalog-validation.json'

def filepin(path):return pin(Path(path).read_bytes())

def safe_relative(name):
    return isinstance(name,str)and bool(name)and ':'not in name and'\\'not in name and not name.startswith('/')and all(p not in ('','.','..')for p in name.split('/'))

def capture_namespace(root,declared):
    require(root.is_dir()and not root.is_symlink()and root.resolve()==root.absolute(),'Require canonical native capture directory')
    files=set();directories={''}
    for path in root.rglob('*'):
        require(not path.is_symlink()and path.resolve()==path.absolute(),'Linked capture member refused')
        name=path.relative_to(root).as_posix()
        if path.is_dir():directories.add(name)
        else:require(path.is_file(),'Nonregular capture member');files.add(name)
    require(directories=={'','inputs','outputs'}and files==set(declared)|{'manifest.json','COMPLETED.json'},'Missing/extra native capture file or directory')

def before(root):
    folder=root/model.ADMISSION;baseline=json.loads((folder/'BASELINE.json').read_bytes())
    for name,key in (('metadata-before.zip','metadata_archive'),('PREVIOUS-SHA256SUMS','previous_checksums'),('PREVIOUS-REGISTRY.json','previous_registry')):
        require(filepin(folder/name)==baseline[key],'Changed exact predecessor seal: '+name)
    with zipfile.ZipFile(folder/'metadata-before.zip')as z:
        require(len(z.namelist())==len(model.META)and set(z.namelist())==set(model.META),'Changed predecessor metadata namespace')
        result={name:z.read(name)for name in z.namelist()}
    require({name:pin(raw)for name,raw in result.items()}==baseline['metadata'],'Changed predecessor metadata bytes')
    return result

def source_roles(root):
    baseline=json.loads((root/model.ADMISSION/'BASELINE.json').read_bytes())
    require(filepin(root/ROLES)==baseline['source_roles'],'Changed reviewed role crosswalk')
    result=json.loads((root/ROLES).read_bytes())
    require(result['new_source_role_reviews']==7 and result['existing_source_reviews_expanded']==1 and result['original_observations_preserved'],'Incorrect existing/new review attribution')
    return result

def private_controls(root,private,q):
    home=private/'native-adapter-integrity-1'
    require((root/model.Q).read_bytes()==(home/'QUALIFICATION.json').read_bytes(),'Public and retained execution qualification differ')
    model.qualified(q)
    producer=private/'native-project-build-Windows-2/project-build/cli/omicsmatic-0.4.1.jar'
    require(filepin(producer)==q['producer'],'Changed exact normal-source executable')
    require(filepin(private/'native-project-build-Windows-2/SOURCES.json')==q['source_manifest'],'Changed exact source-build context')
    for name,expected in q['selected_public_sources'].items():
        require(safe_relative(name),'Unsafe scientific source member')
        require(filepin(root/name)==expected==filepin(private/'native-project-build-Windows-2/source'/name),'Published scientific source differs from its normal build: '+name)
    controls=json.loads((home/'PRIVATE-CONTROLS.json').read_bytes())
    for name,expected in controls.items():
        path=Path(name);require(safe_relative(name),'Unsafe retained receipt member')
        require(filepin(private/path)==expected,'Changed retained execution receipt: '+name)
    for host in ('Windows','Linux'):
        capture=private/('native-sourcebuilt-capture-'+host+'-1/capture')
        require(not(capture/'INCOMPLETE.json').exists()and not(capture/'FAILURE.json').exists(),'Incomplete/failed native capture is not admitted')
        done=json.loads((capture/'COMPLETED.json').read_bytes());manifest=json.loads((capture/'manifest.json').read_bytes())
        capture_namespace(capture,manifest['files'])
        require(done['manifest']==filepin(capture/'manifest.json')and manifest['producer']==q['producer'],'Capture completion/producer differs')
        require(done['analysis_admitted']and not done['whole_package_admitted'],'Expanded capture admission')
        for name,expected in manifest['files'].items():
            require(safe_relative(name),'Unsafe capture member')
            require(filepin(capture/name)==expected,'Changed complete native capture member')
        saved=json.loads((private/('native-sourcebuilt-saved-controls-'+host+'-1/COMPLETED.json')).read_bytes())
        require([row['exit_code']for row in saved['controls']]==[0]+[2]*8+[3],'Missing portable, corruption, resource or skipped-prerequisite controls')

def coverage(roles):
    return dict(schema_version=1,roles=[model.applied(role)for role in roles['roles']],new_native_family_credit=1,new_upstream_family_credit=0,new_source_role_reviews=7,existing_source_review_expanded=1,ALLCools_implementation_qualified=False,full_author_roles_implemented=False,whole_goal_complete=False)

def check(root,artifact_root):
    root,artifact_root=Path(root).absolute(),Path(artifact_root).absolute();old=before(root);roles=source_roles(root);q=json.loads((root/model.Q).read_bytes())
    baseline=json.loads((root/model.ADMISSION/'BASELINE.json').read_bytes());require(filepin(root/model.Q)==baseline['qualification'],'Qualification differs from preserved admission baseline')
    result=model.check(old,{name:(root/name).read_bytes()for name in model.META},roles,q)
    previous=json.loads((root/model.ADMISSION/'PREVIOUS-REGISTRY.json').read_bytes())
    require(json.loads((root/model.ADMISSION/'REGISTRY.json').read_bytes())==model.registry(previous),'Different effective provider registry')
    require(json.loads((root/COVERAGE).read_bytes())==coverage(roles),'Different source-role coverage')
    proof=json.loads((root/PROOF).read_bytes())
    require(proof['status']=='PASS_SCOPED_NATIVE_CONTACT_INSULATION_CATALOG_ADMISSION'and proof['result']==result
        and proof['qualification']==filepin(root/model.Q)and proof['primary_census']==filepin(root/CENSUS),'Different qualification, primary census or catalog proof')
    prior=checksum_rows((root/model.ADMISSION/'PREVIOUS-SHA256SUMS').read_bytes());rows=checksum_rows((root/model.DATA/'SHA256SUMS').read_bytes())
    require(len(prior)==885 and len(rows)==887 and set(rows)==set(prior)|{Path(COVERAGE).name,Path(PROOF).name},'Changed checksum population')
    allowed={Path(name).name for name in model.META if name.startswith(model.DATA+'/')}
    require(all(rows[name]==digest or name in allowed for name,digest in prior.items()),'Unrelated checksum refreshed')
    verify_checksums(root,artifact_root,rows)
    return result

def apply(root,private,cache,out):
    require(root.resolve()==root and private.resolve()==private and out.is_relative_to(private)and out.parent.resolve()==out.parent and not out.exists(),'Canonical roots and NEW receipt directory required')
    old=before(root);require(all((root/name).read_bytes()==raw for name,raw in old.items()),'Current metadata changed; preserve local edits')
    prior=(root/model.ADMISSION/'PREVIOUS-SHA256SUMS').read_bytes();require((root/model.DATA/'SHA256SUMS').read_bytes()==prior,'Current checksum bytes changed')
    verify_checksums(root,root,checksum_rows(prior));primary=primary_audit(root,cache);roles=source_roles(root);q=json.loads((root/model.Q).read_bytes());private_controls(root,private,q)
    baseline=json.loads((root/model.ADMISSION/'BASELINE.json').read_bytes());require(filepin(root/model.Q)==baseline['qualification'],'Qualification changed before admission')
    after=model.transition(old,roles,q);result=model.check(old,after,roles,q)
    require(not(root/PROOF).exists()and not(root/COVERAGE).exists()and not(root/model.ADMISSION/'STATUS.json').exists(),'Admission already exists; preserve it')
    previous=json.loads((root/model.ADMISSION/'PREVIOUS-REGISTRY.json').read_bytes());registry=model.registry(previous)
    out.mkdir();(out/'PLAN.json').write_bytes(encoded(dict(before={name:pin(raw)for name,raw in old.items()},after={name:pin(raw)for name,raw in after.items()},qualification=filepin(root/model.Q),result=result,primary=primary)))
    for index,(name,raw)in enumerate(after.items()):
        target=root/name;require(target.resolve()==target and target.read_bytes()==old[name],'Metadata changed during application: '+name)
        staged=out/('staged-'+str(index));staged.write_bytes(raw);os.replace(staged,target)
    (root/COVERAGE).write_bytes(encoded(coverage(roles)))
    (root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_SCOPED_NATIVE_CONTACT_INSULATION_CATALOG_ADMISSION',result=result,qualification=filepin(root/model.Q),primary_census=filepin(root/CENSUS),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False)))
    (root/model.ADMISSION/'REGISTRY.json').write_bytes(encoded(registry));rows=checksum_rows(prior)
    for name in [name for name in model.META if name.startswith(model.DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=filepin(root/name)['sha256']
    newline='\r\n'if prior.count(b'\n')==prior.count(b'\r\n')else'\n';staged=out/'new-SHA256SUMS';staged.write_bytes(''.join(digest+'  '+name+newline for name,digest in sorted(rows.items())).encode());os.replace(staged,root/model.DATA/'SHA256SUMS')
    require(check(root,root)==result,'Final admission readback differs')
    status=dict(schema_version=1,status='PASS_SCOPED_NATIVE_CONTACT_INSULATION_CATALOG_ADMISSION',result=result,checksum_entries=887,current_checksum_manifest=filepin(root/model.DATA/'SHA256SUMS'),effective_provider_registry=filepin(root/model.ADMISSION/'REGISTRY.json'),raw_process_runtime_cleanup_receipts_published=False,website_reports_published=False,whole_goal_complete=False)
    (root/model.ADMISSION/'STATUS.json').write_bytes(encoded(status));(out/'COMPLETED.json').write_bytes(encoded(status));return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--artifact-root',type=Path);parser.add_argument('--apply',action='store_true');parser.add_argument('--private-root',type=Path);parser.add_argument('--cache',type=Path);parser.add_argument('--out',type=Path);args=parser.parse_args()
    try:
        if args.apply:
            require(all(x is not None for x in (args.private_root,args.cache,args.out)),'Explicit private/cache/NEW output required')
            result=apply(args.root.absolute(),args.private_root.absolute(),args.cache.absolute(),args.out.absolute())
        else:result=check(args.root.absolute(),(args.artifact_root or args.root).absolute())
        print(json.dumps(result,sort_keys=True));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
        print(('SKIP3 DEPENDENT CATALOG ADMISSION SKIPPED: 'if code==3 else'FAIL2 NO CATALOG ADMISSION: ')+str(error)+'. Required action: restore exact permitted sources, XMLs, producer and execution receipts. Preserve old hashes, before/after snapshots and partial attempts; do not overwrite later local edits or claim a complete paper/tool.',file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
