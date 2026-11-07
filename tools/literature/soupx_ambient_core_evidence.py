# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable source-bound ambient-core and full original control audit; no R runtime or installation."""
import gzip,json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from audit_soupx_ambient_controls import audit

BASE='build/literature-review/soupx-runtime/'


def verify():
    folder=ROOT/'docs/literature/soupx-ambient-core-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-SoupX-ambient-arithmetic-and-request-contracts' and p['native_core_admitted'] and not p['ambient_workflow_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document']
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    files=unpack(folder/'controls.zip',p)
    for name,value in p['source_pins'].items():assert pin(ROOT/name)==value and byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda name:json.loads(files[BASE+name]);build=get('ambient-core-build-1/COMPLETED.json');validation=get('ambient-core-validation-5/COMPLETED.json');example=get('ambient-core-example-1/COMPLETED.json');diagnosis=get('poisson-diagnosis-1/COMPLETED.json');assert all(r['artifact']==p['artifact'] for r in [build,validation,example]);assert len(validation['JVMs'])==3 and all(r['tests']==28 and r['skipped']==0 and r['exit_code']==0 for r in validation['JVMs']);assert not validation['ambient_workflow_admitted'] and diagnosis['original_fit_objects_preserved'] and diagnosis['preexisting_libraries_unchanged'];assert files[BASE+'poisson-diagnosis-1/LIBRARIES-BEFORE.json']==files[BASE+'poisson-diagnosis-1/LIBRARIES-AFTER.json'];total=0
    for version,attempt in [('1.6.2',2),('1.3.0',3)]:
        prefix=f'ambient-controls-{version}-{attempt}/';proof=get(prefix+'COMPLETED.json');controls=get(prefix+'controls.json');inputs=get(prefix+'inputs.json');assert proof['status']=='captured-original-SoupX-ambient-controls-not-adapter-validation' and proof['preexisting_libraries_unchanged'] and controls['source_bodies_and_formals_checked_before_and_after'] and not controls['user_analysis_admitted'];assert proof['cases']==len(controls['cases']) and len(controls['guarded_functions'])==(11 if version=='1.6.2' else 10);total+=len(controls['cases']);assert files[BASE+prefix+'LIBRARIES-BEFORE.json']==files[BASE+prefix+'LIBRARIES-AFTER.json'];assert proof['controls']==byte_pin(files[BASE+prefix+'controls.json']) and proof['original_inputs']==byte_pin(files[BASE+prefix+'inputs.json']) and proof['original_input_objects']==byte_pin(files[BASE+prefix+'inputs.rds'])
        for name,value in proof['source_pins'].items():assert byte_pin(files[BASE+prefix+'source-snapshot/'+name])==value
        assert audit(inputs,controls,diagnosis['versions'][version])==p['reference_audits'][version]
        fixture=files['current-source/src/test/resources/org/jlinalg/singlecell/soupx-ambient/original-'+version+'-v1.json.gz'];assert gzip.decompress(fixture)==files[BASE+prefix+'controls.json'];assert gzip.decompress(files['current-source/src/test/resources/org/jlinalg/singlecell/soupx-ambient/inputs-v1.json.gz'])==files[BASE+prefix+'inputs.json']
        for row in controls['cases']:
            path=BASE+prefix+row['id']+'/original-return.rds';assert (path in files)==row['returned']
    assert total==55 and json.loads(gzip.decompress(files['current-source/src/test/resources/org/jlinalg/singlecell/soupx-ambient/poisson-diagnosis-v1.json.gz']))==diagnosis['versions']
    assert files[BASE+'ambient-core-example-1/stdout'].decode('utf8').replace('\r\n','\n')=='Selected droplets: [empty1, empty2]\nSoup fractions: [0.5, 0.5]\nAnalytical Poisson rho: 0.1\nNo count correction was performed.\n';assert get('ambient-core-example-1/process.json')['exit_code']==0
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json']);assert all(after[n]==v for n,v in before.items()) and len(before)==p['unchanged_parent_entries'] and set(after)-set(before)==set(p['added_entries'])
    for name in p['added_entries']:assert name.endswith('.class') and byte_pin(files['candidate-owned-entry/'+name])==after[name]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==(before if label=='parent' else after)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    print('Verified native ambient core:28 checks/JVM,55 original controls,1808 profile rows,372 manual mask decisions and retained GLM differences; execution adapter unfinished')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference/core checks')
    verify()
