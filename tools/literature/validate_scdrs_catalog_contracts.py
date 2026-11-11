# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check current metadata idempotency and the published request's typed schema without scientific execution."""
import argparse
import json
import re
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux
from refresh_scdrs_catalog_metadata import refresh


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    protected = {p.relative_to(ROOT).as_posix(): pin(p) for p in data.iterdir() if p.is_file()}
    protected.update({p.relative_to(ROOT).as_posix(): pin(p) for p in [ROOT / 'site/literature-review.html', ROOT / 'site/literature-warnings.html']})
    refresh()
    assert all(pin(ROOT / n) == v for n, v in protected.items()), 'Metadata refresh is not byte-idempotent'
    guide = ROOT / 'docs/literature/SCDRS_INTEGRATION.md'
    example = ROOT / 'docs/literature/scdrs-request-example.json'
    raw = re.findall(r'```json\n(.*?)\n```', guide.read_text(encoding='utf8'), flags=re.S)[0].encode() + b'\n'
    if example.exists(): assert example.read_bytes() == raw
    else: example.write_bytes(raw)
    assert json.loads(raw)['operation'] == 'score-and-downstream'
    jar = ROOT / 'docs/literature/scdrs-workflow-development/scdrs-workflow-javac21.jar'
    probe = ROOT / 'src/test/java/org/jlinalg/cli/ScdrsCapacityReferenceProbe.java'
    jdk = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
    classes = out / 'classes'
    classes.mkdir()
    def run(label, command):
        result = subprocess.run(command, cwd=ROOT, capture_output=True, stdin=subprocess.DEVNULL, timeout=120)
        (out / (label + '.stdout')).write_bytes(result.stdout)
        (out / (label + '.stderr')).write_bytes(result.stderr)
        save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
        assert result.returncode == 0, (label, result.stderr[-2000:])
    run('compile', [str(jdk / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-sourcepath', '', '-cp', str(jar), '-d', str(classes), str(probe)])
    for platform, java, convert, separator in [
        ('windows21', [str(jdk / 'java.exe')], str, ';'),
        ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], str, ';'),
        ('linux21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java'], linux, ':')]:
        output = out / (platform + '.txt')
        run(platform, java + ['-cp', separator.join(convert(p) for p in [classes, jar]), 'org.jlinalg.cli.ScdrsCapacityReferenceProbe', convert(example), convert(output)])
        assert output.read_text() == '1000000\n'
    assert all(pin(ROOT / n) == v for n, v in protected.items())
    save(out / 'COMPLETED.json', dict(status='PASS_SCDRS_METADATA_IDEMPOTENCY_AND_PUBLISHED_TYPED_REQUEST_THREE_JVMS',
        rendered_catalog_unchanged=True, scientific_analysis_executed=False, logical_matrix_allocated=False,
        platforms=['windows21', 'windows25', 'linux21'], example=pin(example), guide=pin(guide),
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), ROOT / 'tools/literature/refresh_scdrs_catalog_metadata.py', probe]}))
    print('PASS_SCDRS_METADATA_IDEMPOTENCY_AND_PUBLISHED_TYPED_REQUEST_THREE_JVMS', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable byte-idempotency or complete typed-schema checks')
    main()
