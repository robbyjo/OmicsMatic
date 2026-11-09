# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read all250 originals; draft six observed roles and one missing spelling-variant observation."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from mrmega_catalog import load_catalog_registry

ROLES={
 'P047':('algorithm-execution','Applied recovery-curve cisTarget motif enrichment to cluster hypo-DMRs, normalized motif AUC against the motif collection, and selected motifs with AUC>0.01 and NES>3.',
  'The two10th-quantile mCG filters define each input DMR set. TF expression>0 and normalized mCH<1 in at least one enriched cluster are separate biological postfilters. Native ranked-feature AUC/NES requires the complete declared motif background and explicit strict thresholds; it does not score DNA, infer the paper database/edition, select DMRs or apply expression/methylation filters.'),
 'P058':('SCENIC-resource-and-pipeline','Used SCENIC grn, ctx with nes_threshold2.5 and min_genes10, then aucell for SPN regulon activity; downloaded mouse TF resources and the v9 mouse motif-annotation collection from cisTarget resources.',
  'The cisTarget-resource URL and SCENIC ctx invocation do not identify a separately executed cisTarget package or its edition. Coexpression inference, motif annotation/TF filtering, minimum regulon size, AUCell and cluster averaging/scaling remain pipeline operations. A native ranked-feature enrichment calculation does not reproduce this SCENIC workflow.'),
 'P061':('algorithm-execution-with-leading-edge','Ran cisTarget separately on positively and negatively correlated DMR sets for each TF, selected motifs at NES>3, and used leading-edge analysis to assign motif occurrence from Cluster-Buster scores.',
  'Positive and negative DMR sets must remain separate. Native prepared-ranking AUC/NES and exact leading-target membership do not generate Cluster-Buster scores, infer the original database/edition or reconstruct TF-DMR edge selection. The paper does not resolve Python versus R cutoff/SD conventions; select a documented source profile explicitly rather than inferring historical parity.'),
 'P065':('motif-collection-resource-only','Compared TF-Modisco patterns with the cisTarget motif collection using Tomtom to identify similar TF binding sites.',
  'This is motif-collection use through Tomtom, not evidence of a cisTarget enrichment execution. Preserve the motif-collection identity and Tomtom settings as separate requirements. Native cisTarget enrichment does not implement TF-Modisco, Tomtom motif comparison or annotation transfer.'),
 'P094':('SCENIC-plus-database-resource','Supplied precomputed human cisTarget ranking and score databases plus the SCENIC+ motif collection to the SCENIC+ eGRN pipeline with a2Mb search space on each side of gene TSSs.',
  'SEACells0.3.3 metacells, pycisTopic2.0a0 topic/candidate-region analysis and Scanpy1.8.2 RNA preprocessing are separate steps. The source does not identify an independently run cisTarget package edition. Native motif-ranking enrichment does not consume score databases as ranks or reproduce SCENIC+ eGRNs, enhancer-gene linking or the default pipeline.'),
 'P100':('pySCENIC-database-and-annotation-resource','Used pySCENIC0.12.1 with GRNBoost2 and ctx dropout masking, a1390-human-TF list, v9 human motif-to-TF annotations and the hg38 RefSeq-r80 mc9nr500bp-up/100bp-down ranking database, followed by AUCell.',
  'Five percent of cells per broad cell type were sampled. mask_dropouts=True computes TF-target correlations using cells with nonzero expression. The pySCENIC version is not a standalone cisTarget edition. Native enrichment does not reproduce coexpression inference, dropout masking, regulon pruning, AUCell/RSS, Wilcoxon differential testing or igraph/ggraph network construction.')}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';paths=[data/'papers.json',data/'tool-inventory.json',ROOT/'tools/literature/reviewed-uses.json'];before={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    papers=json.loads(paths[0].read_bytes());verify_fixed_cohort(ROOT,data,papers);reviews=json.loads(paths[2].read_bytes())['reviews'];assert len(reviews)==1012
    registry={r['id']:r for r in load_catalog_registry(ROOT)};observations=[];sources={};drafts=[];pending=[]
    for paper in papers:
        source=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(source.read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        xml=ET.fromstring(raw);assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
        ps=paragraphs(xml);matches=[p for p in ps if re.search(r'\bcis[- ]?target\b',p['match_text'],re.I)]
        assert bool(matches)==(paper['paper_id'] in {*ROLES,'P145'}),(paper['paper_id'],'unexpected primary mention')
        if not matches:continue
        if paper['paper_id']=='P145':
            assert not any(t['tool_id']=='cistarget' for t in paper['tools']);anchor=next(p for p in matches if p['paragraph_index']==53)
            pending.append(dict(paper_id='P145',pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],role_kind='pySCENIC-database-and-annotation-resource',
                proposed_tool_id='cistarget',primary_anchor=anchor,supporting_resource_paragraphs=[p for p in matches if p['paragraph_index'] in [64,71]],
                reason='The Methods spelling cis-target is absent from the registry pattern. Only a URL matched in Data availability; this is a genuine missed Methods resource observation, not a new paper or standalone software execution.',
                use_summary='Used pySCENIC0.12.1/GRNBoost2 on20000 sampled normalized/log-transformed nuclei with hg38_10kbp_up_10kbp_down_full_tx_v10 rankings and motifs-v10nr TF motifs from the cis-target database; then AUCell on the full dataset.',
                implementation_scope_note='Ranking and annotation resources feed pySCENIC. Per-sample/cell-type activity averaging and Wilcoxon comparisons are separate. A native cisTarget calculation does not reproduce GRNBoost2, regulon construction, AUCell or condition comparisons. No standalone cisTarget edition is specified.',
                catalog_action_required='Explicitly archive and extend the prior2465-observation catalog, registry spelling and source-binding verifiers; do not silently mutate a frozen predecessor population.',applied=False))
            sources[source.relative_to(ROOT).as_posix()]=pin(source);continue
        tool=next(t for t in paper['tools'] if t['tool_id']=='cistarget');anchor=next(p for p in ps if p['paragraph_index']==tool['evidence_paragraph_index'])
        assert hashlib.sha256(anchor['text'].encode()).hexdigest()==tool['evidence_paragraph_sha256']
        role,summary,scope=ROLES[paper['paper_id']];review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='cistarget',evidence_paragraph_sha256=tool['evidence_paragraph_sha256'],use_summary=summary,implementation_scope_note=scope,version_reported_near_name=None)
        validate_supporting_evidence(ps,[review],registry)
        previous=[r for r in reviews if (r['pmcid'],r['tool_id'])==(paper['pmcid'],'cistarget')]
        assert len(previous)==(1 if paper['paper_id']=='P061' else 0)
        drafts.append(dict(paper_id=paper['paper_id'],role_kind=role,review=review,prior_review=previous[0] if previous else None,action='explicit-review-supersession-required' if previous else 'new-review-draft'))
        observations.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],role_kind=role,anchor=anchor,matches=matches))
        sources[source.relative_to(ROOT).as_posix()]=pin(source)
    assert {r['paper_id'] for r in drafts}==set(ROLES) and len(pending)==1 and before=={n:pin(ROOT/n) for n in before}
    save(out/'source-roles.json',observations);save(out/'role-drafts.json',dict(status='six-primary-bound-cisTarget-role-drafts-not-applied',reviews=drafts,whole_paper_reproduced=False))
    save(out/'pending-observation.json',pending[0])
    save(out/'COMPLETED.json',dict(status='PASS_ALL250_PRIMARY_XML_RECORDS_WITH_SIX_REVIEW_DRAFTS_AND_ONE_PENDING_CISTARGET_OBSERVATION',papers_scanned=250,source_roles=7,observed_source_roles=6,pending_new_observations=1,new_review_drafts=5,explicit_supersession_drafts=1,
        catalog_before=before,catalog_unchanged=True,review_count_unchanged=1012,original_sources=sources,role_drafts=pin(out/'role-drafts.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},
        pending_observation=pin(out/'pending-observation.json'),implementation_credit=False,availability_unchanged=True,whole_goal_complete=False));print('PASS250 originals; six review drafts and one pending observation; catalog and availability unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original evidence checks')
    main()
