# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strict portable native LOFTEE audit component proof; current/future annotation-adapter credit is separate."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin


def verify():
    folder=ROOT/'docs/literature/loftee-audit-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-LOFTEE-output-audit-component' and p['component_admitted'] and not p['LOFTEE_execution_adapter_admitted'] and not p['implementation_credit'] and not p['catalog_changed'] and not p['whole_goal_complete'];assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings'] and pin(folder/'APACHE-2.0.txt')==p['license'];files=unpack(folder/'controls.zip',p)
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','APACHE-2.0.txt','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    for name,value in p['source_pins'].items():assert pin(ROOT/name)==value and byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda n:json.loads(files[n]);base='build/literature-review/loftee-runtime/';reports={k:get(base+p['final_'+k]+'/COMPLETED.json') for k in ['build','native','example','fixture']};assert reports['build']['artifact']==reports['native']['artifact']==reports['example']['artifact']==p['artifact'];assert reports['native']['tests_per_JVM']==p['tests_per_JVM']==43 and len(reports['native']['JVMs'])==3 and all(r['exit_code']==r['skipped']==0 and r['tests']==43 for r in reports['native']['JVMs']);assert reports['example']['status']=='passed-exact-documented-LOFTEE-native-audit-example'
    protocols=[]
    for name in p['final_original_probes']:
        report=get(base+name+'/COMPLETED.json');assert report['status']=='passed-original-VEP105-LOFTEE-synthetic-resource-and-full-output-audit-probes' and report['artifact']==p['artifact'];protocols.extend(report['cases'])
        for case in report['cases']:
            prefix=base+name+'/'+case['case']+'/';assert byte_pin(files[prefix+'original.vcf'])==case['complete_original_vcf'] and byte_pin(files[prefix+'native-annotations.tsv'])==case['native_annotations'] and byte_pin(files[prefix+'native-variants.tsv'])==case['native_variants'];assert get(base+name+'/'+case['case']+'.process.json')['exit_code']==0 and get(base+name+'/'+case['case']+'-native-audit.process.json')['exit_code']==0;assert case['summary']==dict(variants=15,annotations=80,HC=8 if case['case']=='original-default' else 10,LC=2 if case['case']=='original-default' else 0)
        reports[name]=report
    assert len(protocols)==4 and sum(c['summary']['variants'] for c in protocols)==p['original_variant_rows']==60 and sum(c['summary']['annotations'] for c in protocols)==p['original_annotation_rows']==320
    for report in reports.values():
        for name,value in report.get('source_pins',{}).items():assert pin(ROOT/name)==value
    fixture=ROOT/reports['fixture']['fixture']['path'];assert pin(fixture)=={k:reports['fixture']['fixture'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(fixture) as z:
        index=json.loads(z.read('INDEX.json'));assert len(index['cases'])==4 and len(z.namelist())==len(set(z.namelist()))==len(index['files'])+1 and index['independent_python_source_ledgers_match_java']
        for name,value in index['files'].items():assert byte_pin(z.read(name))==value
        for name in index['cases']:
            actual='original-probe-5-cited-fork' if name.startswith('original-probe-5-cited-fork-') else 'original-probe-4';case=name.removeprefix(actual+'-');prefix=base+actual+'/'+case+'/';assert z.read(name+'/original/original.vcf')==files[prefix+'original.vcf'];assert z.read(name+'/expected-annotations.tsv')==files[prefix+'native-annotations.tsv'] and z.read(name+'/expected-variants.tsv')==files[prefix+'native-variants.tsv']
    counts=get('catalog-baseline/summary.json')['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,63,251);assert next(t for t in get('catalog-baseline/tool-inventory.json') if t['id']=='loftee')['implementation_status']=='missing';assert get(base+'role-drafts-1/COMPLETED.json')['status']=='validated-eight-unapplied-LOFTEE-primary-role-drafts';assert get(base+'role-drafts-1/COMPLETED.json')['applied'] is False
    for name in ['dependency-health-3','dependency-health-4']:assert get(base+name+'/COMPLETED.json')['status']=='PASS_PRIVATE_VEP105_AND_EXACT_LOFTEE_MODULE_HEALTH_ONLY'
    assert get(base+'reference-setup-1/COMPLETED.json')['status']=='SKIP3_DEPENDENCY_UNAVAILABLE' and get(base+'dependency-health-1/COMPLETED.json')['status']=='SKIP3_DEPENDENCY_UNAVAILABLE';assert all(get(base+'original-probe-'+str(i)+'/COMPLETED.json')['status']=='FAIL2_ORIGINAL_LOFTEE_PROBE_INCOMPLETE' for i in [1,2,3]);omissions=get('EXPLICIT-OMISSIONS.json');assert omissions['all_final_original_synthetic_inputs_outputs_logs_complete'] and not omissions['LOFTEE_execution_adapter_admitted']
    old=get('artifact-parent-entries.json');new=get('artifact-candidate-entries.json');assert len(old)==p['unchanged_parent_entries'] and all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(p['added_entries']);assert all(byte_pin(files['candidate-owned-entry/'+n])==new[n] for n in p['added_entries'])
    for key,entries in [('parent_artifact',old),('artifact',new)]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==entries
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    print('Verified native LOFTEE audit:43 checks/JVM,4 original cases,60 variants,320 annotations,8 independent source ledgers and documented example; execution adapter and named-tool credit remain separate');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable component/source/evidence checks')
    verify()
