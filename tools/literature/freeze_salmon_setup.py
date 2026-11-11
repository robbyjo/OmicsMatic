# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze additive Salmon table/setup audits while leaving public integration uncredited."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'


def read(path):
    return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable checkpoint integrity checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'docs/literature').resolve()) and not out.exists()
    native_folder = BASE / 'setup-native-development-1/matrix-1'
    quant_folder = BASE / 'quantification-development-1/matrix-1'
    setup_folder = BASE / 'managed-setup-development-2'
    policy_folder = BASE / 'installer-policy-matrix-1'
    boundary_folder = BASE / 'setup-source-boundary-1'
    native, quant, setup, policy, boundary = [read(p / 'COMPLETED.json') for p in [native_folder, quant_folder, setup_folder, policy_folder, boundary_folder]]
    assert native['status'] == 'passed-three-JVM-complete-portable-Salmon-setup-evidence-replay'
    assert len(native['runs']) == 3 and all(r['tests'] == r['passed'] == 139 and r['setup_tests'] == 52 for r in native['runs'])
    assert len(quant['runs']) == 3 and all(r['tests'] == r['passed'] == 87 for r in quant['runs'])
    assert len(setup['rechecks']) == 5 and setup['original_health_quantifications'] == 12
    assert len(setup['negative_controls']) == 4 and setup['existing_report_unchanged'] is True
    assert policy['status'] == 'passed-source-pinned-Windows-and-Linux-Salmon-installer-policy-controls'
    assert {r['name']: r['tests']['skips'] for r in policy['runs']} == {'windows': 2, 'linux': 0}
    assert boundary['status'] == 'passed-original-source-characterization-of-Salmon-100-empty-mapping-SHA512-links'
    assert pin(ROOT / native['artifact']['path']) == {k: native['artifact'][k] for k in ['bytes', 'sha256']}
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == native['canonical_executable_unchanged']
    assert native['unchanged_parent_classes'] == 17769 and len(native['new_classes']) == 6
    sources = {}
    for proof in [native, quant, setup, policy, boundary]:
        for name, value in proof['source_pins'].items():
            assert pin(ROOT / name) == value, name
            assert name not in sources or sources[name] == value, name
            sources[name] = value
    for name in ['tools/literature/build_salmon_runtime_lock.py', 'tools/literature/prepare_salmon_health_kit.py',
                 'tools/literature/salmon-runtime-lock.json', 'src/main/resources/org/jlinalg/omics/salmon-runtime-lock.json',
                 'docs/literature/SALMON_SETUP_EVIDENCE.md', Path(__file__).relative_to(ROOT).as_posix()]:
        sources[name] = pin(ROOT / name)
    assert (ROOT / 'tools/literature/salmon-runtime-lock.json').read_bytes() == (ROOT / 'src/main/resources/org/jlinalg/omics/salmon-runtime-lock.json').read_bytes()
    assert (ROOT / 'tools/literature/install_salmon.py').read_bytes() == (ROOT / 'src/main/resources/org/jlinalg/omics/salmon-runtime-setup/install_salmon.py').read_bytes()
    data = ROOT / 'docs/literature/omics-2023-2026'
    summary = read(data / 'summary.json')
    assert summary['papers'] == 250 and summary['paper_tool_observations'] == 2430 and summary['distinct_named_tools'] == 380
    assert summary['implementation_status_counts']['native-subset-validated'] == 15
    assert summary['implementation_status_counts']['upstream-adapter-validated'] == 49
    assert summary['implementation_status_counts']['missing'] == 256
    entries = {}

    def add(path, name):
        assert path.is_file() and not path.is_symlink() and name not in entries
        entries[name] = path

    def tree(folder, label):
        assert folder.is_dir()
        for path in sorted(folder.rglob('*')):
            if path.is_file():
                add(path, label + '/' + path.relative_to(folder).as_posix())

    for name in sources:
        add(ROOT / name, 'source/' + name)
    for folder, label in [(native_folder, 'native-setup-matrix'), (quant_folder, 'native-quantification-matrix'),
                          (policy_folder, 'installer-policy'), (boundary_folder, 'original-source-boundary')]:
        tree(folder, 'evidence/' + label)
    # The fixture ZIP already contains every successful report, including all
    # inputs/index/results. Retain current controller and negative-control files
    # separately, without duplicating the six complete successful report trees.
    successful = {'fresh-1.9.0', *('recheck-' + v for v in ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0'])}
    for path in sorted(setup_folder.rglob('*')):
        if path.is_file() and path.relative_to(setup_folder).parts[0] not in successful:
            add(path, 'evidence/managed-current/' + path.relative_to(setup_folder).as_posix())
    for name, tests in [('org.jlinalg.cli.SalmonSetupEvidenceTest', 52), ('org.jlinalg.sequence.SalmonQuantificationTest', 60), ('org.jlinalg.sequence.SalmonInferentialReplicatesTest', 27)]:
        path = ROOT / 'build/test-results/test' / ('TEST-' + name + '.xml')
        import xml.etree.ElementTree as ET
        result = ET.parse(path).getroot()
        assert int(result.attrib['tests']) == tests and all(int(result.attrib[k]) == 0 for k in ['failures', 'errors', 'skipped'])
        add(path, 'evidence/gradle/' + path.name)
    old_proof_path = ROOT / 'docs/literature/salmon-native-development/VALIDATION.json'
    old_proof = read(old_proof_path)
    for name, value in old_proof['source_pins'].items():
        assert pin(ROOT / name) == value, name
    add(old_proof_path, 'prior-checkpoint/VALIDATION.json')
    out.mkdir()
    (out / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    archive = out / 'controls.zip'
    inventory = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes()
            info = zipfile.ZipInfo(name, (2026, 11, 3, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            target.writestr(info, raw, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
            inventory[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as check:
        assert check.testzip() is None and len(check.infolist()) == len(set(check.namelist())) == len(inventory)
        for name, value in inventory.items():
            raw = check.read(name)
            assert len(raw) == value['bytes'] and hashlib.sha256(raw).hexdigest() == value['sha256']
    assert archive.stat().st_size < 100 * 1024 * 1024
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    result = dict(schema_version=1, status='passed-native-Salmon-table-and-portable-setup-audits-public-workflow-open',
                  recorded_utc=datetime.now(timezone.utc).isoformat(), artifact=native['artifact'],
                  source_pins=sources, archive=pin(archive), archive_entries=inventory,
                  native_matrix=native['runs'], prior_quantification_matrix=quant['runs'],
                  installer_policy_matrix=policy['runs'], source_boundary_cases=boundary['cases'],
                  current_original_health=dict(reports=6, exact_editions=5, quantifications=12, complete_rows=480, complete_bootstrap_values=36000,
                                               below_burn_in=True, one_synthetic_population_reused=True, independent_biological_datasets=False),
                  earlier_original_quantifications=dict(quantifications=25, complete_rows=1000, original_replicate_values=31600,
                                                       checkpoint='../salmon-native-development/VALIDATION.json', checkpoint_pin=pin(old_proof_path), changed=False),
                  complete_namespace_and_bytes_verified=True, unchanged_parent_classes=17769, new_classes=native['new_classes'],
                  canonical_executable_unchanged=native['canonical_executable_unchanged'],
                  claims=dict(native_complete_table_arithmetic_audit=True, explicit_python_setup_prototype=True, portable_native_setup_report_reader=True,
                              typed_public_quantification_command_available=False, scientific_runtime_admitted=False, user_analysis_admitted=False,
                              whole_index_semantics_certified=False, complete_public_quantification_output_admission=False,
                              Salmon_catalog_promoted=False, whole_paper_reproduced=False, biological_accuracy_certified=False, whole_goal_complete=False,
                              released_or_deployed=False),
                  catalog=dict(new_scoped_integrations=64, native_scopes=15, upstream_adapters=49, missing=256, papers=250, tools=380, observations=2430),
                  required_action='Skip dependent project quantification until native current-host admission and a typed complete public workflow pass. '
                                  'Keep original1.0.0 empty SHA-512 links and automatic IU versus observed ISR distinct. '
                                  'Retain burn-in/fallback warnings and the unresolved four-target positional-bias failures. '
                                  'SKIP3 means unavailable/forbidden dependency or capability: complete permitted setup or skip the analysis. '
                                  'FAIL2 means invalid input, changed evidence, original process, numerical/capacity/cancellation or finalization failure: admit no result. '
                                  'Inspect warnings, complete files and all process receipts; preserve partials and use NEW paths. '
                                  'No setup receipt or arithmetic prefix admits a user analysis. Cell Ranger remains excluded.')
    write(out / 'VALIDATION.json', result)
    (out / 'SHA256SUMS').write_text(pin(archive)['sha256'] + '  controls.zip\n' + pin(out / 'VALIDATION.json')['sha256'] + '  VALIDATION.json\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(status=result['status'], archive=pin(archive), entries=len(inventory), source_pins=len(sources), Salmon_catalog_status='missing')))


if __name__ == '__main__':
    main()
