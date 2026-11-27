"""Replay all250 primary XML sources and tensorQTL's linked-source role audit."""
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
from tensorqtl_source_roles import ROLES
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/tensorqtl-source-audit'
PATTERN=re.compile(r'(?<![A-Za-z0-9])tensorQTL(?![A-Za-z])',re.I)
def read(p):return json.loads(Path(p).read_bytes())
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(p):return identity(Path(p).read_bytes())
def encoded(x):return(json.dumps(x,ensure_ascii=False,indent=2,allow_nan=False)+'\n').encode()
def require(ok,message):
 if not ok:raise ValueError(message)
def collect(root,cache):
 papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers)
 census=dict(schema_version=1,frozen_interval=['2023-10-23','2026-10-23'],pattern=PATTERN.pattern,collector=pin(Path(collector.__file__)),XML={},contexts=[],other_structures=[]);paragraphs={}
 for paper in papers:
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as f:raw=f.read(16_000_001)
  require(len(raw)<=16_000_000 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Primary source differs')
  tree=ET.fromstring(raw);require(any((x.text or'').strip()==paper['doi']for x in tree.findall('front/article-meta/article-id')),'Primary DOI differs');census['XML'][paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],**identity(raw));ps=list(collector.paragraphs(tree))
  for p in ps:
   if PATTERN.search(p['match_text']):census['contexts'].append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if PATTERN.search(collector.matching_words(node)):census['other_structures'].append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  if paper['paper_id']in ROLES:paragraphs[paper['paper_id']]=ps
 require(len(papers)==250 and len(census['contexts'])==14 and {x['paper_id']for x in census['contexts']}==set(ROLES)and not census['other_structures'],'Expanded primary tensorQTL population differs')
 roles=[];reviews=[]
 for pid,definition in ROLES.items():
  paper=next(x for x in papers if x['paper_id']==pid);uses=[x for x in paper['tools']if x['tool_id']=='tensorqtl'];require(len(uses)==1,'TensorQTL membership differs');use=uses[0];by={x['paragraph_index']:x for x in paragraphs[pid]};anchors=[]
  for index in definition['paragraphs']:
   p=by[index];anchors.append(dict(paragraph_index=index,section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest(),matching_text_sha256=hashlib.sha256(p['match_text'].encode()).hexdigest()))
  if pid=='P157':require(re.search(r'tensorQTL\s*v[.]?\s*1[.]0[.]5',by[71]['text'])is not None,'Primary version declaration changed')
  summary=definition['usage'];note=definition['caution']+' Source review alone does not admit runtime analysis, establish an executed author recipe or reproduce a whole paper.'
  role=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],tool_id='tensorqtl',kind=definition['kind'],reported_version=definition['reported_version'],use_summary=summary,details=definition['details'],implementation_scope_note=note,primary_anchors=anchors,extractor_near_name_version_preserved=use['version_reported_near_name'],exact_author_recipe_qualified=False,whole_paper_reproduced=False,source_audit_admits_runtime=False)
  supporting=[]
  for anchor in anchors:
   index=anchor['paragraph_index']
   if index==use['evidence_paragraph_index']:continue
   if PATTERN.search(by[index]['match_text']):supporting.append(anchor)
   elif pid=='P183'and index in[13,14,24]:
    terms={13:['PEER','eQTL'],14:['circRNA','eQTL'],24:['regression','PEER']}[index]
    named=next(x for x in anchors if x['paragraph_index']==15)
    supporting.append(dict(anchor,evidence_role='method-details',named_anchor=named,shared_terms=terms,relation_note='The same cohort-specific RNA eQTL workflow links phenotype/covariate preparation and regression-window details to the explicit tensorQTL regression declaration in paragraph 15.'))
   # Availability-only and adjacent FDR paragraphs stay in the role's complete
   # primary_anchors, rather than becoming unsupported named-tool pointers.
  review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='tensorqtl',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=summary,implementation_scope_note=note,supporting_evidence=supporting)
  collector.validate_supporting_evidence(paragraphs[pid],[review],{'tensorqtl':dict(pattern=r'(?<![A-Za-z0-9_])tensorQTL(?![A-Za-z0-9_])')})
  roles.append(role);reviews.append(review)
 return census,dict(schema_version=1,roles=roles,new_source_reviews=4,new_family_credit=0,source_audit_admits_runtime=False,whole_goal_complete=False),reviews
