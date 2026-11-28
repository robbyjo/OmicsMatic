"""Replay250 frozen primary sources; retain19 CADD/CADD-SV roles and four recovered observations."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
from nfcore_review_source_audit import read,pin,identity,encoded,require
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/cadd-source-audit'
NEW={'P105':11,'P172':15,'P176':36,'P186':25}
ROLES={
'P072':('Unreported edition','CADD scores annotated the six single-variant MR-MEGA credible sets in the Parkinson disease fine-mapping table.','The score-retrieval route and CADD release are unreported; credible-set inference and RegulomeDB interpretation are separate.'),
'P105':('Unreported edition','CADD scores supported interpretation of PAX4 and EFNA1 missense variants within peptic-ulcer fine-mapping credible sets.','These are reported annotation scores, not evidence of a standalone prediction run. The original method-sentence extractor missed this results paragraph.'),
'P135':('CADD1.4','CADD1.4 scores estimated deleteriousness of identified forearm-fracture SNPs.','The qualified1.6 components do not establish1.4 model/data equivalence. RegulomeDB, ATAC accessibility and pathway analyses are separate.'),
'P154':('Unreported edition; PHRED threshold above5','CADD supplied omnibus variant annotations among34 predictors for12,704,497 DeepRVAT exome variants; training and association inputs were also restricted to PHRED-scaled CADD above5.','The CADD release/retrieval route are unreported. Native score lookup/calibration does not train DeepRVAT, generate its other predictors or apply an inferred pathogenicity mask.'),
'P172':('Unreported edition','CADD contributed pathogenicity predictions for the recurrent MAX p.Arg60Gln variant alongside REVEL, SIFT, PolyPhen2 and Grantham.','Software agreement is an author interpretation; no CADD release, exact threshold or execution route is specified. This use was missed by the original extractor.'),
'P174':('CADD1.6','CADD1.6 assessed missense variants with REVEL, MutPred2, VEST4 and BayesDel using literature-derived deleterious-prediction cutoffs.','TableS2 and the cited calibration define the author cutoffs; no universal CADD pathogenicity threshold is inferred. Assembly/retrieval route are unreported.'),
'P175':('Unreported edition; GRCh38/hg38; VEP integration','CADD was among the named VEP integrations for candidate GRCh38 variants and was displayed with linkage disequilibrium and functional consequences around selected loci.','The exact predictor data and VEP/plugin editions are unreported. Standalone local score lookup does not qualify the VEP plugin or LD/GTEx workflow.'),
'P176':('CADD1.6 GRCh37','CADD1.6 GRCh37 scores defined an additional score-at-least20 subset for candidate-gene SKAT-O/CMC-Wald tests and annotated MSH3 variant plots.','The main text and figure caption contain differing allele statements for some variants; preserve the source discrepancy rather than repairing coordinates/alleles. Association tests and author masks are separate. This use was missed by the original extractor.'),
'P186':('Unreported edition; gnomAD-derived score','A gnomAD-derived CADD score of40 supported the reported interpretation of the Finnish TBPL2 p.Arg299Ter variant.','A reported database annotation does not establish an executable/model version or independent clinical classification. The original extractor missed this discussion paragraph.'),
'P192':('Source-named CADD-SV; unreported edition','The source reports a database-derived value of25.7 for the TBX15 rs10494217 missense variant and labels it CADD-SV.','Retain the unusual CADD-SV label instead of silently rewriting it as an ordinary CADD SNV PHRED score or inferring a structural-variant prediction run. This expands an existing review without duplicate credit.'),
'P194':('CADD1.6 through dbNSFP4.3/VEP105','CADD1.6 and REVEL from dbNSFP4.3 contributed missense annotations and evidence-calibrated gene-test categories in the BRaVa VEP105/GENCODE39 workflow.','The dbNSFP/VEP plugins, LOFTEE, SpliceAI, MANE/gene-specific canonical fallback and combined masks are distinct prerequisites and author operations. Standalone CADD scores do not reproduce that complete recipe.'),
'P196':('Unreported edition','CADD assessed missense-variant pathogenicity alongside REVEL, SIFT, AlphaMissense and PrimateAI.','Assembly, release, retrieval route and CADD threshold are unstated. The splicing, conservation, charge, disorder and isoelectric-point analyses are separate.'),
'P197':('Unreported edition; GRCh37/hg19','CADD was one of four predictors used to retain MARK2 missense variants predicted deleterious by more than two programs, with GRCh37/hg19 and the reported transcript.','The numeric CADD cutoff and release are unreported; the more-than-two-program rule and modified ACMG interpretation remain author steps.'),
'P210':('CADD1.6; dbNSFP4.3 plus separate indel annotation','CADD1.6 from dbNSFP4.3 contributed missense/protein-altering annotations; the authors separately applied CADD1.6 to indels within a VEP105/GENCODE39 BRaVa workflow.','Preserve the separate indel route. LOFTEE, SpliceAI, dbNSFP, transcript fallback and hierarchical masks are not qualified by standalone score lookup/calibration.'),
'P220':('CADD1.6 through dbNSFP4.3/VEP105','CADD1.6 and REVEL from dbNSFP4.3 contributed evidence-calibrated damaging-variant categories for obesity/fat-distribution gene tests, alongside LOFTEE and SpliceAI.','The combined masks, gene-specific MANE/canonical fallback and allelic-series analyses are separate; neither a core VEP run nor standalone CADD lookup reproduces the complete recipe.'),
'P226':('CADD1.7','CADD1.7 evaluated TMEM184B missense-variant pathogenicity alongside AlphaMissense, REVEL and GERP using the reported RefSeq transcript.','The qualified1.6 components do not establish1.7 model/data equivalence; the paper-specific transcript and clinical interpretation are separate.'),
'P227':('CADD-SV1.1; raw scores; deletion/duplication batches of5000','CADD-SV1.1 scored deletions and duplications in batches of5000; raw scores compared misexpression-associated and control variants in separate length-adjusted logistic regressions.','The source explicitly excludes inversion/mobile-element-insertion scoring from CADD-SV support. Preserve raw scores for the reported comparisons; ordinary SNV CADD/PHRED conversion is not a substitute for CADD-SV or the downstream models.'),
'P231':('CADD1.7; independent ClinVar comparison','CADD1.7 served as a comparison for5ULTRA on an independent ClinVar5-prime-UTR dataset; reported CADD thresholds of15 and20 yielded different sensitivity/specificity tradeoffs.','Do not substitute1.6 scores, infer the unreported retrieval route, download registered HGMD without prerequisites, or claim reproduction of the5ULTRA training/evaluation.'),
'P232':('CADD1.6 GRCh38; downloaded scores and VEP annotation','Downloaded CADD1.6 GRCh38 scores annotated JAG1 missense variants and were plotted against abnormal membrane-expression results; VEP also supplied CADD annotations.','The unpaired t-test, experimental filtering and VEP integration are separate. Native lookup preserves supplied score rows and does not infer transcript effects or clinical classes.')}

def collect(root,cache):
 papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers);definition=next(x for x in read(root/'docs/literature/omics-2023-2026/tool-inventory.json')if x['id']=='cadd');pattern=re.compile(definition['pattern'],re.I);census=dict(schema_version=1,pattern=pattern.pattern,collector=pin(Path(collector.__file__)),sources={},contexts=[],other_structures=[]);selected={};new_uses={}
 for paper in papers:
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(16000001)
  require(len(raw)<=16000000 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Primary XML differs');tree=ET.fromstring(raw);require(any((n.text or'').strip().lower()==paper['doi'].lower()for n in tree.findall('front/article-meta/article-id')),'Primary DOI differs');census['sources'][paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],**identity(raw));anchors=[];paragraphs=list(collector.paragraphs(tree))
  for p in paragraphs:
   if pattern.search(p['match_text']):
    entry=dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest(),matching_text_sha256=hashlib.sha256(p['match_text'].encode()).hexdigest());anchors.append(entry);census['contexts'].append(dict(paper_id=paper['paper_id'],**entry))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if pattern.search(collector.matching_words(node)):census['other_structures'].append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  pid=paper['paper_id'];uses=[t for t in paper['tools']if t['tool_id']=='cadd'];require(len(uses)<=1,'Duplicate CADD observation')
  if anchors:
   require(pid in ROLES,'Unreviewed additional CADD primary context')
   if pid in NEW:
    p=next(p for p in paragraphs if p['paragraph_index']==NEW[pid]);require(pattern.search(p['match_text']),'Recovered CADD anchor missing');use=dict(tool_id='cadd',tool='CADD',analysis_role='Variant deleteriousness annotation',evidence_section=p['section'],evidence_paragraph_index=p['paragraph_index'],matched_text='CADD',extraction_score=0,usage_context='reported-annotation;direct-executable-not-specified',review_status='source-evidence-extracted; semantic-completeness-review-open',version_reported_near_name=None,evidence_selection='individually-reviewed-recovered-primary-use',evidence_paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest(),use_summary='Variant deleteriousness annotation.',evidence_locator=paper['fulltext_url']+' (section: '+p['section']+')');new_uses[pid]=use
   else:require(len(uses)==1,'Original CADD observation missing');use=uses[0]
   selected[pid]=(paper,anchors,use)
  else:require(not uses,'CADD observation lacks primary evidence')
 require(list(selected)==list(ROLES)and len(census['sources'])==250 and len(census['contexts'])==34,'Frozen CADD source population differs');roles=[];reviews=[]
 for pid,spec in ROLES.items():
  paper,anchors,use=selected[pid];require(any(a['paragraph_index']==use['evidence_paragraph_index']and a['paragraph_sha256']==use['evidence_paragraph_sha256']for a in anchors),'Selected evidence differs');note=spec[2]+' Source review does not establish an executed author recipe or whole-paper reproduction.';role=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],tool_id='cadd',runtime=spec[0],primary_anchors=anchors,use_summary=spec[1],implementation_scope_note=note,reported_version_preserved=use['version_reported_near_name'],qualified_author_recipe=False,whole_paper_reproduced=False,source_review_already_counted=pid=='P192',recovered_observation=pid in NEW);roles.append(role);reviews.append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='cadd',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=note,supporting_evidence=[a for a in anchors if a['paragraph_index']!=use['evidence_paragraph_index']]))
 return census,dict(schema_version=1,roles=roles,new_source_reviews=18,expanded_existing_reviews=1,recovered_observations=4,source_audit_admits_runtime=False,whole_goal_complete=False),dict(schema_version=1,reviews=reviews),dict(schema_version=1,uses=new_uses,changes_to_frozen_paper_cohort=False)

def audit(root,cache):
 for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json','RECOVERED-USES.json'],collect(root,cache)):require(read(root/FOLDER/name)==value,'Primary CADD source role differs: '+name)
 return dict(status='PASS250_CADD_PRIMARY_ROLES',sources=250,contexts=34,reviewed_roles=19,new_source_reviews=18,expanded_existing_reviews=1,recovered_observations=4,source_audit_admits_runtime=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);p.add_argument('--write',action='store_true');a=p.parse_args();root=a.root.resolve()
 try:
  if a.write:
   folder=root/FOLDER;folder.mkdir()
   for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json','RECOVERED-USES.json'],collect(root,a.xml_cache)):
    with(folder/name).open('xb')as out:out.write(encoded(value))
  print(json.dumps(audit(root,a.xml_cache),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete CADD source-role audit; retain evidence and correct cause: '+str(e))
