# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise all16 pinned CellChat database exports and missing/invalid runtime paths."""
import argparse,concurrent.futures,json,shutil,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_database import verify

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_DATABASE_CONTROLS',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        script=ROOT/'tools/literature/export_cellchat_database.py';lockPath=script.with_name('cellchat-source-lock.json');lock=json.loads(lockPath.read_bytes())
        source_paths=[script,script.with_suffix('.R'),Path(__file__).resolve(),script.with_name('verify_cellchat_database.py'),lockPath,ROOT/'build/cli/omicsmatic-0.3.7.jar']
        source_paths.extend(BASE/'source-intake-1'/label/'source'/name for label,s in lock['sources'].items() for name in s['files'])
        before={p.relative_to(ROOT).as_posix():pin(p) for p in source_paths};save(out/'PROTECTED-BEFORE.json',before)
        def execute(label,species,home,extra=(),source=None):
            command=[sys.executable,str(script),'--source-label',label,'--source-root',str(source or BASE/'source-intake-1'/label/'source'),'--species',species,'--out',str(home),*map(str,extra)]
            run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(home.name+'.stdout')).write_bytes(run.stdout);(out/(home.name+'.stderr')).write_bytes(run.stderr)
            save(out/(home.name+'-process.json'),dict(command=command,exit_code=run.returncode));return run.returncode,json.loads((home/'COMPLETED.json').read_bytes())
        jobs=[(label,Path(name).name.removeprefix('CellChatDB.').removesuffix('.rda')) for label,s in lock['sources'].items() for name in s['files'] if name.startswith('data/CellChatDB.')]
        assert len(jobs)==16
        def success(job):
            label,species=job;home=out/(label+'-'+species);code,result=execute(label,species,home);assert code==0;verify(home)
            return dict(label=label,species=species,tables=result['table_index'],receipt=pin(home/'COMPLETED.json'))
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(success,jobs))
        failures=[]
        for name,label,species,extra,source in [
            ('missing-r','2.2.0-2025-release','human',['--rscript',out/'does-not-exist-Rscript.exe'],None),
            ('missing-source','2.2.0-2025-release','human',[],out/'not-a-source'),
            ('unsupported-species','1.0.0','zebrafish',[],None),
            ('unsupported-edition','unreported-edition','human',[],None)]:
            code,result=execute(label,species,out/name,extra,source);assert code==3 and result['status'].startswith('SKIP3') and result['resource_export_complete'] is False
            assert 'Required action:' in (out/name/'WARNINGS.md').read_text();failures.append(dict(case=name,status=result['status']))
        bad=out/'changed-source';(bad/'data').mkdir(parents=True)
        original=BASE/'source-intake-1/2.2.0-2025-release/source';shutil.copyfile(original/'data/CellChatDB.human.rda',bad/'data/CellChatDB.human.rda');(bad/'DESCRIPTION').write_bytes((original/'DESCRIPTION').read_bytes()+b'\nChanged: not-original\n')
        code,result=execute('2.2.0-2025-release','human',out/'wrong-hash',source=bad);assert code==2 and result['status'].startswith('FAIL2') and result['resource_export_complete'] is False
        failures.append(dict(case='wrong-hash',status=result['status']))
        # Read-only verifier must refuse a changed retained output and an extra
        # output. Tamper only a NEW owned copy, leaving original evidence intact.
        copy=out/'tamper-copy';shutil.copytree(out/'2.2.0-2025-release-human',copy)
        file=copy/'tables/interaction/data.tsv';file.write_bytes(file.read_bytes()+b'changed\n')
        try:verify(copy)
        except AssertionError:pass
        else:raise AssertionError('Changed table admitted')
        extra=out/'extra-copy';shutil.copytree(out/'1.0.0-human',extra);(extra/'extra.txt').write_text('unbound')
        try:verify(extra)
        except AssertionError:pass
        else:raise AssertionError('Extra output admitted')
        after={n:pin(ROOT/n) for n in before};save(out/'PROTECTED-AFTER.json',after);assert before==after
        state.update(status='PASS16_ORIGINAL_DATABASE_EXPORTS_FIVE_DISPOSITIONS_TWO_TAMPER_REFUSALS_ONLY',exports=results,dispositions=failures,tamper_refusals=2,protected_sources_unchanged=True,canonical_executable_unchanged=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar'))
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable resource integrity or failure assertions')
    raise SystemExit(main())
