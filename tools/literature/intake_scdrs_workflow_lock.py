# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain immutable upstream archives and exact wheels for the tested scDRS workflow profile."""
import argparse
import concurrent.futures
import hashlib
import io
import json
import re
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile
from pathlib import Path
from packaging.utils import parse_wheel_filename
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE


def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-scDRS-source-intake/1.0'})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'helper-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    intake = BASE / 'workflow-lock-intake-1'
    captured = json.loads((intake / 'COMPLETED.json').read_bytes())
    archive_dir = out / 'archives'
    archive_dir.mkdir()
    resource = ROOT / 'src/main/resources/org/jlinalg/omics/scdrs'
    assert not (resource / 'scdrs-workflow-lock.json').exists()
    try:
        packages = []
        for package in captured['conda_packages']:
            raw = get(package['url'])
            assert hashlib.sha256(raw).hexdigest() == package['sha256']
            assert hashlib.md5(raw).hexdigest() == package['md5']
            path = archive_dir / package['fn']
            path.write_bytes(raw)
            packages.append(dict(filename=package['fn'], **{k: package[k] for k in ['name', 'version', 'build', 'url', 'md5']}, **pin(path)))
        wheels = []
        built_records = []

        def pypi(record):
            name, version, _, _ = parse_wheel_filename(record['name'])
            raw = get('https://pypi.org/pypi/' + str(name) + '/' + str(version) + '/json')
            return record, str(name), str(version), raw

        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
            results = list(executor.map(pypi, captured['wheels']))
        for record, name, version, raw in results:
            save(out / (name + '-' + version + '-pypi.json'), json.loads(raw))
            metadata = json.loads(raw)
            source = intake / 'wheels' / record['name']
            assert pin(source) == {k: record[k] for k in ['bytes', 'sha256']}
            shutil.copyfile(source, archive_dir / source.name)
            match = [f for f in metadata['urls'] if f['filename'] == source.name]
            row = dict(filename=source.name, name=name, version=version, **pin(source))
            if match:
                assert len(match) == 1 and match[0]['digests']['sha256'] == row['sha256']
                row.update(delivery='download', url=match[0]['url'])
            else:
                sdists = [f for f in metadata['urls'] if f['packagetype'] == 'sdist']
                assert len(sdists) == 1
                sdist = sdists[0]
                source_raw = get(sdist['url'])
                assert hashlib.sha256(source_raw).hexdigest() == sdist['digests']['sha256']
                source_path = archive_dir / sdist['filename']
                source_path.write_bytes(source_raw)
                with tarfile.open(fileobj=io.BytesIO(source_raw), mode='r:*') as t:
                    originals = {}
                    for member in t.getmembers():
                        if member.isfile():
                            suffix = member.name.split('/', 1)[1]
                            stream = t.extractfile(member)
                            assert stream is not None
                            originals[suffix] = stream.read()
                checked = {}
                with zipfile.ZipFile(source) as z:
                    for filename in z.namelist():
                        if filename.endswith('.py'):
                            value = z.read(filename)
                            assert filename in originals and originals[filename] == value, (source.name, filename)
                            checked[filename] = dict(bytes=len(value), sha256=hashlib.sha256(value).hexdigest())
                assert checked
                assert not (resource / source.name).exists()
                shutil.copyfile(source, resource / source.name)
                row.update(delivery='bundled', source_distribution=dict(filename=sdist['filename'], url=sdist['url'], **pin(source_path)))
                built_records.append(dict(wheel=row, every_packaged_python_source_byte_identical=True,
                                          packaged_python_source_files=checked))
            wheels.append(row)
        probe = 'import importlib.metadata as m,json; print(json.dumps({d.metadata["Name"]:d.version for d in m.distributions()},sort_keys=True))'
        command = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/var/tmp/omicsmatic-scdrs-reference-2/environment/bin/python', '-s', '-B', '-c', probe]
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
        (out / 'installed-distributions.stdout').write_bytes(result.stdout)
        (out / 'installed-distributions.stderr').write_bytes(result.stderr)
        assert result.returncode == 0
        distributions = json.loads(result.stdout)
        assert distributions['setuptools'] == '68.2.2'
        mtlock = json.loads((ROOT / 'src/main/resources/org/jlinalg/omics/mtag/mtag-workflow-lock.json').read_bytes())
        bootstrap = mtlock['bootstrap']
        assert pin(BASE / 'reference-setup-2/micromamba') == {k: bootstrap[k] for k in ['bytes', 'sha256']}
        prior = json.loads((BASE / 'source-intake-1/COMPLETED.json').read_bytes())
        sources = {}
        for version, profile in prior['profiles'].items():
            sources[version] = dict(commit=profile['commit'], filename='scdrs-' + version + '-source.zip',
                url=profile['url'], **profile['archive'], files=profile['files'])
            shutil.copyfile(BASE / 'source-intake-1' / version / 'upstream.zip', archive_dir / sources[version]['filename'])
        lock = dict(schema_version=1, tool='scDRS', platform='linux-x86_64', python_version='3.10.16',
            sources=sources, bootstrap=bootstrap, packages=packages, wheels=wheels,
            installed_distributions=distributions, numpy_float64_reduction_version='1.23.5',
            full_adapter_validated=False, source_results_or_scientific_defaults_repaired=False,
            bundled_wheel_source_audits=built_records)
        save(resource / 'scdrs-workflow-lock.json', lock)
        save(out / 'COMPLETED.json', dict(status='PASS_EXACT_SCDRS_WORKFLOW_SOURCE_AND_DEPENDENCY_LOCK_ONLY',
            lock=pin(resource / 'scdrs-workflow-lock.json'), conda_packages=len(packages), wheels=len(wheels),
            bundled_source_identical_wheels=len(built_records), new_catalog_credit=False))
        print(json.dumps(dict(packages=len(packages), wheels=len(wheels), bundled=len(built_records))))
    except Exception as error:
        save(out / 'FAILURE.json', dict(error=repr(error), new_catalog_credit=False))
        raise


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable source/archive identity checks')
    main()
