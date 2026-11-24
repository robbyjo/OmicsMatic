"""Check scoped original preparation evidence; no refit or catalog admission.

Public metadata contains source identities and counts. Operational receipts,
private runtimes, original RDS and raw process traces remain local.
"""
from pathlib import Path
import argparse,json,subprocess,sys,xml.etree.ElementTree as E
from genomicsem.primary import audit
from genomicsem.runtime import pin,require

FOLDER='docs/literature/genomicsem-preparation-checkpoint'
PROFILE='genomicsem-preparation-da95d43-r461-win64-lavaan0619-v1'
PRODUCER=dict(bytes=199951327,sha256='38fd28150d960f78294d28a6ce9064f2c5c3aac0db20c151ff0a64f092a2d6c1')
OPERATIONS=['MUNGE','LDSC','SUMSTATS','READ_FUSION','S_LDSC','ENRICH']
SUMMARY_FIELDS=('operation','status','products_admitted','all_retained_units_admitted','available_units','unavailable_units','original_operation_warnings','original_initialization_warnings','original_error_or_nonconvergence_messages')

def read(path):return json.loads(path.read_bytes())

def closure(root,files):
    require(files and len(files)<=50000,'Empty/excessive saved file population')
    for name,identity in files.items():
        p=Path(name);require(not p.is_absolute()and '..'not in p.parts and '\\'not in name and ':'not in name,'Unsafe saved member')
        member=root/p;require(member.resolve()==member.absolute()and member.is_file(),'Missing/aliased saved member: '+name);require(pin(member)==identity,'Changed saved member: '+name)

