# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare explicit public-gene annotation controls. This helper runs no analysis and reconstructs no paper's unspecified query."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,save

def query(id,attributes,values):
    return dict(id=id,attributes=attributes,filters=[dict(name='ensembl_gene_id',type='CHARACTER',values=values)],unique_rows=True,check_filters=True)

def example(manifest,out,profile):
    runtime=json.loads(manifest.read_bytes());host,release,assembly={'GRCh37':('https://grch37.ensembl.org','116','GRCh37.p13'),'GRCh38-115':('https://sep2025.archive.ensembl.org','115','GRCh38.p14'),'GRCh38-116':('https://jun2026.archive.ensembl.org','116','GRCh38.p14')}[profile];genes=['ENSG00000141510','ENSG00000139618']
    return dict(schema_version=1,edition=runtime['edition'],runtime_manifest=str(manifest.resolve()),out=str(out.resolve()),endpoint=dict(base_url=host,port=443,mart='ENSEMBL_MART_ENSEMBL',registry_version='Ensembl Genes '+release,database='ensembl_mart_'+release,dataset='hsapiens_gene_ensembl',assembly=assembly,virtual_schema='default'),queries=[query('gene-annotation',['ensembl_gene_id','hgnc_symbol','gene_biotype','chromosome_name','start_position','end_position'],genes),query('transcript-mapping',['ensembl_transcript_id','ensembl_gene_id','hgnc_symbol'],genes),query('GO-annotation',['ensembl_gene_id','go_id','name_1006','namespace_1003'],genes),query('symbols',['ensembl_gene_id','hgnc_symbol'],genes+genes[:1]),query('unmatched-control',['ensembl_gene_id','hgnc_symbol'],['ENSG00000000000'])],allow_original_html_fallback=False,timeout_seconds=600,http_timeout_seconds=60,maximum_output_bytes=536870912,maximum_http_bytes=134217728)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-manifest',required=True,type=Path);p.add_argument('--profile',choices=['GRCh37','GRCh38-115','GRCh38-116'],default='GRCh37');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);save(out/'request.json',example(a.runtime_manifest,out/'analysis',a.profile));(out/'README.txt').write_text('Public TP53/BRCA2 identifier, transcript and GO controls, plus an explicitly unmatched identifier. No paper-specific gene lists, filters, release assumptions, nearest-gene rules or enrichment tests are inferred. Require analysis exit0 and result_admitted=true; read analysis/WARNINGS.md.\n',encoding='utf8',newline='\n');print(out/'request.json')

if __name__=='__main__':main()
