"""Verify complete CADD1.6 GRCh38 score-resource qualification; full prediction remains pending."""
import argparse,csv,hashlib,json
from collections import Counter
from pathlib import Path
from verify_cadd_linear import verify as verify_prior
ROOT=Path(__file__).resolve().parents[2]
DATA='docs/literature/cadd-genome-validation'
TOTAL=87762995563
IDENTITIES={
 'whole_genome_SNVs.tsv.gz':(86592987071,'a0909305601b70b05ff52daa4777312f379887a8fa888a5a20c6be90364a40bb','faaa80ef3948cf44e56a3629a90cdaaa'),
 'whole_genome_SNVs.tsv.gz.tbi':(2761623,'9ecd5038ca97ba11715e62447c874368281b0e464f666535c5d84d2ea1d87aa2','4843cab24dd4992bb0cc5f1a7ebc807a'),
 'gnomad.genomes.r3.0.indel.tsv.gz':(1165363333,'9b199b4c8a680c243a444e7d036c571b0063d901d53cac37be36c24fcb77fcce','0c4bd2698a88bb065da388448ed2dd2b'),
 'gnomad.genomes.r3.0.indel.tsv.gz.tbi':(1883536,'3bba873b2408c5f623ce47325d513056b90affa6479192deb98a82ca6758f41f','2c53e406058156da87f82f0dff6f7c4e')}
def read(p):return json.loads(Path(p).read_bytes())
def require(value,message):
 if not value:raise ValueError(message)
def pin(p,with_md5=False):
 h=hashlib.sha256();m=hashlib.md5()if with_md5 else None;n=0
 with Path(p).open('rb')as f:
  for raw in iter(lambda:f.read(8388608),b''):
   h.update(raw);n+=len(raw)
   if m is not None:m.update(raw)
 result=dict(bytes=n,sha256=h.hexdigest())
 if m is not None:result['md5']=m.hexdigest()
 return result
def source_rows(p):return[x for x in p.read_text(encoding='utf-8').splitlines()if x and not x.startswith('#')]
def tsv(p):
 with p.open(encoding='utf-8',newline='')as f:return list(csv.DictReader(f,delimiter='\t'))
def science(private):
 original=private/'original-comparisons-1';native=private/'native-Windows-1/analysis';fixture=private/'validation-inputs-1';comparison=read(private/'scientific-comparison-1/COMPLETED.json');require(comparison['status']=='PASS_ALL_NATIVE_FULL_RESOURCE_LOOKUP_ROWS_AGAINST_UNCHANGED_ORIGINAL_SOURCE','Scientific comparison missing')
 require((native/'results/lookup-variants.vcf').read_bytes()==(original/'prepare/stdout.log').read_bytes(),'Any prepared source row or row order differs')
 expected=Counter()
 for i,id in enumerate(['complete-all-snvs','complete-gnomad-indels']):expected.update((id,*line.split('\t'))for line in source_rows(original/('found-'+str(i)+'.tsv')))
 scores=tsv(native/'results/scores.tsv');actual=Counter((row['table_id'],*[row[n]for n in ['Chrom','Pos','Ref','Alt','RawScore','PHRED']])for row in scores);require(actual==expected,'Any exact original score token, duplicate hit or table priority differs')
 alleles=tsv(native/'results/alleles.tsv');provenance=read(fixture/'PROVENANCE.json');expected_original=[(str(p['input_row']),str(i+1),p['id'],p['chromosome'],str(p['position']),p['reference'],alt)for p in provenance for i,alt in enumerate(p['alternates'])];require([(a['input_row'],a['allele_index'],a['ID'],a['original_CHROM'],a['original_POS'],a['original_REF'],a['original_ALT'])for a in alleles]==expected_original,'Original allele/ID/order population differs');require((native/'inputs/original.vcf').read_bytes()==(fixture/'all-contig-boundaries.vcf').read_bytes(),'Original VCF/genotypes differ')
 remaining=['\t'.join([a[n]for n in ['lookup_CHROM','lookup_POS']]+['.']+[a[n]for n in ['lookup_REF','lookup_ALT']])for a in alleles if a['state']=='NO_SCORE_DEPENDENT_PREDICTION_SKIPPED'];require(remaining==source_rows(original/'lookup-1/stdout.log'),'Original unscored allele population differs');summary=read(native/'result-summary.json');require(summary['variants']==157 and summary['alleles']==len(alleles)==729 and summary['score_hits']==len(scores)==sum(expected.values())and summary['source_omitted_alleles']==3 and not summary['prediction_model_executed'],'Scientific population or prediction scope differs')
 bindings=read(native/'score-resource-bindings.json')['resources'];require(len(bindings)==2 and all(len(b['contigs'])==24 for b in bindings),'Complete indexed-contig populations differ')
 for b,(name,index)in zip(bindings,[('whole_genome_SNVs.tsv.gz','whole_genome_SNVs.tsv.gz.tbi'),('gnomad.genomes.r3.0.indel.tsv.gz','gnomad.genomes.r3.0.indel.tsv.gz.tbi')]):
  require((b['file_bytes'],b['sha256'])==IDENTITIES[name][:2]and(b['index_bytes'],b['index_sha256'])==IDENTITIES[index][:2],'Whole original resource binding differs')
 require(read(private/'native-Windows-1/run/RESULT.json')['exit_code']==read(private/'native-Windows-1/verify/RESULT.json')['exit_code']==read(private/'cross-verify-Linux-1/RESULT.json')['exit_code']==0,'Native execution/saved recomputation failed')
 return dict(variants=157,alleles=729,score_hits=len(scores),contigs_per_table=[24,24])
