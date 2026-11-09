# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the guide's beginner export/bind/count/verify examples with only explicit NEW path substitutions."""
import argparse,json,os,re,subprocess,sys,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_BEGINNER_EXAMPLES',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};guide=ROOT/'docs/literature/RSUBREAD_WORKFLOWS.md';blocks=[json.loads(q) for q in re.findall(r'```json\n(.*?)\n```',guide.read_text(encoding='utf8'),re.S)];assert len(blocks)==3
        def execute(name,command):
            run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,(name,run.stderr.decode(errors='replace'))
        execute('package-export',[sys.executable,str(ROOT/'tools/literature/export_rsubread_runtime_package.py'),'--edition','2260','--out',str(out/'private-runtime'),'--allow-install'])
        execute('data-export',[sys.executable,str(ROOT/'tools/literature/export_rsubread_demo.py'),'--out',str(out/'demo')])
        bind,counts,verify=blocks;bind['out']=str(out/'binding');bind['r_library']=str(out/'private-runtime/R-lib');path=out/'bind.json';save(path,bind);java='C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'
        execute('bind',[java,'-Xmx256m','-cp',str(jar),'org.jlinalg.cli.RsubreadRuntimeCli',str(path)])
        counts['out']=str(out/'analysis');counts['binding']=str(out/'binding/COMPLETED.json');counts['cases'][0]['files'][0]['path']=str(out/'demo/single.sam');counts['cases'][0]['annotation']['path']=str(out/'demo/genes.gtf.gz');path=out/'count.json';save(path,counts)
        execute('count',[java,'-Xmx512m','-jar',str(jar),str(path)]);receipt=json.loads((out/'analysis/COMPLETED.json').read_bytes());assert receipt['analysis_admitted'] and receipt['summaries'][0]['rows']==5 and receipt['summaries'][0]['junction_rows']==1
        verify['analysis']=str(out/'analysis');path=out/'verify.json';save(path,verify);execute('verify',[java,'-Xmx512m','-jar',str(jar),str(path)])
        state.update(status='passed-exact-beginner-package-data-export-binding-counting-and-verification-examples',artifact=build['artifact'],paths_only_substituted=True,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),guide,ROOT/'tools/literature/export_rsubread_demo.py',ROOT/'tools/literature/export_rsubread_runtime_package.py']})
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact beginner/example/admission checks')
    raise SystemExit(main())
