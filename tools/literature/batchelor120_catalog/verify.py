#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the exact two-role Batchelor catalog amendment without replacing science."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor120_catalog.model import ROOT,DATA,EVIDENCE,WORKFLOW,META,PAPERS,REVIEWS,REGISTRY,REGISTRY_ROW,PATTERN,pin,encoded,entry,check_transition
from batchelor120_catalog.bridge import load_before,prior_admission,metadata_view
ACTION='Required action: retain diagnostics. Resolve the named exact catalog delta, primary source, registry or scientific-integrity error; do not repin qualified evidence or award an uncovered role credit. --rescan-sources requires all250 exact cached XML sources; missing/forbidden prerequisites SKIP3 the dependent rescan. Default verification checks saved evidence, not a fresh scientific run or authenticated producer. Use NEW output paths.'

def qualification(root):
 d=root/WORKFLOW;v=json.loads((d/'VALIDATION.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_MONOCLE137_BATCHELOR120_COMPLETE_PREPARED_PIPELINE_AND_NATIVE_ORDERING' or v['new_tool_credit'] or v['whole_Batchelor_qualified'] or v['whole_Monocle_qualified'] or v['whole_paper_reproduced'] or v['whole_goal_complete'] or v['original_source_patched']:raise ValueError('Unsupported workflow qualification scope')
 for n,wanted in v['sources'].items():
  if pin(root/n)!=wanted:raise ValueError('Qualified workflow source differs: '+n)
 if pin(d/'controls.zip')!=v['archive']:raise ValueError('Complete workflow archive differs')
 if v['complete_prepared_population']!={'genes':64,'cells':480} or v['public_provider_qualification']['complete_original_PCA_and_alignment_equal'] is not True or v['disconnected_qualification']['infinite_cells']!=240 or v['disconnected_qualification']['all_cells_retained'] is not True:raise ValueError('Full alignment/cell qualification differs')
 for row in v['original_pipeline_qualification']['calls']:
  if row['exit_code']!=row['expected_exit']:raise ValueError('An original workflow control failed')
 if sum(x['checks'] for x in v['packaged_platforms'].values())!=108 or v['setup_refusal_controls']!=5:raise ValueError('Incomplete packaged/setup controls')
 for line in (d/'SHA256SUMS').read_text(encoding='utf8').splitlines():
  expected,name=line.split('  ',1)
  if pin(d/name)['sha256']!=expected:raise ValueError('Workflow public checksum differs: '+name)
 return dict(source_files=len(v['sources']),complete_genes=64,complete_cells=480,packaged_checks=108,infinite_cells_retained=240,original_fitting_repeated=False,whole_Batchelor_qualified=False)

def census(root,papers):
 from collect_omics_review import paragraphs
 records=[]
 for p in papers:
  path=root/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')
  raw=gzip.decompress(path.read_bytes())
  if hashlib.sha256(raw).hexdigest()!=p['xml_sha256']:raise ValueError('Pinned primary XML differs: '+p['paper_id'])
  xml=ET.fromstring(raw);ids=[x.text for x in xml.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
  if p['doi'].lower() not in [x.lower() for x in ids if x]:raise ValueError('Primary DOI differs: '+p['paper_id'])
  ps=paragraphs(xml);matches=[]
  for paragraph in ps:
   tokens=re.findall(PATTERN,paragraph['match_text'],re.I)
   if tokens:matches.append(dict(paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest(),matched_tokens=tokens))
  records.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],xml_sha256=p['xml_sha256'],paragraphs=len(ps),matches=matches))
 return dict(schema_version=1,status='PASS_250_PINNED_MAIN_ARTICLE_PARAGRAPH_BATCHELOR_FUNCTION_CENSUS',pattern=PATTERN,papers=250,matched_papers=[r['paper_id'] for r in records if r['matches']],matched_paragraphs=sum(len(r['matches']) for r in records),records=records,scope='Main-article paragraph population from the fixed250 XML sources; supplements, author notebooks and unnamed algorithm variants are not exhaustively searched.',whole_paper_semantic_completeness_proven=False)

