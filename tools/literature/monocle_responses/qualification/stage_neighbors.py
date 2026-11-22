"""Regress both existing neighbor/Moran engines after the shared runtime extension."""
from pathlib import Path
import argparse,hashlib,json,math,subprocess
from build import ROOT,pin,save
from packaged import mount

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();build=args.build.resolve();normal=json.loads((build/'RESULT.json').read_bytes());assert normal['status']=='PASS_NORMAL_JAR_MONOCLE_RESPONSE_AND_STAGE_CONTROLS'
    jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==normal['producer'];out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    example=ROOT/'examples/monocle100-neighbors';reference=json.loads((example/'original-reference.json').read_bytes());assert reference['official_function_body_formals_identical'];runtime=ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json'
    prefix=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--'];java=prefix+['java','-Xmx2g'];cli=java+['-jar',mount(jar),'monocle-tools','NEIGHBORS-1.0.0'];hosts={}
    def run(folder,label,command):
        with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        record=dict(command=command,exit_code=process.returncode);save(folder/(label+'.PROCESS.json'),record);assert process.returncode==0,'Inspect retained '+str(folder)+'/'+label;return record
    for engine in ['original','java']:
        folder=out/engine;folder.mkdir();work=folder/'run';records={}
        records['prepare']=run(folder,'prepare',prefix+['python3','-B',mount(example/'prepare.py'),'--runtime',mount(runtime),'--engine',engine,'--out',mount(work)])
        records['neighbors']=run(folder,'neighbors',cli+['RUN','--request',mount(work/'neighbors-request.json')])
        records['verify-linux']=run(folder,'verify-linux',cli+['VERIFY','--capture',mount(work/'neighbors'),'--out',mount(work/'verified-linux')])
        parent={p.relative_to(work/'neighbors').as_posix():pin(p) for p in (work/'neighbors').rglob('*') if p.is_file()}
        records['replay']=run(folder,'replay',cli+['REPLAY','--capture',mount(work/'neighbors'),'--out',mount(work/'replayed'),'--runtime',mount(work/'runtime.json')])
        assert parent=={p.relative_to(work/'neighbors').as_posix():pin(p) for p in (work/'neighbors').rglob('*') if p.is_file()}
        assert json.loads((work/'replayed/replay.json').read_bytes())['complete_original_returns_RNG_identical']
        records['moran']=run(folder,'moran',java+['-jar',mount(jar),'monocle-tools','MORAN-LM','--request',mount(work/'moran-request.json')])
        for host,executable in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'),('windows25','C:/Program Files/Java/jdk-25/bin/java.exe')]:records[host]=run(folder,host,[executable,'-Xmx2g','-jar',str(jar),'monocle-tools','NEIGHBORS-1.0.0','VERIFY','--capture',str(work/'neighbors'),'--out',str(work/('verified-'+host))])
        weights=json.loads((work/'neighbors/results/weights.json').read_bytes());replayed=json.loads((work/'replayed/results/weights.json').read_bytes());assert weights==replayed and len(weights)==74
        admitted=json.loads((work/'statistics/analysis.json').read_bytes());assert admitted['result_admitted'] is True
        tests=json.loads((work/'statistics/tests.json').read_bytes());assert [row['gene_id'] for row in tests]==list(reference['expected']);deltas=[]
        for row in tests:
            expected=reference['expected'][row['gene_id']]
            for field in ['expectation','variance','z','p_value','log_p_value','q_value','morans_I']:
                target=float(expected['moran_I' if field=='morans_I' else field]);got=row[field];delta=abs(got-target);assert math.isfinite(got) and delta<=2e-12*max(1,abs(target)),(engine,row['gene_id'],field);deltas.append(delta)
        result=dict(status='PASS_ACTUAL_NORMAL_JAR_NEIGHBOR_WEIGHTS_MORAN_AND_COMPLETE_REPLAY',engine=engine,commands=records,complete_cells=27,complete_response_genes=3,arcs=74,replay_complete_original_returns_RNG_identical=True,parent_preserved=True,original_spdep_fields_compared=21,absolute_relative_tolerance=2e-12,maximum_absolute_difference=max(deltas),actual_original_calculateLW_executed=engine=='original',paper_reproduction=False)
        save(folder/'RESULT.json',result);hosts[engine]=result;print('PASS public',engine,'neighbor/Moran, real replay and three-host verification',flush=True)
    original=json.loads((out/'original/run/neighbors/results/weights.json').read_bytes());native=json.loads((out/'java/run/neighbors/results/weights.json').read_bytes());assert original==native
    save(out/'RESULT.json',dict(status='PASS_BOTH_PUBLIC_ENGINES_REPLAYS_AND_THREE_HOST_VERIFICATION',engines=hosts,producer=pin(jar),new_catalog_credit=0,whole_goal_complete=False));return 0
if __name__=='__main__':raise SystemExit(main())
