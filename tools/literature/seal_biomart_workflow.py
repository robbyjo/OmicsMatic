# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal retained full original comparisons and the final diagnostic-only refinement, without rewriting prior scientific proofs."""
import json,re,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack

def main():
    base=ROOT/'build/literature-review/biomart-runtime';folder=ROOT/'docs/literature/biomart-workflow-development';assert not folder.exists();build=json.loads((base/'workflow-build-6/COMPLETED.json').read_bytes());old=json.loads((base/'workflow-build-5/COMPLETED.json').read_bytes());matrix=json.loads((base/'workflow-validation-5/COMPLETED.json').read_bytes());extra=json.loads((base/'workflow-additional-2/COMPLETED.json').read_bytes());native=json.loads((base/'query-validation-4/COMPLETED.json').read_bytes());assert matrix['status']=='passed-complete-biomaRt-workflow-controls-and-independent-original-comparisons' and extra['status']=='passed-additional-biomaRt-runtime-preservation-capacity-and-Java25-original-comparisons' and native['status']=='passed-native-biomaRt-query-planning-original-public-operation-comparisons';assert matrix['artifact']==old['artifact'] and extra['artifact']==native['artifact']==build['artifact'];assert pin(ROOT/build['artifact']['path'])=={k:build['artifact'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(ROOT/old['artifact']['path']) as a,zipfile.ZipFile(ROOT/build['artifact']['path']) as b:
        assert a.namelist()==b.namelist();changed=[n for n in a.namelist() if a.read(n)!=b.read(n)];assert changed==['org/jlinalg/cli/BiomartRuntime.class']
    paths=[ROOT/'src/main/java/org/jlinalg/genetics/BioMartQueries.java',*sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Biomart*.java')),*sorted((ROOT/'tools/literature').glob('*biomart*.*')),ROOT/'tools/literature/BioMartQueryMatrix.java',ROOT/'docs/literature/BIOMART_WORKFLOWS.md'];paths+=[p for name in ['biomart-queries','biomart-workflow'] for p in (ROOT/'src/main/resources/org/jlinalg/omics'/name).rglob('*') if p.is_file()];paths += [ROOT/'tools/literature'/n for n in ['setup_deseq2_reference.py','signac_scan_evidence.py','validate_mashr_native.py','setup_chipseeker_reference.py']];paths=sorted(set(p for p in paths if p.is_file()));pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths};assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in extra['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in native['source_pins'].items());files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    folders=['query-reference-1','query-reference-2','query-validation-1','query-validation-2','query-validation-3','query-validation-4','workflow-build-1','workflow-build-2','workflow-build-3','workflow-build-4','workflow-build-5','workflow-build-6','workflow-example-1','workflow-validation-1','workflow-validation-2','workflow-validation-3','workflow-validation-4','workflow-validation-5','workflow-additional-1','workflow-additional-2','diagnostic-evolution-1','console-domain-probe-1','service-availability-1','live-reference-1','exact-endpoint-reference-1','exact-endpoint-reference-2','exact-endpoint-reference-3','exact-endpoint-reference-4']
    for name in folders:
        directory=base/name
        for path in directory.rglob('*'):
            if path.is_file() and path.suffix not in ['.jar','.class'] and not any(part in ['classes','driver-classes','__pycache__','private-cache','private-temp'] for part in path.relative_to(directory).parts):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['source-intake-1','source-intake-2','runtime-profile-268-1','runtime-profile-260-1','exported-runtime-268-1']:
        directory=base/name
        for filename in ['COMPLETED.json','runtime.json','WARNINGS.md','SOURCE-CLASSIFICATION-NOTE.json']:
            path=directory/filename
            if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    directory=base/'reference-setup-1'
    for path in directory.rglob('*'):
        if path.is_file() and not any(part in ['R-lib','private-temp','private-cache','__pycache__'] for part in path.relative_to(directory).parts):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    independent=0;rows=0
    for name in ['workflow-validation-5','workflow-additional-2']:
        for path in (base/name).glob('*/independent-reference/COMPLETED.json'):
            report=json.loads(path.read_bytes());assert report['status']=='PASS_COMPLETE_INDEPENDENT_ORIGINAL_DATAFRAMES_AND_TYPED_STATES';independent+=len(report['cases']);rows+=sum(c['rows'] for c in report['cases'])
    assert independent==51
    credential_files=[n for n,raw in files.items() if '/http/' in n and re.search(rb'(?im)^(?:proxy-)?authorization\s*:',raw)];assert not credential_files,('Do not publish credential-bearing traces; preserve originals privately',credential_files)
    parent_paths=['docs/literature/loftee-workflow-development/VALIDATION.json','docs/literature/loftee-workflow-development/controls.zip'];parents={n:pin(ROOT/n) for n in parent_paths};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];held_names=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-07-loftee-workflow-1/selection.json').read_bytes())['preserved'];held={n:pin(ROOT/n) for n in held_names};assert len(held)==18;files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();folder.mkdir();layout=pack(folder/'controls.zip',files);assert (folder/'controls.zip').stat().st_size<100000000;proof=dict(schema_version=1,status='passed-scoped-original-biomaRt-workflow-with-native-query-planning',artifact=build['artifact'],original_full_matrix_artifact=old['artifact'],changed_final_JAR_entries=changed,source_pins=pins,parent_proofs=parents,canonical_executable_unchanged=canonical,archive=pin(folder/'controls.zip'),adapter_admitted=True,implementation_credit=True,admitted_named_tools=['biomart'],native_only_named_tool_credit_added=0,complete_independent_original_dataframes=independent,complete_compared_original_rows=rows,full_matrix_protocol_controls=13,original_live_endpoint_edition_profiles=6,final_candidate_additional_checks=9,native_JVMs=3,complete_query_batches_per_JVM=76,matching_original_errors_per_JVM=10,original_getBM_table_conversion_retained=True,original_R_worker_and_query_algorithms_unchanged_in_final_message_refinement=True,unrelated_files_preserved=18,original_execution_platform='Windows-x86_64-R4.6.1',native_planner_platforms=['Windows-Java21','Windows-Java25','Linux-Java21'],historical_R44_image_reproduced=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout);save(folder/'VALIDATION.json',proof);assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    from biomart_workflow_evidence import verify
    verify();print('Sealed biomaRt:',independent,'complete original dataframes,',rows,'complete source rows; final diagnostics and all18 unrelated files preserved')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete scientific/source admission checks')
    main()
