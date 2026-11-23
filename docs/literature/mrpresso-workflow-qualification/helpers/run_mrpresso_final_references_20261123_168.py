from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import copy,importlib.util,json,os,shutil,subprocess,time,traceback
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'workflow-original-references-2';D.mkdir()
jar=B/'normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'
assert pin(jar)['sha256']=='af72b8ccaa263eca2aad69ff96e871829981a6475dc598a8c5c5b704dba6f0e6'
reader=T/'tools/literature/mrpresso_workflow/compare_original.py';shutil.copyfile(reader,D/'compare_original.py')
spec=importlib.util.spec_from_file_location('mrpresso_reference_comparator',D/'compare_original.py');comparator=importlib.util.module_from_spec(spec);spec.loader.exec_module(comparator)
inputs=[*sorted((B/'original-reference-windows-1/outputs').glob('*/original.json')),*sorted((B/'extended-reference-windows-1/outputs').glob('*/original.json'))];assert len(inputs)==45
unreportable={'essentially-perfect-raw','zero-corrected-inference','balanced-mvmr-distortion-257','balanced-mvmr-distortion-258'}
replay_cases={'outlier-stable-precision','clean-11','multivariable-two','rounding-mvmr-127','continued-mvmr-state','balanced-mvmr-distortion-257','essentially-perfect-raw'}
hosts={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
 'windows25':['C:/Program Files/Java/jdk-25/bin/java.exe'],
 'linux21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java']}
def address(path,host):
 value=Path(path).as_posix();return '/mnt/'+value[0].lower()+value[2:] if host=='linux21' else value
def token(value):
 if isinstance(value,dict):return 'NA' if value['special'].startswith('NA_') else value['special']
 return str(value)
