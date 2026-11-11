# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete raw/input reference-table boundaries from all five original Salmon editions."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'build/literature-review/salmon-runtime/index-edge-controls-3'
OUT = ROOT / 'src/test/resources/org/jlinalg/cli/salmon-index-inputs'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete original-evidence assertions')
    proof = json.loads((SOURCE / 'COMPLETED.json').read_bytes())
    assert proof['status'] == 'completed-original-Salmon-index-edge-observations-with-explicit-discrepancy-status'
    assert len(proof['runs']) == 5 and not proof['user_analysis_admitted'] and not proof['no_quantification_discrepancies']
    assert all(pin(ROOT / name) == value for name, value in json.loads((SOURCE / 'SOURCE-PINS.json').read_bytes()).items())
    assert not OUT.exists()
    OUT.mkdir(parents=True)
    archive = OUT / 'original-index-inputs.zip'
    files, cases = {}, []
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for run in proof['runs']:
            version = run['version']
            observed = json.loads((SOURCE / version / 'OBSERVED.json').read_bytes())
            assert run['process']['exit_code'] == 0 and pin(SOURCE / version / 'OBSERVED.json') == run['observation']
            for row in observed['cases']:
                if row['id'] not in ['mixed-default', 'mixed-keep', 'gencode-keep', 'ambiguous-repeat-a', 'ambiguous-repeat-b']:
                    continue
                name, area = version + '/' + row['id'], SOURCE / version / row['id']
                process = json.loads((area / 'index-process/process.json').read_bytes())
                assert row['index_exit'] == 0 and process['exit_code'] == 0 and process['owned_live_after_cleanup'] == []
                raw_fasta = (area / 'inputs/reference.fa').read_text(encoding='utf-8')
                raw_count = sum(line.startswith('>') for line in raw_fasta.splitlines())
                case = dict(name=name, version=version, case=row['id'], raw_references=raw_count,
                            retained_names=[r['name'] for r in row['references']], original_lengths=[r['original_length'] for r in row['references']],
                            normalized_lengths=[r['normalized_length'] for r in row['references']], positions=len(row['positions']),
                            source_population_discrepancy=row.get('quant_audit') == 'source-boundary-discrepancy-retained-no-user-result')
                cases.append(case)
                for relative in ['inputs/reference.fa', 'inputs/decoys.txt', 'index/ctable.bin', 'index/complete_ref_lens.bin', 'index/reflengths.bin',
                                 'index/info.json', 'index/versionInfo.json', 'index/duplicate_clusters.tsv', 'index-process/process.json']:
                    path = area / relative
                    if relative == 'inputs/decoys.txt' and not path.exists():
                        continue
                    member = name + '/' + relative
                    target.writestr(member, path.read_bytes())
                    files[member] = pin(path)
    assert len(cases) == 25 and sum(c['source_population_discrepancy'] for c in cases) == 15
    provenance = dict(status='complete-original-input-index-boundaries-not-user-expression-results', cases=cases, archive=pin(archive), files=files,
                      producer=pin(Path(__file__)), source_completion=pin(SOURCE / 'COMPLETED.json'), source_artifact=proof['artifact'],
                      scope='Five controlled input populations per original edition; repeated versions are not independent biological datasets. '
                            'The full original graph/index census remains in the source observation bundle. This compact fixture preserves complete raw FASTA/decoys and '
                            'all three reference tables with metadata and original index process evidence. Fifteen requested-expression population discrepancies remain unadmitted.',
                      user_expression_analysis_admitted=False)
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    (OUT / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(cases=len(cases), files=len(files), archive=pin(archive))))


if __name__ == '__main__':
    main()
