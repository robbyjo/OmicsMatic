# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture current official BioMart registry availability without substituting an archive/release or executing gene queries."""
import argparse,concurrent.futures,json,urllib.error,urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save

SERVICES=[('main','https://www.ensembl.org/biomart/martservice?type=registry'),('useast','https://useast.ensembl.org/biomart/martservice?type=registry'),('grch37','https://grch37.ensembl.org/biomart/martservice?type=registry'),('grch37-mart','https://grch37-mart.ensembl.org/biomart/martservice?type=registry'),('sep2025-archive','https://sep2025.archive.ensembl.org/biomart/martservice?type=registry')]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();(out/'probe-at-attempt.py').write_bytes(source.read_bytes())
    def run(item):
        name,url=item;state=dict(name=name,url=url,status='SKIP3_BIOMART_SERVICE_UNAVAILABLE',gene_query_executed=False)
        try:
            try:response=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-BioMart-availability-audit/1.0'}),timeout=60)
            except urllib.error.HTTPError as error:response=error
            with response:
                raw=response.read(2097153);assert len(raw)<=2097152;(out/(name+'.response')).write_bytes(raw);state.update(http_status=response.status,final_url=response.url,headers=dict(response.headers.items()),response=pin(out/(name+'.response')))
            root=ET.fromstring(raw);assert root.tag=='MartRegistry';locations=[e.attrib for e in root if e.tag in ['MartURLLocation','MartDBLocation']];assert locations and response.status==200;state.update(status='PASS_BIOMART_REGISTRY_RESPONSE_ONLY',locations=locations)
        except Exception as error:state['error']=repr(error)
        save(out/(name+'.json'),state);print(name,state['status'],state.get('http_status',''),flush=True);return state
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:reports=list(pool.map(run,SERVICES))
    save(out/'COMPLETED.json',dict(status='captured-current-official-BioMart-registry-availability-only',services=reports,source_pins={source.relative_to(ROOT).as_posix():pin(source)},gene_annotation_executed=False,unavailable_endpoints_must_skip_dependent_analysis=True,archive_or_genome_substitution_permitted=False,implementation_credit=False));print('Registry discovery only; do not infer annotation or paper reproduction from this check')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable service/response completeness checks')
    main()
