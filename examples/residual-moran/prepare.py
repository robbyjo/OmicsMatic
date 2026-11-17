#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy the checked synthetic example and create a native-only request in NEW output."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import sys


def pin(path):
    return dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--request', type=Path, help='Optional NEW request file outside the work directory, useful for an ASCII Windows launcher path')
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    out = args.out.absolute()
    request_path = args.request.absolute() if args.request else out/'request.json'
    owned = False
    try:
        if out.exists():
            raise ValueError('Preserve existing output and select a NEW directory')
        if request_path.exists() or request_path == out or request_path in [out/n for n in ['design.tsv', 'weights.tsv', 'expression.tsv']] or request_path.is_relative_to(out/'result'):
            raise ValueError('Select a NEW request path outside protected inputs and future result directory')
        provenance = json.loads((here/'PROVENANCE.json').read_bytes())
        for name, expected in provenance['inputs'].items():
            if pin(here/name) != expected:
                raise ValueError('Synthetic input identity differs: '+name)
        out.mkdir(parents=True, exist_ok=False)
        owned = True
        for name in provenance['inputs']:
            shutil.copyfile(here/name, out/name)
        request = dict(schema_version=1, design='design.tsv', weights='weights.tsv', expression='expression.tsv',
            response_units='prepared-linear-response', response_provenance='Seeded original-spdep validation example; already prepared responses, not paper counts.',
            population_declaration='All 60 synthetic observations and 3 genes; explicit treatment-coded design retained.',
            zero_policy=False, alternative='greater', out='result')
        if request_path.parent != out:
            for name in ['design', 'weights', 'expression']:
                request[name] = str(out/(name+'.tsv'))
            request['out'] = str(out/'result')
        request_path.parent.mkdir(parents=True, exist_ok=True)
        with request_path.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(json.dumps(request, indent=2, ensure_ascii=False)+'\n')
        for name, expected in provenance['inputs'].items():
            if pin(here/name) != expected or pin(out/name) != expected:
                raise ValueError('Input changed during example preparation: '+name)
        print('Prepared native-only example: '+str(request_path))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        message = ('SKIP3: example prerequisite unavailable. ' if code == 3 else 'FAIL2: example was not prepared. ')+str(error)+'. Preserve partials and retry NEW permitted output. No runtime installation is needed.'
        if owned:
            (out/'FAILURE.txt').write_text(message+'\n', encoding='utf-8')
        print(message, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
