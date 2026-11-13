#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete FastMNN admission and an exact additional source-use record."""
from pathlib import Path
import argparse,gzip,hashlib,json,math,re,sys,traceback,xml.etree.ElementTree as ET,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor129_catalog.model import ROOT,DATA,EVIDENCE,WORKFLOW,REGISTRY,REGISTRY_ROW,META,PAPERS,REVIEWS,encoded,pin,rawpin,entries,additions,check_transition
from batchelor129_catalog.bridge import load_before,metadata_view,prior_admission
ACTION='Required action: retain diagnostics; resolve the named exact catalog, source, full-model or role-coverage contradiction without repinning old scientific controls. Missing/forbidden source-cache prerequisites SKIP3 the dependent rescan. --rescan-sources reads all 250 exact cached XML files. Verification checks complete retained evidence and strict live source hashes; it does not repeat original R fitting, reload RDS, infer paper settings or authenticate producers. Rphenograph 0.1.1 remains unresolved and missing; preserve that literal. Use NEW output paths.'

def fastmnn_qualification(root):
 d=root/WORKFLOW;v=json.loads((d/'VALIDATION.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_FASTMNN129_COMPLETE_PREPARED_CORRECTION_AND_NATIVE_AUDITS' or any(v[k] for k in ['original_source_patched','paper_patch_recovered','whole_paper_reproduced','whole_tool_qualified','new_tool_credit','whole_goal_complete']):raise ValueError('Unsupported original FastMNN qualification scope')
 for name,wanted in v['sources'].items():
  if pin(root/name)!=wanted:raise ValueError('Qualified FastMNN source differs: '+name)
 if pin(d/'controls.zip')!=v['archive'] or pin(d/'QUALIFICATION.json')!=v['qualification']:raise ValueError('Complete FastMNN evidence integrity differs')
 for line in (d/'SHA256SUMS').read_text(encoding='ascii').splitlines():
  sha,name=line.split('  ',1)
  if pin(d/name)['sha256']!=sha:raise ValueError('FastMNN public checksum differs: '+name)
 q=json.loads((d/'QUALIFICATION.json').read_bytes());bank=q['original_cases'];cases=bank['cases']
 if bank['status']!='PASS_NINETEEN_COMPLETE_ORIGINAL_FASTMNN129_STREAM_CONTROLS' or bank['failures'] or len(cases)!=19:raise ValueError('Incomplete original 19-case population')
 successes={};failures={}
 for name,row in cases.items():
  if row['status']=='PASS_COMPLETE_ORIGINAL_FASTMNN129_STREAMED_ALL_VALUES_PARITY':
   successes[name]=row
   if row['complete_cells']!=360 or not 1<=row['complete_features']<=72 or not row['complete_original_roundtrip'] or set(row['complete_comparisons'])!={'cell_ids','feature_ids','batches','corrected','rotation','reconstructed'} or not all(row['complete_comparisons'].values()):raise ValueError('Incomplete independent original axes/matrices: '+name)
   if set(row['complete_RDS_and_metadata'])!={'original-fit.rds','metadata.rds'} or not all(v['bytewise_equal'] and v['bytes']>0 for v in row['complete_RDS_and_metadata'].values()):raise ValueError('Complete original model/metadata equality missing: '+name)
   a=row['native_audit']
   if a['status']!='PASS_COMPLETE_FASTMNN_STREAMED_RECONSTRUCTION_AND_SEED_FACTORS' or a['reconstruction_audit']!='ALL_VALUES' or not a['complete_original_low_rank_seed_factors_equal'] or a['original_analysis_admitted'] or a['original_fitting_repeated'] or a['producer_authenticated'] or a['numeric_reconstruction_values_checked']!=360*row['complete_features'] or not math.isfinite(a['maximum_absolute_difference']):raise ValueError('Incomplete/unsupported native reconstruction scope: '+name)
  elif row['status']=='PASS_ORIGINAL_UNNAMED_WEIGHT_FAILURE_PRESERVED':
   failures[name]=row
   if row['actual_exit']!=2 or row['result_admitted'] or not row['explicit_recovery_visible']:raise ValueError('An original failed weighted model was admitted')
  else:raise ValueError('Unqualified original case: '+name)
 if len(successes)!=17 or set(failures)!={'restricted_weighted','restricted_weighted_exact'}:raise ValueError('Original success/failure census differs')
 provider=q['fresh_provider']
 if provider['status']!='READY_PRIVATE_ORIGINAL_FASTMNN129_NOT_ANALYSIS' or provider['analysis_admitted'] or provider['paper_patch_recovered'] or provider['version']!='1.29.0' or provider['source_commit']!='165c212df5312de6da18dc0a2b78929beaffc812' or len(provider['calls'])!=5 or any(x['exit_code'] for x in provider['calls']):raise ValueError('Fresh original source/runtime installation not qualified')
 if set(q['platforms'])!={'windows21','windows25','linux21'} or {k:x['packaged_checks'] for k,x in q['platforms'].items()}!={'windows21':43,'windows25':43,'linux21':47}:raise ValueError('Incomplete three-platform native/refusal qualification')
 for platform,row in q['platforms'].items():
  if row['complete_successful_original_cases']!=17 or row['complete_lazy_factor_cases']!=1 or not row['legacy_original_checked'] or len(row['calls'])!=row['packaged_checks'] or any(x['exit_code']!=x['expected_exit'] for x in row['calls']):raise ValueError('A packaged comparison/refusal differs: '+platform)
 setup=q['setup_refusals']
 if setup['status']!='PASS_SEVEN_ACTUAL_FASTMNN_SETUP_REFUSALS' or len(setup['calls'])!=7 or any(x['exit_code']!=x['expected_exit'] or x['compiler_launched'] or x['analysis_launched'] for x in setup['calls']):raise ValueError('Installer permission/source refusals not qualified')
 encoding=q['source_encoding'];profile=json.loads((root/'src/main/resources/org/jlinalg/omics/fastmnn129/source-profile.json').read_bytes())
 if encoding['status']!='PASS_NINETY_RAW_OFFICIAL_GIT_BLOBS_AND_EXACT_CRLF_CORRESPONDENCE' or len(encoding['members'])!=90 or encoding['original_scientific_code_patched'] or encoding['analysis_input_normalized'] or encoding['installed_function_profiles_changed'] or len(profile['functions'])!=105 or len(profile['package_versions'])!=51:raise ValueError('Canonical original source/namespace correspondence differs')
 if {x['path']:x['canonical_git_blob'] for x in encoding['members']}!=profile['source_members'] or not all(x['only_CRLF_LF_difference'] for x in encoding['members']):raise ValueError('Raw Git source fingerprints differ')
 with zipfile.ZipFile(d/'controls.zip') as z:
  layout=json.loads(z.read('LAYOUT.json'))
  if layout!={'schema':'FASTMNN129_COMPLETE_MODEL_OBJECTS_V1','files':v['archive_members']}:raise ValueError('Complete archive population differs')
  def content(name):
   row=layout['files'][name];raw=z.read('objects/'+row['sha256'])
   if rawpin(raw)!=row:raise ValueError('Complete evidence object differs: '+name)
   return raw
  for name,row in successes.items():
   for file,wanted in row['complete_RDS_and_metadata'].items():
    actual=content('bank/'+name+'/adapter/'+file);original=content('independent/'+name+'/reference/cases/'+name+'/'+file)
    if actual!=original or rawpin(actual)!={k:wanted[k] for k in ['bytes','sha256']}:raise ValueError('Full saved original model differs: '+name+' '+file)
  lazy=json.loads(content('factors/native-audit.json'))
  if lazy['reconstruction_audit']!='FACTORS' or lazy['numeric_reconstruction_values_checked']!=0 or lazy['maximum_absolute_difference'] is not None or not lazy['complete_original_low_rank_seed_factors_equal']:raise ValueError('Lazy mode falsely claimed reconstructed numeric values')
 replay=json.loads((d/'REPLAYS.json').read_bytes())
 if replay['status']!='PASS_THREE_COMPLETE_PUBLIC_RESTORATIONS_REBUILDS_AND_NATIVE_REPLAYS' or replay['packaged_checks']!=128 or len(replay['calls'])!=3 or any(x['exit_code'] for x in replay['calls']) or replay['original_R_fitting_repeated'] or replay['RDS_reloaded'] or replay['producer_authenticated']:raise ValueError('Full saved public replay scope differs')
 return dict(status=v['status'],original_cases=19,complete_successful_models=17,original_failures_preserved=2,full_model_and_metadata_bytewise_matches=17,qualified_packaged_checks=133,setup_refusals=7,complete_public_replay_checks=128,raw_git_source_members=90,installed_named_functions=105,namespaces=51,original_R_fitting_repeated=False,source_candidate_patch_recovered=False,whole_tool_qualified=False)

def validate_phenograph(root,papers,use,rescan=False):
 value=json.loads((root/'docs/literature/phenograph-source-review/CENSUS.json').read_bytes());source=json.loads((root/'docs/literature/phenograph-source-review/SOURCE.json').read_bytes())
 if value['status']!='PASS_250_PINNED_PRIMARY_PARAGRAPH_PHENOGRAPH_NAME_CENSUS' or value['papers']!=250 or value['matched_papers']!=['P040'] or len(value['matches'])!=1 or len(value['census'])!=250:raise ValueError('Incomplete full-cohort Rphenograph name census')
 for row,p in zip(value['census'],papers,strict=True):
  if row['paper_id']!=p['paper_id'] or row['xml_sha256']!=p['xml_sha256'] or row['matched_paragraph_indices']!=([29] if p['paper_id']=='P040' else []):raise ValueError('Rphenograph census identity differs')
 anchor=value['matches'][0]
 for old,new in [('paragraph_sha256','evidence_paragraph_sha256'),('paragraph_index','evidence_paragraph_index'),('section','evidence_section')]:
  if anchor[old]!=use[new]:raise ValueError('New Rphenograph observation anchor differs')
 if anchor['reported_version_literal']!='0.1.1' or anchor['reported_k']!=40 or source['paper_reported_version_literal']!='0.1.1' or source['paper_reported_k']!=40 or source['exact_reported_version_found_in_inspected_history'] or source['source_executed'] or source['head_commit']!='0298487f0ee13aac55eb77d19992f6bd878ba2fc' or len(source['history_versions'])!=4 or any(x['version']!='0.99.1' or x['license']!='Artistic-2.0' for x in source['history_versions']):raise ValueError('Reported/upstream version discrepancy was lost or overclaimed')
 if rescan:
  from collect_omics_review import paragraphs
  for paper,row in zip(papers,value['census'],strict=True):
   raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
   if hashlib.sha256(raw).hexdigest()!=paper['xml_sha256']:raise ValueError('Pinned primary XML differs: '+paper['paper_id'])
   xml=ET.fromstring(raw);ids=[x.text for x in xml.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
   if paper['doi'].lower() not in [x.lower() for x in ids if x]:raise ValueError('Primary DOI differs')
   found=[p for p in paragraphs(xml) if re.search(value['pattern'],p['match_text'],re.I)]
   if [p['paragraph_index'] for p in found]!=row['matched_paragraph_indices']:raise ValueError('Fresh full-cohort Rphenograph name census differs')
   if found:
    p=found[0]
    if hashlib.sha256(p['text'].encode()).hexdigest()!=anchor['paragraph_sha256'] or p['section']!=anchor['section'] or '(v.0.1.1)' not in p['text'] or 'k = 40' not in p['text']:raise ValueError('Reported Rphenograph version/k/anchor differs')
 return dict(source_papers=250,matched_papers=['P040'],matched_paragraph=29,reported_version_literal='0.1.1',upstream_inspected_version='0.99.1',source_executed=False,implemented=False)

def load_catalog_registry(root=ROOT,before=None):
 from batchelor120_catalog.verify import load_catalog_registry as previous
 before=load_before(root) if before is None else before;old=previous(metadata_view(root,before));fields=['id','name','pattern','role','current_coverage','existing_source','coverage_note'];batch,_=entries(root,before)
 rows=[({k:batch[k] for k in fields} if row['id']=='batchelor' else row) for row in old];extra=json.loads((root/REGISTRY).read_bytes())
 if len(old)!=444 or extra!=[REGISTRY_ROW] or any(x['id']=='rphenograph' for x in old):raise ValueError('Registry addition differs')
 result=rows+extra
 if len({x['id'] for x in result})!=445:raise ValueError('Duplicate matcher IDs')
 return result

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/DATA/'batchelor129-catalog-validation.json').read_bytes())
 if proof['status']!='PASS_TWO_SELECTED_BATCHELOR_WORKFLOWS_AND_REVIEWED_RPHENOGRAPH_GAP' or proof['new_scoped_integration_credit']!=1 or proof['whole_tool_credit'] or proof['whole_goal_complete']:raise ValueError('Unsupported catalog admission scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Current catalog source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive']:raise ValueError('Exact predecessor archive differs')
 result=check_transition(before,{n:(root/n).read_bytes() for n in META},entries(root,before));use,review=additions(before)
 compatibility=json.loads((root/DATA/'batchelor129-reviews-shared-source-compatibility.json').read_bytes())
 from catalog_source_integrity import reverse_delta,verify_source
 if compatibility['status']!='passed-dispatch-catalog-only-source-deltas' or compatibility['schema_version']!=1 or len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:raise ValueError('Exact appended-review compatibility differs')
 verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
 old=prior_admission(root,before);f=fastmnn_qualification(root);papers=json.loads((root/PAPERS).read_bytes());p=validate_phenograph(root,papers,use,rescan);registry=load_catalog_registry(root,before)
 from batchelor120_catalog.verify import census,validate_census,qualification
 primary=json.loads((root/'docs/literature/batchelor-catalog-integration/CENSUS.json').read_bytes());validate_census(primary,papers)
 if rescan and census(root,papers)!=primary:raise ValueError('Fresh complete Batchelor source census differs')
 roles=json.loads((root/DATA/'batchelor129-role-coverage.json').read_bytes());batch,_=entries(root,before);observations={p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='batchelor') for p in papers if any(t['tool_id']=='batchelor' for t in p['tools'])}
 if roles['source_observations']!=observations or roles['covered_selected_roles']!=batch['covered_selected_roles'] or roles['uncovered_reported_Batchelor_operations'] or roles['paper_settings_recovered'] or roles['whole_tool_qualified'] or roles['new_scoped_integration_credit']!=1:raise ValueError('Selected role coverage overstates source support')
 q=qualification(root)
 return dict(status=proof['status'],papers=250,named_tools=407,observations=2482,reviewed_uses=1055,registry_definitions=len(registry),new_scoped_integrations=103,missing=245,new_scoped_integration_credit=1,whole_tool_credit=0,primary_sources_rescanned=rescan,unchanged_previous_observations=2481,unchanged_previous_reviews=1054,primary_addition='One source-anchored P040 Rphenograph observation and review; all prior records unchanged',transition=result,fastmnn_qualification=f,retained_reducedMNN_qualification=q,rphenograph_source=p,previous_admission=old,scientific_sources_overridden=False,scientific_verifiers_changed=False,original_fitting_repeated=False,paper_wide_semantic_review_open=True,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime child output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_BATCHELOR129_CATALOG',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(value));(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/transition checks')
 raise SystemExit(main())
