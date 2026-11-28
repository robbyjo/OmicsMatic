"""Verify scoped WGCNA qualification; optional complete retained private controls."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/wgcna-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as stream:
  for raw in iter(lambda:stream.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def compare(a,b):
 left=list(csv.reader(io.StringIO(a.decode('utf-8')),delimiter='\t'));right=list(csv.reader(io.StringIO(b.decode('utf-8')),delimiter='\t'));require(left and right and left[0]==right[0]and len(left)==len(right),'Complete result row/header population differs');maximum=0.;finite=missing=0
 for x,y in zip(left[1:],right[1:],strict=True):
  require(len(x)==len(y)and x[0]==y[0],'Literal result identity/order differs')
  for u,v in zip(x[1:],y[1:],strict=True):
   if u in ['NA','NaN']:require(v in ['NA','NaN'],'Original missingness differs');missing+=1
   elif u in ['Inf','-Inf']:require(u==v,'Original infinity differs')
   else:
    try:f,g=float(u),float(v)
    except ValueError:require(u==v,'Literal module/label differs');continue
    error=abs(f-g);require(math.isfinite(g)and error<=1e-10+1e-8*abs(f),'Full original numeric result differs');maximum=max(maximum,error);finite+=1
 return dict(rows=len(left)-1,finite=finite,missing=missing,maximum_absolute_error=maximum)
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_SCOPED_WGCNA1721'and q['new_upstream_family_credit']==1 and not q['whole_goal_complete']and not q['whole_paper_reproduced'],'Qualification scope changed')
 for name,expected in q['sources'].items():require(pin(root/name)==expected,'Current WGCNA source/test/guide/verifier differs: '+name)
 require(pin(root/q['numeric_fixture'])==q['numeric_fixture_identity'],'Synthetic fixture changed')
 with zipfile.ZipFile(root/q['numeric_fixture'])as fixture:
  require(set(fixture.namelist())==set(q['numeric_members']),'Synthetic fixture population differs')
  for name,expected in q['numeric_members'].items():raw=fixture.read(name);require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected,'Synthetic member changed')
  if private is not None:
   private=Path(private)
   for name,expected in q['private_evidence'].items():require(pin(private/name)==expected,'Retained producer/source/scientific/failure evidence changed: '+name)
   require(pin(private/q['producer_path'])==q['producer'],'Final producing JAR changed')
   with zipfile.ZipFile(private/q['producer_path'])as jar:
    for name,sha in q['producer_members'].items():require(hashlib.sha256(jar.read(name)).hexdigest()==sha,'Producing class/resource changed')
   for host,folder in q['component_captures'].items():
    result=read(private/folder/'COMPLETED.json');require(result['producer']==q['producer'],'Component producer differs');actual=[]
    for item in result['cases']:
     name=item['id'];capture=private/folder/(name+'-analysis');file='connectivity.tsv'if name.startswith('connectivity-')else'rank-pvalues.tsv';metrics=compare(fixture.read(name+'/expected.tsv'),(capture/'results'/file).read_bytes());require(metrics=={k:item[k]for k in metrics},'Full component comparison changed');state=read(capture/'analysis-status.json');require(state['results_admitted']and state['status']=='COMPLETE_SCOPED_WGCNA','Failed component admitted');actual.append(name)
    require(len(actual)==(22 if host=='Windows'else 23),'Component case population differs')
   full=private/q['full_workflow_capture'];result=read(full/'COMPLETED.json');require(result['producer']==q['producer']and result['population']['rows']==135 and result['population']['columns']==3600 and result['population']['samples_removed']==0 and result['population']['features_removed']==0,'Full original population/producer differs')
   for name in ['modules.tsv','eigengenes.tsv','membership.tsv','connectivity.tsv']:require(compare((private/'direct-original-1/full-liver'/name).read_bytes(),(full/'network-analysis/results'/name).read_bytes())==result['comparisons'][name],'Full direct original network comparison changed')
   for key,a,b in [('native-connectivity','network-analysis/results/connectivity.tsv','native-connectivity-analysis/results/connectivity.tsv'),('signed-kme','network-analysis/results/membership.tsv','signed-kme-analysis/results/membership.tsv')]:require(compare((full/a).read_bytes(),(full/b).read_bytes())==result['comparisons'][key],'Original full component comparison changed')
   negative=read(private/q['negative_capture']/'COMPLETED.json');require(negative['producer']==q['producer']and len(negative['cases'])==8 and all(x['actual']==x['expected']for x in negative['cases']),'Negative refusals changed')
   offline=read(private/q['offline_capture']/'COMPLETED.json');require(offline['producer']==q['producer']and len(offline['cases'])==12 and all(x['exit_code']==0 for x in offline['cases']),'Both-host R-free saved verification changed')
   bicor=private/q['bicor_capture'];require(compare((bicor/'expected.tsv').read_bytes(),(bicor/'analysis/results/membership.tsv').read_bytes())==read(bicor/'COMPLETED.json')['comparison'],'Full original bicor membership comparison differs')
   classes=read(private/'class-comparison-1/COMPLETED.json');require(classes['other_old_classes_identical']and classes['changed_old_classes']==['org/jlinalg/cli/JLinAlgCli.class'],'Unrelated old classes changed')
 profile=read(root/'src/main/resources/org/jlinalg/omics/wgcna1721/SOURCE-PROFILE.json');require(sum(len(x)for x in profile['package_files_md5'].values())==214 and len(profile['source_function_text_md5'])==8 and len(profile['package_versions'])==16,'Original source/runtime guard scope differs');require(q['unit_tests']==dict(tests=23,failures=0,errors=0,skipped=0),'Unit result differs')
 return dict(status=q['status'],unit_tests=23,component_cases_Windows=22,component_cases_Linux=23,full_input_samples=135,full_input_probes=3600,original_and_native_connectivity_rows=3600,negative_cases=8,new_upstream_family_credit=1,new_native_family_credit=0,whole_paper_reproduced=False,whole_goal_complete=False,private_evidence_checked=private is not None)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no scoped WGCNA qualification admitted; retain matching producers/evidence and correct cause: '+str(e))
