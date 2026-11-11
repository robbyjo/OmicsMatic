# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind five adjacent unnamed paragraphs to exact named dndscv anchors; no relaxed evidence rule."""
import argparse,copy,hashlib,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import validate_supporting_evidence
from soupx_catalog import load_catalog_registry

LINKS={('P036',77):(76,['missense'],'This adjacent criterion completes the missense dndscv display-filter sentence in paragraph76; it is a display filter, not an extra fitting rule.'),('P036',79):(78,['nonsense'],'This adjacent criterion completes the nonsense dndscv display-filter sentence in paragraph78; preserve the stated expected/observed conditions without applying them implicitly.'),('P042',44):(43,['driver','genes'],'This next paragraph describes the consensus of the seven named driver methods including dNdSCV; consensus weighting/voting is separate from standalone dndscv inference.'),('P042',46):(45,['distance','missense','nonsense'],'This next paragraph gives the thresholds for the driver-role distance introduced using dNdSCV ratios in paragraph45; the downstream classification is a separate operation.'),('P146',25):(58,['genesets','mutations'],'The results paragraph describes the same eleven a-priori gene sets as the named dndscv methods paragraph58; paragraph24 separately preserves the pentanucleotide model statement.')}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/dndscv-runtime') and not out.exists();out.mkdir();path=ROOT/'tools/literature/dndscv-use-reviews.json';before=path.read_bytes();drafts=json.loads(before);paragraph_path=ROOT/'build/literature-review/dndscv-runtime/role-audit-1/source-paragraphs.json';paragraphs=json.loads(paragraph_path.read_bytes());updated=copy.deepcopy(drafts);seen=set();registry={r['id']:r for r in load_catalog_registry(ROOT)}
    for draft in updated:
        pid=draft['paper_id'];ps=[paragraphs[pid]['primary'],*paragraphs[pid]['supporting']];by={p['paragraph_index']:p for p in ps}
        for pointer in draft['review']['supporting_evidence']:
            key=pid,pointer['paragraph_index']
            if key not in LINKS:continue
            index,terms,note=LINKS[key];anchor=by[index];pointer.update(evidence_role='method-details',named_anchor=dict(paragraph_index=index,section=anchor['section'],paragraph_sha256=hashlib.sha256(anchor['text'].encode('utf8')).hexdigest()),shared_terms=terms,relation_note=note);seen.add(key)
        validate_supporting_evidence(ps,[draft['review']],registry)
    assert seen==set(LINKS) and len(updated)==7;save(out/'linked-reviews.json',updated);(out/'before-reviews.json').write_bytes(before);assert path.read_bytes()==before
    if a.apply:path.write_bytes((out/'linked-reviews.json').read_bytes())
    save(out/'COMPLETED.json',dict(status='passed-five-explicit-named-method-detail-links',applied=a.apply,primary_roles=7,supporting_paragraphs=11,linked_unnamed_paragraphs=5,before=pin(out/'before-reviews.json'),after=pin(out/'linked-reviews.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),paragraph_path,ROOT/'tools/literature/collect_omics_review.py']},catalog_papers_or_prior_reviews_changed=False));print('Verified all11 supporting paragraphs, including5 exact named-method links; no evidence-rule change')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable linked-source checks')
    main()
