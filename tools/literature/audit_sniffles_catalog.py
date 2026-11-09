# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only audit of250 primary XML/DOIs,2465 observations and1009 reviewed uses."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from sniffles_catalog import current_catalog_review_context,verify_review_extension,load_catalog_registry


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers)
    registry={row['id']:row for row in load_catalog_registry(ROOT)};reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len(registry)==438 and len(papers)==250 and len(reviews)==1009
    sources={};observations=0
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw)
        assert next(node.text for node in xml.findall('front/article-meta/article-id') if node.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(xml);sources[paper['pmcid']]=ps;by={row['paragraph_index']:row for row in ps}
        cached={row['tool_id']:row for row in json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes())['tools']}
        for tool in paper['tools']:
            anchor=by[tool['evidence_paragraph_index']];assert anchor['text']==cached[tool['tool_id']]['evidence_text']
            assert hashlib.sha256(anchor['text'].encode()).hexdigest()==tool['evidence_paragraph_sha256'] and tool['tool_id'] in registry
            if not re.search(registry[tool['tool_id']]['pattern'],anchor['match_text'],re.I):
                assert (paper['paper_id'],tool['tool_id'],anchor['paragraph_index'])==('P005','htseq',54)
                assert htseq_matches([anchor],[registry['htseq']],{'htseq':tool['evidence_paragraph_sha256']})[0]['matched_text']=='HT-seq'
            observations+=1
    assert observations==2465
    with current_catalog_review_context():verify_review_extension(ROOT,data,reviews)
    by={paper['pmcid']:paper for paper in papers}
    for review in reviews:
        paper=by[review['pmcid']];assert paper['doi'].lower()==review['doi'].lower()
        tool=next(row for row in paper['tools'] if row['tool_id']==review['tool_id']);validate_supporting_evidence(sources[paper['pmcid']],[review],registry)
        for key in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']:
            if key in review:assert tool[key]==review[key]
    prior=json.loads(gzip.decompress((data/'sniffles-prior-papers.json.gz').read_bytes()));changed=[]
    for old,new in zip(prior,papers,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [t['tool_id'] for t in old['tools']]==[t['tool_id'] for t in new['tools']]
        assert [t for t in old['tools'] if t['tool_id']!='sniffles']==[t for t in new['tools'] if t['tool_id']!='sniffles']
        if old!=new:changed.append(new['paper_id'])
    assert changed==['P033','P102','P129','P164','P247','P248']
    assert sum(len(row.get('supporting_evidence',[])) for row in reviews)==353
    files=[Path(__file__).resolve(),ROOT/'tools/literature/sniffles_catalog.py',ROOT/'tools/literature/collect_omics_review.py',ROOT/'tools/literature/collect_htseq_omics.py']
    save(out/'COMPLETED.json',dict(status='passed-250-primary-XML-DOI-and-1009-reviewed-roles-with-Sniffles-availability',
        source_records=250,observations_verified=2465,reviewed_uses_verified=1009,supporting_pointers_verified=353,
        changed_paper_roles=changed,unchanged_prior_reviews=1003,all_non_sniffles_source_roles_preserved=True,
        source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in files},papers_pin=pin(data/'papers.json'),
        reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),whole_goal_complete=False))
    print('Audited250 original XML/DOIs,2465 observed memberships,1009 reviewed roles and353 supporting pointers; six Sniffles roles added')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary source and prior-role checks')
    main()
