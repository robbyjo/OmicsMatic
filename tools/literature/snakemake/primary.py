#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit the frozen 250-paper corpus for Snakemake roles; never change catalog credit."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
if not __debug__:raise RuntimeError('Primary-source assertions must remain enabled')
sys.dont_write_bytecode=True;sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs

ROLES={
 'P027':dict(anchor='c1b487914e2beb6b7e8dc4326a06744068f5f47e9157a09cdafc0a1f37294760',role='Executed a modified CLAPAnalysis RIP-seq pipeline: trimming/QC, repeat/structural-RNA filtering and C. elegans genome alignment.',separate='Author workflow revisions, aligner/reference options, read inputs and downstream RIP-seq biology remain separate.'),
 'P061':dict(anchor='01492bd14276821d60f4fd23c7b5752acc6d148f829b0b2cb3960d7fe72c14ea',role='Orchestrated large-scale analysis pipelines producing Zarr tensors on cloud infrastructure, alongside ALLCools, Xarray and dask.',separate='SkyPilot cloud provisioning, Google Cloud services, methylation/3D-genome algorithms and author pipeline deployment remain separate.'),
 'P181':dict(anchor='9609598cff0947e60e15740c56156b088b1710c8613a203b6495e5775707ad28',role='Used Snakemake benchmark reports to compare LaDDR and Pantry resource use for 100 Geuvadis samples: summed cpu_time and took maximum max_rss per pipeline.',separate='The native aggregator consumes recorded metrics; it does not measure jobs, recreate the 100-sample experiment, infer repeat assignment, or estimate concurrent whole-pipeline peak memory.'),
 'P182':dict(anchor='519f224c8489e92e951812f943dfe4087329c8e8fff25bdb3bc55c6660c2f382',role='Managed cartilage bulk RNA-seq alignment, counting, transcript-expression aggregation and QC using STAR, featureCounts, RSEM, Picard, dupRadar and RSeQC.',separate='Tool versions, references and complete downstream QC/counting semantics need independent inputs and exact author commands; workflow-engine completion alone is insufficient.'),
 'P190':dict(anchor='6f727a57e81a73afb5aff6c10c152c66b7f6252498d40e0762b8da5e2795b23d',role='Provides a Snakemake pipeline with MAJIQTL code to facilitate running the method; author contributions independently identify development of the pipeline.',separate='This is pipeline-code availability. It does not establish that the reported analyses were executed through that pipeline, or establish an engine version, workflow revision or execution settings.'),
 'P210':dict(anchor='70766d01c000238d94f7135d622e5a3d71f73e604f7d63c21eaa6c3ee5ee36d3',role='Used a SHAPEIT5 Snakemake wrapper for exome/array phasing, prioritizing exome calls at overlap and using common-variant phasing to scaffold rarer variants.',separate='Genotype harmonization, SHAPEIT5 reference/scaffold details, cohort data and recessive burden tests remain separate.'),
}
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def audit(cache):
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());assert len(papers)==250
    inventory=json.loads((ROOT/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes());tool=next(t for t in inventory if t['id']=='snakemake');assert tool['implementation_status']=='missing' and set(tool['papers'])==set(ROLES)-{'P190'}
    primary=[];roles=[];extra=[]
    for paper in papers:
        raw=gzip.decompress((cache/(paper['pmcid']+'.xml.gz')).read_bytes());assert pin(raw)['sha256']==paper['xml_sha256'];primary.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],**pin(raw)))
        article=ET.fromstring(raw);body=paragraphs(article);matches=[p for p in body if re.search(r'(?<![A-Za-z0-9_])snakemake(?![A-Za-z0-9_])',p['match_text'],re.I)]
        if paper['paper_id'] not in ROLES:
            if matches:extra.append(dict(paper_id=paper['paper_id'],anchors=[dict(index=p['paragraph_index'],section=p['section'],sha256=pin(p['text'].encode())['sha256']) for p in matches]))
            continue
        role=ROLES[paper['paper_id']];anchors=[p for p in body if pin(p['text'].encode())['sha256']==role['anchor']];assert len(anchors)==1 and anchors[0] in matches,paper['paper_id'];anchor=anchors[0]
        saved=[t for t in paper['tools'] if t['tool_id']=='snakemake']
        if paper['paper_id']=='P190':
            assert not saved
            support=next(p for p in body if p['paragraph_index']==65);assert pin(support['text'].encode())['sha256']=='21617d34baaab0bc99ee7cbb405d837dc87b98dd5a371e17cc322e80d905e5ae'
        else:assert len(saved)==1 and saved[0]['evidence_paragraph_sha256']==role['anchor'] and saved[0]['evidence_paragraph_index']==anchor['paragraph_index']
        links=sorted({e.get('href') or e.get('{http://www.w3.org/1999/xlink}href') for e in article.iter('ext-link') if (e.get('href') or e.get('{http://www.w3.org/1999/xlink}href') or '').startswith(('https://github.com/','http://github.com/'))})
        roles.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],fulltext_url=paper['fulltext_url'],xml_sha256=paper['xml_sha256'],anchor_index=anchor['paragraph_index'],anchor_section=anchor['section'],anchor_sha256=role['anchor'],use_summary=role['role'],unresolved=role['separate'],usage_context='pipeline-code-availability' if paper['paper_id']=='P190' else 'reported-execution-or-benchmarking',new_catalog_observation_pending=not saved,engine_execution_established=paper['paper_id']!='P190',engine_version_established=False,selected_project_edition='9.27.1',selected_edition_is_author_runtime=False,article_github_links=links,whole_paper_reproduced=False))
    assert not extra and len(roles)==6
    return dict(status='PASS_FROZEN_PRIMARY_SNAKEMAKE_ROLE_AUDIT_NOT_CATALOG_ADMISSION',papers_scanned=250,primary_files=primary,roles=roles,additional_body_matches=extra,new_observation_pending=['P190'],existing_catalog_changed=False,new_catalog_credit=0,whole_goal_complete=False)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    try:
        result=audit(args.primary_cache);(args.out/'CENSUS.json').write_bytes(encoded(result));print(result['status']);return 0
    except Exception as error:
        (args.out/'FAILURE.json').write_bytes(encoded(dict(error=repr(error),traceback=traceback.format_exc(),required_action='Restore the exact permitted primary XML cache or resolve the source discrepancy. Retain the failed audit. No catalog credit or paper reproduction is admitted.')));raise
if __name__=='__main__':raise SystemExit(main())
