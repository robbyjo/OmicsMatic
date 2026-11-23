from pathlib import Path
import copy,importlib.util,json,shutil,subprocess,time
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'protocol-controls-1';D.mkdir()
jar=B/'normal-build-4/project-build/cli/omicsmatic-0.4.0.jar';prior=B/'normal-build-3/project-build/cli/omicsmatic-0.4.0.jar'
java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';records=[]
template=B/'workflow-original-references-2/windows21/outlier-stable-precision';base=json.loads((template/'request.json').read_bytes());source=(template/'associations.tsv').read_text()
def run(label,args,expected,producer=jar):
 command=[java,'-Xmx512m','-jar',str(producer),'mrpresso-tools',*args];started=time.monotonic()
 with (D/(label+'.stdout')).open('xb') as out,(D/(label+'.stderr')).open('xb') as err:
  process=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(label+'.PROCESS.json'),dict(pid=process.pid,command=command));code=process.wait()
 row=dict(case=label,exit_code=code,expected=expected,seconds=time.monotonic()-started);records.append(row);save(D/'PROGRESS.json',records);print(json.dumps(row),flush=True)
 assert code==expected,(label,(D/(label+'.stderr')).read_text(errors='replace')[-1800:])
def request(label,alter=None,text=source,expected=2):
 root=D/label;root.mkdir();table=root/'input.tsv';table.write_text(text,encoding='utf-8');value=copy.deepcopy(base)
 value['out']=str(root/'analysis');value['inputs']['associations']=str(table)
 if alter:alter(value)
 path=root/'request.json';save(path,value);run(label,['--request',str(path)],expected)
 if expected in (2,3):assert not (root/'analysis/COMPLETED.json').exists()
 return root
request('missing-input',lambda x:x['inputs'].__setitem__('associations',str(D/'never-present.tsv')),expected=3)
run('missing-capture',['--verify',str(D/'never-present-capture')],3)
request('unknown-scientific-option',lambda x:x['options'].__setitem__('automatic_repair',True))
request('different-engine',lambda x:x.__setitem__('engine','analytic-presso'))
rows=source.splitlines();header=rows[0].split('\t')
values=rows[1].split('\t');values[header.index('Y')]=' '+values[header.index('Y')]
request('numeric-whitespace',text='\n'.join([rows[0],'\t'.join(values),*rows[2:]])+'\n')
request('incomplete-row',text='\n'.join([rows[0],'\t'.join(rows[1].split('\t')[:-1]),*rows[2:]])+'\n')
values=rows[2].split('\t');values[0]=rows[1].split('\t')[0]
request('duplicate-literal-id',text='\n'.join([rows[0],rows[1],'\t'.join(values),*rows[3:]])+'\n')
values=rows[1].split('\t');values[header.index('Y')]='Inf'
request('retained-infinite-association',text='\n'.join([rows[0],'\t'.join(values),*rows[2:]])+'\n')
request('missing-selected-column',lambda x:x['inputs'].__setitem__('se_outcome','not-present'))
for label,key,value in [('work-limit','maximum_estimated_work',1),('input-byte-limit','maximum_input_bytes',16),('row-limit','maximum_rows',1),('cell-limit','maximum_cells',1)]:
 request(label,lambda x,key=key,value=value:x.__setitem__('limits',{key:value}))
