"""Check native fdensity source, actual-reference captures and qualification bytes; run no models."""
from pathlib import Path,PurePosixPath
import argparse,gzip,hashlib,json,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3];FOLDER='docs/literature/qtltools-fdensity-development'
def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def safe(name):
    path=PurePosixPath(name);require(name and not path.is_absolute() and all(part not in {'.','..'} and ':' not in part and '\\' not in part for part in path.parts),'Unsafe retained path');return path.parts
def scope(profile):
    require(profile['status']=='PASS_NATIVE_QTLTOOLS_FDENSITY_API' and profile['API_only'] is True and profile['CLI_implemented'] is False and profile['source_order_guard_preserved'] is True,'Different native API/source-order scope')
    require(profile['new_catalog_credit']==0 and profile['whole_tool_qualified'] is False and profile['whole_goal_complete'] is False,'False family/whole-tool/goal credit')
    require((profile['cases'],profile['successful_cases'],profile['retained_original_errors'],profile['output_bins'],profile['tests_each_JVM'])==(576,569,7,2838,588),'Different reference/test population')
def verify(out,restore=False,jar=None):
    folder=ROOT/FOLDER;profile=json.loads((folder/'PROFILE.json').read_bytes());scope(profile)
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged']}.items():require(pin(ROOT.joinpath(*safe(name)).read_bytes())==wanted,'Changed source/canonical bytes: '+name)
    for name,wanted in profile['files'].items():require(pin(folder.joinpath(*safe(name)).read_bytes())==wanted,'Changed packet file: '+name)
    if jar is not None:require(pin(jar.read_bytes())==profile['producer'],'Supply the exact qualified normal JAR')
    members=json.loads((folder/'INDEX.json').read_bytes());require(len(members)<=10000,'Too many retained members')
    with zipfile.ZipFile(folder/'controls.zip') as z:
        require(len(z.namelist())==len(members) and set(z.namelist())==set(members),'Different complete control archive population');require(all(info.file_size<=64*1024*1024 for info in z.infolist()) and sum(info.file_size for info in z.infolist())<=256*1024*1024,'Control archive exceeds capacity')
        for name,wanted in members.items():
            parts=safe(name);raw=z.read(name);require(pin(raw)==wanted,'Changed retained member: '+name)
            if restore:
                target=out/'captured'/Path(*parts);target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as f:f.write(raw)
        def read(name):return json.loads(z.read(name))
        reference=read('original-controls-3/RESULT.json');provenance=json.loads((ROOT/profile['fixture_provenance']).read_bytes());require(reference==provenance,'Changed reference/fixture binding')
        old=read('original-controls-1/RESULT.json');parent=read('original-controls-2/RESULT.json');byid={row['id']:row for row in parent['records']}
        for row in old['records']:require(byid[row['id']]==row,'An initial scientific record was changed')
        require(pin(z.read('original-controls-2/RESULT.json'))=={key:reference['parent'][key] for key in ['bytes','sha256']},'Different retained reference parent')
        fixture=(ROOT/profile['fixture']).read_bytes();require(pin(fixture)==reference['fixture'],'Changed actual-reference fixture');records={row['id']:row for row in reference['records']};require(len(records)==len(reference['records'])==576,'Duplicate/missing reference identities')
        case=None;qtls=[];annotations=[];bins=[];seen=set();success=errors=bin_count=0
        for line in gzip.decompress(fixture).decode().splitlines():
            fields=line.split('\t')
            if fields[0]=='C':require(case is None and fields[1] not in seen,'Duplicate or nested fixture case');case=fields[1];window,width=fields[2:4];status=fields[4];qtls=[];annotations=[];bins=[]
            elif fields[0]=='Q':qtls.append('\t'.join(fields[1:])+'\n')
            elif fields[0]=='A':annotations.append('\t'.join(fields[1:])+'\n')
            elif fields[0]=='B':bins.append(' '.join(fields[1:])+'\n')
            elif fields[0]=='E':
                require(case is not None,'Fixture end without case');row=records[case];prefix=row['capture_owner']+'/'+case+'/'
                command=read(prefix+'STARTED.json')['command'];require(command[0]==reference['reference_executable']['path'] and command[1]=='fdensity','Different reference executable/operation')
                for flag,value in [('--window',window),('--bin',width)]:require(command.count(flag)==1 and command[command.index(flag)+1]==value,'Changed fixture/reference parameter binding')
                for flag,name in [('--qtl','qtl.bed'),('--bed','annotation.bed'),('--out','density.txt')]:require(command.count(flag)==1 and command[command.index(flag)+1].endswith('/'+prefix+name),'Changed reference input/output binding')
                for name,values,key in [('qtl.bed',qtls,'qtl'),('annotation.bed',annotations,'annotations')]:require(z.read(prefix+name)==''.join(values).encode() and pin(z.read(prefix+name))==row[key],'Changed complete original input')
                require(read(prefix+'PROCESS.json')['exit_code']==row['exit_code'],'Changed original process result')
                if status=='OK':
                    require(row['exit_code']==0 and row['status']=='OK' and z.read(prefix+'density.txt')==''.join(bins).encode() and pin(z.read(prefix+'density.txt'))==row['output'],'Changed complete original density result');success+=1;bin_count+=len(bins)
                else:require(status=='ERROR' and row['status']=='ORIGINAL_ERROR' and row['exit_code']!=0 and row['output'] is None and prefix+'density.txt' not in members and not bins,'Original error promoted');errors+=1
                seen.add(case);case=None
            else:raise ValueError('Unknown fixture record')
        require(case is None and len(seen)==576 and (success,errors,bin_count)==(569,7,2838),'Truncated/different complete reference population')
        normal=read('normal-gradle-2/RESULT.json');packaged=read('packaged-1/RESULT.json');require(normal['producer']==packaged['producer']==profile['producer'] and normal['tests']==packaged['tests_each_host']==588,'Different qualified JAR/tests')
        for row in normal['suites'].values():require(row['failures']==row['errors']==row['skipped']==0,'Failed normal tests promoted')
        require(set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(row['exit_code']==0 and row['tests']==588 for row in packaged['hosts'].values()),'Missing packaged host')
        comparison=read('packaged-1/JAR-COMPARISON.json');require(comparison['preceding_payloads']==comparison['preceding_payloads_unchanged']==22425 and comparison['changed_preceding_payloads']==[] and len(comparison['new_payloads'])==4,'Changed preceding scientific JAR payload')
        require(read('normal-gradle-1/PROCESS.json')['exit_code']!=0 and read('first-build/FAILURE-SCOPE.json')['failed_tests']==313,'Initial failed native build was promoted or omitted')
        example=read('example-1/RESULT.json');require(example['producer']==profile['producer'] and all(row['rows_match_original_smoke'] for row in example['hosts'].values()),'Changed public example scope')
    result=dict(status='PASS_NATIVE_QTLTOOLS_FDENSITY_SAVED_BYTES_AND_SCOPE_ONLY',cases=576,successful_original_cases=569,retained_original_errors=7,original_output_bins=2838,tests_each_packaged_JVM=588,prior_payloads_unchanged=22425,control_files_verified=len(members),API_only=True,CLI_implemented=False,new_catalog_credit=0,whole_tool_qualified=False,whole_goal_complete=False,scientific_models_executed=False,restored=restore,qualified_JAR_bytes_verified=jar is not None)
    (out/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--restore',action='store_true');p.add_argument('--jar',type=Path);args=p.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW workspace build output');out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(out,args.restore,args.jar);print(json.dumps(result));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;action='Preserve failed evidence. Supply the named exact missing file/JAR or restore changed source/archive bytes, then retry NEW output. Native fdensity needs Java only; if a required upstream reference runtime cannot be installed, skip that dependent analysis and state its exact prerequisite. No model was executed by this reader.'
        if owned:(args.out.resolve()/'FAILURE.json').write_text(json.dumps(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),required_action=action),indent=2)+'\n',encoding='utf-8')
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error)+'\n'+action,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