def verify(root,cache=None,private=None,jar=None,java='java'):
    root=Path(root).absolute();d=root/FOLDER;s=read(d/'STATUS.json');v=read(d/'VALIDATION.json');require(s['status']=='PASS_SCOPED_ORIGINAL_GENOMICSEM_PREPARATION_PENDING_CATALOG'and s['profile']==PROFILE and s['producer']==PRODUCER,'Changed scope/producer')
    require(s['java_preparation_implemented']and not s['catalog_admitted']and not s['historical_paper_editions_qualified']and not s['whole_goal_complete'],'Expanded admission or historical scope')
    require((s['scoped_families'],s['native'],s['upstream'],s['reviewed_uses'],s['pending_uses'],s['new_family_credit'],s['new_review_credit'])==(159,30,129,1760,964,0,0),'Changed unchanged catalog totals')
    require(pin(d/'FILES.json')==s['public_manifest'],'Changed public source closure');public=read(d/'FILES.json');closure(root,public);canonical=read(d/'CANONICAL.json');require(len(canonical)==9,'Incomplete canonical population');closure(root,canonical)
    require(v['operations']==OPERATIONS and v['original_object_comparisons']==24 and v['admitted_original_object_comparisons']==22 and v['policy_rejected_original_object_comparisons']==2,'Changed original API comparison scope')
    require(v['Windows_Java_tests']==v['Linux_Java_tests']==44 and v['fresh_original_executions']==3 and v['documented_example_runs']==6 and v['downstream_fitting_operations']==4,'Incomplete platform, fresh, example or integration evidence')
    require(len(v['refusals'])==20 and all(x['actual_exit']==x['expected_exit']and x['actual_exit']in(2,3)for x in v['refusals']),'Incomplete actual refusal evidence')
    require(v['primary_XML_checked']==250 and v['source_roles']==5 and v['Linux_original_execution_exit']==3 and v['Linux_saved_captures_checked']==6,'Incomplete primary/platform evidence')
    require(len(v['admitted_cases'])==22 and len({x['case']for x in v['admitted_cases']})==22,'Incomplete admitted case population');primary=audit(root,cache)if cache is not None else None;private_count=None;inspected=0
    if private is not None:
        require(jar is not None,'--private requires the exact --jar');private=Path(private).absolute();jar=Path(jar).absolute();require(pin(jar)==PRODUCER,'Restore exact complete producer JAR; never refresh an old hash');manifest=private/'PRIVATE-CLOSURE.json';require(pin(manifest)==s['private_evidence_manifest'],'Changed private closure');files=read(manifest);closure(private,files);private_count=len(files)
        require(read(private/'normal-build-3/RESULT.json')['source_unchanged'],'Source changed during final executable build')
        for dirname in('packaged-final-3','variants-final-3','fresh-final-3','example-final-3','downstream-final-3','controls-final-3','controls-extra-final-3','portable-final-3'):require(read(private/dirname/'RESULT.json')['status']=='PASS','Incomplete actual qualification: '+dirname)
        direct=read(private/'packaged-final-3/direct-original/RESULT.json');variants=read(private/'variants-final-3/direct-original/RESULT.json');require(list(direct)==OPERATIONS and len(variants)==18,'Changed direct comparison population');require(all(x['identical_original_object_and_all_attributes']and x['comparison_tolerance']==0 and x['adapter_worker_not_used']for x in list(direct.values())+list(variants.values())),'Original result comparison did not pass')
        actual=[];captures=[]
        for op in OPERATIONS:
            path=private/'packaged-final-3'/(op+'-run');captures.append(('genomicsem-prep',path));actual.append(dict(case='base-'+op,operation=op,summary={k:read(path/'scientific-audit.json')[k]for k in SUMMARY_FIELDS}))
        specifications=read(private/'variants-final-3/CASES.json');observed=read(private/'variants-final-3/RESULT.json')['cases'];require([x['name']for x in specifications]==[x['case']for x in observed],'Changed variant execution population')
        for spec,result in zip(specifications,observed,strict=True):
            require(result['passed']and result['actual_exit']==result['expected_exit']==spec['expected_exit'],'Unexpected actual variant status')
            if result['actual_exit']==0:
                path=private/'variants-final-3'/spec['name'];captures.append(('genomicsem-prep',path));actual.append(dict(case=spec['name'],operation=spec['operation'],summary={k:read(path/'scientific-audit.json')[k]for k in SUMMARY_FIELDS}))
        require(actual==v['admitted_cases'],'Public/private admission summaries differ');refused=read(private/'controls-final-3/RESULT.json')['cases']+read(private/'controls-extra-final-3/RESULT.json')['cases']+[x for x in observed if x['actual_exit']!=0];require([{k:x[k]for k in('case','expected_exit','actual_exit')}for x in refused]==v['refusals'],'Public/private refusal population differs')
        retained=read(private/'variants-final-3/munge-zero-P-retain/scientific-audit.json');require(retained['unavailable_units']==4 and retained['products_admitted']and not retained['all_retained_units_admitted'],'Zero-P unavailable source endpoints were repaired or unexercised');require(read(private/'variants-final-3/sumstats-operation-warnings-retain/scientific-audit.json')['original_operation_warnings']==4,'Unexercised original warning policy')
        for fresh in('munge-verify','ldsc-replay','enrich-verify'):
            path=private/'fresh-final-3'/fresh;captures.append(('genomicsem-prep',path));require(read(path/'fresh-comparison.json')['new_original_computation_executed'],'No fresh original computation')
        for x in read(private/'fresh-final-3/RESULT.json')['cases']:require(x['parent_all_bytes_unchanged'],'Fresh execution changed its read-only parent')
        for name in('munge','ldsc','sumstats','fusion','stratified','enrich'):captures.append(('genomicsem-prep',private/'example-final-3/example'/(name+'-run')))
        for op in('COMMONFACTOR','USERMODEL','COMMONFACTOR_GWAS','COMMONFACTOR_TSEM'):captures.append(('genomicsem-tools',private/'downstream-final-3'/op))
        total=0
        for suite in('GenomicsemPreparationTest','GenomicsemWorkflowTest','OwnedProcessesTest'):
            x=E.parse(private/'normal-build-3/project-build/test-results/test'/('TEST-org.jlinalg.cli.'+suite+'.xml')).getroot();require(all(int(x.get(k,'0'))==0 for k in('failures','errors','skipped')),'Unsuccessful actual Java tests');total+=int(x.get('tests'))
        require(total==44 and read(private/'linux-java-tests-3/RESULT.json')['exit_code']==0 and '44 tests successful'in(private/'linux-java-tests-3/stdout').read_text(),'Changed actual Java platform tests')
        context=private/'predecessor-context-1';require(pin(context/'FILES.json')==s['predecessor_context_manifest'],'Changed retained predecessor context');context_files=read(context/'FILES.json');require(len(context_files)==83,'Incomplete old source context');closure(context/'source',context_files);require(read(context/'COMPLETED.json')['status']=='PASS_PREDECESSOR_WORKFLOW_IN_ORIGINAL_SOURCE_CONTEXT','Predecessor was not checked in its exact retained context')
        for command,capture in captures:
            p=subprocess.run([java,'-Xmx4g','-jar',str(jar),command,'--inspect',str(capture)],stdout=subprocess.PIPE,stderr=subprocess.PIPE);require(p.returncode==0,'Saved inspection failed: '+capture.name+'; '+p.stderr.decode(errors='replace')[:500]);inspected+=1
        require(inspected==35,'Incomplete saved inspection population')
    return dict(status='PASS_SCOPED_GENOMICSEM_PREPARATION_CHECKPOINT',public_source_files=len(public),private_evidence_files=private_count,saved_captures_inspected=inspected,primary_source_audit=primary,original_object_comparisons=24,admitted_original_object_comparisons=22,policy_rejected_original_object_comparisons=2,actual_refusal_controls=20,fresh_original_executions=3,Java_tests_per_platform=44,catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False,no_upstream_computation_performed=True)

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--cache',type=Path);p.add_argument('--private',type=Path);p.add_argument('--jar',type=Path);p.add_argument('--java',default='java');a=p.parse_args(argv)
    try:print(json.dumps(verify(a.root,a.cache,a.private,a.jar,a.java),indent=2));return 0
    except(OSError,ValueError,KeyError,TypeError)as e:print('FAIL2: preparation checkpoint did not verify: '+str(e)+'. Preserve the attempt and restore its exact source, producer and evidence. This reader grants no catalog or whole-paper credit.',file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
