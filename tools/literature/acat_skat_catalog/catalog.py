"""Plan, inspect, transactionally admit and verify one ACAT family and 22 source roles."""
from pathlib import Path
import argparse,json,sys,traceback
from acat_skat_catalog import primary as p,model as m,evidence as e
from qtltools_catalog.catalog import zipped,read_before,read_control_archive
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from catalog_source_integrity import reverse_delta,verify_source
from wire_signac_catalog import delta
ROOT=p.ROOT;require=p.require
STATUS='PASS_ACAT_NATIVE_COMPONENT_AND22_ACAT_SKAT_SOURCE_ROLES'
def baseline(before):
    require(set(before)==set(m.META),'Different before population')
    for n,raw in before.items():require(raw==e.git(ROOT,'show',e.BASE+':'+n),'Changed exact Git baseline: '+n)
def coverage(primary,result):return p.encoded(dict(schema_version=1,status=STATUS,source_census=p.pin(p.encoded(primary)),roles=primary['roles'],negative_decisions=primary['negative_decisions'],counts=result,applied=True,scope='One bounded native ACAT0.91 combination family;21 existing reviews and one recovered SKATO observation;SKAT adapters remain pending.'))
def qualification(checks):return dict(component=e.component_qualification(checks),controls=e.control_evidence())
def preflight():
    require(not (ROOT/e.FOLDER).exists(),'Preserve existing admission owner;do not reapply')
    for n in [e.PROOF,e.COMPAT,m.COVERAGE]:require(not (ROOT/n).exists(),'Admission target already exists: '+n)
def plan(args,out):
    require(all(v is not None for v in [args.primary_cache,args.supplement_pdf,args.checks_root]),'Plan requires source XML/PDF and actual checks root');preflight()
    before={n:(ROOT/n).read_bytes() for n in m.META};baseline(before);require(clean_manifest()['entries']==844,'Different preceding full manifest')
    primary=p.audit(args.primary_cache,args.supplement_pdf);require(primary==p.frozen(),'Fresh source review differs');qualified=qualification(args.checks_root)
    after=m.transition(before,primary,qualified['component']['result']);result=m.check(before,after,primary,qualified['component']['result']);preview={**after,m.COVERAGE:coverage(primary,result)}
    (out/'metadata-before.zip').write_bytes(zipped(before));(out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes())
    for name,value in [('PRIMARY.json',primary),('QUALIFICATION.json',qualified),('REGISTRY.json',m.registry())]:(out/name).write_bytes(p.encoded(value))
    for name,raw in preview.items():path=out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_'+STATUS,base=e.BASE,source_code=e.sources(),before={n:p.pin(v) for n,v in before.items()},preview={n:p.pin(v) for n,v in preview.items()},primary=p.pin(p.encoded(primary)),qualification=p.pin(p.encoded(qualified)),registry=p.pin((out/'REGISTRY.json').read_bytes()),result=result,canonical_modified=False)
    (out/'PLAN.json').write_bytes(p.encoded(record));return record
def inspect(folder,cache,pdf,checks):
    folder=Path(folder).resolve();require(folder.is_relative_to(ROOT/'build'),'Use current-owner saved plan');preflight();record=json.loads((folder/'PLAN.json').read_bytes());before=read_before(folder/'metadata-before.zip');baseline(before)
    require(record['status']=='PLANNED_'+STATUS and record['base']==e.BASE and record['source_code']==e.sources(),'Changed plan/source')
    require(record['before']=={n:p.pin(v) for n,v in before.items()} and all((ROOT/n).read_bytes()==v for n,v in before.items()),'Changed/concurrent predecessor metadata')
    require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==844,'Changed full preceding manifest')
    primary=json.loads((folder/'PRIMARY.json').read_bytes());require(primary==p.frozen() and p.pin(p.encoded(primary))==record['primary'],'Changed planned source review')
    if cache is not None or pdf is not None:require(cache is not None and pdf is not None and p.audit(cache,pdf)==primary,'Fresh source audit differs')
    qualified=json.loads((folder/'QUALIFICATION.json').read_bytes());require(p.pin(p.encoded(qualified))==record['qualification'] and qualified['controls']==e.control_evidence(),'Changed qualification/control evidence')
    if checks is not None:require(qualification(checks)==qualified,'Actual immutable/source qualification changed')
    require(json.loads((folder/'REGISTRY.json').read_bytes())==m.registry() and p.pin((folder/'REGISTRY.json').read_bytes())==record['registry'],'Changed registry plan')
    after=m.transition(before,primary,qualified['component']['result']);result=m.check(before,after,primary,qualified['component']['result']);preview={**after,m.COVERAGE:coverage(primary,result)}
    require(record['result']==result and record['preview']=={n:p.pin(v) for n,v in preview.items()},'Changed transition/preview')
    for n,raw in preview.items():require((folder/'preview'/n).read_bytes()==raw,'Changed saved preview')
    return record,before,primary,qualified,preview
