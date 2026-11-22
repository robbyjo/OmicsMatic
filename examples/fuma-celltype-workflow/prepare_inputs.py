"""Verify and prepare synthetic classic FUMA cell-type inputs in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = 'd7d0f632f4485fd41331eb2686f4a98cfe182314b8d2582956e0223746c40cce'
MEMBERS = {
    'gene-bundle/gene-status.tsv': '6af237cc717582b515d0e86e4bc949202af16bcc8e192e9d6114faebd7005dac',
    'gene-bundle/genes.raw': '97a0703caf4475e1e14fdadd4ecdd9186f0703d9db30ec483994331a9be7c54f',
    'gene-bundle/magma-bundle.json': 'da62f1f1ca6f06dc82af772a7b3fc87a17ea578d8a70d94d235e3a2c23d4ec93',
    'gene-bundle/results.tsv': '6dc2612df3b6aff179e29e23d7a431e62c8496685c5bfbd490ce9983a4148cfe',
    'properties/DataA.tsv': '926fe12add1c11c05b1c41e28e47cf58a21c09be15aca566bdf82a95e2ec4ebd',
    'properties/DataB.tsv': '760a0d2ea9edc1f34c8d35f0c6390b626af54582ee7e61a86f4bca53d3be236b',
    'reference/step1-2-summary.tsv': 'd27856dd95b44a31c56569835640f323f61f37ef04765514e5650431807ec1cd',
    'reference/step1.tsv': 'dd4c32e32cb1f597bf77717281c5936341aef65eb6352642177c673565b0f7a7',
    'reference/step2.tsv': 'c79b582cea02158c1184b10d66553c4b968de3ae92b05da51a8a1f4e272a3371',
    'reference/step3.tsv': '89f4d65da0275eb50870fd84c578a42ef19c10b77b8576ea18c6848d1ec89d19',
    'source-provenance.json': '23c8251980a9f09204712982d96214c90ae6d6cbb839b8c0704a241e284ab21c',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(directory, home):
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if digest(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Synthetic archive SHA-256 mismatch; restore the checked-in archive')
    contents = {}
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) != len(MEMBERS) or {e.filename for e in entries} != set(MEMBERS):
            raise ValueError('Synthetic archive member population differs from its exact allowlist')
        for entry in entries:
            if entry.file_size > 1048576 or not stat.S_ISREG(entry.external_attr >> 16):
                raise ValueError('Oversized or nonregular synthetic archive member: ' + entry.filename)
            data = source.read(entry)
            if digest(data) != MEMBERS[entry.filename]:
                raise ValueError('Synthetic member SHA-256 mismatch: ' + entry.filename)
            contents[entry.filename] = data
    directory = Path(directory).resolve()
    directory.mkdir(parents=True, exist_ok=False)
    for name, data in contents.items():
        path = directory / name  # Exact fixed allowlist above; no arbitrary archive paths.
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    (directory / 'datasets.tsv').write_text(
        'dataset\tresource_id\tpath\nDataA\tsynthetic-DataA-v1\tproperties/DataA.tsv\n'
        'DataB\tsynthetic-DataB-v1\tproperties/DataB.tsv\n', encoding='utf-8', newline='\n')
    options = {
        'runtime-profile': 'windows-x86_64-static', 'home': str(Path(home).resolve()),
        'gene-bundle': 'gene-bundle', 'datasets': 'datasets.tsv', 'build': 'GRCh38',
        'expression-id': 'synthetic-two-dataset-v1', 'correction': 'BH',
        'gene-coverage': 'error', 'missing-values': 'error',
    }
    for name, step2, step3 in [('marginal', False, False), ('within', True, False), ('cross', True, True)]:
        recipe = dict(options, step2=str(step2).lower(), step3=str(step3).lower(), out=name + '-result')
        (directory / (name + '.json')).write_text(json.dumps(recipe, indent=2) + '\n', encoding='utf-8')
    stage = dict(options, step2='true', step3='true')
    del stage['build']
    parent = dict(schema_version=1, build='GRCh38', out='parent-result', cell_type=stage)
    (directory / 'parent.json').write_text(json.dumps(parent, indent=2) + '\n', encoding='utf-8')
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256, 'members': MEMBERS,
        'scope': 'Synthetic numerical example only. No installation or runtime admission performed.',
    }, indent=2) + '\n', encoding='utf-8')
    return directory


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--home', required=True, type=Path, help='Private admitted original Windows MAGMA 1.10 home')
    args = parser.parse_args()
    try:
        print(prepare(args.out, args.home))
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        parser.exit(2, f'INPUT PREPARATION FAILED: {failure}\nRequired action: restore the pinned example if needed and choose a NEW writable directory.\n')
