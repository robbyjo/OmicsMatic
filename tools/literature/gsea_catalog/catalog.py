"""Plan, inspect, transactionally admit and verify 28 source-bound GSEA roles."""
from pathlib import Path
import argparse,io,json,sys,traceback,zipfile
from gsea_catalog import model as m,primary as p,evidence as e
from gsea_dispatch_transition import DISPATCH,dispatcher_before
from catalog_source_integrity import reverse_delta,verify_source
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from wire_signac_catalog import delta

ROOT=Path(__file__).resolve().parents[3]
STATUS='PASS_GSEA_CUMULATIVE_ADMISSION_28_PROVIDER_DISTINCT_ROLES_AND_NATIVE_GLOBAL_WORKFLOW'
require=p.require
encoded=m.encoded

def read_before(path):
    with zipfile.ZipFile(path) as z:
        require(len(z.namelist())==8 and set(z.namelist())==set(m.META),'Different exact-before archive population')
        require(z.testzip() is None,'Corrupt exact-before archive')
        return {n:z.read(n) for n in m.META}

def zipped(files):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,raw in sorted(files.items()):z.writestr(n,raw)
    return stream.getvalue()

def coverage_bytes(primary,result):
    return encoded(dict(schema_version=1,status='PASS_ADMITTED_GSEA_ROLES_WITH_PROVIDER_EDITION_AND_AUTHOR_GAPS',
        source_crosswalk=primary,admitted_result=result,current_family_status='native-subset-validated',
        canonical_reviews_appended=28,canonical_observations_added=list(p.ADDITIONS),new_native_family_credit=1,new_upstream_family_credit=0,
        other_providers_qualified_by_Broad=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))

def fresh(args):
    require(args.primary_cache is not None and args.p077_supplement is not None,'Fresh source audit needs --primary-cache and --p077-supplement')
    primary=p.audit(args.primary_cache,args.p077_supplement);require(primary==p.frozen(),'Fresh 250-source/candidate/author-source audit differs');return primary

def plan(args,out):
    require(args.historical_checkout is not None,'Plan needs --historical-checkout for the unchanged admitted edgeR owner')
    before={n:(ROOT/n).read_bytes() for n in m.META};foundation=e.baseline(before);manifest=clean_manifest();require(manifest['entries']==823,'Different preceding manifest')
    primary=fresh(args);qualification=e.qualification();after=m.transition(before,primary,qualification['result']);result=m.check(before,after,primary,qualification['result'])
    receipt=e.run_predecessor(args.historical_checkout,before,out,args.primary_cache);current=(ROOT/DISPATCH).read_bytes();retained=e.retained(before,current,receipt)
    (out/'metadata-before.zip').write_bytes(zipped(before));(out/'qualified-dispatcher.java').write_bytes(current);(out/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes())
    for n,v in [('PRIMARY.json',primary),('QUALIFICATION.json',qualification),('PREDECESSOR.json',receipt),('RETAINED.json',retained)]:(out/n).write_bytes(encoded(v))
    preview=dict(after);preview[m.COVERAGE]=coverage_bytes(primary,result)
    for n,raw in preview.items():
        path=out/'preview'/n;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_GSEA_CUMULATIVE_ADMISSION',foundation=foundation,source_code=e.sources(),result=result,
        before={n:e.pin(raw) for n,raw in before.items()},expected_preview={n:e.pin(raw) for n,raw in preview.items()},
        files={n:e.pin((out/n).read_bytes()) for n in ['metadata-before.zip','qualified-dispatcher.java','PREVIOUS-SHA256SUMS','PRIMARY.json',
            'QUALIFICATION.json','PREDECESSOR.json','RETAINED.json','predecessor.stdout','predecessor.stderr','PREDECESSOR-PROCESS.json']})
    (out/'PLAN.json').write_bytes(encoded(record));(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_PREPARED_GSEA_PLAN_WITHOUT_CANONICAL_MUTATION',result=result)));return record

def inspect(folder,primary_cache=None,supplement=None):
    record=json.loads((folder/'PLAN.json').read_bytes());require(record['schema_version']==1 and record['status']=='PLANNED_GSEA_CUMULATIVE_ADMISSION','Different plan schema/status')
    require(record['source_code']==e.sources(),'Owner/control/source-audit code changed after plan')
    for n,w in record['files'].items():require(e.pin((folder/n).read_bytes())==w,'Changed plan evidence: '+n)
    before=read_before(folder/'metadata-before.zip');require(record['foundation']==e.baseline(before),'Git/head/source/canonical baseline changed after plan')
    require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata changed after plan')
    require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==823,'Preceding manifest changed')
    primary=json.loads((folder/'PRIMARY.json').read_bytes());require(primary==p.frozen(),'Different frozen source audit in plan')
    require((primary_cache is None)==(supplement is None),'Supply both exact XML cache and supplementary PDF for a fresh source audit')
    if primary_cache is not None:require(p.audit(primary_cache,supplement)==primary,'Fresh 250-source/author-source audit differs from plan')
    qualification=e.qualification();require(qualification==json.loads((folder/'QUALIFICATION.json').read_bytes()),'Global/source qualification changed')
    after=m.transition(before,primary,qualification['result']);result=m.check(before,after,primary,qualification['result']);require(result==record['result'],'Planned scope/counts differ')
    current=(folder/'qualified-dispatcher.java').read_bytes();require((ROOT/DISPATCH).read_bytes()==current,'Qualified dispatcher changed after plan')
    receipt=json.loads((folder/'PREDECESSOR.json').read_bytes());require(e.retained(before,current,receipt)==json.loads((folder/'RETAINED.json').read_bytes()),'Retained predecessor differs')
    preview=dict(after);preview[m.COVERAGE]=coverage_bytes(primary,result);require(record['expected_preview']=={n:e.pin(raw) for n,raw in preview.items()},'Different deterministic preview')
    for n,raw in preview.items():require((folder/'preview'/n).read_bytes()==raw,'Preview changed: '+n)
    return record,before,primary,qualification,receipt,preview

