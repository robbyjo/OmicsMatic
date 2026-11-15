#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Curated source-specific readings; writes no admitted catalog metadata."""
from pathlib import Path
import argparse,copy,hashlib,json,sys
sys.dont_write_bytecode=True
from audit import ROOT,audit

SUMMARIES={
 'P051':'Annotated mouse neutrophil datasets individually against Immgen for NeuMap construction (release unreported at that anchor); separately used SingleR 2.8.0 with ImmGen for neutrophil identification, and SingleR 2.6.0 with the literally described MouseRNAseq ImmGen references via celldex 1.14.0 plus curated markers in the PDAC/MI analysis. These are distinct analyses; subsequent Seurat projection and SPOTlight deconvolution are separate methods.',
 'P082':'Matched leukemic cells to healthy bone-marrow counterparts by Spearman correlation against a published CITE-seq reference containing 30,672 RNA profiles and 25 antibodies. The study software inventory declares SingleR 2.2.0; the method paragraph does not state function arguments or that release locally.',
 'P192':'Annotated KOBS SAT nuclei with SingleR 1.2.4 and the reference datasets used previously in the cited Pan et al. study; separately annotated RYSA SAT nuclei with SingleR 1.8.1 and the Emont adipose reference atlas. The two reference and release declarations are retained independently.',
 'P233':'Used SingleR, literally reported as 2.41, in residual blood-cell identification/removal alongside Seurat PBMC-reference analysis. That release spelling remains unresolved. The inspected author notebook separately performs four SingleR calls with HPCA and Blueprint/ENCODE main/fine labels, then filters using unpruned labels, and performs a separate Seurat PBMC mapping. Its actual executed commit, release and reference snapshots are unknown; the haemoglobin-expression filter is a separate QC step.'}

