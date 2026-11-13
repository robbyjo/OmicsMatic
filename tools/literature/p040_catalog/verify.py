#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify source-name additions, old-record preservation and explicit candidate limitations."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from p040_catalog.model import ROOT,DATA,EVIDENCE,REGISTRY,CURATION,GUIDE,PHENO,META,PAPERS,REVIEWS,encoded,rawpin,pin,assets,validate_assets,check_transition,CANDIDATE_NOTE
from p040_catalog.bridge import load_before,metadata_view,prior_admission
ACTION='Required action: retain diagnostics; resolve exact source, primary-anchor, population or candidate-scope contradictions without changing old scientific controls. Missing/forbidden cached primary sources SKIP3 skip the dependent rescan; --rescan-sources reads all250 exact XML files. This verifies source curation and complete retained candidate evidence, not new original analysis, paper reproduction or biological validity. The22 added names remain missing; Rphenograph0.1.1 remains unresolved despite the separately qualified0.99.1 candidate. Use NEW output paths.'

def rescan_sources(root,papers):
 from collect_omics_review import paragraphs,validate_supporting_evidence
 census,add,defs=validate_assets(root,papers);rows=[];matches=[];lookup={r['id']:r for r in defs}
 for paper in papers:
  raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());sha=hashlib.sha256(raw).hexdigest()
  if sha!=paper['xml_sha256']:raise ValueError('Exact primary XML differs: '+paper['paper_id'])
  xml=ET.fromstring(raw);dois=[e.text for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi']
  if paper['doi'].lower() not in [d.lower() for d in dois if d]:raise ValueError('Primary DOI differs')
  ps=paragraphs(xml);found={}
  for definition in defs:
   indices=[]
   for p in ps:
    spans=list(re.finditer(definition['pattern'],p['match_text'],re.I))
    if spans:
     indices.append(p['paragraph_index']);matches.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],tool_id=definition['id'],paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode('utf8')).hexdigest(),matched_names=[m.group() for m in spans]))
   if indices:found[definition['id']]=indices
  rows.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=sha,primary_paragraphs=len(ps),matched_paragraphs_by_tool=found))
  for use in add['paper_tool_additions'][paper['paper_id']]:
   source=ps[use['evidence_paragraph_index']-1]
   if source['section']!=use['evidence_section'] or hashlib.sha256(source['text'].encode('utf8')).hexdigest()!=use['evidence_paragraph_sha256'] or not re.search(lookup[use['tool_id']]['pattern'],source['match_text'],re.I):raise ValueError('Primary software observation differs')
  validate_supporting_evidence(ps,[r for r in add['reviews'] if r['pmcid']==paper['pmcid']],lookup)
  if paper['paper_id']=='P250':
   negative=census['negative_controls'][0];p=ps[79]
   if negative['paragraph_sha256']!=hashlib.sha256(p['text'].encode('utf8')).hexdigest() or 'DeepCell, Inc.' not in p['text'] or re.search(lookup['deepcell_mesmer']['pattern'],p['match_text'],re.I):raise ValueError('Company disclosure exclusion differs')
 if rows!=census['census'] or matches!=census['matches']:raise ValueError('Fresh full-cohort census differs')
 return dict(primary_sources=250,new_names=22,name_pairs=186,named_tool_paragraph_matches=273,company_disclosure_excluded=True)

