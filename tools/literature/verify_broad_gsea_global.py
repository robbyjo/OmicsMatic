"""Read-only checks for the frozen native GSEA global-command handover."""
from pathlib import Path
import argparse,hashlib,importlib.util,io,json,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
PACKET=ROOT/'docs/literature/gsea-global-development'
HOSTS=('windows-java21','windows-java25','linux-java21')
STATUS='PASS_NATIVE_GSEA_GLOBAL_HANDOVER_THREE_JVMS'

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def module(name,path):
    s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def verify(packet=PACKET,jar=None):
    q=json.loads((packet/'QUALIFICATION.json').read_bytes())
    require(q['schema_version']==1 and q['status']==STATUS,'Different global qualification schema/status')
    for n,w in q['sources'].items():require(pin((ROOT/n).read_bytes())==w,'Changed checkpoint source: '+n)
    transition=module('gsea_global_transition',ROOT/'tools/literature/gsea_dispatch_transition.py')
    before=transition.dispatcher_before((ROOT/transition.DISPATCH).read_bytes())
    require(pin(before)==transition.BEFORE,'Different dispatcher predecessor')
    legacy=module('gsea_preceding_workflow_reader',ROOT/'tools/literature/verify_broad_gsea_workflow.py')
    previous=legacy.verify();require(previous['original_counterpart_fields_checked']==668739,'Different predecessor counterparts')
    raw=(packet/'checks.zip').read_bytes();require(pin(raw)==q['archive'],'Changed global proof archive')
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=z.namelist();require(len(names)==len(set(names)) and set(names)==set(q['files']),'Different proof population')
        data={n:z.read(n) for n in names}
        for n,b in data.items():require(pin(b)==q['files'][n],'Changed proof member: '+n)
        require(z.testzip() is None,'Corrupt global proof archive')
    def record(n):return json.loads(data[n])
    producer=record('producer/PRODUCER.json')
    require(producer['producer']==q['producer'] and producer['prior_producer']==previous['producer'],'Different producer lineage')
    oldq=json.loads((ROOT/'docs/literature/gsea-workflow-development/QUALIFICATION.json').read_bytes())
    with zipfile.ZipFile(ROOT/'docs/literature/gsea-workflow-development/checks.zip') as z:
        oldmembers=json.loads(z.read('producer/PRODUCER.json'))['members']
    newmembers=producer['members'];require(set(oldmembers)==set(newmembers) and len(newmembers)==23522,'Different producer population')
    require([n for n in sorted(newmembers) if newmembers[n]!=oldmembers[n]]==['org/jlinalg/cli/JLinAlgCli.class'],'Unexpected scientific/payload change')
    require(producer['unchanged_predecessor_members']==23521 and producer['added_members']==[],'Unexpected JAR relation')
    controls=record('transition/COMPLETED.json')
    require(controls['status']=='PASS_EXACT_THREE_LINE_INVERSE_AND_SEVEN_REFUSALS' and len(controls['controls'])==7 and all(c['status']=='EXPECTED_REFUSAL' for c in controls['controls']),'Inverse controls differ')
    tests=record('transition/JUNIT.json')
    require(sum(int(a['tests']) for a in tests)==8 and all(int(a['failures'])==int(a['errors'])==int(a['skipped'])==0 for a in tests),'Focused tests failed')
    require(record('gradle/PROCESS-EXIT.json')['exit_code']==0,'Normal build failed')
    hosts=record('hosts/COMPLETED.json');require(set(hosts['results'])==set(HOSTS) and hosts['producer']==q['producer'] and hosts['global_dispatch_registered'],'Different global host proof')
    results=oldq['archives']['docs/literature/gsea-workflow-development/workflow-results.zip']['files']
    for host in HOSTS:
        r=hosts['results'][host]
        require(r['exact_fields']==668739 and r['admitted_option_cases']==423 and r['previous_producer_capture_VERIFY_REPLAY'] and r['parent_bytes_and_mtimes_preserved'],'Incomplete counterpart/preservation checks: '+host)
        require(record('hosts/'+host+'/COUNTERPART-PINS.json')==results,'Native binary objects differ from qualified counterparts: '+host)
        for label in ['run','verify','replay','previous-verify','previous-replay']:
            p='hosts/'+host+'/'+label+'/'
            c=record(p+'PROCESS.json');command=c['command'];require(c['exit_code']==0 and command[-5:-1]==['-jar',command[-4],'broad-gsea-tools','--request'],'Wrong global invocation: '+p)
            status=record(p+'analysis-status.json');mode=label.removeprefix('previous-').upper()
            require(status['complete_result'] is True and status['status']=='PASS_'+mode,'Incomplete global capture: '+p)
        for label in ['global-help','command-help','missing-request']:
            c=record('hosts/'+host+'/'+label+'/PROCESS.json')
            require(c['exit_code']==(2 if label=='missing-request' else 0),'Wrong syntax/help result: '+host+'/'+label)
    if jar is not None:
        require(pin(Path(jar).read_bytes())==q['producer'],'Different exact optional global producer')
        with zipfile.ZipFile(jar) as z:
            require(len(z.namelist())==len(newmembers) and set(z.namelist())==set(newmembers),'Optional producer population differs')
            for n,w in newmembers.items():require(pin(z.read(n))==w,'Different optional producer payload: '+n)
    return dict(status='PASS_SAVED_NATIVE_GSEA_GLOBAL_HANDOVER',JVMs=3,original_fields_per_JVM=668739,unchanged_predecessor_payloads=23521,focused_JUnit_tests=8,inverse_refusals=7,global_RUN_VERIFY_REPLAY=True,previous_captures_VERIFY_REPLAY=True,optional_exact_jar_checked=jar is not None,new_catalog_credit=0,Java_Gradle_or_models_executed=False,producer=q['producer'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--packet',type=Path,default=PACKET);p.add_argument('--jar',type=Path);a=p.parse_args()
    try:
        require(not a.out.exists() and not a.out.resolve().is_relative_to(a.packet.resolve()),'Choose NEW output outside the proof packet')
        result=verify(a.packet,a.jar);a.out.mkdir(parents=True,exist_ok=False);(a.out/'READBACK.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result,indent=2));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        print(('SKIP3: named exact prerequisite missing/inaccessible. ' if code==3 else 'FAIL2: no valid global qualification. ')+str(error)+'\nRestore the exact permitted prerequisite or keep the skip. Preserve evidence, correct the named cause and retry NEW output. Do not refresh old hashes to admit changed code.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
