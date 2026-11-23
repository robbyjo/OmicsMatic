"""Run the project's original SCENIC+ R2G development entry point on artificial data."""
from pathlib import Path
import argparse
import json
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--python', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--mode', choices=('RUN', 'VERIFY', 'REPLAY'), default='RUN')
    parser.add_argument('--analysis', type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    if not out.parent.is_dir() or out.exists():
        parser.error('--out must be NEW and its parent must already exist')
    request = dict(schema_version=1, mode=args.mode, out=str(out))
    if args.mode == 'RUN':
        if args.python is None:
            parser.error('RUN requires --python pointing to the permitted installed Linux runtime')
        source = Path(__file__).resolve().parent
        request.update(profile='scenicplus-1.0a2-r2g-v1', operation='REGION_TO_GENE',
                       python=str(args.python.absolute()),
                       inputs=dict(expression=str(source / 'expression.tsv'),
                                   accessibility=str(source / 'accessibility.tsv'),
                                   search_space=str(source / 'search-space.tsv')),
                       options=dict(correlation='SR', mask_expr_dropout=False))
    else:
        if args.analysis is None:
            parser.error('VERIFY/REPLAY requires --analysis pointing to the read-only parent')
        request['analysis'] = str(args.analysis.absolute())
        if args.python is not None:
            request['python'] = str(args.python.absolute())
    path = out.with_name(out.name + '.request.json')
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(request, stream, ensure_ascii=False, allow_nan=False, indent=2)
        stream.write('\n')
    return subprocess.run([args.java, '-cp', str(args.jar.absolute()),
                           'org.jlinalg.cli.ScenicPlusToolsCli', '--request', str(path)],
                          stdin=subprocess.DEVNULL).returncode


if __name__ == '__main__':
    sys.exit(main())