def validate_census(value,papers):
 if value['pattern']!=PATTERN or value['papers']!=250 or value['matched_papers']!=['P040','P051'] or value['matched_paragraphs']!=3 or value['whole_paper_semantic_completeness_proven']:raise ValueError('Source census scope differs')
 if len(value['records'])!=250:raise ValueError('Incomplete source census')
 for row,p in zip(value['records'],papers,strict=True):
  if any(row[k]!=p[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Census source identity differs')
 matches={row['paper_id']:row['matches'] for row in value['records'] if row['matches']}
 if [x['paragraph_index'] for x in matches['P040']]!=[28,29] or [x['paragraph_index'] for x in matches['P051']]!=[86]:raise ValueError('Source role anchors differ')

def validate_role_anchors(value,uses):
 by={row['paper_id']:{p['paragraph_index']:p for p in row['matches']} for row in value['records'] if row['matches']}
 for paper_id,use in uses.items():
  anchor=by[paper_id][use['evidence_paragraph_index']]
  if anchor['paragraph_sha256']!=use['evidence_paragraph_sha256'] or anchor['section']!=use['evidence_section'] or use['matched_text'] not in anchor['matched_tokens']:raise ValueError('New observation does not match the primary census: '+paper_id)
  for support in use['supporting_evidence']:
   primary=by[paper_id][support['paragraph_index']]
   if any(primary[k]!=support[k] for k in ['paragraph_sha256','section']):raise ValueError('New supporting evidence differs from the primary census: '+paper_id)

def load_catalog_registry(root=ROOT):
 # Matchers are data, not executable metadata providers. Retain every admitted
 # observed definition plus the38 unobserved original definitions, without
 # invoking obsolete dispatcher snapshots in historical registry providers.
 original=(root/'tools/literature/tool-registry.json').read_bytes()
 if hashlib.sha256(original).hexdigest()!='8c1a86f28a9dc10a8e9c46a7f906a5be34328f621a19611a66609fa6a8179065':raise ValueError('Original425 matcher definitions differ')
 rows=json.loads(original)
 if len(rows)!=425:raise ValueError('Original matcher population differs')
 before=load_before(root);observed=json.loads(before[META[0]]);fields=['id','name','pattern','role','current_coverage','existing_source','coverage_note'];by={r['id']:r for r in rows}
 for row in observed:by[row['id']]={k:row[k] for k in fields}
 rows=list(by.values())
 extra=json.loads((root/REGISTRY).read_bytes())
 if extra!=[REGISTRY_ROW] or len(rows)!=443 or any(r['id']=='batchelor' for r in rows):raise ValueError('Registry addition differs')
 result=rows+extra
 if len({r['id'] for r in result})!=444:raise ValueError('Duplicate registry IDs')
 return result

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/DATA/'batchelor-catalog-validation.json').read_bytes())
 if proof['status']!='PASS_TWO_PRIMARY_BATCHELOR_ROLES_WITH_EXPLICIT_FASTMNN_GAP' or proof['new_scoped_integration_credit'] or proof['whole_goal_complete'] or proof['whole_tool_credit']:raise ValueError('Unsupported catalog credit')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Catalog source/evidence differs: '+n)
 before=load_before(root)
 from catalog_source_integrity import verify_source,reverse_delta
 from batchelor120_catalog.model import rawpin
 compatibility=json.loads((root/DATA/'batchelor-reviews-shared-source-compatibility.json').read_bytes())
 if compatibility['status']!='passed-dispatch-catalog-only-source-deltas' or compatibility['schema_version']!=1 or len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:raise ValueError('Exact appended-review compatibility differs')
 verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive']:raise ValueError('Predecessor archive identity differs')
 result=check_transition(before,{n:(root/n).read_bytes() for n in META},entry(root))
 papers=json.loads((root/PAPERS).read_bytes());value=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());validate_census(value,papers)
 roles=json.loads((root/DATA/'batchelor-role-coverage.json').read_bytes());uses={p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='batchelor') for p in papers if any(t['tool_id']=='batchelor' for t in p['tools'])}
 validate_role_anchors(value,uses)
 if roles['source_observations']!=uses or roles['covered_selected_roles']!=['P051: explicitly selected reducedMNN interpretation'] or roles['uncovered_roles']!=['P040: fastMNN on imaging-protein measurements; reported method version1.29'] or roles['paper_function_and_settings_recovered'] or roles['whole_tool_qualified'] or roles['new_scoped_integration_credit']:raise ValueError('Role coverage overstates support')
 review=json.loads((root/EVIDENCE/'P040_AUTHOR_REVIEW.json').read_bytes())
 if review['commit']!='c8074a59e2fd6497e4e613eb2b2f9ea4f2a85e3d' or review['license'] is not None or review['code_executed'] or review['code_redistributed'] or review['actual_fastMNN_call_recovered'] or review['exact_package_version_recovered']:raise ValueError('Author code review scope differs')
 q=qualification(root);old=prior_admission(root,before);registry=load_catalog_registry(root)
 if rescan and census(root,papers)!=value:raise ValueError('Fresh complete primary census differs')
 return dict(status='PASS_TWO_PRIMARY_BATCHELOR_ROLES_WITH_EXPLICIT_FASTMNN_GAP',papers=250,observations=2481,reviewed_uses=1054,named_tools=406,registry_definitions=len(registry),new_scoped_integrations=102,missing=245,new_scoped_integration_credit=0,whole_tool_credit=0,qualification=q,transition=result,previous_admission=old,primary_predecessor_paths=[PAPERS,REVIEWS],primary_change='Only two appended Batchelor observations/reviews and one supporting pointer; all previous records unchanged',scientific_sources_overridden=False,scientific_verifiers_changed=False,primary_sources_rescanned=rescan,original_fitting_repeated=False,paper_wide_semantic_review_open=True,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Output must be a NEW monocle-runtime child')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;state=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_BATCHELOR_CATALOG',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(state));(out/'WARNINGS_FAILURE.md').write_text(state['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8')
  print(state['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/transition checks')
 raise SystemExit(main())
