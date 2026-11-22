"""Plan,inspect,transactionally admit and verify one original SKAT provider."""
from pathlib import Path
import argparse,json,subprocess,sys,traceback,zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from skat225_catalog import model as m
from acat_skat_catalog import primary as p
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from qtltools_catalog.catalog import zipped,read_before

ROOT=m.ROOT;BASE='abee077e478906a1a2d3f15005806f9767c2c880'
PACKET='docs/literature/skat225-qualification';FOLDER='docs/literature/skat225-catalog-admission'
STATUS='PASS_ONE_ORIGINAL_SKAT225_UPSTREAM_FAMILY_AND_SKATO_ALIAS'
OWN=['tools/literature/skat225_catalog/'+n for n in ['__init__.py','model.py','catalog.py','test_model.py','check_ui.cjs','ADMISSION.md','.gitattributes']]+['tools/literature/skat_runtime_qualification/SkatCaptureControl.java','tools/literature/skat_runtime_qualification/verify_checkpoint.py','tools/literature/skat_runtime_qualification/.gitattributes',m.GUIDE]

def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT,stdin=subprocess.DEVNULL)
def sources():return {n:p.pin((ROOT/n).read_bytes()) for n in OWN}
def baseline(before):
    p.require(set(before)==set(m.META),'Different predecessor population')
    for name,raw in before.items():p.require(raw==git('show',BASE+':'+name),'Changed exact predecessor metadata: '+name)
def packet():
    folder=ROOT/PACKET;q=json.loads((folder/'QUALIFICATION.json').read_bytes());stamp=json.loads((folder/'STATUS.json').read_bytes());info=json.loads((folder/'EVIDENCE.json').read_bytes())
    for name,wanted in stamp['file_pins'].items():p.require(p.pin((ROOT/name).read_bytes())==wanted,'Changed qualified source/evidence: '+name)
    p.require(p.pin((folder/info['archive']).read_bytes())==info['archive_pin'],'Changed qualified archive')
    with zipfile.ZipFile(folder/info['archive']) as z:
        manifest=z.read('manifest.json');p.require(p.pin(manifest)==info['manifest_pin'],'Changed archive logical map');members=json.loads(manifest)['files']
        def read(name):
            entry=members[name];raw=z.read('objects/'+entry['sha256']);p.require(p.pin(raw)==entry,'Changed qualified receipt: '+name);return raw
        p.require(json.loads(read('QUALIFICATION.json'))==q,'Archive and public qualification differ')
        for name,wanted in {**q['inherited_receipts'],**q['current_receipts']}.items():p.require(p.pin(read(name))==wanted,'Changed complete qualification receipt: '+name)
    old=json.loads((ROOT/'docs/literature/skat-runtime-handover-20261122/STATUS.json').read_bytes())
    p.require((ROOT/'docs/literature/skat-runtime-handover-20261122/STATUS.json').read_bytes()==git('show',BASE+':docs/literature/skat-runtime-handover-20261122/STATUS.json'),'Preceding qualification differs from exact published Git bytes')
    p.require(p.pin((ROOT/'docs/literature/skat-runtime-handover-20261122/STATUS.json').read_bytes())==q['existing_frozen_checkpoint_pin'],'Changed preceding recorded qualification')
    p.require(old['latest_normal_gradle']['tests']==q['normal_tests'] and old['latest_normal_gradle']['development_jar']==q['producer'] and old['latest_actual_packaged_original_matrix']['cases']==q['matrix_cases'] and old['latest_actual_packaged_original_matrix']['status']=='PASS_ACTUAL_PACKAGED_MATRIX','Different preceding build/matrix evidence')
    for name,wanted in q['source_inputs'].items():
        raw=(ROOT/name).read_bytes();p.require(p.pin(raw)==wanted and raw==git('show',BASE+':'+name),'Qualified scientific source changed: '+name)
    p.require(q['base_main_with_identical_scientific_source']==BASE,'Changed scientific source owner')
    return q
