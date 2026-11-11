# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Python2 sidecar: capture unchanged original input parsing/QC only; never fit or modify MTAG."""
from __future__ import print_function
import codecs
import csv
import json
import os
import sys
import traceback


def u(value):
    if isinstance(value, unicode):
        return value
    if isinstance(value, str):
        return value.decode('utf8')
    return unicode(value)


def write_rows(path, header, rows):
    with open(path, 'wb') as stream:
        writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
        writer.writerow([u(x).encode('utf8') for x in header])
        for row in rows:
            writer.writerow([u(x).encode('utf8') for x in row])


def main():
    if len(sys.argv) != 4:
        raise RuntimeError('Require SOURCE_DIRECTORY OPTIONS_JSON NEW_OUTPUT_DIRECTORY')
    source, options_path, out = sys.argv[1:]
    if os.path.lexists(out):
        raise RuntimeError('Capture output exists; preserve it and choose a NEW directory')
    os.mkdir(out)
    sys.path.insert(0, source)
    import mtag
    import pandas as pd
    import numpy as np
    if mtag.__version__ != '1.0.8' or sys.version_info[:3] != (2, 7, 15):
        raise RuntimeError('Exact MTAG1.0.8/Python2.7.15 source profile required')
    with open(options_path, 'rb') as stream:
        # Original Python2 CLI argv consists of filesystem-encoded byte strings.
        # Preserve those semantics when transporting arguments through JSON.
        tokens = [x.encode('utf8') if isinstance(x, unicode) else x for x in json.load(stream)]
        args = mtag.parser.parse_args(tokens)
    if args.use_beta_se or args.meta_format:
        raise RuntimeError('This input sidecar does not bypass the disabled beta/SE mode or supply a union-of-traits workflow')
    paths = args.sumstats.split(',')
    raw_files, raw_sizes = [], []
    for trait, path in enumerate(paths):
        data, unused_chunks = mtag._read_GWAS_sumstats(path, args.chunksize)
        name = 'raw-trait-%03d.tsv' % (trait + 1)
        def values():
            for i in range(len(data)):
                def cell(column):
                    return data[column].iloc[i]
                ident = cell(args.snp_name)
                missing = bool(pd.isnull(ident))
                labels = [i + 1, 'true' if missing else 'false', '' if missing else u(ident)]
                for column in [args.chr_name, args.bpos_name, args.a1_name, args.a2_name]:
                    value = cell(column)
                    labels.append('' if pd.isnull(value) else u(value))
                for column in [args.z_name, args.n_name, args.eaf_name, args.p_name]:
                    labels.append(float(cell(column)).hex())
                labels.append(float(cell('info')).hex() if args.info_min is not None and 'info' in data.columns else '')
                yield labels
        write_rows(os.path.join(out, name), ['input_row','snp_missing','SNP','CHR','BP','A1','A2','Z_hex','N_hex','FRQ_hex','P_hex','INFO_hex'], values())
        raw_files.append(name); raw_sizes.append(len(data))
    # These are the original unmodified functions; this second preparation
    # pass fits no covariance/model and performs no maxFDR calculation.
    union, merged, args = mtag.load_and_merge_data(args)
    z, n, freq, template, effective, nraw = mtag.extract_gwas_sumstats(merged, args, list(np.arange(args.P)))
    if len(template) == 0 or z.shape != n.shape or z.shape != freq.shape:
        raise RuntimeError('Original effective input dimensions are empty or inconsistent')
    if n.shape != nraw.shape or not np.array_equal(n, nraw):
        raise RuntimeError('Unexpected sample-size rescaling in the original Z/N workflow')
    header = ['SNP','CHR','BP','A1','A2','strand_ambiguous']
    for trait in range(args.P):
        header += ['Z%d_hex' % trait, 'N%d_hex' % trait, 'FRQ%d_hex' % trait,
                   'CHR%d' % trait, 'BP%d' % trait, 'A1_%d' % trait, 'A2_%d' % trait]
    def values():
        for i in range(len(template)):
            row = [u(template[name].iloc[i]) for name in ['SNP','CHR','BP','A1','A2']]
            row.append('true' if bool(effective['strand_ambig'].iloc[i]) else 'false')
            for trait in range(args.P):
                row += [float(z[i,trait]).hex(), float(n[i,trait]).hex(), float(freq[i,trait]).hex()]
                row += [u(effective[name + str(trait)].iloc[i]) for name in ['CHR','BP','A1','A2']]
            yield row
    write_rows(os.path.join(out, 'effective-inputs.tsv'), header, values())
    effective.to_csv(os.path.join(out, 'original-merged-frame.tsv'), sep='\t', index=False, encoding='utf8')
    record = dict(status='CAPTURED_ORIGINAL_PARSING_AND_QC_ONLY_NO_FIT', tool='MTAG', version=mtag.__version__, python=sys.version.split()[0],
                  traits=args.P, variants=len(template), raw_files=raw_files, raw_rows=raw_sizes,
                  effective_file='effective-inputs.tsv', original_frame='original-merged-frame.tsv',
                  source_functions=['_read_GWAS_sumstats','load_and_merge_data','extract_gwas_sumstats'],
                  source_changed=False, analysis_or_covariance_refitted=False)
    with codecs.open(os.path.join(out, 'capture.json'), 'w', encoding='utf8') as stream:
        json.dump(record, stream, sort_keys=True, indent=2)
    print('Captured original MTAG parsing/QC for %d variants and %d traits; no fit' % z.shape)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('FAIL2: no MTAG prepared-input admission: ' + repr(error), file=sys.stderr)
        traceback.print_exc()
        sys.exit(2)
