#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check retained public proxy evidence and explicitly extended scientific source."""
import hashlib,json,stat,sys,zipfile
from pathlib import Path,PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin
from finalize_mashr_proxy_command import CLAIMS,CHANGED_SOURCE
ROOT=Path(__file__).resolve().parents[2]


def verify(emit=True):
    assert __debug__, 'Do not disable evidence assertions'
    folder=ROOT/'docs/literature/mashr-proxy-command';proof=json.loads((folder/'VALIDATION.json').read_text(encoding='utf-8'))
    assert proof['schema_version']==1 and proof['status']=='passed-public-native-proxy-summary-command-development-checkpoint'
    assert (proof['tests_per_JVM'],proof['new_command_tests_per_JVM'],proof['public_protocol_commands'],proof['cross_produced_bundle_checks'],proof['fresh_original_R_score_decisions'],proof['repository_example_commands'])==(477,37,30,6,360,4)
    assert set(proof['platforms'])=={'windows-java21','windows-java25','linux-java21'} and proof['heap_mib']==128 and proof['claims']==CLAIMS
    for key in ['source_pins','unchanged_prior_source_pins','baseline_dependencies']:
        for n,v in proof[key].items():assert pin(ROOT/n)==v,n
    change=proof['changed_prior_source'];assert change['path']==CHANGED_SOURCE and change['after']==proof['source_pins'][CHANGED_SOURCE] and change['before']!=change['after'] and CHANGED_SOURCE not in proof['unchanged_prior_source_pins']
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    assert proof['archive']['path']=='controls.zip' and pin(folder/'controls.zip')=={k:proof['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(folder/'controls.zip') as archive:
        entries=archive.infolist();assert len(entries)==len({e.filename for e in entries})==len(proof['archive_files'])==proof['archive']['entries'] and {e.filename for e in entries}==set(proof['archive_files']);total=0
        for entry in entries:
            p=PurePosixPath(entry.filename);assert not entry.is_dir() and not p.is_absolute() and '..' not in p.parts and '\\' not in entry.filename and stat.S_IFMT(entry.external_attr>>16) in [0,stat.S_IFREG]
            expected=proof['archive_files'][entry.filename];assert entry.file_size==expected['bytes'] and entry.file_size<=33_554_432;digest=hashlib.sha256();size=0
            with archive.open(entry) as stream:
                while block:=stream.read(1_048_576):size+=len(block);digest.update(block)
            total+=size;assert total<=1_073_741_824 and size==expected['bytes'] and digest.hexdigest()==expected['sha256'],entry.filename
        for n,v in proof['source_pins'].items():assert proof['archive_files']['source/'+n]==v,n
        assert proof['archive_files']['history/before-extension/MashSummaryCli.java']==change['before']
        catalog=proof['catalog_at_validation'];assert proof['archive_files'][catalog['archive_path']]=={k:catalog[k] for k in ['bytes','sha256']}
        for name in ['matrix-3','protocols-1']:
            prefix='evidence/'+name+'/';record=json.loads(archive.read(prefix+'COMPLETED.json'))
            assert {n[len(prefix):]:v for n,v in proof['archive_files'].items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'}==record['files']
            assert record['artifact']==proof['artifact']
            for n,v in record['source_pins'].items():assert proof['source_pins'].get(n,proof['unchanged_prior_source_pins'].get(n))==v,n
            if name=='matrix-3':
                assert record['tests_per_JVM']==477 and len(record['runs'])==3 and all(r['process']['exit_code']==0 for r in record['runs'])
                assert record['changed_prior_source']==change and record['owned_classes']==proof['owned_classes']
            else:
                assert all(c['exit_code']==c['expected_exit_code'] for r in record['runs'] for c in r['commands']) and sum(len(r['commands']) for r in record['runs'])==30
                assert len(record['cross_produced_bundles'])==6 and all(r['exit_code']==0 and r['source_unchanged'] for r in record['cross_produced_bundles'])
                assert sum(r['scores_and_decisions'] for r in record['original_R_runs'])==360 and all(r['process']['exit_code']==0 for r in record['original_R_runs'])
                assert record['original_source_function_unmodified'] and record['prior_v1_bundles_unchanged'] and record['prepared_source_files_unchanged'] and not record['full_dreamlet_package_validated']
        example=json.loads(archive.read('evidence/repository-example-1/COMPLETED.json'));assert len(example['commands'])==4 and all(c['exit_code']==0 for c in example['commands'])
        assert json.loads(archive.read('history/matrix-2/INTERRUPTED.json'))['windows_completion_unproven']
        assert json.loads(archive.read('history/matrix-1/FAILURE.json'))['status']=='FAIL2_PROXY_COMMAND_MATRIX'
    result=dict(status='passed-retained-public-proxy-checkpoint-integrity',tests_per_JVM=477,public_protocol_commands=30,cross_produced_bundle_checks=6,fresh_original_R_score_decisions=360,source_files=len(proof['source_pins']),unchanged_prior_source_files=len(proof['unchanged_prior_source_pins']),archive_files=len(entries),archive_uncompressed_bytes=total,analysis_reexecuted=False,catalog_promoted=False)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    try:verify()
    except Exception as failure:print('FAIL2_PROXY_CHECKPOINT: '+str(failure)+'\nRequired action: retain evidence, correct the named source/archive mismatch and revalidate before dependent analysis.',file=sys.stderr);sys.exit(2)
