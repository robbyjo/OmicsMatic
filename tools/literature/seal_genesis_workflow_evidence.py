# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze standalone GENESIS workflow development controls and failures, retaining zero catalog credit."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source
from verify_genesis_workflow_evidence import HOME,STATUS,inspect
BASE=ROOT/'build/literature-review/genesis-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    assert {p.name for p in HOME.iterdir()}=={'README.md','P128-supplement-source-review.json'}
    def record(name):return json.loads((BASE/name/'COMPLETED.json').read_bytes())
    assert record('adapter-pilot-2')['status']=='PASS_SIX_GENESIS_ADAPTER_PILOTS_ONLY'
    assert record('adapter-protocol-4')['status']=='PASS_GENESIS_SAVED_CONTROLS_AND_RESEALED_SCIENTIFIC_REFUSALS'
    assert record('request-contracts-4')['status']=='PASS30_GENESIS_REQUEST_CONTRACT_EXECUTIONS'
    assert record('full-original-comparison-2')['status']=='PASS_COMPLETE_RETAINED_ORIGINAL_GENESIS_WORKFLOW_FIELD_COMPARISONS'
    assert record('adapter-failure-contracts-1')['status']=='PASS_THREE_ACTUAL_GENESIS_REFUSAL_CONTRACTS'
    attrs=ROOT/'.gitattributes';before=attrs.read_bytes()
    py=['tools/literature/'+n for n in ['build_genesis_tools.py','compare_genesis_workflow_controls.py','test_genesis_request_contracts.py','test_genesis_tools_protocol.py','verify_genesis_workflow_evidence.py','seal_genesis_workflow_evidence.py']]
    addition=('\n# Preserve frozen GENESIS workflow development evidence and verifier source bytes.\ndocs/literature/genesis-workflow-development/** -text whitespace=cr-at-eol\nsrc/main/resources/org/jlinalg/omics/genesis/prepare_genesis_runtime.py -text whitespace=cr-at-eol\n'+''.join(n+' -text whitespace=cr-at-eol\n' for n in py)).encode('utf8')
    assert b'docs/literature/genesis-workflow-development/**' not in before;after=before+addition;row=inverse(before,after);row['path']='.gitattributes';assert reverse_delta(after,row)==before
    data=ROOT/'docs/literature/omics-2023-2026';descriptor=data/'genesis-workflow-sourcebytes-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='genesis',scope='Append-only exact Git byte-preservation metadata for development evidence and Python sources. No scientific source change or allowlist exception.',paths=[row]))
    (out/'gitattributes.before').write_bytes(before);attrs.write_bytes(after);verify_source(ROOT,data,'.gitattributes',row['before_sha256'])
    build=record('adapter-build21-3');source_pins=dict(build['source_pins'])
    names=[*py,'src/main/resources/org/jlinalg/omics/genesis/compare_workflow_reference.R','src/test/java/org/jlinalg/cli/GenesisRequestTest.java',
        'docs/literature/GENESIS_WORKFLOW_DEVELOPMENT.md','docs/literature/genesis-workflow-development/README.md','docs/literature/genesis-workflow-development/P128-supplement-source-review.json','.gitattributes']
    names.append(descriptor.relative_to(ROOT).as_posix());source_pins.update({n:pin(ROOT/n) for n in names});assert all(pin(ROOT/n)==v for n,v in source_pins.items())
    files={};objects={}
    def add(name,path):
        assert name not in files and path.is_file() and not path.is_symlink();raw=path.read_bytes();files[name]=objects.setdefault(byte_pin(raw)['sha256'],raw)
    for name in source_pins:add('current-source/'+name,ROOT/name)
    folders=['workflow-interface-intake-1','workflow-own-controls-1','workflow-runtime-profile-1',
        'original-workflow-pilot-1','original-workflow-pilot-2','pilot-field-export-1','worker-pilot-1','worker-pilot-2','worker-pilot-3',
        'adapter-build21-1','adapter-build21-2','adapter-build25-2','adapter-build21-3','adapter-build25-3','adapter-pilot-1','adapter-pilot-2',
        'request-contracts-1','request-contracts-2','request-contracts-3','request-contracts-4','adapter-protocol-3','adapter-protocol-4',
        'full-original-comparison-1','full-original-comparison-2','adapter-failure-contracts-1']
    for name in folders:
        assert (BASE/name).is_dir(),name
        for path in sorted((BASE/name).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                assert path.suffix.lower() not in ['.exe','.dll','.so','.tar','.gz','.xz','.pdf'],('No upstream binary/archive/article distribution',str(path))
                add(path.relative_to(ROOT).as_posix(),path)
    for name in ['create_workflow_controls_1.py','pilot_original_workflows_1.py','pilot_original_workflows_2.py','test_worker_pilot_1.py','test_worker_pilot_2.py','test_worker_pilot_3.py','test_adapter_pilot_1.py','test_adapter_pilot_2.py','test_adapter_failure_contracts_1.py']:
        if (BASE/name).is_file():add((BASE/name).relative_to(ROOT).as_posix(),BASE/name)
    for name in ['official-intake-1','private-original-setup-1']:add('runtime-intake-receipts/'+name+'.json',BASE/name/'COMPLETED.json')
    original=BASE/'official-intake-1/original-source/GENESIS'
    for folder in ['R','src']:
        for path in sorted((original/folder).rglob('*')):
            if path.is_file():add('upstream-source/GENESIS-2.42.0/'+path.relative_to(original).as_posix(),path)
    for name in ['DESCRIPTION','NAMESPACE']:add('upstream-source/GENESIS-2.42.0/'+name,original/name)
    add('upstream-source/GPL-3.0.txt',ROOT/'LICENSE')
    artifacts={}
    for compiler in [21,25]:
        metadata=record('adapter-build'+str(compiler)+'-3');assert all(pin(ROOT/n)==v for n,v in metadata['source_pins'].items())
        source=BASE/('adapter-build'+str(compiler)+'-3/genesis-tools-development.jar');name='genesis-tools-javac'+str(compiler)+'.jar';shutil.copyfile(source,HOME/name);artifacts[name]=pin(HOME/name)
    packed=pack(out/'controls.zip',files);assert pin(out/'controls.zip')['bytes']<95000000;shutil.copyfile(out/'controls.zip',HOME/'controls.zip')
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-10-genesis-native-1/selection.json').read_bytes())
    assert len(previous['preserved'])==18 and all(pin(ROOT/n)==v for n,v in previous['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=False,GENESIS_tool_still_missing=True,whole_goal_complete=False,whole_package_implemented=False,whole_paper_reproduced=False,
        root_command_available=False,development_workflow_adapter_available=True,source_pins=source_pins,archive=pin(HOME/'controls.zip'),artifacts=artifacts,
        original_workflow_successes=6,original_source_scientific_comparisons=92,original_source_scientific_cells=49218,saved_native_verifications=18,resealed_scientific_refusals=75,JUnit_executions=30,
        actual_refusal_contracts=3,original_runtime_platform='Windows x86_64 R4.6.1 GENESIS2.42.0',saved_verification_platforms=['Windows Java21','Windows Java25','Linux Java21'],
        source_methods_paper_supplement_reviewed_but_catalog_unchanged='P128 Supplementary Note7: explicit GENESIS2.16.1 pcairPartition cutoff0.044; later function settings still unstated.',
        upstream_runtime_binaries_or_package_archives_distributed=False,participant_data_or_article_prose_distributed=False,canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,
        scope='Development only: six explicit original workflows, complete source-field comparisons and stated native admission/replay contracts. Native saved verification does not independently recompute projected-relative PC loadings, kinship/IBD estimators, model fits or P values. Other option branches, examples, root dispatcher and catalog admission remain outstanding. No software authenticity, historical-paper reproduction, biological validity, hard OS quota or production-scale claim.',**packed)
    save(HOME/'VALIDATION.json',proof);inspect()
    old=(data/'SHA256SUMS').read_text(encoding='utf8').splitlines();order=[r.split('  ',1)[1] for r in old];manifest={p.name:pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    order=[n for n in order if n in manifest]+[n for n in manifest if n not in order];(data/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order),encoding='utf8',newline='\n')
    from verify_snprelate_catalog import verify as verify_catalog
    from verify_genesis_partition_evidence import inspect as inspect_native
    verify_catalog();inspect_native()
    save(out/'COMPLETED.json',dict(status=STATUS,proof=pin(HOME/'VALIDATION.json'),archive=proof['archive'],new_catalog_credit=False,whole_goal_complete=False));print(STATUS,proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen source/catalog/canonical or held-edit preservation')
    main()
