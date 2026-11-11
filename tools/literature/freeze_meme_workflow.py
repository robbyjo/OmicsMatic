# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the scoped MEME workflow only after original fits, empty outputs, recovery and root example pass."""
import argparse
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/literature'))
from validate_mashr_native import pin
from signac_scan_evidence import byte_pin, pack

BASE = ROOT/'build/literature-review/meme-runtime'
RUNS = ['workflow-build-11', 'workflow-build-13', 'workflow-build-16', 'root-build-17', 'workflow-contracts-16-2', 'user-workflows-11-1',
    'empty-user-workflows-16-1', 'root-modern-Windows-checks-17-1', 'root-example-17-1',
    'workflow-recovery-17-1', 'diagnostic-paths-after-13-1', 'text-metadata-source-audit-1',
    'health-explicit-attribute-intake-1', 'empty-original-source-13-1', 'empty-original-source-16-1', 'empty-fixture-intake-16-1']


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out == ROOT/'docs/literature/meme-discovery-workflow-development' and not out.exists()
    records = {name: json.loads((BASE/name/'COMPLETED.json').read_bytes()) for name in RUNS}
    backend, root = records['workflow-build-16'], records['root-build-17']
    assert root['parent_artifact'] == backend['artifact']
    contracts = records['workflow-contracts-16-2']
    assert contracts['tests_per_JVM'] == 91 and len(contracts['JVMs']) == 3 and all(r['exit_code'] == 0 and r['tests'] == 91 and r['skipped'] == 0 for r in contracts['JVMs'])
    profiles = {'MEME_4_9_1_BASE','MEME_4_9_1_PATCH2','MEME_5_5_4','MEME_5_5_9'}
    for name, empty in [('user-workflows-11-1',False), ('empty-user-workflows-16-1',True)]:
        row = records[name]
        assert set(row['profiles']) == profiles and row['original_analyses'] == 4 and row['portable_verifications'] == 12
        assert row.get('expected_zero_motifs', False) == empty
        for profile in profiles:
            folder = BASE/name/profile
            for action in ['discover','verify-linux']:
                assert json.loads((folder/(action+'.process.json')).read_bytes())['exit_code'] == 0
            assert json.loads((folder/'result/OUTPUT-AUDIT.json').read_bytes())['motifs'] == (0 if empty else 2)
    example = records['root-example-17-1']
    assert example['artifact'] == root['artifact'] and example['original_analyses'] == 1 and example['portable_verifications'] == 3
    assert records['root-modern-Windows-checks-17-1']['artifact'] == root['artifact']
    sources = list((ROOT/'src/main/java/org/jlinalg/cli').glob('MemeDiscovery*.java')) + [ROOT/'src/main/java/org/jlinalg/cli/MemeToolsCli.java',ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java']
    sources += [p for p in (ROOT/'src/main/resources/org/jlinalg/omics/meme-discovery').rglob('*') if p.is_file()]
    scripts = ['build_meme_discovery_candidate.py','build_meme_discovery_dispatch.py','run_meme_discovery_workflows.py',
        'validate_meme_discovery_contracts.py','validate_meme_discovery_recovery.py','validate_meme_discovery_root.py','validate_meme_discovery_example.py',
        'validate_meme_diagnostic_paths.py','audit_meme_text_metadata.py','prepare_meme_empty_contracts.py','collect_meme_no_motif_controls.py',
        'meme_discovery_workflow_evidence.py','freeze_meme_workflow.py']
    sources += [ROOT/'tools/literature'/name for name in scripts]
    sources += [ROOT/'tools/literature/java/MemeNoMotifSourceTrace.java', ROOT/'src/test/java/org/jlinalg/cli/MemeDiscoveryContractsTest.java',
        ROOT/'src/test/resources/org/jlinalg/sequence/meme-discovery-sites/original-site-contracts.zip',
        ROOT/'src/test/resources/org/jlinalg/sequence/meme-discovery-empty/original-empty-contracts.zip',
        ROOT/'examples/meme-discovery/create_example.py', ROOT/'examples/meme-discovery/README.md']
    pins = {p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    for proof in [backend,root,contracts,example]:
        for name,wanted in proof['source_pins'].items(): assert pin(ROOT/name) == wanted, name
    files = {'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    files['retained-attempts/INTERPRETATION.json'] = (json.dumps(dict(
        workflow_build_15='A source edit while the collector was active caused its source-pin guard to refuse admission. The candidate is retained as unadmitted build diagnostics.',
        workflow_contracts_16_1='Four new test fixtures attempted CREATE_NEW on an existing recipe. The harness was corrected; the same production artifact then passed all91 tests per JVM in workflow-contracts-16-2.',
        empty_source_13_1='Original source traces exposed that modern -evt overrides requested motif count2 to1000. These are retained non-analysis traces. The corrected explicit choice is source-controlled by empty-original-source-16-1 and full zero-motif workflows.',
        positive_fit_scope='Four positive weighted profile runs used the earlier source-bound workflow-build-11. Final native-science parent entries remain preserved; current full zero-motif runs include fresh positive health fits for every edition, and the final root example performs another positive user analysis.'),indent=2)+'\n').encode('utf8')
    for name in RUNS:
        for path in (BASE/name).rglob('*'):
            if path.is_file() and path.suffix != '.jar': files[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    # Preserve failed attempts as diagnostics, never as passing controls.
    for name in ['workflow-build-15','workflow-contracts-16-1']:
        for path in (BASE/name).rglob('*'):
            if path.is_file() and path.suffix != '.jar': files['retained-attempts/'+path.relative_to(ROOT).as_posix()] = path.read_bytes()
    chain = dict(native_parent=backend['parent_artifact'], backend=backend['artifact'], root=root['artifact'])
    entries = {}
    for label,artifact in chain.items():
        path = ROOT/artifact['path']; assert pin(path) == {k:artifact[k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(path) as archive:
            entries[label] = {n:dict(byte_pin(archive.read(n)), directory=archive.getinfo(n).is_dir()) for n in archive.namelist()}
            files['artifact-entries/'+label+'.json'] = (json.dumps(entries[label],sort_keys=True)+'\n').encode('utf8')
            if label == 'root':
                for name in set(entries['root'])-set(entries['native_parent']) | {'org/jlinalg/cli/JLinAlgCli.class'}:
                    files['candidate-owned-entries/'+name] = archive.read(name)
    assert set(entries['backend'])-set(entries['native_parent']) == set(backend['additions'])
    assert all(entries['native_parent'][n] == entries['backend'][n] for n in entries['native_parent'])
    assert entries['root'].keys() == entries['backend'].keys()
    assert [n for n in entries['root'] if entries['root'][n] != entries['backend'][n]] == ['org/jlinalg/cli/JLinAlgCli.class']
    out.mkdir()
    warning_source = ROOT/'docs/literature/MEME_DISCOVERY_WORKFLOW_DEVELOPMENT.md'
    (out/'WARNINGS.md').write_bytes(warning_source.read_bytes())
    layout = pack(out/'controls.zip', files)
    proof = dict(schema_version=1, status='passed-scoped-MEME-discovery-workflow-development', artifact=root['artifact'], artifact_chain=chain,
        source_pins=pins, archive=pin(out/'controls.zip'), warnings_document=pin(out/'WARNINGS.md'), **layout,
        tests_per_JVM=91, JVMs=3, original_positive_analyses=5, original_empty_analyses=4, portable_verifications=27,
        exact_profiles=sorted(profiles), parent_native_validation=pin(ROOT/'docs/literature/meme-discovery-sites-development/VALIDATION.json'),
        canonical_executable_unchanged=backend['canonical_executable_unchanged'],
        source_fit_independently_reimplemented=False, source_significance_independently_reimplemented=False, HTML_original_helper_replay=True,
        historical_whole_suite=False, whole_paper_reproduced=False, biological_validity_certified=False, execution_authentication=False,
        released=False, pushed=False, deployed=False, whole_goal_complete=False)
    assert all(pin(ROOT/n) == want for n,want in pins.items()); save(out/'VALIDATION.json', proof)
    from meme_discovery_workflow_evidence import verify
    verify(out)
    print(json.dumps(dict(status=proof['status'], artifact=proof['artifact'], archive=proof['archive'], logical_files=proof['logical_files'])))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source/artifact/evidence bindings')
    main()