def private_provenance(private):
 a=read(private/'author-intake-1/COMPLETED.json');source=read(private/'author-source-1/COMPLETED.json');upstream=[read(private/'upstream-source-1/COMPLETED.json'),read(private/'upstream-source-2/COMPLETED.json')]
 for name,p in source['source_files'].items():
  raw=(private/'author-source-1'/name).read_bytes();require(identity(raw)=={k:p[k]for k in ['bytes','sha256']}and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==p['git_blob'],'Read-only author source changed')
 for repo,row in a['repositories'].items():
  require(row['status']=='READ_ONLY_TREE_AUDIT','Author tree unavailable');tree=read(private/'author-intake-1'/repo.replace('/','--')/'tree.json');require(not tree['truncated'],'Author tree is truncated')
  if repo=='LiChen1990/lncRNA_circRNA_eQTL':require(row['files']==14 and row['license']is None and not any(x['path'].endswith('tensorQTL_CMC.py')for x in tree['tree']),'Missing custom code/license facts changed')
  if repo=='kal26/pcqtls':require(row['license']is None,'Linked license facts changed')
 env=(private/'author-source-1/kal26--pcqtls/environment.yml').read_text();require('tensorqtl==1.0.9'in env and 'torch==2.1.2+cpu'in env,'Linked environment pins changed')
 pantry=(private/'author-source-1/PejLab--Pantry/pheast/scripts/run_tensorqtl.py').read_text();require('qvalue_lambda=0.85'in pantry and 'random_tiebreak=True'in pantry and 'group_s=groups'in pantry,'Linked grouped workflow changed')
 selected={}
 for i,document in enumerate(upstream):
  for version,record in document.items():
   root=private/('upstream-source-1'if i==0 else'upstream-source-2')/('tensorqtl-'+version)
   for name,p in record['files'].items():require(pin(root/name)=={k:p[k]for k in ['bytes','sha256']},'Original upstream source changed')
   require('BSD 3-Clause License'in(root/'LICENSE').read_text(),'Original source license differs');selected[version]=record
 auxiliary=read(private/'auxiliary-author-source-1/COMPLETED.json')
 for name,wanted in auxiliary['files'].items():require(pin(private/'auxiliary-author-source-1'/name)=={k:wanted[k]for k in ['bytes','sha256']},'Auxiliary read-only author source changed')
 require(pin(private/'auxiliary-author-source-1/PMC9395416.xml')=={k:auxiliary['auxiliary_paper'][k]for k in ['bytes','sha256']},'Auxiliary primary source changed')
 earlier=(private/'auxiliary-author-source-1/codes/ieqtl_analysis_and_plots.py').read_text();require('cis.map_nominal'in earlier and 'run_eigenmt=True'in earlier and 'maf_threshold_interaction=0.05'in earlier,'Earlier interaction recipe evidence changed')
 return dict(schema_version=1,author_repositories=a['repositories'],author_source_files=source['source_files'],upstream_sources=selected,cited_prior_study=auxiliary,author_code_executed=False,author_code_redistributed=False,source_text_published=False,license_boundaries='The inspected LiChen1990 and kal26 trees supply no license declaration; their files remain read only. Independently licensed original tensorQTL code is separate. Current linked commits are inspected candidates, not proven executed study revisions. The auxiliary 2022 paper is provenance context only and adds zero cohort papers; its recovered earlier script does not resolve P157 likelihood-ratio wording.')
def audit(root,cache,private=None):
 census,roles,reviews=collect(root,cache);folder=root/FOLDER;require(read(folder/'CENSUS.json')==census and read(folder/'ROLES.json')==roles and read(folder/'REVIEW-ENTRIES.json')==dict(schema_version=1,reviews=reviews),'Source role evidence differs')
 if private is not None:require(read(folder/'SOURCE-PROVENANCE.json')==private_provenance(private),'Linked original source provenance differs')
 return dict(status='PASS_TENSORQTL_FULL250_PRIMARY_AND_LINKED_SOURCE_AUDIT',papers=250,primary_contexts=14,memberships_reviewed=4,new_source_reviews=4,new_family_credit=0,source_audit_admits_runtime=False,author_recipe_qualified=False,whole_goal_complete=False,private_source_provenance_checked=private is not None)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);p.add_argument('--private',type=Path);p.add_argument('--write',action='store_true');a=p.parse_args()
 try:
  root=a.root.resolve();cache=a.xml_cache.resolve();private=a.private.resolve()if a.private else None
  if a.write:
   require(private is not None,'Original private evidence is required');census,roles,reviews=collect(root,cache);provenance=private_provenance(private);folder=root/FOLDER;folder.mkdir()
   for name,value in [('CENSUS.json',census),('ROLES.json',roles),('REVIEW-ENTRIES.json',dict(schema_version=1,reviews=reviews)),('SOURCE-PROVENANCE.json',provenance)]:
    with(folder/name).open('xb')as f:f.write(encoded(value))
  print(json.dumps(audit(root,cache,private),indent=2))
 except Exception as e:print('FAIL2: tensorQTL source review is not admitted; retain sources and correct the named cause: '+str(e),file=sys.stderr);raise SystemExit(2)
