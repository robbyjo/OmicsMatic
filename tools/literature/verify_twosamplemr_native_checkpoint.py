"""Verify saved native TwoSampleMR qualification bytes, without executing analyses."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys,zipfile
def require(ok,message):
    if not ok:raise ValueError(message)
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def safe(name):
    value=PurePosixPath(name);require(not value.is_absolute() and '..' not in value.parts and '\\' not in name,'Unsafe saved path');return value.parts
def verify(root):
    q=root/'docs/literature/twosamplemr-native-checkpoint';profile=json.loads((q/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_NATIVE_TWOSAMPLEMR638_AND14_SOURCE_PROPOSALS_NOT_CATALOG_ADMISSION' and profile['new_catalog_credit']==0 and not profile['whole_goal_complete'],'Different qualification scope')
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged']}.items():require(pin(root.joinpath(*safe(name)))==wanted,'Qualified source/canonical bytes differ: '+name)
    require(pin(q/'checks.zip')==profile['archive'] and pin(q/'INVENTORY.json')==profile['inventory'] and pin(q/'reference-source.zip')==profile['reference_archive'],'Changed saved packet')
    inventory=json.loads((q/'INVENTORY.json').read_bytes());unique={v['sha256'] for v in inventory.values()}
    with zipfile.ZipFile(q/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(unique) and set(names)=={'blobs/'+sha for sha in unique},'Different saved member population');total=0
        for sha in unique:
            require(re.fullmatch('[0-9a-f]{64}',sha) is not None,'Invalid blob name');size=archive.getinfo('blobs/'+sha).file_size;total+=size
            require(size<=64*1024*1024 and total<=256*1024*1024,'Saved capacity exceeded');require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed saved blob')
        for name,wanted in inventory.items():safe(name);require(len(archive.read('blobs/'+wanted['sha256']))==wanted['bytes'],'Different saved length')
        def read(name):return json.loads(archive.read('blobs/'+inventory[name]['sha256']))
        original=read('current/original-controls-1/RESULT.json');require(original['cases']==63 and original['method_calls']==630 and original['bootstrap_RNG_end_state_and_replicate_SD_checked'],'Different original controls')
        build=read('current/normal-gradle-2/COMPLETED.json');require(build['exit_code']==0 and sum(v['tests'] for v in build['reports'].values())==638 and not any(v[k] for v in build['reports'].values() for k in ['failures','errors','skipped']),'Incomplete normal producer/tests')
        packaged=read('current/packaged-1/RESULT.json');require(packaged['status']=='PASS_PACKAGED_TWOSAMPLEMR638_TESTS_THREE_JVMS' and set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(v['exit_code']==0 and v['tests']==638 and v['skipped']==0 for v in packaged['hosts'].values()),'Incomplete packaged hosts')
        comparison=read('current/packaged-1/JAR-COMPARISON.json');require(comparison['published_predecessor_payloads_unchanged']==22297 and comparison['legacy_MR_and_global_dispatch_unchanged'],'Changed preceding JAR payloads')
        for host in ['windows','linux']:
            source=read('current/source-controls-'+host+'-1/RESULT.json');require(source['positive_papers']==14 and source['papers']==250 and source['semantic_refusals']==16,'Different source control scope')
        require(read('current/examples-1/RESULT.json')['status']=='PASS_NATIVE_MR_PUBLIC_EXAMPLE_THREE_JVMS_AND_UNMODIFIED_ORIGINAL','Incomplete actual example')
        require(read('current/preceding-catalog-1/COMPLETED.json')['result']['integrations']==143,'Different preceding catalog')
    census=json.loads((root/'docs/literature/twosamplemr-source-review-proposal/CENSUS.json').read_bytes())
    require(census['positive_software_papers']==14 and census['new_family_credit']==census['new_canonical_reviews']==0 and not any(r['paper_edition_numerically_qualified'] for r in census['roles']),'Source proposals cannot qualify paper editions or award credit')
    with zipfile.ZipFile(q/'reference-source.zip') as archive:
        expected=profile['reference_members'];require(len(archive.namelist())==len(set(archive.namelist()))==len(expected) and set(archive.namelist())==set(expected),'Different reference-source population')
        for name,wanted in expected.items():
            raw=archive.read(name);require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==wanted,'Changed pinned reference source')
    data=root/'docs/literature/omics-2023-2026';members={}
    for line in (data/'SHA256SUMS').read_text().splitlines():
        sha,name=line.split('  ',1);target=(data/name).resolve();require(target.is_relative_to(root/'docs/literature') and name not in members,'Unsafe/duplicate catalog manifest member');require(pin(target)['sha256']==sha,'Changed catalog member: '+name);members[name]=sha
    require(len(members)==835,'Different catalog manifest population')
    return dict(status=profile['status'],logical_files=len(inventory),unique_blobs=len(unique),tests_per_JVM=638,JVMs=3,source_cases=63,original_method_calls=630,source_role_proposals=14,source_refusals_per_host=16,source_hosts=2,preceding_JAR_payloads_unchanged=22297,canonical_files_unchanged=27,manifest_entries=835,scoped_integrations=143,reviewed_uses=1616,pending_uses=1104,new_catalog_credit=0,global_CLI_registered=False,historical_paper_editions_qualified=False,R_RNG_generated_by_Java=False,fresh_Java_R_Git_XML_or_model_execution=False,whole_goal_complete=False)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');args.out.mkdir(parents=True,exist_ok=False)
    result=verify(Path(__file__).resolve().parents[2]);(args.out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
if __name__=='__main__':main()
