# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay five final complete bundles with both compiled JARs on three JVMs; no R execution."""
import argparse,concurrent.futures,itertools,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/cellchat-runtime';HOME=ROOT/'docs/literature/cellchat-workflow-development'
PLATFORMS={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java']}
BUNDLES={'typed21':BASE/'typed-controls-7/analysis','typed25':BASE/'typed-controls-8/analysis','replicate':BASE/'workflow-edges-2/replicate-zero-and-rare/analysis','constant':BASE/'workflow-edges-2/constant-comparison/analysis','example':BASE/'public-workflow-example-1'}

def linux(path):
    p=path.resolve();assert p.drive.lower()=='d:';return '/mnt/d/'+p.as_posix()[3:]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_PORTABLE_BUNDLE_VERIFICATION',implementation_credit=False,whole_goal_complete=False)
    try:
        jars={c:HOME/('cellchat-workflow-'+c+'.jar') for c in ['javac21','javac25']};paths=[Path(__file__).resolve(),*jars.values(),*(p for b in BUNDLES.values() for p in b.rglob('*') if p.is_file())]
        before={p.relative_to(ROOT).as_posix():pin(p) for p in paths};save(out/'PROTECTED-BEFORE.json',before)
        def check(job):
            compiler,platform,bundle=job;name=compiler+'-'+platform+'-'+bundle;convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            command=[*PLATFORMS[platform],'-Xmx512m','-jar',convert(jars[compiler]),'--verify',convert(BUNDLES[bundle])]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
            result=dict(compiler=compiler,platform=platform,bundle=bundle,command=command,exit_code=r.returncode);save(out/(name+'-process.json'),result);assert r.returncode==0,(name,'Read retained stderr');return result
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,itertools.product(jars,PLATFORMS,BUNDLES)))
        after={name:pin(ROOT/name) for name in before};save(out/'PROTECTED-AFTER.json',after);assert before==after
        state.update(status='PASS30_FROZEN_CELLCHAT_BUNDLE_CHECKS_ACROSS_TWO_BUILDS_THREE_JVMS',checks=results,protected_sources_results_unchanged=True,R_executed=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete bundle/source checks')
    raise SystemExit(main())