def candidate_qualification(root):
 d=root/PHENO;v=json.loads((d/'VALIDATION.json').read_bytes());q=json.loads((d/'QUALIFICATION.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_RPHENOGRAPH099_COMPLETE_CLUSTERING_AND_NATIVE_COEFFICIENTS' or any(v[k] for k in ['paper_package_identity_recovered','original_source_patched','new_tool_credit','whole_tool_qualified','whole_goal_complete']):raise ValueError('Unsupported candidate qualification scope')
 for n,wanted in v['sources'].items():
  if pin(root/n)!=wanted:raise ValueError('Qualified candidate source differs: '+n)
 if pin(d/'controls.zip')!=v['archive'] or pin(d/'QUALIFICATION.json')!=v['qualification']:raise ValueError('Candidate complete archive/qualification differs')
 for line in (d/'SHA256SUMS').read_text(encoding='ascii').splitlines():
  sha,n=line.split('  ',1)
  if pin(d/n)['sha256']!=sha:raise ValueError('Public candidate evidence checksum differs')
 for n,wanted in json.loads((d/'ARTIFACTS.json').read_bytes()).items():
  if pin(root/n)!=wanted:raise ValueError('Published candidate artifact differs')
 if set(q['actual_full_workflow']['cases'])!={'iris','protein40','square','k1','duplicate'} or any(x['exit_code']!=x['expected_exit'] for x in q['actual_full_workflow']['cases'].values()):raise ValueError('Incomplete actual candidate workflow population')
 for name,row in q['complete_model_comparisons'].items():
  if not row['complete_public_graph_and_community_identical'] or not all(row['complete_components_identical'].values()) or row['complete_community_fields']!=['membership','memberships','modularity','names','vcount','algorithm']:raise ValueError('Independent complete model comparison differs')
 if set(q['complete_model_comparisons'])!={'iris','protein40','square','duplicate'}:raise ValueError('Incomplete returned-model population')
 if {k:p['packaged_protocol']['packaged_checks'] for k,p in q['platforms'].items()}!={'windows21':27,'windows25':27,'linux21':32} or len(q['setup_refusals']['calls'])!=8:raise ValueError('Incomplete candidate platform/refusal qualification')
 for p in q['platforms'].values():
  if (p['native_coefficients']['cases'],p['native_coefficients']['complete_rows'],p['native_coefficients']['assertions'])!=(863,105921,637388) or any(x['exit_code']!=x['expected_exit'] for x in p['packaged_protocol']['calls']):raise ValueError('Complete native source comparison/control differs')
 replay=json.loads((d/'REPLAYS.json').read_bytes())
 if replay['status']!='PASS_THREE_COMPLETE_PUBLIC_RESTORATIONS_REBUILDS_AND_NATIVE_REPLAYS' or replay['packaged_checks']!=80 or replay['original_R_execution_repeated'] or replay['RDS_reloaded']:raise ValueError('Public candidate replay scope differs')
 with zipfile.ZipFile(d/'controls.zip') as z:
  layout=json.loads(z.read('LAYOUT.json'))
  if layout!={'schema':'RPHENOGRAPH099_COMPLETE_OBJECTS_V1','files':v['archive_members']}:raise ValueError('Complete candidate archive population differs')
  checked=set()
  for n,row in layout['files'].items():
   if row['sha256'] not in checked:
    raw=z.read('objects/'+row['sha256'])
    if rawpin(raw)!=row:raise ValueError('Complete candidate evidence object differs')
    checked.add(row['sha256'])
  def content(name):return z.read('objects/'+layout['files'][name]['sha256'])
  for name in ['iris','protein40','square','duplicate']:
   if content('models/'+name+'-adapter-state.rds')!=content('models/'+name+'-reference-state.rds'):raise ValueError('Complete independent graph/community state differs')
  square=json.loads(content('bank/square/analysis-status.json'));k1=json.loads(content('bank/k1/analysis-status.json'))
  if square['result_admitted'] or k1['result_admitted'] or square['exit_code']!=2 or k1['exit_code']!=2 or b'Not a matrix.' not in content('bank/k1/worker-error.txt'):raise ValueError('Original failure/unreportable result was admitted/repaired')
 return dict(version='0.99.1',paper_reported_version='0.1.1',paper_package_identity_recovered=False,complete_coefficient_cases=863,complete_coefficient_rows=105921,complete_returned_original_models=4,packaged_checks=86,setup_refusals=8,public_saved_replay_checks=80,new_scoped_integration_credit=0,original_R_execution_repeated=False)

def load_catalog_registry(root=ROOT):
 from batchelor129_catalog.verify import load_catalog_registry as previous
 before=load_before(root);old=previous(metadata_view(root,before));new=json.loads((root/REGISTRY).read_bytes())
 if len(old)!=445 or len(new)!=22 or {r['id'] for r in old}&{r['id'] for r in new}:raise ValueError('New registry population/identity differs')
 result=[dict(r,coverage_note=CANDIDATE_NOTE) if r['id']=='rphenograph' else r for r in old]+new
 if len({r['id'] for r in result})!=467:raise ValueError('Duplicate matcher IDs')
 return result

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/DATA/'p040-catalog-validation.json').read_bytes())
 if proof['status']!='PASS_P040_SOFTWARE_CENSUS_AND_EXPLICIT_RPHENOGRAPH_CANDIDATE' or proof['new_scoped_integration_credit'] or proof['whole_goal_complete']:raise ValueError('Unsupported catalog scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Current source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive']:raise ValueError('Exact predecessor archive differs')
 result=check_transition(root,before,{n:(root/n).read_bytes() for n in META});papers=json.loads(before[PAPERS]);validate_assets(root,papers)
 from catalog_source_integrity import reverse_delta,verify_source
 compatibility=json.loads((root/DATA/'p040-reviews-shared-source-compatibility.json').read_bytes())
 if compatibility['schema_version']!=1 or compatibility['status']!='passed-dispatch-catalog-only-source-deltas' or len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:raise ValueError('Exact appended-review inverse differs')
 verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256']);old=prior_admission(root,before);candidate=candidate_qualification(root);registry=load_catalog_registry(root)
 source=rescan_sources(root,papers) if rescan else dict(primary_sources=250,new_names=22,name_pairs=186,named_tool_paragraph_matches=273,company_disclosure_excluded=True)
 return dict(status=proof['status'],**result,registry_definitions=len(registry),primary_sources_rescanned=rescan,source_census=source,candidate=candidate,previous_admission=old,scientific_sources_overridden=False,scientific_verifiers_changed=False,paper_wide_semantic_review_open=True,original_analysis_executed=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_PRIMARY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_P040_CATALOG',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
