# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package unchanged official ChIPseeker source guards and the measured reference dependency profile."""
import argparse
import csv
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

VERSIONS = ('1.22.1', '1.26.2', '1.34.1', '1.48.1')
CRITICAL = ('AnnotationDbi', 'BiocGenerics', 'GenomicFeatures', 'GenomicRanges', 'GenomeInfoDb',
            'IRanges', 'S4Vectors', 'Seqinfo', 'jsonlite', 'dplyr', 'magrittr', 'yulab.utils')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    destination = ROOT / 'src/main/resources/org/jlinalg/omics/chipseeker-annotation'
    assert not destination.exists()
    out.mkdir(); destination.mkdir(parents=True)
    controls = ROOT / 'build/literature-review/chipseeker-runtime/annotation-original-2'
    result = json.loads((controls / 'COMPLETED.json').read_bytes())
    assert result['calls'] == 200 and result['successes'] == 152 and result['original_errors'] == 48
    with (controls / '1.48.1/controls/installed-packages.tsv').open(encoding='utf-8', newline='') as stream:
        packages = {row['Package']: row['Version'] for row in csv.DictReader(stream, delimiter='\t')}
    critical = {name: packages[name] for name in CRITICAL}
    files = {}; editions = {}
    for version in VERSIONS:
        folder = ROOT / 'build/literature-review/chipseeker-runtime/source-intake-2' / version
        receipt = json.loads((folder / 'SOURCE.json').read_bytes())
        editions[version] = dict(source_url=receipt['source_url'], archive=receipt['archive'], license='Artistic-2.0')
        for name, expected in receipt['source_members'].items():
            if not (name.startswith('ChIPseeker/R/') or name in {'ChIPseeker/DESCRIPTION', 'ChIPseeker/NAMESPACE'}):
                continue
            source = folder / 'selected-source' / name
            assert pin(source) == expected
            relative = version + '/' + name.removeprefix('ChIPseeker/')
            target = destination / relative; target.parent.mkdir(exist_ok=True, parents=True)
            target.write_bytes(source.read_bytes()); files[relative] = pin(target)
    profile = dict(schema_version=1, profile='original-ChIPseeker-annotation-R461-current-dependencies-v1',
        r_version='4.6.1', editions=editions, required_packages=critical, files=files,
        source_guard_scope='Exact original ChIPseeker function bodies/formals and declared dependency versions. Not historical dependency environments, native-DLL source authentication or whole-paper equivalence.')
    save(destination / 'SOURCE-PROFILE.json', profile)
    license_file = Path('C:/Program Files/R/R-4.6.1/share/licenses/Artistic-2.0')
    (destination / 'LICENSE-Artistic-2.0.txt').write_bytes(license_file.read_bytes())
    (destination / 'NOTICE.txt').write_text('Original ChIPseeker audit sources are unchanged and retain their original author notices.\n'
        'License: Artistic-2.0; complete text in LICENSE-Artistic-2.0.txt. Full official source archive URLs and SHA-256 identities are in SOURCE-PROFILE.json.\n'
        'https://bioconductor.org/packages/ChIPseeker/\n'
        'These files audit installed original functions; the project does not substitute them as an installed upstream package.\n', encoding='utf-8')
    save(out / 'COMPLETED.json', dict(status='packaged-original-annotation-source-guards-not-adapter-admission',
        source=pin(Path(__file__)), reference_controls=pin(controls / 'COMPLETED.json'),
        resource_files={p.relative_to(ROOT).as_posix(): pin(p) for p in destination.rglob('*') if p.is_file()}, required_packages=critical))
    print(json.dumps(dict(files=len(files), required_packages=critical)))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source bindings')
    main()
