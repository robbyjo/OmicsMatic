#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent Python-struct files for explicit amplifier/compensation bridge tests.

Creates a NEW directory. No vendor file is edited. The fixed-length PnE change
below changes only this generator's synthetic file before it is published.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
from generate_reader_controls import frame


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    out = parser.parse_args().out
    out.mkdir(parents=True, exist_ok=False)
    cases = []
    def add(name, *, change=None, **options):
        raw, _ = frame(names=('X','Y'), rows=((2,8),(-2,4)), **options)
        if change is not None:
            old, new = change
            assert len(old) == len(new) and raw.count(old) == 1
            raw = raw.replace(old,new)
        (out/(name+'.fcs')).write_bytes(raw)
        cases.append(name+'\t'+base64.b64encode(raw).decode())
    spill = '2,X,Y,1,0.1,0.2,1'
    add('gain-and-spill',extra=(('$P1G','2'),('$P2G','4'),('SPILL',spill)))
    add('two-compensations',extra=(('SPILL',spill),('$SPILLOVER',spill)))
    add('legacy-compensation',extra=(('$COMP','original-legacy-description'),))
    add('legacy-zero-log-minimum',version='2.0',change=(b'$P1E|0,0|',b'$P1E|4,0|'))
    add('legacy-log-positive',version='3.0',change=(b'$P1E|0,0|',b'$P1E|4,2|'))
    add('float31-log',change=(b'$P1E|0,0|',b'$P1E|4,2|'))
    add('bad-standard-diagonal',extra=(('$SPILLOVER','2,X,Y,2,0.1,0.2,1'),))
    add('singular-square',extra=(('SPILL','2,X,Y,1,1,1,1'),))
    add('invalid-gain',extra=(('$P1G','0'),))
    add('no-compensation')
    add('numeric-space-padding',total=' 2 ',ranges=(' 1024 ',1024),extra=(('$P1G',' 2 '),('SPILL',' 2 ,X,Y, 1 , 0.1 , 0.2 , 1 ')))
    add('numeric-tab-padding',total='2\t')
    fixture = '# id\tfcs_base64\n'+'\n'.join(cases)+'\n'
    (out/'gating-input-controls.tsv').write_text(fixture,encoding='utf8',newline='\n')
    record = dict(status='PASS_INDEPENDENT_SYNTHETIC_GATING_INPUT_GENERATION',cases=len(cases),bytes=len(fixture.encode()),sha256=hashlib.sha256(fixture.encode()).hexdigest(),generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    (out/'COMPLETED.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    print(json.dumps(record))

if __name__ == '__main__':
    main()
