"""Verify and prepare synthetic supplied-reference novelty inputs in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = 'd7ef79d552d3d8cd53575781280b1aa07f7e9dccd91ba4f7d730ab9cbc2d5b0e'
MEMBERS = {'inputs/coverage-all.tsv': '0ecfa51ac6498c35423e24021b2c6155a87e669d38efaa2512087070acc961f5', 'inputs/coverage-exact.tsv': 'cb7e89d2c886f73208106773e0f7c823df3a05df485d0efc8776203421cdd684', 'inputs/known.tsv': '04fd5cc232e584a356a101be89b2bcf9710f09ef2565e54033f423463b922592', 'inputs/leads.tsv': 'a330f089f0d9a66a2c96a2af970c003909a3dac4f295f38cbfee00ebcfa4a1fb', 'reference/all-known/coverage-rows.tsv': '13a452a4eebf3e0381f4f17ed33cdae2f4063aa338df772cac3e03265e1e5856', 'reference/all-known/known-matches.tsv': '280a6ad83f911f926b6c42afbac61ee0c98a557016092b8556665763eb1ca02c', 'reference/all-known/known-rows.tsv': '4bb1222a9064f496a66d5a61d09d516791b449b69f3dce4ada98f859755d8b84', 'reference/all-known/locus-novelty.tsv': '4e13c777be2de4a02c6c8a09fda8c49b793f7c413040b8733119222adcc7b718', 'reference/inclusive/coverage-rows.tsv': 'f816cfc7e21c080b21d7a1a3f82a7ad1cef4f9a02493a1c7a7e6ed5ca32cf314', 'reference/inclusive/known-matches.tsv': '83e8dd1a36585f930d567db50dedea5c71b86231493484fdfa9c5a82b8af2c12', 'reference/inclusive/known-rows.tsv': '4aad52b6901eb52c6acfcdc95c6c7bd0f9cf94a6343cef08dc70d2c8cc82164a', 'reference/inclusive/locus-novelty.tsv': 'bb159edf80a0b36500df3562b49d394b922fa13122a99306601495a0eba702f1', 'reference/strict/coverage-rows.tsv': 'f816cfc7e21c080b21d7a1a3f82a7ad1cef4f9a02493a1c7a7e6ed5ca32cf314', 'reference/strict/known-matches.tsv': 'a7bcea55dabdb90f51ec410aa139398576b27fdc6941750b8aa04c4fd17217f8', 'reference/strict/known-rows.tsv': 'adc5eb4218ef5dede0f780c705eb2d78c5ecb133e2035cf4f00c40c830721c83', 'reference/strict/locus-novelty.tsv': 'e1afbaaf73e2712b3ebf7d38c345008ed5d323c4efd1e9efb8839dec07799a77', 'reference/unknown-coverage/coverage-rows.tsv': '51a0d05553ad322cf3ba4b8d280982e36615d3797d5b5afe3b894d172a9046be', 'reference/unknown-coverage/known-matches.tsv': '83e8dd1a36585f930d567db50dedea5c71b86231493484fdfa9c5a82b8af2c12', 'reference/unknown-coverage/known-rows.tsv': '4aad52b6901eb52c6acfcdc95c6c7bd0f9cf94a6343cef08dc70d2c8cc82164a', 'reference/unknown-coverage/locus-novelty.tsv': '258f9fe15b975f9de925aca3125e900d6a8ca79290355b2dcb7088fb6e593591', 'source-provenance.json': 'dbf04e123e7bf0f3018d1724adcd8cce9e2becc1b9ab4a79b85e08e1aad73126'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(directory):
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if digest(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Synthetic archive SHA-256 mismatch; restore the pinned example')
    contents = {}
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) != len(MEMBERS) or {entry.filename for entry in entries} != set(MEMBERS):
            raise ValueError('Synthetic archive differs from its exact member allowlist')
        for entry in entries:
            if entry.file_size > 1048576 or not stat.S_ISREG(entry.external_attr >> 16):
                raise ValueError('Oversized or nonregular synthetic member: ' + entry.filename)
            data = source.read(entry)
            if digest(data) != MEMBERS[entry.filename]:
                raise ValueError('Synthetic member SHA-256 mismatch: ' + entry.filename)
            contents[entry.filename] = data
    directory = Path(directory).resolve()
    directory.mkdir(parents=True, exist_ok=False)
    for name, data in contents.items():
        path = directory / name  # Fixed exact allowlist only; no arbitrary archive extraction.
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    options = {'method': 'locus-novelty', 'build': 'GRCh37', 'known-build': 'GRCh37',
               'catalog-id': 'synthetic-independent-v1', 'analysis-id': 'synthetic-six-lead-review',
               'leads': 'inputs/leads.tsv', 'known': 'inputs/known.tsv',
               'coverage': 'inputs/coverage-exact.tsv', 'radius': '250000',
               'boundary': 'inclusive', 'trait-policy': 'exact'}
    recipes = {'inclusive': dict(options), 'strict': dict(options, boundary='strict'),
               'all-known': dict(options, coverage='inputs/coverage-all.tsv', **{'trait-policy': 'all-known'}),
               'unknown-coverage': dict(options, coverage='none')}
    for name, recipe in recipes.items():
        recipe['out'] = name + '-result'
        (directory / (name + '.json')).write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8')
    stage = dict(options)
    del stage['build']
    (directory / 'parent.json').write_text(json.dumps({'schema_version': 1, 'build': 'GRCh37',
        'out': 'parent-result', 'locus_novelty': stage}, indent=2) + '\n', encoding='utf-8')
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256, 'members': MEMBERS,
        'scope': 'Synthetic supplied-reference distance/coverage examples. Independent integer references; no installation, catalog acquisition, LD or universal novelty assertion.',
    }, indent=2) + '\n', encoding='utf-8')
    return directory


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    try:
        print(prepare(args.out))
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        parser.exit(2, f'INPUT PREPARATION FAILED: {failure}\nRequired action: restore the pinned example if needed and choose a NEW writable directory.\n')
