#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck all 250 frozen source identities and the reviewed LDAK/SumHer roles."""
from pathlib import Path
import argparse,gzip,hashlib,json,sys,traceback,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
if not __debug__:
    raise RuntimeError('Primary-source audit assertions must remain enabled')


def audit(cache):
    curated=json.loads((ROOT/'docs/literature/ldak-workflow-development/PAPER-ROLES.json').read_bytes())
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    assert len(papers)==250 and len({p['pmcid'] for p in papers})==250
    assert curated['primary_files']==[{k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256']} for p in papers]
    roles={r['paper_id']:r for r in curated['roles']}
    assert set(roles)=={'P090','P109','P120','P171','P238','P245'}
    hits=[]
    for paper in papers:
        raw=gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],paper['paper_id']
        body=paragraphs(ET.fromstring(raw));byindex={p['paragraph_index']:p for p in body}
        for para in body:
            if any(term in para['match_text'].lower() for term in ['ldak','sumher']):
                hits.append(dict(paper_id=paper['paper_id'],paragraph_index=para['paragraph_index'],paragraph_sha256=hashlib.sha256(para['text'].encode()).hexdigest()))
        if paper['paper_id'] not in roles:continue
        role=roles[paper['paper_id']]
        for key in ['pmcid','doi','xml_sha256','fulltext_url']:assert role[key]==paper[key]
        for anchor in role['anchors']:
            para=byindex[anchor['paragraph_index']]
            assert anchor['paragraph_sha256']==hashlib.sha256(para['text'].encode()).hexdigest()
            assert anchor['section']==para['section']
    assert hits==curated['named_body_hits'] and len(hits)==10
    assert roles['P245']['usage_context']=='background-or-previous-work-not-current-ldak-execution'
    assert roles['P238']['usage_context']=='current-study-comparative-benchmark'
    return dict(status='PASS_ALL250_PRIMARY_IDENTITIES_SIX_REVIEWED_ROLES_AND_TEN_NAMED_ANCHORS',papers_scanned=250,reviewed_roles=6,named_paragraphs=10,background_or_prior_work_roles=1,pending_catalog_observation='P238',catalog_admission=False,whole_goal_complete=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--primary-cache',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    try:
        value=audit(args.primary_cache)
        (args.out/'COMPLETED.json').write_bytes((json.dumps(value,indent=2)+'\n').encode())
        print(value['status'])
    except Exception:
        (args.out/'FAILURE.txt').write_text(traceback.format_exc(),encoding='utf-8')
        print('FAIL2: retain the primary-source discrepancy; no catalog admission.',file=sys.stderr)
        raise SystemExit(2)
