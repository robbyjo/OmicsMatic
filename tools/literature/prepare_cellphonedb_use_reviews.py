# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Draft twelve primary-bound CellPhoneDB/related roles; distinguish source execution, database reuse and five newly observed tools. Apply nothing to the current catalog."""
import argparse,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from leafcutter_catalog import load_catalog_registry

ROWS=[
('P008','cellphonedb',78,'5.1','DEG-method3-with-sex-microenvironments',
 'Used reported CellphoneDB5.1 method3 with Seurat positive cell-type/sex markers and an at-least10% expression criterion, restricting mesenchymal corpus-spongiosum–urethral-epithelium pairs to same-sex microenvironments at8–14PCW. Also used method3 for four Wolffian/Müllerian cell types at6–8PCW in one microenvironment.',
 'Method3 consumes caller-supplied DEGs; it does not perform the Seurat differential-expression tests. The captured PyPI versions do not resolve the reported5.1 label; do not silently reinterpret it as5.0.1. Database edition, remaining options and full model choices are unstated. A categorical microenvironment constraint is not a measured cell-distance model. Preserve the paper at-least10% wording separately from literal >threshold source comparisons.',[79]),
('P010','cellphonedb',96,None,'Squidpy-implementation-and-resource-context',
 'Used the Squidpy integration of CellPhoneDB and OmniPath to evaluate time-dependent receptor–ligand shifts; related mean pair values to time with Spearman correlation and Bonferroni correction, then visualized increasing/decreasing interactions.',
 'This names a Squidpy integration and interaction-resource context, not a demonstrated standalone CellPhoneDB Python/CLI run. Software/database editions, interaction-mapping details and permutation settings are unreported. Spearman/Bonferroni temporal analysis and visualization are distinct downstream steps; do not assign stock CellPhoneDB output semantics or a specific null test without source evidence.',[]),
('P070','cellphonedb',43,'2.1.4','stage-matched-legacy-communication',
 'Used CellPhoneDB.org2.1.4 separately for development-stage-matched scRNA-seq datasets; stage-matched Visium data checked spatial distance and expression patterns of ligand–receptor interactions significant atP<0.05.',
 'The complete paragraph explicitly reports2.1.4, correcting the prior missing-version extraction. Exact database, mode, permutations, expression threshold and RNG choices are unreported. Do not infer them from later-version defaults or treat Visium validation as CellPhoneDB spatial modeling. Current5.0.1 source/runtime controls would not establish historical2.1.4 execution.',[]),
('P100','cellphonedb',51,'5.0.0','cluster-label-permutation-specificity',
 'Used CellPhoneDB5.0.0 for scRNA-seq cluster interactions, requiring all interaction members in at least10% of relevant cells and permuting cell-cluster labels1000times to assess cell-type specificity of ligand–receptor expression means. Visualized pairs atP≤0.01 with CCplotR; spatial communication used COMMOT separately.',
 'Keep the reported all-subunit expression criterion,1000permutations, inclusive-tail prose and P≤0.01 visualization threshold distinct from literal source comparisons. The captured modern helper uses strict > for expression eligibility and shuffled>observed; do not silently rewrite the paper or source to reconcile this. Database edition, RNG seed, other options and biological validation are unstated. COMMOT, CCplotR and the reported RSPO3–LGR6 follow-up are separate analyses.',[25]),
('P160','cellphonedb',26,None,'neural-GPCR-module-resource-context',
 'Figure7c identifies use of a published CellPhoneDB neural–GPCR interaction module in spatially informed analysis of SAN cardiomyocytes, neighboring cell states and chromaffin cells, highlighting differentially expressed ligand–receptor components within their respective subsets.',
 'The available named-tool evidence is Results/figure context. It does not establish a standalone CellPhoneDB run, exact database/software edition, method, DEG test, null model or threshold. The >0.07 co-detection criterion describes the separate niche graph and must not be assigned to CellPhoneDB interaction significance. Preserve resource-only/unspecified execution status.',[]),
('P161','cellphonedb',70,None,'histologic-neighborhood-microenvironments',
 'Used CellPhoneDB to identify spatially plausible interactions with each pathologist-annotated histologic neighborhood as a distinct microenvironment; Results report569such interactions. Used CytoSignal separately for a spatially resolved signaling analysis.',
 'Exact CellPhoneDB software/database editions, mode, input representation, expression threshold and permutation choices are unstated. QuPath alignment/annotations, GeoPandas merging and neighborhood Wilcoxon DE are separate preparation steps. CytoSignal P/read/significance thresholds and its diffusion/contact model must not be attributed to CellPhoneDB. A microenvironment restricts cell-type pairs and does not itself infer continuous distances or diffusion.',[29,30]),
('P167','cellphonedb',27,'2.0','database-only-neighborhood-analysis',
 'Reused a reported CellPhoneDB2.0 human ligand–receptor database alongside CellChat and iTALK resources to form a combined4028-pair set for spatial-neighborhood analysis performed by Spyrrow0.1 commu.cn.call.',
 'The2.0 label is stated for the database/resource, not proof of a standalone CellPhoneDB2.0 runtime. The4028pairs belong to the combined resources, not CellPhoneDB alone. The reported activity sqrt(sender-ligand expression×receiver-receptor expression) and neighborhood calculations are attributed to Spyrrow, not CellPhoneDB arithmetic cluster means, permutations or DEG method3. Exact source database snapshot, mapping and neighborhood options remain unspecified.',[]),
('P010','omnipath',96,None,'interaction-resource-through-Squidpy',
 'Used OmniPath through Squidpy together with its CellPhoneDB integration to identify time-dependent receptor–ligand interactions, followed by temporal Spearman correlations, Bonferroni correction and visualization.',
 'The paragraph establishes interaction-resource/integration use; it does not establish a standalone OmniPath client, exact database snapshot or extraction/mapping settings. Preserve the separate Squidpy implementation and downstream temporal statistics rather than assigning them to an OmniPath runtime.',[]),
('P100','commot',51,'0.0.3','spatial-optimal-transport-communication',
 'Applied COMMOT0.0.3 with reported default parameters to spatial transcriptomics communication analysis, separately from CellPhoneDB permutation analysis on scRNA-seq.',
 'Default parameters are version-bound; exact database, spatial representation and remaining inputs are unreported. This optimal-transport method is not established by CellPhoneDB, generic spatial neighbors or a Java correlation/interaction score.',[]),
('P100','ccplotr',51,'0.99.3','interaction-visualization',
 'Visualized CellPhoneDB ligand–receptor pairs with P≤0.01 using CCplotR0.99.3.',
 'This is downstream visualization, not inference of CellPhoneDB P values or COMMOT spatial transport. Plot calls, aesthetics and complete input/output specifications are unreported; a generic plotter does not establish CCplotR source parity.',[]),
('P167','italk',27,None,'combined-ligand-receptor-resource',
 'Reused the iTALK human ligand–receptor resource alongside CellChat and CellPhoneDB2.0 for a combined4028-pair set analyzed using Spyrrow0.1.',
 'No standalone iTALK execution or exact iTALK software/database version is stated. The combined pair count and Spyrrow neighborhood/activity calculations must not be credited to iTALK alone.',[]),
('P167','spyrrow',27,'0.1','spatial-neighborhood-activity',
 'Executed Spyrrow0.1 commu.cn.call under Python3.10.5 to quantify cell-to-neighbor communication from the combined CellChat, CellPhoneDB and iTALK resources, using the reported sqrt(sender-ligand expression×receiver-receptor expression) activity; plotted results with ggplot2.',
 'Preserve the declared spatial-neighborhood restriction and formula; the complete graph construction, gene/resource mapping and remaining function arguments are unstated. This is the executing software for the reported score, not standalone CellPhoneDB inference. Exact code commit is unspecified despite the code link; source/runtime validation remains required.',[])
]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);draft=ROOT/'tools/literature/cellphonedb-use-reviews.json';assert not draft.exists();papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());old=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes());registry=load_catalog_registry(ROOT);extra_path=ROOT/'tools/literature/cellphonedb-named-software-registry.json';extra=json.loads(extra_path.read_bytes());assert len(registry)==428 and len(extra)==5 and not ({r['id'] for r in extra}&{r['id'] for r in registry});by={r['id']:r for r in registry+extra};assert len(by)==433;assert len(old['reviews'])==966 and not any(r['tool_id']=='cellphonedb' for r in old['reviews']);sources={};drafts=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();sources[paper['paper_id']]=paragraphs(root)
    for paper_id,tool,index,version,context,summary,scope,support in ROWS:
        paper=next(p for p in papers if p['paper_id']==paper_id);ps=sources[paper_id];paragraph=next(p for p in ps if p['paragraph_index']==index);digest=hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest();review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=digest,use_summary=summary,implementation_scope_note=scope,usage_context=context,version_reported_near_name=version,supporting_evidence=[])
        if version is not None:assert version in paragraph['text']
        for i in support:
            row=next(p for p in ps if p['paragraph_index']==i);review['supporting_evidence'].append(dict(paragraph_index=i,section=row['section'],paragraph_sha256=hashlib.sha256(row['text'].encode('utf8')).hexdigest()))
        validate_supporting_evidence(ps,[review],by);matches=tool_matches(ps,[by[tool]],{tool:digest});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==index;published=publication_record(dict(paper,tools=matches),[review])['tools'][0];assert published['use_summary']==summary and published['implementation_scope_note']==scope;drafts.append(dict(paper_id=paper_id,pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],primary_paragraph=index,review=review))
    assert len(drafts)==12 and len({(d['pmcid'],d['review']['tool_id']) for d in drafts})==12;save(draft,drafts);(out/'preparer-at-attempt.py').write_bytes(Path(__file__).read_bytes());save(out/'COMPLETED.json',dict(status='PASS_TWELVE_PRIMARY_BOUND_CELLPHONEDB_AND_RELATED_DRAFT_ROLES_ONLY',source_XMLs_and_DOIs_checked=250,prior_reviews_unchanged=966,cellphonedb_roles=7,new_software_definitions=5,new_named_observations=5,new_supporting_pointers=sum(len(d['review']['supporting_evidence']) for d in drafts),draft_pin=pin(draft),registry_extension=pin(extra_path),preparer_pin=pin(Path(__file__)),applied=False,implementation_credit=False,whole_goal_complete=False));print('Prepared12 primary-bound roles,5 new named software definitions and4 support pointers; catalog and implementation counts unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary-source checks')
    main()