def run_host(host):
 out=D/host;out.mkdir();commands=[];comparisons=[];cases=[]
 def run(label,args,expected):
  command=[*hosts[host],'-Xmx512m','-jar',('/var/tmp/omicsmatic-mrpresso-qualification-20261123-168/omicsmatic-0.4.0.jar' if host=='linux21' else address(jar,host)),'mrpresso-tools',*args];started=time.monotonic()
  with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
   process=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
   save(out/(label+'.PROCESS.json'),dict(pid=process.pid,command=command));code=process.wait()
  row=dict(label=label,exit_code=code,expected=expected,seconds=time.monotonic()-started);commands.append(row);save(out/'COMMANDS.json',commands)
  assert code==expected,(host,label,code,expected,(out/(label+'.stderr')).read_text(errors='replace')[-1800:])
  return code
 def compare(record,capture,label):
  result=comparator.compare(record,json.loads((capture/'native-result.json').read_bytes()));result['capture']=str(capture);result['label']=label
  comparisons.append(result);save(out/'ORIGINAL-COMPARISONS.json',comparisons)
 try:
  for index,path in enumerate(inputs):
   record=json.loads(path.read_bytes());name=comparator.scalar(comparator.field(record,'case'));case=out/name;case.mkdir();shutil.copyfile(path,case/'original.json')
   frame=comparator.field(record,'input');columns=comparator.columns(frame);names=list(columns)
   table=case/'associations.tsv';table.write_text('row_id\t'+'\t'.join(names)+'\n'+'\n'.join(str(label)+'\t'+'\t'.join(token(columns[n][i]) for n in names) for i,label in enumerate(frame['row_names']))+'\n',encoding='utf-8')
   arguments=comparator.field(record,'arguments');seed=comparator.field(arguments,'seed');rng=dict(kind='Mersenne-Twister/Inversion',sample_kind=comparator.field(record,'rng_kind')['values'][2])
   if seed is None:rng['state']=comparator.field(record,'rng_before')['values']
   else:rng['seed']=comparator.scalar(seed)
   options={key:comparator.scalar(comparator.field(arguments,key)) for key in ('OUTLIERtest','DISTORTIONtest','SignifThreshold','NbDistribution') if key in arguments['names']}
   request=dict(schema_version=1,mode='RUN',engine='native',profile='MRPRESSO_1.0_3e3c92d',operation='ANALYZE',out=address(case/'analysis',host),
    inputs=dict(associations=address(table,host),id_column='row_id',beta_outcome=comparator.scalar(comparator.field(arguments,'BetaOutcome')),
     se_outcome=comparator.scalar(comparator.field(arguments,'SdOutcome')),beta_exposure=comparator.field(arguments,'BetaExposure')['values'],se_exposure=comparator.field(arguments,'SdExposure')['values']),
    rng=rng,options=options,limits=dict(timeout_seconds=60))
   request_path=case/'request.json';save(request_path,request)
   source_error=not comparator.scalar(comparator.field(record,'returned'));unresolved_alias=name=='aliased-exposures';bad=source_error or unresolved_alias or name in unreportable
   run(name+'-run',['--request',address(request_path,host)],2 if bad else 0)
   capture=case/'analysis'
   if bad:
    assert not (capture/'COMPLETED.json').exists() and (capture/'FAILURE.json').is_file()
    status=json.loads((capture/'analysis-status.json').read_bytes());assert not status['results_admitted'] and status['status']=='FAIL2_NO_COMPLETE_RESULT'
    if name in unreportable:
     compare(record,capture,'default-policy-withheld')
     alternate=copy.deepcopy(request);alternate['out']=address(case/'explicit-unreportable',host);alternate['policies']=dict(allow_unreportable_fields=True)
     p=case/'explicit-unreportable.json';save(p,alternate);run(name+'-explicit-policy',['--request',address(p,host)],0)
     capture=case/'explicit-unreportable';compare(record,capture,'explicit-policy-raw-fields-retained')
     status=json.loads((capture/'analysis-status.json').read_bytes());assert status['status']=='COMPLETED_WITH_UNREPORTABLE_FIELDS'
    else:
     assert not (capture/'native-result.json').exists();cases.append(dict(case=name,original_error=source_error,source_alias_unreportable=unresolved_alias,native_exit_code=2));continue
   else:compare(record,capture,'complete-native-analysis')
   run(name+'-verify',['--verify',address(capture,host)],0)
   if name in replay_cases:
    p=case/'replay.json';save(p,dict(schema_version=1,mode='REPLAY',analysis=address(capture,host),out=address(case/'replayed',host)))
    run(name+'-replay',['--request',address(p,host)],0);compare(record,case/'replayed','actual-replay-original-comparison')
    run(name+'-verify-replay',['--verify',address(case/'replayed',host)],0)
   cases.append(dict(case=name,default_policy_exit_code=2 if bad else 0,complete_capture=address(capture,host),source_compared=True))
   if (index+1)%10==0:print(json.dumps(dict(host=host,processed=index+1,commands=len(commands))),flush=True)
  result=dict(host=host,status='PASS',original_cases=len(inputs),commands=len(commands),comparisons=len(comparisons),
   admitted_native_captures=sum(x['expected']==0 and (x['label'].endswith('-run') or x['label'].endswith('-explicit-policy')) for x in commands),
   expected_failures=sum(x['expected']==2 for x in commands),replays=sum(x['label'].endswith('-replay') and not x['label'].endswith('-verify-replay') for x in commands),
   cases=cases,whole_workflow_qualified=False,new_family_credit=0,new_reviews=0)
 except Exception as error:
  (out/'FAILURE.txt').write_text(traceback.format_exc(),encoding='utf-8');result=dict(host=host,status='FAIL_RETAINED',error=str(error),commands=len(commands),comparisons=len(comparisons))
 save(out/'RESULT.json',result);print(json.dumps({k:v for k,v in result.items() if k!='cases'}),flush=True);return result
with ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(run_host,hosts))
assert pin(reader)==pin(D/'compare_original.py'),'Independent comparator changed during validation'
save(D/'RESULT.json',dict(status='PASS' if all(x['status']=='PASS' for x in results) else 'FAIL_RETAINED',producer=pin(jar),hosts=results,
 independent_original_comparator=pin(reader),original_reference_calls=45,whole_workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
assert all(x['status']=='PASS' for x in results),'Inspect retained per-host original/native comparison failures'
