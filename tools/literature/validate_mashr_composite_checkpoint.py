#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check retained composite evidence and its current scientific-source closure."""
import hashlib, json, stat, sys, zipfile
from pathlib import Path, PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin
from finalize_mashr_composite import CLAIMS
ROOT=Path(__file__).resolve().parents[2]


def verify():
    assert __debug__, 'Do not disable evidence assertions'
    folder=ROOT/'docs/literature/mashr-composite';proof=json.loads((folder/'VALIDATION.json').read_text())
    assert proof['schema_version']==1 and proof['status']=='passed-native-composite-proxy-library-development-checkpoint'
    assert (proof['tests_per_JVM'],proof['new_tests_per_JVM'],proof['new_analytic_tests'],proof['original_R_cases'],proof['heap_mib'])==(440,44,14,30,128)
    assert set(proof['platforms'])=={'windows-java21','windows-java25','linux-java21'} and proof['claims']==CLAIMS
    for key in ['source_pins','protected_prior_source_pins','baseline_dependencies']:
        for n,v in proof[key].items():assert pin(ROOT/n)==v,n
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'] and pin(ROOT/'docs/literature/omics-2023-2026/summary.json')==proof['catalog_summary_unchanged']
    assert proof['archive']['path']=='controls.zip' and pin(folder/'controls.zip')=={k:proof['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(folder/'controls.zip') as archive:
        entries=archive.infolist(); assert len(entries)==len({e.filename for e in entries})==len(proof['archive_files'])==proof['archive']['entries']
        assert {e.filename for e in entries}==set(proof['archive_files']); total=0
        for entry in entries:
            p=PurePosixPath(entry.filename);assert not entry.is_dir() and not p.is_absolute() and '..' not in p.parts and '\\' not in entry.filename and stat.S_IFMT(entry.external_attr>>16) in [0,stat.S_IFREG]
            expected=proof['archive_files'][entry.filename];assert entry.file_size==expected['bytes'] and entry.file_size<=33_554_432;digest=hashlib.sha256();size=0
            with archive.open(entry) as stream:
                while block:=stream.read(1_048_576):size+=len(block);digest.update(block)
            total+=size;assert total<=1_073_741_824 and size==expected['bytes'] and digest.hexdigest()==expected['sha256'],entry.filename
        for n,v in proof['source_pins'].items():assert proof['archive_files']['source/'+n]==v,n
        for name in ['matrix-1','original-controls-2']:
            prefix='evidence/'+name+'/';record=json.loads(archive.read(prefix+'COMPLETED.json'))
            assert {n[len(prefix):]:v for n,v in proof['archive_files'].items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'}==record['files']
            assert all(proof['archive_files'].get('source/'+n,proof['protected_prior_source_pins'].get(n))==v for n,v in record['source_pins'].items() if not n.startswith('build/'))
            if name=='matrix-1':
                assert record['tests_per_JVM']==440 and len(record['runs'])==3 and all(r['process']['exit_code']==0 for r in record['runs'])
                assert record['artifact']==proof['artifact'] and record['owned_classes']==proof['owned_classes']
            else:
                assert record['cases']==30 and record['process']['exit_code']==0 and record['source_function_loaded_directly'] is True and record['full_dreamlet_package_installed_or_validated'] is False
                assert archive.read('intake/dreamlet/R/compositePosteriorTest.R')==archive.read(prefix+'original-compositePosteriorTest.R')
        audit=json.loads(archive.read('source/docs/literature/mashr-composite/role-audit.json'))
        assert audit['role_count']==8 and audit['claims']['method_formula_resolved'] and not audit['claims']['catalog_promoted']
        assert {r['paper_id'] for r in audit['roles']}=={'P019','P021','P024','P073','P092','P104','P201','P213'}
        assert 'FAIL2' in json.loads(archive.read('history/original-controls-1/FAILURE.json'))['status']
        assert 'threshold >=0.05: true, false, false' in archive.read('logs/example-run-1.log').decode()
        assert 'dimnames' in archive.read('history/original-controls-1/original.stderr').decode()
    print(json.dumps(dict(status='passed-retained-native-composite-checkpoint-integrity',tests_per_JVM=440,
          original_R_cases=30,source_files=len(proof['source_pins']),protected_source_files=len(proof['protected_prior_source_pins']),
          archive_files=len(entries),archive_uncompressed_bytes=total,analysis_reexecuted=False,catalog_promoted=False),indent=2))


if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('FAIL2_COMPOSITE_CHECKPOINT: '+str(failure)+'\nRequired action: retain evidence, correct the source/archive mismatch and revalidate; do not report a complete paper reproduction.',file=sys.stderr);sys.exit(2)
