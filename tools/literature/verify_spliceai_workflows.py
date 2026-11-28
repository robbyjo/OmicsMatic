"""Verify the bounded original five-model SpliceAI1.3 workflow and all retained whole-input controls."""
import argparse,collections,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/spliceai-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def require(v,m):
 if not v:raise ValueError(m)
def data(p):return[x.split('\t')for x in p.read_text(encoding='utf-8').splitlines()if x and not x.startswith('#')]
def capture(root,original):
 seal=read(root/'capture-files.json');actual={p.relative_to(root).as_posix():pin(p)for p in root.rglob('*')if p.is_file()and p.name!='capture-files.json'};require(seal['schema_version']==1 and seal['authentication']is False and actual==seal['files'],'Whole SpliceAI capture integrity differs')
 require(all(not p.is_symlink()for p in root.rglob('*')),'Capture contains aliases');state=read(root/'analysis-status.json');require(state['status']=='COMPLETE_SCOPED_SPLICEAI_ANALYSIS'and state['results_admitted']and state['original_five_model_engine_executed']==original and state['prediction_model_executed']==original and state['whole_paper_reproduced']is False,'Failed/skipped/non-prediction result misclassified')
def scientific_comparison(private):
 source=private/'source-1/extracted/SpliceAI-409d0683bfe48bebe6dbfc96abf84629011100e8';require(read(private/'original-tests-1/RESULT.json')['exit_code']==0 and 'Ran 2 tests in' in(private/'original-tests-1/stderr.log').read_text()and(private/'original-tests-1/stderr.log').read_text().rstrip().endswith('OK'),'Both complete unchanged original tests must pass');require(data(private/'human-original-1/d50-mask0/original.vcf')==data(source/'examples/output.vcf')and(private/'human-original-1/input.vcf').read_bytes()==(source/'examples/input.vcf').read_bytes(),'Complete original published ten-row example differs')
 require(read(private/'grch38-original-3/COMPLETED.json')['reference_whole'],'Whole GRCh38 control missing');require(read(private/'grch38-original-2/bundled-d50-m0/RESULT.json')['exit_code']==1 and len(data(private/'grch38-original-2/input.vcf'))==8,'Original full failed VCF control was lost')
 count=0;expected={}
 for folder,oracle in [('original-Linux-GRCh37-2','human-original-1'),('original-Linux-GRCh38-bundled-2','grch38-original-3'),('original-Linux-GRCh38-provided-2','grch38-original-3')]:
  parent=private/folder;require(read(parent/'RESULT.json')['exit_code']==0,'Original adapter failed');root=parent/'capture';capture(root,True);request=read(root/'request.json');input=data(root/'inputs/original.vcf');require(input==data(private/oracle/'input.vcf'),'Complete source input population changed');require(len(input)==(10 if 'GRCh37' in folder else 8),'Source input row reduction');summary=read(root/'result-summary.json')
  for analysis in request['analyses']:
   name=analysis['id'];directory=root/'worker'/name;require(data(directory/'original.vcf')==data(private/oracle/name/'original.vcf'),'Any original output field differs: '+folder+'/'+name);require(all(a[:5]==b[:5]for a,b in zip(input,data(directory/'original.vcf'))),'Literal source identities changed');report=read(directory/'runtime-report.json');receipt=read(directory/'process-receipt.json');require(report['main_returned_normally']and report['model_assets_required']==5 and report['source_modified']is False and report['tensorflow_gpu_available']is False and report['unresolved_executable_library_paths']==[],'Original full CPU engine/closure differs');require(receipt['original_exit_code']==0 and all(not x['alive_after_cleanup']for x in receipt['owned_process_cleanup']),'Owned source worker survived');require(summary[name]['partial_tuples']==summary[name]['nonfinite_score_annotations']==0,'Invalid fresh numerical prediction was admitted');count+=1
  if 'provided'in folder:require(summary['provided-d50-m0']['annotations']==12 and summary['provided-d50-m0']['gene_groups']==7,'Duplicate gene contexts were dropped')
 require(count==6,'Whole original analysis population differs');families={};native_annotations={}
 for host in ['Windows','Linux']:
  report=read(private/('native-'+host+'-2/COMPLETED.json'));require(len(report['cases'])==7,'Native whole-input cases differ');cases={};annotations=0
  for case in report['cases']:
   root=private/case['capture'];capture(root,False);actual={p.name:pin(p)for p in(root/'results/native').iterdir()};require(actual==case['result_files']and case['summary']==read(root/'result-summary.json'),'Saved native result manifest differs');cases[case['id']]=actual;summary=case['summary']['native'];annotations+=summary['annotations'];file=root/'inputs/annotated.vcf'
   if case['id']!='diagnostics-gzip':
    expected=[]
    for row,f in enumerate(data(file),1):
     for token in f[7].split(';'):
      if token.startswith('SpliceAI=')and token!='SpliceAI=.':expected.extend((str(row),x.split('|'))for x in token[9:].split(','))
    actual_rows=[x.split('\t')for x in(root/'results/native/annotations.tsv').read_text(encoding='utf-8').splitlines()][1:];require([(x[0],x[3:13])for x in actual_rows]==expected,'Any literal original DS/DP/gene context differs')
   else:require(summary['variants']==7 and summary['alternate_alleles']==14 and summary['nonfinite_score_annotations']==1 and summary['partial_tuples']==2 and summary['annotations_without_gene']==1,'Whole diagnostic state population differs')
  families[host]=cases;native_annotations[host]=annotations
 require(families['Windows']==families['Linux'],'Native Windows/Linux result bytes differ');require(native_annotations==dict(Windows=68,Linux=68),'Whole source/native annotation population differs');return dict(original_analyses=6,native_cases_per_host=7,native_annotations_per_host=68,original_source_tests=2)
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_ORIGINAL_SPLICEAI13_WORKFLOW'and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and q['prediction_model_qualified']and not q['whole_paper_reproduced']and not q['whole_goal_complete'],'Scoped SpliceAI qualification differs')
 for name,expected in q['sources'].items():require(pin(root/name)==expected,'Owned source/profile/test/audit/guide differs: '+name)
 require(q['unit_tests']==dict(tests=27,failures=0,errors=0,skipped=0)and q['original_analyses']==6 and q['native_cases_per_host']==7 and q['native_annotations_per_host']==68 and q['negative_cases']==21 and q['saved_verifications_per_host']==17,'Control population differs');require('spliceai-tools'in(root/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_text(),'Global SpliceAI route missing')
 if private is not None:
  private=Path(private)
  for name,expected in q['private_evidence'].items():require(pin(private/name)==expected,'Retained source/scientific/failed evidence differs: '+name)
  for name,expected in q['producing_jars'].items():require(pin(private/name)==expected,'Retained producer JAR differs: '+name)
  with zipfile.ZipFile(private/q['final_producer'])as z:
   for name,expected in q['final_producer_members'].items():require(hashlib.sha256(z.read(name)).hexdigest()==expected,'Final producing member differs: '+name)
  scientific_comparison(private);negative=read(private/'negative-Windows-2/COMPLETED.json');require(len(negative['cases'])==17 and negative['negative_cases']==16 and all(x['actual']==x['expected']for x in negative['cases']),'Packaged native/refusal/tamper controls differ')
  for name in ['negative-original-Linux-END-1','negative-original-Linux-timeout-1','negative-original-Linux-D0-deletion-1']:r=read(private/name/'COMPLETED.json');require(r['actual']==r['expected']==2 and not r['owned_processes_alive']and r['full_input_retained']and not r['source_data_or_model_reduced'],'Original full failed analysis was incorrectly admitted')
  require(len(read(private/'negative-runtime-Linux-1/COMPLETED.json')['cases'])==2,'Runtime skip/failure controls missing')
  for host in ['Windows','Linux']:require(len(read(private/('saved-verify-'+host+'-1/COMPLETED.json'))['cases'])==17,'Saved/cross-host verification population differs')
  classes=read(private/'class-comparison-1/COMPLETED.json');require(classes['changed_old_classes']==['org/jlinalg/cli/JLinAlgCli.class']and classes['other_old_classes_identical'],'Unrelated previous classes changed')
 return dict(status=q['status'],unit_tests=27,original_source_tests=2,original_analyses=6,native_cases_per_host=7,native_annotations_per_host=68,negative_cases=21,saved_verifications_per_host=17,private_evidence_checked=private is not None,prediction_model_qualified=True,whole_paper_reproduced=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete scoped original SpliceAI qualification; retain evidence and correct cause: '+str(e))
