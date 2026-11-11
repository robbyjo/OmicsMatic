# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original short-reference/decoy disagreements and exact no-decoy uint64 metadata."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'build/literature-review/salmon-runtime/index-edge-controls-1'
OUT = ROOT / 'src/test/resources/org/jlinalg/cli/salmon-index-boundaries'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable boundary evidence checks')
    assert not OUT.exists()
    assert json.loads((SOURCE / '1.8.0.json').read_bytes())['exit_code'] == 1
    assert 'invalid JSON' in (SOURCE / '1.8.0.stderr').read_text()
    out = OUT
    out.mkdir(parents=True)
    archive = out / 'original-boundaries.zip'
    files = {}
    cases = []
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name in ['mixed-default', 'mixed-keep', 'gencode-keep']:
            area = SOURCE / '1.8.0-FAILED' / name
            observation = json.loads((area / 'OBSERVATION.json').read_bytes())
            assert observation['index_exit'] == 0 and observation['quant_audit'] == 'source-boundary-discrepancy-retained-no-user-result'
            for kind in ['index', 'quant']:
                process = json.loads((area / (kind + '-process/process.json')).read_bytes())
                assert process['exit_code'] == 0 and process['owned_live_after_cleanup'] == [] and process['status'] == 'completed-process-only'
            for relative in ['inputs/reference.fa', 'inputs/decoys.txt', 'index/ctable.bin', 'index/complete_ref_lens.bin', 'index/reflengths.bin',
                             'index/info.json', 'index/duplicate_clusters.tsv', 'quant/quant.sf', 'quant/aux_info/meta_info.json', 'quant/cmd_info.json',
                             'quant/lib_format_counts.json', 'index-process/process.json', 'quant-process/process.json', 'OBSERVATION.json']:
                path = area / relative
                member = name + '/' + relative
                target.writestr(member, path.read_bytes())
                files[member] = pin(path)
            observed = [line.split('\t')[0] for line in (area / 'quant/quant.sf').read_text().splitlines()[1:]]
            requested = observation['expected_nondecoy_names']
            cases.append(dict(name=name, declared_nondecoys=requested, original_output_nondecoys=observed, omitted_declared_names=[n for n in requested if n not in observed],
                              original_runtime_num_decoys=observation['quant_meta']['num_decoy_targets']))
        for relative in ['index/info.json', 'index/ctable.bin', 'index/complete_ref_lens.bin', 'index/reflengths.bin', 'inputs/reference.fa', 'index-process/process.json']:
            path = SOURCE / '1.8.0-FAILED/ambiguous-repeat-a' / relative
            member = 'no-decoys/' + relative
            target.writestr(member, path.read_bytes())
            files[member] = pin(path)
    no_decoy = json.loads((SOURCE / '1.8.0-FAILED/ambiguous-repeat-a/index/info.json').read_bytes())
    assert no_decoy['num_decoys'] == 0 and no_decoy['first_decoy_index'] == (1 << 64) - 1
    proof = dict(status='original-source-boundary-disagreements-retained-never-admitted-as-requested-population', version='1.8.0', cases=cases,
                 source_probe_status='terminal failure in a later no-decoy metadata read; earlier original index/quant processes exited zero but requested-population audits failed',
                 producer=pin(Path(__file__)), archive=pin(archive), archive_files=files, source_fixture=pin(SOURCE / 'fixture-spec.json'),
                 source_artifact=pin(ROOT / 'build/literature-review/salmon-runtime/index-reference-development-1/package-1/omicsmatic-0.3.7-salmon-development.jar'),
                 no_decoy_first_boundary=str(no_decoy['first_decoy_index']), user_analysis_admitted=False,
                 required_action='Keep protected declared decoys and original runtime assignments separate. Source classification precedes updating the current short-reference count. '
                                 'A short nondecoy at the boundary is omitted by the original quantifier. Do not silently relabel it as a requested decoy or fabricate a zero row. '
                                 'Source-as-run arithmetic and complete requested-population coverage are different claims.')
    (out / 'provenance.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8', newline='\n')
    (out / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(cases=len(cases), omitted_declared_names=[c['omitted_declared_names'] for c in cases], archive=pin(archive))))


if __name__ == '__main__':
    main()
