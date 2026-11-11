# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare eight primary-bound trajectory/spatial/resource roles; catalog application is separate."""
import argparse,gzip,hashlib,json,re
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs,tool_matches,validate_supporting_evidence
from audit_cellphonedb_effective_sources import DEFINITIONS

ROWS=[
('P024','palantir',107,None,'microglial-pseudotime-comparator',
 'Ran Palantir as a trajectory-inference comparator on the microglial subset, excluding adaptive and perivascular macrophage cells. In each of200 repeats, a starting cell was sampled among donors with Braak stage0, CERAD score1 and no dementia or mild cognitive impairment. The starting cell yielding the highest Pearson correlation between inferred pseudotime and observed Braak stage was retained for the reported comparison with the neural-network predictions.',
 'The Palantir edition, actual diffusion/input representation, full hyperparameters and random seed are unreported in this paragraph. The text describes UMAP construction separately and does not establish that UMAP was the Palantir input. The reported maximum-over200 root-selection correlation is a selected comparison, not an independent held-out accuracy estimate. The py-monocle wrapper is a distinct Monocle3 comparator; do not attribute that wrapper or the neural-network fit to Palantir.'),
('P094','palantir',29,None,'multiscale-diffusion-space',
 'Used Palantir to obtain a multiscale diffusion space after PCA of the count-matrix subset containing1,500 highly variable genes, then generated an embedding from that space. The described principal trees and cell pseudotime were fitted downstream with scFates.',
 'The Palantir edition, diffusion-component count, graph/kernel parameters and full input preprocessing are not specified here. This role establishes multiscale diffusion-space construction, not a demonstrated Palantir fate-probability, terminal-state, branch-entropy or complete pseudotime workflow. The tree and pseudotime parameters reported in this paragraph belong to scFates.'),
('P094','scfates',29,None,'simpleppt-tree-and-pseudotime',
 'Fitted scFates tl.tree SimplePPT trees in two stages: a200-node tree on harmonized nose/jaw UMAP space with method=ppt,sigma=0.5,lambda=100 and Euclidean distance, and a3,000-node tree for each sample on diffusion space with sigma=0.0005,lambda=200 and Euclidean distance. Pseudotime was obtained as tree distance from selected roots and projected to cells through the soft cell-to-node assignment matrix R.',
 'The package edition, selected root identities, initialization, convergence settings and all preprocessing choices are unreported here. The200-node and3,000-node configurations are distinct analyses and must not be merged into one default. Palantir constructs the upstream multiscale diffusion space; RNA velocity and CytoTRACE are separate methods. Generic graph distance or a deterministic tree helper does not establish SimplePPT fitting parity.'),
('P094','stereo-saw',30,None,'stereo-seq-mapping-counting-segmentation',
 'Processed Stereo-seq reads with the SAW pipeline for mapping, expression counting and cell segmentation. Stereopy conversion to h5ad, CellTypist label transfer and COMMOT communication inference were performed as subsequent, distinct stages.',
 'The SAW edition, reference build, alignment/counting/segmentation options and full sample-specific settings are not reported in this paragraph. Space Ranger processes the separate Visium data; do not attribute that execution or downstream file conversion to SAW. Source/license/runtime availability requires a separate review before installation or analysis.'),
('P094','stereopy',30,'0.12.1','stereo-seq-h5ad-conversion',
 'Used Stereopy0.12.1 to convert the SAW-processed Stereo-seq data to h5ad before CellTypist1.6.3 label transfer and COMMOT signaling analysis.',
 'The conversion entry point, exact input/output schema and detailed options are not specified here. This is a conversion role, not evidence of Stereopy mapping, segmentation, CellTypist classification or COMMOT inference. Do not substitute the separately reported1.1.0 conversion from another paper without explicit version validation.'),
('P117','stereo-saw',50,'7.0.0','adrenal-stereo-seq-processing',
 'Processed adrenal Stereo-seq sequencing data with SAW7.0.0 using the GRCh38 reference. The resulting tissue.gef expression data at bin_size40 and adjusted.cellbin.gef segmented-cell expression data were used for subsequent conversion to Seurat objects.',
 'Detailed mapping, filtering and segmentation settings and the exact reference bundle are not stated. bin_size40 describes the reported binned output; no physical size or alignment setting is inferred. Stereopy1.1.0 and the separate h5ad2rds.R script perform the downstream conversion. Preserve binned versus segmented-cell data and the declared GRCh38 identity.'),
('P117','stereopy',50,'1.1.0','gef-to-anndata-and-seurat-conversion',
 'Used Stereopy1.1.0 st.io.read_gef and st.io.stereo_to_anndata to read the SAW tissue.gef/bin_size40 and adjusted.cellbin.gef outputs. The separate h5ad2rds.R script from the Stereopy repository completed the conversion to Seurat.rds.',
 'This is a two-language conversion pipeline, not evidence that stereo_to_anndata alone produces a Seurat object. The script revision, full conversion arguments and supported GEF/AnnData/R object schemas are not given. Preserve binned versus segmented-cell identities and validate both conversion stages; do not claim equivalence to the0.12.1 h5ad conversion in P094.'),
('P169','dorothea',51,None,'tf-target-regulatory-evidence-resource',
 'Obtained transcription-factor–target regulatory interactions from the DoRothEA and OmniPath resources, using evidence from curated literature, ChIP-seq peaks, binding-site motifs and expression-inferred interactions. The expression evidence derives from TCGA tumors or GTEx normal tissues.',
 'The resource snapshot, confidence-level selection, extraction commands and exact allocation of evidence between DoRothEA and OmniPath are unspecified. This is resource reuse, not a demonstrated DoRothEA/VIPER transcription-factor activity fit or ligand–receptor inference. Expression-inferred links remain distinct from experimentally confirmed regulatory effects.')
]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);destination=ROOT/'tools/literature/trajectory-spatial-use-reviews.json';assert not destination.exists();papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());registry={d['id']:d for d in DEFINITIONS};old_roles=pin(ROOT/'tools/literature/reviewed-uses.json');all_mentions=[];sources={};drafts=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);sources[paper['paper_id']]=ps
        for anchor in ps:
            for definition in DEFINITIONS:
                if re.search(definition['pattern'],anchor['match_text'],re.I):all_mentions.append(dict(paper_id=paper['paper_id'],tool_id=definition['id'],paragraph_index=anchor['paragraph_index'],section=anchor['section'],paragraph_sha256=hashlib.sha256(anchor['text'].encode()).hexdigest(),evidence_text=anchor['text'],matching_text=anchor['match_text']))
    assert {(m['paper_id'],m['tool_id'],m['paragraph_index']) for m in all_mentions}=={(r[0],r[1],r[2]) for r in ROWS} and len(all_mentions)==len(ROWS)==8,'Additional body mentions require semantic review before admitting these drafts'
    for paper_id,tool,index,version,context,summary,scope in ROWS:
        paper=next(p for p in papers if p['paper_id']==paper_id);ps=sources[paper_id];anchor=next(p for p in ps if p['paragraph_index']==index);sha=hashlib.sha256(anchor['text'].encode()).hexdigest();review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,evidence_paragraph_sha256=sha,use_summary=summary,implementation_scope_note=scope,usage_context=context,version_reported_near_name=version,supporting_evidence=[]);validate_supporting_evidence(ps,[review],registry);match=tool_matches(ps,[registry[tool]],{tool:sha});assert len(match)==1 and match[0]['evidence_paragraph_index']==index
        if version:assert re.search(r'\bv?'+re.escape(version)+r'\b',anchor['match_text']),('Version/citation separation failed',paper_id,tool)
        drafts.append(dict(paper_id=paper_id,pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],primary_paragraph=index,review=review))
    assert pin(ROOT/'tools/literature/reviewed-uses.json')==old_roles;save(destination,dict(status='eight-primary-bound-role-drafts-not-applied',registry_applied=False,implementation_credit=False,reviews=drafts,whole_goal_complete=False));save(out/'all-named-body-paragraphs.json',all_mentions);save(out/'COMPLETED.json',dict(status='passed-eight-source-bound-trajectory-spatial-resource-role-drafts',draft=pin(destination),all_body_paragraphs_scanned=True,source_records=250,distinct_named_candidates=5,observations=8,prior980_reviews_unchanged=old_roles,registry_applied=False,implementation_credit=False,whole_goal_complete=False));print('Prepared8 primary-bound role drafts over all250 bodies; retained980 applied reviews; registry/integration application remains separate')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary role/version checks')
    main()
