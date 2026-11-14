#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record six individual primary-source readings; confer no catalog or runtime credit."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs

EXPECTED={'P005':[60],'P008':[92],'P070':[38],'P096':[9,53],'P099':[9,40],'P100':[23,45]}
READINGS={
 'P005':dict(version_reported_near_name=None,primary=60,
  use_summary='Integrates snRNA-seq subclusters with spinal-cord spatial transcriptomics and supplies the abundance matrix to a separate sklearn NMF analysis of spatially co-occurring cell types.',
  reported_settings={'N_cells_per_location':12,'detection_alpha':200,'training_epochs':40000,'printed_covariate_arguments':{'batch_key':'Date library prep','continuous_covariates':'total_counts','categorical_covariates':'User'},'separate_sklearn_NMF':{'n_components':8,'alpha':0.9,'max_iter':1000,'shuffle':True,'init':'nndsvda','l1_ratio':0.9}},
  uncertainties=['Edition is not reported.','Do not translate printed continuous_covariates/categorical_covariates shorthand into another API or infer the stage to which each argument applies.','The earlier 10000-count normalization/log transformation is stated for PCA/UMAP; it does not establish the count layer supplied to cell2location.','The reported gene filters exclude the top30 genes per dataset, mitochondrial genes, and low-detection/low-mean genes; their exact ordering and input layer require author data/code.','The separate sklearn NMF call is not automatically the cell2location wrapper.']),
 'P008':dict(version_reported_near_name=None,primary=92,
  use_summary='Uses negative-binomial reference signatures from the greater-than10-PCW female and male scRNA-seq views to decompose Visium RNA count matrices into estimated cell-type abundances per spot.',reported_settings={},
  uncertainties=['Edition, input layer, filtering, batch/covariate design, training parameters and posterior selection are not specified here.','Adjacent Space Ranger, squidpy, anatomical annotation and downstream scoring are separate operations.']),
 'P070':dict(version_reported_near_name=None,primary=38,
  use_summary='Fits negative-binomial reference profiles for developing-limb scRNA-seq clusters, filters low-expression genes leaving13763, estimates cell abundances jointly across slides, and uses the cell2location sklearn-NMF wrapper to define colocating microenvironments.',
  reported_settings={'retained_genes':13763,'joint_slides':True,'batch_key':'sample ID','categorical_covariate_keys':'sample ID','microenvironment_cell_type_fraction_threshold':'>0.2'},
  uncertainties=['Edition, exact low-expression cutoffs, raw count layer, mapping priors, epochs, NMF factors and seed are not stated.','The threshold is a downstream membership rule, not a fitted mapping parameter.','Separate slide stitching and image alignment are not cell2location operations.']),
 'P096':dict(version_reported_near_name='0.7a0',primary=53,
  use_summary='Uses annotated temporal-cortex snRNA-seq reference signatures for75 cell populations to map Visium abundance, exports posterior5-percent quantiles, and tests NMF factors5through30 before choosing15 for cortical compartments.',
  reported_settings={'reference_cell_types':75,'reference_cells':144438,'reference_genes':14209,'shared_mapping_genes':14197,'mapping_observations_printed_as_cells':14324,'N_cells_per_location':7,'detection_alpha':20,'mapping_iterations':30000,'posterior_quantile':0.05,'NMF_factor_range_as_reported':{'minimum':5,'maximum':30,'step_unspecified_in_paper':True},'NMF_factors_selected':15,'author_code_doi':'10.5281/zenodo.13321265'},
  uncertainties=['The paper reports raw, unnormalized/untransformed reference counts. Author Script24 loads a filename containing log_normalised and uses default X with no explicit layer; the filename cannot establish the actual matrix transformation, so retain the uncertainty.','The printed0.7a0 label matches PEP440 normalization of historical upstream0.07a Git metadata; more than one such commit exists with different scvi requirements, and the author commit/environment is unreported.','The selected modern0.1.3 runtime does not establish execution of the reported0.7a0 edition.','Author Script24 explicitly trains the reference for250epochs with batch_size2500, train_size1, lr0.002, CPU and1000 posterior samples; the paper mapping30000iterations must not be assigned to reference training.','The paper reports an NMF factor range5to30. Author Script26 instead supplies np.arange(5,30), which generates5through29, and explicitly sets3restarts; retain the upper-bound difference. Script27 selects n_fact15; figure filenames alone do not override this setting.','The paper wording14324 cells is retained rather than silently rewritten as spots.','VistoSeg nucleus segmentation is a separate operation.']),
 'P099':dict(version_reported_near_name=None,primary=40,
  use_summary='Aggregates about50 single-cell transcriptomes using k-means in subclustering UMAP space, maps EasySci subtypes to study and external spatial datasets with reported default cell2location settings, manually inspects top10 pixels, and retains mapping scores above1.',
  reported_settings={'approximate_cells_per_aggregate':50,'manual_top_pixels':10,'mapping_score_threshold':'>1','mapping_settings_as_reported':'default'},
  uncertainties=['Edition and exact defaults are not stated.','The aggregation operation, count transformation/layer and exact mapping-score field are unreported; do not infer a sum, mean or raw-count workflow.','The preceding UMAP/k-means aggregation and manual anatomical review are separate operations.']),
 'P100':dict(version_reported_near_name='0.1.3',primary=45,
  use_summary='Fits batch-aware negative-binomial reference signatures and maps absolute cell-type abundance separately for each Visium section, using shared genes in untransformed/unnormalized spatial counts with mitochondrial/ribosomal genes excluded.',
  reported_settings={'N_cells_per_location':8,'detection_alpha':20,'mapping_iterations':30000,'per_section_mapping':True,'ELBO_scaling_as_reported':'locations times genes'},
  uncertainties=['Reference-training epochs, exact reference count layer, seed, hardware and full historical environment are unreported.','The paper reports convergence after30000 mapping iterations; a short synthetic integration run cannot reproduce that conclusion.','Original0.1.3 computes inverse locations-times-genes scaling when scale_elbo=None; the paper does not identify the exact supplied Python argument.','The spatial association with HSC/central-vein EC abundance is a downstream study finding, not a new cell2location fitting operation.'])
}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--xml-directory',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());assert len(papers)==250
 census=[];readings=[]
 for paper in papers:
  raw=gzip.decompress((a.xml_directory/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],paper['paper_id']
  xml=ET.fromstring(raw);assert paper['doi'].lower() in [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi' and e.text]
  named=[r for r in paragraphs(xml) if re.search(r'(?<![A-Za-z0-9_])cell2location(?![A-Za-z0-9_])',r['match_text'],re.I)]
  assert [r['paragraph_index'] for r in named]==EXPECTED.get(paper['paper_id'],[]),paper['paper_id']
  supports=[dict(paragraph_index=r['paragraph_index'],section=r['section'],paragraph_sha256=hashlib.sha256(r['text'].encode()).hexdigest()) for r in named]
  census.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],named_anchors=supports))
  if not named:continue
  row=dict(READINGS[paper['paper_id']]);primary=row.pop('primary');anchor=next(x for x in supports if x['paragraph_index']==primary)
  readings.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],primary_xml_sha256=paper['xml_sha256'],tool_id='cell2location',evidence_paragraph_index=primary,evidence_paragraph_sha256=anchor['paragraph_sha256'],supporting_evidence=supports,named_anchor_classification={str(x['paragraph_index']):'analysis-role' if x['paragraph_index']==primary else 'results-and-figure-context' for x in supports},review_scope='All nine named primary paragraphs across the six papers; author-code differences retained where inspected. Candidate reading only; no catalog or execution credit.',**row))
 assert len(readings)==6 and sum(len(x['supporting_evidence']) for x in readings)==9
 result=dict(status='SOURCE_BOUND_SIX_CELL2LOCATION_READINGS_NOT_YET_CATALOG_ADMITTED',papers_checked=250,named_papers=6,named_anchors=9,readings=readings,new_tool_credit=0,catalog_review_credit=0,whole_goal_complete=False)
 for n,v in [('READINGS.json',result),('CENSUS.json',census)]:
  (out/n).write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
 print(result['status'],flush=True)

if __name__=='__main__':main()
