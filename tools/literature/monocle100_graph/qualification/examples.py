"""Execute the public prepared-CDS graph example, full replays and fresh original comparisons."""
from pathlib import Path
import argparse,json,shutil,subprocess
from build import ROOT,pin,save
from packaged import mount

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();build=args.build.resolve();normal=json.loads((build/'RESULT.json').read_bytes());assert normal['status']=='PASS_NORMAL_JAR_MONOCLE100_GRAPH_CONTROLS';jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==normal['producer'];out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,out/'EXECUTED-examples.py')
    runtime=json.loads((ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json').read_bytes());runtime['profile']='monocle100-original-graph-r43-v1';save(out/'runtime.json',runtime)
    prefix=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--'];cli=prefix+['java','-Xmx2g','-cp',mount(jar),'org.jlinalg.cli.Monocle100GraphCli'];work=out/'work'
    def run(folder,label,command):
        save(folder/(label+'.STARTED.json'),dict(command=command))
        with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=850)
        record=dict(command=command,exit_code=p.returncode);save(folder/(label+'.PROCESS.json'),record);assert p.returncode==0,'Inspect retained '+str(folder)+'/'+label;return record
    run(out,'prepare',prefix+['python3','-B',mount(ROOT/'examples/monocle100-graph/prepare.py'),'--runtime',mount(out/'runtime.json'),'--root-cell','cell-001','--out',mount(work)])
    results={}
    for operation in ['learn','order','run']:
        folder=out/operation;folder.mkdir();capture=work/operation;records={}
        records['run']=run(folder,'run',cli+['RUN','--request',mount(work/(operation+'-request.json'))]);before={p.relative_to(capture).as_posix():pin(p) for p in capture.rglob('*') if p.is_file()}
        records['replay']=run(folder,'replay',cli+['REPLAY','--capture',mount(capture),'--out',mount(work/(operation+'-replayed')),'--runtime',mount(out/'runtime.json')])
        assert before=={p.relative_to(capture).as_posix():pin(p) for p in capture.rglob('*') if p.is_file()}
        replay=json.loads((work/(operation+'-replayed')/'replay.json').read_bytes());assert replay['parent_preserved'] and replay['complete_original_scientific_state_ordering_RNG_identical']
        records['linux21']=run(folder,'linux21',cli+['VERIFY','--capture',mount(capture),'--out',mount(work/(operation+'-verified-linux21'))])
        for host,java in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'),('windows25','C:/Program Files/Java/jdk-25/bin/java.exe')]:records[host]=run(folder,host,[java,'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.Monocle100GraphCli','VERIFY','--capture',str(capture),'--out',str(work/(operation+'-verified-'+host))])
        audit=json.loads((capture/'results/audit.json').read_bytes());assert (audit['cells'],audit['genes_in_source_cds'],audit['principal_points'],audit['principal_edges'])==(27,3,5,4) and audit['pseudotime_computed']==(operation!='learn') and audit['scientific_test_admitted'] is False
        row=dict(status='PASS_PUBLIC_GRAPH_OPERATION_REPLAY_AND_THREE_HOST_VERIFY',operation=operation,commands=records,audit=audit,parent_preserved=True,complete_original_scientific_state_ordering_RNG_replay_identical=True);save(folder/'RESULT.json',row);results[operation]=row;print('PASS public',operation,'complete replay and three-host R-free verification',flush=True)
    assert (work/'order/results/pseudotime.tsv').read_bytes()==(work/'run/results/pseudotime.tsv').read_bytes()
    assert (work/'learn/results/principal-graph.json').read_bytes()==(work/'run/results/principal-graph.json').read_bytes()
    # Fresh unchanged original calls use the exact captured source CDS and explicit
    # settings for each operation, including ORDER's actual caller-supplied graph.
    original=out/'fresh-original';original.mkdir();profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/monocle100-graph/PROFILE.json').read_bytes());cases=[dict(name=name,source_cds=mount(work/name/'inputs/source-cds.rds'),parameters=json.loads((work/name/'effective-request.json').read_bytes())) for name in results];save(original/'PLAN.json',dict(cases=cases,packages=profile['packages'],guarded_functions=profile['guarded_functions']))
    source=ROOT.parents[1]/'build/literature-review/monocle-runtime/source-intake-1/1.0.0/source';previous=ROOT.parents[1]/'build/monocle100-neighbor-diagnostics-20261121-1';libraries=json.loads((ROOT.parent/'mo-1/build/monocle100-1/install-1/RESULT.json').read_bytes())['libraries']
    def original_command(directory,script,plan):
        for name in ['home','tmp','cache']:(directory/name).mkdir()
        command=json.loads((previous/'COMMAND.json').read_bytes());command=command[:command.index('--vanilla')+1];command=[value.replace(mount(previous),mount(directory)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in command]
        if script.name=='EXECUTED-reference.R':return command+[mount(script),mount(directory),*libraries,mount(source),mount(plan)]
        return command+[mount(script),mount(directory),*libraries,mount(plan),mount(source)]
    shutil.copyfile(ROOT/'tools/literature/monocle100_graph/reference.R',original/'EXECUTED-reference.R');run(original,'original',original_command(original,original/'EXECUTED-reference.R',original/'PLAN.json'));reference=json.loads((original/'RESULT.json').read_bytes());assert all(row['expected_cli_exit']==0 for row in reference['cases'].values())
    comparison=out/'independent-comparison';comparison.mkdir();save(comparison/'PLAN.json',dict(cases=[dict(name=name,capture=mount(work/name),reference=mount(original/name)) for name in results],guarded_functions=profile['guarded_functions'],reference_source_signatures=reference['source_function_signatures']));shutil.copyfile(Path(__file__).with_name('compare_original.R'),comparison/'EXECUTED-compare_original.R');run(comparison,'comparison',original_command(comparison,comparison/'EXECUTED-compare_original.R',comparison/'PLAN.json'));matched=json.loads((comparison/'RESULT.json').read_bytes());assert matched['complete_output_CDS']==matched['complete_RNG_returns']==3 and matched['complete_ordering_returns']==2
    save(out/'RESULT.json',dict(status='PASS_THREE_PUBLIC_GRAPH_OPERATIONS_REPLAYS_AND_FRESH_ORIGINAL_OBJECTS',operations=results,producer=pin(jar),fresh_original_complete_output_CDS=3,fresh_original_complete_orderings=2,fresh_original_complete_RNG=3,learn_then_order_and_combined_pseudotime_identical=True,complete_LEARN_RUN_principal_projections_identical=True,new_catalog_credit=0,whole_goal_complete=False));return 0
if __name__=='__main__':raise SystemExit(main())
