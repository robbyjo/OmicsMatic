#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate independent Python-struct FCS storage controls; no analysis or dependencies.

Creates a NEW directory and complete literal control files. Hand-declared values,
standard integer masks and Python's IEEE struct encoder supply the references.
These controls test stored-channel extraction, not biological models or vendor gates.
"""
import argparse
import base64
import hashlib
import json
import math
import struct
from pathlib import Path


def frame(*, version='3.1', dtype='F', widths=(32, 32), rows=((0.1, -1234.5), (0.0, 1000000.0)),
          ranges=None, endian='<', encoding='utf8', names=None, extra=(), mode='L', nextdata=0,
          byteorder=None, padding=0, data_first=False, mismatch=False, total=None):
    ranges = ranges or [2**b if dtype == 'I' else 1024 for b in widths]
    names = names or ['channel'+str(i+1) for i in range(len(widths))]
    raw = bytearray()
    expected = []
    for row in rows:
        assert len(row) == len(widths)
        for v, b, r in zip(row, widths, ranges):
            if dtype == 'I':
                raw.extend(v.to_bytes((b+7)//8, 'little' if endian == '<' else 'big'))
                expected.append(float(v & ((1 << (r-1).bit_length())-1)))
            else:
                fmt = endian+('f' if dtype == 'F' else 'd')
                encoded = struct.pack(fmt, v); raw.extend(encoded)
                expected.append(struct.unpack(fmt, encoded)[0])
    start = 0 if not rows else 58 if data_first else 0
    end = 0 if not rows else start+len(raw)-1
    for _ in range(3):
        pairs = [('$BEGINANALYSIS', '0'), ('$ENDANALYSIS', '0'), ('$BEGINSTEXT', '0'), ('$ENDSTEXT', '0'),
                 ('$BEGINDATA', f'{start:010d}'), ('$ENDDATA', f'{end:010d}'),
                 ('$BYTEORD', byteorder or ('1,2,3,4' if endian == '<' else '4,3,2,1')),
                 ('$DATATYPE', dtype), ('$MODE', mode), ('$NEXTDATA', str(nextdata)),
                 ('$PAR', str(len(widths))), ('$TOT', str(len(rows) if total is None else total))]
        for i, (b, r, name) in enumerate(zip(widths, ranges, names), 1):
            pairs.extend([(f'$P{i}B', str(b)), (f'$P{i}R', str(r)), (f'$P{i}N', name), (f'$P{i}E', '0,0')])
        pairs.extend(extra)
        text = ('|'+'|'.join(str(x).replace('|', '||') for pair in pairs for x in pair)+'|').encode(encoding)+b' '*padding
        ts = 58+len(raw) if data_first else 58
        te = ts+len(text)-1
        start = 0 if not rows else 58 if data_first else te+1
        end = 0 if not rows else start+len(raw)-1
    header = ('FCS'+version+'    '+''.join(f'{v:8d}' for v in [ts, te, start+(1 if mismatch else 0), end, 0, 0])).encode('ascii')
    assert len(header) == 58
    values = b''.join(bytes.fromhex('7ff8000000000000') if math.isnan(v) else struct.pack('>d', v) for v in expected)
    return header+(bytes(raw)+text if data_first else text+bytes(raw)), hashlib.sha256(values).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out; out.mkdir(parents=True, exist_ok=False)
    cases = []

    def add(name, expected='PASS', legacy='UTF-8', policy='EXACT', **options):
        raw, digest = frame(**options); (out/(name+'.fcs')).write_bytes(raw)
        cases.append([name, legacy, policy, expected, str(len(options.get('rows', ((0.1, -1234.5), (0.0, 1000000.0))))),
                      str(len(options.get('widths', (32, 32)))), digest, base64.b64encode(raw).decode()])

    add('float-little'); add('float-big', endian='>')
    add('double-little', dtype='D', widths=(64, 64), rows=((-0.0, 0.1), (-1e200, 1e200)))
    add('double-big', dtype='D', widths=(64, 64), endian='>', rows=((-0.0, 0.1), (-1e200, 1e200)))
    add('integer8-mask', dtype='I', widths=(8,), ranges=(10,), rows=((255,), (16,), (9,)))
    add('integer16-big-mask', dtype='I', widths=(16,), ranges=(1024,), endian='>', rows=((65535,), (32768,), (1023,)))
    add('integer24-big', dtype='I', widths=(24,), endian='>', rows=((16777215,), (8388608,), (1,)))
    add('integer-mixed', dtype='I', widths=(8, 16, 24, 32), rows=((255, 65535, 16777215, 4294967295),))
    add('unicode-and-dollar', names=('β|$FSC', 'Case Sensitive'), extra=(('VendorKey', 'value$with|delimiter'),))
    add('legacy-latin1', version='2.0', legacy='ISO-8859-1', encoding='latin1', names=('café', 'second'))
    add('legacy30', version='3.0', names=('not renamed', 'second'))
    add('explicit-padding', padding=19, policy='ALLOW_TRAILING_SPACE_PADDING')
    add('data-before-text', data_first=True)
    add('empty', rows=())
    add('nonfinite', rows=((float('nan'), float('inf')), (-float('inf'), -0.0)))
    add('unselected-padding', expected='FAIL2', padding=19)
    add('offset-discrepancy', expected='FAIL2', mismatch=True)
    add('population-discrepancy', expected='FAIL2', total=3)
    add('duplicate-key', expected='FAIL2', extra=(('$pAr', '2'),))
    add('multiple-data-sets', expected='SKIP3', nextdata=100000)
    add('mixed-byte-order', expected='SKIP3', byteorder='3,4,1,2')
    add('packed-integer', expected='SKIP3', dtype='I', widths=(10,), ranges=(1024,), rows=((1023,),))
    add('unsupported-edition', expected='SKIP3', version='3.2')
    add('histogram-mode', expected='SKIP3', mode='C')
    add('bad-utf8', expected='FAIL2', encoding='latin1', names=('café', 'second'))
    fixture = '# id\tlegacy_charset\ttext_policy\texpected\tevents\tparameters\tcanonical_ieee64_sha256\tfcs_base64\n'+'\n'.join('\t'.join(c) for c in cases)+'\n'
    (out/'storage-controls.tsv').write_text(fixture, encoding='utf8', newline='\n')
    record = dict(status='PASS_INDEPENDENT_FCS_STORAGE_FIXTURE_GENERATION_ONLY', cases=len(cases),
                  complete_fixture=dict(bytes=len(fixture.encode()), sha256=hashlib.sha256(fixture.encode()).hexdigest()),
                  generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), scientific_analysis_executed=False)
    (out/'COMPLETED.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf8'); print(json.dumps(record))


if __name__ == '__main__':
    main()
