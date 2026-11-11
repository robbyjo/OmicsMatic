# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only source, artifact, archive and claim verification for the nf-core foundation checkpoint."""
import hashlib, json, stat, sys, zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT, pin


def main():
    data = ROOT / 'docs/literature/nfcore-engine-development'
    checksums = {}
    for line in (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines():
        identity, name = line.split('  ', 1)
        assert name not in checksums and PurePosixPath(name).name == name
        assert pin(data / name)['sha256'] == identity, ('Checkpoint checksum differs', name)
        checksums[name] = identity
    proof = json.loads((data / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'PASS_NFCORE_ENGINE_FOUNDATION_ONLY_NO_TOOL_CREDIT'
    assert proof['claims'] == dict(new_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80,
        including_three_existing=102, missing=244, reviewed_roles=1049, paper_tool_observations=2476,
        nfcore_status='missing', source_role_drafts=6, full_nfcore_adapter=False, biological_accuracy=False,
        whole_paper_reproduction=False, whole_goal_complete=False)
    assert len(proof['matrix']['controls']) == 15 and all(r['passed'] for r in proof['matrix']['controls'])
    assert len(proof['integrity']['executions']) == 3 and len(proof['integrity']['saved_verification']) == 12
    assert all(r['passed'] for r in proof['integrity']['saved_verification'])
    assert len(proof['census']['sources']) == 250 and len(proof['census']['matching_paragraphs']) == 11
    assert len(proof['derivation']['records']) == 3
    for name, identity in proof['artifacts'].items(): assert pin(data / name) == identity
    assert pin(data / 'nextflow-engine-dependency.jar') == proof['helper_dependency']
    assert pin(data / 'controls.zip') == proof['archive']
    for name, identity in proof['source_pins'].items(): assert pin(ROOT / name) == identity, ('Current source differs from frozen foundation', name)
    for name, identity in proof['catalog_unchanged'].items(): assert pin(ROOT / name) == identity, ('Catalog changed; preserve this proof and assess a new checkpoint explicitly', name)
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    with zipfile.ZipFile(data / 'controls.zip') as z:
        infos = z.infolist(); names = [i.filename for i in infos]
        assert len(names) == len(set(names))
        assert set(names) == set(proof['archive_files']) | set(proof['archive_directories'])
        for item in infos:
            name = item.filename; path = PurePosixPath(name)
            assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            assert str(path) + ('/' if item.is_dir() else '') == name
            assert not stat.S_ISLNK(item.external_attr >> 16)
            raw = z.read(item)
            if item.is_dir(): assert raw == b''
            else: assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == proof['archive_files'][name]
        for name, identity in proof['source_pins'].items():
            assert proof['archive_files']['sources/' + name] == identity
        matrix = json.loads(z.read('evidence/engine-controls-5/COMPLETED.json'))
        integrity = json.loads(z.read('evidence/engine-integrity-2/COMPLETED.json'))
        assert matrix == proof['matrix'] and integrity == proof['integrity']
        # Recomputed command/manifest hashes are insufficient to bypass exact command semantics.
        for platform in ['windows21', 'windows25']:
            error = z.read('evidence/engine-integrity-2/resealed-command-order-' + platform + '.stderr')
            assert b'FAIL2' in error and b'original command' in error
    print('PASS nf-core foundation:15 engine controls;3 cross-compiler executions;12 saved-result controls;250 paper sources;0 new tool credit.', flush=True)
    return 0


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable frozen source, archive or claim checks')
    try: raise SystemExit(main())
    except Exception as error:
        print('FAIL2 nf-core foundation verification: ' + repr(error) + '\nRequired action: no result admitted. Preserve the checkpoint, inspect the named hash or contract discrepancy and use a new validated checkpoint after correction. Never rewrite historical evidence to hide a mismatch.', file=sys.stderr)
        raise SystemExit(2)
