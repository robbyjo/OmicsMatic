"""Verify and prepare synthetic MAGMA tissue inputs in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = '144b2bdbe3de33d492c6cb09124e1053c17a5792d39d0ea1c17dab3b8a6a747b'
MEMBERS = {'gene-bundle/gene-status.tsv': '6af237cc717582b515d0e86e4bc949202af16bcc8e192e9d6114faebd7005dac', 'gene-bundle/genes.raw': '97a0703caf4475e1e14fdadd4ecdd9186f0703d9db30ec483994331a9be7c54f', 'gene-bundle/magma-bundle.json': 'da62f1f1ca6f06dc82af772a7b3fc87a17ea578d8a70d94d235e3a2c23d4ec93', 'gene-bundle/results.tsv': '6dc2612df3b6aff179e29e23d7a431e62c8496685c5bfbd490ce9983a4148cfe', 'properties/detailed54.tsv': '12e2e33c7bda60ea24f99ceb82a782bb1fd37093f1afcb63610d891824ac421c', 'properties/general30.tsv': 'baddb9774ee11a20436b49f4a9a2e126f2095a044fd205feddeea44446519997', 'properties/long-labels.tsv': '4d2baeaaf3783441e7fc3fae0e7616be06b4182a180a7df0f853771287747a09', 'properties/missing.tsv': '78fa82a24ac685f17bec9ca56fe5de7010b885906eb7f8d94e1a5a697452198b', 'reference/detailed.gsa.out.txt': '52f0a0ffd1eac6e9e2b84ab0f84175150fdab483515d69dc89b929a9cca6dcbe', 'reference/drop.gsa.out.txt': '93238332c7a7ea1dc0f314c6a9bca9f31691e286ceb7acb5df441a6581818d4c', 'reference/general.gsa.out.txt': '08661771e1764928c4ff5cbcf67452d7ab73619b1ee4e725a06db02854770d22', 'reference/long.gsa.out.txt': 'be8b58e93c0b1f305a4daa1ba6badf0d420c9a037678213a03797cede8ad7059', 'reference/mean.gsa.out.txt': '35efa734ac3d7b8fba8e23e2e3c645eb4e550bf43e7563c9cb6aa2173ca28e23', 'reference/step1.tsv': '736a1084244e889130333b2462732019a8db41bbb81b48012b2ef074a6175aec', 'source-provenance.json': 'd12ef20f7288f79239a94ff611dba64c64f794d628ae2b653139340e54ffd50b'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(directory, home):
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if digest(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Synthetic archive SHA-256 mismatch; restore the pinned example')
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
        path = directory / name  # Only the fixed exact allowlist above is written.
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    options = {'method': 'properties', 'build': 'GRCh38', 'home': str(Path(home).resolve()),
               'gene-bundle': 'gene-bundle', 'property-direction': 'positive',
               'condition-hide': 'Average', 'seed': '42', 'missing-values': 'error', 'max-missing': '0'}
    recipes = {}
    for name, filename, missing, maximum in [
            ('detailed', 'detailed54', 'error', '0'), ('general', 'general30', 'error', '0'),
            ('long', 'long-labels', 'error', '0'), ('mean', 'missing', 'mean', '.2'),
            ('drop', 'missing', 'drop', '.2')]:
        recipes[name] = dict(options, properties='properties/' + filename + '.tsv',
                             out=name + '-result', **{'missing-values': missing, 'max-missing': maximum})
    (directory / 'recipes.json').write_text(json.dumps(recipes, indent=2) + '\n', encoding='utf-8')
    stage = dict(recipes['detailed'])
    del stage['out']
    del stage['build']
    (directory / 'parent.json').write_text(json.dumps({'schema_version': 1, 'build': 'GRCh38',
        'out': 'parent-result', 'magma_enrichment': stage}, indent=2) + '\n', encoding='utf-8')
    (directory / 'datasets.tsv').write_text(
        'dataset\tresource_id\tpath\nDetail54\tsynthetic-Detail54-v1\tproperties/detailed54.tsv\n'
        'General30\tsynthetic-General30-v1\tproperties/general30.tsv\n', encoding='utf-8', newline='\n')
    marginal = {'runtime-profile': 'windows-x86_64-static', 'home': options['home'],
                'gene-bundle': 'gene-bundle', 'datasets': 'datasets.tsv', 'build': 'GRCh38',
                'expression-id': 'synthetic-tissue-families-explicit', 'correction': 'BH',
                'step2': 'false', 'step3': 'false', 'gene-coverage': 'error',
                'missing-values': 'error', 'out': 'marginal-result'}
    (directory / 'marginal.json').write_text(json.dumps(marginal, indent=2) + '\n', encoding='utf-8')
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256, 'members': MEMBERS,
        'scope': 'Synthetic supplied tissue matrices and original fits only. No expression/Average calculation, resource acquisition, installation or runtime admission.',
    }, indent=2) + '\n', encoding='utf-8')
    return directory


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--home', required=True, type=Path)
    args = parser.parse_args()
    try:
        print(prepare(args.out, args.home))
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        parser.exit(2, f'INPUT PREPARATION FAILED: {failure}\nRequired action: restore the pinned example if needed and choose a NEW writable directory.\n')