def actual(checks,jar,q):
    checks=Path(checks).resolve();p.require(checks.is_relative_to(ROOT/'build'),'Use actual current-owner checks,not a substituted report')
    p.require(json.loads((checks/'QUALIFICATION.json').read_bytes())==q,'Actual qualification differs')
    for name,wanted in {**q['current_receipts'],**q['inherited_receipts']}.items():p.require(p.pin((checks/name).read_bytes())==wanted,'Changed actual control receipt: '+name)
    p.require(p.pin(Path(jar).read_bytes())==q['producer'],'Supply unchanged actual measured producer')
    files=json.loads((checks/'inherited/packaged-matrix-5/RUNTIME-BEFORE.json').read_bytes())
    for path,wanted in files.items():p.require(p.pin(Path(path).read_bytes())==wanted,'Original runtime changed after qualification: '+path)
    return dict(status='PASS_ACTUAL_PRODUCER_RECEIPTS_AND_RUNTIME',resolved_runtime_files=len(files),producer=q['producer'])
def coverage(q,result):
    return p.encoded(dict(schema_version=1,status=STATUS,qualification=p.pin(p.encoded(q)),existing_source_roles=[r for r in p.frozen()['roles'] if r['tool_id'] in m.TARGETS],counts=result,new_reviews=0,new_observations=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))
def preflight():p.require(not (ROOT/FOLDER).exists() and not (ROOT/m.PROOF).exists() and not (ROOT/m.COVERAGE).exists(),'Preserve existing admission;do not reapply or overwrite evidence')
def plan(args,out):
    preflight();before={n:(ROOT/n).read_bytes() for n in m.META};baseline(before);p.require(clean_manifest()['entries']==847,'Changed preceding full manifest');q=packet();measured=actual(args.checks_root,args.jar,q)
    after=m.transition(before,q);result=m.check(before,after,q);preview={**after,m.COVERAGE:coverage(q,result)}
    (out/'metadata-before.zip').write_bytes(zipped(before));(out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes());(out/'QUALIFICATION.json').write_bytes(p.encoded(q));(out/'REGISTRY.json').write_bytes(p.encoded(m.registry()))
    for name,raw in preview.items():path=out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_'+STATUS,base=BASE,source_code=sources(),before={n:p.pin(v) for n,v in before.items()},preview={n:p.pin(v) for n,v in preview.items()},qualification=p.pin(p.encoded(q)),registry=p.pin((out/'REGISTRY.json').read_bytes()),result=result,actual=measured,canonical_modified=False)
    (out/'PLAN.json').write_bytes(p.encoded(record));return record
def inspect(folder,checks,jar):
    preflight();folder=Path(folder).resolve();p.require(folder.is_relative_to(ROOT/'build'),'Use saved current-owner plan');record=json.loads((folder/'PLAN.json').read_bytes());before=read_before(folder/'metadata-before.zip');baseline(before)
    p.require(record['status']=='PLANNED_'+STATUS and record['base']==BASE and record['source_code']==sources(),'Changed plan/source')
    p.require(record['before']=={n:p.pin(v) for n,v in before.items()} and all((ROOT/n).read_bytes()==v for n,v in before.items()),'Concurrent predecessor change')
    p.require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==847,'Changed full predecessor manifest')
    q=packet();p.require((folder/'QUALIFICATION.json').read_bytes()==p.encoded(q) and record['qualification']==p.pin(p.encoded(q)),'Changed planned qualification');actual(checks,jar,q)
    p.require((folder/'REGISTRY.json').read_bytes()==p.encoded(m.registry()) and record['registry']==p.pin((folder/'REGISTRY.json').read_bytes()),'Changed registry')
    after=m.transition(before,q);result=m.check(before,after,q);preview={**after,m.COVERAGE:coverage(q,result)}
    p.require(record['result']==result and record['preview']=={n:p.pin(v) for n,v in preview.items()},'Changed transition preview')
    for name,raw in preview.items():p.require((folder/'preview'/name).read_bytes()==raw,'Changed preview bytes: '+name)
    return record,before,q,preview
