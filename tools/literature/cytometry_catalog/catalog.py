"""Plan, inspect, atomically admit and verify 44 cytometry source roles."""
from pathlib import Path
import argparse,json,os,subprocess,sys,traceback
from cytometry_catalog import primary as p,model as m
from verify_cytometry_intensity_checkpoint import verify as workflow_verify
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from qtltools_catalog.catalog import zipped,read_before

ROOT=p.ROOT;BASE='68c260fb2aede0b0ad4ee29a841179380cb162f7'
FOLDER='docs/literature/cytometry-catalog-admission'
STATUS='PASS_ONE_NATIVE_CYTOMETRY_FAMILY_44_ROLES_41_NEW_REVIEWS'
OWN=['tools/literature/cytometry_catalog/'+name for name in ('__init__.py','primary.py','model.py','catalog.py','test_model.py','test_transaction.py','check_ui.cjs','README.md')]
OWN+=['tools/literature/close_cytometry_catalog.py',p.FOLDER+'/ROLES.json',p.FOLDER+'/CENSUS.json',p.FOLDER+'/.gitattributes',m.GUIDE]
FOUNDATION=['tools/literature/impute_catalog/model.py','tools/literature/common_statistics/prism_catalog.py','tools/literature/snpeff/model.py','tools/literature/seurat_catalog/catalog.py','tools/literature/qtltools_catalog/catalog.py','tools/literature/verify_cytometry_intensity_checkpoint.py','tools/literature/verify_impute_catalog_checkpoint.py','tools/literature/collect_omics_review.py']

def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT,stdin=subprocess.DEVNULL)
def sources():return {name:p.pin((ROOT/name).read_bytes()) for name in OWN+FOUNDATION}

def baseline(before):
    p.require(set(before)==set(m.META),'Changed predecessor file population')
    for name,raw in before.items():p.require(raw==git('show',BASE+':'+name),'Changed exact predecessor: '+name)

