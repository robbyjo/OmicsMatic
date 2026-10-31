"""Write synthetic precomputed expression/DEG resources into a NEW directory."""
from pathlib import Path
import argparse
import json


def prepare(directory):
    directory = Path(directory).resolve()
    directory.mkdir(parents=True, exist_ok=False)
    resources = {
        'symbols.tsv': 'ensg\tsymbol\nE1\tAlpha\nE2\tBeta\nE3\tGamma\nE4\tDelta\n',
        'query.txt': 'E1\nE2\n',
        'background.txt': 'E1\nE2\nE3\nE4\n',
        'log2.tsv': 'ensg\tLiver\tBrain\nE3\t0.0\t3.5\nE2\t10.25\t0.0\nE1\t-1.5\t1.0\nE4\t0.0\t0.0\n',
        'norm.tsv': 'ensg\tLiver\tBrain\nE3\t-0.5\t0.5\nE2\t1.0\t-1.0\nE1\t-2.0\t2.0\nE4\t0.0\t0.0\n',
        'deg.txt': 'all\t4\tE1:E2:E3:E4\nLiver.up\t99\tE1:E2\nBrain.up\t0\t\nBrain.down\t2\tE3:E4\nLiver.twoside\t2\tE1:E2\n',
    }
    for name, text in resources.items():
        (directory / name).write_text(text, encoding='utf-8', newline='\n')
    options = {
        'build': 'synthetic-v1', 'annotation-id': 'synthetic-symbols-v1',
        'expression-id': 'synthetic-precomputed-values-v1', 'deg-id': 'synthetic-memberships-v1',
        'symbols': 'symbols.tsv', 'query': 'query.txt', 'background': 'background.txt',
        'log2': 'log2.tsv', 'norm': 'norm.tsv', 'deg-sets': 'deg.txt',
        'duplicates': 'error', 'outside-query': 'error', 'out': 'result',
    }
    (directory / 'options.json').write_text(json.dumps(options, indent=2) + '\n', encoding='utf-8')
    stage = {k: v for k, v in options.items() if k not in {'out', 'build'}}
    (directory / 'workflow.json').write_text(json.dumps({
        'schema_version': 1, 'build': options['build'], 'out': 'workflow-result', 'expression': stage,
    }, indent=2) + '\n', encoding='utf-8')
    return directory


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    try:
        print(prepare(args.out))
    except (OSError, ValueError) as failure:
        parser.exit(2, f'INPUT PREPARATION FAILED: {failure}\nRequired action: choose a NEW writable directory.\n')
