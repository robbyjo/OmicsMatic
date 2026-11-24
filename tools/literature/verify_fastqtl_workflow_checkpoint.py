"""Check saved prepared-FastQTL qualification; optional private bytes/primary census, no model refit."""
from pathlib import Path
import argparse,csv,hashlib,io,json,math,os,subprocess,sys,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)
def file_pin(path):
    h=hashlib.sha256();size=0
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=h.hexdigest())

def verify(root,jar=None,private_root=None,cache=None,predecessor_root=None,predecessor_jar=None):
    root=root.resolve()
    # The historical reader runs with its own physical source directory as cwd.
    # Bind caller-relative prerequisites before starting that subprocess.
    jar=jar.resolve() if jar is not None else None
    private_root=private_root.resolve() if private_root is not None else None
    cache=cache.resolve() if cache is not None else None
    predecessor_root=predecessor_root.resolve() if predecessor_root is not None else None
    predecessor_jar=predecessor_jar.resolve() if predecessor_jar is not None else None
    folder=root/'docs/literature/fastqtl-workflow-checkpoint';state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='PASS_PREPARED_FASTQTL_RUNTIME_PENDING_CATALOG' and not state['catalog_admitted'] and not state['whole_goal_complete'],'Expanded qualification/admission claim')
    require(state['new_family_credit']==state['new_reviews']==0 and state['historical_2_165_qualified'] is False,'Expanded family/version claim')
    for name,key in [('FILES.json','manifest'),('evidence.zip','archive'),('PRIVATE-FILES.json','private_inventory'),('OMISSIONS.json','omissions')]:require(file_pin(folder/name)==state[key],'Changed checkpoint '+name)
    files=json.loads((folder/'FILES.json').read_bytes())
    with zipfile.ZipFile(folder/'evidence.zip') as z:
        names=z.namelist();require(len(names)==len(set(names))==state['unique_objects'] and set(names)=={'objects/'+v['sha256'] for v in files.values()},'Changed archive objects');require(sum(v.file_size for v in z.infolist())<64000000,'Oversized saved packet');objects={n:z.read(n) for n in names}
    require(len(files)==state['logical_files'],'Changed saved population')
    for name,identity in files.items():require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name and ':' not in name,'Unsafe saved name');require(pin(objects['objects/'+identity['sha256']])==identity,'Changed saved bytes: '+name)
    def raw(name):return objects['objects/'+files[name]['sha256']]
    def obj(name):return json.loads(raw(name))
    for name,identity in state['source_code'].items():require(file_pin(root/name)==pin(raw('source/'+name))==identity,'Changed qualified source: '+name)
    for name,identity in state['canonical_unchanged'].items():require(file_pin(root/name)==identity,'Catalog changed; preserve this checkpoint and create a new admission context')
    build=obj('normal-build-3/RESULT.json');require(build['exit_code']==0 and build['source_unchanged'] and build['jar']==state['producer'],'Missing normal exact producer build')
    for name,identity in state['compiled_sources'].items():require(obj('normal-build-3/SOURCE-INPUTS.json')[name]==identity==file_pin(root/name),'Compiled source differs: '+name)
    tests=state['tests'];require(tests==dict(FastqtlWorkflowTest=8,ImputeWorkflowTest=9,OwnedProcessesTest=13),'Changed test population')
    import xml.etree.ElementTree as E
    for name,count in tests.items():
        suite=E.fromstring(raw('normal-build-3/tests/TEST-org.jlinalg.cli.'+name+'.xml'));require(int(suite.get('tests'))==count and all(int(suite.get(k))==0 for k in ('failures','errors','skipped')),'Failed/skipped Java test suite')
    q=obj('qualified-checks-1/RESULT.json');require(q['status']=='PASS_EIGHT_ORIGINAL_COMPARISONS_TWO_FRESH_REFITS_FIFTEEN_REFUSALS' and q['jar']==state['producer'] and len(q['cases'])==8 and len(q['refits'])==2 and len(q['controls'])==15,'Incomplete actual CLI qualification')
    for row in q['cases']:
        label=row['case'];base='qualified-checks-1/'+label+'-capture/';request=obj(base+'canonical-request.json');manifest=obj(base+'manifest.json');seal=obj(base+'COMPLETED.json');summary=obj(base+'scientific-audit.json')
        require(all(row[key]['exit_code']==0 for key in ('actual','direct','inspect')) and row['byte_identical_direct_original_output'],'Failed original/adapter/inspect comparison')
        require(raw(base+'worker/result.txt')==raw('qualified-checks-1/'+label+'-direct/result.txt'),'Original/adapter scientific bytes differ')
        require(seal['manifest']==pin(raw(base+'manifest.json')) and manifest['scientific_summary']==summary==row['scientific_summary'] and summary['nonfinite_rows']==0,'Changed scientific completion')
        for name,identity in manifest['inventory']['files'].items():require(pin(raw(base+name))==identity,'Changed completed capture member')
        lines=raw(base+'worker/result.txt').decode('ascii').splitlines();require(len(lines)==summary['rows'],'Wrong derived result count')
        nulls=0
        for line in lines:
            fields=line.split('\t');permutation=request['operation']=='PERMUTATION';require(len(fields)==(17 if permutation else 9),'Wrong source schema')
            if permutation and fields[1]=='0':require(fields[2:]==['NA']*15,'Changed no-cis result');nulls+=1
            else:
                skip={0,6} if permutation else {0,1}
                require(all(math.isfinite(float(v)) for i,v in enumerate(fields) if i not in skip),'Nonfinite result admitted')
        require(nulls==summary['no_cis_variant_rows'],'Wrong no-result summary')
    require(q['cases'][4]['scientific_summary']['rows']==0 and q['cases'][5]['scientific_summary']['no_cis_variant_rows']==1,'Missing empty/no-cis controls')
    for row in q['refits']:require(row['actual']['exit_code']==0 and row['parent_unchanged'] and row['comparison']['new_original_computation_executed'] and len(row['comparison']['matched_files'])==5,'No fresh original computation')
    missing={'missing-input','missing-runtime','historical-edition','wrong-runtime','missing-library'}
    for row in q['controls']:require(row['actual']['exit_code']==row['expected']==(3 if row['case'] in missing else 2),'Wrong actual refusal status')
    process=obj('qualified-checks-1/owned-timeout-capture/worker-process.json');require(process['owned_processes'] and all(not p['same_identity_alive'] for p in process['owned_processes']),'Owned timed-out worker still alive at recorded close')
    require(obj('qualified-checks-1/nonfinite-source-capture/scientific-audit.json')['nonfinite_rows']>0 and b'Unreportable/changed scientific summary' in raw('qualified-checks-1/forged-nonfinite-inspect.stderr'),'Numerical failure/metadata-forgery refusal missing')
    r=list(csv.DictReader(io.StringIO(raw('R-reference-2/comparisons.tsv').decode()),delimiter='\t'));require(len(r)==240,'Incomplete independent R comparison')
    for row in r:
        actual,expected=float(row['source']),float(row['R_lm']);require(math.isfinite(actual) and math.isfinite(expected) and abs(actual-expected)<=1e-12+5e-5*abs(expected),'Independent nominal reference differs')
    require(b'PASS 240 independent nominal statistics' in raw('R-reference-2/RESULT.txt'),'Missing R pass')
    platform=obj('platform-checks-1/RESULT.json');require(platform['status']=='PASS_NATIVE_WINDOWS_SKIPS_AND_PRIVATE_LINUX_STAGING' and [r['exit_code'] for r in platform['cases']]==[3,3,2,3,0] and not platform['OS_packages_installed'] and platform['runtime_members_identical_to_scientifically_used_bundle'],'Incorrect platform/install claim')
    census=obj('CENSUS.json');require(census['papers_checked']==250 and len(census['identities'])==250 and len(census['anchors'])==10 and {v['paper_id'] for v in census['anchors']}=={'P019','P157','P170','P187','P212'} and census['structures']==[] and not census['canonical_modified'],'Wrong cohort census')
    require(obj('predecessor-context-1/COMPLETED.json')['status']=='PASS_UNCHANGED_CYTOMETRY_CATALOG_IN_PHYSICAL_PRE_EXTENSION_CONTEXT','Historical source context missing')
    if jar is not None:require(file_pin(jar)==state['producer'],'Restore exact complete producing JAR; do not refresh historical hashes')
    private_count=0
    if private_root is not None:
        for name,identity in json.loads((folder/'PRIVATE-FILES.json').read_bytes()).items():require(file_pin(private_root/name)==identity,'Changed/missing retained private evidence: '+name);private_count+=1
    fresh=0
    if cache is not None:
        sys.path.insert(0,str(root/'tools/literature'))
        from fastqtl_workflow.primary import audit
        require(audit(root,cache)==census,'Fresh full-cohort primary census differs');fresh=250
    predecessor=False
    if predecessor_root is not None:
        saved=obj('predecessor-context-1/COMPLETED.json')
        for name,identity in saved['files'].items():require(file_pin(predecessor_root/name)==identity,'Changed physical predecessor source')
        gitdir=subprocess.check_output(['git','-C',str(root),'rev-parse','--absolute-git-dir'],text=True).strip();env=dict(os.environ,GIT_DIR=gitdir,GIT_WORK_TREE=str(predecessor_root),PYTHONDONTWRITEBYTECODE='1');command=[sys.executable,'-B',str(predecessor_root/'tools/literature/verify_cytometry_catalog_checkpoint.py'),'--root',str(predecessor_root)]
        if predecessor_jar is not None:command+=['--jar',str(predecessor_jar)]
        if cache is not None:command+=['--cache',str(cache)]
        result=subprocess.run(command,cwd=predecessor_root,env=env,capture_output=True);require(result.returncode==0,'Unchanged predecessor reader failed: '+result.stderr.decode(errors='replace'));predecessor=True
    return dict(status=state['status'],synthetic_cases=8,fresh_refits_recorded=2,actual_refusal_controls=15,independent_R_statistics=240,Java_tests=sum(tests.values()),exact_JAR_checked=jar is not None,private_files_checked=private_count,fresh_primary_XML_checked=fresh,unchanged_predecessor_reader_executed=predecessor,scientific_analysis_reexecuted=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    for name in ('jar','private-root','cache','predecessor-root','predecessor-jar'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    try:print(json.dumps(verify(args.root,args.jar,args.private_root,args.cache,args.predecessor_root,args.predecessor_jar),indent=2))
    except FileNotFoundError as error:
        print('SKIP3: exact permitted prerequisite unavailable: '+str(error)+'. Restore the named input/runtime/evidence or keep dependent verification skipped.',file=sys.stderr);raise SystemExit(3)
    except Exception as error:
        print('FAIL2: prepared FastQTL qualification invalid: '+repr(error)+'. Preserve originals/failed traces; restore exact source, producer or inputs. Never refresh old hashes to force success.',file=sys.stderr);raise SystemExit(2)
