# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute source sample-filter boundaries and nonfinite comparison families through the actual JAR."""
import argparse,csv,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

BASE=ROOT/'build/literature-review/cellchat-runtime';JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'

def table(path):
    with path.open(encoding='utf8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='workflow-build21-7');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_EDGE_CONTROLS',implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((BASE/a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']}
        for name,wanted in built['source_pins'].items():assert pin(ROOT/name)==wanted
        libraries=[BASE/'presto-reference-setup-2/R-lib',BASE/'reference-setup-1/R-lib',*[Path(p) for p in json.loads((BASE/'reference-setup-1/STARTED.json').read_bytes())['libraries']]]
        script=ROOT/'tools/literature/create_cellchat_workflow_edge_controls.R';files=[Path(__file__).resolve(),script];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for file in files:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        def run(name,command):
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:result=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=2400)
            save(out/(name+'-process.json'),dict(command=command,exit_code=result.returncode));assert result.returncode==0,(name,'Read retained stderr')
        reports=[]
        for profile in ['replicate-zero-and-rare','constant-comparison']:
            home=out/profile;home.mkdir();source=BASE/'source-intake-1/2.2.0-2025-release/source'
            run(profile+'-inputs',[str(RSCRIPT),'--vanilla',str(out/'source-snapshot/tools/literature/create_cellchat_workflow_edge_controls.R'),str(home/'inputs'),str(source),profile,*map(str,libraries)])
            analysis=json.loads((home/'inputs/request.json').read_bytes());request=dict(schema_version=1,out=str(home/'analysis'),runtime_profile=str(BASE/'runtime-profile-1/runtime-profile.json'),analysis=analysis,limits=dict(input_bytes=268435456,output_bytes=1073741824,wall_seconds=1800,max_genes=100000,max_cells=100000,max_files=200000,runtime_bytes=34359738368))
            save(home/'request.json',request);run(profile+'-analysis',[JAVA,'-Xmx512m','-jar',str(jar),'--request',str(home/'request.json')]);run(profile+'-verify',[JAVA,'-Xmx512m','-jar',str(jar),'--verify',str(home/'analysis')])
            provenance=json.loads((home/'analysis/provenance.json').read_bytes());warning=(home/'analysis/WARNINGS.md').read_text(encoding='utf8');comparisons=table(home/'analysis/results/comparison/pooled-and-sample-probabilities.tsv')
            if profile=='constant-comparison':
                assert provenance['original_nonfinite_sample_test_fields']>0 and 'ORIGINAL NONFINITE SAMPLE TEST WARNING:' in warning
                assert any(r['status']=='original-nonfinite-wilcox-result' and r['wilcox_pvalue'].strip()=='NaN' for r in comparisons)
            else:
                assert provenance['not_tested_sample_comparisons']>0 and 'COMPARISON COVERAGE WARNING:' in warning
                assert provenance['positive_pooled_rows_with_zero_selected_sample_scores']>0 and 'POOLED VERSUS SAMPLE WARNING:' in warning and len(provenance['pooled_sample_zero_examples'])==5
                pooledZeroed=0;rareZeroed=0
                for id in ['condition-1','condition-2']:
                    case=home/'analysis/results/cases'/id;pairs=table(case/'selected-interactions.tsv');vega={r['resource_row'] for r in pairs if r['ligand']=='VEGFA'};assert vega
                    before=table(case/'probabilities-unfiltered.tsv');after=table(case/'probabilities-filtered.tsv');assert len(before)==len(after)
                    for x,y in zip(before,after,strict=True):
                        if x['interaction'] in vega and float(x['value'])>0 and y['source']!='rare' and y['target']!='rare':assert float(y['value'])==0;pooledZeroed+=1
                for i in range(1,7):
                    case=home/'analysis/results/cases'/('sample-'+str(i));before=table(case/'probabilities-unfiltered.tsv');after=table(case/'probabilities-filtered.tsv')
                    for x,y in zip(before,after,strict=True):
                        if 'rare' in [y['source'],y['target']] and float(x['value'])>0:assert float(y['value'])==0;rareZeroed+=1
                assert pooledZeroed>0 and rareZeroed>0
                provenance.update(verified_pooled_VEGFA_edges_removed_by_three_sample_filter=pooledZeroed,verified_rare_edges_zeroed_at_exact_min_cells10=rareZeroed)
            reports.append(dict(profile=profile,provenance=provenance,receipt=pin(home/'analysis/COMPLETED.json')));print('PASS original CellChat edge family '+profile,flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='PASS_TWO_ACTUAL_ORIGINAL_CELLCHAT_REPLICATE_BOUNDARY_AND_NONFINITE_FAMILIES',families=reports,source_pins=dict(built['source_pins'],**pins),artifact=pin(jar))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source filtering/nonfinite assertions')
    raise SystemExit(main())
