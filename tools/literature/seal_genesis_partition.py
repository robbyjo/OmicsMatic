# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the native GENESIS matrix component, complete source controls and retained failures; no tool credit."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source
from verify_genesis_partition_evidence import HOME,STATUS,inspect
BASE=ROOT/'build/literature-review/genesis-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    assert HOME.is_dir() and {p.name for p in HOME.iterdir()}=={'README.md'}
    validated=json.loads((BASE/'native-partition-validation-1/COMPLETED.json').read_bytes());assert validated['status']=='PASS300_NATIVE_GENESIS_CASE_DISPOSITIONS_2048_R64_SUMS_AND30_API_TEST_EXECUTIONS'
    protocol=json.loads((BASE/'partition-reference-protocol-1/COMPLETED.json').read_bytes());assert protocol['fresh_complete_original_files_equal']==528 and len(protocol['records'])==3
    reference=json.loads((BASE/'original-partition-controls-6/COMPLETED.json').read_bytes());assert reference['status']=='PASS_ORIGINAL_GENESIS242_PARTITION_AND512_R64_REFERENCE_CONTROLS'
    # Append-only Git transport metadata. No scientific source can use this
    # inverse; older byte-preservation declarations reconstruct exactly.
    attrs=ROOT/'.gitattributes';before=attrs.read_bytes();addition=b'\n# Frozen native GENESIS partition evidence and owned binary base controls.\ndocs/literature/genesis-native-development/** -text whitespace=cr-at-eol\nsrc/test/resources/org/jlinalg/genetics/genesis-partition/** -text whitespace=cr-at-eol\n'
    assert b'docs/literature/genesis-native-development/**' not in before
    after=before+addition;row=inverse(before,after);row['path']='.gitattributes';assert reverse_delta(after,row)==before
    data=ROOT/'docs/literature/omics-2023-2026';descriptor=data/'genesis-native-sourcebytes-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='genesis',
        scope='Append-only byte preservation for the new native component evidence and owned binary fixture. Exact preceding Git metadata reconstructs. No algorithm, scientific source or source-allowlist exception.',paths=[row]))
    (out/'gitattributes.before').write_bytes(before);attrs.write_bytes(after);verify_source(ROOT,data,'.gitattributes',row['before_sha256'])
    source_pins=dict(validated['source_pins'])
    sources=['tools/literature/validate_genesis_partition_native.py','tools/literature/collect_genesis_partition_reference.py','tools/literature/verify_genesis_partition_evidence.py',
        'tools/literature/seal_genesis_partition.py','src/main/resources/org/jlinalg/omics/genesis/partition_reference.R',
        'src/test/resources/org/jlinalg/genetics/genesis-partition/declared-own-cases.rds','docs/literature/genesis-native-development/README.md','.gitattributes']
    source_pins.update({n:pin(ROOT/n) for n in sources})
    assert all(pin(ROOT/n)==v for n,v in source_pins.items())
    files={};objects={}
    def add(name,path):
        assert name not in files and not path.is_symlink();raw=path.read_bytes();files[name]=objects.setdefault(byte_pin(raw)['sha256'],raw)
    for n in source_pins:add('current-source/'+n,ROOT/n)
    folders=['original-partition-controls-1','original-partition-controls-2','original-partition-controls-3','original-partition-controls-4','original-partition-controls-5','original-partition-controls-6',
        'native-partition-build21-1','native-partition-build21-2','native-partition-build21-3','native-partition-probe21-1',
        'native-partition-api21-1','native-partition-api25-1','native-partition-validation-1','partition-reference-protocol-1','R461-sum-source-intake-1']
    for name in folders:
        assert (BASE/name).is_dir(),name
        for path in sorted((BASE/name).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                assert path.suffix.lower() not in ['.exe','.dll','.so','.tar','.gz','.zip','.xz'],('No original runtime/archive distribution',str(path))
                add(path.relative_to(ROOT).as_posix(),path)
    for name in ['collect_partition_controls_1.py','collect_partition_controls_2.py','retry_partition_controls_2.py','export_partition_controls_3.py','export_partition_controls_4.py',
        'retry_export_partition_4.py','GenesisPartitionProbe.java','compare_partition_probe_1.py','prepare_public_partition_probe_1.py','prepare_partition_reference_resources_1.py','check_partition_reference_protocol_1.py']:
        add((BASE/name).relative_to(ROOT).as_posix(),BASE/name)
    upstream=BASE/'official-intake-1/original-source/GENESIS'
    for name in ['DESCRIPTION','R/pcairPartition.R','R/util_methods.R']:
        add('upstream-source/GENESIS-2.42.0/'+name,upstream/name)
    add('upstream-source/GPL-3.0.txt',ROOT/'LICENSE')
    artifacts={}
    for compiler in [21,25]:
        build=json.loads((BASE/f'native-partition-api{compiler}-1/COMPLETED.json').read_bytes())
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        for kind in ['api','consumer']:
            name=f'genesis-partition-{kind}-javac{compiler}.jar';source=BASE/f'native-partition-api{compiler}-1'/f'genesis-partition-{kind}.jar'
            shutil.copyfile(source,HOME/name);artifacts[name]=pin(HOME/name)
    packed=pack(out/'controls.zip',files);assert pin(out/'controls.zip')['bytes']<95000000;shutil.copyfile(out/'controls.zip',HOME/'controls.zip')
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-10-snprelate-tools-1/selection.json').read_bytes())
    assert all(pin(ROOT/n)==v for n,v in previous['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=False,GENESIS_tool_still_missing=True,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        native_component_available=True,root_command_available=False,project_workflow_admitted=False,source_pins=source_pins,archive=pin(HOME/'controls.zip'),artifacts=artifacts,
        original_case_dispositions=75,original_source_successes=69,original_source_failures=6,successful_native_profile_partitions=67,unsupported_nonfinite_cases=2,
        full_reference_profiles=4,complete_native_partitions=268,reproduced_original_failures=24,unsupported_nonfinite_refusals=8,bit_exact_R_scalar_sums=2048,JUnit_test_executions=30,
        R_longdouble_significand_bits=64,fresh_public_worker_original_files_equal=528,actual_reference_skip_preservation_contracts=3,
        runtime_binaries_or_package_archives_distributed=False,primary_article_prose_or_participant_data_distributed=False,
        canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,
        retained_failures='Initial strict Java compilation refused a List-typed serialized exception field; a serializable defensive ArrayList preserved warnings. Initial reference serialization mishandled empty sets, then missing R vector names from the original NaN-input result; only the derived serializer was corrected, with original inputs/results retained. The initial reference-harness SKIP3 label was explicitly corrected to FAIL2 in its retained warning sidecar.',
        scope='Native GENESIS2.42.0 finite/symmetric/unique-ID matrix pcairPartition profile, explicit cutoffs, exact original ordered sets and warnings, source matrix-host refusals and source bookkeeping/simplification findings. Native R4.6.1 Windows64-significand accumulation emulation is separately controlled. No PCA/projection, PC-Relate, model fitting, historical study reproduction, generic independent-set replacement, producer authentication, hard OS quota or production-scale claim.',**packed)
    save(HOME/'VALIDATION.json',proof);inspect(HOME)
    old=(data/'SHA256SUMS').read_text(encoding='utf8').splitlines();order=[r.split('  ',1)[1] for r in old]
    manifest={p.name:pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    order=[n for n in order if n in manifest]+[n for n in manifest if n not in order]
    (data/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order),encoding='utf8',newline='\n')
    from verify_snprelate_catalog import verify as snprelate_verify
    snprelate_verify()
    save(out/'COMPLETED.json',dict(status=STATUS,proof=pin(HOME/'VALIDATION.json'),archive=proof['archive'],new_catalog_credit=False,whole_goal_complete=False))
    print(STATUS,proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full source/evidence, Git-byte inverse or unrelated/canonical preservation checks')
    main()
