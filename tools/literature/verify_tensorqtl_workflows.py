"""Verify scoped tensorQTL qualification and full synthetic original returns.

No original source text, runtime, study data or model pickle is public input.
"""
import argparse,csv,hashlib,io,json,math,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/tensorqtl-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 d=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for x in iter(lambda:f.read(1048576),b''):n+=len(x);d.update(x)
 return dict(bytes=n,sha256=d.hexdigest())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def exported(root):
 values={}
 with(root/'results/values.tsv').open(encoding='utf-8',newline='')as stream:
  for row in csv.DictReader(stream,delimiter='\t'):values.setdefault(int(row['element']),[]).append(json.loads(row['values']))
 def scalar(x):return x['value']
 def node(x):
  kind=x['kind']
  if kind=='dict':return dict(type='dict',items=[[scalar(y['key']),node(y['value'])]for y in x['items']])
  if kind in['list','tuple']:return dict(type=kind,items=[node(y)for y in x['items']])
  if kind in['dataframe','series']:
   n=x['index_levels'];data=values.get(x['element'],[]);frame=dict(type='dataframe',shape=x['shape'],columns=[scalar(y)for y in x['columns']],index_names=[scalar(y)for y in x['index_names']],index=[y[0]if n==1 else y[:n]for y in data],dtypes=x['dtypes'],data=[y[n:]for y in data])
   return dict(type='series',name=scalar(x['series_name']),data=frame)if kind=='series'else frame
  if kind in['ndarray','tensor']:
   array=dict(type='array',shape=x['shape'],dtype=x['dtype'],data=[y[0]for y in values.get(x['element'],[])])
   return dict(type='tensor',dtype=x['torch_dtype'],device=x['source_device'],data=array)if kind=='tensor'else array
  if kind=='scalar':return dict(type='scalar',value=scalar(x))
  raise ValueError('Unrecognized typed original export '+kind)
 return node(read(root/'results/objects.json'))
def compare(expected,actual):
 stats=dict(numeric=0,logical=0,undefined_nan=0,maximum_absolute_error=0)
 def visit(a,b,path):
  if isinstance(a,dict):
   require(isinstance(b,dict)and a.keys()==b.keys(),'Original object fields differ: '+path)
   for k in a:visit(a[k],b[k],path+'/'+k)
  elif isinstance(a,list):
   require(isinstance(b,list)and len(a)==len(b),'Original sequence population differs: '+path)
   for i,(x,y)in enumerate(zip(a,b)):visit(x,y,path+'/'+str(i))
  elif isinstance(a,bool):require(b is a or b==('TRUE'if a else'FALSE'),'Original logical value differs: '+path);stats['logical']+=1
  elif isinstance(a,(int,float)):
   require(not isinstance(b,bool),'Original numeric type differs: '+path);x=float(b);require(math.isfinite(a)and math.isfinite(x)and math.isclose(a,x,rel_tol=1e-10,abs_tol=1e-12),'Original numeric value differs: '+path);stats['numeric']+=1;stats['maximum_absolute_error']=max(stats['maximum_absolute_error'],abs(a-x))
  else:
   require(a==b,'Original literal value differs: '+path)
   if a=='NaN':stats['undefined_nan']+=1
 visit(expected,actual,'result');return stats
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_SCOPED_ORIGINAL_TENSORQTL'and q['new_upstream_family_credit']==1 and q['new_source_reviews']==4 and not q['whole_goal_complete'],'Invalid qualification scope')
 require(all(pin(root/n)==p for n,p in q['sources'].items()),'Current implementation/test/guide/verifier/source role bytes differ; retain their matching producer/context')
 fixture=root/q['numeric_fixture'];require(pin(fixture)==q['numeric_fixture_identity'],'Synthetic fixture differs')
 with zipfile.ZipFile(fixture)as z:
  require(set(z.namelist())==set(q['numeric_members']),'Synthetic member population differs')
  for n,p in q['numeric_members'].items():require(identity(z.read(n))==p,'Synthetic member differs: '+n)
  public_results={row['id']:compare(json.loads(z.read(row['id']+'/REFERENCE.json')),json.loads(z.read(row['id']+'/ACTUAL.json')))for row in q['cases']if row['status']=='COMPLETE'}
  require(public_results==q['full_original_comparisons'],'Complete public synthetic comparisons differ')
  if private is not None:
   private=Path(private);require(all(pin(private/n)==p for n,p in q['private_evidence'].items()),'Retained source/runtime/producer/raw/failure evidence differs')
   producer=private/q['producer_path'];require(pin(producer)==q['producer'],'Producing executable JAR differs')
   with zipfile.ZipFile(producer)as jar:require(all(hashlib.sha256(jar.read(n)).hexdigest()==p for n,p in q['producer_members'].items()),'Producing class/resource bytes differ')
   for context in q['producer_contexts'].values():
    p=private/context['path'];require(pin(p)==context['identity'],'Retained producer context differs')
    with zipfile.ZipFile(p)as jar:require(all(hashlib.sha256(jar.read(n)).hexdigest()==wanted for n,wanted in context['members'].items()),'Retained producer class/resource differs')
   comparisons={}
   for row in q['cases']:
    name=row['id'];capture=private/row['capture'];state=read(capture/'analysis-status.json');outcome=read(private/row['outcome']);complete=row['status']=='COMPLETE'
    require(outcome['exit_code']==(0 if complete else 2)and state['results_admitted']==complete,'Failure/partial admission differs: '+name)
    if complete:
     for key in row['input_keys']:require(z.read(name+'/inputs/'+key)==(capture/'inputs'/key).read_bytes(),'Direct-original and packaged literal input bytes differ')
     comparisons[name]=compare(json.loads(z.read(name+'/REFERENCE.json')),exported(capture))
    else:
     report=read(capture/'worker/report.json');require(report['status']=='FAIL2_NO_COMPLETE_RESULT'and not report['results_admitted'],'Original error/failure was admitted')
     if row['status']=='NONCONVERGED_NO_RESULT':require(report['nonconverged_models']and any(not e['source_converged']for e in report['original_events']if'source_converged'in e),'Original SuSiE returns not retained before host selection')
     if row['status']=='FAILED_OPTIMIZER_NO_RESULT':require(report['optimizer_failures']and any(not e['source_success']for e in report['original_events']if'source_success'in e),'Original failed minimizer return not retained')
   require(comparisons==q['full_original_comparisons'],'Full direct-original comparisons differ')
   negative=read(private/q['negative_path']);require(all(x['actual']==x['expected']for x in negative['cases'])and negative['cases']==q['negative_cases'],'Refusal/replay evidence differs')
   offline=read(private/q['offline_path']);require(offline['verified_complete_captures']==len(comparisons),'Windows verification incomplete')
   linux=read(private/q['linux_current_revision_verification']);require(linux['verified_complete_captures']==len(comparisons)and all(x['actual']==x['expected']for x in linux['cases']),'Current Linux verification/refusals/replay incomplete')
 return dict(status=q['status'],unit_tests=q['unit_tests'],complete_original_cases=len(q['full_original_comparisons']),numeric_values=sum(x['numeric']for x in q['full_original_comparisons'].values()),undefined_source_fields=sum(x['undefined_nan']for x in q['full_original_comparisons'].values()),new_upstream_family_credit=1,new_source_reviews=4,author_recipe_qualified=False,whole_goal_complete=False,private_evidence_checked=private is not None)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:print('FAIL2: no scoped tensorQTL qualification; retain evidence and restore the matching context or correct the named cause: '+str(e),file=sys.stderr);raise SystemExit(2)
