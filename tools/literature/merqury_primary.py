"""Verify all250 exact frozen primary XMLs and the complete Merqury source-role census; no analyses/downloads."""
from pathlib import Path
import argparse,gzip,json,re,sys,xml.etree.ElementTree as ET
import collect_omics_review as collector
from gemma_primary import pin,require
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/merqury-source-audit'
PATTERN=re.compile(r'(?<![a-z0-9_])merqury(?:fk)?(?![a-z0-9_])',re.I)
PAIRS={'P101','P164','P171','P248'}
MUTABLE={'review_status','use_summary','implementation_scope_note','evidence_selection','supporting_evidence','source_role_qualification'}

def audit(root,cache):
 root,cache=Path(root),Path(cache);census=json.loads((root/FOLDER/'CENSUS.json').read_bytes());roles=json.loads((root/FOLDER/'ROLES.json').read_bytes())['roles'];papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes())
 require(pin(Path(collector.__file__).read_bytes())==census['collector'],'Selected paragraph collector changed; preserve historical source hashes')
 require(census['frozen_interval']==['2023-10-23','2026-10-23']and census['pattern']==PATTERN.pattern,'Frozen interval or name-screening pattern changed')
 require(len(papers)==len(census['XML'])==250 and [p['paper_id']for p in papers]==list(census['XML']),'Full frozen cohort changed')
 hits=[];other=[];all_anchors={}
 for paper in papers:
  expected=census['XML'][paper['paper_id']];require(paper['pmcid']==expected['pmcid']and paper['xml_sha256']==expected['sha256']and re.fullmatch(r'PMC[0-9]+',paper['pmcid']),'Original paper/XML identity changed');require(0<expected['bytes']<=33554432,'Complete XML capacity exceeded')
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(expected['bytes']+1)
  require(pin(raw)=={k:expected[k]for k in('bytes','sha256')},'Missing, changed, extra or truncated original XML population')
  tree=ET.fromstring(raw);require(any((item.text or'').strip()==paper['doi']for item in tree.findall('front/article-meta/article-id')),'Original primary DOI differs')
  for paragraph in collector.paragraphs(tree):
   anchor=dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=pin(paragraph['text'].encode())['sha256']);all_anchors[paper['paper_id'],paragraph['paragraph_index']]=anchor
   if PATTERN.search(paragraph['match_text']):hits.append(anchor)
  for location in('front/article-meta/abstract','back','floats-group','body//supplementary-material'):
   for element in tree.findall(location):
    if PATTERN.search(collector.matching_words(element)):other.append(dict(paper_id=paper['paper_id'],location=location,sha256=pin(ET.tostring(element))['sha256']))
 require(hits==census['anchors']and other==census['other_structures'],'Complete source contexts or nonbody pointers changed')
 require(len(hits)==5 and len(other)==1 and {r['paper_id']for r in hits}==PAIRS,'Different complete Merqury mention population')
 require(len(roles)==len({r['paper_id']for r in roles})==4 and {r['paper_id']for r in roles}==PAIRS,'Missing/duplicate reviewed source role')
 claimed=set();by_id={r['paper_id']:r for r in roles}
 for role in roles:
  require(role['tool_id']=='merqury'and role['whole_paper_reproduced']is False and role['implementation_available']is False and role['implementation_status']=='PENDING_RUNTIME_QUALIFICATION_NO_CATALOG_CREDIT','Source review expanded into runtime admission')
  require(role['source_role_review_status']=='MANUAL_FULL_DIRECT_CONTEXT_REVIEW_OTHER_STRUCTURE_POINTERS_RETAINED'and role['use_summary']and role['remaining_scope'],'Incomplete semantic review')
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);require((paper['doi'],paper['pmcid'])==(role['doi'],role['pmcid']),'Role paper identity changed');uses=[u for u in paper['tools']if u['tool_id']=='merqury'];require(len(uses)==1,'Different existing Merqury observation')
  require(all(uses[0].get(k)==value for k,value in role['raw_catalog_observation_preserved'].items()if k not in MUTABLE),'Raw version/source fields changed')
  for anchor in role['primary_anchors']:
   require(all_anchors.get((anchor['paper_id'],anchor['paragraph_index']))==anchor,'Reviewed source paragraph anchor changed');claimed.add((anchor['paper_id'],anchor['paragraph_index']))
 require(claimed=={(r['paper_id'],r['paragraph_index'])for r in hits},'A full primary matching context was omitted')
 require(by_id['P164']['distinct_uses']==['parental k-mer database construction for Verkko','assembly k-mer completeness']and len(by_id['P164']['primary_anchors'])==2,'Parental preparation was omitted or Yak roles reassigned')
 require({k:v['literal_package_version_reported_elsewhere']for k,v in by_id.items()}==dict(P101='1.3',P164='1.3',P171='1.3',P248='1.1'),'Reported author versions were inferred or changed')
 require(sum(r['source_review_previously_present']for r in roles)==1 and by_id['P164']['source_review_previously_present'],'Different review delta')
 return dict(status='PASS_FULL250_MERQURY_SOURCE_ROLE_CENSUS',papers=250,direct_contexts=5,reviewed_roles=4,other_structure_pointers=1,new_source_reviews_eligible=3,existing_source_review_expanded=1,new_papers_or_observations=0,runtime_admission=False,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
 try:print(json.dumps(audit(a.root,a.cache),sort_keys=True));return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT MERQURY SOURCE AUDIT SKIPPED: 'if code==3 else'FAIL2 NO MERQURY SOURCE AUDIT ADMITTED: ')+str(e)+'. Required action: restore the exact permitted250XMLs and selected parser/source context; preserve old hashes and failed attempts. Do not replace papers or infer author settings.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
