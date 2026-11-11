# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review two further uses found by a complete250-paper scan of the five registry additions. Preserve the original12 drafts."""
import argparse,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence

ROWS=[
('P094','commot',30,None,'spatial-pathway-and-target-activation',
 'Applied COMMOT across the spatial datasets to infer signaling pathways and obtain pathway-associated gene activations. The described target analysis uses tradeSeq associationTest and fitted smooth expression curves, retains positive associations, selects50 gene–pathway activations per sample, removes genes contributing to the pathway score, and retains the largest association per pathway–gene pair across samples.',
 'COMMOT edition, interaction database, transport parameters and full statistical settings are unreported. The target-gene procedure and pathway interpretation are downstream of the communication inference; they do not establish experimental signaling causality. Scanpy graph construction, Tangram mapping, SAW processing, Stereopy conversion and CellTypist annotation are distinct preparation tools. This spatial analysis is not CellPhoneDB permutation or supplied-DEG inference.',[16,17]),
('P169','omnipath',51,None,'transcription-factor-target-evidence-resource',
 'Obtained regulatory interaction evidence from OmniPath and DoRothEA for transcription-factor–target links, combining curated knowledge, ChIP-seq, motif and expression evidence in the effector-gene investigation. Expression-informed interactions use TCGA tumors or GTEx normal tissues.',
 'This role concerns a regulatory interaction resource, not a demonstrated OmniPath ligand–receptor communication run or client invocation. Exact resource snapshots, confidence cutoffs and extraction commands are unstated. Do not attribute all combined DoRothEA/OmniPath evidence to OmniPath alone or treat expression-inferred links as experimental proof.',[])
]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);destination=ROOT/'tools/literature/cellphonedb-additional-use-reviews.json';assert not destination.exists();initial=ROOT/'tools/literature/cellphonedb-use-reviews.json';before=pin(initial);definitions=json.loads((ROOT/'tools/literature/cellphonedb-named-software-registry.json').read_bytes());registry={r['id']:r for r in definitions};papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());drafts=[]
    for paper_id,tool,index,version,context,summary,scope,support in ROWS:
        paper=next(p for p in papers if p['paper_id']==paper_id);raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);anchor=next(p for p in ps if p['paragraph_index']==index);review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=hashlib.sha256(anchor['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=scope,usage_context=context,version_reported_near_name=version,supporting_evidence=[])
        for n in support:
            p=next(p for p in ps if p['paragraph_index']==n);review['supporting_evidence'].append(dict(paragraph_index=n,section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()))
        validate_supporting_evidence(ps,[review],registry);matched=tool_matches(ps,[registry[tool]],{tool:review['evidence_paragraph_sha256']});assert len(matched)==1 and matched[0]['evidence_paragraph_index']==index;assert publication_record(dict(paper,tools=matched),[review])['tools'][0]['use_summary']==summary;drafts.append(dict(paper_id=paper_id,pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],primary_paragraph=index,review=review))
    assert pin(initial)==before;save(destination,drafts);save(out/'COMPLETED.json',dict(status='PASS_TWO_ADDITIONAL_PRIMARY_BOUND_CELLPHONEDB_RELATED_ROLES_ONLY',draft=pin(destination),initial_twelve_drafts_unchanged=before,additional_named_observations=2,additional_supporting_pointers=2,applied=False,whole_goal_complete=False));print('Prepared2 further source-bound roles; original12 preserved; no catalog count changed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary-source checks')
    main()
