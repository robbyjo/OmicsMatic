# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extract complete original Salmon output-contract families from the immutable native checkpoint."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/literature/salmon-native-development'
OUT = ROOT / 'src/test/resources/org/jlinalg/cli/salmon-output-contracts'


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:
        raise RuntimeError('Never disable immutable original checkpoint checks')
    proof = json.loads((SOURCE / 'VALIDATION.json').read_bytes())
    assert pin((SOURCE / 'controls.zip').read_bytes())['sha256'] == proof['archive']['sha256'] and not OUT.exists()
    OUT.mkdir(parents=True)
    files, cases = {}, []
    with zipfile.ZipFile(SOURCE / 'controls.zip') as original, zipfile.ZipFile(OUT / 'original-output-contracts.zip', 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        def capture(source, name):
            raw = original.read(source)
            assert pin(raw) == proof['archive_files'][source]
            target.writestr(name, raw)
            files[name] = pin(raw)
            return raw

        population = json.loads(capture('evidence/reference-protocols-3/inputs/population.json', 'population.json'))
        capture('evidence/reference-protocols-3/inputs/transcripts.fa', 'transcripts.fa')
        for family in proof['original_reference_validation']['runs']:
            version = family['version']
            capture('evidence/reference-protocols-3/' + version + '/index/info.json', version + '/index-info.json')
            for fit in family['quantifications']:
                name = version + '/' + fit['mode']
                prefix = 'evidence/reference-protocols-3/' + name + '/'
                assert fit['call']['exit_code'] == 0
                argv = fit['call']['command']
                start = argv.index('quant') - 2
                assert argv[start].endswith('/bin/salmon') and argv[start + 1] == '--no-version-check'
                argv = argv[start:]
                meta = json.loads(capture(prefix + 'aux_info/meta_info.json', name + '/aux_info/meta_info.json'))
                for relative in ['quant.sf', 'cmd_info.json']:
                    capture(prefix + relative, name + '/' + relative)
                alignment = fit['mode'].startswith('alignment-')
                if not alignment:
                    capture(prefix + 'lib_format_counts.json', name + '/lib_format_counts.json')
                if fit['bootstraps'] > 0:
                    for relative in ['aux_info/bootstrap/names.tsv.gz', 'aux_info/bootstrap/bootstraps.gz']:
                        capture(prefix + relative, name + '/' + relative)
                cases.append(dict(name=name, version=version, mode=fit['mode'], argv=argv, alignment=alignment, paired=not fit['mode'].startswith('single-'),
                                  bootstraps=fit['bootstraps'], processed_fragments=population['alignment_groups'] if alignment else population['paired_fragments']))
                assert meta['num_processed'] == cases[-1]['processed_fragments']
    assert len(cases) == 25
    provenance = dict(status='complete-original-output-contract-fixtures-not-process-admission', archive=pin((OUT / 'original-output-contracts.zip').read_bytes()), files=files, cases=cases,
                      producer=pin(Path(__file__).read_bytes()), source_validation=pin((SOURCE / 'VALIDATION.json').read_bytes()),
                      source_archive_sha256=proof['archive']['sha256'], scope='Twenty-five completed original protocols over one reused controlled population. Full raw command/metadata, quantification tables, '
                      'independent original assigned counters and complete requested bootstrap streams. Output reading alone does not admit process/host/input provenance, user analysis or biological accuracy.')
    (OUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    (OUT / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    print(json.dumps(dict(cases=len(cases), files=len(files), archive=provenance['archive'])))


if __name__ == '__main__':
    main()
