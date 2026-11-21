"""Plan,inspect,transactionally admit and independently verify original TWOSAMPLEMR source metadata."""
from pathlib import Path
import argparse,io,json,sys,traceback,zipfile
from twosamplemr_catalog import primary as p,model as m,evidence as e
from twosamplemr_dispatch_transition import DISPATCH,dispatcher_before
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from catalog_source_integrity import reverse_delta,verify_source
from wire_signac_catalog import delta

ROOT=Path(__file__).resolve().parents[3]
STATUS='PASS_TWOSAMPLEMR_CUMULATIVE_ADMISSION_ONE_FAMILY_14_SOURCE_ROLES'
require=p.require
def zipped(files):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for n,raw in sorted(files.items()):archive.writestr(n,raw)
    return stream.getvalue()
def read_before(path):
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist())==8 and set(archive.namelist())==set(m.META),'Different exact-before archive population');require(archive.testzip() is None,'Corrupt exact-before archive');return {n:archive.read(n) for n in m.META}
def coverage(primary,result):return p.encoded(dict(schema_version=1,status='PASS_ADMITTED_TWOSAMPLEMR14_SOURCE_ROLES',source_crosswalk=primary,result=result,one_implementation_family='twosamplemr',new_upstream_family_credit=1,new_native_family_credit=0,native_helpers_additional_family_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))

def fresh(cache,code_cache):
    require(cache is not None,'Fresh source audit requires --primary-cache');result=p.audit(cache,code_cache);require(result==p.frozen(),'Fresh primary audit differs from frozen crosswalk');return result
def plan(args,out):
    require(args.historical_checkout is not None and args.author_code_cache is not None,'Plan requires the exact --historical-checkout and preceding --author-code-cache');before={n:(ROOT/n).read_bytes() for n in m.META};foundation=e.baseline(before);require(clean_manifest()['entries']==835,'Different previous manifest')
    primary=fresh(args.primary_cache,args.author_code_cache);qualification=e.admission_qualification();after=m.transition(before,primary,qualification['result']);result=m.check(before,after,primary,qualification['result']);receipt=e.run_predecessor(args.historical_checkout,before,out,args.primary_cache,args.author_code_cache);current=(ROOT/DISPATCH).read_bytes();retained=e.retained(before,current,receipt)
    (out/'metadata-before.zip').write_bytes(zipped(before));(out/'qualified-dispatcher.java').write_bytes(current);(out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes())
    for n,value in [('PRIMARY.json',primary),('QUALIFICATION.json',qualification),('PREDECESSOR.json',receipt),('RETAINED.json',retained)]:(out/n).write_bytes(p.encoded(value))
    preview=dict(after);preview[m.COVERAGE]=coverage(primary,result)
    for n,raw in preview.items():path=out/'preview'/n;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    files=['metadata-before.zip','qualified-dispatcher.java','PREVIOUS-SHA256SUMS','PRIMARY.json','QUALIFICATION.json','PREDECESSOR.json','RETAINED.json','context.stdout','context.stderr','global.stdout','global.stderr','PREDECESSOR-PROCESS.json']
    record=dict(schema_version=1,status='PLANNED_TWOSAMPLEMR_ONE_FAMILY_ADMISSION',foundation=foundation,source_code=e.sources(),result=result,before={n:p.pin(raw) for n,raw in before.items()},expected_preview={n:p.pin(raw) for n,raw in preview.items()},files={n:p.pin((out/n).read_bytes()) for n in files})
    (out/'PLAN.json').write_bytes(p.encoded(record));(out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_TWOSAMPLEMR_PLAN_WITHOUT_CANONICAL_MUTATION',result=result)));return record
def inspect(folder,cache=None,code_cache=None):
    record=json.loads((folder/'PLAN.json').read_bytes());require(record['schema_version']==1 and record['status']=='PLANNED_TWOSAMPLEMR_ONE_FAMILY_ADMISSION' and record['source_code']==e.sources(),'Stale plan/schema/source')
    for n,w in record['files'].items():require(p.pin((folder/n).read_bytes())==w,'Changed plan evidence: '+n)
    before=read_before(folder/'metadata-before.zip');require(record['foundation']==e.baseline(before),'Changed Git/head/metadata/source baseline');require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata changed after plan')
    require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==835,'Changed preceding manifest')
    primary=json.loads((folder/'PRIMARY.json').read_bytes());require(primary==p.frozen(),'Different planned source audit')
    if cache is not None:require(p.audit(cache,code_cache)==primary,'Fresh primary source audit differs')
    qualification=e.admission_qualification();require(qualification==json.loads((folder/'QUALIFICATION.json').read_bytes()),'Changed qualification or controls')
    after=m.transition(before,primary,qualification['result']);result=m.check(before,after,primary,qualification['result']);require(result==record['result'],'Different planned result')
    receipt=json.loads((folder/'PREDECESSOR.json').read_bytes());current=(folder/'qualified-dispatcher.java').read_bytes();require(current==(ROOT/DISPATCH).read_bytes(),'Changed qualified dispatcher');require(e.retained(before,current,receipt)==json.loads((folder/'RETAINED.json').read_bytes()),'Changed previous-owner retention')
    preview=dict(after);preview[m.COVERAGE]=coverage(primary,result);require(record['expected_preview']=={n:p.pin(raw) for n,raw in preview.items()},'Different preview identity')
    for n,raw in preview.items():require((folder/'preview'/n).read_bytes()==raw,'Changed preview file: '+n)
    return record,before,primary,qualification,receipt,preview
