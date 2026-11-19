# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit METAL source decisions; this does not admit or mutate the catalog."""
import argparse,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs,validate_supporting_evidence
from metal_workflows.reviews import REVIEWS
PREVIEW='docs/literature/metal-workflow-development/ROLE-REVIEW-PREVIEW.json'

def require(ok,why):
 if not ok:raise ValueError(why)

def digest(raw):return hashlib.sha256(raw).hexdigest()

def audit(cache,papers,definition):
 require(len(papers)==250 and len(REVIEWS)==24,'Require the complete fixed cohort and 24 individual source decisions')
 preview=json.loads((ROOT/PREVIEW).read_bytes());frozen={row['paper_id']:row for row in preview['reviews']};require(set(frozen)==set(REVIEWS),'Qualified review preview population differs')
 identities=[];roles=[];named=[]
 for paper in papers:
  raw=gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes());require(digest(raw)==paper['xml_sha256'],'Primary XML hash differs: '+paper['paper_id']);identities.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],xml_bytes=len(raw)))
  body=paragraphs(ET.fromstring(raw));by_index={p['paragraph_index']:p for p in body};hits=[]
  for p in body:
   match=re.search(definition['pattern'],p.get('match_text',p['text']),re.I)
   if match:hits.append(dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=digest(p['text'].encode()),matched_text=match[0]))
  if hits:named.append(dict(paper_id=paper['paper_id'],paragraphs=hits))
  if paper['paper_id'] not in REVIEWS:continue
  id=paper['paper_id'];indices,edition,kind,summary,caution=REVIEWS[id];record=frozen[id];selected=[]
  require(record['pmcid']==paper['pmcid'] and record['doi']==paper['doi'] and record['source_xml_sha256']==paper['xml_sha256'] and record['source_url']==paper['fulltext_url'],'Review preview identity differs: '+id)
  require((record['role'],record['use_summary'],record['edition_literal'],record['required_caution'])==(kind,summary,edition,caution),'Frozen individual review changed: '+id)
  for index in indices:
   require(index in by_index,'Selected primary paragraph missing: '+id);p=by_index[index];require(re.search(definition['pattern'],p.get('match_text',p['text']),re.I),'Selected paragraph does not name METAL: '+id)
   selected.append(dict(paragraph_index=index,section=p['section'],paragraph_sha256=digest(p['text'].encode())))
  require(selected==record['selected_primary_evidence'],'Selected primary pointers differ: '+id)
  text='\n'.join(by_index[i]['text'] for i in indices)
  if edition is not None:require(edition in text,'Literal reported edition not in selected primary text: '+id)
  uses=[u for u in paper['tools'] if u['tool_id']=='metal'];require(len(uses)==1,'Require one existing METAL observation: '+id)
  original=uses[0];require(original['evidence_paragraph_index'] in by_index and digest(by_index[original['evidence_paragraph_index']]['text'].encode())==original['evidence_paragraph_sha256'],'Original METAL anchor stale: '+id)
  historical=edition is not None and '2011' in edition
  role=dict(paper_id=id,pmcid=paper['pmcid'],doi=paper['doi'],fulltext_url=paper['fulltext_url'],xml_sha256=paper['xml_sha256'],role=kind,use_summary=summary,reported_edition_literal=edition,unresolved=caution,anchors=selected,anchor_index=selected[0]['paragraph_index'],anchor_section=selected[0]['section'],anchor_sha256=selected[0]['paragraph_sha256'],previous_anchor_index=original['evidence_paragraph_index'],previous_anchor_sha256=original['evidence_paragraph_sha256'],positive_use=True,usage_context='current-study-comparative-benchmark-data-preparation' if id=='P076' else 'current-study-allele-frequency-support' if id=='P212' else 'current-study-analysis',selected_project_profile='2020-05-05',reported_historical_edition_not_reproduced=historical,exact_author_settings_established=False,whole_paper_reproduced=False)
  validate_supporting_evidence(body,[dict(tool_id='metal',supporting_evidence=selected[1:])],{'metal':definition});roles.append(role)
 require(len(identities)==250 and len(roles)==24 and {r['paper_id'] for r in named}==set(REVIEWS),'All named METAL papers must be individually accounted for')
 require(sum(len(r['paragraphs']) for r in named)==47,'Named primary paragraph population differs')
 return dict(status='PASS_ALL250_PRIMARY_IDENTITIES_24_INDIVIDUAL_METAL_ROLES_AND47_NAMED_PARAGRAPHS',papers_scanned=250,primary_files=identities,roles=roles,named_body_hits=named,positive_roles=24,named_paragraphs=47,observations_added=[],observations_removed=[],whole_goal_complete=False)


def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--cache',type=Path,required=True,help='Directory containing the 250 frozen PMCID.xml.gz files')
 parser.add_argument('--out',type=Path,required=True,help='A new output directory; existing paths are refused')
 args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
 try:
  from ldak_catalog.model import registry
  papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
  result=audit(args.cache,papers,next(tool for tool in registry() if tool['id']=='metal'))
  (args.out/'COMPLETED.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
  (args.out/'AUDIT-SOURCE.json').write_text(json.dumps(dict(path='tools/literature/metal_catalog/primary.py',sha256=digest(Path(__file__).read_bytes()),canonical_catalog_applied=False),indent=2)+'\n',encoding='utf-8')
  print(json.dumps({k:v for k,v in result.items() if k not in ('primary_files','roles','named_body_hits')}))
 except Exception as error:
  (args.out/'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_NO_COMPLETED_PRIMARY_AUDIT',error=str(error)),indent=2)+'\n',encoding='utf-8')
  raise


if __name__=='__main__':main()
