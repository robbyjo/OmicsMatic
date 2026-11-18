#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the pinned upstream ATAC source, registry identity and public test-data revision.

This read-only network intake never installs software or executes scientific code.
Every output directory must be new. Raw responses and failures remain available.
"""
import argparse
import hashlib
import json
import tarfile
import traceback
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path, PurePosixPath

COMMIT = '1b3a832db5a53c92c2c89a4d6d79455e860461ad'
ARCHIVE = dict(bytes=1026072, sha256='682b01c651f6e611c61878613b15027f61cc0599940ce8f573c01a9c00ea5375')
TEST_COMMIT = 'cd022b097372b078a68d8afadb172ad7342fd91f'
TEST_ARCHIVE = dict(bytes=47003695, sha256='4b882dd9380a7474c144040996187a75a565f8b3010bd6ba27a7255c2d2bfd7c')
IMAGE_DIGEST = 'sha256:cb843b04a06abd3538f0dfc0e0e4f08c691a79e895638811096c3a94d4767d5c'
ACTION = ('Intake only; no scientific result. Preserve this directory and inspect FAILURE.json '
          'on error. Download/install only where permitted. An unavailable or forbidden exact '
          'prerequisite requires skipping the dependent analysis; do not substitute versions. '
          'Current registry and test-data identities do not establish historical author use.')


def pin_bytes(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def save(path, obj):
    path.write_text(json.dumps(obj, indent=2, allow_nan=False)+'\n', encoding='utf-8', newline='\n')


def download(out, url, name, accept=None, bearer=None):
    headers = {'User-Agent': 'OmicsMatic-pinned-source-intake'}
    if accept:
        headers['Accept'] = accept
    if bearer:
        headers['Authorization'] = 'Bearer '+bearer
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=120) as response:
        raw = response.read(268435457)
        if len(raw) > 268435456:
            raise ValueError('Intake response exceeds complete-file capacity: '+url)
        final_url = response.geturl()
        if urllib.parse.urlparse(final_url).scheme != 'https':
            raise ValueError('Unexpected non-HTTPS redirect')
    (out/name).write_bytes(raw)
    parsed = urllib.parse.urlsplit(final_url)
    record = dict(url=url, final_url=urllib.parse.urlunsplit(parsed._replace(query='', fragment='')),
                  **pin_bytes(raw))
    if parsed.query or parsed.fragment:
        record['temporary_redirect_query_omitted'] = True
    return raw, record


def unpack(archive, destination):
    destination.mkdir()
    files = {}
    with tarfile.open(archive) as stream:
        members = stream.getmembers()
        prefix = PurePosixPath(members[0].name).parts[0]
        seen = set()
        for member in members:
            parts = PurePosixPath(member.name).parts
            if (not parts or parts[0] != prefix or '..' in parts
                    or member.name.startswith('/') or not (member.isdir() or member.isfile())):
                raise ValueError('Unexpected source archive member: '+member.name)
            if len(parts) == 1:
                continue
            relative = '/'.join(parts[1:])
            if relative in seen:
                raise ValueError('Duplicate source archive member: '+relative)
            seen.add(relative)
            target = destination.joinpath(*parts[1:])
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                raw = stream.extractfile(member).read()
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(raw)
                target.chmod(member.mode & 0o777)
                files[relative] = dict(**pin_bytes(raw), mode=member.mode & 0o777)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    stage = 'source'
    try:
        source, source_record = download(out, 'https://codeload.github.com/nf-core/atacseq/tar.gz/'+COMMIT, 'source.tar.gz')
        if pin_bytes(source) != ARCHIVE:
            raise ValueError('Pinned upstream source archive changed')
        files = unpack(out/'source.tar.gz', out/'source')
        if len(files) != 86 or 'MIT License' not in (out/'source/LICENSE').read_text():
            raise ValueError('Unexpected original source/license census')
        save(out/'source-lock.json', dict(pipeline='nf-core/atacseq', version='1.2.1', commit=COMMIT,
                                        archive=source_record, files=files))
        stage = 'registry'
        # Authentication tokens are short lived credentials: never save or print them.
        with urllib.request.urlopen('https://auth.docker.io/token?service=registry.docker.io&scope=repository:nfcore/atacseq:pull', timeout=120) as response:
            token = json.load(response)['token']
        accept = ','.join(['application/vnd.docker.distribution.manifest.v2+json',
                           'application/vnd.docker.distribution.manifest.list.v2+json',
                           'application/vnd.oci.image.index.v1+json',
                           'application/vnd.oci.image.manifest.v1+json'])
        manifest, tag_record = download(out, 'https://registry-1.docker.io/v2/nfcore/atacseq/manifests/1.2.1', 'tag-manifest.json', accept, token)
        selected = json.loads(manifest)
        tag_digest = 'sha256:'+hashlib.sha256(manifest).hexdigest()
        platform_digest = tag_digest
        if 'manifests' in selected:
            candidates = [row for row in selected['manifests'] if row['platform']['os'] == 'linux' and row['platform']['architecture'] == 'amd64']
            if len(candidates) != 1:
                raise ValueError('No unique original Linux amd64 image')
            platform_digest = candidates[0]['digest']
            manifest, platform_record = download(out, 'https://registry-1.docker.io/v2/nfcore/atacseq/manifests/'+platform_digest, 'platform-manifest.json', accept, token)
            if 'sha256:'+hashlib.sha256(manifest).hexdigest() != platform_digest:
                raise ValueError('Platform manifest digest changed')
            selected = json.loads(manifest)
        else:
            (out/'platform-manifest.json').write_bytes(manifest)
            platform_record = tag_record
        if platform_digest != IMAGE_DIGEST:
            raise ValueError('Original source image tag now resolves to different bytes; preserve the new response and use the pinned qualified digest')
        config, config_record = download(out, 'https://registry-1.docker.io/v2/nfcore/atacseq/blobs/'+selected['config']['digest'], 'image-config.json', bearer=token)
        config_json = json.loads(config)
        if (pin_bytes(config)['bytes'] != selected['config']['size']
                or 'sha256:'+hashlib.sha256(config).hexdigest() != selected['config']['digest']
                or config_json['os'] != 'linux' or config_json['architecture'] != 'amd64'):
            raise ValueError('Original image configuration digest/platform differs')
        image = dict(source_container='nfcore/atacseq:1.2.1', tag_digest=tag_digest,
                     platform_digest=platform_digest, tag=tag_record, platform=platform_record,
                     config=config_record, layers=selected['layers'],
                     compressed_layer_bytes=sum(row['size'] for row in selected['layers']),
                     author_image_digest_reported=False)
        save(out/'image-lock.json', image)
        stage = 'public-test-data'
        ref, ref_record = download(out, 'https://api.github.com/repos/nf-core/test-datasets/commits/'+TEST_COMMIT, 'test-data-ref.json', 'application/vnd.github+json')
        revision = json.loads(ref)['sha']
        data, data_record = download(out, 'https://codeload.github.com/nf-core/test-datasets/tar.gz/'+revision, 'test-data.tar.gz')
        if revision != TEST_COMMIT or pin_bytes(data) != TEST_ARCHIVE:
            raise ValueError('Pinned public validation-data source/archive differs')
        data_files = unpack(out/'test-data.tar.gz', out/'test-data')
        save(out/'test-data-lock.json', dict(commit=revision, reference=ref_record, archive=data_record, files=data_files,
                                           historical_author_data=False))
        state = dict(status='PASS_ORIGINAL_SOURCE_REGISTRY_AND_TEST_DATA_INTAKE_ONLY', source_commit=COMMIT,
                     source_files=len(files), image=image, test_data_commit=revision, test_data_files=len(data_files),
                     scientific_analysis_executed=False, required_action=ACTION)
        save(out/'COMPLETED.json', state)
        print(json.dumps(dict(status=state['status'], image_digest=platform_digest,
                              compressed_layer_bytes=image['compressed_layer_bytes'], test_data_files=len(data_files))))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (urllib.error.URLError, TimeoutError, PermissionError)) else 2
        status = 'SKIP3_INTAKE_UNAVAILABLE' if code == 3 else 'FAIL2_INTAKE_NO_ANALYSIS'
        save(out/'FAILURE.json', dict(status=status, stage=stage, error=repr(error),
                                     traceback=traceback.format_exc(), required_action=ACTION))
        print(status+' stage '+stage+': '+str(error)+'\n'+ACTION)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