def apply(args,out):
    require(args.plan_dir is not None and args.primary_cache is not None and args.author_code_cache is not None,'Apply needs --plan-dir, --primary-cache and --author-code-cache');record,before,primary,qualification,receipt,preview=inspect(args.plan_dir,args.primary_cache,args.author_code_cache);current=(ROOT/DISPATCH).read_bytes();after={n:preview[n] for n in m.META};result=m.check(before,after,primary,qualification['result']);retained=e.retained(before,current,receipt)
    folder=ROOT/e.FOLDER;require(not folder.exists(),'Preserve existing admission owner; do not reapply');folder.mkdir(parents=True)
    artifacts={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),'qualified-dispatcher.java':current,'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PRIMARY.json':p.encoded(primary),'QUALIFICATION.json':p.encoded(qualification),'PREDECESSOR.json':p.encoded(receipt),'RETAINED.json':p.encoded(retained),'PLAN.json':p.encoded(record),'owner-source.zip':zipped({n:(ROOT/n).read_bytes() for n in e.OWN}),'WARNINGS.md':('# Warnings and required actions\n\n'+m.ACTION+'\n\n'+m.NOTE+'\n').encode(),'README.md':('# TWOSAMPLEMR cumulative admission evidence\n\nExact-before metadata, one original-runtime implementation, two newly witnessed observations and fourteen appended source roles, qualified unchanged global dispatcher, previous-owner execution and current owner source are retained.\n\nRun `python -B tools/literature/close_twosamplemr_catalog.py --verify --out build/NEW-twosamplemr-verify`; add `--primary-cache PATH` for a fresh all-250 TWOSAMPLEMR XML/DOI audit. Old owners and fingerprints remain unchanged.\n\n'+m.ACTION+'\n').encode()}
    for n in ['context.stdout','context.stderr','global.stdout','global.stderr','PREDECESSOR-PROCESS.json']:artifacts[n]=(args.plan_dir/n).read_bytes()
    updates={e.FOLDER+'/'+n:raw for n,raw in artifacts.items()};updates.update(after);updates[m.COVERAGE]=preview[m.COVERAGE]
    changes=[delta(DISPATCH,dispatcher_before(current),current),delta(m.META[5],before[m.META[5]],after[m.META[5]])]
    updates[e.COMPAT]=p.encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='twosamplemr',scope='Already qualified three-line TWOSAMPLEMR root route/help inverse and 14 appended source reviews only; no scientific source inverse or hash waiver.',paths=changes))
    proof=dict(schema_version=1,status=STATUS,base_commit=e.BASE,source_code=e.sources(),before={n:p.pin(raw) for n,raw in before.items()},after={n:p.pin(raw) for n,raw in after.items()},qualified_dispatcher=p.pin(current),result=result,qualification=qualification,retained_predecessor=retained,evidence={n:p.pin(raw) for n,raw in updates.items() if n.startswith(e.FOLDER+'/')},whole_goal_complete=False)
    proof['evidence'].update({m.GUIDE:p.pin((ROOT/m.GUIDE).read_bytes()),m.COVERAGE:p.pin(updates[m.COVERAGE]),e.COMPAT:p.pin(updates[e.COMPAT])});updates[e.PROOF]=p.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={n:None for n in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for n in updates:(ROOT/n).parent.mkdir(parents=True,exist_ok=True)
    def post():
        require((ROOT/DISPATCH).read_bytes()==current,'Changed qualified dispatcher during admission');require(m.check(before,{n:(ROOT/n).read_bytes() for n in m.META},primary,qualification['result'])==result,'Installed metadata differs');manifest=clean_manifest();require(manifest['entries']==838,'Different after manifest population');return manifest
    manifest=install(ROOT,out,expected,updates,post);(out/'COMPLETED.json').write_bytes(p.encoded(dict(status=STATUS,result=result,current_manifest=manifest)))
def verify(out,cache=None,code_cache=None):
    proof=json.loads((ROOT/e.PROOF).read_bytes());require(proof['schema_version']==1 and proof['status']==STATUS and proof['base_commit']==e.BASE and proof['source_code']==e.sources(),'Different admitted proof/source')
    for n,w in proof['evidence'].items():require(p.pin((ROOT/n).read_bytes())==w,'Changed admitted evidence: '+n)
    folder=ROOT/e.FOLDER;before=read_before(folder/'metadata-before.zip')
    for n,raw in before.items():require(raw==e.git(ROOT,'show',e.BASE+':'+n),'Retained exact-before differs')
    require((folder/'PREVIOUS-SHA256SUMS').read_bytes()==e.git(ROOT,'show',e.BASE+':'+m.DATA+'/SHA256SUMS'),'Changed prior manifest');current=(folder/'qualified-dispatcher.java').read_bytes();require(current==(ROOT/DISPATCH).read_bytes()==e.git(ROOT,'show',e.BASE+':'+DISPATCH),'Changed qualified dispatcher')
    primary=json.loads((folder/'PRIMARY.json').read_bytes());require(primary==p.frozen(),'Changed frozen source audit')
    if cache is not None:require(p.audit(cache,code_cache)==primary,'Fresh primary source audit differs')
    qualification=e.admission_qualification();require(qualification==proof['qualification']==json.loads((folder/'QUALIFICATION.json').read_bytes()),'Changed global/control qualification')
    after={n:(ROOT/n).read_bytes() for n in m.META};require(proof['before']=={n:p.pin(raw) for n,raw in before.items()} and proof['after']=={n:p.pin(raw) for n,raw in after.items()},'Changed canonical identities');result=m.check(before,after,primary,qualification['result']);require(result==proof['result'],'Changed admitted scope/counts')
    receipt=json.loads((folder/'PREDECESSOR.json').read_bytes());require(e.retained(before,current,receipt)==proof['retained_predecessor'],'Changed retained previous owner')
    compat=json.loads((ROOT/e.COMPAT).read_bytes());require(len(compat['paths'])==2 and {r['path'] for r in compat['paths']}=={DISPATCH,m.META[5]},'Unexpected compatibility scope')
    for row in compat['paths']:
        expected=dispatcher_before(current) if row['path']==DISPATCH else before[m.META[5]];require(reverse_delta((ROOT/row['path']).read_bytes(),row)==expected,'Different exact source inverse');verify_source(ROOT,ROOT/m.DATA,row['path'],p.pin(expected)['sha256'])
    manifest=clean_manifest();require(manifest['entries']==838,'Different current manifest');record=dict(status=STATUS,result=result,current_manifest=manifest,fresh_source_audit_performed=cache is not None,fresh_scientific_model_execution=False);(out/'COMPLETED.json').write_bytes(p.encoded(record));return record
def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ['plan','inspect','apply','verify']:mode.add_argument('--'+name,action='store_true')
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--primary-cache',type=Path);parser.add_argument('--author-code-cache',type=Path);parser.add_argument('--plan-dir',type=Path);parser.add_argument('--historical-checkout',type=Path);args=parser.parse_args();owned=False
    e.configure_owner(args.historical_checkout)
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        if args.plan:plan(args,args.out)
        elif args.inspect:
            require(args.plan_dir is not None,'Inspect needs --plan-dir');record=inspect(args.plan_dir,args.primary_cache,args.author_code_cache)[0];(args.out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_INSPECTED_TWOSAMPLEMR_PLAN',result=record['result'])))
        elif args.apply:apply(args,args.out)
        else:verify(args.out,args.primary_cache,args.author_code_cache)
        print('PASS TWOSAMPLEMR guarded '+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),detail=traceback.format_exc(),complete_result=False,required_action=m.ACTION)
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(record))
        print(record['status']+': '+str(error)+'\n'+m.ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
