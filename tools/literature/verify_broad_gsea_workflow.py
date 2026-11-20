"""Read-only original-object and integrity checks for the frozen native file workflow."""
from pathlib import Path
import argparse,hashlib,importlib.util,io,json,sys,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[2]
REL='docs/literature/gsea-workflow-development'
PACKET=ROOT/REL
STATUS='PASS_NATIVE_GSEA_FILE_WORKFLOW_THREE_JVMS_ORIGINAL_COUNTERPARTS_AND_CONTROLS'
HOSTS=('windows-java21','windows-java25','linux-java21')
SWEEP='src/test/resources/org/jlinalg/enrichment/broad-gsea-engine-originals.zip'
LONG='docs/literature/gsea-engine-development/complete-null-references.zip'
OWN={
 'src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['BroadGseaFiles','BroadGseaOutputs','BroadGseaRecipe','BroadGseaRuntime','BroadGseaWorker','BroadGseaWorkflowCli','NextflowJson','NextflowParameters']}
OWN|={'src/main/java/org/jlinalg/enrichment/'+n+'.java' for n in ['BroadGseaEngine','BroadGseaStatistics']}
OWN|={'src/test/java/org/jlinalg/cli/BroadGseaRecipeTest.java','src/main/resources/META-INF/licenses/GSEA-DESKTOP-LICENSE.txt',
 'docs/vignettes/gsea-native-workflow.md',REL+'/README.md',REL+'/.gitattributes',
 'tools/literature/gsea_workflows/check_results.py','tools/literature/gsea_workflows/prepare_fixture_requests.py',
 'tools/literature/gsea_workflows/README.md','tools/literature/verify_broad_gsea_workflow.py'}
OWN|={'examples/native-gsea/'+n for n in ['.gitattributes','README.md','expression.json','preranked.json','run.json','verify.json','replay.json']}
REFUSALS={
 'duplicate-json-key','unknown-request-key','unsupported-exact-edition','missing-exact-input','numeric-seed-refused',
 'nonfinite-float32','nan-string','duplicate-gene','duplicate-set-member','undefined-weight-mass','class-overlap',
 'class-incomplete','too-few-snr-samples','impossible-source-randomizer','capacity-maximum_dense_cells',
 'capacity-maximum_null_cells','capacity-maximum_work_per_analysis','capacity-maximum_output_bytes','capacity-maximum_files',
 'permutation-capacity','partial-batch-failure','owned-timeout-cleanup','owned-heap-exhaustion','changed-result-hash',
 'resealed-false-result','resealed-input-with-old-result','original-effective-recipe-disagreement','changed-owned-producer',
 'missing-sealed-member-is-failure','resealed-extra-member','extra-empty-directory','verify-output-inside-parent',
 'run-output-inside-parent','aliased-parent-output'}
def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def path_for(name,packet):return packet/name[len(REL)+1:] if name.startswith(REL+'/') else ROOT/name
def archive(name,record,packet,keep=lambda n:True):
    raw=path_for(name,packet).read_bytes();require(pin(raw)==record['archive'],'Changed archive: '+name);result={}
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=z.namelist();require(len(names)==len(set(names)) and set(names)==set(record['files']),'Archive population differs: '+name)
        for n in names:
            require(not n.startswith('/') and '\\' not in n and '..' not in n.split('/') and ':' not in n,'Invalid archive member')
            b=z.read(n);require(pin(b)==record['files'][n],'Changed archive member: '+name+'/'+n)
            if keep(n):result[n]=b
        require(z.testzip() is None,'Corrupt archive CRC: '+name)
    return result
def verify(packet=PACKET,jar=None):
    q=json.loads((packet/'QUALIFICATION.json').read_bytes())
    require(q['schema_version']==1 and q['status']==STATUS,'Different qualification status/schema')
    require(q['file_workflow_qualified'] is True and q['global_dispatch_registered'] is False and q['new_catalog_credit']==0,'Qualification scope changed')
    require(q['original_fields_per_JVM']==668739 and q['expected_refusals_per_JVM']==34,'Qualification counters differ')
    require(set(q['sources'])==OWN,'Missing/extra checkpoint source guard')
    for n,w in q['sources'].items():require(pin(path_for(n,packet).read_bytes())==w,'Changed source: '+n)
    expected={REL+'/checks.zip',REL+'/workflow-results.zip',SWEEP,LONG};require(set(q['archives'])==expected,'Archive guards differ')
    metadata=archive(REL+'/checks.zip',q['archives'][REL+'/checks.zip'],packet,
        lambda n:n.endswith(('COMPLETED.json','PROCESS.json','analysis-status.json','.xml')) or n in {'producer/PRODUCER.json','RESULT-REFERENCES.json','SCIENTIFIC-OUTPUT-PINS.json'})
    results=archive(REL+'/workflow-results.zip',q['archives'][REL+'/workflow-results.zip'],packet)
    sweep=archive(SWEEP,q['archives'][SWEEP],packet)
    long=archive(LONG,q['archives'][LONG],packet,lambda n:n.startswith('reference/'))
    producer=json.loads(metadata['producer/PRODUCER.json'])
    require(producer['producer']==q['producer'] and producer['unchanged_predecessor_members']==23508,'Different final producer or predecessor population')
    require(producer['prior_producer']==dict(bytes=195847299,sha256='34c8a8ac31aaa2e944dcb4c209fa81dec49c33ed86fa20af99493c7c91d32d55'),'Different qualified predecessor')
    require(all(n.startswith('org/jlinalg/cli/BroadGsea') and n.endswith('.class') for n in producer['added_members']),'Unexpected added JAR payload')
    require(len(producer['members'])==23508+len(producer['added_members']),'JAR member population differs')
    require(json.loads(metadata['gradle/PROCESS.json'])['exit_code']==0,'Final normal build failed')
    for name,count in [('BroadGseaRecipeTest',5),('BroadGseaEngineTest',1),('BroadGseaStatisticsTest',2)]:
        xml=ET.fromstring(metadata['gradle/'+name+'.xml']);require(int(xml.attrib['tests'])==count and all(int(xml.attrib[k])==0 for k in ['failures','errors','skipped']),'Focused tests differ: '+name)
    hosts=json.loads(metadata['hosts/COMPLETED.json']);require(set(hosts['results'])==set(HOSTS),'Host population differs')
    for host in HOSTS:
        r=hosts['results'][host];require(r['exact_fields']==668739 and r['admitted_option_cases']==423 and r['exit_code']==0 and r['parent_bytes_and_mtimes_preserved'] and not r['upstream_runtime_on_classpath'],'Original source comparisons failed: '+host)
        for mode in ['run','verify','replay']:
            prefix='hosts/'+host+'/'+mode+'/'
            require(json.loads(metadata[prefix+'PROCESS.json'])['exit_code']==0,'Failed packaged mode: '+prefix)
            status=json.loads(metadata[prefix+'analysis-status.json']);require(status['complete_result'] is True and status['status']=='PASS_'+mode.upper(),'Incomplete packaged mode: '+prefix)
    controls=json.loads(metadata['controls/COMPLETED.json']);require(set(controls['results'])==set(HOSTS),'Control hosts differ')
    for host in HOSTS:
        r=controls['results'][host];rows={v['name']:v for v in r['controls']};positive={'baseline','replay-positive','verify-replay-positive'}
        if host!='linux-java21':positive.add('owned-java-environment-sanitized')
        require(set(rows)==REFUSALS|positive and r['expected_refusals']==34,'Control population differs: '+host)
        require(all(r[k] for k in ['parent_preserved','timeout_owned_cleanup','unrelated_sentinel_preserved','heap_exhaustion_explicit_failure','actual_alias_refused']),'Failed preservation/containment control: '+host)
        for name,row in rows.items():
            code=3 if name in {'unsupported-exact-edition','missing-exact-input'} else 2 if name in REFUSALS else 0
            require(row['exit_code']==row['expected_exit']==code,'Failed control: '+host+'/'+name)
            prefix='controls/'+host+'/'+name+'/'
            require(json.loads(metadata[prefix+'PROCESS.json'])['exit_code']==code,'Control process differs: '+prefix)
            if row['capture_preserved']:
                s=json.loads(metadata[prefix+'analysis/analysis-status.json']);require(s['complete_result']==(code==0),'False control completion: '+prefix)
    transport=json.loads(metadata['transport/COMPLETED.json']);require(transport['all_parent_bytes_and_mtimes_preserved'] and set(transport['results'])==set(HOSTS),'Transport hosts or preservation differ')
    common={'signed-seed-min','signed-seed-max','literal-unicode-and-control-identities','float32-lexeme-midpoint','cross-host-verify','cross-host-replay'}
    for host,r in transport['results'].items():
        rows={v['name']:v for v in r['checks']};names=common|({'class-directory-is-explicit-skip'} if host=='windows-java21' else set());require(set(rows)==names,'Transport population differs')
        for n,row in rows.items():require(row['exit_code']==row['expected_exit']==(3 if n=='class-directory-is-explicit-skip' else 0),'Transport failed: '+host+'/'+n)
    scientific=json.loads(metadata['SCIENTIFIC-OUTPUT-PINS.json']);require(set(scientific)==set(HOSTS) and all(scientific[h]==scientific[HOSTS[0]] for h in HOSTS),'Cross-host scientific output bytes differ')
    spec=importlib.util.spec_from_file_location('gsea_object_reader',ROOT/'tools/literature/gsea_workflows/check_results.py');checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
    mapping=json.loads(metadata['RESULT-REFERENCES.json']);require(set(mapping)==set(results) and len(mapping)==432,'Native counterpart object population differs')
    counts={'long':0,'options':0};cases={'long':0,'options':0}
    for name,r in mapping.items():
        kind=r['kind'];original=(long if kind=='long' else sweep)[r['reference']]
        if kind=='options':require(b'NATIVE_POLICY\tADMIT' in original,'Wrong source policy')
        counts[kind]+=checker.compare_bytes(results[name],original,raw_metrics=kind=='options',label=name);cases[kind]+=1
    require(counts==dict(long=468270,options=200469) and cases==dict(long=9,options=423),'Original field/case counters differ')
    if jar is not None:
        require(pin(Path(jar).read_bytes())==q['producer'],'Different exact optional normal producer')
        with zipfile.ZipFile(jar) as z:
            require(len(z.namelist())==len(producer['members']) and set(z.namelist())==set(producer['members']),'Optional JAR member population differs')
            for n,w in producer['members'].items():require(pin(z.read(n))==w,'Different optional JAR payload: '+n)
    return dict(status='PASS_SAVED_NATIVE_GSEA_FILE_WORKFLOW_QUALIFICATION',JVMs=3,original_counterpart_fields_checked=counts['long']+counts['options'],original_cases=cases,expected_refusals_per_JVM=34,
        focused_JUnit_tests=8,cross_host_verify_replay_passed=True,optional_exact_jar_checked=jar is not None,file_workflow_qualified=True,global_dispatch_registered=False,new_catalog_credit=0,Java_Gradle_R_or_models_executed=False,producer=q['producer'])
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--packet',type=Path,default=PACKET);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:
        require(not args.out.exists() and not args.out.resolve().is_relative_to(args.packet.resolve()),'Choose NEW output outside the packet')
        result=verify(args.packet,args.jar);args.out.mkdir(parents=True,exist_ok=False);(args.out/'READBACK.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result,indent=2));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        print(('SKIP3: named exact source/evidence/producer prerequisite missing or inaccessible. ' if code==3 else 'FAIL2: no valid saved workflow qualification. ')+str(error)+'\nRestore the named exact permitted prerequisites or retain the skip. Preserve evidence; correct the named integrity/schema cause and retry NEW output. Do not refresh old hashes to admit changed code.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
