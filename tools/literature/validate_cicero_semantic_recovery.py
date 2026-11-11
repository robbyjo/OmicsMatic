# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay native audits against actual copied original outputs; detect semantic corruption without hash guards."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];baseline=a.baseline.resolve();assert json.loads((baseline/'COMPLETED.json').read_bytes())['artifact']==artifact and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};source=ROOT/'src/test/java/org/jlinalg/cli/CiceroAuditReplay.java';source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [source,Path(__file__)]};classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    def change(path,fn):
        value=json.loads(path.read_bytes());fn(value);path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8',newline='\n')
    def models(worker):return worker/'cases/coaccess/models.json'
    def matrix(worker):
        m=json.loads(models(worker).read_bytes());return worker/'cases/coaccess'/next(w['matrix_file'] for w in m['windows'] if w['status']=='original-model')
    def column(table,name):return next(c for c in table['columns'] if c['name']==name)
    def invalid_axis(worker):change(worker/'constructed-counts.json',lambda x:x['column_ids'].__setitem__(slice(0,2),list(reversed(x['column_ids'][:2]))))
    def invalid_count(worker):change(worker/'constructed-counts.json',lambda x:x['entries'][0].__setitem__('value',str(float(x['entries'][0]['value'])+1)))
    def invalid_duplicate(worker):change(worker/'constructed-counts.json',lambda x:x['entries'].append(dict(x['entries'][0])))
    def invalid_aggregate(worker):change(worker/'cases/coaccess/aggregate-counts.json',lambda x:x['entries'][0].__setitem__('value',str(float(x['entries'][0]['value'])+1)))
    def invalid_membership(worker):change(worker/'cases/coaccess/aggregation-map.json',lambda x:column(x,'cell')['tokens'].__setitem__(0,column(x,'cell')['tokens'][1]))
    def invalid_covariance(worker):change(matrix(worker),lambda x:x['covariance']['values'][0].__setitem__(0,str(float(x['covariance']['values'][0][0])+0.001)))
    def invalid_penalty(worker):change(matrix(worker),lambda x:x['penalty']['values'][0].__setitem__(1,str(float(x['penalty']['values'][0][1])+0.01)))
    def invalid_model_error(worker):change(matrix(worker),lambda x:x.__setitem__('errflag',1))
    def invalid_fitted_covariance(worker):
        def mutate(x):
            v=str(float(x['w']['values'][0][1])+1e-4);x['w']['values'][0][1]=v;x['w']['values'][1][0]=v
        change(matrix(worker),mutate)
    def invalid_NA(worker):
        def mutate(x):
            c=column(x,'coaccess');i=c['kinds'].index('NA');c['tokens'][i]='0';c['kinds'][i]='value'
        change(worker/'cases/coaccess/connections.json',mutate)
    def invalid_score(worker):
        def mutate(x):
            c=column(x,'coaccess');i=c['kinds'].index('value');c['tokens'][i]=str(float(c['tokens'][i])+0.01)
        change(worker/'cases/coaccess/connections.json',mutate)
    def invalid_factor(worker):change(worker/'cases/coaccess/connections.json',lambda x:column(x,'Peak2')['levels'].reverse())
    def invalid_population(worker):
        def mutate(x):
            x['rows']-=1
            for c in x['columns']:c['tokens'].pop();c['kinds'].pop()
        change(worker/'cases/coaccess/connections.json',mutate)
    def invalid_window(worker):change(models(worker),lambda x:x['windows'].pop())
    controls=[('complete-original',None),('constructed-axis',invalid_axis),('constructed-count',invalid_count),('duplicate-coordinate',invalid_duplicate),('aggregate-count',invalid_aggregate),('bin-membership',invalid_membership),('model-covariance',invalid_covariance),('distance-penalty',invalid_penalty),('model-error-flag',invalid_model_error),('fitted-W-coaccess',invalid_fitted_covariance),('NA-to-zero',invalid_NA),('numeric-score',invalid_score),('factor-level-order',invalid_factor),('missing-pair',invalid_population),('missing-window',invalid_window)];reports=[]
    for name,mutate in controls:
        folder=out/name;folder.mkdir();worker=folder/'worker';shutil.copytree(baseline/'dense-result/worker',worker);shutil.copytree(baseline/'dense-result/inputs',folder/'inputs')
        for path in worker.glob('cases/*/java-audit.json'):
            assert path.resolve().is_relative_to(out);path.unlink()
        if mutate:mutate(worker)
        command=[str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.CiceroAuditReplay',str(jar),str(baseline/'dense-tsv.json'),str(worker)]
        with (folder/'stdout.log').open('xb') as stdout,(folder/'stderr.log').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=120)
        wanted=0 if mutate is None else 2;save(folder/'process.json',dict(command=command,exit_code=r.returncode,expected=wanted));assert r.returncode==wanted,name+': inspect native replay diagnostics';reports.append(dict(id=name,exit_code=r.returncode,expected=wanted,refusal=(folder/'stderr.log').read_text(encoding='utf8',errors='replace').strip()));print(name+': '+str(wanted),flush=True)
    assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};save(out/'COMPLETED.json',dict(status='passed-native-semantic-Cicero-replay-and-fourteen-corruption-refusals',artifact=artifact,source_pins=source_pins,controls=reports,hash_guards_used_for_semantic_refusals=False,portable_user_VERIFY_claimed=False,implementation_credit=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable semantic recovery/source bindings')
    main()
