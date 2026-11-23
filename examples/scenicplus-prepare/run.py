"""Invoke original unpaired SCENIC+ preparation through the complete Java JAR."""
from pathlib import Path
import argparse
import json
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
for option in ('jar', 'python', 'rna', 'cistopic', 'out'):
    parser.add_argument('--' + option, type=Path, required=True)
parser.add_argument('--group-key', required=True)
parser.add_argument('--use-raw', choices=('true', 'false'), required=True)
parser.add_argument('--metacells', type=int, help='Omit to use original automatic counts')
parser.add_argument('--cells-per-metacell', type=int, default=10)
parser.add_argument('--separator', default='_')
args = parser.parse_args()
request = dict(schema_version=1, mode='RUN', profile='scenicplus-1.0a2-prepare-v1',
               operation='UNPAIRED', python=str(args.python.absolute()),
               out=str(args.out.absolute()),
               inputs=dict(rna=str(args.rna.absolute()), cistopic=str(args.cistopic.absolute())),
               options=dict(group_key=args.group_key, use_raw=args.use_raw == 'true',
                            nr_metacells=args.metacells, cells_per_metacell=args.cells_per_metacell,
                            separator=args.separator, scale_factor=1000000))
request_path = args.out.absolute().with_name(args.out.name + '.request.json')
with request_path.open('x', encoding='utf-8') as stream:
    json.dump(request, stream, indent=2)
    stream.write('\n')
sys.exit(subprocess.run(['java', '-cp', str(args.jar), 'org.jlinalg.cli.ScenicPlusPrepareCli',
                         '--request', str(request_path)], check=False).returncode)
