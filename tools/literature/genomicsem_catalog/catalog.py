"""Plan, inspect, atomically admit and verify five GenomicSEM source roles."""
from pathlib import Path
import argparse,json,os,subprocess,sys,traceback
from genomicsem_catalog import primary as p,model as m
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from qtltools_catalog.catalog import zipped,read_before

ROOT=p.ROOT;BASE='9bf1aeb93ca5196dd1de321c640d02b5387d511f'
FOLDER='docs/literature/genomicsem-catalog-admission'
STATUS='PASS_GENOMICSEM_UPSTREAM_FAMILY_FIVE_SOURCE_ROLES'
BEFORE_ENTRIES=871;AFTER_ENTRIES=873
OWN=['tools/literature/genomicsem_catalog/'+name for name in('__init__.py','primary.py','model.py','catalog.py','test_model.py','test_transaction.py','check_ui.cjs','README.md')]
OWN+=['tools/literature/close_genomicsem_catalog.py',p.FOLDER+'/ROLES.json',p.FOLDER+'/CENSUS.json',m.GUIDE]
FOUNDATION=['tools/literature/fastqtl_catalog/model.py','tools/literature/fastqtl_catalog/primary.py','tools/literature/cytometry_catalog/model.py','tools/literature/cytometry_catalog/primary.py','tools/literature/impute_catalog/model.py','tools/literature/common_statistics/prism_catalog.py','tools/literature/snpeff/model.py','tools/literature/seurat_catalog/catalog.py','tools/literature/qtltools_catalog/catalog.py','tools/literature/verify_genomicsem_preparation_checkpoint.py','tools/literature/genomicsem/primary.py','tools/literature/genomicsem/runtime.py','tools/literature/collect_omics_review.py','docs/GENOMICSEM_WORKFLOW.md','docs/GENOMICSEM_PREPARATION.md','README.md']
CHECKPOINT_FILES=['docs/literature/genomicsem-preparation-checkpoint/'+n for n in('STATUS.json','VALIDATION.json','COMPLETION.json')]+['docs/literature/genomicsem-workflow-checkpoint/'+n for n in('STATUS.json','VALIDATION.json')]
FOUNDATION+=CHECKPOINT_FILES

def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT,stdin=subprocess.DEVNULL)
def sources():return{name:p.pin((ROOT/name).read_bytes())for name in OWN+FOUNDATION}

def baseline(before):
    p.require(set(before)==set(m.META),'Changed predecessor file population')
    for name,raw in before.items():p.require(raw==git('show',BASE+':'+name),'Changed exact predecessor: '+name)

def qualified(args,before):
    for key in('jar','workflow_source_root','private_root','cache'):
        value=getattr(args,key)
        if value is None or not value.exists():raise FileNotFoundError('Restore exact permitted prerequisite: '+str(value))
    p.required_primary(args.cache,before[m.META[0]])
    archived=args.workflow_source_root.absolute();p.require(archived.resolve()==archived,'Use a physical original preparation source context')
    context=archived.parent;members=json.loads((context/'FILES.json').read_bytes());folder='docs/literature/genomicsem-preparation-checkpoint';expected=set(json.loads(git('show',BASE+':'+folder+'/FILES.json')));expected.update(folder+'/'+n for n in('FILES.json','STATUS.json','COMPLETION.json'));expected.update(json.loads(git('show',BASE+':'+folder+'/CANONICAL.json')));expected.update(git('ls-tree','-r','--name-only',BASE,'tools/literature/genomicsem').decode().splitlines());p.require(set(members)==expected and len(members)==109,'Incomplete/changed pre-admission source population')
    for name,identity in members.items():
        path=Path(name);p.require(not path.is_absolute()and '..'not in path.parts and '\\'not in name and ':'not in name,'Unsafe historical source member');raw=(archived/name).read_bytes();p.require(p.pin(raw)==identity and raw==git('show',BASE+':'+name),'Changed historical source: '+name)
    for name,raw in before.items():p.require((archived/name).read_bytes()==raw,'Historical canonical bytes differ')
    for name in CHECKPOINT_FILES:p.require((ROOT/name).read_bytes()==git('show',BASE+':'+name),'Changed original workflow qualification: '+name)
    state=json.loads((archived/m.CHECKPOINT).read_bytes());p.require(state['producer']==p.pin(args.jar.read_bytes()),'Restore the exact whole producer JAR; never refresh its old hash')
    receipt=json.loads((context/'COMPLETED.json').read_bytes());r=receipt['readback'];p.require(receipt['status']=='PASS_PREPARATION_IN_EXACT_PRE_ADMISSION_SOURCE_CONTEXT'and receipt['base_commit']==BASE and receipt['source_files']==109 and not receipt['old_source_or_producer_hashes_refreshed'],'Missing actual immutable-reader receipt')
    p.require(r['status']=='PASS_SCOPED_GENOMICSEM_PREPARATION_CHECKPOINT'and(r['public_source_files'],r['private_evidence_files'],r['saved_captures_inspected'],r['original_object_comparisons'],r['actual_refusal_controls'])==(93,5875,35,24,20)and r['primary_source_audit']['primary_XML_checked']==250,'Incomplete prior actual saved-reader execution')
    # The full saved reader ran against this exact physical context before any
    # catalog changes. Every mode rechecks all its source/private bytes. This
    # avoids relaunching 35 identical Java inspections for an unchanged capsule.
    import verify_genomicsem_preparation_checkpoint as old
    p.require((ROOT/'tools/literature/verify_genomicsem_preparation_checkpoint.py').read_bytes()==(archived/'tools/literature/verify_genomicsem_preparation_checkpoint.py').read_bytes(),'Historical reader changed')
    public=old.verify(archived);manifest=args.private_root/'PRIVATE-CLOSURE.json';p.require(p.pin(manifest.read_bytes())==state['private_evidence_manifest'],'Changed private evidence manifest');private=json.loads(manifest.read_bytes());p.require(len(private)==5875,'Incomplete historical evidence');old.closure(args.private_root.absolute(),private)
    roles,fresh=p.audit(args.cache,before[m.META[0]],archived)
    measured=dict(immutable_pre_admission_source_files=109,immutable_public_files_checked=public['public_source_files'],private_evidence_files_rehashed=len(private),prior_actual_saved_captures_verified=35,prior_full_saved_reader_receipt_verified=True,identical_saved_reader_relaunched=False,fresh_primary=fresh,historical_source_or_hashes_refreshed=False,original_scientific_analysis_reexecuted=False)
    (args.out/'PREREQUISITE-READBACK.json').write_bytes(p.encoded(measured));return m.qualification(),measured,roles

