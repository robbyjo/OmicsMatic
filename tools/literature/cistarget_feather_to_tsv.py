# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export every motif and feature from a cisTarget Feather ranking database to native TSV."""
import argparse,csv,hashlib,json,traceback
from pathlib import Path


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')


def identifier(value):
    if not isinstance(value,str) or not value or value!=value.strip() or any(ord(c)<32 or ord(c)==127 for c in value):raise ValueError('Empty, padded or control-character identifier; no identifier is repaired')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--database',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--max-cells',type=int,required=True,help='Explicit positive full-matrix export capacity; larger databases are refused without subsetting')
    args=parser.parse_args();out=args.out.absolute()
    if out.exists() or out.is_symlink():parser.error('Output exists; preserve it and choose NEW --out')
    if not out.parent.is_dir() or out.parent.is_symlink():parser.error('Output parent must exist and not be a symlink')
    out.mkdir();state=dict(status='SKIP3_FEATHER_CONVERSION_RUNTIME_UNAVAILABLE',dependent_analysis_admitted=False,software_installed=False,whole_paper_reproduced=False)
    try:
        # Analysis never performs an implicit install. Use a permitted private runtime.
        import pyarrow as pa
        import pyarrow.feather as feather
        if pa.__version__!='17.0.0':raise ImportError('This converter is validated with pyarrow17.0.0; provide that version in a permitted private Python runtime')
        state['status']='FAIL2_FEATHER_CONVERSION_NOT_ADMITTED'
        database=args.database.absolute()
        if not database.is_file() or database.is_symlink():raise ValueError('Database must be a regular file, not a symlink')
        if not any(database.name.endswith('.'+suffix+'.rankings.feather') for suffix in ['genes_vs_motifs','regions_vs_motifs']):raise ValueError('Only genes_vs_motifs/regions_vs_motifs ranking orientation is supported; scores and transposed matrices are refused')
        before=pin(database);table=feather.read_table(database,memory_map=True,use_threads=False)
        if len(set(table.column_names))!=len(table.column_names) or table.column_names.count('motifs')!=1:raise ValueError('Expected unique feature columns and exactly one motifs column')
        features=[name for name in table.column_names if name!='motifs'];motifs=table.column('motifs').to_pylist()
        if len(features)<2 or len(motifs)<2 or args.max_cells<1 or len(features)*len(motifs)>args.max_cells:raise ValueError('Full matrix is empty/too small or exceeds explicit --max-cells; no motif/feature is truncated')
        if len(set(motifs))!=len(motifs):raise ValueError('Duplicate motif identifiers')
        for value in [*features,*motifs]:identifier(value)
        for name in features:
            if table.column(name).type not in [pa.int16(),pa.int32()]:raise ValueError('Ranking columns must all be int16/int32; scores/nonintegral ranks are not converted')
            if table.column(name).null_count:raise ValueError('Missing rank cells are not imputed')
        columns=[table.column(name).to_numpy(zero_copy_only=False) for name in features]
        with (out/'rankings.partial.tsv').open('x',encoding='utf8',newline='') as stream:
            writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(['motif',*features])
            for row,motif in enumerate(motifs):
                values=[int(column[row]) for column in columns]
                if any(value<0 or value>=len(features) for value in values):raise ValueError('Rank outside zero-based full database domain; no rank-base shift is inferred')
                writer.writerow([motif,*values])
        if pin(database)!=before:raise ValueError('Source database changed during export; no conversion is admitted')
        partial=out/'rankings.partial.tsv';state.update(status='PASS_COMPLETE_FEATHER_RANKINGS_TO_TSV_ONLY',database=str(database),database_pin=before,
            output_pin=pin(partial),background_motifs=len(motifs),database_features=len(features),rank_base_transformation='none',
            all_motifs_and_features_preserved=True,pyarrow_version=pa.__version__,max_cells=args.max_cells,converter_pin=pin(Path(__file__)),
            required_action='Require PASS receipt and rankings.tsv, then run native Java with explicit source profile, signature, thresholds and annotations. Preserve this receipt alongside the native run.')
        save(out/'COMPLETED.json',state)
        (out/'WARNINGS.md').write_text('# CONVERSION WARNINGS\n\nAll motifs and features are exported. No DNA scoring, rank-base conversion, annotation transfer, genome conversion or enrichment is performed. The original file remains unchanged.\n\nRequired action: preserve COMPLETED.json and its input/output hashes with the native result; conversion alone admits no analysis. Use an organism/build/motif-collection-compatible signature and annotation table.\n',encoding='utf8')
        partial.rename(out/'rankings.tsv')
    except Exception as failure:
        if isinstance(failure,(ImportError,ModuleNotFoundError)):state['status']='SKIP3_FEATHER_CONVERSION_RUNTIME_UNAVAILABLE'
        state.update(error=repr(failure),required_action='Skip this conversion and all dependent Feather-based analyses. If permitted, install pyarrow17.0.0 in a NEW private Python environment and retry NEW paths; otherwise supply a complete TSV database to native Java. Correct named schema/capacity/integrity problems without filtering motifs, repairing identifiers or overwriting failed attempts.')
        save(out/'COMPLETED.json',state);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
        (out/'WARNINGS.md').write_text('# CONVERSION SKIPPED OR FAILED - NO ANALYSIS ADMITTED\n\n'+state['error']+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8')
    print(state['status']);print('WARNINGS AND REQUIRED ACTION:',out/'WARNINGS.md');return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__=='__main__':raise SystemExit(main())
