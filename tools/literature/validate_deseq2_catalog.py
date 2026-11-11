# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run owned local browser, source-review and website gates without publication."""
import json,os,socket,subprocess,sys,time,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,save,pin
BASE=ROOT/'build/literature-review/deseq2-runtime/catalog-promotion-1'
NODE=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe')
MODULES=NODE.parent.parent/'node_modules'
EDGE=Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')

def command(label,argv,cwd=ROOT,env=None):
    with (BASE/(label+'.stdout')).open('xb') as stdout,(BASE/(label+'.stderr')).open('xb') as stderr:
        result=subprocess.run(list(map(str,argv)),cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
    save(BASE/(label+'-process.json'),dict(command=list(map(str,argv)),exit_code=result.returncode))
    assert result.returncode==0,label+' failed; inspect retained logs'

def main():
    assert BASE.exists() and all(p.exists() for p in [NODE,MODULES/'playwright',EDGE])
    command('source-review-tests',[sys.executable,'-X','utf8','-m','unittest','test_reviewed_declarations','test_collect_omics_review','test_review_support'],ROOT/'tools/literature')
    # Bind check prevents accidentally testing a pre-existing unrelated server.
    with socket.socket() as s:s.bind(('127.0.0.1',8768))
    owned=BASE/'browser-run-1';owned.mkdir();server_log=(owned/'server.log').open('xb')
    server=subprocess.Popen([sys.executable,'-m','http.server','8768','--bind','127.0.0.1','--directory',str(ROOT/'site')],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=server_log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:
        for _ in range(50):
            assert server.poll() is None,'Owned server exited before readiness'
            try:
                with urllib.request.urlopen('http://127.0.0.1:8768/literature-review.html',timeout=1) as r:assert r.status==200
                break
            except OSError:time.sleep(.1)
        else:raise RuntimeError('Owned server did not become ready')
        for label,script in [('focused','check_deseq2_review_ui.cjs'),('broad','check_review_ui.cjs')]:
            env=os.environ.copy();env['JLINALG_REVIEW_UI_OUT']=str(BASE/(label+'-ui-1'));command(label+'-ui',[NODE,ROOT/'tools/literature'/script,MODULES,EDGE],env=env)
    finally:
        server.terminate()
        try:server.wait(timeout=10)
        except subprocess.TimeoutExpired:server.kill();server.wait(timeout=10)
        server_log.close();save(owned/'SERVER.json',dict(owned_pid=server.pid,owned_helper_stopped=server.poll() is not None,returncode=server.returncode,bind='127.0.0.1:8768',published=False))
    env=os.environ.copy();env['JAVA_HOME']='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
    command('checkWebsite',[ROOT/'gradlew.bat','--offline','--no-daemon','--no-configuration-cache','checkWebsite'],env=env)
    save(BASE/'GATES.json',dict(status='passed-DESeq2-catalog-browser-source-and-website-gates',focused_checks=len(json.loads((BASE/'focused-ui-1/ui-validation.json').read_bytes())['checks']),broad_checks=len(json.loads((BASE/'broad-ui-1/ui-validation.json').read_bytes())['checks']),source_review_tests=51,website_check_passed=True,owned_server_stopped=True))
    print('Passed focused/broad browser,51 source-review tests and checkWebsite; owned server stopped')

if __name__=='__main__':main()