def verify(root,private=None,resources=None):
 q=read(root/DATA/'QUALIFICATION.json');require(q['status']=='PASS_COMPLETE_CADD16_GRCh38_SCORE_RESOURCES_AND_SOURCE_ENVIRONMENT_DEPLOYMENT'and q['whole_resource_bytes']==TOTAL and q['new_family_credit']==q['new_source_review_credit']==q['recovered_observation_credit']==0 and not q['raw_variant_prediction_qualified']and not q['whole_goal_complete'],'Qualification scope differs');verify_prior(root)
 for name,value in q['sources'].items():require(pin(root/name)==value,'Source/guide/qualification verifier differs: '+name)
 r=read(root/DATA/'RESOURCES.json');require(len(r['resources'])==4 and sum(x['bytes']for x in r['resources'])==TOTAL,'Whole resource set differs')
 for row in r['resources']:require((row['bytes'],row['sha256'],row['md5'])==IDENTITIES[row['name']],'Original resource identity differs')
 result=dict(status=q['status'],whole_resource_bytes=TOTAL,new_family_credit=0,raw_variant_prediction_qualified=False,private_evidence_checked=private is not None,external_resource_files_rehashed=resources is not None,whole_goal_complete=False)
 if private is not None:
  private=Path(private)
  for name,value in q['private_evidence'].items():require(pin(root/name)==value,'Retained source/scientific/failure/deployment evidence differs: '+name)
  require(pin(root/q['producer']['path'])==q['producer']['identity'],'Producing JAR differs');result.update(science(private));deployment=read(root/'build/tool-audit-917/workflow-deployment-1/COMPLETED.json');require(deployment['scientific_package_count']==316 and len(deployment['cases'])==4 and deployment['whole_original_input_preparation_rows']==6 and deployment['package_metadata_unchanged']and deployment['all_original_source_model_files_unchanged']and not deployment['full_raw_variant_prediction_executed'],'Original deployment scope or complete source input differs')
 if resources is not None:
  for name,(size,sha,md5)in IDENTITIES.items():require(pin(Path(resources)/name,True)==dict(bytes=size,sha256=sha,md5=md5),'External complete original resource hash differs: '+name)
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);p.add_argument('--resources',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None,a.resources.resolve()if a.resources else None),indent=2))
 except Exception as e:raise SystemExit('FAIL2: complete CADD resources not qualified; retain diagnostics, correct cause and use new analysis output: '+str(e))
