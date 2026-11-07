# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify exact root dispatch, native ORDER controls and original RUN evidence; install nothing."""
import json
import zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta


def verify():
    folder=ROOT/'docs/literature/monocle-tools-development';data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((folder/'VALIDATION.json').read_bytes())
    assert p['status']=='passed-Monocle-root-ORDER-and-original-RUN-development-controls'
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==digest
    assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for name,wanted in p['source_pins'].items():verify_source(ROOT,data,name,wanted['sha256']);assert byte_pin(files['current-source/'+name])==wanted
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json']);root='org/jlinalg/cli/JLinAlgCli.class'
    assert set(before)&set(after)==set(before) and {n for n in before if before[n]!=after[n]}=={root}
    assert set(after)-set(before)==set(p['added_entries']) and len(p['added_entries'])==2
    for name in [root,*p['added_entries']]:assert byte_pin(files['candidate-owned-entry/'+name])==after[name]
    base='build/literature-review/monocle-runtime/';get=lambda n:json.loads(files[base+n])
    build=get('root-build-1/COMPLETED.json');matrix=get('root-matrix-1/COMPLETED.json');runtime=get('root-runtime-1/COMPLETED.json');example=get('root-example-1/COMPLETED.json')
    assert build['artifact']==matrix['artifact']==runtime['artifact']==example['artifact']==p['artifact']
    assert matrix['tests_per_JVM']==535 and len(matrix['JVMs'])==3 and all(r['exit_code']==0 and r['tests']==535 for r in matrix['JVMs'])
    assert runtime['comparison']['all_atomic_coefficient_and_graph_test_columns_equal'] and runtime['comparison']['trajectory_axes_clusters_embedding_pseudotime_identical']
    assert len(example['runs'])==3 and all(r['exit_code']==0 and r['cell_values']==[3,0,5] and r['selected_cell']=='b' for r in example['runs'])
    delta=json.loads(files['current-source/docs/literature/omics-2023-2026/monocle-root-shared-source-compatibility.json'])
    source=files['current-source/src/main/java/org/jlinalg/cli/JLinAlgCli.java'];inverse=reverse_delta(source,delta['paths'][0])
    assert inverse==files[base+'root-build-1/inverse-root/JLinAlgCli.java']
    assert byte_pin(files[base+'root-build-1/inverse-classes/'+root])==before[root]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert {n:byte_pin(z.read(n)) for n in z.namelist()}==(before if label=='parent' else after)
    preservation=get('root-checkpoint-preservation-1/COMPLETED.json');assert preservation['preexisting_R_libraries_unchanged'] and len(preservation['preserved_unrelated_sources'])==18
    assert p['implementation_credit'] is False and p['whole_goal_complete'] is False and p['Monocle2_analysis_admitted'] is False
    print('Verified root ORDER/RUN, 535 tests on three JVMs, exact inverse dispatcher and preserved scientific entries')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/control checks')
    verify()
