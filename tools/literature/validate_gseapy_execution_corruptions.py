# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject actual altered primitive/report families using the production native audit; preserve all original inputs."""
import argparse,hashlib,json,shutil,struct,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','original','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();original=a.original.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_NATIVE_CORRUPTION_CONTROLS',checks=[],whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};source=original/'1.1.8';request=source/'request.original.json';before={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-file-pins.json',before);driver=ROOT/'tools/literature/java/org/jlinalg/cli/GseapyExecutionSingleAudit.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(driver)];run=subprocess.run(command,capture_output=True,timeout=90);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        def load(home,name):return json.loads((home/name).read_bytes())
        def edit(home,name,fn):value=load(home,name);fn(value);save(home/name,value)
        def summary(home,field,value):edit(home,'observed-backend-summary.json',lambda v:v['summaries'][0].__setitem__(field,value))
        def altered_binary(home,frame_name,column,value):
            frame=load(home,frame_name)['res2d'];desc=frame['column_data'][frame['columns'].index(column)];raw=bytearray((home/desc['path']).read_bytes());assert desc['dtype']=='<f8';raw[:8]=struct.pack('<d',value);digest=hashlib.sha256(raw).hexdigest();target=home/'objects'/(digest+'.bin')
            if not target.exists():target.write_bytes(raw)
            desc['sha256']=digest;desc['path']='objects/'+target.name;whole=load(home,frame_name);whole['res2d']=frame;save(home/frame_name,whole)
        def wrong_leading(home):
            result=load(home,'original-return.json');term=load(home,'observed-backend-summary.json')['summaries'][0]['term'];result['_results']['prerank'][term]['lead_genes']='INVENTED_LEADING_GENE';save(home/'original-return.json',result)
        def wrong_hits(home):
            def alter(v):v['values'][0][5]['values'][0]='INVENTED_HIT'
            edit(home,'observed-enrichr-statistics.json',alter)
        def wrong_annotation(home):
            def alter(v):f=v['res2d'];f['column_data'][f['columns'].index('Genes')]['values'][0]='REPAIRED_GENES'
            edit(home,'original-return.json',alter)
        tests=[('enrichr-source-P','enrichr-local',lambda h:edit(h,'observed-enrichr-statistics.json',lambda v:v['values'][0].__setitem__(1,0.42))),('enrichr-source-hits','enrichr-local',wrong_hits),('enrichr-rehashed-adjusted-P','enrichr-local',lambda h:altered_binary(h,'original-return.json','Adjusted P-value',0.987654321)),('enrichr-gene-annotation','enrichr-local',wrong_annotation),('observed-ES','prerank',lambda h:summary(h,'es',0.87654321)),('dense-curve','prerank',lambda h:edit(h,'observed-backend-summary.json',lambda v:v['summaries'][0]['run_es'].__setitem__(3,0.654321))),('original-null-draw','prerank',lambda h:edit(h,'observed-backend-summary.json',lambda v:v['summaries'][0]['esnull'].__setitem__(0,0.999991))),('source-FDR','prerank',lambda h:summary(h,'fdr',0.987654321)),('source-FWER','prerank',lambda h:summary(h,'fwerp',0.987654321)),('source-hit-position','prerank',lambda h:edit(h,'observed-backend-summary.json',lambda v:v['summaries'][0]['hits'].__setitem__(0,79))),('leading-genes','prerank',wrong_leading),('GSVA-report-coherence','gsva-gaussian',lambda h:summary(h,'es',99.0)),('GSVA-truncated-TSV','gsva-gaussian',lambda h:(h/'full-result.tsv').write_text('\n'.join((h/'full-result.tsv').read_text().splitlines()[:-1])+'\n')),('changed-source-input','prerank',lambda h:edit(h,'source-input.json',lambda v:v['ids'].__setitem__(0,'CHANGED_INPUT_ID'))),('ssGSEA-normalization','ssgsea-rank',lambda h:summary(h,'nes',99.0))]
        for name,case,mutate,expected in [(f'baseline-{c}',c,None,0) for c in ['enrichr-local','prerank','gsva-gaussian','ssgsea-rank']]+[(n,c,f,2) for n,c,f in tests]:
            home=out/name;home.mkdir();data=home/'case';shutil.copytree(source/'worker'/case,data);(data/'NEGATIVE_FIXTURE.md').write_text('Retained-data native-audit control only. No original analysis or batch is admitted. Read the parent check status.\n',encoding='utf8')
            if mutate:mutate(data)
            command=[str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.GseapyExecutionSingleAudit',str(data),str(request),str(source/'inputs'/(case+'.json')),case,str(home/'audit.json')]
            with (home/'audit.stdout').open('xb') as first,(home/'audit.stderr').open('xb') as second:run=subprocess.run(command,stdout=first,stderr=second,cwd=ROOT,timeout=60)
            save(home/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==expected,(name,run.returncode);report=load(home,'audit.json');assert not report['analysis_admitted'];state['checks'].append(dict(name=name,exit_code=run.returncode,report=pin(home/'audit.json')))
        assert all(pin(original/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-four-original-baselines-and-fifteen-altered-native-families',artifact=built['artifact'],original_file_pins=pin(out/'original-file-pins.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve()),driver.relative_to(ROOT).as_posix():pin(driver)},independent_permutation_or_kernel_validation=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
