"""Generate NEW artificial FUSION fixtures. Never overwrite existing evidence."""
from pathlib import Path
import argparse
import hashlib
import json
import math
import os
import random
import shutil
import subprocess
import sys


def generate(out, rscript):
    executable = shutil.which(str(rscript)) or str(rscript)
    if not Path(executable).is_file():
        print('SKIP3: Rscript unavailable. Install an allowed R runtime or use the frozen example data; '
              'dependent generation is skipped.', file=sys.stderr)
        return 3
    out = out.resolve()
    if out.exists() or not out.parent.is_dir():
        raise ValueError('Use NEW output with an existing parent; existing evidence is preserved')
    out.mkdir()
    (out / 'weights').mkdir()
    rng = random.Random(9346)
    n, p = 64, 12
    rows = []
    for _ in range(n):
        latent = rng.choices([0, 1, 2], [.3, .4, .3])[0]
        rows.append([latent if rng.random() < .8 else rng.randrange(3) for _ in range(p)])
    fam = [f'ART{i:03d} ART{i:03d} 0 0 0 {i%11+0.125}' for i in range(n)]
    (out / 'ref.1.fam').write_text('\n'.join(fam) + '\n', encoding='ascii', newline='\n')
    (out / 'ref.1.bim').write_text(''.join(f'1 synth{i:03d} 0 {100000+i*1000} A C\n'
                                               for i in range(p)), encoding='ascii', newline='\n')
    raw = bytearray([0x6c, 0x1b, 1])
    encoding = {0:3, 1:2, 2:0}
    for j in range(p):
        for start in range(0, n, 4):
            raw.append(sum(encoding[rows[i][j]] << (2*(i-start)) for i in range(start, min(start+4,n))))
    (out / 'ref.1.bed').write_bytes(raw)
    for name, missing, beta in [('complete', False, False), ('missing', True, False), ('beta-se', False, True)]:
        content = 'SNP A1 A2 Z' + (' BETA SE' if beta else '') + '\n'
        for j in range(p):
            if missing and j in [2,4,7,10]:
                continue
            z = 5 + math.sin(j)
            content += f'synth{j:03d} A C {z:.17g}' + (f' {z/100:.17g} 0.01' if beta else '') + '\n'
        (out / (name + '.sumstats')).write_text(content, encoding='ascii', newline='\n')
    # Separate artificial training phenotype/covariate, generated in the original order.
    rng = random.Random(9346123)
    training, phenotype, covariates = [], [], ['FID IID C1']
    for i, line in enumerate(fam):
        fields = line.split()
        y = 1.4*rows[i][0] + .4*rows[i][4] + 1.2*rng.gauss(0,1)
        fields[5] = f'{y:.17g}'
        training.append(' '.join(fields))
        phenotype.append(f'{fields[0]} {fields[1]} {y:.17g}')
        covariates.append(f'{fields[0]} {fields[1]} {rng.gauss(0,1):.17g}')
    for suffix in ['bed', 'bim']:
        shutil.copyfile(out / ('ref.1.' + suffix), out / ('train.' + suffix))
    for name, values in [('train.fam', training), ('phenotype', phenotype), ('covariates', covariates)]:
        (out / name).write_text('\n'.join(values) + '\n', encoding='ascii', newline='\n')
    env = dict(os.environ, LC_ALL='C', LANG='C')
    args = [executable, '--vanilla', str(Path(__file__).with_name('generate_weights.R')), str(out)]
    result = subprocess.run(args, env=env, capture_output=True)
    (out / 'generation.stdout').write_bytes(result.stdout)
    (out / 'generation.stderr').write_bytes(result.stderr)
    if result.returncode:
        print('FAIL2: R fixture generation failed; retain logs/partial output and retry NEW output.', file=sys.stderr)
        return 2
    files = {p.relative_to(out).as_posix():dict(bytes=p.stat().st_size,
             sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in out.rglob('*')
             if p.is_file() and p.name not in ['generation.stdout', 'generation.stderr']}
    (out / 'GENERATION.json').write_text(json.dumps(dict(synthetic=True, samples=n,
            variants=p, files=files, byte_identity_qualification='Windows R4.6.1; other runtimes may serialize differently'),
            indent=2)+'\n', encoding='utf-8')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--rscript', required=True)
    args = parser.parse_args()
    try:
        return generate(args.out, args.rscript)
    except Exception as error:
        print('FAIL2: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
