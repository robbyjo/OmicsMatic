"""Current native ComBat: full missing-variance failure propagation and 16 finite controls."""
import argparse,csv,hashlib,json,math,sys,zipfile
from pathlib import Path
from verify_combat346_workflows import read,pin,require,table,compare
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/combat346-revision2-validation';Q=FOLDER+'/QUALIFICATION.json'

def undefined(reference,actual):
 source=table(reference/'adjusted.tsv');native=table(actual/'adjusted.tsv');require(len(source)==len(native)and source[0]==native[0],'Undefined output dimensions differ');different=0
 for a,b in zip(source[1:],native[1:],strict=True):
  require(a[0]==b[0]and len(a)==len(b),'Undefined output row differs')
  for x,y in zip(a[1:],b[1:],strict=True):
   bad=x in ['NA','NaN','Inf','-Inf'];require(bad==(y in ['NA','NaN','Inf','-Inf']),'Complete original nonfinite mask differs')
   if not bad:require(abs(float(x)-float(y))<=2e-10*max(1,abs(float(x))),'Finite part of failed raw output differs')
   different+=x=='NA'and y=='NaN'
 return dict(complete_original_nonfinite_mask_matches=True,derived_R_NA_exported_as_NaN=different)

def verify(root,private=None):
 root=Path(root);q=read(root/Q);require(q['status']=='PASS_NATIVE_COMBAT346_REVISION2'and q['implementation_revision']==2 and q['unit_tests']==21 and q['whole_goal_complete']is False,'Qualification scope changed')
 require(all(pin(root/name)==identity for name,identity in q['sources'].items()),'Current method/test/guide/verifier differs; retain matching historical context')
 fixture=root/'src/test/resources/org/jlinalg/confounding/sva346-original-reference.zip';profile=read(root/'src/test/resources/org/jlinalg/confounding/sva346-reference-profile.json');require(pin(fixture)==profile['reference_archive'],'Reference archive changed')
 with zipfile.ZipFile(fixture)as stream:require(set(stream.namelist())==set(profile['members'])and all(dict(bytes=len(stream.read(n)),sha256=hashlib.sha256(stream.read(n)).hexdigest())==p for n,p in profile['members'].items()),'Reference file population/bytes changed')
 if private is not None:
  private=Path(private);require(all(pin(private/n)==p for n,p in q['private_evidence'].items()),'Retained source/input/producer/failure evidence changed or disappeared');producer=private/q['producer_path'];require(pin(producer)==q['producer'],'Producing JAR changed')
  with zipfile.ZipFile(producer)as archive:require(all(hashlib.sha256(archive.read(n)).hexdigest()==p for n,p in q['producer_classes'].items()),'Packaged code changed')
  reference=private/'original-reference-6';rows=list(csv.DictReader((reference/'cases.tsv').read_text().splitlines(),delimiter='\t'));require(sum(r['status']=='COMPLETE_FINITE_OBSERVED_VALUES'for r in rows)==16,'Finite source case population differs')
  for host in ['Windows','Linux']:
   folder=private/('packaged-'+host+'-2');runs=read(folder/'COMPLETED.json');require(runs['producer']==q['producer']and all(r['actual']==r['expected']for r in runs['cases']),'Packaged execution/refusal failed');finite={};failed={}
   for row in rows:
    output=folder/(row['case']+'-analysis');require(read(output/'analysis-status.json')['result_admitted']==(row['status']=='COMPLETE_FINITE_OBSERVED_VALUES'),'Original failure/undefined output admitted')
    if row['status']=='COMPLETE_FINITE_OBSERVED_VALUES':finite[row['case']]=compare(reference/row['case'],output)
    elif row['status']=='ORIGINAL_UNDEFINED_NO_ADMISSION':failed[row['case']]=undefined(reference/row['case'],output);require(read(output/'results.json')['undefined_observed_values']==int(row['new_nonfinite']),'Undefined observed-cell count differs')
   require(finite==q['full_original_comparisons'][host]and failed==q['full_undefined_masks'][host],'Complete original comparisons differ');require(sum(r['expected']==3 for r in runs['cases'])==3 and sum(r['expected']==2 for r in runs['cases'])==10,'Refusal population differs')
 return dict(status='PASS_CURRENT_NATIVE_COMBAT346_REVISION2',implementation_revision=2,unit_tests=21,original_finite_cases_per_host=16,full_original_finite_values_per_host=q['finite_comparisons_per_host'],full_original_undefined_masks_per_host=2,fresh_native_recomputations=33,refusals_per_host=dict(SKIP3=3,FAIL2=10),private_evidence_checked=private is not None,new_extra_family_credit=0,total_native_combat_family_credit=1,whole_paper_reproduced=False,whole_goal_complete=False)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Verification skipped; restore the named permitted evidence. 'if code==3 else'FAIL2: No complete revision2 qualification. ')+str(error),file=sys.stderr);raise SystemExit(code)
