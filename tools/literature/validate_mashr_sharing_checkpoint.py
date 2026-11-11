#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check every retained sharing artifact and live source binding; no R rerun."""
import hashlib,json,stat,sys,zipfile
from pathlib import Path,PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin
ROOT=Path(__file__).resolve().parents[2]


def verify():
    if not __debug__:raise RuntimeError('Never disable scientific evidence assertions')
    folder=ROOT/'docs/literature/mashr-sharing';proof=json.loads((folder/'VALIDATION.json').read_text(encoding='utf-8'))
    assert proof['schema_version']==1 and proof['status']=='passed-native-mashr-sharing-and-marginal-score-development-checkpoint'
    assert proof['tests_per_JVM']==371 and proof['new_tests_per_JVM']==46 and proof['original_R_cases']==32 and proof['heap_mib']==128
    assert set(proof['platforms'])=={'windows-java21','windows-java25','linux-java21'}
    for field in ['source_pins','protected_native_source_pins','protected_preparation_source_pins','baseline_dependencies']:
        assert all(pin(ROOT/n)==v for n,v in proof[field].items()),field
    assert len(proof['protected_native_source_pins'])==81 and len(proof['protected_preparation_source_pins'])==22
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'] and pin(ROOT/'docs/literature/omics-2023-2026/summary.json')==proof['catalog_summary_unchanged']
    assert proof['claims']==dict(native_library_API_available=True,original_reference_functions_executed=True,public_summary_command_available=False,study_route_available=False,formal_P024_composite_implemented=False,paper_role_coverage_complete=False,joint_event_probability_inferred=False,catalog_promoted=False,whole_paper_reproduced=False,biological_accuracy_certified=False,released_or_deployed=False)
    assert proof['archive']['path']=='controls.zip' and pin(folder/'controls.zip')=={k:proof['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(folder/'controls.zip') as archive:
        entries=archive.infolist();assert len(entries)==len({e.filename for e in entries})==len(proof['archive_files'])==proof['archive']['entries'];assert {e.filename for e in entries}==set(proof['archive_files'])
        total=0
        for entry in entries:
            path=PurePosixPath(entry.filename);assert not entry.is_dir() and not path.is_absolute() and '..' not in path.parts and '\\' not in entry.filename and stat.S_IFMT(entry.external_attr>>16) in [0,stat.S_IFREG]
            expected=proof['archive_files'][entry.filename];assert entry.file_size==expected['bytes'] and entry.file_size<=33_554_432
            h=hashlib.sha256();size=0
            with archive.open(entry) as stream:
                while block:=stream.read(1_048_576):size+=len(block);h.update(block)
            total+=size;assert total<=536_870_912 and size==expected['bytes'] and h.hexdigest()==expected['sha256'],entry.filename
        for n,v in proof['source_pins'].items():assert proof['archive_files']['source/'+n]==v
        for name in ['matrix-2','original-controls-1']:
            record=json.loads(archive.read('evidence/'+name+'/COMPLETED.json'));prefix='evidence/'+name+'/'
            actual={n[len(prefix):]:v for n,v in proof['archive_files'].items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'};assert actual==record['files']
            if name=='matrix-2':assert record['tests_per_JVM']==371 and len(record['runs'])==3 and all(r['process']['exit_code']==0 for r in record['runs']) and record['owned_classes']==proof['owned_classes']
            else:assert record['cases']==32 and record['process']['exit_code']==0 and record['process']['error'] is None
        original=archive.read('history/before-storage-margin/MashSignScores.java');assert dict(bytes=len(original),sha256=hashlib.sha256(original).hexdigest())==proof['reference_collection_java_context']['before']
        current=(ROOT/proof['reference_collection_java_context']['path']).read_bytes();assert current.replace(b'Math.multiplyExact(128L*rows,groups.size())',b'Math.multiplyExact(96L*rows,groups.size())')==original
    audit=json.loads((folder/'method-audit.json').read_text(encoding='utf-8'));assert audit['P092_supplement']['reviewed_pdf_page']==4 and audit['P024_formal_composite']['status']=='unresolved-not-substituted'
    print(json.dumps(dict(status='passed-retained-native-sharing-checkpoint-integrity',tests_per_JVM=371,platforms=proof['platforms'],original_R_cases=32,source_files=len(proof['source_pins']),archive_files=len(entries),archive_uncompressed_bytes=total,analysis_reexecuted=False,catalog_promoted=False),indent=2))


if __name__=='__main__':
    try:verify()
    except Exception as failure:print('FAIL2_SHARING_CHECKPOINT: '+str(failure)+'\nRequired action: retain the evidence, correct the named source/archive mismatch and revalidate. Do not promote incomplete roles.',file=sys.stderr);sys.exit(2)
