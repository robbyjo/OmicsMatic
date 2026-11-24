"""Check the scoped Genomic SEM workflow checkpoint; never refit or admit a catalog use."""
from pathlib import Path
import argparse,json,subprocess,sys,xml.etree.ElementTree as E
from genomicsem.primary import audit
from genomicsem.runtime import pin,require

FOLDER='docs/literature/genomicsem-workflow-checkpoint'
PROFILE='genomicsem-da95d43-r461-win64-lavaan0619-v1'
PRODUCER=dict(bytes=199832559,sha256='ca871beca9ebc856bccc059cfb9c267610adbc59dbe4aed3ce7cfdda964c29f6')
FIELDS=('original_rows','original_tables','inferential_rows','source_missing_or_fixed_inference_rows','unavailable_source_model_fit_indices','Q_factor_tests','warning_occurrences','bounded_source_P_labels','zero_source_P_endpoints','negative_variance_estimates','nonzero_source_smoothing_diagnostics','S_or_V_required_upstream_smoothing','association_units','requested_association_paths')

def read(path):return json.loads(path.read_bytes())

def closure(root,files):
    require(files and len(files)<=50000,'Empty/excessive saved file manifest')
    for name,identity in files.items():
        p=Path(name)
        require(not p.is_absolute() and '..'not in p.parts and '\\'not in name and ':'not in name,'Unsafe saved member')
        actual=root/p;require(actual.resolve()==actual.absolute() and actual.is_file(),'Missing/aliased saved member: '+name)
        require(pin(actual)==identity,'Changed saved member: '+name)

