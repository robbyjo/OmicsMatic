# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write a complete synthetic typed request from committed controls and an explicitly prepared runtime; run no analysis."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from pack_loftee_workflow_inputs import NAMES

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-home',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--edition',choices=['grch38-1.0.4','cited-fork-c8fdde00'],default='grch38-1.0.4');a=p.parse_args();out=a.out.resolve();runtime=a.runtime_home.resolve();assert out.is_relative_to(ROOT/'build/literature-review/loftee-runtime') and not out.exists();receipt=json.loads((runtime/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_EXACT_COMPACT_LOFTEE_RUNTIME_PREPARED_ONLY';out.mkdir(parents=True);inputs=out/'inputs';inputs.mkdir();fixture=ROOT/'src/test/resources/org/jlinalg/genetics/loftee-original/workflow-inputs-v1.zip'
    with zipfile.ZipFile(fixture) as archive:
        assert len(archive.namelist())==len(set(archive.namelist()))==len(NAMES) and set(archive.namelist())==set(NAMES) and archive.testzip() is None
        for name in NAMES:
            assert '/' not in name and '\\' not in name;raw=archive.read(name);assert len(raw)<1048576;(inputs/name).write_bytes(raw)
    path=lambda name:str(inputs/name);assembly='SYNTHETIC_LOFTEE_CONTROLS';q=dict(schema_version=1,mode='ANALYZE',profile='original-VEP105.0-LOFTEE-GRCh38-Linux-x86_64',edition=a.edition,input_vcf=path('genotyped.vcf.gz'),input_compression='gzip',reference_origin='synthetic-controls',transcripts=dict(kind='gtf',file=path('genes.gtf.gz'),index=path('genes.gtf.gz.tbi'),assembly_label=assembly,release_label='five-synthetic-transcripts'),fasta=dict(file=path('reference.fa.gz'),index=path('reference.fa.gz.fai'),compression='bgzip',gzi=path('reference.fa.gz.gzi'),assembly_label=assembly,release_label='synthetic-4000-bases'),gerp=dict(kind='bigwig',file=path('gerp.bw'),assembly_label=assembly,release_label='synthetic-two-level-GERP'),ancestor=dict(kind='source-disabled'),conservation=dict(kind='source-disabled'),runtime=dict(environment=str(runtime/'environment'),bioperl=str(runtime/'bioperl'),source=str(runtime/'source'/a.edition)),analyses=[dict(id='default-GERP',settings={},ancestral_check=False,conservation_check=False,complete_cds_check='source-disabled'),dict(id='explicit-percentile',settings=dict(use_gerp_end_trunc=False),ancestral_check=False,conservation_check=False,complete_cds_check='source-disabled')],threads=1,out=str(out/'analysis'));save(out/'request.json',q);source=Path(__file__).resolve();save(out/'PREPARED.json',dict(status='prepared-synthetic-example-only',request=pin(out/'request.json'),fixture=pin(fixture),runtime_preparation_receipt=pin(runtime/'COMPLETED.json'),source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [source,ROOT/'tools/literature/pack_loftee_workflow_inputs.py']},analysis_executed=False));print('Prepared '+str(out/'request.json')+'; analysis not run. On Linux use LofteeWorkflowCli --request with this exact file, then read status and warnings.')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete synthetic fixture checks')
    main()
