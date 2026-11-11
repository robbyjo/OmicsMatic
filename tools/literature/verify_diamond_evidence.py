# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete frozen DIAMOND source controls and typed admission relationships offline."""
import argparse, collections, io, json, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source
HOME = ROOT/'docs/literature/diamond-development'
STATUS = 'PASS_FROZEN_DIAMOND_ORIGINAL_EXECUTION_AND_NATIVE_TRANSPORT_EVIDENCE'


def verify(home=HOME):
    home = Path(home)
    proof = json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status'] == STATUS and proof['new_catalog_credit'] is False and proof['whole_goal_complete'] is False
    for name, value in proof['source_pins'].items():
        if name == 'src/main/java/org/jlinalg/cli/JLinAlgCli.java':
            verify_source(ROOT, ROOT/'docs/literature/omics-2023-2026', name, value['sha256'])
        else:
            assert pin(ROOT/name) == value, ('Changed scientific/component source', name)
    archive = home/'controls.zip'
    assert pin(archive) == proof['archive']
    files = unpack(archive, proof)
    base = 'build/literature-review/diamond-runtime/'

    def obj(name):
        return json.loads(files[base+name])

    assert (proof['original_cases'], proof['successful_source_cases'], proof['preserved_original_failure_cases']) == (78, 72, 6)
    original = obj('reference-controls-2/COMPLETED.json')
    assert original['status'] == 'PASS_COMPLETE_ORIGINAL_DIAMOND_REFERENCE_DISPOSITIONS'
    assert original['successful_cases'] == 72 and original['original_failure_cases'] == 6
    assert len(original['ordinary_output_population_checks']) == 6
    assert all(r['full_population_equal'] for r in original['ordinary_output_population_checks'])
    lock = json.loads(files['current-source/tools/literature/diamond-source-lock.json'])
    assert len(lock['output_fields']) == 14
    for version, row in lock['versions'].items():
        assert byte_pin(files[base+f'source-intake-1/{version}/original-source.zip']) == row['source_archive']
        assert obj(f'source-intake-1/{version}/COMPLETED.json')['analysis_admitted'] is False
    seen, admitted, failed = set(), 0, 0
    for case in proof['cases']:
        version, name, home_name = case['version'], case['case'], case['home']
        assert (version, name) not in seen
        seen.add((version, name))
        saved = json.loads(files[home_name+'/VALIDATION.json'])
        direct = obj(f'reference-controls-2/{version}/{name}/execution/process.json')
        done = json.loads(files[home_name+'/analysis/COMPLETED.json'])
        assert saved['original_exit'] == direct['exit_code'] == case['original_exit']
        assert saved['adapter_exit'] == case['adapter_exit'] and done['result_admitted'] is (case['adapter_exit'] == 0)
        if direct['exit_code'] == 0:
            admitted += 1
            raw = files[home_name+'/analysis/results/hits.tsv'].splitlines()
            expected = files[base+f'reference-controls-2/{version}/{name}/hits.tsv'].splitlines()
            assert collections.Counter(raw) == collections.Counter(expected)
            assert len(raw) == saved['rows'] and all(len(row.split(b'\t')) == 14 for row in raw)
            assert saved['complete_population_equal'] and saved['native_transport_replay']
            provenance = json.loads(files[home_name+'/analysis/provenance.json'])
            assert provenance['original_alignment_rows'] == len(raw) and provenance['native_coordinate_identity_statistic_checks']
            assert provenance['alignment_placement_and_Evalues_independently_refitted'] is False
            assert provenance['study_cutoff_reinterpretation_applied'] is False
            if name == 'long-exact-rounded-Evalue':
                assert provenance['source_reported_zero_Evalues'] > 0
                assert b'ORIGINAL ZERO E-VALUES' in files[home_name+'/analysis/WARNINGS.md']
        else:
            failed += 1
            assert case['adapter_exit'] == 2 and saved['original_failure_preserved_without_result']
            worker = json.loads(files[home_name+'/analysis/unadmitted-worker/search.process.json'])
            assert worker['exit_code'] == (128-direct['exit_code'] if direct['exit_code'] < 0 else direct['exit_code'])
            assert worker['owned_processes_terminal']
            assert b'FAIL2' in files[home_name+'/analysis/WARNINGS.md']
    assert (len(seen), admitted, failed) == (78, 72, 6)
    assert seen == {(r['version'], r['id']) for r in original['records']}
    assert obj('protocol-1/COMPLETED.json')['status'] == 'PASS_DIAMOND_PROTOCOL_SKIP_FAILURE_AND_REHASHED_REFUSALS'
    assert len(obj('protocol-1/COMPLETED.json')['records']) == 22
    setup = obj('packaged-setup-1/COMPLETED.json')
    assert setup['status'] == 'PASS_DIAMOND_PACKAGED_SETUP_AND_EXPLICIT_RUNTIME_DISPOSITIONS'
    assert setup['explicit_download'] and setup['downloaded_runtime_alignment_validated']
    existing = obj('adapter-existing-db-1/COMPLETED.json')
    assert existing['successful_alignments'] == 6 and all(r['provenance']['native_reference_population_checked'] is False for r in existing['records'])
    rooted = obj('root-validation-1/COMPLETED.json')
    assert rooted['status'] == 'PASS216_DIAMOND_ROOT_COMPLETE_CHECKS18_REFUSALS3_SKIPS15_PREDECESSORS3_ALIGNMENTS'
    assert (rooted['complete_root_bundle_checks'], rooted['failed_root_bundle_refusals'], rooted['actual_root_dependency_skips'], rooted['predecessor_complete_checks'], rooted['actual_root_original_alignments']) == (216, 18, 3, 15, 3)
    for name, value in proof['artifacts'].items():
        assert pin(home/name) == value
    for co in [21, 25]:
        with zipfile.ZipFile(home/f'diamond-root-javac{co}.jar') as routed, zipfile.ZipFile(home/f'diamond-javac{co}.jar') as standalone:
            for name in standalone.namelist():
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/diamond/'):
                    assert routed.read(name) == standalone.read(name)
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof, files


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable frozen DIAMOND evidence checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, default=HOME)
    args = parser.parse_args()
    report, _ = verify(args.home)
    print(report['status'])
