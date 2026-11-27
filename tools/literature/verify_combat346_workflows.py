"""Verify the source-specific native ComBat qualification and retained evidence."""
import argparse,csv,hashlib,json,math,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/combat346-validation';Q=FOLDER+'/QUALIFICATION.json'

def read(path):return json.loads(Path(path).read_bytes())
def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 return dict(bytes=size,sha256=digest.hexdigest())
def require(ok,message):
 if not ok:raise ValueError(message)
def table(path):return list(csv.reader(Path(path).read_text(encoding='utf-8').splitlines(),delimiter='\t'))

def compare(reference,actual):
 fitted=[row[1]=='TRUE'for row in table(reference/'feature-status.tsv')[1:]];count=0;missing=0;not_estimated=0;maximum=0.0
 for name in ['adjusted.tsv','gamma.tsv','delta.tsv']:
  expected,current=table(reference/name),table(actual/name);require(len(expected)==len(current)and expected[0]==current[0],'Result dimensions/labels differ')
  for i,(left,right)in enumerate(zip(expected[1:],current[1:],strict=True)):
   require(left[0]==right[0]and len(left)==len(right),'Result row identity differs')
   for j,(a,b)in enumerate(zip(left[1:],right[1:],strict=True)):
    if name!='adjusted.tsv'and not fitted[j]:require(a=='NA'and b=='NOT_ESTIMATED','Excluded posterior is not unestimated');not_estimated+=1;continue
    if a=='NA':require(b=='NA','Original missing cell changed');missing+=1;continue
    a,b=float(a),float(b);require(math.isfinite(a)and math.isfinite(b)and abs(a-b)<=2e-10*max(1,abs(a)),'Native value differs from original R');count+=1;maximum=max(maximum,abs(a-b))
 return dict(finite_values=count,missing_values=missing,not_estimated_cells=not_estimated,maximum_absolute_error=maximum)

def verify(root,private=None):
 root=Path(root);q=read(root/Q);require(q['status']=='PASS_NATIVE_SVA346_CONTINUOUS_COMBAT_QUALIFICATION'and q['unit_tests']==21 and q['original_finite_cases_per_host']==14 and q['whole_paper_reproduced']is False and q['whole_goal_complete']is False,'Qualification scope changed')
 require(all(pin(root/name)==identity for name,identity in q['sources'].items()),'Live method, test, example, guide or verifier source differs; retain matching historical context')
 fixture=root/'src/test/resources/org/jlinalg/confounding/sva346-original-reference.zip';profile=read(root/'src/test/resources/org/jlinalg/confounding/sva346-reference-profile.json');require(pin(fixture)==profile['reference_archive'],'Original numeric reference archive changed')
 with zipfile.ZipFile(fixture)as stream:
  require(set(stream.namelist())==set(profile['members'])and all(dict(bytes=len(stream.read(name)),sha256=hashlib.sha256(stream.read(name)).hexdigest())==expected for name,expected in profile['members'].items()),'Original numeric reference population/bytes changed')
 if private is not None:
  private=Path(private);require(all(pin(private/name)==identity for name,identity in q['private_evidence'].items()),'Retained original/source/input/producer/test/failure evidence changed or disappeared')
  producer=private/q['producer_path'];require(pin(producer)==q['producer'],'Producing executable JAR changed')
  with zipfile.ZipFile(producer)as stream:require(all(hashlib.sha256(stream.read(name)).hexdigest()==digest for name,digest in q['producer_classes'].items()),'Packaged numerical/dispatcher bytecode changed')
  reference=private/'original-reference-5';rows=list(csv.DictReader((reference/'cases.tsv').read_text().splitlines(),delimiter='\t'));require(sum(r['status']=='COMPLETE_FINITE_OBSERVED_VALUES'for r in rows)==14,'Original finite reference count differs')
  for host in ['Windows','Linux']:
   runs=read(private/('packaged-'+host+'-1/COMPLETED.json'));require(runs['producer']==q['producer']and all(r['actual']==r['expected']for r in runs['cases']),'Packaged execution/refusal failed')
   comparisons={}
   for row in rows:
    output=private/('packaged-'+host+'-1')/(row['case']+'-analysis');state=read(output/'analysis-status.json');finite=row['status']=='COMPLETE_FINITE_OBSERVED_VALUES';require(state['result_admitted']==finite,'Failed/original undefined result was admitted')
    if finite:comparisons[row['case']]=compare(reference/row['case'],output)
   require(comparisons==q['full_original_comparisons'][host],'Saved whole-matrix original comparisons differ')
   require(sum(r['expected']==3 for r in runs['cases'])==3 and sum(r['expected']==2 for r in runs['cases'])==8,'Refusal outcome population differs')
 return dict(status='PASS_SAVED_NATIVE_COMBAT346_CONTEXT',unit_tests=21,original_finite_cases_per_host=14,full_original_finite_values_per_host=q['finite_comparisons_per_host'],fresh_native_recomputations=29,refusals_per_host=dict(SKIP3=3,FAIL2=8),private_evidence_checked=private is not None,new_native_family_credit=1,new_upstream_family_credit=0,whole_paper_reproduced=False,whole_goal_complete=False)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Restore the named permitted retained evidence; verification skipped. 'if code==3 else'FAIL2: No complete native ComBat qualification. ')+str(error),file=sys.stderr);raise SystemExit(code)
