"""Verify and prepare synthetic complete-family association inputs in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = '38dedf6318a0ad695e3ec71cdf42342a41cb2b78a4308542a39f32463dc52810'
MEMBERS = {'inputs/adjusted-families.tsv': '7767067eb7f137adb64e3c4eea753d446bca7ec9afb52a6989136afb8841e3ea', 'inputs/adjusted-tests.tsv': 'ff52cb68252b8fab36a7ad8939ecb0eb1c6cb9356900f879ce4e4bf3fa39be96', 'inputs/families.tsv': 'c763f57745d7bb5f7524d7ffe5c709d13eb91787540098a232bb744b4488485a', 'inputs/members.tsv': '5f3736d0b359690b09eb53069e6a3fe68e6747ad415aa7d2df4c7e0299b04ffb', 'inputs/sources.tsv': '69a40cc97d49a452205e43753350578b2c978b9211bf074b3e800d9e756fa8a1', 'inputs/tests.tsv': '02d56cd5b68adcb3f877466c1290890c6cf7df6c2f11d9d7fb8e05ff3e91fed8', 'reference/across-traits/all-tests.tsv': 'e57fbcf565b27f2fe50f80909627e24d4f02e1bcf3fd68dc0ff829c382bf59d6', 'reference/across-traits/analysis-sources.tsv': 'fd18c9861a76379c3c2df5e877e40fbce4246a57ca61c3ea1b5ba6d3d5bab790', 'reference/across-traits/baseline-coverage.tsv': '6d4bbf367c0d53d5b6d90c1b143a89ba35be87a8a67b3071858a7aea84d55411', 'reference/across-traits/family-audit.tsv': '62da0f16ff2bd91911c5908d6ced3ea55f39b6258519cb4c3b74789fb1409b22', 'reference/across-traits/family-members.tsv': '2f733aba5b57fe93d2657444996f5a0bb4d961894b0dada9c7c6332d81bc7b69', 'reference/across-traits/known-contributors.tsv': '7905d49b7395971bf4e470232fc497f65815c8b1d3722af3cac7287c48112afa', 'reference/across-traits/known-links.tsv': '50b9f4c5dd3fd22dd6ea55143944a25880ea7e022f5b4b6ec11132fd1a0a6e32', 'reference/across-traits/known-union.tsv': '8a0bcd16b535ba0a91fe7d520c111123885097eae25ad398bd106f29bbeeeaa0', 'reference/across-traits/target-decisions.tsv': '451238b0bd18b1bd89d55896d61f913f1221dc8e4c826b4f02e10fd3fedb6c2d', 'reference/adjustment/input.tsv': '2d19c9bd2ac5028fc5ace2defffcd17cb0acbcbbef9c593ec051a1fc2cd9aacd', 'reference/adjustment/reference.R': 'cfa869d232fb4802d7bf54ef908b8c7bcc313f117ba62c8b41123a829d28f109', 'reference/adjustment/reference.tsv': 'a1fd12e52e7441783e424a4b462a3355e199c25d50efb2c00fb84ee914feb217', 'reference/adjustment/source-provenance.json': '4226306ff29e20b123eb6daa77ab865e93e2135aa4d9168fbd2042e0b2673da7', 'reference/exact/all-tests.tsv': 'e57fbcf565b27f2fe50f80909627e24d4f02e1bcf3fd68dc0ff829c382bf59d6', 'reference/exact/analysis-sources.tsv': 'fd18c9861a76379c3c2df5e877e40fbce4246a57ca61c3ea1b5ba6d3d5bab790', 'reference/exact/baseline-coverage.tsv': '2751025fbb41e8d111d4227a3277e444cd95313af7c9b37d05790d3b4a482b77', 'reference/exact/family-audit.tsv': '62da0f16ff2bd91911c5908d6ced3ea55f39b6258519cb4c3b74789fb1409b22', 'reference/exact/family-members.tsv': '2f733aba5b57fe93d2657444996f5a0bb4d961894b0dada9c7c6332d81bc7b69', 'reference/exact/known-contributors.tsv': '20014837870caf2a221481eec725659e80daf1d9625444ee20a6c4f65123bca3', 'reference/exact/known-links.tsv': 'ba0a1d9430b9810aba18ce32f5512ce318d14c71c8eacd11426e473436d61b47', 'reference/exact/known-union.tsv': '4b4f6deb29404b2ee6749df368386d91e19c1bb85ee74591244ff4db0dfd92df', 'reference/exact/target-decisions.tsv': '43570db79671fe869b42d8bd512981270b5ca4b3f1fa1d1fa09c5e451398412c', 'reference/selected-reference/all-tests.tsv': 'e57fbcf565b27f2fe50f80909627e24d4f02e1bcf3fd68dc0ff829c382bf59d6', 'reference/selected-reference/analysis-sources.tsv': 'fd18c9861a76379c3c2df5e877e40fbce4246a57ca61c3ea1b5ba6d3d5bab790', 'reference/selected-reference/baseline-coverage.tsv': '2751025fbb41e8d111d4227a3277e444cd95313af7c9b37d05790d3b4a482b77', 'reference/selected-reference/family-audit.tsv': '62da0f16ff2bd91911c5908d6ced3ea55f39b6258519cb4c3b74789fb1409b22', 'reference/selected-reference/family-members.tsv': '2f733aba5b57fe93d2657444996f5a0bb4d961894b0dada9c7c6332d81bc7b69', 'reference/selected-reference/known-contributors.tsv': '20014837870caf2a221481eec725659e80daf1d9625444ee20a6c4f65123bca3', 'reference/selected-reference/known-links.tsv': 'ba0a1d9430b9810aba18ce32f5512ce318d14c71c8eacd11426e473436d61b47', 'reference/selected-reference/known-union.tsv': '4b4f6deb29404b2ee6749df368386d91e19c1bb85ee74591244ff4db0dfd92df', 'reference/selected-reference/target-decisions.tsv': 'f7e0c67332f75cc391ccc155c2261dfe0ce9763eabdb8024deaaa6b2be7ea4db', 'reference/supplied-adjusted/all-tests.tsv': '243128f1077e1885576ffab62273acf5304ca4645c6a33dd13e49aa23ca2de34', 'reference/supplied-adjusted/analysis-sources.tsv': 'fd18c9861a76379c3c2df5e877e40fbce4246a57ca61c3ea1b5ba6d3d5bab790', 'reference/supplied-adjusted/baseline-coverage.tsv': '2751025fbb41e8d111d4227a3277e444cd95313af7c9b37d05790d3b4a482b77', 'reference/supplied-adjusted/family-audit.tsv': '19576f7dc743b63d058aeafe79ab42d0617dd6351c93dfd3369b4240ae97a4d8', 'reference/supplied-adjusted/family-members.tsv': '2f733aba5b57fe93d2657444996f5a0bb4d961894b0dada9c7c6332d81bc7b69', 'reference/supplied-adjusted/known-contributors.tsv': '20014837870caf2a221481eec725659e80daf1d9625444ee20a6c4f65123bca3', 'reference/supplied-adjusted/known-links.tsv': 'ba0a1d9430b9810aba18ce32f5512ce318d14c71c8eacd11426e473436d61b47', 'reference/supplied-adjusted/known-union.tsv': '4b4f6deb29404b2ee6749df368386d91e19c1bb85ee74591244ff4db0dfd92df', 'reference/supplied-adjusted/target-decisions.tsv': 'f7e0c67332f75cc391ccc155c2261dfe0ce9763eabdb8024deaaa6b2be7ea4db', 'source-provenance.json': 'afff6147206406ac76758af180d18b165459ac73d493206e7e9034d1bf06cfbe'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(directory):
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if archive.stat().st_size > 8388608 or digest(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Synthetic archive size or SHA-256 differs; restore the pinned example')
    contents = {}
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) != len(MEMBERS) or {entry.filename for entry in entries} != set(MEMBERS):
            raise ValueError('Synthetic archive differs from its exact member allowlist')
        if sum(entry.file_size for entry in entries) > 33554432:
            raise ValueError('Synthetic archive exceeds its complete uncompressed byte budget')
        for entry in entries:
            if entry.file_size > 4194304 or not stat.S_ISREG(entry.external_attr >> 16):
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
    options = {'method': 'known-associations', 'analysis-id': 'synthetic-complete-R-reference-v1',
               'gene-namespace': 'gene-v1', 'trait-namespace': 'trait-v1', 'cutoff': '0.05',
               'sources': 'inputs/sources.tsv', 'families': 'inputs/families.tsv',
               'members': 'inputs/members.tsv', 'tests': 'inputs/tests.tsv',
               'matching': 'gene-trait', 'absence': 'require-baseline-tests'}
    recipes = {'exact': dict(options),
               'selected-reference': dict(options, absence='selected-reference'),
               'across-traits': dict(options, matching='gene-across-traits', absence='selected-reference'),
               'supplied-adjusted': dict(options, absence='selected-reference',
                                        families='inputs/adjusted-families.tsv', tests='inputs/adjusted-tests.tsv')}
    for name, recipe in recipes.items():
        recipe['out'] = name + '-result'
        (directory / (name + '.json')).write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8')
    (directory / 'parent.json').write_text(json.dumps({'schema_version': 1, 'build': 'GRCh37',
        'out': 'parent-result', 'known_associations': options}, indent=2) + '\n', encoding='utf-8')
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256, 'members': MEMBERS,
        'scope': 'Synthetic complete probability families with independent R references and union/coverage/link equations. No biological association dataset, fitting software or inferred historical defaults.',
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
