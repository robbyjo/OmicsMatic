# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual packaged SoupX input, runtime and complete-output admission against saved references."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_mashr_native import JAVA21
from probe_soupx_workflow_worker import inputs,analyses


def sparse(path):
    root=json.loads(path.read_bytes());values={}
    for name in root['entry_parts']:
        for e in json.loads((path.parent/name).read_bytes())['entries']:
            key=e['row']-1,e['column']-1;assert key not in values;values[key]=e['value']
    return root['row_ids'],root['column_ids'],values


def table(path):
    root=json.loads(path.read_bytes())
    if 'parts' in root:
        records=[]
        for name in root['parts']:records.extend(table(path.parent/name))
        assert len(records)==root['rows'];return records
    return [{c['name']:c['tokens'][i] for c in root['columns']} for i in range(root['rows'])]


def reference_check(result,version):
    attempt=2 if version=='1.6.2' else 3;base=ROOT/f'build/literature-review/soupx-runtime/ambient-controls-{version}-{attempt}';reference=json.loads((base/'controls.json').read_bytes());cases={r['id']:r for r in reference['cases']};reports=[]
    for name in ['fixed-constant','fixed-per-cell']:
        rows,cells,values=sparse(result/'results'/name/'corrected-counts.json');expected=cases['fixed-cell-only-subtraction-FALSE']['corrected'];assert rows==expected['genes'] and cells==expected['cells'];ev={(e['gene'],e['cell']):e['value'] for e in expected['entries']};assert values==ev;reports.append(dict(id=name,corrected_entries_exactly_match_saved_original=len(values)))
    for name,source in [('manual-provided','manual-supplied-mask'),('manual-estimated','manual-automatic-mask-supplied-clusters'),('auto-estimate','auto-P145-overrides' if version=='1.6.2' else None),('auto-adjust','auto-P145-overrides' if version=='1.6.2' else 'auto-legacy-declared-apply')]:
        if source is None:continue
        original=cases[source];expected=original['channel_metadata'] if 'channel_metadata' in original else original['metadata'];observed=table(result/'results'/name/'metadata.json');assert len(expected)==len(observed)
        for i,(old,new) in enumerate(zip(expected,observed)):
            for field,value in old.items():assert new[field]==value,(name,i,field,new[field],value)
        reports.append(dict(id=name,metadata_rows_exactly_match_saved_original=len(observed)))
    if version=='1.3.0':
        expected=[{k:('TRUE' if value else 'FALSE') if isinstance(value,bool) else value for k,value in row.items()} for row in cases['auto-legacy-defaults']['table']]
        for name in ['auto-estimate','auto-adjust']:assert table(result/'results'/name/'legacy-auto-table.json')==expected;reports.append(dict(id=name,legacy_estimate_rows_exactly_match_saved_original=len(expected),logical_JSON_boolean_to_R_token_representation_checked=True))
    return reports


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};fixture=inputs(out/'inputs');reference=base/('reference-1.6.2-1' if a.version=='1.6.2' else 'reference-1.3.0-2');libraries=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before);source=base/('source-intake-1/1.6.2/source' if a.version=='1.6.2' else 'source-recovery-1/1.3.0/source');request=dict(schema_version=1,mode='AMBIENT',version=a.version,count_units='raw-integer-counts',matrix_format='matrix-market',clusters='supplied',counts_relation='raw-subset' if fixture['raw_subset_equal'] else 'supplied-independent',raw_counts='inputs/raw-counts',filtered_counts='inputs/filtered-counts',gene_metadata='inputs/genes.tsv',droplet_metadata='inputs/droplets.tsv',cell_metadata='inputs/cells.tsv',gene_sets='inputs/gene-sets.tsv',nonexpression_mask='inputs/nonexpression-mask.tsv',rho_by_cell='inputs/rho-by-cell.tsv',soup_source=str(source),soup_range=[0,100],analyses=analyses(a.version),out='result',rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_home='C:/Program Files/R/R-4.6.1',r_libraries=list(map(str,libraries)),transport_chunk_entries=1000);save(out/'request.json',request);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/probe_soupx_workflow_worker.py',out/'request.json',*[p for p in (out/'inputs').glob('*') if p.is_file()]]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    command=[str(JAVA21/'java.exe'),'-Xmx768m','-cp',str(jar),'org.jlinalg.cli.SoupXWorkflowCli','--request',str(out/'request.json')];save(out/'STARTED.json',dict(command=command,source_pins=pins,artifact=build['artifact']))
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state=json.loads((out/'result/analysis-status.json').read_bytes());checks=[]
    if r.returncode==0:assert state['result_admitted'];checks=reference_check(out/'result',a.version)
    else:assert not state['result_admitted'] and not (out/'result/results').exists()
    save(out/'COMPLETED.json',dict(status='passed-packaged-SoupX-development-probe' if r.returncode==0 else 'failed-packaged-SoupX-development-probe',version=a.version,artifact=build['artifact'],source_pins=pins,preexisting_libraries_unchanged=True,reference_comparisons=checks,analysis_status=state,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print(json.dumps(dict(exit_code=r.returncode,status=state['status'],cause=state.get('cause'),reference_comparisons=len(checks))));return r.returncode


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable input/source/reference checks')
    raise SystemExit(main())
