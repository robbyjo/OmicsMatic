# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline source/archive verification of the summary foundation; never install/refit."""
import hashlib,json,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack

def main():
    folder=ROOT/'docs/literature/seurat-marker-summary-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-six-edition-marker-summary-foundation-not-Signac-or-Seurat-whole-tool' and pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for n,v in p['source_pins'].items():
        assert pin(ROOT/n)==v,n;raw=files['current-source/'+n];assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==v,n
    assert (p['normal_Gradle_tests'],p['actual_JAR_tests_per_JVM'],p['source_summary_comparisons'],p['controlled_p_callback_postprocessing_matches'],p['explicit_missing_identity_refusals'],p['exact_detection_rounding_values'])==(165,165,162,156,6,2638)
    assert len(p['JVMs'])==3 and len(p['source_profiles'])==6 and p['current_installed_source_function_body_and_formals_matches']==42 and p['source_statistical_reference_requests']==6 and p['distinct_original_nonfinite_transport_probe_passed'] and p['prior_R_library_files_unchanged']==27958 and not p['changed_parent_entries']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_preserved']
    for key in ['native_hypothesis_test_implementation','public_original_runtime_request_interface','root_dispatch_added','catalog_implementation_credit','historical_whole_packages_executed','Linux_R_fitting_executed','installed_DLL_source_authentication','biological_accuracy_certified','full_paper_reproduction','full_project_test_suite_run','released','pushed','deployed','whole_goal_complete']:assert p[key] is False
    artifact=ROOT/p['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:p['artifact'][k] for k in ['bytes','sha256']}
    print(json.dumps(dict(status='passed-offline-native-marker-summary-foundation',source_pins=len(p['source_pins']),logical_files=p['logical_files'],native_hypothesis_test_implementation=False,catalog_implementation_credit=False)))

if __name__=='__main__':
    try:main()
    except Exception as e:
        print('MARKER SUMMARY EVIDENCE FAIL2: '+str(e)+'\nRequired action: preserve evidence, correct named source/archive mismatch and repeat verification. Do not label summaries as admitted differential results.',file=sys.stderr);sys.exit(2)
