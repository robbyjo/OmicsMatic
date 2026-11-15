#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete synthetic records and exact source-bound qualification receipts."""
from pathlib import Path
import argparse,shutil,sys,zipfile
sys.dont_write_bytecode=True
VALIDATION=Path(__file__).resolve().parent;MODULE=VALIDATION.parent/'spotlight103';sys.path.insert(0,str(MODULE))
from Runtime import *
BASE=Path('/mnt/d/projects/JLinAlg/build/literature-review/monocle-runtime')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute()
 out=check_output(out,[ROOT/'tools',ROOT/'docs',BASE]);out.mkdir(parents=True,exist_ok=False)
 roots={
  'paired-current':Path('/opt/omicsmatic-spotlight-paired-controls-20261115-3'),
  'public-guards':Path('/opt/omicsmatic-spotlight-public-guards-20261115-1'),
  'installed-offline-analysis':Path('/opt/omicsmatic-spotlight-installed-offline-analysis-20261115-1'),
  'installed-online-analysis':Path('/opt/omicsmatic-spotlight-installed-online-analysis-20261115-1'),
  'windows-guards':BASE/'spotlight103-windows-guards-1',
  'historical-fixture-preparation-failure':Path('/opt/omicsmatic-spotlight-paired-controls-20261115-1'),
  'historical-exact-cpptrace-comparison-failure':Path('/opt/omicsmatic-spotlight-paired-controls-20261115-2'),
  'historical-core-source':BASE/'spotlight103-source-before-prominent-original-errors-1',
  'historical-validation-source':BASE/'spotlight103-validation-before-cpptrace-contract-1',
  'source-intake':BASE/'spotlight103-source-intake-1'}
 current=load(roots['paired-current']/'COMPLETED.json')
 if (current['controls'],current['complete_returns'],current['original_failures_without_result'])!=(29,20,9):raise ValueError('Every intended positive and original error must be checked')
 guards=load(roots['public-guards']/'COMPLETED.json')
 if len(guards['guards'])!=11 or not all(g['passed'] for g in guards['guards']):raise ValueError('All eleven actual public guards required')
 if len(load(roots['windows-guards']/'COMPLETED.json')['guards'])!=2:raise ValueError('Both Windows prerequisite skips required')
 for key in ['installed-offline-analysis','installed-online-analysis']:
  if load(roots[key]/'COMPLETED.json')['status']!='PASS_ACTUAL_INSTALLED_SPOTLIGHT103_REFERENCE_AND_COMPLETE_INDEPENDENT_ORIGINAL':raise ValueError('Installed analysis incomplete')
 for name,identity in load(roots['paired-current']/'SOURCES.json').items():
  if pin(ROOT/name)!=identity:raise ValueError('Actually compared source changed: '+name)
 bundles=out/'bundles';bundles.mkdir();index={};setup_files={}
 def bundle(label,files):
  rows={};target=bundles/(label+'.zip')
  with zipfile.ZipFile(target,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,path in sorted(files.items()):
    raw=path.read_bytes();identity=pin(path)
    if len(raw)!=identity['bytes']:raise ValueError('Concurrent record change')
    z.writestr(name,raw);rows[name]=identity
  index[label]=dict(archive='bundles/'+target.name,archive_identity=pin(target),files=rows)
 for label,path in roots.items():
  listing=inventory(path)
  if any(r['kind']!='file' for r in listing.values()):raise ValueError('Synthetic record bundle requires regular self-contained files: '+label)
  bundle(label,{name:path/name for name in listing})
 for mode,number in [('offline',2),('online',1)]:
  workspace=Path('/opt/omicsmatic-spotlight-public-'+mode+'-install-20261115-'+str(number));provider=workspace.parent/(workspace.name+'-provider')
  if load(workspace/'COMPLETED.json')['status']!='PASS_INSTALLED_ORIGINAL_SPOTLIGHT103_NOT_REFERENCE_ANALYSIS':raise ValueError('Actual public installer did not complete')
  setup_files.update({mode+'/'+p.name:p for p in workspace.iterdir() if p.is_file()})
  setup_files.update({mode+'-provider/'+name:provider/name for name in inventory(provider)})
  ready=load(provider/'READY.json');check(ready)
  for name,identity in load(roots['installed-'+mode+'-analysis']/'SOURCES.json').items():
   if pin(ROOT/name)!=identity:raise ValueError('Actually installed analysis source differs: '+name)
 # The first completed installer/provider predates a recording-only source edit;
 # preserve it with that exact historical source, never refresh its old hashes.
 old=Path('/opt/omicsmatic-spotlight-public-offline-install-20261115-1');provider=old.parent/(old.name+'-provider')
 setup_files['historical-fixture-MakeRequests.R']=BASE/'spotlight103-MakeRequests-before-list-replacement-1.R'
 setup_files.update({'historical-offline/'+p.name:p for p in old.iterdir() if p.is_file()})
 setup_files.update({'historical-offline-provider/'+n:provider/n for n in inventory(provider)})
 bundle('setup-receipts-and-full-runtime-inventories',setup_files)
 save(out/'BUNDLE-INDEX.json',dict(status='COMPLETE_ORIGINAL_SYNTHETIC_RECORDS_AND_HISTORICAL_FAILURES_RETAINED',bundles=index,
  full_original_returns_conditions_rng_and_native_backtraces_retained=True,runtime_binaries_redistributed=False,biological_reference_data_included=False))
 receipts=out/'receipts';receipts.mkdir()
 selected={'PAIRED-CONTROLS.json':roots['paired-current']/'COMPLETED.json','COMPARISONS.json':roots['paired-current']/'COMPARISONS.json',
  'CONTROL-SOURCES.json':roots['paired-current']/'SOURCES.json','PUBLIC-GUARDS.json':roots['public-guards']/'COMPLETED.json','WINDOWS-GUARDS.json':roots['windows-guards']/'COMPLETED.json'}
 for mode,number in [('offline',2),('online',1)]:
  selected[mode.upper()+'-INSTALLATION.json']=Path('/opt/omicsmatic-spotlight-public-'+mode+'-install-20261115-'+str(number))/'COMPLETED.json'
  selected[mode.upper()+'-INSTALLED-ANALYSIS.json']=roots['installed-'+mode+'-analysis']/'COMPLETED.json'
  selected[mode.upper()+'-INSTALLED-COMPARISON.json']=roots['installed-'+mode+'-analysis']/'COMPARISON.json'
 for name,path in selected.items():shutil.copyfile(path,receipts/name)
 source=BASE/'spotlight103-source-cache-1/SPOTlight-git-1.0.3.tar.gz';shutil.copyfile(source,out/source.name)
 (out/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
 files=[p for folder in [MODULE,VALIDATION] for p in folder.rglob('*') if p.is_file()]+[UTILITY,NAMESPACE_AUDIT,ROOT/'docs/literature/SPOTLIGHT103_DECONVOLUTION_WORKFLOWS.md']
 source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
 archive=profile()['packages']['GenomeInfoDbData'];reused=ROOT/'docs/literature/celldex114-reference-validation/original-software-sources'/archive['archive_filename']
 if pin(reused)!=archive['archive']:raise ValueError('Preserved dependency source archive differs')
 evidence={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}
 proof=dict(status='PASS_ORIGINAL_SPOTLIGHT103_FULL_DECONVOLUTION_SOURCE_RECORDING_AND_PUBLIC_EXECUTION',
  original_release='1.0.3',original_commit=profile()['packages']['SPOTlight']['commit'],original_controls=29,complete_original_returns=20,original_failures_without_result=9,
  entire_original_return_objects_and_rng_identical=True,all_condition_fields_calls_and_original_native_origin_compared=True,
  full_process_specific_cpp_backtraces_retained_but_not_required_equal=True,
  actual_public_Linux_guards=11,actual_Windows_prerequisite_guards=2,both_public_installer_branches_and_complete_installed_analyses_passed=True,
  original_unapplied_model_minprop_and_extra_argument_confirmed=True,original_zero_spot_NaN_retained=True,
  original_algorithms_or_warning_policy_patched=False,source_files=source_pins,evidence_files=evidence,
  reused_original_source_archives={reused.relative_to(ROOT).as_posix():pin(reused)},
  full_synthetic_record_file_entries=sum(len(b['files']) for b in index.values()),
  original_NMF_convergence_certified=False,author_exact_commit_inputs_options_seed_environment_established=False,
  whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
 save(out/'QUALIFICATION.json',proof);print(proof['status'],proof['full_synthetic_record_file_entries'],len(source_pins),flush=True)
if __name__=='__main__':main()
