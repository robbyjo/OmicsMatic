from pathlib import Path
import copy,importlib.util,json,shutil,subprocess,sys,time
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/('saved-controls-'+sys.argv[1]);D.mkdir()
jar=B/('normal-build-'+sys.argv[2]+'/project-build/cli/omicsmatic-0.4.0.jar')
suite=B/'workflow-original-references-2/windows21';template=suite/'outlier-stable-precision/analysis';tiny=suite/'tiny-outcome-univ/analysis'
assert (template/'COMPLETED.json').is_file() and (tiny/'COMPLETED.json').is_file()
java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
records=[]
def run(label,root,expected):
 for host in ('windows21','windows25','linux21'):
  if host=='linux21':
   address=Path(root).as_posix();address='/mnt/'+address[0].lower()+address[2:]
   command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx512m','-jar','/var/tmp/omicsmatic-mrpresso-qualification-20261123-168/omicsmatic-0.4.0.jar','mrpresso-tools','--verify',address]
  else:
   executable=java if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
   command=[executable,'-Xmx512m','-jar',str(jar),'mrpresso-tools','--verify',str(root)]
  with (D/(label+'.'+host+'.stdout')).open('xb') as out,(D/(label+'.'+host+'.stderr')).open('xb') as err:
   p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(label+'.'+host+'.PROCESS.json'),dict(pid=p.pid,command=command));code=p.wait()
  row=dict(case=label,host=host,exit_code=code,expected=expected,matched=code==expected);records.append(row);save(D/'PROGRESS.json',records)
  print(json.dumps(row),flush=True)
def reseal(root):
 p=root/'capture-files.json';value=json.loads(p.read_bytes())
 # Rebuild identities but retain closed namespace and path population unless
 # a control explicitly adds/removes a named entry.
 for name in value['files']:value['files'][name]=pin(root/name)
 save(p,value);marker=json.loads((root/'COMPLETED.json').read_bytes());marker['seal_identity']=pin(p);save(root/'COMPLETED.json',marker)
def edit_result(root,action):
 p=root/'native-result.json';value=json.loads(p.read_bytes());action(value);save(p,value)
def make(label,action,source=template,sealed=True):
 target=D/label;shutil.copytree(source,target);action(target)
 if sealed:reseal(target)
 run(label,target,2)
run('valid',template,0)
make('false-main-beta',lambda p:edit_result(p,lambda x:x['raw_mr']['beta'][0].__setitem__('value',x['raw_mr']['beta'][0]['value']*1.02)))
def inflated(root):
 def alter(value):
  for name in ('raw_mr','corrected_mr'):
   for key in ('beta','standard_error'):
    for cell in value[name][key]:cell['value']*=1_000_000
 edit_result(root,alter)
 p=root/'mr-results.tsv';rows=p.read_text().splitlines();output=[rows[0]]
 for row in rows[1:]:
  values=row.split('\t')
  for j in (2,3):values[j]=str(float(values[j])*1_000_000)
  output.append('\t'.join(values))
 p.write_text('\n'.join(output)+'\n',encoding='utf-8')
make('coherent-tiny-inflation',inflated,tiny)
def bound(value):value['global_p_value']['reported_bound']['value']*=2
make('false-source-bound',lambda p:edit_result(p,bound))
def count(value):
 p=value['global_p_value'];p['exceedances']+=1;p['kind']='EMPIRICAL';p['internal_source_value']['value']=p['exceedances']/p['distributions'];p['reported_bound']=None
make('false-count-with-coherent-probability',lambda p:edit_result(p,count))
make('false-rng-state',lambda p:edit_result(p,lambda x:x['rng_after'].__setitem__(12,x['rng_after'][12]+1)))
make('false-retained-row-id',lambda p:edit_result(p,lambda x:x['retained_row_ids'].__setitem__(0,'another-variant')))
def coherent_fit(root):
 def alter(value):
  for key in ('beta','standard_error'):value['raw_mr'][key][0]['value']*=1.02
 edit_result(root,alter)
make('coherent-false-fit',coherent_fit)
make('false-projection',lambda p:(p/'mr-results.tsv').write_text((p/'mr-results.tsv').read_text().replace('\tRaw\t','\tFalse-label\t'),encoding='utf-8'))
def provenance(root):
 p=root/'source/R/mr_presso.R';p.write_bytes(p.read_bytes()+b'\n# altered source provenance\n')
make('false-source-provenance',provenance)
def producer(root):
 p=root/'producer.json';value=json.loads(p.read_bytes())
 # Locate only the recorded producer identity; do not alter the real JAR.
 def replace(x):
  if isinstance(x,dict):
   for key in x:
    if key=='sha256':x[key]='0'*64
    else:replace(x[key])
  elif isinstance(x,list):
   for item in x:replace(item)
 replace(value);save(p,value)
make('false-producer-identity',producer)
make('extra-empty-directory',lambda p:(p/'unrelated-empty').mkdir())
make('missing-completion-marker',lambda p:(p/'COMPLETED.json').unlink(),sealed=False)
make('missing-seal',lambda p:(p/'capture-files.json').unlink(),sealed=False)
def forged_status(root):
 p=root/'analysis-status.json';value=json.loads(p.read_bytes());value['status']='COMPLETED_WITH_UNREPORTABLE_FIELDS';save(p,value)
make('false-admission-status',forged_status)
def changed_canonical(root):
 p=root/'canonical-request.json';value=json.loads(p.read_bytes());value['rng']['seed']=20;save(p,value)
make('unbound-canonical-seed',changed_canonical)
make('extra-native-field',lambda p:edit_result(p,lambda x:x.__setitem__('automatic_repair_performed',False)))
save(D/'RESULT.json',dict(status='PASS' if all(x['matched'] for x in records) else 'FAIL_RETAINED',producer=pin(jar),
 cases=records,unexpected=[x for x in records if not x['matched']],whole_workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
assert all(x['matched'] for x in records),'Inspect retained saved-capture control outcomes'
