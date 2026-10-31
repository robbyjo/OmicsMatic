"""Verify and prepare synthetic supplied MAGMA report data in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = 'b0623c5552c8f4e257106ffbe181eeaef12785bd242c1bd7a42d4ec8a3c34776'
MEMBERS = {'inputs/baseline.gsa.out': 'e34c0d08df8be58c7b923d21eb7202da7fb02c4c67875b4b6b70de9c1b6fc5bb', 'inputs/baseline.tsv': '929faaa96938f3c01fab29670ce2c17e395be20908dd428a12a6d03274a7a847', 'inputs/genes.tsv': '8c8ff400eb53224d56e3971071e2233f5fcc0e732d5e05db958268fcb7c55d6b', 'inputs/missing.gsa.out': '794af2b9dcbf9d2e181586fc98e421380702e83d3fae8eea57da4469e7d87de4', 'inputs/padded.gsa.out': 'a76c50f9e0530c9bb21fed355d393f42b96e499f05ee8d8ec9bfeed2a7bc2de3', 'inputs/symbols.tsv': 'b3efc13a8f0cbb268b29956dcda8366bd59b5b3367337f3ad81b16bd298dced5', 'inputs/unused-sets.out': '050d99b21a95848ec674a84d17180237a00da36130d758d7e6fd4f2672d84f57', 'reference/baseline-full-tests.tsv': '9a3b4f6cc905d45324a32d133e8f2f9632ce35d570749ca6e9e2557828a7ea6f', 'reference/baseline-sets-top.tsv': '4d7b52e5e41fb37c986cd6b3abc34f69a62828ce40a5a7d93c8677a7f1a77800', 'reference/genes.tsv': 'c879596b0bdf9ab98b30af10ef8f4f1c053ca9f440e0f2a82d4033f82bf9937e', 'reference/missing-full-tests.tsv': '1f898d4edfaa9bfac77950fe22198655300be303e2396eb2cc9b34aa1410c614', 'reference/missing-sets-top.tsv': 'a29b1939727efb5b842acb8e5dc22c93ac9cf422da8a10ca236b99e78a43919c', 'reference/padded-full-tests.tsv': '64e6d186fe10a1bdf7524fb660f9de012fff16f131ba87d571b856834f421f13', 'reference/padded-sets-top.tsv': '60755380bf30d925579a87ca59c9a5fd9dc4572500529f2958a633c1a2798391', 'source-provenance.json': '5fca12812f637ddd9e0053fd4d0aa16ea17ff0d8756d5c482e02a186b3630404'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(directory):
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if digest(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Synthetic archive SHA-256 mismatch; restore the pinned archive')
    contents = {}
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) != len(MEMBERS) or {e.filename for e in entries} != set(MEMBERS):
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
        path = directory / name  # Fixed exact allowlist; arbitrary archive paths are never extracted.
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    options = {'method': 'magma-report', 'build': 'synthetic-v1', 'annotation-id': 'synthetic-symbols-v1',
               'analysis-id': 'synthetic-supplied-MAGMA-results', 'gene-results': 'inputs/genes.tsv',
               'symbols': 'inputs/symbols.tsv', 'gsa': 'inputs/baseline.gsa.out', 'sets': 'inputs/unused-sets.out',
               'sets-format': 'magma-skip3', 'duplicates': 'source-first', 'missing-p': 'error', 'placeholders': 'error'}
    recipes = {'minimal': dict(options),
               'fallback': dict(options, gsa='none', sets='inputs/baseline.tsv', **{'sets-format': 'tsv'}),
               'padded': dict(options, gsa='inputs/padded.gsa.out', sets='none', placeholders='source-rows'),
               'missing': dict(options, gsa='inputs/missing.gsa.out', sets='none', placeholders='source-rows', **{'missing-p': 'source-na'})}
    for name, recipe in recipes.items():
        recipe['out'] = name + '-result'
        (directory / (name + '.json')).write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8')
    stage = dict(options)
    del stage['build']
    (directory / 'parent.json').write_text(json.dumps({'schema_version': 1, 'build': 'synthetic-v1',
        'out': 'parent-result', 'magma_report': stage}, indent=2) + '\n', encoding='utf-8')
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256, 'members': MEMBERS,
        'scope': 'Synthetic supplied report only. No MAGMA fit, model certification, installation or biological resource acquisition.',
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
