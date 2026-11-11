# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete typed original dndscv cases from the actual candidate and compare every saved output table/array."""
import argparse,gzip,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_mashr_native import JAVA21
from probe_dndscv_worker import base_analysis,table


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};reference=base/'reference-setup-3';libs=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);saved=base/'original-controls-1';source=base/'source-intake-1/source';data=json.loads((saved/'inputs.json').read_bytes());frame=data['mutations'];(out/'mutations.tsv').write_text('sample_id\tchromosome\tposition\treference\talternate\n'+''.join('\t'.join(c['tokens'][i] for c in frame['columns'])+'\n' for i in range(frame['rows'])),encoding='utf8',newline='\n');(out/'genes.tsv').write_text('gene_id\n'+'\n'.join(data['target_genes'])+'\n',encoding='utf8',newline='\n');cases=[base_analysis(),base_analysis('targeted-2','2r_3w'),dict(base_analysis('one-sided-selection'),one_sided=True)];request=dict(schema_version=1,mode='SELECTION',profile='source-43c5e2f1beaaf3ac26df9c046b281ceb0edbfff7',independent_events='caller-asserted',genetic_code=1,mutations='mutations.tsv',duplicate_policy='source-collapse-with-ledger',reference=dict(kind='builtin',assembly='hg19',opportunities='verify-unweighted-standard-code'),genes=dict(kind='supplied',file='genes.tsv',cdkn2a_policy='source-expand-isoforms'),covariates=dict(kind='builtin',name='hg19'),known_cancer_genes=dict(kind='builtin',name='cgc81'),duplex=dict(kind='none'),analyses=cases,source=str(source),out='result',rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_home='C:/Program Files/R/R-4.6.1',r_libraries=list(map(str,libs)),transport_chunk_rows=1000);save(out/'request.json',request);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/probe_dndscv_worker.py']};command=[str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.DndscvWorkflowCli','--request',str(out/'request.json')];save(out/'STARTED.json',dict(command=command,artifact=build['artifact'],source_pins=pins));state=dict(status='FAIL2_DNDSCV_TYPED_WORKFLOW_PROBE_INCOMPLETE',artifact=build['artifact'],source_pins=pins,implementation_credit=False,whole_goal_complete=False)
    try:
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect result/analysis-status.json, WARNINGS.md and retained worker outputs';status=json.loads((out/'result/analysis-status.json').read_bytes());assert status['result_admitted'];comparisons=[]
        for case in cases:
            home=out/'result/results'/case['id'];old=saved/case['id'];n=0
            for name in ['globaldnds','sel_cv','sel_loc','annotmuts','genemuts','geneindels','mle_submodel','syncv','wrongmuts','exclmuts']:
                original=old/(name+'.json')
                if not original.exists():assert not (home/(name+'.json')).exists();continue
                value=json.loads(original.read_bytes());expected=([c['name'] for c in value['columns']],value['row_names'],[[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])]);assert table(home/(name+'.json'))==expected,(case['id'],name);n+=1
            for name in ['N.f64be.gz','L.f64be.gz']:assert gzip.decompress((home/name).read_bytes())==gzip.decompress((old/name).read_bytes()),(case['id'],name)
            assert (home/'native-event-ledger.tsv').is_file();comparisons.append(dict(id=case['id'],complete_original_tables_compared=n,complete_N_and_L_arrays_exact=True,native_event_ledger_present=True))
        state.update(status='passed-typed-original-dndscv-workflow-probe',reference_comparisons=comparisons,result_admitted=True)
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert before==after and all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',state)
    print(json.dumps(state));return 0 if state['status']=='passed-typed-original-dndscv-workflow-probe' else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    raise SystemExit(main())