def apply(args,out):
    require(all(v is not None for v in [args.plan_dir,args.primary_cache,args.supplement_pdf,args.checks_root]),'Apply requires plan,source XML/PDF and actual checks root')
    record,before,primary,qualified,preview=inspect(args.plan_dir,args.primary_cache,args.supplement_pdf,args.checks_root);after={n:preview[n] for n in m.META};result=m.check(before,after,primary,qualified['component']['result'])
    receipts={n:(args.checks_root/n).read_bytes() for n in qualified['component']['actual_receipts']};require({n:p.pin(v) for n,v in receipts.items()}==qualified['component']['actual_receipts'],'Changed actual receipts')
    evidence={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PRIMARY.json':p.encoded(primary),'QUALIFICATION.json':p.encoded(qualified),'PLAN.json':p.encoded(record),'REGISTRY.json':(args.plan_dir/'REGISTRY.json').read_bytes(),'qualification-receipts.zip':zipped(receipts),'owner-source.zip':zipped({n:(ROOT/n).read_bytes() for n in e.OWN}),'README.md':('# ACAT/SKAT source admission\n\n'+m.CURRENT+'\n\n'+m.SCOPE+'\n\n'+m.ACTION+'\n').encode()}
    updates={e.FOLDER+'/'+n:v for n,v in evidence.items()};updates.update(after);updates[m.COVERAGE]=preview[m.COVERAGE]
    inverse=delta(m.META[5],before[m.META[5]],after[m.META[5]]);require(reverse_delta(after[m.META[5]],inverse)==before[m.META[5]],'Review inverse does not restore old bytes')
    updates[e.COMPAT]=p.encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='acat',scope='Append22 source-bound reviews and preserve all1639 prior records. No scientific/dispatcher source delta or hash waiver.',paths=[inverse]))
    proof=dict(schema_version=1,status=STATUS,base_commit=e.BASE,source_code=e.sources(),before={n:p.pin(v) for n,v in before.items()},after={n:p.pin(v) for n,v in after.items()},result=result,qualification=qualified,evidence={n:p.pin(v) for n,v in updates.items() if n.startswith(e.FOLDER+'/')},whole_goal_complete=False)
    proof['evidence'].update({m.GUIDE:p.pin((ROOT/m.GUIDE).read_bytes()),m.COVERAGE:p.pin(updates[m.COVERAGE]),e.COMPAT:p.pin(updates[e.COMPAT])})
    for directory in [ROOT/p.FOLDER,ROOT/e.CONTROLS]:
        for path in directory.iterdir():
            if path.is_file():proof['evidence'][path.relative_to(ROOT).as_posix()]=p.pin(path.read_bytes())
    updates[e.PROOF]=p.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={n:None for n in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for n in updates:(ROOT/n).parent.mkdir(parents=True,exist_ok=True)
    def post():
        require(m.check(before,{n:(ROOT/n).read_bytes() for n in m.META},primary,qualified['component']['result'])==result,'Installed metadata differs');manifest=clean_manifest();require(manifest['entries']==847,'Different resulting full manifest');return manifest
    manifest=install(ROOT,out,expected,updates,post);(out/'COMPLETED.json').write_bytes(p.encoded(dict(status=STATUS,result=result,current_manifest=manifest,canonical_modified=True,scientific_source_changed=False)));return result
def verify(args,out):
    proof=json.loads((ROOT/e.PROOF).read_bytes());require(proof['schema_version']==1 and proof['status']==STATUS and proof['base_commit']==e.BASE and proof['source_code']==e.sources(),'Changed admitted source/scope')
    for n,w in proof['evidence'].items():require(p.pin((ROOT/n).read_bytes())==w,'Changed evidence: '+n)
    folder=ROOT/e.FOLDER;before=read_before(folder/'metadata-before.zip');baseline(before);require(proof['before']=={n:p.pin(v) for n,v in before.items()},'Changed original before metadata')
    require((folder/'PREVIOUS-SHA256SUMS').read_bytes()==e.git(ROOT,'show',e.BASE+':'+m.DATA+'/SHA256SUMS'),'Changed preceding manifest')
    primary=json.loads((folder/'PRIMARY.json').read_bytes());require(primary==p.frozen(),'Changed admitted source review')
    if args.primary_cache is not None or args.supplement_pdf is not None:require(args.primary_cache is not None and args.supplement_pdf is not None and p.audit(args.primary_cache,args.supplement_pdf)==primary,'Fresh admitted source audit differs')
    qualified=json.loads((folder/'QUALIFICATION.json').read_bytes());require(qualified==proof['qualification'] and qualified['controls']==e.control_evidence(),'Changed admitted qualification');read_control_archive(folder/'qualification-receipts.zip',qualified['component']['actual_receipts'])
    if args.checks_root is not None:require(qualification(args.checks_root)==qualified,'Changed actual immutable qualification')
    for n,w in qualified['component']['source_inputs'].items():require(p.pin((ROOT/n).read_bytes())==w,'Changed qualified scientific source')
    require(not e.git(ROOT,'diff',e.BASE,'--','src/main','src/test','build.gradle','settings.gradle','gradle.properties'),'Changed scientific/build source')
    after={n:(ROOT/n).read_bytes() for n in m.META};require(proof['after']=={n:p.pin(v) for n,v in after.items()},'Changed canonical after-state');result=m.check(before,after,primary,qualified['component']['result']);require(result==proof['result'],'Changed admission result')
    compat=json.loads((ROOT/e.COMPAT).read_bytes());require(len(compat['paths'])==1 and compat['paths'][0]['path']==m.META[5] and reverse_delta(after[m.META[5]],compat['paths'][0])==before[m.META[5]],'Changed review-only exact inverse');verify_source(ROOT,ROOT/m.DATA,m.META[5],p.pin(before[m.META[5]])['sha256'])
    require(json.loads((folder/'REGISTRY.json').read_bytes())==m.registry(),'Changed provider definitions');manifest=clean_manifest();require(manifest['entries']==847,'Different current full manifest')
    result=dict(status=STATUS,result=result,current_manifest=manifest,fresh_primary_audit=args.primary_cache is not None,actual_owners_rechecked=args.checks_root is not None,scientific_models_refit=False,whole_goal_complete=False);(out/'COMPLETED.json').write_bytes(p.encoded(result));return result
def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ['plan','inspect','apply','verify']:mode.add_argument('--'+name,action='store_true')
    for name in ['out','primary-cache','supplement-pdf','checks-root','plan-dir']:parser.add_argument('--'+name,type=Path,required=name=='out')
    args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        if args.plan:plan(args,args.out)
        elif args.inspect:
            require(args.plan_dir is not None,'Inspect requires --plan-dir');record=inspect(args.plan_dir,args.primary_cache,args.supplement_pdf,args.checks_root)[0];(args.out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_INSPECTED_ACAT_SKAT_PLAN',result=record['result'])))
        elif args.apply:apply(args,args.out)
        else:verify(args,args.out)
        print('PASS guarded ACAT/SKAT catalog '+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=m.ACTION)))
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error)+'\n'+m.ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
