# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every fixed-cohort XML for eager and retain distinct source-bound screening/ancestry roles; no catalog credit."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
BASE=ROOT/'build/literature-review/nfcore-runtime'
ROLES={
 7:dict(role_id='P002-eager-treponemal-capture-mapping',pipeline='eager',pipeline_version=None,nextflow_version=None,
  source_use='Mapped enriched Treponema pallidum capture reads to the Nichols reference. This use does not establish a pipeline or engine version; supplementary methods remain a separate source requirement.',
  reported_components={},reported_choices=dict(reference_accession='NC_021490.2',SNP_minimum_read_support=4,UDG_none=['JUC013','DFU001','RAZ007'],UDG_partial=['GAP009','MXV001']),
  boundaries=['Do not inherit eager2.4.4 or eager2.3.4 from the other roles.','Capture mapping, minimum read support, pooled libraries and downstream SNP processing require Supplementary Information sections6-8 and Supplementary Table5. A mapping-only control does not establish the complete role.']),
 29:dict(role_id='P002-eager-pathogen-screening',pipeline='eager',pipeline_version='2.4.4',nextflow_version='21.04.1',
  source_use='Processed ancient DNA, removed human reads and evaluated MALT taxonomic candidates through HOPS. Retain the stated historical database composition and dates.',
  reported_components={'AdapterRemoval':'2','BWA':'0.7.17','samtools':'c1.12','DeDup':'0.21.8','DamageProfiler':None,'HOPS':None,'MALT':None},
  reported_choices=dict(remove_mapping_quality_less_than=37,bacterial_database_date='2017-11-03',viral_database_date='2017-10-30',bacterial_assembly_levels=['complete','chromosome'],viral_assembly_level='complete',viral_names_exclude=['other','unknown'],additional_eukaryotic_genomes='selection unreported here',unassessable_candidate_read_counts=[3,2]),
  boundaries=['Actual image/package versions require their own intake; the source environment declares DeDup0.12.8 while the paper says0.21.8. Preserve both values and leave the discrepancy unresolved.','Do not treat three/two assigned reads as an assessable HOPS result or a negative biological finding.','The original complete historical database is separate from a small software-control database; never label the latter as author data.']),
 32:dict(role_id='P002-eager-human-ancestry',pipeline='eager',pipeline_version='2.3.4',nextflow_version=None,
  source_use='Processed shotgun and human-enriched capture reads against GRCh37/hg19 for ancient human ancestry, damage and contamination assessment. This is a separate pipeline version from pathogen screening.',
  reported_components={'AdapterRemoval':'2','BWA':'0.7.12','mapDamage':'2.0','ANGSD':'0.935','custom pileupCaller':'8.2.2'},
  reported_choices=dict(reference='GRCh37 (hg19)',minimum_read_length=30,minimum_base_quality=20,BWA_seed_length=32,mpileup_arguments=['-q','30','-Q','30','-B'],genotype_sampling='pseudo-haploid random draw',retention_SNPs_strictly_greater_than=10000,male_X_rate_interval=[0.35,0.55],male_Y_rate_interval=[0.4,0.7],sex_coverage_scope='targeted SNPs normalized by chromosome length'),
  boundaries=['The engine edition is unreported for this role; the screening paragraph does not establish it.','Keep reported component versions distinct from the selected2.3.4 source environment and actual image. Record discrepancies without silently replacing BWA0.7.12 or custom pileupCaller8.2.2.','The separately described mpileup/custom pileupCaller step, SNP-panel exclusions, targeted sex ratios and downstream population analyses are not established solely by a pipeline-version match.'])}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_EAGER_ROLE_CENSUS',new_tool_credit=0,reviewed_roles_added=0)
 try:
  data=ROOT/'docs/literature/omics-2023-2026';guards={n:pin(ROOT/n) for n in ['docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/summary.json','tools/literature/reviewed-uses.json','src/main/resources/org/jlinalg/omics/nfcore/nfcore-source-lock.json']};papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);hits=[];reviews=[];sources=[]
  for paper in papers:
   raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(x.text for x in root.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi').lower()==paper['doi'].lower();sources.append(dict(paper_id=paper['paper_id'],xml_sha256=paper['xml_sha256']))
   for paragraph in paragraphs(root):
    if not re.search(r'\beager\b',paragraph['match_text'],re.I):continue
    pointer=dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],paragraph_index=paragraph['paragraph_index'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),section=paragraph['section']);hits.append(dict(**pointer,matching_text=paragraph['match_text']))
    if paper['paper_id']=='P002' and paragraph['paragraph_index'] in ROLES:
     role=ROLES[paragraph['paragraph_index']];text=paragraph['match_text']
     if role['pipeline_version'] is not None:assert re.search(r'eager\s+v\.?\s*'+re.escape(role['pipeline_version'])+r'\b',text,re.I)
     else:assert 'nf-core/eager' in text and not re.search(r'eager\s+v\.?\s*\d',text,re.I)
     assert ('21.04.1' in text)==(role['nextflow_version'] is not None);reviews.append(dict(**pointer,**role,review_state='SOURCE_BOUND_ROLE_DRAFT_NOT_APPLIED',implementation_status='missing',catalog_credit=False))
  assert len(reviews)==3 and {x['pipeline_version'] for x in reviews}==set(['2.4.4','2.3.4',None]);assert all(pin(ROOT/n)==v for n,v in guards.items());save(out/'role-drafts.json',reviews);save(out/'all-eager-paragraphs.json',hits);state.update(status='PASS250_XMLS_AND_THREE_DISTINCT_EAGER_ROLE_REQUIREMENTS_ONLY',papers_verified=250,eager_matching_paragraphs=len(hits),matching_papers=sorted({x['paper_id'] for x in hits}),role_drafts=pin(out/'role-drafts.json'),complete_source_audit=sources,catalog_and_shared_lock_unchanged=guards,source_pin=pin(Path(__file__)),pipeline_versions_required=['2.4.4','2.3.4'],additional_unversioned_role=True,full_nfcore_family_or_biological_admission=False)
 except Exception as error:
  import traceback
  state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS250_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete cohort/source/role checks')
 raise SystemExit(main())
