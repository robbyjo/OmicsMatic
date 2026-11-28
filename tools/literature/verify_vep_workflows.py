"""Check scoped VEP105 qualification, with optional complete retained private source controls."""
import argparse,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/vep-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as stream:
  for raw in iter(lambda:stream.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def vcf(p):
 lines=p.read_text(encoding='utf-8').splitlines();return([x for x in lines if x.startswith('##INFO=<ID=CSQ,')],[x for x in lines if x.startswith('#CHROM')],[x for x in lines if x and not x.startswith('#')])
def capture(path):
 seal=read(path/'capture-files.json');require(seal['schema_version']==1 and seal['authentication']is False,'Invalid capture seal');actual={}
 for p in path.rglob('*'):
  require(not p.is_symlink()and p.resolve()==path.resolve()/p.relative_to(path),'Capture path alias')
  if p.is_file()and p!=path/'capture-files.json':actual[p.relative_to(path).as_posix()]=pin(p)['sha256']
 require(actual==seal['files'],'Complete saved capture population/bytes differ');state=read(path/'analysis-status.json');require(state['status']=='COMPLETE_SCOPED_VEP'and state['results_admitted']is True,'Failed/skipped capture cannot be admitted')
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_SCOPED_ORIGINAL_VEP105_AND_NATIVE_CSQ'and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and not q['whole_paper_reproduced']and not q['whole_goal_complete'],'VEP qualification scope differs')
 for name,expected in q['sources'].items():require(pin(root/name)==expected,'VEP source/test/profile/guide/verifier differs: '+name)
 require(q['unit_tests']==dict(tests=24,failures=0,errors=0,skipped=0),'Contract-test population differs');require(q['synthetic_original_cases']==16 and q['native_imports_per_host']==16 and q['original_saved_verifications_per_host']==3 and q['negative_cases']==9,'Qualified control population differs');require('vep-tools' in(root/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_text(),'Global VEP route unavailable')
 profile=read(root/'src/main/resources/org/jlinalg/omics/vep105/RUNTIME.json');require(profile['vep_version']=='105.0'and profile['api_version']==105 and len(profile['environment']['files'])==4840 and len(profile['bioperl']['files'])==820,'Exact original core scope differs');reference=read(root/'src/main/resources/org/jlinalg/omics/vep105/REFERENCE.json');require(reference['cache_version']==105 and reference['assembly']=='GRCh38'and reference['fasta_sequences']==194 and reference['fasta_bases']==3099750718 and reference['subset_or_relabelling']is False,'Entire original reference profile differs');require('samtools1.19.2'in reference['fai_original_tool'],'Original FASTA-index producer misidentified')
 if private is not None:
  private=Path(private)
  for name,expected in q['private_evidence'].items():require(pin(private/name)==expected,'Retained original source/scientific/producer/failure evidence differs: '+name)
  for name,expected in q['producing_jars'].items():require(pin(private/name)==expected,'Retained producing JAR differs: '+name)
  with zipfile.ZipFile(private/q['final_producer'])as jar:
   for name,expected in q['final_producer_members'].items():require(hashlib.sha256(jar.read(name)).hexdigest()==expected,'Final VEP producer member differs: '+name)
  comparison=read(private/'synthetic-comparison-2/COMPLETED.json');require(comparison['status']=='PASS16_COMPLETE_TYPED_VEP105_VS_INDEPENDENT_ORIGINAL_CONTROLS','Complete synthetic comparison missing')
  for row in comparison['cases']:
   name=row['id'];require(vcf(private/'direct-synthetic-1'/name/'original.vcf')==vcf(private/'public-example-4/analysis/worker'/name/'original.vcf'),'Complete independent synthetic source comparison differs: '+name)
  for row in comparison['extra_cases']:
   kind,name=row['id'].split('-',1);require(vcf(private/'direct-extra-1'/row['id']/'original.vcf')==vcf(private/'extra-inputs-1'/(kind+'-analysis')/'worker'/name/'original.vcf'),'Complete GFF/custom source comparison differs: '+row['id'])
  for name in ['public-example-4/analysis','extra-inputs-1/custom-analysis','extra-inputs-1/gff-analysis']:capture(private/name)
  for host in ['Windows','Linux']:
   report=read(private/('portable-native-'+host+'-1/COMPLETED.json'));require(len(report['cases'])==16 and len(report['original_saved_verifications'])==3,'Portable source population differs')
   for case in report['cases']:
    capture(private/case['capture']);left=private/case['source_capture']/'results'/case['source_analysis'];right=private/case['capture']/'results/native';require({p.name:pin(p)for p in left.iterdir()}=={p.name:pin(p)for p in right.iterdir()}==case['files'],'Every portable ledger byte must match: '+host+'/'+case['id'])
  negatives=read(private/'negative-Linux-4/COMPLETED.json');require(len(negatives['cases'])==9 and all(x['expected']==x['actual']for x in negatives['cases']),'Prerequisite/source/integrity/capacity/semantic refusals differ')
  facts=read(private/'packaged-facts-1/COMPLETED.json');require(facts['pairs']==1600,'Independent source rank/impact coverage differs');original={x['SO_term']:dict(rank=int(x['rank']),impact=x['impact'])for x in read(private/'runtime-inspection-1/SOURCE-CONSTANTS.json')['terms']};require(len(original)==40,'Original source fact count differs');impact=['HIGH','MODERATE','LOW','MODIFIER'];pairs=terms=0
  for line in(private/'packaged-facts-1/facts.tsv').read_text().splitlines():
   fields=line.split('\t')
   if fields[0]=='FACT':require(original[fields[1]]==dict(rank=int(fields[2]),impact=fields[3]),'Original consequence fact differs');terms+=1
   else:
    _,a,b,ranked,worst=fields;minimum=min(original[a]['rank'],original[b]['rank']);expected=list(dict.fromkeys(x for x in[a,b]if original[x]['rank']==minimum));require(ranked==','.join(expected)and worst==min([original[a]['impact'],original[b]['impact']],key=impact.index),'Original rank tie/impact pair differs');pairs+=1
  require((terms,pairs)==(40,1600),'Native fact/pair population differs');classes=read(private/'class-comparison-1/COMPLETED.json');require(classes['other_old_classes_identical']and classes['changed_old_classes']==['org/jlinalg/cli/JLinAlgCli.class'],'Unrelated old classes changed')
  human=read(private/q['human_comparison']);require(human['status']=='PASS_COMPLETE_ORIGINAL_VEP105_HUMAN_CACHE_CONTROLS'and human['all_original_input_rows_retained']and human['cases'],'Full original human reference controls missing')
  for case in human['cases']:
   require(vcf(private/case['independent_vcf'])==vcf(private/case['adapter_vcf']),'Complete original human comparison differs: '+case['id'])
  for name in human['captures']:capture(private/name)
 return dict(status=q['status'],unit_tests=24,synthetic_original_cases=16,native_imports_per_host=16,original_saved_verifications_per_host=3,negative_cases=9,source_facts=40,source_fact_pairs=1600,human_original_cases=q['human_original_cases'],new_upstream_family_credit=1,new_native_family_credit=0,whole_paper_reproduced=False,whole_goal_complete=False,private_evidence_checked=private is not None)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete scoped VEP qualification; preserve evidence and correct cause: '+str(e))
