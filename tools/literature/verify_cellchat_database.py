# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only structural/resource verification; no R execution or independent inference."""
import argparse,csv,json
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import pin

def verify(bundle):
    root=bundle.resolve();receipt=json.loads((root/'COMPLETED.json').read_bytes())
    assert receipt['status']=='completed-cellchat-database-export-only' and receipt['resource_export_complete'] is True and receipt['analysis_admitted'] is False
    assert receipt['source_files_unchanged'] is True and pin(root/'WARNINGS.md')==receipt['warnings']
    lock=json.loads(Path(__file__).with_name('cellchat-source-lock.json').read_bytes());source=lock['sources'][receipt['source_label']]
    assert (receipt['repository'],receipt['commit'])==(source['repository'],source['commit'])
    expected=set(receipt['output_files'])|{'COMPLETED.json','WARNINGS.md'}
    actual=set()
    for path in root.rglob('*'):
        assert not path.is_symlink(),'Symlink refused in completed export'
        if path.is_file():actual.add(path.relative_to(root).as_posix())
    assert expected==actual,'Incomplete or extra resource-export files'
    for name,wanted in receipt['output_files'].items():
        path=PurePosixPath(name)
        assert path.as_posix()==name and not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name
        assert pin(root/name)==wanted,(name,'changed output bytes')
    before=json.loads((root/'INPUTS-BEFORE.json').read_bytes());after=json.loads((root/'INPUTS-AFTER.json').read_bytes());assert before==after
    # Verify retained source bindings without requiring the original installation.
    wantedDB=source['files']['data/CellChatDB.'+receipt['species']+'.rda']
    assert sum(v==wantedDB for v in before.values())==1
    assert sum(v==source['files']['DESCRIPTION'] for v in before.values())==1
    assert (root/'R-COMPLETED.txt').read_text().strip()=='PASS_ORIGINAL_CELLCHAT_DATABASE_TABLES_ROUNDTRIP_ONLY'
    process=json.loads((root/'process.json').read_bytes());assert process['exit_code']==0
    index=receipt['table_index'];assert [r['table'] for r in index]==['interaction','complex','cofactor','geneInfo']
    for record in index:
        home=root/'tables'/record['table']
        with (home/'data.tsv').open(encoding='utf8',newline='') as stream:
            reader=csv.reader(stream,delimiter='\t');header=next(reader);rows=list(reader)
        assert header[0]=='resource_row' and len(header)==int(record['columns'])+1 and len(rows)==int(record['rows'])
        assert len({r[0] for r in rows})==len(rows) and all(len(r)==len(header) for r in rows)
        with (home/'missing-values.tsv').open(encoding='utf8',newline='') as stream:missing=list(csv.DictReader(stream,delimiter='\t'))
        assert len(missing)==int(record['missing_values'])
        for entry in missing:
            row=int(entry['row']);column=int(entry['column']);assert 1<=row<=len(rows) and 1<=column<len(header) and rows[row-1][column]=='NA'
    return receipt

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete resource verification')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--bundle',type=Path,required=True);args=parser.parse_args()
    report=verify(args.bundle);print('Verified original CellChat resource tables only: '+report['source_label']+'/'+report['species']+'; no analysis admitted')
