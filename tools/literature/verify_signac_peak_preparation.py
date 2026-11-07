# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline verification of native split preparation; never execute MACS or install."""
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack


def main():
    if not __debug__:
        raise RuntimeError('Assertions must be enabled; never Python -O')
    folder = ROOT / 'docs/literature/signac-peak-preparation-development'
    proof = json.loads((folder / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'passed-native-grouped-peak-preparation-not-MACS-workflow'
    assert pin(folder / 'controls.zip') == proof['archive'] and pin(folder / 'WARNINGS.md') == proof['warnings_document']
    files = unpack(folder / 'controls.zip', proof)
    for n, wanted in proof['source_pins'].items():
        assert pin(ROOT / n) == wanted, n
        raw = files['current-source/' + n]
        assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == wanted
    assert proof['normal_Gradle_tests'] == proof['actual_JAR_tests_per_JVM'] == 32
    assert len(proof['JVMs']) == 3 and all(r['passed'] == 32 and r['skipped'] == 0 for r in proof['JVMs'])
    junit = ET.fromstring(files['normal-Gradle-tests.xml'])
    assert all(int(junit.get(k)) == value for k, value in dict(tests=32, skipped=0, failures=0, errors=0).items())
    prefix = 'build/literature-review/signac-runtime/'
    original = json.loads(files[prefix + 'peaks-split-linux-original-1/COMPLETED.json'])
    fixture = json.loads(files['current-source/src/test/resources/org/jlinalg/sequence/signac-peak-preparation/original-cases.json'])
    assert len(original['cases']) == proof['original_split_cases'] == 7
    assert fixture['cases'] == original['cases']
    assert original['source_editions']['1.9.0'] == original['source_editions']['1.14.0'] == original['source_editions']['1.17.1']
    for name, case in original['cases'].items():
        for group in case['source_names']:
            actual = files[prefix + 'peaks-split-linux-original-1/' + name + '/split/' + group + '.bed']
            assert actual.hex() == case['expected_hex'][group]
            assert actual.replace(b'\n', b'\r\n').hex() == case['original_Windows_Rcpp_hex'][group]
    for key in ['actual_MACS_peak_fits', 'MACS_runtime_admitted', 'complete_CallPeaks_workflow',
                'root_dispatch_added', 'catalog_implementation_credit', 'full_Linux_Signac_runtime',
                'historical_whole_packages_executed', 'installed_DLL_source_authentication',
                'biological_accuracy_certified', 'full_paper_reproduction', 'full_project_test_suite_run',
                'released', 'pushed', 'deployed', 'whole_goal_complete']:
        assert proof[key] is False, key
    assert proof['native_grouped_fragment_preparation'] and proof['original_Windows_newline_difference_preserved']
    assert proof['catalog_counts'] == dict(papers=250, tools=387, observations=2444,
                                          new_scoped_integrations=70, including_existing=73, missing=258)
    assert proof['Signac_catalog_status'] == 'missing'
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_preserved']
    artifact = ROOT / proof['artifact']['path']
    if artifact.exists():
        assert pin(artifact) == {k: proof['artifact'][k] for k in ['bytes','sha256']}
        parent = ROOT / 'build/literature-review/signac-runtime/differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
        assert pin(parent) == proof['parent']
        with zipfile.ZipFile(artifact) as current, zipfile.ZipFile(parent) as old:
            assert not proof['changed_parent_entries']
            assert all(current.read(n) == old.read(n) for n in old.namelist())
            assert sorted(set(current.namelist()) - set(old.namelist())) == proof['new_class_entries']
            assert all(current.read(n) == files['actual-new-classes/' + n] for n in proof['new_class_entries'])
    print(json.dumps(dict(status='passed-offline-native-grouped-peak-preparation',
                          source_pins=len(proof['source_pins']), logical_files=proof['logical_files'],
                          original_split_cases=7, actual_MACS_peak_fits=False,
                          catalog_implementation_credit=False)))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('SIGNAC PEAK PREPARATION EVIDENCE FAIL2: ' + str(error) +
              '\nRequired action: preserve the evidence, correct the named source/archive mismatch and repeat verification. '
              'Do not label preparation files or partial outputs as admitted peak analyses.', file=sys.stderr)
        sys.exit(2)
