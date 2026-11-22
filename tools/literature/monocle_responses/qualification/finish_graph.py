"""Finish only full-object comparison after the graph harness's missing-helper failure."""
from pathlib import Path
import argparse,json,shutil,subprocess
from build import ROOT,pin,save
from packaged import mount

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--capture',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    build=args.build.resolve();capture=args.capture.resolve();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    normal=json.loads((build/'RESULT.json').read_bytes());jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert normal['status']=='PASS_NORMAL_JAR_MONOCLE_RESPONSE_AND_STAGE_CONTROLS' and pin(jar)==normal['producer']
    before={p.relative_to(capture).as_posix():pin(p) for p in capture.rglob('*') if p.is_file()}
    assert not (capture/'RESULT.json').exists()
    operations={name:json.loads((capture/name/'RESULT.json').read_bytes()) for name in ['learn','order','run']}
    for row in operations.values():assert row['parent_preserved'] and row['complete_original_scientific_state_ordering_RNG_replay_identical'] and all(record['exit_code']==0 for record in row['commands'].values())
    reference=json.loads((capture/'fresh-original/RESULT.json').read_bytes());assert all(row['expected_cli_exit']==0 for row in reference['cases'].values())
    comparison=out/'comparison';comparison.mkdir();profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/monocle100-graph/PROFILE.json').read_bytes());work=capture/'work'
    save(comparison/'PLAN.json',dict(cases=[dict(name=name,capture=mount(work/name),reference=mount(capture/'fresh-original'/name)) for name in operations],guarded_functions=profile['guarded_functions'],reference_source_signatures=reference['source_function_signatures']))
    original_helper=ROOT/'tools/literature/monocle100_graph/qualification/compare_original.R';local_helper=Path(__file__).with_name('compare_original.R');assert pin(original_helper)==pin(local_helper);shutil.copyfile(local_helper,comparison/'EXECUTED-compare_original.R');shutil.copyfile(__file__,out/'EXECUTED-finish_graph.py')
    previous=ROOT.parents[1]/'build/monocle100-neighbor-diagnostics-20261121-1';source=ROOT.parents[1]/'build/literature-review/monocle-runtime/source-intake-1/1.0.0/source';libraries=json.loads((ROOT.parent/'mo-1/build/monocle100-1/install-1/RESULT.json').read_bytes())['libraries']
    for name in ['home','tmp','cache']:(comparison/name).mkdir()
    command=json.loads((previous/'COMMAND.json').read_bytes());command=command[:command.index('--vanilla')+1];command=[value.replace(mount(previous),mount(comparison)).replace('LANG=C','LANG=C.UTF-8').replace('LC_ALL=C','LC_ALL=C.UTF-8') for value in command];command +=[mount(comparison/'EXECUTED-compare_original.R'),mount(comparison),*libraries,mount(comparison/'PLAN.json'),mount(source)]
    save(comparison/'STARTED.json',dict(command=command,resume_only_complete_object_comparison=True,original_graph_operations_not_restarted=True))
    with (comparison/'stdout').open('xb') as stdout,(comparison/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
    save(comparison/'PROCESS.json',dict(exit_code=p.returncode));assert p.returncode==0,(comparison/'stderr').read_text()[-4000:]
    matched=json.loads((comparison/'RESULT.json').read_bytes());assert matched['status']=='PASS_COMPLETE_ORIGINAL_GRAPH_MODELS_ORDERING_ERRORS_AND_RNG' and matched['complete_output_CDS']==matched['complete_RNG_returns']==3 and matched['complete_ordering_returns']==2 and matched['source_function_signatures_unchanged']
    assert before=={p.relative_to(capture).as_posix():pin(p) for p in capture.rglob('*') if p.is_file()}
    assert (work/'order/results/pseudotime.tsv').read_bytes()==(work/'run/results/pseudotime.tsv').read_bytes()
    assert (work/'learn/results/principal-graph.json').read_bytes()==(work/'run/results/principal-graph.json').read_bytes()
    result=dict(status='PASS_THREE_PUBLIC_GRAPH_OPERATIONS_REPLAYS_AND_FRESH_ORIGINAL_OBJECTS',operations=operations,producer=pin(jar),fresh_original_complete_output_CDS=3,fresh_original_complete_orderings=2,fresh_original_complete_RNG=3,learn_then_order_and_combined_pseudotime_identical=True,complete_LEARN_RUN_principal_projections_identical=True,resumed_final_comparison_only=True,original_capture_preserved=True,earlier_harness_missing_helper_failure_retained=True,new_catalog_credit=0,whole_goal_complete=False)
    save(out/'RESULT.json',result);print('PASS resumed final original-object comparison;all3 global graph operations and captures preserved',flush=True);return 0

if __name__=='__main__':raise SystemExit(main())
