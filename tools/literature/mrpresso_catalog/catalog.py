"""Plan, inspect, transactionally admit and verify six source-bound MR-PRESSO uses."""
from pathlib import Path
import argparse,json,subprocess,sys,traceback
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mrpresso_catalog import model as m,primary as p
from verify_mrpresso_workflow_checkpoint import verify as workflow_verify
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from qtltools_catalog.catalog import zipped,read_before

ROOT=p.ROOT
BASE='6dda7a239967c68266c786de5678a156e3196658'
FOLDER='docs/literature/mrpresso-catalog-admission'
STATUS='PASS_ONE_MRPRESSO_NATIVE_FAMILY_SIX_SOURCE_ROLES_WITH_UNREPORTED_SETTINGS'
OWN=['tools/literature/mrpresso_catalog/'+n for n in (
    '__init__.py','primary.py','model.py','catalog.py','compatibility.py','test_model.py','test_transaction.py','check_ui.cjs','README.md','ADMISSION.md')]
OWN+=['tools/literature/audit_mrpresso_sources.py','tools/literature/close_mrpresso_catalog.py',
      'docs/literature/mrpresso-catalog-sources/ROLES.json','docs/literature/mrpresso-catalog-sources/STATUS.json',m.GUIDE]
FOUNDATION=['tools/literature/seurat_catalog/catalog.py','tools/literature/qtltools_catalog/catalog.py',
            'tools/literature/squidpy_catalog/model.py','tools/literature/collect_omics_review.py',
            'tools/literature/verify_mrpresso_workflow_checkpoint.py','docs/literature/mrpresso-development-handover-20261123/EVIDENCE.json']


def git(*args):
    return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT,stdin=subprocess.DEVNULL)


def sources():return {n:p.pin((ROOT/n).read_bytes()) for n in OWN+FOUNDATION}


def baseline(before):
    p.require(set(before)==set(m.META),'Different predecessor metadata population')
    for name,raw in before.items():p.require(raw==git('show',BASE+':'+name),'Changed exact predecessor: '+name)


def qualified(args,before):
    measured=workflow_verify(ROOT,args.jar)
    q=json.loads((ROOT/'docs/literature/mrpresso-workflow-qualification/STATUS.json').read_bytes())
    fresh=False
    if args.cache is not None:
        current=p.audit(args.cache,before[m.META[0]])
        p.require(current==m.frozen_primary(),'Fresh primary census differs from frozen source roles');fresh=True
    from mrpresso_catalog.compatibility import verify as compatibility_verify
    bridge=compatibility_verify()
    return q,dict(workflow=measured,predecessor_dispatch_bridge=bridge,fresh_primary_XML_checked=250 if fresh else 0,original_analysis_reexecuted=False)


def coverage(q,result):
    return m.encoded(dict(status=STATUS,counts=result,roles=m.frozen_primary()['roles'],qualification=p.pin(m.encoded(q)),
        new_reviews=6,existing_scope_notes_updated=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))


def preflight():
    p.require(not (ROOT/FOLDER).exists() and not (ROOT/m.PROOF).exists() and not (ROOT/m.COVERAGE).exists(),
              'Preserve existing admission; do not reapply')


def plan(args):
    preflight();before={n:(ROOT/n).read_bytes() for n in m.META};baseline(before)
    p.require(clean_manifest()['entries']==857,'Changed complete predecessor manifest')
    q,measured=qualified(args,before);primary=m.frozen_primary();after=m.transition(before,primary,q)
    result=m.check(before,after,primary,q);preview={**after,m.COVERAGE:coverage(q,result)}
    (args.out/'metadata-before.zip').write_bytes(zipped(before))
    (args.out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes())
    (args.out/'QUALIFICATION.json').write_bytes(m.encoded(q));(args.out/'REGISTRY.json').write_bytes(m.encoded(m.registry()))
    for name,raw in preview.items():
        path=args.out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_'+STATUS,base=BASE,source_code=sources(),before={n:p.pin(raw) for n,raw in before.items()},
        preview={n:p.pin(raw) for n,raw in preview.items()},result=result,actual=measured,qualification=p.pin(m.encoded(q)),
        registry=p.pin((args.out/'REGISTRY.json').read_bytes()),canonical_modified=False)
    (args.out/'PLAN.json').write_bytes(m.encoded(record));return record


def inspect(args):
    preflight();folder=args.plan_dir.resolve();p.require(folder.is_relative_to(ROOT/'build'),'Use saved current-owner plan')
    record=json.loads((folder/'PLAN.json').read_bytes());before=read_before(folder/'metadata-before.zip');baseline(before)
    p.require(record['status']=='PLANNED_'+STATUS and record['base']==BASE and record['source_code']==sources(),'Changed plan or admission code')
    p.require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()) and record['before']=={n:p.pin(raw) for n,raw in before.items()},'Concurrent predecessor change')
    p.require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==857,'Changed complete predecessor manifest')
    q,measured=qualified(args,before)
    p.require((folder/'QUALIFICATION.json').read_bytes()==m.encoded(q) and record['qualification']==p.pin(m.encoded(q)),'Changed qualification')
    p.require((folder/'REGISTRY.json').read_bytes()==m.encoded(m.registry()) and record['registry']==p.pin((folder/'REGISTRY.json').read_bytes()),'Changed planned registry')
    primary=m.frozen_primary();after=m.transition(before,primary,q);result=m.check(before,after,primary,q)
    expected={**after,m.COVERAGE:coverage(q,result)}
    p.require(record['result']==result and record['preview']=={n:p.pin(raw) for n,raw in expected.items()},'Changed deterministic preview')
    for name,raw in expected.items():p.require((folder/'preview'/name).read_bytes()==raw,'Changed preview bytes: '+name)
    return record,before,q,expected,measured


