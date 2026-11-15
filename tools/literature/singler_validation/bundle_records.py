#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete SingleR scientific records, sources and retained failed attempts."""
from pathlib import Path
import argparse,hashlib,importlib.util,struct,sys
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];BASE=ROOT/'build/literature-review/monocle-runtime'
sys.path.insert(0,str(ROOT/'tools/literature/singler_workflows'))
from Runtime import load,pin,save
spec=importlib.util.spec_from_file_location('singler_bundle_evidence',ROOT/'tools/literature/scrattchbigcat005_workflows/Evidence.py')
evidence=importlib.util.module_from_spec(spec);spec.loader.exec_module(evidence)
VERSIONS=['1.2.4','1.8.1','2.2.0','2.6.0','2.8.0']

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
    writer=evidence.Writer(out/'complete-validation-records.zip');private=[];current={}
    def tree(label,path):
        for file in sorted(Path(path).rglob('*')):
            if file.is_symlink():raise ValueError('Unexpected evidence link; preserve and declare explicitly: '+str(file))
            if not file.is_file():continue
            relative=file.relative_to(path).as_posix()
            if file.name=='core' or file.name.startswith('core.') or file.suffix in ['.core','.dmp']:
                with file.open('rb') as stream:header=stream.read(20)
                elf=header[:4]==b'\x7fELF' and len(header)>=18 and int.from_bytes(header[16:18],'little' if header[5]==1 else 'big')==4
                if elf:
                    private.append(dict(path=str(file),reason='Complete native process memory dump retained privately; raw stderr and all available scientific records remain public',**pin(file)))
                    continue
            writer.add(label+'/'+relative,file)
    for version in VERSIONS:
        folder=BASE/('singler-controls-'+version+'-2');proof=load(folder/'COMPLETED.json')
        if proof['status']!='PASS_FRESH_COMPLETE_ORIGINAL_SINGLER_WORKER_CONTROLS_NOT_PUBLIC_RUNNER_GUARDS' or proof['controls']!=50:
            raise ValueError('Complete current 50-control qualification required: '+version)
        current[version]=proof;tree('current/controls-'+version,folder)
        tree('current/provider-'+version,BASE/('singler-provider-'+version+'-dev-3'))
        old=BASE/('singler-controls-'+version+'-1')
        if old.exists():tree('historical/first-controls-'+version,old)
        for suffix in ['dev-1','dev-2']:
            previous=BASE/('singler-provider-'+version+'-'+suffix)
            if previous.exists():tree('historical/provider-'+version+'-'+suffix,previous)
    folders={
      'public-runner':'singler-public-runner-guards-1','windows-prerequisites':'singler-windows-public-guards-1',
      'whole-object-guards':'singler-extra-guard-whole-objects-1','S4-delegate-guard':'singler-extra-guard-S4-delegate-1',
      'installed-analysis-offline124':'singler-installed-analysis-offline124-1','installed-analysis-download280':'singler-installed-analysis-download280-1'}
    for label,name in folders.items():
        if not (BASE/name/'COMPLETED.json').is_file():raise ValueError('Complete current public/installed/semantic record missing: '+name)
        tree('current/'+label,BASE/name)
    if len(load(BASE/'singler-public-runner-guards-1/COMPLETED.json')['guards'])!=17:raise ValueError('All 17 real Linux public guards are required')
    for label,name in [('retained-code-dev2','singler-retained-adapter-dev2-1'),('retained-code-before-S4','singler-retained-adapter-before-s4-audit-1'),
      ('native-dependency-diagnostic','singler124-native-dependency-diagnostic-1')]:tree('historical/'+label,BASE/name)
    for attempt in [1,2]:
        replay_failure=BASE/('singler-frozen-replay-'+str(attempt))
        if (replay_failure/'FAILURE.json').exists():
            label='historical/frozen-replay-'+str(attempt)
            for name in ['FAILURE.json','WARNINGS.md']:
                writer.add(label+'/'+name,replay_failure/name)
            tree(label+'/fresh',replay_failure/'fresh')
            tree(label+'/qualified-project-sources',replay_failure/'records/qualified-project-sources')
    for i in range(1,5):
        folder=BASE/('singler-s4-audit-development-'+str(i))
        if folder.exists():tree('historical/S4-audit-'+str(i),folder)
    # Only complete logs/receipts are selected from installation workspaces.
    # Installed binaries, libraries, caches and duplicated working sources stay private.
    install_paths=[Path('/opt')/('omicsmatic-singler-public-'+kind+'-20261115-1') for kind in ['offline124','download280']]
    workspaces=[Path('/opt/omicsmatic-singler-development-20261115-1')/v for v in VERSIONS]
    workspaces += [Path('/opt/omicsmatic-singler-development-20261115-2/2.6.0'),Path('/opt/omicsmatic-singler-development-20261115-3/1.2.4')]
    for folder in install_paths+workspaces:
        if not folder.exists():continue
        label=('installations/' if folder in install_paths else 'historical/workspaces/')+folder.parent.name+'/'+folder.name
        for file in sorted(folder.iterdir()):
            if file.is_file() and file.suffix not in ['.gz','.bz2','.so','.dll']:
                writer.add(label+'/'+file.name,file)
        completion=folder/'dependency-completion-2'
        if completion.is_dir():tree(label+'/dependency-completion-2',completion)
        if folder in install_paths:
            if load(folder/'COMPLETED.json')['status']!='PASS_INSTALLED_AND_AUDITED_ORIGINAL_SINGLER_PROFILE_NOT_ANALYSIS':
                raise ValueError('Actual public installation did not complete')
            tree('current/installed-provider-'+folder.name,folder.parent/(folder.name+'-provider'))
    # Original allowed source archives preserve upstream notices, licenses and
    # corresponding source alongside the serialized synthetic scientific records.
    cache=BASE/'singler-exact-source-cache-1';entries=load(cache/'SOURCE-CACHE.json')['archives']
    if len(entries)!=12:raise ValueError('All twelve unchanged selected original source archives required')
    for name,entry in entries.items():
        if pin(cache/name)!=entry['identity']:raise ValueError('Original permitted source archive differs: '+name)
        writer.add('original-source-archives/'+name,cache/name)
    for filename in ['singler-curated-reading-independent-check-1.json','singler-all250-input-regeneration-preflight-1.json']:
        writer.add('source-reading/'+filename,BASE/filename)
    for folder in ['singler-source-intake-1','singler-data-source-intake-1','singler-dependency-source-intake-1','singler-delayedmatrixstats-source-intake-1']:
        for file in sorted((BASE/folder).rglob('*.json')):
            if 'author-FDProject' in file.parts or file.name=='bioconda-package-metadata.json':continue
            writer.add('source-intake/'+folder+'/'+file.relative_to(BASE/folder).as_posix(),file)
    # Operational recovery and unrelated shared-work inventories are retained
    # locally. They contain no substituted or withheld original analysis result.
    recovery=BASE/'singler-wsl-recovery-1'
    for file in recovery.iterdir():
        if file.is_file():private.append(dict(path=str(file),reason='Task-owned operational process recovery receipt; not scientific data',**pin(file)))
    save(out/'PRIVATE-RECORDS.json',dict(status='DECLARED_COMPLETE_PRIVATE_OPERATIONAL_OR_CORE_RECORDS_RETAINED_UNCHANGED',files=private,
      original_analysis_consoles_or_available_scientific_objects_withheld=False,unrelated_shared_work_inventory_not_bundled=True,
      inspected_unlicensed_author_notebook_redistributed=False,installed_runtime_binaries_bundled=False))
    writer.add('PRIVATE-RECORDS.json',out/'PRIVATE-RECORDS.json')
    sources=[]
    for folder in ['tools/literature/singler_workflows','tools/literature/singler_validation','tools/literature/singler_source_audit','docs/literature/singler-workflow-development']:
        sources.extend(p for p in (ROOT/folder).rglob('*') if p.is_file())
    sources.append(ROOT/'tools/literature/scrattchbigcat005_workflows/Evidence.py')
    identities={p.relative_to(ROOT).as_posix():pin(p) for p in sorted(sources)}
    for name,identity in identities.items():
        writer.add('qualified-project-sources/'+name,ROOT/name)
        if {k:writer.files['qualified-project-sources/'+name][k] for k in ['bytes','sha256']}!=identity:raise ValueError('Source changed during capture')
    original_returns=sum(p['complete_returns'] for p in current.values());original_errors=sum(p['original_failures_without_result'] for p in current.values())
    if (original_returns,original_errors)!=(232,18):raise ValueError('Complete five-profile original outcome population differs')
    proof=dict(status='PASS_SCOPED_FIVE_ORIGINAL_SINGLER_RELEASES_AND_COMPLETE_RECORDING',source_files=identities,
      original_releases=VERSIONS,original_controls=250,controls_per_release=50,complete_original_returns=original_returns,
      original_failures_without_result=original_errors,complete_original_objects_conditions_rng_preserved=True,
      comparisons_use_whole_version3_serialization_without_environment_or_field_removal=True,
      serialized_training_external_pointers_are_reusable=False,live_native_index_internal_equivalence_claimed=False,
      public_Linux_runner_guards=17,actual_Windows_prerequisite_guards=3,
      both_actual_public_install_branches_passed=True,both_actual_installed_analyses_compared_whole=True,
      whole_object_unchanged_copies_accepted=2,whole_object_scientific_or_environment_tampers_rejected=6,
      original_S4_captured_delegate_swap_rejected_with_unchanged_method_code=True,ZIP_envelope_resealing_claimed=False,
      historical_failures_and_source_copies_retained=True,original_source_archives_bundled=12,installed_runtime_binaries_bundled=False,
      primary_papers_rescanned=250,curated_SingleR_papers=4,primary_SingleR_named_anchors=9,
      literal_paper_release_2_41_resolved=False,author_reference_objects_or_environment_inferred=False,unlicensed_author_notebook_redistributed=False,
      source_or_dependency_algorithms_patched=False,whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
    layout=writer.finish(proof)
    for name,identity in identities.items():
        if pin(ROOT/name)!=identity:raise ValueError('Qualified source changed during evidence freeze: '+name)
    proof.update(evidence_layout=layout,evidence_bundle=pin(out/'complete-validation-records.zip'),evidence_payloads={p.name:pin(p) for p in writer.part_paths})
    save(out/'QUALIFICATION.json',proof)
    print('FROZEN_SINGLER_NUMERIC_CONTROL_CANDIDATE_FRESH_REPLAY_STILL_REQUIRED',layout,flush=True)

if __name__=='__main__':main()
