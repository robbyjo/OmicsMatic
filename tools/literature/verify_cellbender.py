#!/usr/bin/env python3
"""Fit the public synthetic CellBender example and independently check original reference counts.

Uses Python standard library, a supplied project JAR and a previously admitted
private original runtime. Never downloads, installs or resumes a checkpoint.
"""
import argparse
import csv
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def matrix(path):
    opener = gzip.open if path.name.endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8') as stream:
        if stream.readline().strip() != '%%MatrixMarket matrix coordinate integer general':
            raise ValueError('Expected integer Matrix Market reference')
        lines = (line.strip() for line in stream if line.strip() and not line.lstrip().startswith('%'))
        genes, cells, nnz = map(int, next(lines).split())
        if genes < 1 or cells < 1 or genes > 200 or cells > 2000 or nnz > 200000:
            raise ValueError('Synthetic fixture matrix exceeds bounds')
        values = {}
        seen = 0
        for line in lines:
            gene, cell, value = map(int, line.split())
            if not 1 <= gene <= genes or not 1 <= cell <= cells or value < 0:
                raise ValueError('Invalid count coordinate')
            key = (cell-1, gene-1)
            values[key] = values.get(key, 0)+value
            seen += 1
        if seen != nnz:
            raise ValueError('Truncated or extra matrix coordinates')
    return genes, cells, {key: value for key, value in values.items() if value}


