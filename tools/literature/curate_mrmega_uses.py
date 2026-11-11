# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Correct unpublished draft errors against primary text; append three MR-MEGA roles."""
import argparse,copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,publication_record,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from sniffles_catalog import load_catalog_registry


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';paths=dict(papers=data/'papers.json',reviews=ROOT/'tools/literature/reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()}
    papers=json.loads(before['papers']);old=json.loads(before['reviews']);verify_fixed_cohort(ROOT,data,papers)
    assert len(old['reviews'])==1009 and sum(len(p['tools']) for p in papers)==2465
    audit=base/'primary-roles-1';prior=json.loads((audit/'COMPLETED.json').read_bytes())
    assert all(pin(ROOT/n)==v for n,v in prior['catalog_before'].items()) and pin(audit/'role-drafts.json')==prior['role_drafts']
    drafts=copy.deepcopy(json.loads((audit/'role-drafts.json').read_bytes())['reviews']);by={r['paper_id']:r['review'] for r in drafts};assert set(by)=={'P072','P105','P162'}
    by['P072']['implementation_scope_note']='The study analyzed variants present in at least six of seven cohorts and explicitly applied no genomic control before meta-analysis. Native MR-MEGA0.2 ingestion, three-axis inference, source cohort admission, heterogeneity tests and lnBF are available through mrmega-tools. CrossMap hg19 harmonization, imputation/MAF/autosomal filters and the separate PLINK random-effects analysis remain distinct. Downstream locus-normalized Bayes-factor posterior/credible-set construction, reported significance conditions and MHC/MAPT exclusion are separate from the regression output. Complete paper commands, cohort effect/CI encoding and the exact historical binary are not frozen; no whole-paper reproduction is claimed.'
    by['P105']['implementation_scope_note']='The four-axis MR-MEGA0.2 analysis is separate from the preceding METAL fixed-effect analyses. Do not infer two aggregate input cohorts from EAS/EUR labels. Native mrmega-tools supports four inferred axes and retains raw/adjusted chi-square and degree fields; explicitly named stable native survival probabilities are independently R-tested and do not overwrite original approximation fields. Exact paper cohort inputs, GC flags, source binary and recalculation code are unstated; the current tested workflow does not establish historical file or whole-paper reproduction.'
    by['P162']['use_summary']='Used MR-MEGA to examine index-variant effect heterogeneity attributable to ancestry rather than residual effects; six index variants showed significant ancestry-related heterogeneity.'
    by['P162']['implementation_scope_note']='The source does not specify the MR-MEGA edition, ancestry-axis count, complete input/test family, command or GC settings. The six significant index variants are reported findings, not evidence that only those variants were tested. Native mrmega-tools supports separate ancestry and residual tests with explicit supplied/inferred axes, but does not infer those missing paper settings or variant population. The adjacent SuSiEx credible-set analysis is separate and must not be attributed to MR-MEGA.'
    registry={r['id']:r for r in load_catalog_registry(ROOT)};updated=copy.deepcopy(papers);reviews=copy.deepcopy(old);applied=[];source_anchors={}
    for paper in updated:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        xml=ET.fromstring(raw);assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(xml);named=[p for p in ps if re.search(registry['mrmega']['pattern'],p['match_text'],re.I)]
        assert bool(named)==(paper['paper_id'] in by)
        if not named:continue
        review=by[paper['paper_id']];tool=next(t for t in paper['tools'] if t['tool_id']=='mrmega')
        assert review['pmcid']==paper['pmcid'] and review['doi']==paper['doi'] and review['evidence_paragraph_sha256']==tool['evidence_paragraph_sha256']
        paragraphs_by={p['paragraph_index']:p for p in ps}
        if paper['paper_id']=='P072':assert 'at least 6 of the 7 cohorts' in paragraphs_by[19]['text'] and 'No genomic control was applied prior' in paragraphs_by[19]['text'] and 'three principal components' in paragraphs_by[18]['text']
        if paper['paper_id']=='P162':assert 'Six of our index variants' in paragraphs_by[8]['text']
        if paper['paper_id']=='P105':assert 'four axes' in paragraphs_by[45]['text'] and 'P values were recalculated' in paragraphs_by[45]['text']
        validate_supporting_evidence(ps,[review],registry);assert not any((r['pmcid'],r['tool_id'])==(review['pmcid'],'mrmega') for r in reviews['reviews'])
        reviews['reviews'].append(review);cached=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());source=next(t for t in cached['tools'] if t['tool_id']=='mrmega')
        replacement=publication_record(dict(paper,tools=[source]),[review])['tools'][0];index=next(i for i,t in enumerate(paper['tools']) if t['tool_id']=='mrmega');paper['tools'][index]=replacement
        applied.append(dict(paper_id=paper['paper_id'],review=review));source_anchors[paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],anchor_index=tool['evidence_paragraph_index'],anchor_sha256=tool['evidence_paragraph_sha256'])
    assert len(applied)==3 and len(reviews['reviews'])==1012 and reviews['reviews'][:1009]==old['reviews']
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews'])==355
    for previous,current in zip(papers,updated,strict=True):
        assert {k:v for k,v in previous.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'}
        assert [t['tool_id'] for t in previous['tools']]==[t['tool_id'] for t in current['tools']]
        assert [t for t in previous['tools'] if t['tool_id']!='mrmega']==[t for t in current['tools'] if t['tool_id']!='mrmega']
    after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviews,before['reviews']))
    correction=dict(status='corrected-two-unpublished-MRMEGA-draft-count-errors-against-original-XML',prior_drafts=pin(audit/'role-drafts.json'),
        corrections=[dict(paper_id='P072',field='implementation_scope_note',incorrect='at least three of seven cohorts',correct='at least six of seven cohorts',source_paragraph=19),
            dict(paper_id='P162',field='use_summary-and-scope',incorrect='three significant index variants',correct='six significant index variants',source_paragraph=8)],
        primary_sources=source_anchors,prior_drafts_preserved=True,incorrect_drafts_never_applied=True,whole_paper_reproduced=False)
    save(out/'mrmega-draft-corrections.json',correction)
    save(out/'mrmega-use-reviews.json',dict(status='three-primary-bound-MRMEGA-role-reviews',reviews=applied,prior_draft_corrections=pin(out/'mrmega-draft-corrections.json'),whole_paper_reproduced=False,whole_goal_complete=False))
    for k,raw in before.items():(out/('before-'+k+'.json.gz')).write_bytes(gzip.compress(raw,mtime=0))
    for k,raw in after.items():(out/('candidate-'+k+'.json')).write_bytes(raw)
    report=dict(status='passed-three-MRMEGA-primary-role-reviews',applied=args.apply,prior_reviews=1009,unchanged_prior_reviews=1009,new_reviews=3,total_reviews=1012,supporting_pointers=355,
        source_records=250,observations=2465,changed_paper_roles=sorted(by),curator_sha256=pin(Path(__file__))['sha256'],
        before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),
        draft_pin=pin(out/'mrmega-use-reviews.json'),corrections_pin=pin(out/'mrmega-draft-corrections.json'),all_prior_reviews_and_non_mrmega_roles_preserved=True,whole_paper_reproduced=False,whole_goal_complete=False)
    if args.apply:
        from mrmega_workflow_evidence import verify
        proof,_=verify(False);assert proof['standalone_native_file_workflow_available']
        destinations=[data/'mrmega-role-coverage.json',data/'mrmega-prior-reviews.json.gz',data/'mrmega-prior-papers.json.gz',data/'mrmega-reviews-shared-source-compatibility.json',ROOT/'tools/literature/mrmega-use-reviews.json',data/'mrmega-draft-corrections.json']
        assert not any(p.exists() for p in destinations) and all(p.read_bytes()==before[k] for k,p in paths.items())
        destinations[1].write_bytes(gzip.compress(before['reviews'],mtime=0));destinations[2].write_bytes(gzip.compress(before['papers'],mtime=0));report.update(prior_review_archive=pin(destinations[1]),prior_papers_archive=pin(destinations[2]))
        row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json'
        save(destinations[3],dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mrmega',scope='Append three corrected primary MR-MEGA reviews; preserve all1009 prior reviews and all250 identities/2465 memberships. No scientific source exception.',paths=[row]))
        destinations[4].write_bytes((out/'mrmega-use-reviews.json').read_bytes());destinations[5].write_bytes((out/'mrmega-draft-corrections.json').read_bytes())
        for k,p in paths.items():p.write_bytes(after[k])
        save(destinations[0],report)
    save(out/'COMPLETED.json',report);print('Validated three corrected primary roles;1012 reviews/355 pointers; applied='+str(args.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-text corrections and prior-review preservation')
    main()
