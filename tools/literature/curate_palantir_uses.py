# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply eight source-bound roles from five named definitions; preserve980 prior reviews and the exact250-paper cohort."""
import argparse,copy,gzip,hashlib,json,sys
from pathlib import Path
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse,serialize
from collect_omics_review import paragraphs,tool_matches,publication_record,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from cellphonedb_effective_catalog import load_catalog_registry

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';here=ROOT/'tools/literature';paths=dict(papers=data/'papers.json',reviews=here/'reviewed-uses.json');before={k:p.read_bytes() for k,p in paths.items()};papers=json.loads(before['papers']);old=json.loads(before['reviews']);draft_path=here/'trajectory-spatial-use-reviews.json';draft=json.loads(draft_path.read_bytes());assert draft['status']=='eight-primary-bound-role-drafts-not-applied' and not draft['registry_applied'];drafts=draft['reviews'];extra=json.loads((here/'palantir-named-software-registry.json').read_bytes());extra_ids={r['id'] for r in extra};registry={r['id']:r for r in load_catalog_registry(ROOT)};assert len(registry)==433 and len(extra)==5 and not(extra_ids&set(registry));registry.update({r['id']:r for r in extra});verify_fixed_cohort(ROOT,data,papers);assert len(old['reviews'])==980 and sum(len(p['tools']) for p in papers)==2457 and len(drafts)==8 and not any(r['tool_id'] in extra_ids for r in old['reviews'])
 if a.apply:
  from palantir_workflow_evidence import verify
  assert verify(emit=False)['coordinate_workflow_adapter_admitted']
 updated=copy.deepcopy(papers);reviewed=copy.deepcopy(old);reviewed['reviews'].extend(d['review'] for d in drafts);by={};caches=[];changed=[];scanned=[]
 for d in drafts:by.setdefault(d['paper_id'],[]).append(d)
 assert sorted(by)==['P024','P094','P117','P169'];new_observations=0
 for paper in updated:
  raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];xml=ET.fromstring(raw);assert next(e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(xml);new_matches=tool_matches(ps,extra,{});scanned.extend((paper['paper_id'],m['tool_id']) for m in new_matches)
  if paper['paper_id'] not in by:assert not new_matches;continue
  cache=ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json');rawcache=cache.read_bytes();cached=json.loads(rawcache)
  for d in by[paper['paper_id']]:
   r=d['review'];tool=r['tool_id'];assert paper['xml_sha256']==d['xml_sha256'];validate_supporting_evidence(ps,[r],registry);matches=tool_matches(ps,[registry[tool]],{tool:r['evidence_paragraph_sha256']});assert len(matches)==1 and matches[0]['evidence_paragraph_index']==d['primary_paragraph'];assert not any(t['tool_id']==tool for t in paper['tools']) and not any(t['tool_id']==tool for t in cached['tools']);paper['tools'].append(publication_record(dict(paper,tools=matches),[r])['tools'][0]);cached['tools'].append(matches[0]);new_observations+=1
  assert {m['tool_id'] for m in new_matches}=={d['review']['tool_id'] for d in by[paper['paper_id']]};caches.append((cache,rawcache,serialize(cached,rawcache)));changed.append(paper['paper_id'])
 for prior,current in zip(papers,updated):assert {k:v for k,v in prior.items() if k!='tools'}=={k:v for k,v in current.items() if k!='tools'} and prior['tools']==[t for t in current['tools'] if t['tool_id'] not in extra_ids]
 assert len(scanned)==new_observations==8 and len(caches)==4 and changed==sorted(by) and sum(len(p['tools']) for p in updated)==2465 and len({t['tool_id'] for p in updated for t in p['tools']})==400 and reviewed['reviews'][:980]==old['reviews'] and len(reviewed['reviews'])==len({(r['pmcid'],r['tool_id']) for r in reviewed['reviews']})==988 and sum(len(r.get('supporting_evidence',[])) for r in reviewed['reviews'])==340;after=dict(papers=serialize(updated,before['papers']),reviews=serialize(reviewed,before['reviews']));row=inverse(before['reviews'],after['reviews']);row['path']='tools/literature/reviewed-uses.json'
 for key,path in paths.items():(out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[key],mtime=0))
 result=dict(status='passed-eight-trajectory-spatial-primary-role-reviews',applied=a.apply,prior_reviews_preserved=980,new_reviews=8,total_reviews=988,total_observations=2465,new_named_observations=8,distinct_named_tools=400,source_XML_hashes_checked=250,fulltext_DOIs_checked=250,supporting_pointers=340,changed_paper_roles=changed,curator_sha256=pin(Path(__file__))['sha256'],draft_pin=pin(draft_path),registry_extension=pin(here/'palantir-named-software-registry.json'),before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(),papers_sha256=hashlib.sha256(after['papers']).hexdigest(),whole_paper_reproduced=False,whole_goal_complete=False)
 if a.apply:
  roles=data/'palantir-role-coverage.json';baseline=data/'palantir-prior-reviews.json.gz';prior_papers=data/'palantir-prior-papers.json.gz';descriptor=data/'palantir-reviews-shared-source-compatibility.json';assert not any(p.exists() for p in [roles,baseline,prior_papers,descriptor]);assert all(paths[k].read_bytes()==raw for k,raw in before.items()) and all(p.read_bytes()==raw for p,raw,_ in caches);baseline.write_bytes(gzip.compress(before['reviews'],mtime=0));prior_papers.write_bytes(gzip.compress(before['papers'],mtime=0));result.update(prior_review_archive=pin(baseline),prior_papers_archive=pin(prior_papers));save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='palantir',scope='Exactly8 appended primary-bound roles and8 new observations from five definitions; all980 prior reviews and250 paper identities retained. No scientific/runtime source exception.',paths=[row]));
  for key,path in paths.items():path.write_bytes(after[key])
  for path,raw,new in caches:path.write_bytes(new)
  save(roles,result)
 save(out/'COMPLETED.json',result);print('Validated250 XML/DOIs;8 appended roles,8 new observations,988 total reviews,340 supporting pointers; applied='+str(a.apply))
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/cohort checks')
 main()