def qualified(args,before):
    for key in ('jar','predecessor_root','predecessor_jar','cache'):
        path=getattr(args,key)
        if path is None or not path.exists():raise FileNotFoundError('Restore the exact permitted prerequisite: '+str(path))
    for key in ('private_root','predecessor_private_root'):
        path=getattr(args,key)
        if path is not None and not path.is_dir():raise FileNotFoundError('Restore permitted private evidence directory: '+str(path))
    current=workflow_verify(ROOT,args.jar,args.private_root)
    p.require(current['packaged_samples_per_host']==23 and current['exact_JAR_checked'] and not current['vendor_statistics_qualified'],'Incomplete current native workflow qualification')
    archived=args.predecessor_root.resolve()
    for name,raw in before.items():p.require((archived/name).read_bytes()==raw,'Archived predecessor metadata differs')
    p.require((archived/m.DATA/'SHA256SUMS').read_bytes()==git('show',BASE+':'+m.DATA+'/SHA256SUMS'),'Archived predecessor manifest differs')
    reader=archived/'tools/literature/verify_impute_catalog_checkpoint.py'
    p.require(reader.read_bytes()==git('show',BASE+':tools/literature/verify_impute_catalog_checkpoint.py'),'Historical reader changed')
    env=dict(os.environ,GIT_DIR=git('rev-parse','--absolute-git-dir').decode().strip(),GIT_WORK_TREE=str(archived),PYTHONDONTWRITEBYTECODE='1')
    command=[sys.executable,'-B',str(reader),'--root',str(archived),'--jar',str(args.predecessor_jar),'--cache',str(args.cache)]
    if args.predecessor_private_root is not None:command+=['--private-root',str(args.predecessor_private_root)]
    result=subprocess.run(command,cwd=archived,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    (args.out/'predecessor.stdout').write_bytes(result.stdout);(args.out/'predecessor.stderr').write_bytes(result.stderr)
    p.require(result.returncode==0,'Unchanged historical catalog reader failed; inspect retained diagnostics, never refresh saved hashes')
    roles,fresh=p.audit(args.cache,before[m.META[0]])
    return m.qualification(),dict(current_native_workflow=current,historical_catalog_reader=json.loads(result.stdout),fresh_primary=fresh,physical_predecessor_source=str(archived),historical_source_or_hashes_refreshed=False,original_scientific_analysis_reexecuted=False),roles


def coverage(q,result):return m.encoded(dict(status=STATUS,counts=result,roles=[m.applied(r) for r in p.frozen()['roles']],workflow_qualification=p.pin(m.encoded(q)),whole_goal_complete=False))
def preflight():p.require(not (ROOT/FOLDER).exists() and not (ROOT/m.PROOF).exists() and not (ROOT/m.COVERAGE).exists(),'Already admitted or partial admission; preserve and inspect evidence')

def plan(args):
    preflight();before={n:(ROOT/n).read_bytes() for n in m.META};baseline(before);p.require(clean_manifest()['entries']==867,'Changed complete predecessor manifest')
    q,measured,roles=qualified(args,before);after=m.transition(before,roles,q);result=m.check(before,after,roles,q);preview={**after,m.COVERAGE:coverage(q,result)}
    (args.out/'metadata-before.zip').write_bytes(zipped(before));(args.out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes());(args.out/'QUALIFICATION.json').write_bytes(m.encoded(q));(args.out/'REGISTRY.json').write_bytes(m.encoded(m.registry()))
    for name,raw in preview.items():path=args.out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_'+STATUS,base=BASE,source_code=sources(),before={n:p.pin(raw) for n,raw in before.items()},preview={n:p.pin(raw) for n,raw in preview.items()},result=result,actual=measured,qualification=p.pin(m.encoded(q)),registry=p.pin((args.out/'REGISTRY.json').read_bytes()),canonical_modified=False)
    (args.out/'PLAN.json').write_bytes(m.encoded(record));return record

def inspect(args):
    preflight();folder=args.plan_dir.resolve();p.require(folder.is_relative_to(ROOT/'build'),'Use a current-owner build plan')
    record=json.loads((folder/'PLAN.json').read_bytes());before=read_before(folder/'metadata-before.zip');baseline(before)
    p.require(record['status']=='PLANNED_'+STATUS and record['base']==BASE and record['source_code']==sources(),'Changed plan or admission source')
    p.require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()) and record['before']=={n:p.pin(raw) for n,raw in before.items()},'Concurrent predecessor edit')
    p.require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==867,'Changed predecessor manifest')
    p.require((folder/'QUALIFICATION.json').read_bytes()==m.encoded(m.qualification()),'Changed saved workflow qualification')
    q,measured,roles=qualified(args,before)
    p.require(record['qualification']==p.pin(m.encoded(q)) and (folder/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and record['registry']==p.pin((folder/'REGISTRY.json').read_bytes()),'Changed qualification or registry')
    after=m.transition(before,roles,q);result=m.check(before,after,roles,q);expected={**after,m.COVERAGE:coverage(q,result)}
    p.require(record['result']==result and record['preview']=={n:p.pin(raw) for n,raw in expected.items()},'Changed deterministic preview')
    for name,raw in expected.items():p.require((folder/'preview'/name).read_bytes()==raw,'Changed preview: '+name)
    return record,before,q,expected,measured

def apply(args):
    record,before,q,preview,measured=inspect(args);result=m.check(before,{n:preview[n] for n in m.META},p.frozen(),q)
    evidence={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PLAN.json':m.encoded(record),'REGISTRY.json':(args.plan_dir/'REGISTRY.json').read_bytes(),'QUALIFICATION.json':m.encoded(q),'README.md':('# Cytometry catalog admission\n\n'+m.CURRENT+'\n\n'+m.SCOPE+'\n\n'+m.NOTE+'\n\n**Warnings and required actions:** '+m.ACTION+'\n').encode()}
    updates={FOLDER+'/'+n:raw for n,raw in evidence.items()};updates.update(preview)
    proof=dict(schema_version=1,status=STATUS,base_commit=BASE,source_code=sources(),before={n:p.pin(raw) for n,raw in before.items()},after={n:p.pin(preview[n]) for n in m.META},qualification=p.pin(m.encoded(q)),result=result,evidence={n:p.pin(raw) for n,raw in updates.items() if n.startswith(FOLDER+'/') or n==m.COVERAGE},whole_goal_complete=False)
    updates[m.PROOF]=m.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={n:None for n in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for name in updates:(ROOT/name).parent.mkdir(parents=True,exist_ok=True)
    def post():
        p.require(m.check(before,{n:(ROOT/n).read_bytes() for n in m.META},p.frozen(),q)==result,'Installed catalog differs');current=clean_manifest();p.require(current['entries']==869,'Wrong complete after manifest');return current
    current=install(ROOT,args.out,expected,updates,post);(args.out/'COMPLETED.json').write_bytes(m.encoded(dict(status=STATUS,result=result,current_manifest=current,actual=measured,canonical_modified=True)));return result

def verify(args):
    proof=json.loads((ROOT/m.PROOF).read_bytes());p.require(proof['status']==STATUS and proof['base_commit']==BASE and proof['source_code']==sources(),'Changed admitted source/scope')
    for name,wanted in proof['evidence'].items():p.require(p.pin((ROOT/name).read_bytes())==wanted,'Changed admission evidence: '+name)
    before=read_before(ROOT/FOLDER/'metadata-before.zip');baseline(before);p.require(proof['before']=={n:p.pin(raw) for n,raw in before.items()} and (ROOT/FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',BASE+':'+m.DATA+'/SHA256SUMS'),'Changed predecessor')
    q,measured,roles=qualified(args,before);p.require(proof['qualification']==p.pin(m.encoded(q)) and (ROOT/FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()),'Changed qualified registry')
    after={n:(ROOT/n).read_bytes() for n in m.META};result=m.check(before,after,roles,q);p.require(proof['after']=={n:p.pin(raw) for n,raw in after.items()} and proof['result']==result,'Changed catalog')
    current=clean_manifest();p.require(current['entries']==869,'Changed complete catalog manifest');record=dict(status=STATUS,result=result,current_manifest=current,actual=measured,original_scientific_analysis_reexecuted=False,whole_goal_complete=False);(args.out/'COMPLETED.json').write_bytes(m.encoded(record));return record

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ('plan','inspect','apply','verify'):mode.add_argument('--'+name,action='store_true')
    for name in ('out','cache','jar','private-root','predecessor-root','predecessor-jar','predecessor-private-root','plan-dir'):parser.add_argument('--'+name,type=Path,required=name=='out')
    args=parser.parse_args();owned=False
    try:
        p.require(__debug__,'Run without -O');p.require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW current-owner build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        p.require(all(getattr(args,name) is not None for name in ('cache','jar','predecessor_root','predecessor_jar')),'Supply exact source/cache/JAR/private prerequisites')
        if args.apply or args.inspect:p.require(args.plan_dir is not None,'Supply the saved --plan-dir')
        result=plan(args) if args.plan else inspect(args)[0] if args.inspect else apply(args) if args.apply else verify(args)
        if args.inspect:(args.out/'COMPLETED.json').write_bytes(m.encoded(dict(status='PASS_INSPECTED_'+STATUS,result=result['result'])))
        print(json.dumps(dict(status='PASS_CYTOMETRY_CATALOG_'+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'))));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(m.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=m.ACTION)))
        print(('SKIP3: ' if code==3 else 'FAIL2: ')+repr(error)+'. '+m.ACTION,file=sys.stderr);return code
