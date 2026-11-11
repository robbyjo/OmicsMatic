#!/usr/bin/env python3
"""Prepare only pinned synthetic inputs and a reviewed RSEM fitting recipe. No installation or analysis."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

ARCHIVE_SHA256 = '7bb083c676754c03887866c65463535993e1c368bee22faa867c661d7e9b7590'
VERSIONS = {'1.2.28', '1.3.0', '1.3.1', '1.3.2', '1.3.3'}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--setup-completion', required=True, type=Path)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--case', choices=['paired', 'single'], default='paired')
    p.add_argument('--posterior', choices=['NONE', 'MEAN', 'INTERVAL'], default='NONE')
    args = p.parse_args()
    setup = json.loads(args.setup_completion.read_bytes())
    if setup.get('status') != 'COMPLETED_EXACT_ORIGINAL_SOURCE_SETUP' or setup.get('source_version') not in VERSIONS:
        raise ValueError('Use the COMPLETED.json from a successful matching rsem-tools setup; it is not a scientific-result ticket')
    if not isinstance(setup.get('build_home'), str) or not re.fullmatch('[0-9a-f]{64}', setup.get('build_receipt_sha256', '')) or not isinstance(setup.get('library_directories'), list) or not all(isinstance(x, str) for x in setup['library_directories']):
        raise ValueError('Incomplete source-build identity or library declaration')
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if hashlib.sha256(archive.read_bytes()).hexdigest() != ARCHIVE_SHA256:
        raise ValueError('Pinned synthetic archive changed; restore it from the matching reviewed repository snapshot')
    members = {}
    with zipfile.ZipFile(archive) as z:
        if len(z.infolist()) != 40 or len(set(z.namelist())) != 40:
            raise ValueError('Unexpected or duplicated archive members')
        for item in z.infolist():
            if item.is_dir() or item.file_size > 2_000_000 or not re.fullmatch(r'(health/[A-Za-z0-9_.-]+|reference/(1\.2\.28|1\.3\.[0-3])/reference\.[A-Za-z0-9.]+|provenance\.json|SHA256SUMS)', item.filename):
                raise ValueError('Unsafe or unexpected synthetic archive member')
            members[item.filename] = z.read(item)
    manifest = {}
    for line in members['SHA256SUMS'].decode('ascii').splitlines():
        digest, name = line.split('  ', 1)
        if name in manifest or not re.fullmatch('[0-9a-f]{64}', digest):
            raise ValueError('Invalid complete synthetic checksum manifest')
        manifest[name] = digest
    if set(manifest) != set(members) - {'SHA256SUMS'}:
        raise ValueError('Incomplete synthetic checksum manifest')
    for name, digest in manifest.items():
        if hashlib.sha256(members[name]).hexdigest() != digest:
            raise ValueError('Synthetic member changed: ' + name)
    directory = args.out.absolute()
    if directory.parent.resolve(strict=True) != directory.parent or directory.exists():
        raise ValueError('Require an existing canonical parent and a NEW output directory; existing outputs are preserved')
    directory.mkdir()
    try:
        version = setup['source_version']
        for name, raw in members.items():
            if name.startswith('health/') or name.startswith('reference/' + version + '/') or name == 'provenance.json':
                target = directory / name
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw)
        settings = dict(seed=1618, worker_threads=1, strand='NONE', posterior=args.posterior, estimate_read_start=False, append_names=False)
        if args.case == 'single':
            settings['single_end_fragment'] = dict(mean=200, standard_deviation=20)
        recipe = dict(schema_version=1, source_version=version, build_home=setup['build_home'], build_receipt_sha256=setup['build_receipt_sha256'],
            library_directories=setup['library_directories'], reference_prefix=str(directory / 'reference' / version / 'reference'),
            alignment=str(directory / 'health' / (args.case + '.sam')), read_type=3 if args.case == 'paired' else 1, read_type_tag='', settings=settings)
        (directory / 'fit.json').write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8')
        (directory / 'WARNINGS.md').write_text('# Synthetic RSEM inputs prepared; no analysis performed\n\nOnly the selected release reference and complete health inputs were extracted after validating the whole archive. The source-build receipt is checked again by the fitting command. Run inside Linux/WSL, review fit.json, use NEW fitting output and inspect its warnings. These synthetic data establish no biological performance or whole-paper equivalence.\n', encoding='utf-8')
        print('Prepared synthetic recipe: ' + str(directory / 'fit.json'))
    except BaseException:
        (directory / 'PREPARATION_FAILED.txt').write_text('Input preparation failed. Preserve partial files and retry to NEW output after correcting the reported cause. No analysis has run.\n', encoding='utf-8')
        raise

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('RSEM EXAMPLE PREPARATION FAILED - NO ANALYSIS: ' + str(error), file=sys.stderr)
        sys.exit(2)