def coverage(q,result):return p.encoded(dict(status=STATUS,counts=result,roles=[m.applied(r)for r in p.frozen()['roles']],workflow_qualification=p.pin(p.encoded(q)),whole_goal_complete=False))
def preflight():p.require(not(ROOT/FOLDER).exists()and not(ROOT/m.PROOF).exists()and not(ROOT/m.COVERAGE).exists(),'Existing/partial admission preserved; inspect before retrying')

def plan(args):
    preflight();source_before=sources();before={n:(ROOT/n).read_bytes()for n in m.META};baseline(before);manifest_before=(ROOT/m.DATA/'SHA256SUMS').read_bytes();p.require(clean_manifest()['entries']==BEFORE_ENTRIES,'Changed complete predecessor manifest');q,actual,roles=qualified(args,before);after=m.transition(before,roles,q);result=m.check(before,after,roles,q);preview={**after,m.COVERAGE:coverage(q,result)};p.require(sources()==source_before,'Admission source changed during qualification');p.require(all((ROOT/n).read_bytes()==raw for n,raw in before.items())and(ROOT/m.DATA/'SHA256SUMS').read_bytes()==manifest_before,'Concurrent canonical change during planning')
    (args.out/'metadata-before.zip').write_bytes(zipped(before));(args.out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes());(args.out/'QUALIFICATION.json').write_bytes(p.encoded(q));(args.out/'REGISTRY.json').write_bytes(p.encoded(m.registry()))
    for name,raw in preview.items():path=args.out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_'+STATUS,base=BASE,source_code=source_before,before={n:p.pin(raw)for n,raw in before.items()},preview={n:p.pin(raw)for n,raw in preview.items()},result=result,actual=actual,qualification=p.pin(p.encoded(q)),registry=p.pin((args.out/'REGISTRY.json').read_bytes()),canonical_modified=False)
    (args.out/'PLAN.json').write_bytes(p.encoded(record));return record

