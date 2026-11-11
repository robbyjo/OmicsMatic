# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Shared capture helpers for real standalone Signac peak workflow controls."""
import csv
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21, JAVA25, linux

BASE = ROOT / 'build/literature-review/signac-runtime'
JAR = BASE / 'peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar'
CLI = 'org.jlinalg.cli.SignacPeaksCli'
VERSIONS = ['2.2.7.1', '2.2.9', '2.2.9.1']


def read(path):
    return json.loads(path.read_bytes())


def files(root):
    return {p.relative_to(root).as_posix(): pin(p) for p in root.rglob('*') if p.is_file()}


def invoke(out, label, request, expected=0, platform='linux-java21', cli=CLI):
    if platform == 'linux-java21':
        argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java',
                '-Xmx768m', '-cp', linux(JAR), cli, '--request', linux(request)]
    else:
        argv = [JAVA21 if platform == 'windows-java21' else JAVA25, '-Xmx768m',
                '-cp', JAR, cli, '--request', request]
    argv = list(map(str, argv))
    with (out / (label + '.stdout')).open('xb') as a, (out / (label + '.stderr')).open('xb') as b:
        p = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=300)
    save(out / (label + '.process.json'), dict(command=argv, exit_code=p.returncode, expected_exit_code=expected))
    assert p.returncode == expected, label + ' failed; inspect retained logs'


def verify(out, label, target, expected=0, platforms=('windows-java21', 'windows-java25', 'linux-java21')):
    request = out / (label + '-verify.json')
    save(request, dict(schema_version=1, mode='VERIFY', analysis=target.name))
    for platform in platforms:
        invoke(out, label + '-verify-' + platform, request, expected, platform)


def base_request(target, version='2.2.7.1'):
    return dict(schema_version=1, mode='POOLED', out=linux(target), source_profile='Signac-1.9.0',
                macs2_version=version, macs2_home=linux(BASE / ('peaks-reference-setup-5/' + version)),
                genome_build='synthetic-control-not-a-biological-result', effective_genome_size=1000000,
                sources=[dict(fragments=linux(BASE / 'peaks-macs-original-1/inputs' / n)) for n in ['a.tsv', 'b.tsv']],
                name='fixture', timeout_seconds=120, maximum_output_bytes=100000000)


def table(path):
    with path.open(encoding='utf8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def expected_peaks(version, cases, labels, broad=False):
    rows = []
    reference = BASE / ('peaks-macs-original-' + str(VERSIONS.index(version) + 1)) / version
    for case, label in zip(cases, labels):
        p = reference / case / ('fixture_peaks.' + ('broadPeak' if broad else 'narrowPeak'))
        for line in p.read_text().splitlines():
            v = line.split('\t')
            rows.append(dict(group=label, chromosome=v[0], start=str(int(v[1])+1), end=v[2], strand='*',
                             name=v[3].replace('fixture_peak_', label + '_peak_', 1), score=v[4],
                             fold_change=v[6], negative_log10_pvalue=v[7], negative_log10_qvalue=v[8],
                             relative_summit_position='' if broad else v[9]))
    return rows
