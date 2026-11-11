# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve unchanged original five-edition alignment-protocol inputs for native admission tests."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'build/literature-review/salmon-runtime/reference-protocols-3'
OUT = ROOT / 'src/test/resources/org/jlinalg/cli/salmon-alignment-inputs'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable original input provenance checks')
    proof = json.loads((SOURCE / 'COMPLETED.json').read_bytes())
    assert proof['status'] == 'passed-25-original-Salmon-mapping-alignment-bias-and-inferential-protocols'
    assert not OUT.exists()
    names = ['alignments.sam', 'transcripts.fa', 'population.json']
    assert all(pin(SOURCE / 'inputs' / name) == proof['input_pins'][name] for name in names)
    rows = [line.split('\t') for line in (SOURCE / 'inputs/alignments.sam').read_text(encoding='ascii').splitlines() if not line.startswith('@')]
    groups = [r[0] for i, r in enumerate(rows) if i == 0 or r[0] != rows[i - 1][0]]
    assert len(groups) == len(set(groups)) and len(rows) % 2 == 0
    OUT.mkdir(parents=True)
    archive = OUT / 'original-alignment-inputs.zip'
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name in names:
            target.writestr(name, (SOURCE / 'inputs' / name).read_bytes())
    provenance = dict(status='original-alignment-inputs-not-independent-biological-validation', archive=pin(archive), files={n: proof['input_pins'][n] for n in names},
                      source_completion=pin(SOURCE / 'COMPLETED.json'), producer=pin(Path(__file__)), physical_records=len(rows), contiguous_query_groups=len(groups),
                      complete_reference_population=sum(line.startswith('>') for line in (SOURCE / 'inputs/transcripts.fa').read_text().splitlines()),
                      scope='Unchanged controlled transcriptome SAM/FASTA used by five original alignment-mode quantifications. A BAM written from this SAM in tests is a conversion control, '
                            'not an additional original Salmon experiment. Native input census is separate from inference, bias models and result admission.')
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    (OUT / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps({k: provenance[k] for k in ['archive', 'physical_records', 'contiguous_query_groups', 'complete_reference_population']}))


if __name__ == '__main__':
    main()
