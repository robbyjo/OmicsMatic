"""Prepare the complete project-synthetic15-variant/two-sample VEP example; run no analysis."""
import argparse,hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-home',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();runtime=a.runtime_home.resolve()
 if out.exists():raise RuntimeError('Preserve existing example; choose NEW output')
 out.mkdir(parents=True);archive=ROOT/'src/test/resources/org/jlinalg/genetics/loftee-original/workflow-inputs-v1.zip';files={}
 with zipfile.ZipFile(archive)as z:
  for member in z.infolist():
   if member.filename!=Path(member.filename).name or member.is_dir():raise RuntimeError('Unexpected synthetic archive member')
   raw=z.read(member)
   with(out/member.filename).open('xb')as target:target.write(raw)
   files[member.filename]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
 if sum(bool(line)and not line.startswith('#')for line in(out/'genotyped.vcf').read_text().splitlines())!=15:raise RuntimeError('Complete synthetic population differs')
 analyses=[dict(id=mode,options=dict(transcript_mode=mode))for mode in ['all','pick','pick_allele','pick_allele_gene','per_gene','flag_pick','flag_pick_allele','flag_pick_allele_gene','most_severe','summary']]
 analyses +=[dict(id='features-hgvs',options=dict(transcript_mode='all',canonical=True,biotype=True,symbol=True,protein=True,numbers=True,hgvs=True,hgvsg=True,variant_class=True)),dict(id='pick-order',options=dict(transcript_mode='pick',pick_order=['mane','canonical','appris','tsl','biotype','ccds','rank','length','ensembl','refseq']))]
 request=dict(schema_version=1,mode='RUN',backend='ORIGINAL',profile='original-VEP105.0-API105-linux64',out='analysis',input_vcf='genotyped.vcf',runtime={key:str(runtime/key)for key in ['environment','bioperl','helpers']},reference=dict(kind='gtf',annotation='genes.gtf.gz',fasta='reference.fa',fai='reference.fa.fai',species='homo_sapiens',assembly='synthetic-test',index_mode='generate-from-captured-BGZF'),analyses=analyses,threads=1,perl_hash_seed='0',timeout_seconds=600,maximum_output_bytes=100000000,maximum_resource_bytes=100000000)
 for name,value in [('request.json',request),('PREPARED.json',dict(status='PREPARED_COMPLETE_SYNTHETIC_VEP_EXAMPLE_NO_ANALYSIS',variants=15,samples=2,synthetic_fasta_bases=4000,transcripts=5,files=files,original_archive_sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),whole_paper_reproduced=False))]:
  with(out/name).open('x',encoding='utf-8')as target:json.dump(value,target,indent=2,allow_nan=False);target.write('\n')
 (out/'WARNINGS.md').write_text('# PREPARED ONLY — NO VARIANT RESULT\n\nRun vep-tools --request request.json with the current source-built JAR. Require exit0, analysis-status.json results_admitted=true, then fresh --verify. Read all original warnings. Missing or forbidden exact dependencies mean SKIP3; invalid inputs/runtime drift/process/capacity failures mean FAIL2 with no complete result. Preserve failed attempts and retry NEW paths. These15 rows and two samples are software controls, not human biological findings.\n',encoding='utf-8');print('Prepared complete synthetic example: '+str(out/'request.json'));return 0
if __name__=='__main__':
 try:raise SystemExit(main())
 except Exception as error:raise SystemExit('FAIL2: no complete VEP example; preserve partials and correct cause: '+repr(error))
