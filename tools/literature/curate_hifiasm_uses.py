# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare seven source-bound hifiasm reviews; apply only after complete candidate evidence passes."""
import argparse,copy,gzip,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import publication_record


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--audit',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);audit=a.audit.resolve();proof=json.loads((audit/'COMPLETED.json').read_bytes());assert proof['status']=='passed-250-primary-XML-DOI-and-2465-anchors-with-seven-hifiasm-role-drafts-only' and pin(audit/'role-drafts.json')==proof['role_drafts'];drafts=json.loads((audit/'role-drafts.json').read_bytes())['reviews'];records=json.loads((audit/'source-audit.json').read_bytes())['papers'];assert len(drafts)==len(records)==7;current=ROOT/'tools/literature/reviewed-uses.json';before=current.read_bytes();reviews=json.loads(before);assert len(reviews['reviews'])==993 and not any(r['tool_id']=='hifiasm' for r in reviews['reviews']);prepared=[]
    for role,record in zip(drafts,records,strict=True):
        role=copy.deepcopy(role)
        for field in ['use_summary','implementation_scope_note']:
            for old,new in [('hifiasm0','hifiasm 0'),('Yak0','Yak 0'),('RagTag2','RagTag 2'),('from32','from 32'),('assembled54','assembled 54'),('versions0','versions 0'),('and0.','and 0.'),('xref11','xref 11'),('length>100kb','length >100 kb'),('QV>10','QV >10')]:role[field]=role[field].replace(old,new)
        assert role['pmcid']==record['pmcid'] and role['doi']==record['doi'] and role['evidence_paragraph_sha256']==record['review']['evidence_paragraph_sha256'];prepared.append(dict(paper_id=record['paper_id'],review=role))
    draftpath=ROOT/'tools/literature/hifiasm-use-reviews.json';document=dict(status='seven-primary-bound-hifiasm-role-reviews',reviews=prepared,source_audit=dict(path=(audit/'source-audit.json').relative_to(ROOT).as_posix(),**pin(audit/'source-audit.json')))
    if draftpath.exists():assert json.loads(draftpath.read_bytes())==document,'Existing role draft differs; preserve it and review before applying'
    else:save(draftpath,document)
    state=dict(status='prepared-seven-primary-hifiasm-reviews-without-catalog-credit',applied=False,draft=pin(draftpath),prior_reviews=993,new_reviews=7,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    if a.apply:
        from hifiasm_execution_evidence import verify
        execution=verify(False);assert execution['full_declared_hifiasm_workflow_admitted'];data=ROOT/'docs/literature/omics-2023-2026';oldpapers=(data/'papers.json').read_bytes();new=copy.deepcopy(reviews);new['reviews'].extend(r['review'] for r in prepared);assert len(new['reviews'])==1000 and len({(r['pmcid'],r['tool_id']) for r in new['reviews']})==1000 and sum(len(r.get('supporting_evidence',[])) for r in new['reviews'])==347
        papers=json.loads(oldpapers);updated=copy.deepcopy(papers);by={r['paper_id']:r['review'] for r in prepared};changed=[]
        for paper in updated:
            if paper['paper_id'] not in by:continue
            review=by[paper['paper_id']];cached=json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes());tool=next(t for t in cached['tools'] if t['tool_id']=='hifiasm');replacement=publication_record(dict(paper,tools=[tool]),[review])['tools'][0];index=next(i for i,t in enumerate(paper['tools']) if t['tool_id']=='hifiasm');assert paper['tools'][index]['evidence_paragraph_sha256']==review['evidence_paragraph_sha256'];paper['tools'][index]=replacement;changed.append(paper['paper_id'])
        assert changed==['P068','P101','P109','P164','P171','P235','P248']
        for prior,after in zip(papers,updated,strict=True):assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in after.items() if k!='tools'} and [t for t in prior['tools'] if t['tool_id']!='hifiasm']==[t for t in after['tools'] if t['tool_id']!='hifiasm']
        for name,raw in [('hifiasm-prior-reviews.json.gz',before),('hifiasm-prior-papers.json.gz',oldpapers)]:path=data/name;assert not path.exists();path.write_bytes(gzip.compress(raw,mtime=0))
        (data/'papers.json').write_bytes(serialize(updated,oldpapers))
        save(current,new);after=current.read_bytes();row=inverse(before,after);row['path']='tools/literature/reviewed-uses.json';save(data/'hifiasm-reviews-shared-source-compatibility.json',dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='hifiasm',scope='Append seven source-bound hifiasm roles only. All993 previous reviews are byte-semantically preserved; no scientific source exception.',paths=[row]));coverage=dict(status='passed-seven-hifiasm-primary-role-reviews',applied=True,prior_reviews=993,unchanged_prior_reviews=993,new_reviews=7,total_reviews=1000,supporting_pointers=347,before_reviews_sha256=pin(out/'reviews-before.json')['sha256'] if (out/'reviews-before.json').exists() else __import__('hashlib').sha256(before).hexdigest(),reviews_sha256=pin(current)['sha256'],draft_pin=pin(draftpath),curator_sha256=pin(Path(__file__).resolve())['sha256'],prior_review_archive=pin(data/'hifiasm-prior-reviews.json.gz'),prior_papers_archive=pin(data/'hifiasm-prior-papers.json.gz'),all_non_hifiasm_reviews_preserved=True,whole_goal_complete=False);save(data/'hifiasm-role-coverage.json',coverage);state.update(status='passed-applied-seven-hifiasm-primary-role-reviews',applied=True,coverage=pin(data/'hifiasm-role-coverage.json'))
    save(out/'COMPLETED.json',state);print(state['status'])


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-role and prior-review preservation checks')
    main()
