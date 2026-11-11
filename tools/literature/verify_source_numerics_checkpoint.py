# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity/scope verification; does not rerun original software or scientific analysis."""
import hashlib, json, stat, sys, zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT, pin


def main():
    data = ROOT / 'docs/literature/source-numerics-development'; proof = json.loads((data / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'PASS_NATIVE_LOWESS_AND_DEFAULT_FILTER_ONLY'
    assert proof['claims'] == dict(new_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80, including_three_existing=102,
        missing=244, nfcore_status='missing', full_nfcore_adapter=False, fitted_model_admitted=False, whole_pipeline_admitted=False,
        biological_accuracy_certified=False, whole_paper_reproduction=False, whole_goal_complete=False)
    expected = set(proof['artifacts']) | {'controls.zip', 'VALIDATION.json'}; seen = set()
    for line in (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1); assert name in expected and name not in seen and pin(data / name)['sha256'] == digest; seen.add(name)
    assert seen == expected
    for name, identity in proof['artifacts'].items(): assert pin(data / name) == identity
    assert pin(data / 'controls.zip') == proof['archive']
    for name, identity in proof['source_pins'].items(): assert pin(ROOT / name) == identity, ('Current numerical source/reference differs', name)
    for name, identity in proof['catalog_unchanged'].items(): assert pin(ROOT / name) == identity, ('Catalog differs', name)
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    assert pin(ROOT / 'docs/literature/nfcore-consensus-development/VALIDATION.json') == proof['predecessor_proof']
    assert len(proof['lowess']['cases']) == 17 and len(proof['lowess']['platforms']) == 6
    assert len(proof['filtering']['cases']) == 12 and len(proof['filtering']['platforms']) == 6
    assert proof['filtering']['actual_hypotheses'] == 1472 and proof['filtering']['quantile_BH_families_per_population'] == 50
    assert proof['lowess']['actual_complete_deseq126_filter_curve'] and proof['filtering']['missing_and_NaN_preserved'] and proof['filtering']['strict_source_alpha_boundary_preserved']
    assert {k: proof['junit'][k] for k in ['tests', 'failures', 'errors', 'skipped']} == dict(tests=32, failures=0, errors=0, skipped=0)
    assert proof['packaged']['status'] == 'PASS_TWELVE_PACKAGED_NATIVE_REFERENCE_REPLAYS_ONLY'
    assert len(proof['packaged']['checks']) == 12 and all(r['exit_code'] == 0 and r['complete_reference'] for r in proof['packaged']['checks'])
    assert not proof['packaged']['external_R_or_native_runtime_used']
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
        for name, identity in proof['source_pins'].items(): assert proof['archive_files']['sources/' + name] == identity
        assert json.loads(z.read('evidence/lowess-controls-2/COMPLETED.json')) == proof['lowess']
        assert json.loads(z.read('evidence/independent-filter-controls-2/COMPLETED.json')) == proof['filtering']
        failed = json.loads(z.read('evidence/lowess-controls-1/COMPLETED.json')); assert failed['status'].startswith('FAIL2_') and b'identical(expected, m$lo.fit)' in z.read('evidence/lowess-controls-1/original-r362.stderr')
        for compiler in ['21', '25']:
            with zipfile.ZipFile(data / ('source-numerics-javac' + compiler + '.jar')) as jar:
                assert b'Main-Class:' not in jar.read('META-INF/MANIFEST.MF')
                for block in ['lowess', 'filtering']:
                    for name, identity in proof[block]['native_classes'][compiler].items():
                        raw = jar.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == identity
                for name in ['GPL-3', 'LGPL-3', 'Artistic-2.0', 'NOTICE.txt']: assert jar.read('META-INF/source-numerics/' + name)
                for name in ['src/main/java/org/jlinalg/loess/RLowess.java', 'src/main/java/org/jlinalg/sequence/Deseq2IndependentFiltering.java', 'src/main/java/org/jlinalg/sequence/Deseq2SizeFactors.java']:
                    assert jar.read('META-INF/source-numerics/sources/' + name) == (ROOT / name).read_bytes()
    print('PASS source-specific native numerics:17 LOWESS curves;12 complete filter populations;50 families each;32 JUnit tests;12 packaged replays;0 tool credit.', flush=True)
    return 0


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable frozen numerical source, reference or scope checks')
    try: raise SystemExit(main())
    except Exception as error:
        print('FAIL2 NO NUMERICAL CHECKPOINT ADMISSION: ' + repr(error) + '\nRequired action: preserve historical evidence, inspect the named discrepancy and use a new validated checkpoint after correction. Never alter original values, omit hypotheses or rewrite old hashes to hide a failure.', file=sys.stderr)
        raise SystemExit(2)
