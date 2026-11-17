#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an independent, synthetic SnpEff tutorial reference with original CDS/protein checks."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--edition', choices=['5.2','3.1i-r354-source'], required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    try:
        runtime = args.runtime.resolve()
        marker = json.loads((runtime/'COMPLETED.json').read_bytes())
        expected = {'5.2':'3d630f73332cff635f64301d06e8cc3c12889c794903b8b9be3a82227e47a291', '3.1i-r354-source':'d29e6257ee81795f895c8c1f94c10f9be00a616ce30cc1a662b36498d7ac6b27'}
        if marker['edition'] != args.edition or marker['jar_sha256'] != expected[args.edition] or marker['jar_sha256'] != hashlib.sha256((runtime/'snpeff.jar').read_bytes()).hexdigest():
            raise ValueError('Prepared runtime edition/content differs; retain it and investigate')
        out.mkdir(parents=True, exist_ok=False)
        owned = True
        notice = 'INDEPENDENT SYNTHETIC TUTORIAL. These data are not a paper reference or biological reproduction. Retain all build warnings. See docs/literature/SNPEFF_WORKFLOWS_DEVELOPMENT.md.\n'
        (out/'WARNINGS.txt').write_text(notice, encoding='utf-8')
        data = out/'data/tutorial'
        data.mkdir(parents=True)
        fixtures = ROOT/'src/test/resources/org/jlinalg/genetics/snpeff'
        for name in ['genes.gff','sequences.fa','cds.fa','protein.fa']:
            shutil.copyfile(fixtures/name, data/name)
        legacy = args.edition != '5.2'
        fmt = 'txt' if legacy else 'vcf'
        shutil.copyfile(fixtures/('legacy-input.txt' if legacy else 'modern-input.vcf'), out/('variants.'+fmt))
        original = (runtime/'snpEff.config').read_bytes()
        (out/'snpEff.config').write_bytes(original+b'\n# Explicit independent synthetic tutorial reference; local data directory.\n'+(b'data_dir' if legacy else b'data.dir')+b' = data\ntutorial.genome = Independent annotation tutorial\n')
        command = [args.java, '-Xmx512m', '-Dfile.encoding=UTF-8', '-Duser.timezone=UTC', '-jar', str(runtime/'snpeff.jar'), 'build', '-noLog', '-c', 'snpEff.config', '-gff3', '-v', 'tutorial']
        if not legacy:
            command.append('-noDownload')
        with (out/'build.stdout').open('xb') as so, (out/'build.stderr').open('xb') as se:
            result = subprocess.run(command, cwd=out, stdout=so, stderr=se, stdin=subprocess.DEVNULL, timeout=120, check=False)
        (out/'BUILD.json').write_text(json.dumps(dict(command=command, exit_code=result.returncode, independent_tutorial=True, cds_protein_checks_not_disabled=True), indent=2)+'\n', encoding='utf-8')
        if result.returncode or not (data/'snpEffectPredictor.bin').is_file():
            raise ValueError('Original tutorial reference build failed; inspect retained build.stderr')
        # Java properties escape backslashes/colon; absolute paths are explicit here.
        def escape(path):
            return json.dumps(str(path).replace('\\','/'), ensure_ascii=True)[1:-1].replace(':','\\:')
        values = dict(edition=args.edition, jar=escape(runtime/'snpeff.jar'), config='snpEff.config', data='data', genome='tutorial', variants='variants.'+fmt, format=fmt, up_down_stream='100', heap_mb='512', wall_ms='30000', max_bytes='268435456', max_files='2000')
        (out/'request.properties').write_text(''.join(key+'='+value+'\n' for key,value in values.items()), encoding='ascii')
        print('PASS independent tutorial build; '+str(out/'request.properties')+'; inspect WARNINGS.txt and build.stderr')
        return 0
    except Exception as error:
        status = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        message = ('SKIP3: dependent analysis skipped. ' if status==3 else 'FAIL2: no complete tutorial result. ')+str(error)+'\nResolve the named prerequisite/cause and retry NEW output.\n'
        if owned:
            (out/'FAILURE.txt').write_text(message, encoding='utf-8')
        print(message, file=sys.stderr)
        return status

if __name__ == '__main__':
    raise SystemExit(main())
