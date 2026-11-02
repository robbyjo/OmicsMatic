#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native sharing evidence and paper-method audit, retaining prior checkpoints."""
import json,sys,zipfile
from datetime import datetime,timezone
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import census,pin,write
ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/literature-review/mashr-runtime/sharing-development-1'
DEST=ROOT/'docs/literature/mashr-sharing'
SOURCES=['src/main/java/org/jlinalg/shrinkage/MashSharing.java','src/main/java/org/jlinalg/shrinkage/MashSignScores.java','src/test/java/org/jlinalg/shrinkage/MashSharingTest.java','src/test/java/org/jlinalg/shrinkage/MashSharingOriginalTest.java','src/test/resources/org/jlinalg/shrinkage/mashr-sharing/.gitattributes','src/test/resources/org/jlinalg/shrinkage/mashr-sharing/original-controls.zip','tools/literature/mashr-sharing-controls.R','tools/literature/collect_mashr_sharing_controls.py','tools/literature/validate_mashr_sharing.py','tools/literature/finalize_mashr_sharing.py','tools/literature/validate_mashr_sharing_checkpoint.py','docs/literature/MASHR_SHARING.md','docs/literature/mashr-sharing/method-audit.json','docs/literature/mashr-sharing/.gitattributes','examples/mashr-sharing/Example.java','examples/mashr-sharing/README.md']


