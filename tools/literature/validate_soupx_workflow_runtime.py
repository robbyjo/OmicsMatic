# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test actual original SoupX correction matrices, complete estimator pipelines and prominent skip/failure admission."""
import argparse,copy,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_mashr_native import JAVA21
from probe_soupx_workflow_worker import inputs,fixed,manual,analyses
from probe_soupx_workflow import sparse,table


def matrix_equal(path,expected):
    genes,cells,values=sparse(path);assert genes==expected['genes'] and cells==expected['cells'];original={(e['gene'],e['cell']):e['value'] for e in expected['entries']};assert values==original,'Complete original corrected coordinate/value mismatch';return len(values)


def adjustment(scope='cell-only',method='subtraction',rounded=False):return dict(method=method,clustering_scope=scope,round_to_integer=rounded,tolerance=.001,p_cut=.01)
def case(identifier,e,d):return dict(id=identifier,seed=190731,estimation=e,adjustment=d)
def correction_matrix(version):return [case(f'fixed-{scope}-{method}-{str(rounded).upper()}',fixed(version),adjustment(scope,method,rounded)) for scope in ['cell-only','supplied-clusters'] for method in ['subtraction','soupOnly','multinomial'] for rounded in [False,True]]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};chains={};all_libraries=[];sources={};references={};old={};pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/probe_soupx_workflow_worker.py',ROOT/'tools/literature/probe_soupx_workflow.py']}
    for version in ['1.6.2','1.3.0']:
        ref=base/('reference-1.6.2-1' if version=='1.6.2' else 'reference-1.3.0-2');chains[version]=[ref/'R-lib',*map(Path,json.loads((ref/'STARTED.json').read_bytes())['libraries'])];all_libraries.extend(chains[version]);sources[version]=base/('source-intake-1/1.6.2/source' if version=='1.6.2' else 'source-recovery-1/1.3.0/source');reference=base/f'adjustment-reference-{version}-1';proof=json.loads((reference/'COMPLETED.json').read_bytes());assert proof['status']=='captured-direct-original-SoupX-adjustment-references';references[version]={r['id']:r for r in json.loads((reference/'controls.json').read_bytes())['cases']};oldpath=base/('ambient-controls-1.6.2-2/controls.json' if version=='1.6.2' else 'ambient-controls-1.3.0-3/controls.json');old[version]={r['id']:r for r in json.loads(oldpath.read_bytes())['cases']}
        for path in [reference/'COMPLETED.json',reference/'controls.json',reference/'rho-by-cell.tsv',oldpath]:pins[path.relative_to(ROOT).as_posix()]=pin(path)
    libraries=list(dict.fromkeys(all_libraries));before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    reports=[]
    def run(identifier,version,cases,code=0,changes=None,mutate=None,dense=False,expected=None,maximum_seconds=180):
        folder=out/identifier;folder.mkdir();data=inputs(folder/'inputs');request=dict(schema_version=1,mode='AMBIENT',version=version,count_units='raw-integer-counts',matrix_format='dense-tsv' if dense else 'matrix-market',clusters='supplied',counts_relation='raw-subset' if data['raw_subset_equal'] else 'supplied-independent',raw_counts='inputs/raw-counts',filtered_counts='inputs/filtered-counts',gene_metadata='inputs/genes.tsv',droplet_metadata='inputs/droplets.tsv',cell_metadata='inputs/cells.tsv',soup_source=str(sources[version]),soup_range=[0,100],analyses=copy.deepcopy(cases),out='result',rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_home='C:/Program Files/R/R-4.6.1',r_libraries=list(map(str,chains[version])),transport_chunk_entries=1000)
        if any(c['estimation']['kind']=='manual-global-poisson' for c in cases):request['gene_sets']='inputs/gene-sets.tsv'
        if any(c['estimation'].get('mask_mode')=='provided' for c in cases):request['nonexpression_mask']='inputs/nonexpression-mask.tsv'
        if any(c['estimation'].get('rho_mode')=='per-cell' for c in cases):request['rho_by_cell']='inputs/rho-by-cell.tsv'
        if dense:
            for name,m in [('raw-counts',data['raw']),('filtered-counts',data['filtered'])]:
                values={(e['gene'],e['cell']):e['value'] for e in m['entries']};(folder/'inputs'/name).write_text('gene_id\t'+'\t'.join(m['cells'])+'\n'+''.join(g+'\t'+'\t'.join(values.get((i,j),'0') for j in range(len(m['cells'])))+'\n' for i,g in enumerate(m['genes'])),encoding='utf8',newline='\n')
        request.update(changes or {})
        if mutate:mutate(folder,request)
        save(folder/'request.json',request);command=[str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.SoupXWorkflowCli','--request',str(folder/'request.json')];save(folder/'STARTED.json',dict(command=command,artifact=build['artifact']))
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=maximum_seconds)
        save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==code,(identifier,r.returncode,(folder/'stderr').read_text(encoding='utf8',errors='replace')[-1600:]);result=folder/'result';state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted']==(code==0) and (result/'results').exists()==(code==0);warnings=(result/'WARNINGS.md').read_text(encoding='utf8');assert 'REQUIRED ACTION:' in warnings and 'WARNINGS / REQUIRED ACTIONS:' in (folder/'stderr').read_text(encoding='utf8');cleanup=result/'worker/process-receipt.json';started=cleanup.exists()
        if started:
            receipt=json.loads(cleanup.read_bytes());assert receipt['owned_process_cleanup'] and all(not r['alive_after_cleanup'] for r in receipt['owned_process_cleanup'])
        report=dict(id=identifier,version=version,exit_code=code,result_admitted=code==0,owned_worker_started=started,owned_processes_terminal=True)
        if code==0:
            comparisons=[]
            for c in cases:
                if c['adjustment'] is None:assert not (result/'results'/c['id']/'corrected-counts.json').exists();continue
                ref=(expected or {}).get(c['id'])
                if ref is not None:comparisons.append(dict(id=c['id'],corrected_entries_exactly_match_direct_original=matrix_equal(result/'results'/c['id']/'corrected-counts.json',ref)))
            report['reference_comparisons']=comparisons
        else:assert state['status']==('SKIP3_DEPENDENCY_UNAVAILABLE' if code==3 else 'FAIL2_NO_RESULT_ADMITTED');report['cause']=state['cause']
        reports.append(report);return folder,state
    for version in ['1.6.2','1.3.0']:
        cm=correction_matrix(version);expected={c['id']:old[version][c['id']]['corrected'] for c in cm};run('correction-matrix-'+version,version,cm,expected=expected)
        e=copy.deepcopy(analyses(version)[-1]['estimation']);e2=copy.deepcopy(e)
        if version=='1.3.0':e2['apply_density_mode']=False
        pipeline=[case('varied-rho-cell-only',fixed(version,'per-cell'),adjustment()),case('varied-rho-supplied-clusters',fixed(version,'per-cell'),adjustment('supplied-clusters')),case('manual-provided-adjust',manual(version,True),adjustment('supplied-clusters')),case('manual-estimated-adjust',manual(version,False),adjustment()),case('auto-adjust-cell-only',e,adjustment()),case('auto-adjust-supplied-clusters',e,adjustment('supplied-clusters')),case('auto-estimation-only',e2,None)]
        def varied(folder,request):
            rho=base/f'adjustment-reference-{version}-1/rho-by-cell.tsv';(folder/'inputs/rho-by-cell.tsv').write_bytes(rho.read_bytes())
        run('complete-pipelines-'+version,version,pipeline,mutate=varied,expected=references[version]);run('dense-input-'+version,version,[case('dense-fixed',fixed(version),adjustment())],dense=True,expected={'dense-fixed':old[version]['fixed-cell-only-subtraction-FALSE']['corrected']})
    simple=[case('fixed',fixed('1.6.2'),adjustment())]
    def no_mask(folder,request):
        path=folder/'inputs/nonexpression-mask.tsv';rows=path.read_text(encoding='utf8').splitlines();path.write_text('\n'.join([rows[0],*['\t'.join([*r.split('\t')[:2],'false']) for r in rows[1:]]])+'\n',encoding='utf8',newline='\n')
    atomic=[case('earlier-success',fixed('1.6.2'),adjustment()),case('manual-failure',manual('1.6.2',True),None)];folder,state=run('late-original-case-failure','1.6.2',atomic,code=2,mutate=no_mask);assert (folder/'result/worker/cases/earlier-success/corrected-counts.json').exists();assert 'manual-failure [estimation]' in (folder/'result/WARNINGS.md').read_text(encoding='utf8') and 'No cells specified' in state['cause'];reports[-1]['earlier_original_success_retained_but_not_admitted']=True
    high=fixed('1.6.2');high['rho']=.9;run('current-high-rho-refusal','1.6.2',[case('high-rho',high,None)],code=2)
    run('missing-rscript','1.6.2',simple,code=3,changes={'rscript':str(out/'missing-Rscript.exe')});run('wrong-installed-edition','1.6.2',simple,code=3,changes={'r_libraries':list(map(str,chains['1.3.0']))})
    def corrupt_source(folder,request):
        profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/soupx-workflow/SOURCE-PROFILE.json').read_bytes());source=folder/'wrong-source';source.mkdir()
        for name in profile['editions']['1.6.2']['files']:
            target=source/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((sources['1.6.2']/name).read_bytes())
        path=source/'R/adjustCounts.R';path.write_bytes(path.read_bytes()+b'\n# Modified source must not pass exact profile admission\n');request['soup_source']=str(source)
    run('changed-original-source','1.6.2',simple,code=3,mutate=corrupt_source);run('native-operation-capacity','1.6.2',simple,code=2,changes={'maximum_audit_operations':1});run('worker-output-capacity','1.6.2',simple,code=2,changes={'maximum_output_bytes':1});folder,state=run('worker-timeout','1.6.2',simple,code=2,changes={'timeout_seconds':1,'transport_chunk_entries':1});assert 'timed out' in state['cause']
    after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-original-SoupX-runtime-correction-pipeline-and-failure-protocols',artifact=build['artifact'],source_pins=pins,protocols=reports,complete_corrected_matrices_compared=sum(len(r.get('reference_comparisons',[])) for r in reports),complete_corrected_entries_compared=sum(v['corrected_entries_exactly_match_direct_original'] for r in reports for v in r.get('reference_comparisons',[])),preexisting_libraries_unchanged=True,preexisting_files=sum(map(len,before.values())),ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Passed',len(reports),'runtime protocols;',sum(len(r.get('reference_comparisons',[])) for r in reports),'complete original corrected matrices compared; failures/skips prominent')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/runtime/admission checks')
    main()
