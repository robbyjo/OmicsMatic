# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline original MACS reference/source audit; no install, analysis or promotion."""
import hashlib
import json
import sys
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack


def main():
    if not __debug__:raise RuntimeError('Never disable assertions with Python -O')
    folder=ROOT/'docs/literature/signac-peak-runtime-development';proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['status']=='passed-three-original-MACS2-reference-runtimes-and-27-calls-not-Java-workflow'
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof);prefix='build/literature-review/signac-runtime/'
    for name,wanted in proof['source_pins'].items():
        assert pin(ROOT/name)==wanted,name;raw=files['current-source/'+name]
        assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==wanted
    assert (proof['actual_original_calls'],proof['successful_original_outputs'],proof['valid_empty_peak_outputs'],proof['expected_empty_input_failures'])==(27,24,3,3)
    assert proof['exact_MACS2_editions']==['2.2.7.1','2.2.9','2.2.9.1']
    actual=success=fail=empty=0
    for index,version in enumerate(proof['exact_MACS2_editions'],1):
        record=json.loads(files[prefix+'peaks-macs-original-'+str(index)+'/COMPLETED.json'])
        assert record['actual_calls']==9 and record['input_records']==6600
        runtime=record['runtimes'][0];assert runtime['version']==version
        guard=proof['runtime_guards'][version]
        assert guard['source_python_and_headers_unchanged'] and guard['installed_script_interpreter_directive_only']
        for case in runtime['cases']:
            directory=prefix+'peaks-macs-original-'+str(index)+'/'+version+'/'+case['name']+'/'
            assert {n.removeprefix(directory) for n in files if n.startswith(directory)}==set(case['files'])
            for name,wanted in case['files'].items():
                raw=files[directory+name];assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==wanted
            process=json.loads(files[directory+'PROCESS.json']);assert process['exit_code']==case['exit_code']
            actual+=1
            if case['name']=='empty-input':
                assert case['exit_code']==1 and case['peak_rows'] is None;fail+=1;continue
            assert case['exit_code']==0;success+=1
            path='fixture_peaks.'+('broadPeak' if case['broad'] else 'narrowPeak')
            rows=files[directory+path].decode('ascii').splitlines();assert len(rows)==case['peak_rows']
            if not rows:assert case['name']=='no-significant-peaks';empty+=1
            for row in rows:
                fields=row.split('\t');assert len(fields)==(9 if case['broad'] else 10)
                assert fields[0]=='chr1' and 0<=int(fields[1])<int(fields[2])
                assert int(fields[4])>=0 and fields[5]=='.'
                for token in fields[6:9]:assert float(token)>=0
                if not case['broad']:assert 0<=int(fields[9])<int(fields[2])-int(fields[1])
        directory=prefix+'peaks-macs-original-'+str(index)+'/'+version+'/'
        original=files[directory+'pooled-BED/fixture_peaks.narrowPeak']
        assert original==files[directory+'pooled-gzip-BED/fixture_peaks.narrowPeak']==files[directory+'support-does-not-weight/fixture_peaks.narrowPeak']
        assert original!=files[directory+'pooled-BEDPE/fixture_peaks.narrowPeak']
        metadata=json.loads(files[prefix+'peaks-source-intake-1/MACS2-'+version+'-pypi.json'])
        archive=files[prefix+'peaks-source-intake-1/MACS2-'+version+'.tar.gz']
        expected=next(r for r in metadata['urls'] if r['packagetype']=='sdist')
        assert hashlib.sha256(archive).hexdigest()==expected['digests']['sha256']==proof['unchanged_source_archives'][version]['sha256']
        home=ROOT/guard['home']
        if home.exists():
            assert pin(home/'bin/macs2')==guard['installed_script']
            assert all(pin(home/'lib/python3.9/site-packages/MACS2'/n)==v for n,v in guard['package_files'].items())
    assert (actual,success,empty,fail)==(27,24,3,3)
    for key in ['MACS_scientific_or_build_source_patched','actual_Java_CallPeaks_analysis_interface',
                'complete_group_reduction_validated','user_analysis_admitted','catalog_implementation_credit',
                'root_dispatch_added','historical_whole_environment_reproduced','full_host_or_DLL_authentication',
                'biological_accuracy_certified','full_paper_reproduction','full_project_test_suite_run',
                'released','pushed','deployed','whole_goal_complete']:assert proof[key] is False,key
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_preserved']
    assert pin(ROOT/'docs/literature/signac-peak-preparation-development/VALIDATION.json')==proof['native_preparation_parent']
    print(json.dumps(dict(status='passed-offline-original-MACS2-27-call-reference',exact_editions=proof['exact_MACS2_editions'],logical_files=proof['logical_files'],source_pins=len(proof['source_pins']),Java_workflow_admitted=False)))


if __name__=='__main__':
    try:main()
    except Exception as e:
        print('MACS REFERENCE EVIDENCE FAIL2: '+str(e)+'\nRequired action: preserve the evidence, correct the named source/archive/runtime mismatch and repeat verification. No Java/user peak result is admitted.',file=sys.stderr);sys.exit(2)