def verify(root,cache=None,private=None,jar=None,java='java'):
    root=Path(root).absolute();d=root/FOLDER;status=read(d/'STATUS.json');validation=read(d/'VALIDATION.json')
    require(status['status']=='PASS_PREPARED_ORIGINAL_GENOMICSEM_WORKFLOW_PENDING_CATALOG' and status['profile']==PROFILE,'Changed scope/profile')
    require(status['producer']==PRODUCER and status['java_adapter_implemented'] and not status['catalog_admitted'],'Changed producer/admission')
    require((status['scoped_families'],status['native'],status['upstream'],status['reviewed_uses'],status['pending_uses'],status['new_family_credit'],status['new_review_credit'])==(159,30,129,1760,964,0,0),'Changed unchanged catalog totals')
    require(not status['whole_goal_complete'] and not status['historical_paper_editions_qualified'] and not status['preprocessing_qualified'],'Expanded whole-tool or historical scope')
    require(pin(d/'FILES.json')==status['public_manifest'],'Changed public source manifest');public=read(d/'FILES.json');closure(root,public)
    canonical=read(d/'CANONICAL.json');require(len(canonical)==9,'Incomplete canonical population');closure(root,canonical)
    cases=read(root/'tools/literature/genomicsem_workflow/fixtures/CASES.json');names=[c['name']for c in cases]
    require(len(names)==len(set(names))==31 and [c['case']for c in validation['cases']]==names,'Changed synthetic qualification population')
    require(all(c['original_R_object_identical_including_attributes'] and c['saved_inspection_passed']for c in validation['cases']),'Incomplete original/source comparison')
    require(validation['Windows_Java_tests']==validation['Linux_Java_tests']==31 and validation['fresh_original_refits']==2 and validation['portable_saved_inspection'] and validation['Linux_original_execution_exit']==3,'Incomplete platform/fresh evidence')
    require(len(validation['refusals'])==21 and all(c['actual_exit']==c['expected_exit'] and c['actual_exit']in(2,3)for c in validation['refusals']),'Incomplete actual refusal controls')
    require(validation['documented_example_runs']==2 and validation['primary_XML_checked']==250 and validation['source_roles']==5,'Incomplete example/source evidence')
    require(next(c for c in validation['cases']if c['case']=='explicit-upstream-smoothing')['scientific_summary']['S_or_V_required_upstream_smoothing'],'Unexercised upstream smoothing')
    primary=audit(root,cache)if cache is not None else None;private_count=None;inspected=0
    if private is not None:
        require(jar is not None,'--private requires the exact --jar');private=Path(private).absolute();jar=Path(jar).absolute();require(pin(jar)==PRODUCER,'Restore exact whole producer JAR; never refresh an old hash')
        manifest=private/'PRIVATE-CLOSURE.json';require(pin(manifest)==status['private_evidence_manifest'],'Changed local evidence closure');files=read(manifest);closure(private,files);private_count=len(files)
        result=read(private/'comparisons-final-2/RESULT.json');require(result['status']=='PASS' and result['jar']==PRODUCER and [c['case']for c in result['cases']]==names and all(c['passed']for c in result['cases']),'Incomplete private qualification')
        captures=[];rolefiles=dict(covariance='S.tsv',sampling_covariance='V.tsv',intercepts='I.tsv',associations='associations.tsv',model='model.txt')
        for c,declared in zip(cases,validation['cases'],strict=True):
            folder=private/'comparisons-final-2'/c['name'];capture=folder/'capture';captures.append(capture)
            original=read(folder/'reference/COMPARISON.json');require(original['status']=='PASS_ORIGINAL_RESULT_IDENTICAL' and original['identical'] and original['all_equal_zero_tolerance'] is True,'Original R object comparison differs')
            summary=read(capture/'scientific-audit.json');require(summary['results_admitted'] and {k:summary[k]for k in FIELDS}==declared['scientific_summary'],'Private/public scientific summary differs')
            for role,name in c['request']['inputs'].items():require(pin(root/'tools/literature/genomicsem_workflow/fixtures'/name)==pin(capture/'inputs'/rolefiles[role]),'Changed synthetic input binding')
        fresh=read(private/'fresh-portable-2/RESULT.json');require(len(fresh['cases'])==4 and [c['exit_code']for c in fresh['cases']]==[0,0,0,3],'Changed fresh/portable results')
        for mode in ('verify','replay'):
            capture=private/'fresh-portable-2'/mode;captures.append(capture);require(read(capture/'fresh-comparison.json')['new_original_computation_executed'],'No new original fit recorded')
        for name in ('model','gwas'):captures.append(private/'example-validation-1/example'/(name+'-run'))
        refused=read(private/'controls-2/RESULT.json')['cases']+read(private/'controls-extra-1/RESULT.json')['cases']
        require([{k:c[k]for k in ('case','actual_exit','expected_exit')}for c in refused]==validation['refusals'],'Private/public refusal evidence differs')
        total=0
        for suite in ('GenomicsemWorkflowTest','OwnedProcessesTest'):
            x=E.parse(private/'normal-build-6/project-build/test-results/test'/('TEST-org.jlinalg.cli.'+suite+'.xml')).getroot();require(all(int(x.get(k,'0'))==0 for k in ('failures','errors','skipped')),'Unsuccessful actual Windows tests');total+=int(x.get('tests'))
        require(total==31 and read(private/'linux-java-tests-2/RESULT.json')['exit_code']==0 and '31 tests successful'in(private/'linux-java-tests-2/stdout').read_text(),'Changed actual Java tests')
        for capture in captures:
            p=subprocess.run([java,'-Xmx4g','-jar',str(jar),'genomicsem-tools','--inspect',str(capture)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            require(p.returncode==0,'Saved inspection failed: '+capture.name+'; '+p.stderr.decode(errors='replace')[:300]);inspected+=1
    return dict(status='PASS_SCOPED_GENOMICSEM_WORKFLOW_CHECKPOINT',public_source_files=len(public),private_evidence_files=private_count,saved_captures_inspected=inspected,primary_source_audit=primary,synthetic_original_comparisons=31,actual_refusal_controls=21,fresh_original_refits=2,Java_tests_per_platform=31,catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False,no_statistical_refit_performed=True)

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--cache',type=Path);p.add_argument('--private',type=Path);p.add_argument('--jar',type=Path);p.add_argument('--java',default='java');a=p.parse_args(argv)
    try:print(json.dumps(verify(a.root,a.cache,a.private,a.jar,a.java),indent=2));return 0
    except (OSError,ValueError,KeyError,TypeError)as e:print('FAIL2: Genomic SEM workflow checkpoint did not verify: '+str(e)+'. Preserve the attempt and restore its exact source/producer/evidence. This checker performs no fit and grants no catalog or whole-paper credit.',file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
