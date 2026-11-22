"""Verify and prepare synthetic supplied SNP annotation data in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = 'ad979cfe9df7cf13add6aac8610fab1d2219c4d5bcdfac4cf209124cb608d8aa'
MEMBERS = {
    'inputs/annotations.tsv': 'a91a43ae48174b92d766cb167a3c4e95a8d92ee1691c294520d8aab5b294ccaa',
    'inputs/bed/A.bed.gz': '7c213b41935d1a47f2567a783ce7c38608012da458436ba2528fff47cdc2fbe2',
    'inputs/bed/B.bed': '8a10524b52361179521fd7a1b960754ef39899b278752f8587cf5057c8420944',
    'inputs/consequences.tsv': '319d38cd61a21032ec506ad4cb613d4855cd4c81000c5d4b2f18a8dd7cfbc2d7',
    'inputs/genes.tsv': 'c93015127fbf90f3c5e63868c3f5a66c053516b5dc166545c61bbe87ffb98041',
    'inputs/independent.tsv': '0b04392f3ca5a90c5e3f300a10445827be9fa81fecdf64a78fc4571a9767d657',
    'inputs/ld.tsv': 'e1bce9d75b1d0d1ed055fe3d9b251dc40558e3c9f80a57cc6ec0ad34700e93e5',
    'inputs/reference-counts.tsv': '76a57e2dd7d5dbb7bebb12083325ce8dd3c1c5f01d76e20cf2f4daa9eb10a576',
    'inputs/snps.tsv': '6b9e009ad369268d2adf3573b985c1d5c79254378707b762d562867efe4de0e0',
    'reference/annot.bed': '3caa359835d5c29e018122b07eb1775e65986606fadc7afe8ceeddf44476bb5d',
    'reference/annotation-statistics.tsv': '598f11bc9bde8f715c710037fc3fe1f0f8ceced1e43bd5189da24aaa8ea904c0',
    'reference/annotations-with-bed.tsv': '20c13dc11e2b6ea09127c2a7673423a2ba0ea4284a4571e4629e047e32089f34',
    'reference/annov.tsv': '5305efad89f49d2a0ede7d27ae70a4122b1da00a4f695f072986cf8dda6e809e',
    'reference/independent.tsv': 'ba98cfe00371542d70799ce6f797a9b81308cf5dd516cc50f1315f67b23c6b26',
    'reference/nearest.tsv': 'dc3f7e707fe92f6dd1dd3e00c5f4e0b3406efbdd2c9d4c97af551b7120145e1c',
    'reference/snps.tsv': '642cfb8987509e6c836602f419d02cf1274400e2d0950ba3be1e0c31a5097fe1',
    'source-provenance.json': '8f534611d1f09e7189ad61c12b630c459dc04af6bdd26fe03c9bbe386816851a',
}


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
    (directory / 'tracks.tsv').write_text(
        'resource_id\tpath\tformat\nRoadmap/A.bed.gz\tinputs/bed/A.bed.gz\tbed-gzip\n'
        'Other/B.bed.gz\tinputs/bed/B.bed\tbed\n', encoding='utf-8', newline='\n')
    options = {'build': 'synthetic-v1', 'states': 's1,s2,s3', 'duplicates': 'error', 'single-state': 'error'}
    for key in ['annotation-id', 'consequence-id', 'cadd-id', 'rdb-id', 'state-id', 'reference-id']:
        options[key] = 'synthetic-' + key + '-v1'
    for key in ['snps', 'genes', 'annotations', 'consequences', 'ld', 'independent', 'reference-counts']:
        options[key] = 'inputs/' + key + '.tsv'
    for name, selected in [('minimal', 'none'), ('bed', 'tracks.tsv')]:
        recipe = dict(options, **{'bed-manifest': selected, 'out': name + '-result'})
        (directory / (name + '.json')).write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8')
    stage = dict(options, **{'bed-manifest': 'tracks.tsv'})
    del stage['build']
    (directory / 'parent.json').write_text(json.dumps({
        'schema_version': 1, 'build': 'synthetic-v1', 'out': 'parent-result', 'variant_annotation': stage,
    }, indent=2) + '\n', encoding='utf-8')
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256, 'members': MEMBERS,
        'scope': 'Synthetic numerical example only. No ANNOVAR prediction, software installation or biological resource acquisition.',
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