def apply(args,out):
    require(args.plan_dir is not None and args.primary_cache is not None and args.p077_supplement is not None,'Apply needs --plan-dir and both exact source arguments')
    record,before,primary,qualification,receipt,preview=inspect(args.plan_dir,args.primary_cache,args.p077_supplement)
    folder=ROOT/e.FOLDER;require(not folder.exists() or folder.is_dir() and not any(folder.iterdir()),'Preserve existing admission evidence; select the correct new stage');folder.mkdir(parents=True,exist_ok=True)
    current=(args.plan_dir/'qualified-dispatcher.java').read_bytes();after={n:preview[n] for n in m.META};result=m.check(before,after,primary,qualification['result']);retained=e.retained(before,current,receipt)
    artifacts={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),
        'qualified-dispatcher.java':current,'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PRIMARY.json':encoded(primary),
        'QUALIFICATION.json':encoded(qualification),'PREDECESSOR.json':encoded(receipt),'RETAINED.json':encoded(retained),
        'predecessor.stdout':(args.plan_dir/'predecessor.stdout').read_bytes(),'predecessor.stderr':(args.plan_dir/'predecessor.stderr').read_bytes(),
        'PREDECESSOR-PROCESS.json':(args.plan_dir/'PREDECESSOR-PROCESS.json').read_bytes(),'PLAN.json':encoded(record),
        'owner-source.zip':zipped({n:(ROOT/n).read_bytes() for n in e.OWN}),'WARNINGS.md':('# Warnings and required actions\n\n'+m.NOTE+'\n\n'+m.ACTION+'\n').encode(),
        'README.md':('# GSEA cumulative admission evidence\n\nEight exact preceding metadata files, unchanged qualified dispatcher/manifest, exact old-owner execution, all-250 source/56-candidate audit, native global qualification and exact owner source are retained. No source overlays or historical scientific-model refits are used.\n\nRun `python -B tools/literature/close_gsea_catalog.py --verify --out build/NEW-gsea-verify`. Add both primary-source arguments for a fresh XML/PDF audit. This owner uses Git for exact predecessor identity; native saved-proof readers execute no Git/Java/Gradle/model.\n\n'+m.ACTION+'\n').encode()}
    updates={e.FOLDER+'/'+n:raw for n,raw in artifacts.items()};updates.update(after);updates[m.COVERAGE]=preview[m.COVERAGE]
    prior=dispatcher_before(current);changes=[delta(DISPATCH,prior,current),delta(m.META[5],before[m.META[5]],after[m.META[5]])]
    updates[e.COMPAT]=encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='gsea',
        scope='The already-qualified three-line global route/help relation and 28 appended reviews only. This admission changes no dispatcher/scientific code and supplies no scientific inverse.',paths=changes))
    proof=dict(schema_version=1,status=STATUS,base_commit=e.BASE,source_code=e.sources(),before={n:e.pin(raw) for n,raw in before.items()},
        after={n:e.pin(raw) for n,raw in after.items()},qualified_dispatcher=e.pin(current),preceding_edger_dispatcher=e.pin(prior),result=result,
        qualification=qualification,retained_predecessor=retained,evidence={n:e.pin(raw) for n,raw in updates.items() if n.startswith(e.FOLDER+'/')},whole_goal_complete=False)
    proof['evidence'].update({n:e.pin((ROOT/n).read_bytes()) for n in e.GUIDES});proof['evidence'].update({m.COVERAGE:e.pin(updates[m.COVERAGE]),e.COMPAT:e.pin(updates[e.COMPAT])})
    updates[e.PROOF]=encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={n:None for n in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    def post():
        require((ROOT/DISPATCH).read_bytes()==current,'Qualified dispatcher changed during metadata admission')
        require(m.check(before,{n:(ROOT/n).read_bytes() for n in m.META},primary,qualification['result'])==result,'Installed metadata differs')
        manifest=clean_manifest();require(manifest['entries']==826,'Different after manifest population');return manifest
    manifest=install(ROOT,out,expected,updates,post);(out/'COMPLETED.json').write_bytes(encoded(dict(status=STATUS,result=result,current_manifest=manifest)))

def verify(out,primary_cache=None,supplement=None):
    proof=json.loads((ROOT/e.PROOF).read_bytes());require(proof['schema_version']==1 and proof['status']==STATUS and proof['base_commit']==e.BASE,'Different admitted proof schema/status/base')
    require(proof['source_code']==e.sources(),'Admitted owner/control/source-audit code changed')
    for n,w in proof['evidence'].items():require(e.pin((ROOT/n).read_bytes())==w,'Admitted evidence changed: '+n)
    folder=ROOT/e.FOLDER;before=read_before(folder/'metadata-before.zip')
    for n,raw in before.items():require(raw==e.git(ROOT,'show',e.BASE+':'+n),'Exact retained base bytes differ: '+n)
    require((folder/'PREVIOUS-SHA256SUMS').read_bytes()==e.git(ROOT,'show',e.BASE+':'+m.DATA+'/SHA256SUMS'),'Exact preceding manifest differs')
    current=(folder/'qualified-dispatcher.java').read_bytes();require((ROOT/DISPATCH).read_bytes()==current==e.git(ROOT,'show',e.BASE+':'+DISPATCH),'Qualified global dispatcher changed')
    primary=json.loads((folder/'PRIMARY.json').read_bytes());require(primary==p.frozen(),'Saved primary source audit changed')
    require((primary_cache is None)==(supplement is None),'Supply both exact source arguments for a fresh source audit')
    if primary_cache is not None:require(p.audit(primary_cache,supplement)==primary,'Fresh source/author-source audit differs')
    qualification=e.qualification();require(qualification==proof['qualification']==json.loads((folder/'QUALIFICATION.json').read_bytes()),'Native global qualification differs')
    after={n:(ROOT/n).read_bytes() for n in m.META};require(proof['before']=={n:e.pin(raw) for n,raw in before.items()} and proof['after']=={n:e.pin(raw) for n,raw in after.items()},'Admitted canonical identities differ')
    result=m.check(before,after,primary,qualification['result']);require(result==proof['result'],'Admitted scope/counts differ')
    receipt=json.loads((folder/'PREDECESSOR.json').read_bytes());require(e.retained(before,current,receipt)==proof['retained_predecessor'],'Retained predecessor differs')
    compat=json.loads((ROOT/e.COMPAT).read_bytes());require(len(compat['paths'])==2 and {r['path'] for r in compat['paths']}=={DISPATCH,m.META[5]},'Unexpected compatibility scope')
    for row in compat['paths']:
        expected=dispatcher_before(current) if row['path']==DISPATCH else before[m.META[5]]
        require(reverse_delta((ROOT/row['path']).read_bytes(),row)==expected,'Exact source inverse differs');verify_source(ROOT,ROOT/m.DATA,row['path'],e.pin(expected)['sha256'])
    manifest=clean_manifest();require(manifest['entries']==826,'Current manifest differs')
    result=dict(status=STATUS,result=result,current_manifest=manifest,fresh_source_audit_performed=primary_cache is not None,fresh_scientific_model_execution=False)
    (out/'COMPLETED.json').write_bytes(encoded(result));return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ['plan','inspect','apply','verify']:mode.add_argument('--'+name,action='store_true')
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--plan-dir',type=Path);parser.add_argument('--primary-cache',type=Path);parser.add_argument('--p077-supplement',type=Path);parser.add_argument('--historical-checkout',type=Path);args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
        require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output')
        args.out.mkdir(parents=True,exist_ok=False);owned=True
        if args.plan:plan(args,args.out)
        elif args.inspect:
            require(args.plan_dir is not None,'Inspect needs --plan-dir');record=inspect(args.plan_dir,args.primary_cache,args.p077_supplement)[0];(args.out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_INSPECTED_GSEA_ADMISSION_PLAN',result=record['result'])))
        elif args.apply:apply(args,args.out)
        else:verify(args.out,args.primary_cache,args.p077_supplement)
        print('PASS GSEA guarded '+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),detail=traceback.format_exc(),complete_result=False,required_action=m.ACTION)
        if owned:(args.out/'FAILURE.json').write_bytes(encoded(record))
        print(record['status']+': '+str(error)+'\n'+m.ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
