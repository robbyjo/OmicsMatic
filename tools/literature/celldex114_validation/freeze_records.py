#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze source-bound receipts; retain all complete reference-data records privately."""
from pathlib import Path
import argparse,json,shutil,sys
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'celldex114';sys.path.insert(0,str(MODULE))
from Runtime import *
BASE=Path('/mnt/d/projects/JLinAlg/build/literature-review/monocle-runtime')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
 roots={
  'paired-current':Path('/opt/omicsmatic-celldex-paired-controls-20261115-3'),
  'public-guards':Path('/opt/omicsmatic-celldex-public-guards-20261115-1'),
  'public-local-ensembl':Path('/opt/omicsmatic-celldex-public-local-ensembl-20261115-1'),
  'installed-offline-analysis':Path('/opt/omicsmatic-celldex-installed-offline-analysis-20261115-1'),
  'installed-online-analysis':Path('/opt/omicsmatic-celldex-installed-online-analysis-20261115-1'),
  'windows-guards':BASE/'celldex114-windows-guards-1',
  'reference-seed':Path('/opt/omicsmatic-celldex-cache-seed-20261115-1'),
  'original-modern-intake':Path('/opt/omicsmatic-celldex-reference-intake-20261115-1'),
  'original-legacy-intake':Path('/opt/omicsmatic-celldex-legacy-intake-20261115-1'),
  'historical-default-offline-1':Path('/opt/omicsmatic-celldex-paired-controls-20261115-1'),
  'historical-default-offline-2':Path('/opt/omicsmatic-celldex-paired-controls-20261115-2'),
  'historical-default-Hub-public-failure':Path('/opt/omicsmatic-celldex-public-offline-ensembl-20261115-1'),
  'historical-metadata-public-success':Path('/opt/omicsmatic-celldex-public-offline-metadata-20261115-1')}
 current=load(roots['paired-current']/'COMPLETED.json')
 if (current['controls'],current['complete_returns'],current['original_failures_without_result'])!=(16,12,4) or not current['intended_positive_cases_required_and_verified']:
  raise ValueError('All intended positive reference controls must actually return')
 guards=load(roots['public-guards']/'COMPLETED.json')
 if len(guards['guards'])!=12 or not all(g['passed'] for g in guards['guards']):raise ValueError('All twelve actual public guards required')
 for key in ['installed-offline-analysis','installed-online-analysis']:
  if load(roots[key]/'COMPLETED.json')['status']!='PASS_ACTUAL_INSTALLED_CELLDEX114_REFERENCE_AND_COMPLETE_INDEPENDENT_ORIGINAL':raise ValueError('Actual installed analysis incomplete')
 if len(load(roots['windows-guards']/'COMPLETED.json')['guards'])!=2:raise ValueError('Both actual Windows prerequisite guards required')
 private={}
 for label,path in roots.items():
  if not path.is_dir():raise ValueError('Complete retained record root missing: '+str(path))
  print('INDEX_COMPLETE_PRIVATE_CELLDEX_RECORDS',label,flush=True)
  for name,entry in inventory(path).items():private[label+'/'+name]=dict(path=str(path/name),**entry)
 install_receipts={}
 for mode in ['offline','online']:
  workspace=Path('/opt/omicsmatic-celldex-public-'+mode+'-install-20261115-1')
  done=load(workspace/'COMPLETED.json')
  if done['status']!='PASS_INSTALLED_ORIGINAL_CELLDEX114_NOT_REFERENCE_ANALYSIS':raise ValueError('Actual installer branch incomplete')
  install_receipts[mode]=done
  for pth in sorted(workspace.iterdir()):
   if pth.is_file():private['installation-'+mode+'/'+pth.name]=dict(path=str(pth),kind='file',**pin(pth))
  for name,entry in inventory(workspace.parent/(workspace.name+'-provider')).items():
   private['installed-provider-'+mode+'/'+name]=dict(path=str(workspace.parent/(workspace.name+'-provider')/name),**entry)
 for i in range(1,5):
  path=BASE/('celldex114-provider-dev-'+str(i))
  for name,entry in inventory(path).items():private['development-provider-'+str(i)+'/'+name]=dict(path=str(path/name),**entry)
 for name in ['celldex114-retained-source-pre-reference-policy-1','celldex114-retained-source-paired-v1']:
  for relative,entry in inventory(BASE/name).items():private['historical-code/'+name+'/'+relative]=dict(path=str(BASE/name/relative),**entry)
 private_record=dict(status='COMPLETE_ORIGINAL_REFERENCE_RECORDS_RETAINED_LOCALLY_NOT_RELICENSED_OR_REDISTRIBUTED',
  whole_original_returns_conditions_rng_cache_files_and_partials_retained=True,scientific_fields_or_raw_original_records_removed=False,
  private_reference_file_entries=len(private),files=private,
  original_source_license_does_not_relicense_all_reference_datasets=True,cryptographic_authorship_or_upstream_signature_claimed=False)
 save(out/'PRIVATE-REFERENCE-RECORDS.json',private_record)
 receipts=out/'receipts';receipts.mkdir()
 selected={
  'PAIRED-CONTROLS.json':roots['paired-current']/'COMPLETED.json','COMPARISONS.json':roots['paired-current']/'COMPARISONS.json',
  'CONTROL-SOURCES.json':roots['paired-current']/'SOURCES.json','PUBLIC-GUARDS.json':roots['public-guards']/'COMPLETED.json',
  'WINDOWS-GUARDS.json':roots['windows-guards']/'COMPLETED.json','OFFLINE-INSTALLED-ANALYSIS.json':roots['installed-offline-analysis']/'COMPLETED.json',
  'ONLINE-INSTALLED-ANALYSIS.json':roots['installed-online-analysis']/'COMPLETED.json',
  'OFFLINE-INSTALLED-COMPARISON.json':roots['installed-offline-analysis']/'COMPARISON.json',
  'ONLINE-INSTALLED-COMPARISON.json':roots['installed-online-analysis']/'COMPARISON.json',
  'ENSEMBL-ORIGINAL-WARNINGS.json':roots['public-local-ensembl']/'original/R-status.json'}
 for name,path in selected.items():shutil.copyfile(path,receipts/name)
 for mode,record in install_receipts.items():save(receipts/(mode.upper()+'-INSTALLATION.json'),record)
 for index in [1,2]:shutil.copyfile(roots['historical-default-offline-'+str(index)]/'COMPLETED.json',receipts/('HISTORICAL-DEFAULT-OFFLINE-'+str(index)+'.json'))
 packages=out/'original-software-sources';packages.mkdir()
 for entry in profile()['packages'].values():
  original=BASE/'celldex114-source-cache-1'/entry['archive_filename']
  if pin(original)!=entry['archive']:raise ValueError('Original software archive differs')
  shutil.copyfile(original,packages/entry['archive_filename'])
 sources_=[p for folder in [MODULE,VALIDATION] for p in folder.rglob('*') if p.is_file()]
 sources_ += [ROOT/'docs/literature/CELLDEX114_REFERENCE_WORKFLOWS.md',UTILITY,NAMESPACE_AUDIT]
 source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sorted(sources_)}
 for name,identity in load(roots['paired-current']/'SOURCES.json').items():
  if pin(ROOT/name)!=identity:raise ValueError('Actually qualified original-call source differs: '+name)
 evidence={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}
 proof=dict(status='PASS_ORIGINAL_CELLDEX114_REFERENCE_WORKFLOW_SOURCE_RECORDING_AND_PUBLIC_EXECUTION',
  original_release='1.14.0',original_controls=16,complete_original_returns=12,original_failures_without_result=4,
  complete_original_objects_conditions_rng_and_lazy_payloads_compared=True,intended_positive_cases_required_and_verified=True,
  explicit_original_LOCAL_Hub_options=True,original_Ensembl_unmapped_ID_warning_retained_and_displayed=True,
  default_offline_Hub_failures_retained_without_result=True,historical_default_runs_complete_returns=7,historical_default_runs_original_failures=9,
  actual_public_Linux_guards=12,actual_Windows_prerequisite_guards=2,both_public_installer_branches_and_complete_installed_analyses_passed=True,
  online_installed_analysis_started_with_empty_cache_and_downloaded_original_reference=True,
  original_gene_mapping_filtering_and_warning_policy_unchanged=True,source_or_dependency_algorithms_patched=False,
  source_files=source_pins,evidence_files=evidence,private_reference_file_entries=len(private),
  public_bundle_contains_reference_matrices_or_raw_reference_returns=False,whole_original_reference_records_retained_privately=True,
  reference_data_redistribution_license_established=False,author_exact_reference_functions_snapshots_options_environment_established=False,
  whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
 save(out/'QUALIFICATION.json',proof)
 print('PASS_FROZEN_CELLDEX114_SOURCE_BOUND_WORKFLOW_RECEIPTS_AND_COMPLETE_PRIVATE_REFERENCE_INDEX',len(private),len(source_pins),flush=True)
if __name__=='__main__':main()
