#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check retained public-summary evidence, live code and unchanged scientific baselines."""
import hashlib,json,stat,sys,zipfile
from pathlib import Path,PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin
ROOT=Path(__file__).resolve().parents[2]


def verify():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    folder=ROOT/'docs/literature/mashr-summary';proof=json.loads((folder/'VALIDATION.json').read_text(encoding='utf-8'))
    assert proof['schema_version']==1 and proof['status']=='passed-public-native-mash-summary-command-development-checkpoint'
    assert proof['tests_per_JVM']==396 and proof['new_command_tests_per_JVM']==25 and proof['real_commands']==39 and proof['cross_produced_summary_bundles']==6
    assert set(proof['platforms'])=={'windows-java21','windows-java25','linux-java21'} and proof['heap_mib']==128
    for name in ['source_pins','protected_prior_source_pins','baseline_dependencies']:assert all(pin(ROOT/n)==v for n,v in proof[name].items()),name
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'] and pin(ROOT/'docs/literature/omics-2023-2026/summary.json')==proof['catalog_summary_unchanged']
    assert proof['claims']==dict(standalone_public_summary_command_available=True,complete_native_source_replayed=True,source_posterior_tables_captured=True,nominal_P_values_inferred=False,joint_probability_inferred=False,root_dispatcher_changed=False,study_route_available=False,paper_role_coverage_complete=False,catalog_promoted=False,whole_paper_reproduced=False,biological_accuracy_certified=False,released_or_deployed=False)
    assert proof['archive']['path']=='controls.zip' and pin(folder/'controls.zip')=={k:proof['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(folder/'controls.zip') as archive:
        entries=archive.infolist();assert len(entries)==len({e.filename for e in entries})==len(proof['archive_files'])==proof['archive']['entries'] and {e.filename for e in entries}==set(proof['archive_files'])
        total=0
        for entry in entries:
            path=PurePosixPath(entry.filename);assert not entry.is_dir() and not path.is_absolute() and '..' not in path.parts and '\\' not in entry.filename and stat.S_IFMT(entry.external_attr>>16) in [0,stat.S_IFREG]
            expected=proof['archive_files'][entry.filename];assert entry.file_size==expected['bytes'] and entry.file_size<=33_554_432;digest=hashlib.sha256();size=0
            with archive.open(entry) as stream:
                while block:=stream.read(1_048_576):size+=len(block);digest.update(block)
            total+=size;assert total<=1_073_741_824 and size==expected['bytes'] and digest.hexdigest()==expected['sha256'],entry.filename
        for n,v in proof['source_pins'].items():assert proof['archive_files']['source/'+n]==v
        for name in ['matrix-2','commands-2']:
            record=json.loads(archive.read('evidence/'+name+'/COMPLETED.json'));prefix='evidence/'+name+'/'
            actual={n[len(prefix):]:v for n,v in proof['archive_files'].items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'};assert actual==record['files']
            if name=='matrix-2':assert record['tests_per_JVM']==396 and len(record['runs'])==3 and all(r['process']['exit_code']==0 for r in record['runs']) and record['owned_classes']==proof['owned_classes']
            else:
                assert sum(len(r['commands']) for r in record['runs'])==39 and len(record['cross_produced_bundles'])==6 and all(r['exit_code']==0 and r['source_unchanged'] is True for r in record['cross_produced_bundles'])
                assert all(c['exit_code']==c['expected_exit_code'] for r in record['runs'] for c in r['commands']) and record['nominal_P_controls_synthetic'] is True and record['prepared_source_files_unchanged'] is True
        marker=json.loads(archive.read('evidence/repository-example-result/COMPLETED.json'));assert marker['tool']=='mash-summary' and marker['scoped_result_admitted'] is True and marker['joint_probability_inferred'] is False
        assert 'verified by native-source and complete summary replay' in archive.read('logs/repository-example-verify-1.log').decode('utf-8')
    print(json.dumps(dict(status='passed-retained-public-summary-checkpoint-integrity',tests_per_JVM=396,platforms=proof['platforms'],real_commands=39,cross_produced_summary_bundles=6,source_files=len(proof['source_pins']),protected_source_files=len(proof['protected_prior_source_pins']),archive_files=len(entries),archive_uncompressed_bytes=total,analysis_reexecuted=False,catalog_promoted=False),indent=2))


if __name__=='__main__':
    try:verify()
    except Exception as failure:print('FAIL2_SUMMARY_CHECKPOINT: '+str(failure)+'\nRequired action: retain evidence, correct the named source/archive mismatch and revalidate; do not promote incomplete roles.',file=sys.stderr);sys.exit(2)
