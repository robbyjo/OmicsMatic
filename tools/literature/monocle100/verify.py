"""Verify retained Monocle1.0.0 source/control bytes without Git, Java, R or XML execution."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,re,sys,tarfile,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def parts(name):
    path=PurePosixPath(name);require(bool(name) and not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe retained path');return path.parts

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
    require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    root=Path(__file__).resolve().parents[3];out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);folder=root/'docs/literature/monocle100-weights-checkpoint'
    try:
        profile=json.loads((folder/'PROFILE.json').read_bytes());require(profile['status']=='PASS_MONOCLE100_NATIVE_WEIGHT_STAGE_AND_SOURCE_CHECKED_BRIDGE','Different checkpoint scope')
        for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged']}.items():require(pin(root.joinpath(*parts(name)).read_bytes())==wanted,'Changed current source/canonical bytes: '+name)
        for name,wanted in profile['packet_files'].items():require(pin(folder.joinpath(*parts(name)).read_bytes())==wanted,'Changed retained packet file: '+name)
        inventory=json.loads((folder/'INVENTORY.json').read_bytes());blobs={row['sha256'] for row in inventory.values()}
        with zipfile.ZipFile(folder/'checks.zip') as archive:
            names=archive.namelist();require(len(names)==len(set(names))==len(blobs) and set(names)=={'blobs/'+sha for sha in blobs},'Different packet population');total=0
            for sha in blobs:
                require(re.fullmatch('[0-9a-f]{64}',sha) is not None,'Invalid blob identity');size=archive.getinfo('blobs/'+sha).file_size;total+=size
                require(size<=64*1024*1024 and total<=256*1024*1024,'Packet exceeds declared capacity');require(hashlib.sha256(archive.read('blobs/'+sha)).hexdigest()==sha,'Changed saved blob')
            def raw(name):
                row=inventory[name];value=archive.read('blobs/'+row['sha256']);require(pin(value)==row,'Changed retained length');return value
            def read(name):return json.loads(raw(name))
            for name in inventory:
                relative=parts(name)
                if args.restore:
                    target=out/'evidence'/Path(*relative);target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as stream:stream.write(raw(name))
            current='current/'
            normal=read(current+'normal-gradle-3/RESULT.json');require(normal['status']=='PASS_NORMAL_JAR_MONOCLE100_SOURCE_CONTROLS' and normal['tests']==636,'Different normal producer qualification')
            packaged=read(current+'packaged-1/RESULT.json');require(packaged['status']=='PASS_PACKAGED_MONOCLE100_636_TESTS_THREE_JVMS' and packaged['JAR']==normal['producer'],'Different packaged producer')
            require(set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(row['exit_code']==0 and row['tests']==636 for row in packaged['hosts'].values()),'Incomplete JVM population')
            comparison=read(current+'packaged-1/JAR-COMPARISON.json');require(comparison['preceding_payloads_unchanged']==comparison['preceding_payloads']==22378 and not comparison['changed_preceding_payloads'] and len(comparison['new_payloads'])==5,'Changed preceding scientific payloads')
            original=read(current+'original-weights-5/RESULT.json');require((original['cases'],original['returned'],original['errors'])==(33,25,8) and original['original_functions_unchanged'] and original['RNG_identical_all'],'Different original controls')
            controls=read(current+'original-weights-5/cases.json');tokens=read(current+'exact-weight-export-1/weights-exact-tokens.json');fixture=json.loads((root/'src/test/resources/org/jlinalg/singlecell/monocle100/weights.json').read_bytes())
            require(len(controls)==len(fixture)==33 and len(tokens)==25,'Different control populations')
            for name,case in controls.items():
                wanted=json.loads(json.dumps(case))
                if case['reference']['returned']:wanted['reference']['weights_exact_tokens']=tokens[name]
                require(fixture[name]==wanted,'Changed exact source control or binary-weight projection')
            runtime=read(current+'install-1/RESULT.json');require(runtime['status']=='READY_EXACT_ORIGINAL_MONOCLE100_NAMESPACE_NOT_ANALYSIS' and runtime['runtime_admitted'] and runtime['preexisting_protected_files_unchanged'] and not runtime['scientific_analysis_admitted'],'Different exact original runtime scope')
            require(raw(current+'install-1/PROTECTED-BEFORE.json')==raw(current+'install-1/PROTECTED-AFTER.json'),'Changed preceding protected files')
            readiness=read(current+'install-1/READINESS.json');require(readiness['monocle3']=='1.0.0' and len(readiness['guarded_functions'])==11,'Different original namespace guard')
            bridge=read(current+'original-bridge-2/REFERENCE.json');require(bridge['official_function_body_formals_identical'] and bridge['cells']==27 and bridge['genes']==3,'Missing original spdep source guard')
            spdep=json.loads((root/'src/test/resources/org/jlinalg/spatial/regression-moran/PROVENANCE.json').read_bytes())['upstream_source']
            require(pin(raw(current+'spdep-source-1/spdep_1.4-1.tar.gz'))=={key:spdep[key] for key in ['bytes','sha256']},'Changed original spdep archive')
            with tarfile.open(fileobj=io.BytesIO(raw(current+'spdep-source-1/spdep_1.4-1.tar.gz')),mode='r:gz') as tar:
                original_function=tar.extractfile('spdep/R/lm.morantest.R').read()
                require(pin(original_function)==spdep['files']['spdep/R/lm.morantest.R'] and original_function==raw(current+'spdep-source-1/lm.morantest.R'),'Changed exact original spdep primitive')
            examples=read(current+'examples-1/RESULT.json');require(examples['status']=='PASS_THREE_HOST_PUBLIC_WEIGHT_TO_MORAN_EXAMPLES' and set(examples['hosts'])==set(packaged['hosts']),'Incomplete actual examples')
            for row in examples['hosts'].values():require(row['weights_binary64_identical'] and row['arcs']==74 and row['original_spdep_fields_compared']==21 and all(item['exit_code']==0 for item in row['commands'].values()),'Different actual bridge scope')
            context=read(current+'context-1/RESULT.json');require(context['status']=='PASS_FRESH_MONOCLE9_AND_RETAINED_CATALOG_BOTH_HOSTS_WITH_THREE_SETUP_REFUSALS' and context['source_papers_per_host']==250 and context['source_roles']==9 and len(context['setup_controls'])==3,'Different fresh context')
            locked=json.loads((root/'tools/literature/monocle100/source-profile.json').read_bytes());source=raw('upstream/monocle3-source.tar.gz');require(pin(source)==locked['archive'],'Different original source archive')
            with tarfile.open(fileobj=io.BytesIO(source),mode='r:gz') as tar:
                actual={}
                for member in tar.getmembers():
                    pieces=parts(member.name);require(pieces[0]=='monocle3-'+locked['commit'],'Different original source prefix')
                    if member.isdir():continue
                    require(member.isfile() and not member.issym() and not member.islnk() and member.size<=32*1024*1024,'Unsupported original source member')
                    name=PurePosixPath(*pieces[1:]).as_posix();require(name not in actual,'Duplicate original source member');actual[name]=pin(tar.extractfile(member).read())
                require(actual==locked['source_members'] and len(actual)==166,'Different complete source inventory')
        data=root/'docs/literature/omics-2023-2026';seen=set()
        for line in (data/'SHA256SUMS').read_text().splitlines():
            sha,name=line.split('  ',1);path=(data/name).resolve();require(path.is_relative_to(root/'docs/literature') and name not in seen and pin(path.read_bytes())['sha256']==sha,'Changed catalog manifest');seen.add(name)
        require(len(seen)==838,'Different preserved catalog population')
        result=dict(status='PASS_SAVED_MONOCLE100_WEIGHT_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(blobs),tests_per_JVM=636,JVMs=3,new_API_CLI_tests=77,original_cases=33,original_returned=25,original_errors_retained=8,preceding_JAR_payloads_exact=22378,actual_three_host_bridge_examples=True,fresh_source_papers_per_host=250,source_roles=9,scoped_catalog_families=144,reviewed=1630,pending=1092,manifest_entries=838,new_catalog_credit=0,Git_Java_R_XML_or_scientific_models_executed=False,whole_goal_complete=False)
        (out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result))
    except BaseException as error:
        (out/'FAILURE.txt').write_text(repr(error)+'\n',encoding='utf-8',newline='\n');raise

if __name__=='__main__':main()
