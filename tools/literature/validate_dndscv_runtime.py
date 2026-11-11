# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original dndscv options and actual atomic failure/dependency/resource protocols."""
import argparse,copy,gzip,json,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_mashr_native import JAVA21
from probe_dndscv_worker import base_analysis,table


def compare(home,old):
    n=0
    for name in ['globaldnds','sel_cv','sel_loc','annotmuts','genemuts','geneindels','mle_submodel','syncv','wrongmuts','exclmuts']:
        source=old/(name+'.json')
        if not source.exists():assert not (home/(name+'.json')).exists();continue
        value=json.loads(source.read_bytes());expected=([c['name'] for c in value['columns']],value['row_names'],[[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])]);assert table(home/(name+'.json'))==expected,(home.name,name);n+=1
    for name in ['N.f64be.gz','L.f64be.gz']:assert gzip.decompress((home/name).read_bytes())==gzip.decompress((old/name).read_bytes()),(home.name,name)
    return dict(complete_original_tables_compared=n,complete_N_and_L_arrays_exact=True)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--additional',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--site-reference',required=True,type=Path);p.add_argument('--edge',required=True,type=Path);p.add_argument('--reuse-from',type=Path);a=p.parse_args();a.additional=a.additional.resolve();a.site_reference=a.site_reference.resolve();a.edge=a.edge.resolve();a.reuse_from=a.reuse_from.resolve() if a.reuse_from else None;base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};template=json.loads((base/'workflow-probe-1/request.json').read_bytes());libs=list(map(Path,template['r_libraries']));before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/probe_dndscv_worker.py',a.additional/'COMPLETED.json',a.additional/'controls.json',a.site_reference/'COMPLETED.json',a.edge/'COMPLETED.json',a.edge/'controls.json']};reports=[]
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    if a.reuse_from:
        prior=json.loads((a.reuse_from/'COMPLETED.json').read_bytes());assert prior['artifact']==build['artifact'];assert (a.reuse_from/'LIBRARIES-BEFORE.json').read_bytes()==(a.reuse_from/'LIBRARIES-AFTER.json').read_bytes();assert json.loads((a.reuse_from/'LIBRARIES-AFTER.json').read_bytes())==before
    original=base/'original-controls-1';input_source=base/'workflow-probe-1'
    def run(identifier,request,expected,inputs=None):
        home=out/identifier;home.mkdir();request=copy.deepcopy(request)
        if inputs is None:
            shutil.copyfile(input_source/'mutations.tsv',home/'mutations.tsv');shutil.copyfile(input_source/'genes.tsv',home/'genes.tsv')
        else:shutil.copytree(inputs,home/'inputs')
        sets=request.pop('_gene_sets',None)
        if sets:(home/'gene-sets.tsv').write_text('gene_set\tgene_id\n'+''.join(name+'\t'+gene+'\n' for name,genes in sets.items() for gene in genes),encoding='utf8',newline='\n')
        request['out']='result';save(home/'request.json',request);command=[str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.DndscvWorkflowCli','--request',str(home/'request.json')]
        previous=a.reuse_from/identifier if a.reuse_from else None
        reused=previous is not None and (previous/'process.json').exists()
        if reused:
            old=json.loads((previous/'process.json').read_bytes());assert old['exit_code']==expected and json.loads((previous/'request.json').read_bytes())==request
            for path in home.rglob('*'):
                if path.is_file() and path.name!='request.json':assert pin(path)==pin(previous/path.relative_to(home))
            (home/'request.json').write_bytes((previous/'request.json').read_bytes());result=previous/'result';assert json.loads((result/'effective-request.json').read_bytes())['profile']==template['profile'];save(home/'READONLY-REUSE.json',dict(original_attempt=previous.relative_to(ROOT).as_posix(),original_request=pin(previous/'request.json'),original_process=pin(previous/'process.json'),original_status=pin(result/'analysis-status.json'),artifact=build['artifact'],model_or_R_process_reexecuted=False,failed_overall_previous_checker_not_rewritten=True));code=old['exit_code']
        else:
            with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
            code=r.returncode;save(home/'process.json',dict(command=command,exit_code=code));assert code==expected,(identifier,code,(home/'stderr').read_text(encoding='utf8',errors='replace')[-1200:]);result=home/'result'
        state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted']==(expected==0);receipt=result/'worker/process-receipt.json'
        if receipt.exists():assert all(not p['alive_after_cleanup'] for p in json.loads(receipt.read_bytes())['owned_process_cleanup'])
        if expected:assert not (result/'results').exists() and 'REQUIRED ACTION:' in (result/'WARNINGS.md').read_text(encoding='utf8')
        reports.append(dict(id=identifier,exit_code=expected,result_admitted=expected==0,owned_worker_started_in_original_run=receipt.exists(),read_only_reuse=reused));print(identifier,'passed'+(' (read-only reuse)' if reused else ''),flush=True);return result,reports[-1]
    state=dict(status='FAIL2_DNDSCV_RUNTIME_VALIDATION_INCOMPLETE',artifact=build['artifact'],source_pins=pins,protocols=reports,implementation_credit=False,whole_goal_complete=False)
    try:
        group=copy.deepcopy(template);cases=[base_analysis('targeted-12','12r_3w'),dict(base_analysis('default-event-limits'),max_muts_per_gene_per_sample=3,max_coding_muts_per_sample=3000),dict(base_analysis('unconstrained-truncation'),constrain_truncation=False),dict(base_analysis(),gene_set_comparison=True)];group['analyses']=cases;rows=json.loads((original/'targeted-192/genemuts.json').read_bytes());genes=next(c['tokens'] for c in rows['columns'] if c['name']=='gene_name')[:20];group['gene_sets']='gene-sets.tsv';group['_gene_sets']={'original-first20':genes};result,report=run('original-options-and-gene-set',group,0);report['reference_comparisons']=[dict(id=c['id'],**compare(result/'results'/c['id'],original/c['id'])) for c in cases]
        for name in ['globaldnds_geneset','globaldnds_rest']:
            value=json.loads((original/'targeted-192'/(name+'.json')).read_bytes());expected=([c['name'] for c in value['columns']],value['row_names'],[[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])]);assert table(result/'results/targeted-192/gene-sets/set-000001'/(name+'.json'))==expected
        no_cov=copy.deepcopy(template);no_cov['covariates']={'kind':'none'};no_cov['analyses']=[base_analysis('no-covariates')];result,report=run('no-covariates',no_cov,0);report['reference_comparisons']=[dict(id='no-covariates',**compare(result/'results/no-covariates',original/'no-covariates'))]
        additional=json.loads((a.additional/'controls.json').read_bytes())
        for case in additional['cases']:
            config=json.loads((a.additional/case['id']/'runtime-input.json').read_bytes());request=copy.deepcopy(template);request.update(config);returned=case['returned'] or case['id']=='per-site-coverage';result,report=run(case['id'],request,0 if returned else 2,a.additional/case['id']/'inputs')
            if returned:report['reference_comparisons']=[dict(id=case['id'],**compare(result/'results'/case['id'],a.site_reference if case['id']=='per-site-coverage' else a.additional/case['id']))]
            else:assert case['id'] in (result/'WARNINGS.md').read_text(encoding='utf8') and 'FAIL2' in (result/'WARNINGS.md').read_text(encoding='utf8')
        for case in json.loads((a.edge/'controls.json').read_bytes())['cases']:
            assert case['returned'];request=copy.deepcopy(template);request.update(json.loads((a.edge/case['id']/'runtime-input.json').read_bytes()));result,report=run(case['id'],request,0,a.edge/case['id']/'inputs');report['reference_comparisons']=[dict(id=case['id'],**compare(result/'results'/case['id'],a.edge/case['id']))]
        late=copy.deepcopy(template);late['analyses']=[base_analysis('earlier-success'),dict(base_analysis('late-error'),gene_set_comparison=True)];late['gene_sets']='gene-sets.tsv';all_genes=next(c['tokens'] for c in rows['columns'] if c['name']=='gene_name');late['_gene_sets']={'missing-member-after-earlier-case':[all_genes[0],'ABSENT_REFERENCE_GENE']};result,report=run('late-original-gene-set-failure',late,2);assert json.loads((result/'worker/cases/earlier-success/source-status.json').read_bytes())['returned'] and (result/'worker/cases/earlier-success/original-return.rds').exists();assert 'late-error' in (result/'WARNINGS.md').read_text(encoding='utf8')
        missing=copy.deepcopy(template);missing['rscript']=str(out/'Rscript-unavailable.exe');missing['analyses']=[base_analysis()];result,_=run('missing-Rscript',missing,3);assert not (result/'worker/process-receipt.json').exists()
        missing_pkg=copy.deepcopy(template);missing_pkg['r_libraries']=template['r_libraries'][1:];missing_pkg['analyses']=[base_analysis()];run('missing-original-package',missing_pkg,3)
        source=out/'changed-source';source.mkdir();(source/'DESCRIPTION').write_text('changed original source\n',encoding='utf8');changed=copy.deepcopy(template);changed['source']=str(source);changed['analyses']=[base_analysis()];result,_=run('changed-original-source',changed,3);assert not (result/'worker/process-receipt.json').exists()
        for identifier,updates in [('native-audit-capacity',dict(maximum_audit_operations=5000)),('worker-growth-capacity',dict(maximum_output_bytes=4096)),('owned-worker-timeout',dict(timeout_seconds=1))]:
            request=copy.deepcopy(template);request['analyses']=[base_analysis()];request.update(updates);result,_=run(identifier,request,2);assert (result/'worker/process-receipt.json').exists()
        state['status']='passed-original-dndscv-runtime-options-and-atomic-failure-protocols'
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert before==after and all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',state)
    print(json.dumps({k:v for k,v in state.items() if k!='source_pins'}));return 0 if state['status']=='passed-original-dndscv-runtime-options-and-atomic-failure-protocols' else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/runtime/admission checks')
    raise SystemExit(main())
