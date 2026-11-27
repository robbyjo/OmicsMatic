"""Check the scoped original SVA qualification and complete synthetic comparisons."""
import argparse,csv,hashlib,io,json,math,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
Q='docs/literature/sva346-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 d=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for x in iter(lambda:f.read(1048576),b''):n+=len(x);d.update(x)
 return dict(bytes=n,sha256=d.hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def compare(expected,actual):
 a=list(csv.DictReader(expected.decode().splitlines(),delimiter='\t'));b=list(csv.DictReader(actual.decode().splitlines(),delimiter='\t'));require(len(a)==len(b),'Full result row population differs');maximum=0.;finite=0
 for x,y in zip(a,b,strict=True):
  require((x['component'],x['index'])==(y['component'],y['index']),'Complete component order differs')
  if x['value']in ['TRUE','FALSE']:require(x==y,'Original logical component differs')
  else:
   u,v=float(x['value']),float(y['value']);require(math.isfinite(u)and math.isfinite(v),'Nonfinite result admitted');error=abs(u-v);require(error<=1e-9+1e-8*max(abs(u),abs(v)),'Original full result differs');maximum=max(maximum,error);finite+=1
 return dict(exported_values=len(a),finite_numeric_values=finite,maximum_absolute_error=maximum)
def compare_shapes(a,b):
 require(a.keys()==b.keys(),'Original shape population differs')
 for key in a:require(a[key]=={k:b[key][k]for k in a[key]},'Original type/class/dimension differs: '+key)
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_SCOPED_ORIGINAL_SVA346'and q['new_upstream_family_credit']==1 and not q['whole_goal_complete'],'Invalid qualification scope')
 require(all(pin(root/n)==v for n,v in q['sources'].items()),'Current SVA code, tests, guide or verifier differs; retain matching producer/context')
 fixture=root/q['numeric_fixture'];require(pin(fixture)==q['numeric_fixture_identity'],'Synthetic numeric fixture differs')
 with zipfile.ZipFile(fixture)as z:
  require(set(z.namelist())==set(q['numeric_members']),'Numeric fixture population differs')
  for name,p in q['numeric_members'].items():raw=z.read(name);require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==p,'Numeric member changed')
  require(sum(r['status']=='COMPLETE'for r in q['cases'])==17 and sum(r['status']=='ORIGINAL_ERROR_NO_RESULT'for r in q['cases'])==2,'Original case population differs')
  if private is not None:
   private=Path(private);require(all(pin(private/n)==p for n,p in q['private_evidence'].items()),'Retained original/producer/raw/failure evidence changed')
   producer=private/q['producer_path'];require(pin(producer)==q['producer'],'Producing JAR differs')
   with zipfile.ZipFile(producer)as jar:require(all(hashlib.sha256(jar.read(n)).hexdigest()==v for n,v in q['producer_members'].items()),'Producing classes/resources changed')
   results={}
   for row in q['cases']:
    name=row['id'];capture=private/'packaged-Linux-2'/(name+'-analysis');outcome=read(private/'packaged-Linux-2'/(name+'-OUTCOME.json'));require(outcome['exit_code']==(0 if row['status']=='COMPLETE'else 2),'Packaged exit differs');state=read(capture/'analysis-status.json');require(state['results_admitted']==(row['status']=='COMPLETE'),'Failure admitted')
    for key in ['matrix','design','null_design','controls']:
     member=name+'/'+key+'.tsv'
     if member in z.namelist():require(z.read(member)==(capture/'inputs'/key).read_bytes(),'Direct original and packaged input bytes differ')
    if row['status']=='COMPLETE':
     compare_shapes(read(private/'original-reference-2'/name/'original-shapes.json'),read(capture/'results/shapes.json'));results[name]=compare(z.read(name+'/original-components.tsv'),(capture/'results/components.tsv').read_bytes())
     require(outcome['verify']['exit_code']==0,'Fresh Linux verification failed')
   require(results==q['full_original_comparisons'],'Full reference comparisons differ')
   negative=read(private/'negative-Linux-1/COMPLETED.json');require(all(x['actual']==x['expected']for x in negative['cases'])and negative['cases']==q['negative_cases'],'Refusal/replay outcomes differ')
   require(read(private/'offline-Windows-1/COMPLETED.json')['verified_complete_captures']==17,'Windows R-free verification failed')
 return dict(status=q['status'],unit_tests=10,full_original_cases=17,original_errors_refused=2,numeric_values=sum(x['finite_numeric_values']for x in q['full_original_comparisons'].values()),new_upstream_family_credit=1,author_recipe_qualified=False,whole_goal_complete=False,private_evidence_checked=private is not None)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:print('FAIL2: scoped SVA qualification not admitted; restore the matching source/producer/evidence or correct the named cause: '+str(e),file=sys.stderr);raise SystemExit(2)