def build():
    census=audit();papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    records={p['paper_id']:p for p in papers};reviews=[]
    unknowns=['exact original function arguments','marker selection settings','correlation quantile','fine-tuning settings',
      'pruning settings','reference object snapshot and exact label vector','caller RNG state','actual author environment']
    analyses={
      'P051':[
        dict(paragraph_index=81,version_reported_at_this_anchor=None,role='Per-dataset mouse cell annotation for NeuMap',reference_literal='Immgen database',
          separate_operations=['Rhapsody analysis pipeline 1.9.1 processing','manual second-round QC','Seurat integration and later MapQuery/TransferData projection']),
        dict(paragraph_index=93,version_reported_at_this_anchor='2.8.0',role='Cell-type identification and neutrophil subset selection',reference_literal='ImmGen'),
        dict(paragraph_index=119,version_reported_at_this_anchor='2.6.0',role='Reference-based annotation in PDAC and MI analysis',
          reference_literal='MouseRNAseq ImmGen databases',reference_loader_software='celldex',reference_loader_version='1.14.0',
          additional_annotation_basis='curated marker genes from the dataset clustering results',
          heading_is_human_neutrophils=True,reference_species_description_repaired=False,
          separate_operations=['Seurat integration/clustering/marker extraction','SPOTlight 1.0.3 spatial deconvolution and >=10% neutrophil spot rule'])],
      'P082':[dict(paragraph_index=45,version_reported_at_this_anchor=None,role='Match leukemic cells to healthy bone-marrow counterparts',
        annotation_basis_reported='Spearman correlation',reference_RNA_profiles=30672,reference_antibodies=25,
        study_software_inventory_release='2.2.0',inventory_anchors=[64,71],inventory_duplicate_is_additional_analysis=False)],
      'P192':[dict(paragraph_index=16,version_reported_at_this_anchor='1.2.4',role='Annotate SAT nuclei from eight KOBS participants',
        reference_description='Same reference datasets as previously described in citation 14',
        cited_previous_reference_study=dict(authors='Pan et al.',year=2021,doi='10.1186/s13073-021-00939-2',pmcid='PMC8327600'),
        exact_reference_objects_established_in_current_paper=False),
        dict(paragraph_index=18,version_reported_at_this_anchor='1.8.1',role='RYSA SAT cell-type annotation',reference_description='Emont et al. adipose snRNA-seq/scRNA-seq atlas')],
      'P233':[dict(paragraph_index=8,version_reported_at_this_anchor='2.41',release_identity_resolved=False,
        role='Residual PBMC/RBC identification in stromal-cell preprocessing',paper_reference_context='SingleR alongside Seurat multimodal CITE-seq PBMC reference analysis',
        separate_QC=dict(genes=['HBB','HBA1','HBA2'],reported_transcript_threshold=3,reported_comparator='>',is_SingleR_scoring_setting=False))]}
    for pid in ['P051','P082','P192','P233']:
        paper=records[pid];observation=next(t for t in paper['tools'] if t['tool_id']=='singler');anchors=census['primary_singleR_anchors'][pid]
        row=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],tool_id='singler',primary_xml_sha256=paper['xml_sha256'],
          evidence_paragraph_index=observation['evidence_paragraph_index'],evidence_paragraph_sha256=observation['evidence_paragraph_sha256'],
          version_reported_near_name='2.41' if pid=='P233' else observation['version_reported_near_name'],
          use_summary=SUMMARIES[pid],supporting_evidence=anchors,analysis_uses=analyses[pid],
          named_anchor_classification=[dict(paragraph_index=a['paragraph_index'],kind='study-software-inventory' if pid=='P082' and a['paragraph_index'] in [64,71] else 'primary-methods') for a in anchors],
          explicit_unknowns=unknowns,implementation_scope_note='Development only: original source-built annotation profiles and complete-return recording are undergoing independent qualification. No catalog implementation credit yet. Literal 2.41 stays unresolved; that dependent analysis is SKIP3, without version substitution. The original reference objects and manual cell-removal choices must be supplied explicitly. Whole-tool validity and paper reproduction remain unqualified.')
        reviews.append(row)
    identity=json.loads((ROOT/'build/literature-review/monocle-runtime/singler-source-intake-1/author-FDProject/CODE-IDENTITY.json').read_bytes())
    notebook=ROOT/'build/literature-review/monocle-runtime/singler-source-intake-1/author-FDProject/04_FilterBloodCells.Rmd'
    original=notebook.read_bytes()
    if len(original)!=identity['bytes'] or hashlib.sha256(original).hexdigest()!=identity['sha256']:
        raise ValueError('Pinned inspected original author notebook differs')
    lines=original.decode().splitlines()
    for first,reference,label in [(121,'hpca.se','label.main'),(124,'hpca.se','label.fine'),(131,'encode.se','label.main'),(134,'encode.se','label.fine')]:
        block='\n'.join(lines[first-1:first+2])
        if 'SingleR(test =' not in block or 'ref = '+reference not in block or reference+'$'+label not in block:
            raise ValueError('Original author reference/label call differs at line '+str(first))
    if not all('subset = '+field+' !=' in '\n'.join(lines) for field in ['hpca.main','encode.main','hpca.fine']):
        raise ValueError('Observed unpruned author filter fields differ')
    author=next(r for r in reviews if r['paper_id']=='P233')
    author['inspected_author_notebook']={**identity,'repository':'HsiaoLabUCSF/FDProject','actual_author_execution_commit_established':False,
      'reported_literal_release_resolved':False,'source_redistributed':False,'code_license_established':False,
      'observed_calls':[
        dict(lines=[121,124],reference='celldex::HumanPrimaryCellAtlasData()',label_fields=['label.main','label.fine']),
        dict(lines=[131,134],reference='celldex::BlueprintEncodeData()',label_fields=['label.main','label.fine'])],
      'test_expression_source_observed':'SCT assay data slot converted to a matrix',
      'pruned_labels_saved_but_unpruned_labels_used_for_observed_filters':True,
      'separate_Seurat_PBMC_mapping_lines':[174,180],
      'separate_Seurat_PBMC_reference_path_literal':'../data/pbmc_multimodal.h5seurat'}
    candidates={
      'celldex':[
        dict(paper_id='P051',paragraph_index=119,kind='reference-data-retrieval-software',literal_release='1.14.0',analysis_role='Obtain literally described MouseRNAseq ImmGen reference profiles for SingleR annotation'),
        dict(paper_id='P069',paragraph_index=58,kind='company-name-not-software',company='Celldex Therapeutics')],
      'spotlight':[
        dict(paper_id='P032',paragraph_index=68,kind='company-name-not-software',company='Spotlight Therapeutics'),
        dict(paper_id='P046',paragraph_index=2,kind='ordinary-verb-not-software'),
        dict(paper_id='P051',paragraph_index=114,kind='primary-software-analysis',literal_release='1.0.3',reference_description='LungMap single-cell reference',method='seeded NMF regression',reported_neutrophil_fraction_threshold=.1,reported_comparator='>='),
        dict(paper_id='P051',paragraph_index=119,kind='primary-software-analysis',literal_release='1.0.3',reference_description='Single-cell reference described in this PDAC/MI paragraph',method='seeded NMF regression',reported_neutrophil_fraction_threshold=.1,reported_comparator='>=')],
      'bd-rhapsody':[
        dict(paper_id='P051',paragraph_index=3,kind='hardware-and-study-description'),
        dict(paper_id='P051',paragraph_index=80,kind='library-preparation-and-instrument-hardware'),
        dict(paper_id='P051',paragraph_index=81,kind='primary-software-analysis',literal_release='1.9.1',analysis_role='Local alignment, quantification, automated cell-quality filtering and doublet tagging/filtering',separate_manual_QC=True),
        dict(paper_id='P051',paragraph_index=85,kind='BAM-input-origin-context-not-additional-pipeline-execution'),
        dict(paper_id='P051',paragraph_index=104,kind='hardware-plus-unversioned-primary-software-analysis',literal_release=None,analysis_role='Raw sequencing-file processing using the BD Rhapsody analysis pipeline')],
      'immgen-reference':[dict(paper_id='P051',paragraph_index=i,kind='biological-reference-not-software') for i in [81,93,119]]}
    for family,rows in candidates.items():
        got=[(r['paper_id'],r['paragraph_index']) for r in rows]
        wanted=[(r['paper_id'],a['paragraph_index']) for r in census['census'] for a in r['anchors'][family]]
        if got!=wanted:raise ValueError('Every broad named candidate must be explicitly classified: '+family)
    correction=dict(paper_id='P233',tool_id='singler',field='version_reported_near_name',before=None,after='2.41',
      supporting_evidence=census['primary_singleR_anchors']['P233'][0],literal_2_41_interpreted_as_2_4_1=False,
      reason='The original primary paragraph literally contains SingleR (2.41). This records the ambiguous declaration; it does not resolve its identity.')
    return dict(status='PASS_CURATED_FOUR_SINGLER_READINGS_AND_ALL_RELATED_NAME_CANDIDATES_NOT_CATALOG_ADMISSION',
      schema_version=1,reviews=reviews,candidate_classification=candidates,proposed_literal_source_correction=correction,
      admitted_catalog_files_changed=False,new_family_credit=0,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();result=build()
    with a.out.open('x',encoding='utf-8',newline='\n') as s:s.write(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(result['status'])

if __name__=='__main__':main()