def inspect(args):
    preflight();folder=args.plan_dir.resolve();p.require(folder.is_relative_to(ROOT/'build'),'Use an owned build plan');record=json.loads((folder/'PLAN.json').read_bytes());before=read_before(folder/'metadata-before.zip');baseline(before)
    p.require(record['status']=='PLANNED_'+STATUS and record['base']==BASE and record['source_code']==sources(),'Changed plan or admission source');p.require(all((ROOT/n).read_bytes()==raw for n,raw in before.items())and record['before']=={n:p.pin(raw)for n,raw in before.items()},'Concurrent predecessor edit');p.require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes()and clean_manifest()['entries']==BEFORE_ENTRIES,'Changed predecessor manifest')
    p.require((folder/'QUALIFICATION.json').read_bytes()==p.encoded(m.qualification()),'Changed qualification');q,actual,roles=qualified(args,before);p.require(record['qualification']==p.pin(p.encoded(q))and(folder/'REGISTRY.json').read_bytes()==p.encoded(m.registry())and record['registry']==p.pin((folder/'REGISTRY.json').read_bytes()),'Changed workflow qualification/registry')
    after=m.transition(before,roles,q);result=m.check(before,after,roles,q);expected={**after,m.COVERAGE:coverage(q,result)};p.require(record['result']==result and record['preview']=={n:p.pin(raw)for n,raw in expected.items()},'Changed deterministic preview')
    for name,raw in expected.items():p.require((folder/'preview'/name).read_bytes()==raw,'Changed preview: '+name)
    p.require(record['source_code']==sources(),'Admission source changed during inspection');p.require(all((ROOT/n).read_bytes()==raw for n,raw in before.items())and(ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes(),'Concurrent canonical change during inspection');return record,before,q,expected,actual

def apply(args):
    record,before,q,preview,actual=inspect(args);result=m.check(before,{n:preview[n]for n in m.META},p.frozen(),q)
    evidence={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PLAN.json':p.encoded(record),'REGISTRY.json':(args.plan_dir/'REGISTRY.json').read_bytes(),'QUALIFICATION.json':p.encoded(q),'README.md':('# GenomicSEM catalog admission\n\n'+m.CURRENT+'\n\n'+m.SCOPE+'\n\n'+m.NOTE+'\n\n**Warnings and required actions:** '+m.ACTION+'\n').encode()}
    p.require(sources()==record['source_code'],'Admission source changed before installation');updates={FOLDER+'/'+n:raw for n,raw in evidence.items()};updates.update(preview);proof=dict(schema_version=1,status=STATUS,base_commit=BASE,source_code=record['source_code'],before={n:p.pin(raw)for n,raw in before.items()},after={n:p.pin(preview[n])for n in m.META},qualification=p.pin(p.encoded(q)),result=result,evidence={n:p.pin(raw)for n,raw in updates.items()if n.startswith(FOLDER+'/')or n==m.COVERAGE},whole_goal_complete=False)
    updates[m.PROOF]=p.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates);expected={n:None for n in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for name in updates:(ROOT/name).parent.mkdir(parents=True,exist_ok=True)
    def post():
        p.require(sources()==record['source_code'],'Admission source changed during installation');p.require(m.check(before,{n:(ROOT/n).read_bytes()for n in m.META},p.frozen(),q)==result,'Installed catalog differs');current=clean_manifest();p.require(current['entries']==AFTER_ENTRIES,'Wrong complete after manifest');return current
    current=install(ROOT,args.out,expected,updates,post);(args.out/'COMPLETED.json').write_bytes(p.encoded(dict(status=STATUS,result=result,current_manifest=current,actual=actual,canonical_modified=True)));return result

def verify(args):
    proof=json.loads((ROOT/m.PROOF).read_bytes());p.require(proof['status']==STATUS and proof['base_commit']==BASE and proof['source_code']==sources(),'Changed admitted source/scope')
    for name,wanted in proof['evidence'].items():p.require(p.pin((ROOT/name).read_bytes())==wanted,'Changed admission evidence: '+name)
    before=read_before(ROOT/FOLDER/'metadata-before.zip');baseline(before);p.require(proof['before']=={n:p.pin(raw)for n,raw in before.items()}and(ROOT/FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',BASE+':'+m.DATA+'/SHA256SUMS'),'Changed exact predecessor')
    q,actual,roles=qualified(args,before);p.require(proof['qualification']==p.pin(p.encoded(q))and(ROOT/FOLDER/'REGISTRY.json').read_bytes()==p.encoded(m.registry()),'Changed qualified registry');after={n:(ROOT/n).read_bytes()for n in m.META};result=m.check(before,after,roles,q);p.require(proof['after']=={n:p.pin(raw)for n,raw in after.items()}and proof['result']==result,'Changed installed catalog')
    current=clean_manifest();p.require(current['entries']==AFTER_ENTRIES and proof['source_code']==sources(),'Changed complete manifest/source during verification');record=dict(status=STATUS,result=result,current_manifest=current,actual=actual,original_scientific_analysis_reexecuted=False,whole_goal_complete=False);(args.out/'COMPLETED.json').write_bytes(p.encoded(record));return record

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in('plan','inspect','apply','verify'):mode.add_argument('--'+name,action='store_true')
    for name in('out','cache','jar','private-root','workflow-source-root','plan-dir'):parser.add_argument('--'+name,type=Path,required=name=='out')
    args=parser.parse_args();owned=False
    try:
        p.require(__debug__,'Run without -O');p.require(args.out.resolve().is_relative_to(ROOT/'build')and args.out.resolve()!=ROOT/'build','Use NEW owned build output');args.out.mkdir(parents=True,exist_ok=False);owned=True;p.require(all(getattr(args,n)is not None for n in('cache','jar','private_root','workflow_source_root')),'Supply exact source/cache/private/JAR prerequisites')
        if args.apply or args.inspect:p.require(args.plan_dir is not None,'Supply saved --plan-dir')
        result=plan(args)if args.plan else inspect(args)[0]if args.inspect else apply(args)if args.apply else verify(args)
        if args.inspect:(args.out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_INSPECTED_'+STATUS,result=result['result'])))
        print(json.dumps(dict(status='PASS_GENOMICSEM_CATALOG_'+('plan'if args.plan else'inspect'if args.inspect else'apply'if args.apply else'verify'))));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3'if code==3 else'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=m.ACTION)))
        print(('SKIP3: 'if code==3 else'FAIL2: ')+repr(error)+'. '+m.ACTION,file=sys.stderr);return code
