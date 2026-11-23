"""Run prepared SCENIC+ network inference on this wholly artificial fixture."""
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
    parser.add_argument('--interface', choices=('CLI_TSV', 'IN_MEMORY_API'))
    args = parser.parse_args()
    out = args.out.absolute()
    if out.exists() or not out.parent.is_dir():
        parser.error('--out must be NEW under an existing directory')
    request = dict(schema_version=1, mode=args.mode, out=str(out))
    if args.mode == 'RUN':
        if args.python is None or args.interface is None:
            parser.error('RUN requires --python and an explicit --interface matching the intended analysis')
        source = Path(__file__).resolve().parent
        files = dict(expression='expression.tsv', accessibility='accessibility.tsv', search_space='search-space.tsv',
                     tfs='tfs.txt', cistromes='cistromes.h5ad', ranking_db='artificial.regions_vs_motifs.rankings.feather')
        request.update(profile='scenicplus-1.0a2-network-v1', operation='NETWORK', python=str(args.python.absolute()),
                       inputs={key:str(source / value) for key, value in files.items()},
                       options=dict(interface=args.interface, tf_method='GBM', tf_seed=17,
                                    r2g_correlation='SR', mask_expr_dropout=False,
                                    egrn=dict(seed=17, gsea_n_perm=100, top_n_regionTogenes_per_gene=[2, 3],
                                              min_target_genes=5, merge_eRegulons=True),
                                    activity=dict(auc_threshold=.05, normalize=False)))
    else:
        if args.analysis is None or args.interface is not None:
            parser.error('VERIFY/REPLAY requires --analysis and inherits its interface and scientific settings')
        request['analysis'] = str(args.analysis.absolute())
        if args.python is not None:
            request['python'] = str(args.python.absolute())
    request_path = out.with_name(out.name + '.request.json')
    with request_path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(request, stream, indent=2, ensure_ascii=False, allow_nan=False)
        stream.write('\n')
    return subprocess.run([args.java, '-cp', str(args.jar.absolute()), 'org.jlinalg.cli.ScenicPlusNetworkCli',
                           '--request', str(request_path)], stdin=subprocess.DEVNULL).returncode


if __name__ == '__main__':
    sys.exit(main())
