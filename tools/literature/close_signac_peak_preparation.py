# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Save native grouped-peak preparation; no MACS runtime or whole-tool admission."""
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack


def main():
    base = ROOT / 'build/literature-review/signac-runtime'
    folder = ROOT / 'docs/literature/signac-peak-preparation-development'
    assert not folder.exists()
    matrix = json.loads((base / 'peaks-preparation-native-matrix-1/COMPLETED.json').read_bytes())
    example = json.loads((base / 'peaks-preparation-example-1/COMPLETED.json').read_bytes())
    assert matrix['tests_per_JVM'] == 32 and len(matrix['JVMs']) == 3 and not matrix['changed_parent_entries']
    assert len(example['JVMs']) == 3 and all(r['existing_output_preserved'] for r in example['JVMs'])
    assert all(pin(ROOT / n) == v for p in [matrix, example] for n, v in p['source_pins'].items())
    junit = ROOT / 'build/test-results/test/TEST-org.jlinalg.cli.SignacPeakPreparationTest.xml'
    attributes = ET.parse(junit).getroot()
    assert all(int(attributes.get(k)) == v for k, v in dict(tests=32, skipped=0, failures=0, errors=0).items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == matrix['canonical_executable_preserved']
    fixture = ROOT / 'src/test/resources/org/jlinalg/sequence/signac-peak-preparation'
    parent_proof = ROOT / 'docs/literature/seurat-differential-development/VALIDATION.json'
    parent = json.loads(parent_proof.read_bytes())
    assert pin(ROOT / parent['artifact']['path']) == matrix['parent']
    catalog = ROOT / 'docs/literature/omics-2023-2026'
    summary = json.loads((catalog / 'summary.json').read_bytes())
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations']) == (250, 387, 2444)
    assert summary['implementation_status_counts']['missing'] == 258
    names = [ROOT / 'src/main/java/org/jlinalg/sequence/SignacPeakPreparation.java',
             ROOT / 'src/test/java/org/jlinalg/cli/SignacPeakPreparationTest.java',
             ROOT / 'docs/literature/SIGNAC_PEAK_PREPARATION_DEVELOPMENT.md',
             *fixture.iterdir(), *(ROOT / 'examples/signac-peak-preparation').iterdir()]
    names += [ROOT / 'tools/literature' / name for name in [
        'close_signac_peak_preparation.py', 'verify_signac_peak_preparation.py',
        'freeze_signac_peak_preparation_fixtures.py', 'collect_signac_peak_preparation.R',
        'collect_signac_peak_split_linux.py', 'validate_signac_peak_preparation.py',
        'validate_signac_peak_preparation_example.py', 'signac-peak-preparation-development.init.gradle']]
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in names if p.is_file()}
    files = {'current-source/' + n: (ROOT / n).read_bytes() for n in sources}
    for family in ['peaks-preparation-original-1', 'peaks-preparation-original-2',
                   'peaks-split-linux-original-1', 'peaks-preparation-native-matrix-1',
                   'peaks-preparation-example-1']:
        for p in (base / family).rglob('*'):
            if p.is_file():
                files[p.relative_to(ROOT).as_posix()] = p.read_bytes()
    files['normal-Gradle-tests.xml'] = junit.read_bytes()
    files['parent-VALIDATION.json'] = parent_proof.read_bytes()
    with zipfile.ZipFile(ROOT / matrix['artifact']['path']) as archive:
        for name in matrix['added_entries']:
            files['actual-new-classes/' + name] = archive.read(name)
    warning = '''# PREPARATION ONLY: NO MACS PEAK OR SIGNIFICANCE RESULT

This checkpoint implements original Linux grouped-fragment splitting only. No MACS statistical peak call, complete CallPeaks capture/VERIFY interface or catalog credit is admitted. The ongoing private MACS setup work is separate from this checkpoint.

Review SOURCE_GROUP_NAME_SUBSTITUTION and SOURCE_GROUP_LABEL_COLLISION: spaces/slashes become underscores, and colliding source labels pool fragments. Every original label remains represented. Read support never multiplies records; duplicates and unterminated-file appending remain exact source bytes. A joined malformed record is not silently repaired or admitted downstream.

Native results match original Linux C++ bytes. Original Windows C++ text translation inserts CR before LF and is retained as a distinct platform behavior, not claimed identical. The Linux harness stubs only Rcpp progress/interrupt callbacks; it is not a whole Linux Signac installation. Malformed inputs, exhausted bounds and cancellation raise exceptions and preserve owned partials. Keep inputs and attempts; correct the named cause and choose NEW output paths. Existing output directories are refused.

Missing/forbidden downstream MACS requirements mean SKIP3 and skip dependent peak analysis. Supply exact permitted runtimes in private paths without modifying upstream source/shared libraries. Setup alone is not user-analysis admission. This native API itself needs Java only and provides no automatic installation or scientific capture authentication.

Read SIGNAC_PEAK_PREPARATION_DEVELOPMENT.md and examples/signac-peak-preparation/README.md. Signac remains missing;70new scoped integrations,73including existing adapters and258missing. Cell Ranger stays excluded. No full Signac, historical whole-package, biological/full-paper validation, full project tests, release, push, deployment or whole-goal completion is claimed.
'''
    folder.mkdir()
    (folder / '.gitattributes').write_text('* -text whitespace=cr-at-eol\n', encoding='utf8')
    (folder / 'WARNINGS.md').write_text(warning, encoding='utf8', newline='\n')
    layout = pack(folder / 'controls.zip', files)
    result = dict(schema_version=1, status='passed-native-grouped-peak-preparation-not-MACS-workflow',
        source_pins=sources, archive=pin(folder / 'controls.zip'), warnings_document=pin(folder / 'WARNINGS.md'),
        **layout, artifact=matrix['artifact'], parent=matrix['parent'],
        changed_parent_entries=matrix['changed_parent_entries'], new_class_entries=matrix['added_entries'],
        canonical_executable_preserved=matrix['canonical_executable_preserved'],
        normal_Gradle_tests=32, actual_JAR_tests_per_JVM=32, JVMs=matrix['JVMs'],
        original_split_cases=7, compression_variants_per_case=2, original_Windows_newline_difference_preserved=True,
        matching_unchanged_cpp_editions=['1.9.0','1.14.0','1.17.1'], synthetic_example_JVMs=3,
        catalog_counts=dict(papers=250, tools=387, observations=2444, new_scoped_integrations=70, including_existing=73, missing=258),
        catalog_snapshot_pins={n:pin(catalog/n) for n in ['summary.json','tool-inventory.json','papers.json','SHA256SUMS']},
        Signac_catalog_status='missing', native_grouped_fragment_preparation=True,
        actual_MACS_peak_fits=False, MACS_runtime_admitted=False, complete_CallPeaks_workflow=False,
        root_dispatch_added=False, catalog_implementation_credit=False, full_Linux_Signac_runtime=False,
        historical_whole_packages_executed=False, installed_DLL_source_authentication=False,
        biological_accuracy_certified=False, full_paper_reproduction=False, full_project_test_suite_run=False,
        released=False, pushed=False, deployed=False, whole_goal_complete=False)
    assert all(pin(ROOT / n) == value for n, value in sources.items())
    save(folder / 'VALIDATION.json', result)
    print(json.dumps(dict(status=result['status'], source_pins=len(sources), archive=result['archive'], logical_files=layout['logical_files'])))


if __name__ == '__main__':
    main()
