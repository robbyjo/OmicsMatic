# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict portable source/fixture/reference proof for the native SMR component; unfinished adapters stay missing."""
import gzip,json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

BASE='build/literature-review/smr-runtime/'


def verify():
    folder=ROOT/'docs/literature/smr-statistics-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-SMR-statistics-component' and p['component_admitted'] and not p['SMR_or_HEIDI_workflow_admitted'] and not p['implementation_credit'] and not p['catalog_changed'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p);get=lambda n:json.loads(files[n])
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    for n,v in p['source_pins'].items():assert pin(ROOT/n)==v and byte_pin(files['current-source/'+n])==v
    for n,v in p['parent_proofs'].items():assert pin(ROOT/n)==v
    reports={k:get(BASE+n+'/COMPLETED.json') for k,n in p['final_attempts'].items()};assert reports['build']['artifact']==reports['native']['artifact']==reports['example']['artifact']==p['artifact'];assert reports['native']['component_admitted'] and not reports['native']['SMR_or_HEIDI_workflow_admitted'] and all(r['tests']==81 and r['skipped']==r['exit_code']==0 for r in reports['native']['JVMs']) and len(reports['native']['JVMs'])==3;assert reports['kernel']['cases']==72 and not reports['kernel']['original_scientific_function_bodies_patched'];assert reports['example']['snippet_compiled_from_document'];assert reports['roles']['primary_roles']==12 and reports['roles']['supporting_pointers']==5 and not reports['roles']['catalog_changed']
    for report in reports.values():
        for name,value in report['source_pins'].items():
            if not name.startswith('build/'):assert pin(ROOT/name)==value
    omitted=get('BUILD-ONLY-OMISSIONS.json');assert not omitted['scientific_input_or_output_replaced'] and len(omitted['files'])==p['build_only_omission_receipts'];assert all(name not in files for name in omitted['files'])
    for key,source_dir in [('source_131','source-2023-initial-1'),('source_143','source-intake-1')]:
        report=reports[key];assert report['status']=='built-unchanged-original-SMR-reference-only' and report['original_source_and_dependencies_unchanged'] and report['owned_source_members_unchanged'] and report['original_help_exit_code']==1;assert byte_pin(files[report['program']['path']])=={k:report['program'][k] for k in ['bytes','sha256']};receipt=get(BASE+source_dir+'/SOURCE.json');assert receipt['commit']==report['source_commit'] and all(byte_pin(files[BASE+source_dir+'/source/'+n])==v for n,v in receipt['source_members'].items())
    fixture=json.loads(gzip.decompress(files['current-source/src/test/resources/org/jlinalg/mr/smr/statistics-controls-v1.json.gz']));assert len(fixture['kernel_inputs'])==len(fixture['kernel_original'])==72;assert sum(len(r['original_covariance'])**2 for r in fixture['kernel_original'])==25116;assert sum(len(r['original_chisq_survival_for_ratio_diagnostics']) for r in fixture['kernel_original'])==924;assert len(fixture['original_tables'])==10 and sum(len(t['rows']) for t in fixture['original_tables'])==40
    for version in ['1.3.1','1.4.3']:
        prefix=BASE+'original-probe-'+version+'-1/';probe=get(prefix+'COMPLETED.json');assert probe['status']=='captured-full-original-SMR-workflows-not-adapter-validation' and len(probe['protocols'])==7 and all(r['exit_code']==0 for r in probe['protocols']) and not probe['adapter_validated']
        for table in [t for t in fixture['original_tables'] if t['version']==version]:
            path=prefix+table['case']+('/original.msmr' if table['case']=='multi' else '/original.smr');assert byte_pin(files[path])==table['original_file'];rows=[line.split('\t') for line in files[path].decode('utf8').splitlines()];assert rows[0]==table['header'] and rows[1:]==table['rows']
    assert all(r['status']=='SKIP3_OFFICIAL_RUNTIME_UNAVAILABLE' and r['detail']=='HTTP Error 403: Forbidden' for r in get(BASE+'reference-download-1/COMPLETED.json')['attempts']);assert get(BASE+'reference-1.3.1-build-1/COMPLETED.json')['status']==get(BASE+'reference-1.4.3-build-1/COMPLETED.json')['status']=='SKIP3_SMR_REFERENCE_UNAVAILABLE';assert get(BASE+'reference-1.3.1-recovery-1/COMPLETED.json')['upstream_help_exit_code']==get(BASE+'reference-1.4.3-recovery-1/COMPLETED.json')['upstream_help_exit_code']==1
    old=get('artifact-parent-entries.json');new=get('artifact-candidate-entries.json');assert len(old)==p['unchanged_parent_entries'] and all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(p['added_entries']);assert all(byte_pin(files['candidate-owned-entry/'+n])==new[n] for n in p['added_entries'])
    for key,entries in [('parent_artifact',old),('artifact',new)]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==entries
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    historical_catalog=json.loads((ROOT/'docs/literature/omics-2023-2026/dndscv-catalog-validation.json').read_bytes());assert (historical_catalog['new_scoped_integrations'],historical_catalog['missing_integrations'],historical_catalog['reviewed_uses'])==(78,253,932);print('Verified native SMR component:81 checks/JVM,72 original covariance cases,10 complete original ratio tables;12 unapplied role drafts and no full-adapter credit in this checkpoint');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/fixture/component checks')
    verify()
