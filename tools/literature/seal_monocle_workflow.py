# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete scoped original Monocle3 workflow evidence without catalog credit or release."""
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from monocle_workflow_evidence import tables


def main():
    base=ROOT/'build/literature-review/monocle-runtime';target=ROOT/'docs/literature/monocle3-workflow-development';assert not target.exists();target.mkdir()
    build=json.loads((base/'workflow-build-3/COMPLETED.json').read_bytes());files={}
    complete=['workflow-validation-4','workflow-families-3','workflow-variants-2','workflow-contracts-2','workflow-example-2']
    for name in complete:assert json.loads((base/name/'COMPLETED.json').read_bytes())['artifact']==build['artifact']
    # Retain every early failed transport/guard/comparison, as well as complete
    # successful original and Java controls. None of the early failures is a pass.
    folders=['workflow-profile-1','workflow-build-1','workflow-build-2','workflow-build-3',
        'workflow-validation-1','workflow-validation-2','workflow-validation-3','workflow-validation-4',
        'workflow-families-1','workflow-families-2','workflow-families-3','workflow-variants-1','workflow-variants-2',
        'workflow-contracts-1','workflow-contracts-2','workflow-example-1','workflow-example-2','workflow-checkpoint-preservation-1']
    for name in folders:
        assert (base/name).is_dir()
        for path in (base/name).rglob('*'):
            if path.is_file() and path.suffix!='.jar':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Retain the independent earlier full original reference, including complete
    # save directories and raw RDS objects, rather than only its summary.
    for path in (base/'current-readiness-probe-3').rglob('*'):
        if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['STARTED.json','RESULT.json','DECLARED-AUTHOR-OVERRIDES.json']:
        path=base/'reference-setup-3'/name;files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    sources=set(build['source_pins']);sources.add('src/test/java/org/jlinalg/cli/Monocle3WorkflowTest.java')
    sources.update(['.gitattributes','docs/literature/omics-2023-2026/monocle-workflow-checkout-shared-source-compatibility.json','docs/literature/MONOCLE3_WORKFLOW_DEVELOPMENT.md'])
    sources.update('tools/literature/'+n for n in ['prepare_monocle_workflow_profile.py','validate_monocle_workflow.py','validate_monocle_workflow_families.py',
        'validate_monocle_workflow_variants.py','validate_monocle_workflow_contracts.py','seal_monocle_workflow.py','monocle_workflow_evidence.py'])
    sources.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'examples/monocle3-workflow').rglob('*') if p.is_file())
    pins={n:pin(ROOT/n) for n in sorted(sources)};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    parent=ROOT/build['parent_artifact']['path'];candidate=ROOT/build['artifact']['path']
    for path,key in [(parent,'parent_artifact'),(candidate,'artifact')]:assert pin(path)=={k:build[key][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(candidate) as new:
        before={n:byte_pin(old.read(n)) for n in old.namelist()};after={n:byte_pin(new.read(n)) for n in new.namelist()}
        assert all(after[n]==v for n,v in before.items()) and set(after)-set(before)==set(build['added_entries'])
        files['artifact-parent-entries.json']=(json.dumps(before,sort_keys=True)+'\n').encode()
        files['artifact-candidate-entries.json']=(json.dumps(after,sort_keys=True)+'\n').encode()
        for n in build['added_entries']:files['candidate-owned-entry/'+n]=new.read(n)
    table_count=tables(files);layout=pack(target/'controls.zip',files)
    warning=target/'WARNINGS.md';warning.write_text('''# Original Monocle3 workflow development controls

Development artifact and standalone classpath command only. Canonical/released JAR and root dispatcher are unchanged. No Monocle catalog integration credit or whole-paper reproduction is claimed. Read ../MONOCLE3_WORKFLOW_DEVELOPMENT.md and the executed example.

**SKIP3:** supply the exact declared Windows R 4.6.1 / current 1.4.27 private runtime only where permitted; otherwise skip the dependent analysis. Never substitute a new edition for an old request. Historical 1.3.1/grr installation remains skipped; 1.3.3/1.3.7 remain unresolved. Analysis installs nothing.

**FAIL2:** no result of a failed atomic batch is admitted. Gaussian's original FAIL status and all-zero-gene failures are retained, including full models/coefficients. Earlier successful cases remain unadmitted if another case fails. Preserve errors/warnings and retry NEW paths after correcting the named cause; never drop genes, reorder IDs or replace Inf/NA to force success.

Retain subbars dependency warnings and prior IterableMatrix installation messages. Disk-backed inputs are not validated. Preserve entire CDS save directories and indices; a lone RDS is incomplete. TSV omits declared list columns: use complete original RDS and per-value kinds. Coefficient q-values retain original Holm adjustment. Stock graph_test is not a custom paper covariate model. Native auditing replays prepared-graph ordering; it does not independently refit graphs/models/tests. No portable VERIFY interface, DLL authentication or biological certification is claimed.

Early attempts are diagnostic evidence, not passing validation: workflow-validation-1 refused an incorrectly compared dependency version spelling; workflow-validation-2 found precision lost by the fixture exporter; workflow-families-1 rejected original Gaussian FAIL statuses; workflow-variants-1 caught R header whitespace stripping. The corrected literal parser and 17-digit fixtures preserve source values. Failed attempts remain retained. Final selected artifact is workflow-build-3; passing controls are validation-4, families-3, variants-2, contracts-2 and example-2.
''',encoding='utf8',newline='\n')
    record=dict(schema_version=1,status='passed-scoped-original-Monocle3-workflow-development-controls',artifact=build['artifact'],parent_artifact=build['parent_artifact'],
        archive=pin(target/'controls.zip'),warnings_document=pin(warning),source_pins=pins,unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],
        canonical_executable_unchanged=build['canonical_executable_unchanged'],atomic_table_exports_checked=table_count,root_dispatcher_changed=False,
        original_fitting_platform='Windows R4.6.1; actual Java adapter under Windows Java21',native_contract_JVMs=['Windows Java21','Windows Java25','Linux Java21'],
        implementation_credit=False,whole_goal_complete=False,pushed=False,**layout)
    save(target/'VALIDATION.json',record)
    (target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json']),encoding='utf8',newline='\n')
    print('Sealed',len(files),'logical files and',table_count,'complete atomic table exports; no catalog credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original evidence checks')
    main()