def count_hash(values):
    digest = hashlib.sha256()
    for (cell, gene), value in sorted(values.items()):
        digest.update(f'{cell}\t{gene}\t{value}\n'.encode('utf-8'))
    return digest.hexdigest()


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    try:
        out.mkdir(parents=True, exist_ok=False)
    except OSError as failure:
        print('ANALYSIS FAILED: '+str(failure)+'\nRequired action: choose a NEW writable --out directory.', file=sys.stderr)
        return 2
    record = dict(status='running', completed_workflows=0, scope='One synthetic original0.4.0 CPU full/MCKP reference;no real-data accuracy or paper reproduction.')
    code = 2
    try:
        java = shutil.which(args.java)
        if java is None or not args.jar.is_file():
            code = 3
            record.update(status='skipped-dependency-unavailable', error='Supplied Java executable or project JAR unavailable.')
            return code
        fixture = Path(__file__).resolve().parents[2]/'examples/cellbender-tools'
        reference = json.loads((fixture/'reference.json').read_text(encoding='utf-8'))
        if reference['schema_version'] != 1:
            raise ValueError('Unsupported fixture reference schema')
        for name, digest in reference['input_sha256'].items():
            if Path(name).name != name or sha(fixture/'raw-counts'/name) != digest:
                raise ValueError('Synthetic source fixture hash differs')
        options = json.loads((fixture/'options.json').read_text(encoding='utf-8'))
        for key in ['matrix', 'features', 'barcodes']:
            options[key] = str(fixture/options[key])
        options.update(home=str(args.home.absolute()), out=str(out/'analysis'))
        carrier = out/'options.json'
        carrier.write_text(json.dumps(options, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
        command = [java, '-Xmx1536m', '-jar', str(args.jar.absolute()), 'cellbender-tools', '--cellbender-options-file', str(carrier), '--no-config']
        record.update(candidate_sha256=sha(args.jar), reference_sha256=sha(fixture/'reference.json'), command=command)
        # The adapter enforces its own timeout and joins owned descendants.
        # Keep its Java parent alive so cleanup can complete and be recorded.
        with (out/'stdout').open('wb') as stdout, (out/'stderr').open('wb') as stderr:
            result = subprocess.run(command, stdout=stdout, stderr=stderr)
        record['analysis_exit_code'] = result.returncode
        if result.returncode == 3:
            code = 3
            record.update(status='skipped-dependency-unavailable', error='Original runtime unavailable. Read analysis/WARNINGS.md and session.json for the exact cause.')
            return code
        if result.returncode != 0:
            raise ValueError('Analysis failed;read retained analysis/WARNINGS.md,session.json and partial/ logs')
        analysis = out/'analysis'
        if json.loads((analysis/'analysis-status.json').read_text())['status'] != 'completed-with-review-warnings':
            raise ValueError('No completed audited model fit was published')
        genes, cells, raw = matrix(fixture/'raw-counts/matrix.mtx.gz')
        out_genes, out_cells, corrected = matrix(analysis/'full-corrected.mtx')
        if (out_genes, out_cells) != (genes, cells) or any(v > raw.get(k, 0) for k, v in corrected.items()):
            raise ValueError('Count conservation or full dimensions differ')
        if count_hash(corrected) != reference['canonical_corrected_count_sha256']:
            raise ValueError('Corrected coordinates differ from the independent original reference')
        barcodes = (analysis/'full-barcodes.tsv').read_text(encoding='utf-8').splitlines()
        selected = (analysis/'filtered-barcodes.tsv').read_text(encoding='utf-8').splitlines()
        if len(barcodes) != cells or len(set(barcodes)) != cells or len(set(selected)) != len(selected):
            raise ValueError('Barcode population invalid')
        probabilities = {row['cell_id']: float(row['probability']) for row in table(analysis/'model-probabilities.tsv')}
        if probabilities != reference['cell_probabilities'] or set(selected) != {key for key, value in probabilities.items() if value > .5}:
            raise ValueError('Original reference probabilities or strict>0.5 calls differ')
        fg, fc, filtered = matrix(analysis/'filtered-corrected.mtx')
        indices = {barcode: index for index, barcode in enumerate(barcodes)}
        projected = {(i, gene): value for i, barcode in enumerate(selected) for (cell, gene), value in corrected.items() if cell == indices[barcode]}
        if (fg, fc) != (genes, len(selected)) or filtered != projected:
            raise ValueError('Filtered corrected counts differ from full matrix subsetting')
        marginals = table(analysis/'cell-counts.tsv')
        for i, row in enumerate(marginals):
            if row['cell_id'] != barcodes[i] or int(row['raw_UMIs']) != sum(v for (cell, gene), v in raw.items() if cell == i) or int(row['corrected_UMIs']) != sum(v for (cell, gene), v in corrected.items() if cell == i):
                raise ValueError('Independent cell marginal differs')
        if len(marginals) != cells:
            raise ValueError('Incomplete cell marginal ledger')
        expected = reference['expected']
        called_indices = {indices[barcode] for barcode in selected}
        actual = dict(all_barcodes=cells, features=genes, all_raw=sum(raw.values()), all_corrected=sum(corrected.values()), called=len(selected), called_raw=sum(v for (cell, gene), v in raw.items() if cell in called_indices), called_corrected=sum(v for (cell, gene), v in corrected.items() if cell in called_indices), analyzed=len(probabilities))
        if actual != expected:
            raise ValueError('Original reference population totals differ')
        for name, digest in reference['input_sha256'].items():
            if sha(fixture/'raw-counts'/name) != digest:
                raise ValueError('Original fixture input changed during analysis')
        session = json.loads((analysis/'session.json').read_text(encoding='utf-8'))
        if session['fit_process']['alive_after_cleanup'] is not False:
            raise ValueError('Owned process cleanup is not confirmed')
        record.update(status='passed-original-reference-counts-probabilities-and-independent-audit', completed_workflows=1, totals=actual, canonical_corrected_count_sha256=count_hash(corrected))
        code = 0
        return code
    except Exception as failure:
        record.update(status='failed', error=type(failure).__name__+': '+str(failure))
        return code
    finally:
        record['exit_code'] = code
        record['required_action'] = 'Read this verification.json plus analysis/WARNINGS.md,session.json,count ledgers and original report/logs. A missing,unsupported,changed or forbidden runtime SKIPS this analysis(exit3). Use Linux Java/Ubuntu24.04 x86_64 with a previously admitted private home;install pinned software only where permitted with install_cellbender.py. Correct failures(exit2),preserve all attempts and rerun to a NEW output directory. No automatic download/install,Cell Ranger execution,GPU or historical/full-paper equivalence.'
        (out/'verification.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        (out/'WARNINGS.md').write_text('# CELLBENDER '+('ANALYSIS SKIPPED' if code == 3 else 'VERIFICATION FAILED' if code else 'REVIEW REQUIRED')+'\n\n'+record.get('error', record['scope'])+'\n\n'+record['required_action']+'\n', encoding='utf-8')
        print(record['status']+';completed workflows='+str(record['completed_workflows'])+';read '+str(out/'WARNINGS.md'), file=sys.stderr)


if __name__ == '__main__':
    sys.exit(main())