request('owned-native-deadline',lambda x:(x['options'].__setitem__('NbDistribution',1_000_000),x.__setitem__('limits',dict(timeout_seconds=1,maximum_estimated_work=1_000_000_000_000))))
require_deadline=(D/'owned-native-deadline.stderr').read_text();assert 'deadline' in require_deadline
state=json.loads((template/'analysis/native-result.json').read_bytes())['rng_before']
request('seed-and-state',lambda x:x['rng'].__setitem__('state',state))
request('sampling-state-disagreement',lambda x:x.__setitem__('rng',dict(kind='Mersenne-Twister/Inversion',sample_kind='Rounding',state=state)))
request('all-zero-state',lambda x:x.__setitem__('rng',dict(kind='Mersenne-Twister/Inversion',sample_kind='Rejection',state=[10403,624]+[0]*624)))
request('missing-explicit-sampling-kind',lambda x:x['rng'].pop('sample_kind'))
run('different-producer-refusal',['--verify',str(template/'analysis')],2,prior)
# Actual documentation commands: no rewritten requests or copied data.
run('documented-example-run',['--request',str(T/'examples/mrpresso-native/run.json')],0)
example=T/'build/NEW-mrpresso-native-example';before={p.relative_to(example).as_posix():pin(p) for p in example.rglob('*') if p.is_file()}
run('existing-output-refusal',['--request',str(T/'examples/mrpresso-native/run.json')],2)
assert before=={p.relative_to(example).as_posix():pin(p) for p in example.rglob('*') if p.is_file()}
run('documented-example-verify',['--verify',str(example)],0)
run('documented-example-replay',['--request',str(T/'examples/mrpresso-native/replay.json')],0)
run('documented-example-replay-verify',['--verify',str(T/'build/NEW-mrpresso-native-example-replay')],0)
parent=D/'owned-parent';shutil.copytree(template/'analysis',parent);before=pin(parent/'capture-files.json')
child=D/'child';path=D/'first-replay.json';save(path,dict(schema_version=1,mode='REPLAY',analysis=str(parent),out=str(child)))
run('owned-parent-replay',['--request',str(path)],0);assert before==pin(parent/'capture-files.json')
script=D/'relocate-owned-parent.ps1';script.write_text('''$ErrorActionPreference='Stop'
$taskRoot='D:\\projects\\JLinAlg\\build\\fu-1\\build\\mrpresso-1\\protocol-controls-1'
$taskSource=Join-Path $taskRoot 'owned-parent'
$taskTarget=Join-Path $taskRoot 'relocated-parent'
$taskResolved=(Resolve-Path -LiteralPath $taskSource).Path
if ($taskResolved -ne $taskSource -or -not $taskTarget.StartsWith($taskRoot+'\\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $taskTarget)) { throw 'Unexpected relocation target' }
$taskItem=Get-Item -LiteralPath $taskSource -Force
while ($null -ne $taskItem) { if (($taskItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse point in relocation path' }; $taskItem=$taskItem.Parent }
Rename-Item -LiteralPath $taskSource -NewName 'relocated-parent' -ErrorAction Stop
''',encoding='utf-8')
command=['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-File',str(script)]
p=subprocess.run(command,cwd=T,capture_output=True);(D/'relocate.stdout').write_bytes(p.stdout);(D/'relocate.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr
assert not parent.exists() and before==pin(D/'relocated-parent/capture-files.json')
run('standalone-child-after-parent-relocation',['--verify',str(child)],0)
run('relocated-parent-verify',['--verify',str(D/'relocated-parent')],0)
path=D/'chain.json';save(path,dict(schema_version=1,mode='REPLAY',analysis=str(child),out=str(D/'grandchild')))
run('actual-replay-chain',['--request',str(path)],0);run('actual-replay-chain-verify',['--verify',str(D/'grandchild')],0)
path=D/'forbidden-replay-option.json';save(path,dict(schema_version=1,mode='REPLAY',analysis=str(child),out=str(D/'forbidden-new-output'),options=dict(NbDistribution=1000)))
run('replay-option-override-refusal',['--request',str(path)],2);assert not (D/'forbidden-new-output').exists()
save(D/'RESULT.json',dict(status='PASS',producer=pin(jar),commands=records,actual_deadline_refused=True,
 actual_owned_JVM_returned_after_deadline=True,unrelated_processes_stopped=0,existing_output_bytes_unchanged=True,
 standalone_child_without_original_parent_path=True,actual_replay_chain=True,whole_workflow_qualified=False,new_family_credit=0,new_reviews=0))
