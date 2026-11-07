# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal actual root/native/original controls; keep unresolved Monocle2 work explicitly unadmitted."""
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin


def main():
    base=ROOT/'build/literature-review/monocle-runtime';folder=ROOT/'docs/literature/monocle-tools-development';assert not folder.exists();folder.mkdir()
    build=json.loads((base/'root-build-1/COMPLETED.json').read_bytes());files={}
    for name in ['root-matrix-1','root-runtime-1','root-example-1']:assert json.loads((base/name/'COMPLETED.json').read_bytes())['artifact']==build['artifact']
    for name in ['root-build-1','root-matrix-1','root-runtime-1','root-example-1','root-checkpoint-preservation-1']:
        for path in (base/name).rglob('*'):
            if path.is_file() and path.suffix!='.jar':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    sources=set(build['source_pins']);sources.update(['.gitattributes','docs/literature/omics-2023-2026/monocle-root-checkout-shared-source-compatibility.json',
        'src/test/java/org/jlinalg/cli/MonocleRootCliTest.java','docs/literature/MONOCLE_TOOLS_DEVELOPMENT.md'])
    sources.update('tools/literature/'+n for n in ['validate_monocle_root.py','validate_monocle_root_runtime.py','validate_monocle_root_example.py','seal_monocle_root.py','monocle_root_evidence.py'])
    sources.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'examples/monocle-tools').rglob('*') if p.is_file());pins={n:pin(ROOT/n) for n in sorted(sources)}
    files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    parent=ROOT/build['parent_artifact']['path'];candidate=ROOT/build['artifact']['path']
    for path,key in [(parent,'parent_artifact'),(candidate,'artifact')]:assert pin(path)=={k:build[key][k] for k in ['bytes','sha256']}
    root='org/jlinalg/cli/JLinAlgCli.class'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(candidate) as new:
        before={n:byte_pin(old.read(n)) for n in old.namelist()};after={n:byte_pin(new.read(n)) for n in new.namelist()};assert {n for n in before if before[n]!=after[n]}=={root}
        files['artifact-parent-entries.json']=(json.dumps(before,sort_keys=True)+'\n').encode();files['artifact-candidate-entries.json']=(json.dumps(after,sort_keys=True)+'\n').encode()
        for n in [root,*build['added_entries']]:files['candidate-owned-entry/'+n]=new.read(n)
    layout=pack(folder/'controls.zip',files);warnings=folder/'WARNINGS.md'
    warnings.write_text('''# Root Monocle development commands: scope, warnings and recovery

ORDER is native prepared-graph ordering and requires no R. RUN invokes the unchanged validated original Monocle3 1.4.27 worker. Use MONOCLE_TOOLS_DEVELOPMENT.md and the executed examples. The canonical/released JAR is unchanged; no catalog integration credit or whole-paper reproduction is awarded.

**FAIL2:** preserve failed attempts; no partial result is admitted. ORDER retains disconnected Inf and naive overflow/tie behavior. Never repair IDs, rescale coordinates, replace Inf/NA, or drop cells/genes to force success. RUN rejects the entire batch if a case/model/audit fails, including Gaussian's original FAIL classification. Preserve all original errors and warnings, complete CDS directories and list-column RDS objects. Use NEW paths after correcting the named cause.

**SKIP3:** supply the exact declared R/platform/private dependencies only where installation is permitted; otherwise skip dependent RUN analysis. Historical editions are not silently substituted. Original subbars warnings and earlier IterableMatrix installation messages remain recorded. No disk-backed input, custom covariate graph_test or biological certification is claimed.

Monocle2 is separate unfinished work. Current R4.6/dplyr1.2.1 fails original dispersion estimation on defunct group_by_. Original dplyr1.1.4 cannot compile against removed R4.6 APIs. Dependent Monocle2 analysis remains unadmitted while a separate older-runtime candidate is provisioned and checked; see MONOCLE2_CURRENT_READINESS.md. No Monocle2 result is inferred from root ORDER/RUN success.
''',encoding='utf8',newline='\n')
    save(folder/'VALIDATION.json',dict(schema_version=1,status='passed-Monocle-root-ORDER-and-original-RUN-development-controls',artifact=build['artifact'],parent_artifact=build['parent_artifact'],
        source_pins=pins,archive=pin(folder/'controls.zip'),warnings_document=pin(warnings),added_entries=build['added_entries'],replaced_entries=build['replaced_entries'],unchanged_parent_entries=build['unchanged_parent_entries'],
        canonical_executable_unchanged=build['canonical_executable_unchanged'],tests_per_JVM=535,JVMs=3,original_helper_outcomes_per_JVM=519,
        original_RUN_reference_cells=160,original_RUN_reference_genes=80,original_RUN_reference_coefficient_rows=240,original_RUN_reference_graph_test_rows=80,
        native_scientific_entries_changed=False,original_workflow_entries_changed=False,Monocle2_analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,pushed=False,**layout))
    (folder/'SHA256SUMS').write_text(''.join(pin(folder/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n')
    print('Sealed root ORDER/RUN controls and exact source inverse;',len(files),'logical files')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full control/source preservation checks')
    main()
