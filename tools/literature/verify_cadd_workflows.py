"""Verify source-bound native CADD score components; genome-wide prediction remains unqualified."""
import argparse,collections,gzip,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/cadd-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as stream:
  for raw in iter(lambda:stream.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def capture(root):
 seal=read(root/'capture-files.json');require(seal['schema_version']==1 and seal['authentication']is False,'Invalid CADD integrity seal');actual={}
 for p in root.rglob('*'):
  require(not p.is_symlink()and p.resolve()==root.resolve()/p.relative_to(root),'CADD capture alias')
  if p.is_file()and p.name!='capture-files.json':actual[p.relative_to(root).as_posix()]=pin(p)
 require(actual==seal['files'],'Complete CADD capture bytes/population differ');state=read(root/'analysis-status.json');require(state==dict(status='COMPLETE_SCOPED_CADD_COMPONENTS',results_admitted=True,prediction_model_executed=False,whole_paper_reproduced=False),'Failed/skipped CADD capture is not admitted')
def rows(path):return[line.split('\t')for line in path.read_text(encoding='utf-8').splitlines()]
def scientific_comparison(private):
 original=private/'original-comparisons-2';inputs=private/'validation-inputs-1';require(read(original/'COMPLETED.json')['status']=='COMPLETE_UNMODIFIED_CADD16_SOURCE_COMPONENT_ORACLES','Unmodified source controls missing');counts={};result_families={}
 for host in ['Windows','Linux']:
  report=read(private/('native-'+host+'-1/COMPLETED.json'));require(report['status']=='PASS11_COMPLETE_NATIVE_CADD_COMPONENT_CASES'and len(report['cases'])==11,'Complete native case population differs');calibration_rows=lookup_cases=score_hits=0;families={}
  for case in report['cases']:
   root=private/case['capture'];capture(root);name=case['id'];actual={p.name:pin(p)for p in(root/'results').iterdir()};require(actual==case['result_files']and read(root/'result-summary.json')==case['summary'],'Native result manifest differs');families[name]=actual
   if name.startswith('calibration-'):
    assembly=name.split('-',1)[1];original_rows=rows(original/name/'stdout.log');diagnostics=[r for r in original_rows if len(r)!=3];require(diagnostics==[['get_larger: SHOULD NOT HAPPEN! 5583 max: 11167']],'Original NaN search diagnostic differs; preserve full stdout');source=[r for r in original_rows if len(r)==3];result=rows(root/'results/calibrated.tsv');submitted=rows(inputs/assembly/'scores.tsv');plan=read(inputs/assembly/'PLAN.json');require(len(result)==len(source)==len(submitted)==plan['complete_calibration_probes']+1,'Full conversion-table boundary probes differ')
    for i,(a,b,c)in enumerate(zip(source[1:],result[1:],submitted[1:]),1):require(len(a)==3 and len(b)==6 and b[:3]==[str(i),*c]and[b[1],b[3],b[4]]==a,'CADD raw formatting/PHRED differs from unchanged original source at '+host+'/'+assembly+'/'+str(i))
    require(plan['raw_conversion_rows']==11167 and plan['distinct_boundaries']==11167 and len(result)==44680,'Complete original conversion-table coverage differs');calibration_rows+=len(result)-1
   else:
    lookup_cases+=1;score_hits+=case['summary']['score_hits'];native=[tuple(row[4:])for row in rows(root/'results/scores.tsv')[1:]]
    if name.startswith('example-'):
     assembly=name.split('-')[1];expected=[tuple(row)for row in rows(original/('found-'+assembly+'.tsv'))if row and not row[0].startswith('#')];prepared=original/('prepare-'+assembly)/'stdout.log';require(case['summary']['variants']==6 and case['summary']['matched_alleles']==6,'Original six-row example was reduced or unmatched')
    else:
     mode,order=('source','forward')if name.endswith('-gzip')else name.split('-')[1:];expected=[tuple(row)for i in range(2)for row in rows(original/f'found-{mode}-{order}-{i}.tsv')if row and not row[0].startswith('#')];prepared=original/'prepare-synthetic/stdout.log'if mode=='source'else original/'literal.vcf';require(case['summary']['variants']==11 and case['summary']['alleles']==12,'Full synthetic input was reduced')
    require(collections.Counter(native)==collections.Counter(expected),'Every original source score field/hit/priority must match: '+host+'/'+name);require((root/'results/lookup-variants.vcf').read_bytes()==prepared.read_bytes(),'Original preparation/literal query representation differs: '+host+'/'+name)
  counts[host]=dict(cases=11,calibration_rows=calibration_rows,lookup_cases=lookup_cases,score_hits=score_hits);result_families[host]=families
 require(result_families['Windows']==result_families['Linux'],'Portable native result bytes differ');require(counts['Windows']==counts['Linux']==dict(cases=11,calibration_rows=89358,lookup_cases=9,score_hits=63),'Qualified scientific populations differ')
 initial=read(inputs/'INPUTS.json')
 for name,expected in initial['input_files'].items():require(pin(inputs/name)==expected,'Original complete validation input changed: '+name)
 return counts
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_NATIVE_CADD16_SOURCE_COMPONENTS'and q['new_native_family_credit']==1 and q['new_upstream_family_credit']==0 and not q['prediction_model_qualified']and not q['genome_wide_resource_qualified']and not q['whole_paper_reproduced']and not q['whole_goal_complete'],'CADD component qualification scope differs')
 for name,expected in q['sources'].items():require(pin(root/name)==expected,'CADD owned source/test/guide/verifier differs: '+name)
 require(q['unit_tests']==dict(tests=19,failures=0,errors=0,skipped=0)and q['native_cases_per_host']==11 and q['calibration_rows_per_host']==89358 and q['lookup_cases_per_host']==9 and q['score_hits_per_host']==63 and q['negative_cases']==15,'Qualified control population differs');require('cadd-tools'in(root/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_text(),'Global CADD route missing')
 if private is not None:
  private=Path(private)
  for name,expected in q['private_evidence'].items():require(pin(private/name)==expected,'Retained complete source/scientific/failure evidence differs: '+name)
  for name,expected in q['producing_jars'].items():require(pin(private/name)==expected,'Producing CADD JAR differs: '+name)
  with zipfile.ZipFile(private/q['final_producer'])as jar:
   for name,expected in q['final_producer_members'].items():require(hashlib.sha256(jar.read(name)).hexdigest()==expected,'Final producer member differs: '+name)
  scientific_comparison(private);negative=read(private/'negative-Windows-1/COMPLETED.json');require(len(negative['cases'])==15 and all(x['actual']==x['expected']for x in negative['cases']),'Packaged refusal/tamper controls differ');classes=read(private/'class-comparison-1/COMPLETED.json');require(classes['changed_old_classes']==['org/jlinalg/cli/JLinAlgCli.class']and classes['other_old_classes_identical'],'Unrelated old classes changed')
  for host in ['Windows','Linux']:require(len(read(private/('cross-verify-'+host+'-1/COMPLETED.json'))['cases'])==11,'Cross-host saved-verification population differs')
 return dict(status=q['status'],unit_tests=19,native_cases_per_host=11,calibration_rows_per_host=89358,lookup_cases_per_host=9,score_hits_per_host=63,negative_cases=15,private_evidence_checked=private is not None,prediction_model_qualified=False,genome_wide_resource_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete scoped native CADD qualification; retain evidence and correct cause: '+str(e))
