"""Replay all250 frozen primary XML sources and record the four distinct pbmm2 applications."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
from nfcore_review_source_audit import read,pin,identity,encoded,require
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/pbmm2-source-audit'
ROLES={
 'P171':('pbmm2 1.17.0 in the primary paragraph; preserve the original null near-name extraction','Redundant wheat IsoSeq isoforms were mapped to the soft-masked genome with pbmm2 1.17.0 before a separate IsoSeq 4.3 collapse step.','IsoSeq collapse, GMAP/StringTie processing and TransDecoder are separate operations. The exact whole wheat reference and author mapping/collapse settings are not reproduced by an adapter control.'),
 'P214':('pbmm2 1.17.0; distinct human long-read and methylation-tagged read contexts','Human HiFi long-read genome reads were mapped to GRCh38 with pbmm2 1.17.0. A separate paragraph describes mapping 5mC-tagged ccs reads to hg38 with the same edition.','Keep both primary contexts. Source Table S5 parameters have not been inspected here. WhatsHap, variant/CNV/repeat callers and pb-CpG-tools are distinct downstream operations; its reported editions differ between contexts. Capturing MM/ML tags does not independently certify methylation interpretation.'),
 'P232':('Unreported pbmm2 edition; exact JAG1 reference/preset unresolved','pbmm2 was used for JAG1 sequence mapping in the source-described NM_000214.3 and Hg38/GRCh38 target context.','Do not infer a whole-genome versus transcript target, release or preset from this wording. Lima, read-depth filtering, annotation, control selection and membrane-expression experiments are separate. The exact author target and mapping recipe remain unresolved.'),
 'P248':('pbmm2 1.13.1; PBSV branch','The PBSV structural-variant branch used pbmm2 1.13.1 with long-read data from the reported 125 HiFi and eight CLR accessions against CLv4.0.','Minimap2 2.28 serves other caller branches. Source PASS, read-depth and variant-length filters are downstream caller/author operations. Full accession inputs, the complete CLv4.0 reference and the author PBSV recipe remain unqualified.')}

def collect(root,cache):
 papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers)
 definition=next(x for x in read(root/'docs/literature/omics-2023-2026/tool-inventory.json')if x['id']=='pbmm2');pattern=re.compile(definition['pattern'],re.I)
 census=dict(schema_version=1,pattern=pattern.pattern,collector=pin(Path(collector.__file__)),sources={},contexts=[],other_structures=[]);selected={}
 for paper in papers:
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(16000001)
  require(len(raw)<=16000000 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Primary XML differs: '+paper['paper_id'])
  tree=ET.fromstring(raw);require(any((n.text or'').strip().lower()==paper['doi'].lower()for n in tree.findall('front/article-meta/article-id')),'Primary DOI differs')
  census['sources'][paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],**identity(raw));anchors=[]
  for p in collector.paragraphs(tree):
   if pattern.search(p['match_text']):
    entry=dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest(),matching_text_sha256=hashlib.sha256(p['match_text'].encode()).hexdigest());anchors.append(entry);census['contexts'].append(dict(paper_id=paper['paper_id'],**entry))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if pattern.search(collector.matching_words(node)):census['other_structures'].append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  uses=[t for t in paper['tools']if t['tool_id']=='pbmm2'];require(len(uses)<=1,'Duplicate original pbmm2 observation')
  if anchors:require(paper['paper_id']in ROLES and len(uses)==1,'Unreviewed or missing pbmm2 use');selected[paper['paper_id']]=(paper,anchors,uses[0])
  else:require(not uses,'Original use lacks primary evidence')
 require(list(selected)==list(ROLES)and len(census['sources'])==250 and len(census['contexts'])==5,'Frozen pbmm2 source population differs')
 roles=[];reviews=[]
 for pid,spec in ROLES.items():
  paper,anchors,use=selected[pid];require(any(a['paragraph_index']==use['evidence_paragraph_index']and a['paragraph_sha256']==use['evidence_paragraph_sha256']for a in anchors),'Selected primary evidence differs')
  note=spec[2]+' This source-role audit does not establish an executed author recipe or whole-paper reproduction.'
  role=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],tool_id='pbmm2',runtime=spec[0],primary_anchors=anchors,use_summary=spec[1],implementation_scope_note=note,reported_version_preserved=use['version_reported_near_name'],qualified_author_recipe=False,whole_paper_reproduced=False,source_review_already_counted=False);roles.append(role)
  reviews.append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='pbmm2',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=note,supporting_evidence=[a for a in anchors if a['paragraph_index']!=use['evidence_paragraph_index']]))
 return census,dict(schema_version=1,roles=roles,new_source_reviews=4,recovered_observations=0,source_audit_admits_runtime=False,whole_goal_complete=False),dict(schema_version=1,reviews=reviews)

def audit(root,cache):
 for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json'],collect(root,cache)):require(read(root/FOLDER/name)==value,'Primary source-role record differs: '+name)
 return dict(status='PASS250_PBMM2_PRIMARY_ROLES',sources=250,contexts=5,reviewed_roles=4,new_source_reviews=4,recovered_observations=0,source_audit_admits_runtime=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);p.add_argument('--write',action='store_true');a=p.parse_args();root=a.root.resolve()
 try:
  if a.write:
   values=collect(root,a.xml_cache);folder=root/FOLDER;folder.mkdir()
   for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json'],values):(folder/name).write_bytes(encoded(value))
  print(json.dumps(audit(root,a.xml_cache),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete pbmm2 source-role audit; retain evidence and correct cause: '+str(e))
