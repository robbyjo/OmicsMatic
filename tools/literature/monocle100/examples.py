"""Execute the public complete-population Java weight/Moran bridge on three JVM hosts."""
from pathlib import Path
import argparse,concurrent.futures,json,math,subprocess
from setup import ROOT,pin,require,save
from packaged import mounted

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    build=args.build.resolve();result=json.loads((build/'RESULT.json').read_bytes());require(result['status']=='PASS_NORMAL_JAR_MONOCLE100_SOURCE_CONTROLS','Use complete normal producer');jar=build/'project-build/cli/omicsmatic-0.4.0.jar';require(pin(jar)==result['producer'],'Changed producer')
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW build output');out.mkdir(parents=True,exist_ok=False)
    example=ROOT/'examples/monocle100-weights';reference=json.loads((example/'original-reference.json').read_bytes());require(reference['official_function_body_formals_identical'],'Exact original spdep source guard required')
    fixture=json.loads((ROOT/'src/test/resources/org/jlinalg/singlecell/monocle100/weights.json').read_bytes())['27-5-principal_graph']['reference']
    def host(name):
        folder=out/name;folder.mkdir();work=folder/'run';linux=name=='linux21'
        if linux:
            prefix=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--'];prepare=prefix+['python3','-B',mounted(example/'prepare.py'),'--out',mounted(work)];java=prefix+['java','-Xmx2g'];path=mounted
        else:
            prepare=['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(example/'prepare.py'),'--out',str(work)]
            java=[('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if name=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'),'-Xmx2g'];path=str
        commands={'prepare':prepare,'weights':java+['-cp',path(jar),'org.jlinalg.cli.Monocle100WeightsCli','--request',path(work/'weights-request.json')],
                  'moran':java+['-jar',path(jar),'monocle-tools','MORAN-LM','--request',path(work/'moran-request.json')]};rows={}
        for label,command in commands.items():
            with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
            row=dict(command=command,exit_code=process.returncode);save(folder/(label+'.PROCESS.json'),row);rows[label]=row;require(process.returncode==0,'Inspect retained example failure: '+name+'/'+label)
        weight=json.loads((work/'weights/analysis.json').read_bytes());require(weight['weights_admitted'] and not weight['scientific_test_admitted'] and weight['cells']==27 and weight['self_links']==0,'Different admitted weight population')
        values=json.loads((work/'weights/weights.json').read_bytes());original_neighbors=list(fixture['neighbors_zero_based'].values());tokens=fixture['weights_exact_tokens'];index=0
        for source,(neighbors,weights) in enumerate(zip(original_neighbors,tokens,strict=True)):
            for target,token in zip(neighbors,weights,strict=True):
                row=values[index];require(row['from']==source and row['to']==target and row['weight']==float(token),'Different complete original weight field');index+=1
        require(index==len(values)==74,'Different original arc population')
        admitted=json.loads((work/'statistics/analysis.json').read_bytes());require(admitted['result_admitted'] is True,'No complete residual-Moran result')
        observed=json.loads((work/'statistics/tests.json').read_bytes());require([row['gene_id'] for row in observed]==list(reference['expected']),'Different complete testing family')
        differences=[]
        for row in observed:
            expected=reference['expected'][row['gene_id']]
            for field in ['expectation','variance','z','p_value','log_p_value','q_value','morans_I']:
                wanted=float(expected['moran_I' if field=='morans_I' else field]);got=row[field];delta=abs(got-wanted)
                require(math.isfinite(got) and delta<=2e-12*max(1,abs(wanted)),'Original spdep bridge differs: '+row['gene_id']+'/'+field)
                differences.append(dict(gene=row['gene_id'],field=field,absolute_difference=delta))
        answer=dict(status='PASS_ACTUAL_NORMAL_JAR_WEIGHT_TO_MORAN_BRIDGE',commands=rows,cells=27,genes=3,arcs=74,weights_binary64_identical=True,original_spdep_fields_compared=21,absolute_relative_tolerance=2e-12,maximum_absolute_difference=max(row['absolute_difference'] for row in differences),differences=differences,source_profile=pin(example/'PROVENANCE.json'),scientific_paper_execution=False)
        save(folder/'RESULT.json',answer);print(name,'PASS74 exact original weights and21 original-spdep fields',flush=True);return name,answer
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:hosts=dict(pool.map(host,['windows21','windows25','linux21']))
    save(out/'RESULT.json',dict(status='PASS_THREE_HOST_PUBLIC_WEIGHT_TO_MORAN_EXAMPLES',hosts=hosts,producer=pin(jar),new_catalog_credit=0,whole_goal_complete=False));return 0

if __name__=='__main__':raise SystemExit(main())
