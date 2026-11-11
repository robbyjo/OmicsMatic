# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only frozen consensus source/artifact/evidence verification; does not repeat scientific analysis."""
import hashlib, json, stat, sys, zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT, pin


def main():
    data = ROOT / 'docs/literature/nfcore-consensus-development'; proof = json.loads((data / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'PASS_NATIVE_ORIGINAL_CONSENSUS_COMPONENT_ONLY'
    assert proof['claims'] == dict(new_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80,
        including_three_existing=102, missing=244, nfcore_status='missing', full_nfcore_adapter=False,
        scientific_result_admitted=False, biological_accuracy_certified=False, whole_paper_reproduction=False, whole_goal_complete=False)
    expected = set(proof['artifacts']) | {'controls.zip', 'VALIDATION.json'}; seen = set()
    for line in (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1); assert name in expected and name not in seen and pin(data / name)['sha256'] == digest; seen.add(name)
    assert seen == expected
    for name, value in proof['artifacts'].items(): assert pin(data / name) == value
    assert pin(data / 'controls.zip') == proof['archive']
    for name, value in proof['source_pins'].items(): assert pin(ROOT / name) == value, ('Current source differs', name)
    for name, value in proof['catalog_unchanged'].items(): assert pin(ROOT / name) == value, ('Catalog differs', name)
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    old_dir = ROOT / 'docs/literature/nfcore-captured-development'; predecessor = old_dir / 'nfcore-chipseq121-audit-javac21.jar'
    assert pin(old_dir / 'VALIDATION.json') == proof['predecessor_proof'] and pin(predecessor) == proof['predecessor_artifact']
    controls = proof['controls']; cli = proof['cli']
    assert controls['status'] == 'PASS_COMPLETE_NATIVE_CONSENSUS_AGAINST_ORIGINAL_TOOLS_ONLY' and len(controls['cases']) == 12
    assert all(len(r['checks']) == 6 and all(c['passed'] for c in r['checks']) for r in controls['cases'])
    assert controls['original_full_peak_population_replayed_byte_exactly'] and controls['source_SAF_coordinate_convention_preserved']
    assert cli['status'] == 'PASS_PACKAGED_COMPLETE_NATIVE_CONSENSUS_AUDITS_ONLY' and cli['positive_platforms'] == 4 and cli['refusals'] == 6
    assert cli['full_reports_identical'] and cli['coordinate_warning_prominent']
    assert proof['packaged']['status'] == 'PASS_SIX_EXTRACTED_PACKAGED_COMPLETE_NATIVE_CONSENSUS_COMPARISONS'
    assert len(proof['packaged']['checks']) == 6 and all(r['exit_code'] == 0 for r in proof['packaged']['checks'])
    with zipfile.ZipFile(data / 'controls.zip') as z:
        infos = z.infolist(); names = [i.filename for i in infos]
        assert len(names) == len(set(names)) and set(names) == set(proof['archive_files']) | set(proof['archive_directories'])
        for item in infos:
            name = item.filename; path = PurePosixPath(name)
            assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            assert str(path) + ('/' if item.is_dir() else '') == name and not stat.S_ISLNK(item.external_attr >> 16)
            raw = z.read(item)
            if item.is_dir(): assert raw == b''
            else: assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == proof['archive_files'][name]
        for name, value in proof['source_pins'].items(): assert proof['archive_files']['sources/' + name] == value
        assert json.loads(z.read('evidence/consensus-controls-1/COMPLETED.json')) == controls
        assert json.loads(z.read('evidence/consensus-cli-controls-2/COMPLETED.json')) == cli
        reports = [json.loads(z.read('evidence/consensus-cli-controls-2/' + n + '/audit.json')) for n in ['windows21', 'windows25', 'linux21-compiler21', 'linux21-compiler25']]
        assert all(r == reports[0] for r in reports) and reports[0]['nfcore_tool_credit'] == 0 and not reports[0]['scientific_result_admitted']
        consensus = reports[0]['antibodies'][0]['native_consensus']; assert consensus['source_peak_occurrences'] == 3357 and consensus['retained_consensus_intervals'] == 1472
        for platform in ['windows21', 'windows25']:
            warning = z.read('evidence/consensus-cli-controls-2/' + platform + '/WARNINGS.md')
            assert b'zero-based Start directly to SAF' in warning and b'separate declared analysis' in warning
            failure = json.loads(z.read('evidence/consensus-cli-controls-2/' + platform + '-wrong-kind/FAILURE.json'))
            assert failure['status'] == 'FAIL2_NO_CONSENSUS_COMPONENT_RESULT' and 'narrowPeak' in failure['error'] and 'Required action:' in failure['error']
        for compiler in ['21', '25']:
            jar = data / ('nfcore-consensus-javac' + compiler + '.jar'); assert proof['artifacts'][jar.name] == cli['artifacts'][compiler]
            with zipfile.ZipFile(predecessor) as old, zipfile.ZipFile(jar) as current:
                assert all(current.read(n) == old.read(n) for n in old.namelist() if n != 'META-INF/MANIFEST.MF')
                for name, value in controls['native_classes'][compiler].items():
                    raw = current.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    print('PASS native original consensus:3357 full peaks;1472 intervals;12 source cases;4 packaged audits;6 extracted comparisons;0 tool credit.', flush=True)
    return 0


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable frozen consensus source, artifact, scope or evidence checks')
    try: raise SystemExit(main())
    except Exception as error:
        print('FAIL2 NO CONSENSUS COMPONENT RESULT: ' + repr(error) + '\nRequired action: preserve the original checkpoint, inspect the named discrepancy and make a new validated checkpoint after correction. Do not rewrite old hashes or repair original coordinates to hide a failure.', file=sys.stderr)
        raise SystemExit(2)
