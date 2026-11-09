# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all250 original XML records and draft six separate CellChat paper roles."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort

ROLES={
 'P060':('database-resource-with-custom-score',None,[84],
  'Used CellChat ligand-receptor database entries for subclass-level analysis after restricting transferred labels to confidence above0.8; calculated a custom communication score on imputed expression.',
  'The printed product over ligand/receptor component indices is not sufficient to resolve its exact implementation. This is database-resource use, not evidence of computeCommunProb execution. Do not substitute CellChat Hill probabilities for the paper score or infer a database release/species from the package name.'),
 'P085':('condition-comparison-with-spatial-region-annotations','1.1.3',[51,52],
  'Used CellChat1.1.3 and CellChatDB.human on normalized single-cell expression with cell-type and CellTrek region annotations, excluding groups with fewer than30 cells. Separate DSAD and control objects underwent overexpression selection, PPI projection, communication inference/filtering, pathway aggregation and centrality, then merge/similarity and pathway-flow comparisons.',
  'CellTrek region annotations do not establish coordinate-based CellChat spatial-distance inference. projectData, netAnalysis_computeCentrality, mergeCellChat, functional/structural computeNetSimilarityPairwise and rankNet are separate required operations. LIANA0.1.13/Pearson comparison is a separate tool. Prepared RNA scores do not implement this complete workflow.'),
 'P090':('default-snRNA-communication','1.0',[39],
  'Used CellChatv1.0 with default settings in R4.0.3 to infer cell-type communication in DCM and control single-nucleus data using the curated ligand-receptor database and law-of-mass-action model; endocardial cells were separated from other endothelial cells.',
  'CellChat1.0.0 is an explicit available source profile, not proof of the exact historical paper commit or R4.0.3 compatibility. The original1.0.0 default raw.use is FALSE, so a prepared raw.use=TRUE reference is not a default paper reproduction. Overexpression selection and any projected-expression prerequisites remain outside the native component.'),
 'P100':('communication-input-to-FlowSig','2.1.2',[49],
  'Used CellChat2.1.2 ligand-receptor communication inference to supply unique interactions/flows for downstream FlowSig0.1.2 analysis.',
  'FlowSig prioritization, Mann-Whitney/Wilcoxon comparisons and adjusted-P<0.01 plus absolute logFC>1 thresholds are downstream requirements, not CellChat options. Prepared RNA scores do not reproduce the FlowSig causal/information-flow analysis or infer the original database release.'),
 'P145':('pooled-and-replicate-aware-condition-comparison','2.2.0',[54],
  'Used CellChat2.2.0 and CellChatDB.human excluding non-protein signaling. Replicates were pooled within each condition for default overexpression/communication inference, filterCommunication with min.samples3 and min.cells10, pathway inference and incoming/outgoing centrality; treated-minus-control strength differences above0.25 were retained. Inference was also repeated per sample for Wilcoxon comparisons across conditions.',
  'Biological sample identities and pooled versus per-sample analyses must remain distinct. The original filterCommunication code excludes groups with counts<=min.cells; do not reinterpret that as a different boundary. The three captured2.2.0 commits are source candidates and do not identify the paper commit. Prepared RNA calculations do not implement sample-consistency filtering, centrality or condition testing.'),
 'P167':('human-database-resource-in-Spyrrow-neighborhood-analysis',None,[27],
  'Combined CellChat human database entries with CellPhoneDB2.0 and iTALK into4028 ligand-receptor pairs for CosMx neighborhood analysis; Spyrrow0.1 commu.cn.call computed adjacent-region activity as the square root of sender-ligand times receiver-receptor expression.',
  'This is database-resource use through a separate Spyrrow implementation, not evidence of CellChat computeCommunProb execution. Preserve adjacency/region definitions and the combined-database provenance. CellChat Hill probabilities do not replace the neighborhood geometric-mean score, and no CellChat database release is reported.')}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';paths=[data/'papers.json',data/'tool-inventory.json',ROOT/'tools/literature/reviewed-uses.json']
    before={p.relative_to(ROOT).as_posix():pin(p) for p in paths};papers=json.loads(paths[0].read_bytes());verify_fixed_cohort(ROOT,data,papers)
    reviews=json.loads(paths[2].read_bytes())['reviews'];assert len(reviews)==1018 and not any(r['tool_id']=='cellchat' for r in reviews)
    observations=[];drafts=[];pending=[];sources={}
    for paper in papers:
        source=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(source.read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw)
        assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(xml);matches=[p for p in ps if re.search(r'\bcellchat(?:db)?\b',p['match_text'],re.I)]
        observed=[t for t in paper['tools'] if t['tool_id']=='cellchat']
        assert bool(observed)==(paper['paper_id'] in ROLES),(paper['paper_id'],'unexpected existing observation')
        if not matches:assert not observed;continue
        sources[source.relative_to(ROOT).as_posix()]=pin(source)
        if not observed:
            pending.append(dict(paper_id=paper['paper_id'],matches=matches,applied=False));continue
        tool=observed[0];anchor=next(p for p in ps if p['paragraph_index']==tool['evidence_paragraph_index'])
        assert hashlib.sha256(anchor['text'].encode()).hexdigest()==tool['evidence_paragraph_sha256']
        role,version,support,summary,scope=ROLES[paper['paper_id']]
        pointers=[dict(section=p['section'],paragraph_index=p['paragraph_index'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest(),text=p['text']) for p in ps if p['paragraph_index'] in support]
        assert len(pointers)==len(support)
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='cellchat',evidence_paragraph_sha256=tool['evidence_paragraph_sha256'],use_summary=summary,implementation_scope_note=scope,version_reported_near_name=version)
        drafts.append(dict(paper_id=paper['paper_id'],role_kind=role,review=review,supporting_primary_paragraphs=pointers,action='draft-only-not-applied'))
        observations.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],role_kind=role,anchor=anchor,matches=matches))
    assert {r['paper_id'] for r in drafts}==set(ROLES) and before=={n:pin(ROOT/n) for n in before}
    save(out/'source-roles.json',observations);save(out/'role-drafts.json',dict(status='six-primary-bound-cellchat-role-drafts-not-applied',reviews=drafts,whole_paper_reproduced=False));save(out/'pending-mentions.json',pending)
    save(out/'COMPLETED.json',dict(status='PASS_ALL250_PRIMARY_XML_CELLCHAT_AUDIT_WITH_SIX_UNAPPLIED_ROLE_DRAFTS',papers_scanned=250,observed_source_roles=6,new_review_drafts=6,pending_unobserved_mentions=len(pending),catalog_before=before,catalog_unchanged=True,review_count_unchanged=1018,original_sources=sources,role_drafts=pin(out/'role-drafts.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,availability_unchanged=True,whole_goal_complete=False))
    print('PASS250 originals; six unapplied CellChat role drafts; extra primary mentions='+str(len(pending))+'; catalog unchanged')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original evidence checks')
    main()