def apply(args,out):
    record,before,q,preview=inspect(args.plan_dir,args.checks_root,args.jar);result=m.check(before,{n:preview[n] for n in m.META},q)
    evidence={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PLAN.json':p.encoded(record),'REGISTRY.json':(args.plan_dir/'REGISTRY.json').read_bytes(),'QUALIFICATION.json':p.encoded(q),'README.md':('# Original SKAT2.2.5 catalog admission\n\n'+m.CURRENT+'\n\n'+m.SCOPE+'\n\n'+m.ACTION+'\n').encode()}
    updates={FOLDER+'/'+n:raw for n,raw in evidence.items()};updates.update(preview)
    proof=dict(schema_version=1,status=STATUS,base_commit=BASE,source_code=sources(),before={n:p.pin(v) for n,v in before.items()},after={n:p.pin(preview[n]) for n in m.META},qualification=p.pin(p.encoded(q)),result=result,evidence={n:p.pin(raw) for n,raw in updates.items() if n.startswith(FOLDER+'/')},qualified_packet_status=p.pin((ROOT/PACKET/'STATUS.json').read_bytes()),whole_goal_complete=False)
    proof['evidence'][m.COVERAGE]=p.pin(updates[m.COVERAGE]);updates[m.PROOF]=p.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={n:None for n in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for name in updates:(ROOT/name).parent.mkdir(parents=True,exist_ok=True)
    def post():
        p.require(m.check(before,{n:(ROOT/n).read_bytes() for n in m.META},q)==result,'Installed metadata differs');current=clean_manifest();p.require(current['entries']==849,'Incorrect full after manifest');return current
    current=install(ROOT,out,expected,updates,post);(out/'COMPLETED.json').write_bytes(p.encoded(dict(status=STATUS,result=result,current_manifest=current,canonical_modified=True,scientific_source_changed=False)));return result
def verify(args,out):
    proof=json.loads((ROOT/m.PROOF).read_bytes());p.require(proof['status']==STATUS and proof['base_commit']==BASE and proof['source_code']==sources(),'Changed admitted source/scope')
    for name,wanted in proof['evidence'].items():p.require(p.pin((ROOT/name).read_bytes())==wanted,'Changed admitted evidence: '+name)
    before=read_before(ROOT/FOLDER/'metadata-before.zip');baseline(before);p.require(proof['before']=={n:p.pin(v) for n,v in before.items()},'Changed exact retained before bytes')
    p.require((ROOT/FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',BASE+':'+m.DATA+'/SHA256SUMS'),'Changed predecessor full manifest')
    q=packet();p.require(p.pin(p.encoded(q))==proof['qualification'] and p.pin((ROOT/PACKET/'STATUS.json').read_bytes())==proof['qualified_packet_status'],'Changed qualified packet');p.require((ROOT/FOLDER/'REGISTRY.json').read_bytes()==p.encoded(m.registry()),'Changed admitted registry')
    after={n:(ROOT/n).read_bytes() for n in m.META};result=m.check(before,after,q);p.require(proof['after']=={n:p.pin(v) for n,v in after.items()} and proof['result']==result,'Changed deterministic installed catalog')
    measured=actual(args.checks_root,args.jar,q) if args.checks_root is not None and args.jar is not None else None
    current=clean_manifest();p.require(current['entries']==849,'Changed full849-entry manifest');record=dict(status=STATUS,result=result,current_manifest=current,actual_runtime_rechecked=measured,scientific_models_refit=False,whole_goal_complete=False);(out/'COMPLETED.json').write_bytes(p.encoded(record));return record
def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for n in ['plan','inspect','apply','verify']:mode.add_argument('--'+n,action='store_true')
    for n in ['out','checks-root','jar','plan-dir']:parser.add_argument('--'+n,type=Path,required=n=='out')
    args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');p.require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        p.require((args.checks_root is None)==(args.jar is None),'Supply both --checks-root and --jar for an actual runtime/producer recheck')
        if not args.verify:p.require(args.checks_root is not None and args.jar is not None,'Plan/inspect/apply require actual checks and original JAR')
        if args.plan:plan(args,args.out)
        elif args.inspect:
            p.require(args.plan_dir is not None,'Inspect requires --plan-dir');record=inspect(args.plan_dir,args.checks_root,args.jar)[0];(args.out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_INSPECTED_'+STATUS,result=record['result'])))
        elif args.apply:
            p.require(args.plan_dir is not None,'Apply requires --plan-dir');apply(args,args.out)
        else:verify(args,out=args.out)
        print('PASS original SKAT2.2.5 catalog '+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=m.ACTION)))
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error)+'\n'+m.ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
