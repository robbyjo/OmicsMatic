# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export a pinned original CellChat database as tables. This performs no inference."""
import argparse,csv,json,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

class MissingRuntime(Exception):pass

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-label',required=True);p.add_argument('--source-root',type=Path,required=True)
    p.add_argument('--species',choices=['human','mouse','zebrafish'],required=True)
    p.add_argument('--rscript',type=Path,default=RSCRIPT);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve()
    if out.exists():p.error('Output exists. Preserve it and choose a NEW output directory.')
    out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_DATABASE_EXPORT',analysis_admitted=False,resource_export_complete=False,implementation_credit=False,whole_goal_complete=False)
    warning=out/'WARNINGS.md'
    def warnings(detail):
        warning.write_text('# '+state['status']+'\n\n'+detail+'\n\nRequired action: SKIP3 means a named source/runtime capability is unavailable; skip dependent analysis and supply that exact source/runtime where permitted. FAIL2 means input, integrity, process, capacity or finalization failed; admit no result. Preserve all files and retry with NEW output paths. Resource-only completion never admits a CellChat communication analysis or identifies a historical paper database.\n',encoding='utf8')
    warnings('Provisional export: no completion has been admitted.')
    try:
        lockPath=Path(__file__).with_name('cellchat-source-lock.json');lock=json.loads(lockPath.read_bytes())
        if a.source_label not in lock['sources']:raise MissingRuntime('Unsupported CellChat source label; choose one explicitly listed in cellchat-source-lock.json')
        wanted=lock['sources'][a.source_label];source=a.source_root.resolve();database='data/CellChatDB.'+a.species+'.rda'
        if database not in wanted['files']:raise MissingRuntime('This exact source edition does not supply the requested species database; no cross-edition fallback is allowed')
        selected=['DESCRIPTION',database]
        for name in selected:
            if not (source/name).is_file():raise MissingRuntime('Missing exact original source file: '+name)
            if pin(source/name)!=wanted['files'][name]:raise ValueError('Original source hash mismatch: '+name)
        if not a.rscript.is_file():raise MissingRuntime('Rscript is unavailable. Install base R where permitted and pass --rscript with its full executable path')
        script=Path(__file__).with_suffix('.R');paths=[lockPath,script,Path(__file__).resolve(),*(source/n for n in selected)]
        before={str(f.resolve()):pin(f) for f in paths};save(out/'INPUTS-BEFORE.json',before)
        command=[str(a.rscript.resolve()),'--vanilla',str(script),str(source/database),a.species,str(out)]
        with (out/'export.stdout').open('xb') as stdout,(out/'export.stderr').open('xb') as stderr:
            run=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Original database export failed; inspect export.stdout and export.stderr'
        assert (out/'R-COMPLETED.txt').read_text().strip()=='PASS_ORIGINAL_CELLCHAT_DATABASE_TABLES_ROUNDTRIP_ONLY'
        with (out/'table-index.tsv').open(newline='',encoding='utf8') as stream:index=list(csv.DictReader(stream,delimiter='\t'))
        assert [r['table'] for r in index]==['interaction','complex','cofactor','geneInfo'] and all(int(r['rows'])>0 for r in index)
        after={name:pin(Path(name)) for name in before};save(out/'INPUTS-AFTER.json',after);assert before==after,'An input/source changed during export'
        output_files={f.relative_to(out).as_posix():pin(f) for f in out.rglob('*') if f.is_file() and f.name not in {'COMPLETED.json','WARNINGS.md'}}
        state.update(status='completed-cellchat-database-export-only',resource_export_complete=True,source_label=a.source_label,repository=wanted['repository'],commit=wanted['commit'],species=a.species,table_index=index,source_files_unchanged=True,output_files=output_files)
        warnings('Resource export only: tables, order, exact identifiers, missing-value positions and component columns passed a base-R roundtrip. No alias mapping, merged database, protein-only filtering, interaction selection, normalization or communication inference was performed.')
    except MissingRuntime as error:
        state.update(status='SKIP3_CELLCHAT_DATABASE_SOURCE_OR_RUNTIME_UNAVAILABLE',detail=str(error));warnings(str(error))
    except Exception as error:
        state['detail']=str(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');warnings(str(error))
    state['warnings']=pin(warning)
    save(out/'COMPLETED.json',state)
    print(state['status']+': '+state.get('detail','resource-only completion'),file=sys.stdout if state['resource_export_complete'] else sys.stderr)
    return 0 if state['resource_export_complete'] else 3 if state['status'].startswith('SKIP3') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source integrity checks')
    raise SystemExit(main())