def main():
    assert not (DEST/'VALIDATION.json').exists() and not (DEST/'controls.zip').exists()
    matrix=json.loads((RUN/'matrix-2/COMPLETED.json').read_text(encoding='utf-8'));original=json.loads((RUN/'original-controls-1/COMPLETED.json').read_text(encoding='utf-8'))
    assert matrix['status']=='passed-three-JVM-native-mash-sharing-development-matrix' and matrix['tests_per_JVM']==371 and len(matrix['runs'])==3 and original['cases']==32
    assert all(pin(ROOT/n)==v for n,v in matrix['source_pins'].items())
    assert original['process']['exit_code']==0 and original['process']['error'] is None
    # The reference producer ran R only. Preserve its observed Java context and
    # prove the subsequent allocation-margin-only edit without changing its hashes.
    margin='src/main/java/org/jlinalg/shrinkage/MashSignScores.java';before=RUN/'before-storage-margin/MashSignScores.java'
    assert pin(before)==original['source_pins'][margin]
    assert (ROOT/margin).read_bytes().replace(b'Math.multiplyExact(128L*rows,groups.size())',b'Math.multiplyExact(96L*rows,groups.size())')==before.read_bytes()
    assert all(pin(ROOT/n)==v for n,v in original['source_pins'].items() if n!=margin)
    old=json.loads((ROOT/'docs/literature/mashr-native-workflow/VALIDATION.json').read_text(encoding='utf-8'));preparation=json.loads((ROOT/'docs/literature/mashr-original-preparation/VALIDATION.json').read_text(encoding='utf-8'))
    assert all(pin(ROOT/n)==v for n,v in old['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in preparation['source_pins'].items())
    catalog=pin(ROOT/'docs/literature/omics-2023-2026/summary.json');assert catalog==preparation['catalog_summary_unchanged']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==matrix['canonical_executable_unchanged']==preparation['canonical_executable_unchanged']
    assert pin(ROOT/matrix['artifact']['path'])=={k:matrix['artifact'][k] for k in ['bytes','sha256']}
    example=(RUN/'example-run-2.log').read_text(encoding='utf-8');assert 'selected=2, shared=1, fraction=0.5' in example and 'EMPTY_SELECTED_POPULATION' in example and 'UNDEFINED_SELECTED_RATIO' in example and 'Feature 3:' in example
    bound={n:pin(ROOT/n) for n in SOURCES};items={};graphs={}
    def tree(path,prefix):
        graph=census(path,536_870_912,20000);graphs[path]=graph
        for name,value in graph.items():assert prefix+'/'+name not in items;items[prefix+'/'+name]=(path/name,value)
    for name in ['matrix-2','original-controls-1']:
        tree(RUN/name,'evidence/'+name);assert {n:v for n,v in graphs[RUN/name].items() if n!='COMPLETED.json'}==json.loads((RUN/name/'COMPLETED.json').read_text(encoding='utf-8'))['files']
    for name in ['matrix-1','before-storage-margin','example-classes-2']:tree(RUN/name,'history/'+name)
    # Review metadata records the primary PDF hash; the publisher PDF remains in
    # the private intake rather than being redistributed in this source archive.
    receipt=RUN/'P092-supplement-intake-1/RECEIPT.json';items['paper-review/P092-RECEIPT.json']=(receipt,pin(receipt))
    for path in RUN.glob('*.log'):items['logs/'+path.name]=(path,pin(path))
    for name,value in bound.items():items['source/'+name]=(ROOT/name,value)
    with zipfile.ZipFile(DEST/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,(path,value) in sorted(items.items()):
            raw=path.read_bytes();assert pin(path)==value;entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
    assert all(census(path,536_870_912,20000)==graph for path,graph in graphs.items()) and all(pin(ROOT/n)==v for n,v in bound.items())
    dependencies=['docs/literature/mashr-native-workflow/VALIDATION.json','docs/literature/mashr-native-workflow/controls.zip','docs/literature/MASHR_NATIVE_EXECUTION_VALIDATION.json','docs/literature/mashr-native-execution-controls.zip','docs/literature/mashr-original-preparation/VALIDATION.json','docs/literature/mashr-original-preparation/controls.zip']
    proof=dict(schema_version=1,status='passed-native-mashr-sharing-and-marginal-score-development-checkpoint',recorded_utc=datetime.now(timezone.utc).isoformat(),scope='Native strict/inclusive significance summaries, original posterior-mean pairwise sharing and explicitly nominal-gated marginal sign-score products; no inferred joint probability.',artifact=matrix['artifact'],archive=dict(path='controls.zip',**pin(DEST/'controls.zip'),entries=len(items)),source_pins=bound,protected_native_source_pins=old['source_pins'],protected_preparation_source_pins=preparation['source_pins'],baseline_dependencies={n:pin(ROOT/n) for n in dependencies},tests_per_JVM=371,new_tests_per_JVM=46,original_R_cases=32,heap_mib=128,platforms=[r['name'] for r in matrix['runs']],protected_packaged_classes_unchanged=matrix['protected_packaged_classes_unchanged'],owned_classes=matrix['owned_classes'],canonical_executable_unchanged=matrix['canonical_executable_unchanged'],catalog_summary_unchanged=catalog,reference_collection_java_context=dict(path=margin,before=pin(before),after=pin(ROOT/margin),difference='Only conservative result-storage coefficient increased from96 to128; original reference producer executed no Java; final actual-JAR matrix rerun in full.'),archive_files={n:v for n,(_,v) in sorted(items.items())},claims=dict(native_library_API_available=True,original_reference_functions_executed=True,public_summary_command_available=False,study_route_available=False,formal_P024_composite_implemented=False,paper_role_coverage_complete=False,joint_event_probability_inferred=False,catalog_promoted=False,whole_paper_reproduced=False,biological_accuracy_certified=False,released_or_deployed=False),required_action='Preserve empty and undefined sharing states rather than reporting zero. Marginal sign products are not joint probabilities or same-sign sharing. Retain invalid/cancelled request diagnostics, correct the named cause and rerun the full request. Keep baseline archives for dependency closure; read MASHR_SHARING.md and method-audit.json.')
    write(DEST/'VALIDATION.json',proof);print(json.dumps(dict(status=proof['status'],tests_per_JVM=371,original_R_cases=32,archive=proof['archive'])));return 0


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_SHARING_FREEZE: '+str(failure),file=sys.stderr);sys.exit(2)