def apply(args):
    record,before,q,preview,measured=inspect(args)
    result=m.check(before,{n:preview[n] for n in m.META},m.frozen_primary(),q)
    evidence={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),
        'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PLAN.json':m.encoded(record),
        'REGISTRY.json':(args.plan_dir/'REGISTRY.json').read_bytes(),'QUALIFICATION.json':m.encoded(q),
        'README.md':('# MR-PRESSO catalog admission\n\n'+m.CURRENT+'\n\n'+m.SCOPE+'\n\n'+m.NOTE+'\n\n'+m.ACTION+'\n').encode()}
    updates={FOLDER+'/'+n:raw for n,raw in evidence.items()};updates.update(preview)
    proof=dict(schema_version=1,status=STATUS,base_commit=BASE,source_code=sources(),before={n:p.pin(raw) for n,raw in before.items()},
        after={n:p.pin(preview[n]) for n in m.META},qualification=p.pin(m.encoded(q)),result=result,
        qualified_packet_status=p.pin((ROOT/'docs/literature/mrpresso-workflow-qualification/STATUS.json').read_bytes()),
        evidence={n:p.pin(raw) for n,raw in updates.items() if n.startswith(FOLDER+'/') or n==m.COVERAGE},whole_goal_complete=False)
    updates[m.PROOF]=m.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={n:None for n in updates};expected.update(before)
    expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for name in updates:(ROOT/name).parent.mkdir(parents=True,exist_ok=True)
    def post():
        p.require(m.check(before,{n:(ROOT/n).read_bytes() for n in m.META},m.frozen_primary(),q)==result,'Installed metadata differs')
        current=clean_manifest();p.require(current['entries']==859,'Incorrect complete after manifest');return current
    current=install(ROOT,args.out,expected,updates,post)
    (args.out/'COMPLETED.json').write_bytes(m.encoded(dict(status=STATUS,result=result,current_manifest=current,actual=measured,
        canonical_modified=True,scientific_source_changed=False)));return result


def verify(args):
    proof=json.loads((ROOT/m.PROOF).read_bytes())
    p.require(proof['status']==STATUS and proof['base_commit']==BASE and proof['source_code']==sources(),'Changed admitted scope/code')
    for name,wanted in proof['evidence'].items():p.require(p.pin((ROOT/name).read_bytes())==wanted,'Changed admission evidence: '+name)
    before=read_before(ROOT/FOLDER/'metadata-before.zip');baseline(before)
    p.require(proof['before']=={n:p.pin(raw) for n,raw in before.items()},'Changed retained predecessor')
    p.require((ROOT/FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',BASE+':'+m.DATA+'/SHA256SUMS'),'Changed predecessor manifest')
    q,measured=qualified(args,before)
    p.require(proof['qualification']==p.pin(m.encoded(q)) and proof['qualified_packet_status']==p.pin((ROOT/'docs/literature/mrpresso-workflow-qualification/STATUS.json').read_bytes()),'Changed qualified packet')
    p.require((ROOT/FOLDER/'REGISTRY.json').read_bytes()==m.encoded(m.registry()),'Changed admitted registry')
    after={n:(ROOT/n).read_bytes() for n in m.META};result=m.check(before,after,m.frozen_primary(),q)
    p.require(proof['after']=={n:p.pin(raw) for n,raw in after.items()} and proof['result']==result,'Changed installed catalog')
    current=clean_manifest();p.require(current['entries']==859,'Changed complete859-entry manifest')
    record=dict(status=STATUS,result=result,current_manifest=current,actual=measured,original_analysis_reexecuted=False,whole_goal_complete=False)
    (args.out/'COMPLETED.json').write_bytes(m.encoded(record));return record


def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ('plan','inspect','apply','verify'):mode.add_argument('--'+name,action='store_true')
    for name in ('out','cache','jar','plan-dir'):parser.add_argument('--'+name,type=Path,required=name=='out')
    args=parser.parse_args();owned=False
    try:
        p.require(__debug__,'Run Python without -O')
        p.require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW current-owner build output')
        args.out.mkdir(parents=True,exist_ok=False);owned=True
        if not args.verify:p.require(args.cache is not None and args.jar is not None,'Plan/inspect/apply require actual primary cache and exact producer')
        if args.apply or args.inspect:p.require(args.plan_dir is not None,'Supply saved --plan-dir')
        result=plan(args) if args.plan else inspect(args)[0] if args.inspect else apply(args) if args.apply else verify(args)
        if args.inspect:(args.out/'COMPLETED.json').write_bytes(m.encoded(dict(status='PASS_INSPECTED_'+STATUS,result=result['result'])))
        print(json.dumps(dict(status='PASS_MRPRESSO_CATALOG_'+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'))));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(m.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=m.ACTION)))
        print(('SKIP3: ' if code==3 else 'FAIL2: ')+str(error),file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
