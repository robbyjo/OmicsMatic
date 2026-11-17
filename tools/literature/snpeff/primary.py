#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck all frozen primary sources and the four individually reviewed SnpEff roles."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
if not __debug__:raise RuntimeError('Primary-source assertions must remain enabled')
EXPECTED={
 'P002':('722cb9ece32577a1369440037867ec4968d2a1a79a5fc4ba09b2a7bbd71a76c1','3.1i'),
 'P045':('c93bec89f15621cc74a82a97488091ce1edf9cc0a5443b4b8a8b20a4261a40c5',None),
 'P171':('7fa6c4098d2d204956135f0569f0d61124b1d1dd5ad3dbec4e197e4f4b53a890','5.2'),
 'P207':('dfe248c08bba34e3702e911e48ea148dd9ed9ff8ad1abaf32b7871464363d3d7',None)}
CURATION='docs/literature/snpeff-workflow-development/PAPER-ROLES.json'

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def audit(cache,papers=None):
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes()) if papers is None else papers
    assert len(papers)==250 and len({p['pmcid'] for p in papers})==250
    curated=json.loads((ROOT/CURATION).read_bytes());assert curated['papers_scanned']==250
    roles={row['paper_id']:row for row in curated['roles']};assert set(roles)==set(EXPECTED)
    primary=[];result=[];unexpected=[]
    for paper in papers:
        raw=gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert rawpin(raw)['sha256']==paper['xml_sha256'],paper['paper_id']
        primary.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],**rawpin(raw)))
        body=paragraphs(ET.fromstring(raw))
        hits=[row for row in body if re.search(r'(?<![A-Za-z0-9])snp[\s-]*eff(?![A-Za-z0-9])',row['match_text'],re.I)]
        if paper['paper_id'] not in EXPECTED:
            if hits:unexpected.append(dict(paper_id=paper['paper_id'],paragraphs=[row['paragraph_index'] for row in hits]))
            continue
        digest,version=EXPECTED[paper['paper_id']]
        assert len(hits)==1 and rawpin(hits[0]['text'].encode())['sha256']==digest,paper['paper_id']
        anchor=hits[0];source=roles[paper['paper_id']]
        assert source['pmcid']==paper['pmcid'] and source['xml_sha256']==paper['xml_sha256'] and source['paragraph_sha256']==digest and source['paragraph_index']==anchor['paragraph_index']
        existing=[row for row in paper['tools'] if row['tool_id']=='snpeff'];assert len(existing)==1
        assert existing[0]['evidence_paragraph_sha256']==digest and existing[0]['version_reported_near_name']==version
        result.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],fulltext_url=paper['fulltext_url'],anchor_index=anchor['paragraph_index'],anchor_section=anchor['section'],anchor_sha256=digest,use_summary=source['use'],unresolved=source['separate'],reported_edition=source['reported_edition'],reported_reference=source['reference'],reported_engine_version_known=version is not None,selected_profile='3.1i-r354-source' if paper['paper_id']=='P002' else '5.2',selected_runtime_is_author_binary=False,whole_paper_reproduced=False))
    assert not unexpected and len(result)==4
    return dict(status='PASS_ALL250_PRIMARY_IDENTITIES_AND_FOUR_SOURCE_REVIEWED_SNPEFF_ROLES',papers_scanned=250,primary_files=primary,roles=result,additional_body_matches=unexpected,observations_added=0,whole_goal_complete=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    args.out.mkdir(parents=True,exist_ok=False)
    try:
        value=audit(args.primary_cache);(args.out/'CENSUS.json').write_bytes(encoded(value));print(value['status']);return 0
    except Exception:
        (args.out/'FAILURE.txt').write_text(traceback.format_exc(),encoding='utf-8');print('FAIL2: retain source diagnostics, resolve the source mismatch and retry NEW output; no catalog credit.',file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
