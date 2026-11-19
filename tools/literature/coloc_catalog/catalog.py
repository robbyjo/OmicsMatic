"""Plan/inspect/apply/verify bounded native COLOC admission on exact predecessor bytes."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import traceback
import zipfile
from coloc_catalog.model import META,DATA,GUIDE,COVERAGE,NOTE,ACTION,registry,transition,check
from coloc_catalog.coverage import build,encoded,require
from coloc_catalog.evidence import ROOT,BASE,FOUNDATION,pin,rawpin,git,baseline,predecessor,source_census,qualification
from plink_catalog.catalog import failure_code as previous_failure_code
from coloc_source_roles import MissingSource
from seurat_catalog.catalog import install,clean_manifest,manifest_bytes,HISTORICAL_WARNING

sys.dont_write_bytecode=True
FOLDER='docs/literature/coloc-catalog-admission'
PROOF=DATA+'/coloc-catalog-validation.json'
COMPAT=DATA+'/coloc-catalog-shared-source-compatibility.json'
OWN=['tools/literature/coloc_catalog/'+n for n in ['coverage.py','model.py','evidence.py','catalog.py','test_admission.py','ADMISSION.md']]
STATUS='PASS_COLOC_CUMULATIVE_ADMISSION_27_ROLES_SCOPED_NATIVE'

def failure_code(error):
    return 3 if isinstance(error,MissingSource) else previous_failure_code(error)

def source_pins():return {n:pin(ROOT/n) for n in OWN}

def foundation():
    values={n:pin(ROOT/n) for n in FOUNDATION}
    for n,wanted in values.items():require(wanted==rawpin(git('show',BASE+':'+n)),'Preserved foundation changed: '+n)
    return values

def collect(args,out,before):
    prior=predecessor(args.primary_cache,args.supplement_pdf,args.author_archive,before,out/'predecessor')
    census=source_census(args.primary_cache);q=qualification();after=transition(before,census,q)
    result=check(before,after,census,q)
    return prior,census,q,after,result

def inspect_plan(plan):
    plan=Path(plan).resolve();require(plan.is_relative_to(ROOT/'build'),'Plan must be in workspace build')
    record=json.loads((plan/'PLAN.json').read_bytes())
    require(record['status']=='PASS_REVIEWABLE_COLOC_ADMISSION_PLAN' and record['base_commit']==BASE
            and record['source_code']==source_pins(),'Different plan/source revision')
    for n,wanted in record['before'].items():require(pin(ROOT/n)==wanted,'Stale plan; preserve current metadata: '+n)
    for n,wanted in record['files'].items():require(pin(plan/n)==wanted,'Changed plan artifact: '+n)
    before=baseline();census=json.loads((plan/'SOURCE-CENSUS.json').read_bytes());q=json.loads((plan/'QUALIFICATION.json').read_bytes())
    after={n:(plan/'preview'/n).read_bytes() for n in META}
    require(record['after']=={n:rawpin(raw) for n,raw in after.items()},'Plan preview differs')
    result=check(before,after,census,q);require(result==record['result'],'Plan result differs')
    return record

def admit(args,out):
    before=baseline();require(all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Canonical metadata differs from actual predecessor; preserve edits')
    old_manifest=clean_manifest();sources=foundation();prior,census,q,after,result=collect(args,out,before)
    if args.plan_dir is not None:
        planned=inspect_plan(args.plan_dir)
        require(planned['after']=={n:rawpin(raw) for n,raw in after.items()} and planned['result']==result,'Fresh proposal differs from inspected plan')
        for name,value in [('QUALIFICATION.json',q),('SOURCE-CENSUS.json',census),('PREDECESSOR-CHECK.json',prior)]:
            require(json.loads((args.plan_dir/name).read_bytes())==value,'Fresh evidence differs from plan: '+name)
    for name,value in [('SOURCE-CENSUS.json',census),('QUALIFICATION.json',q),('PREDECESSOR-CHECK.json',prior)]:
        (out/name).write_bytes(encoded(value))
    for name,raw in after.items():
        path=out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(status='PASS_REVIEWABLE_COLOC_ADMISSION_PLAN',base_commit=BASE,result=result,source_code=source_pins(),
        before={n:rawpin(raw) for n,raw in before.items()},after={n:rawpin(raw) for n,raw in after.items()},
        files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()})
    (out/'PLAN.json').write_bytes(encoded(record))
    if not args.apply:
        (out/'COMPLETED.json').write_bytes(encoded(dict(status=record['status'],**result)));return result
    require(args.plan_dir is not None,'Apply requires --plan-dir from an inspected concrete plan')
    require((ROOT/GUIDE).is_file(),'Finish the full source/scope crosswalk before admission')
    folder=ROOT/FOLDER
    require(not folder.exists() and all(not (ROOT/n).exists() for n in [PROOF,COMPAT,COVERAGE]),'Admission paths already exist; preserve them')
    folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for n,raw in before.items():archive.writestr(n,raw)
    (folder/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/DATA/'SHA256SUMS').read_bytes())
    (folder/'REGISTRY.json').write_bytes(encoded(registry()));(folder/'PREDECESSOR-CHECK.json').write_bytes(encoded(prior));(folder/'QUALIFICATION.json').write_bytes(encoded(q))
    (folder/'WARNINGS.md').write_bytes(('# COLOC admission warnings\n\n'+ACTION+'\n\n'+HISTORICAL_WARNING+'\n\n'+NOTE+'\n').encode())
    with zipfile.ZipFile(folder/'verifier-source.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for n in OWN:archive.writestr(n,(ROOT/n).read_bytes())
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    name=META[5];change=delta(name,before[name],after[name]);require(reverse_delta(after[name],change)==before[name],'Exact new review inverse differs')
    extras={COVERAGE:encoded(build(census)),COMPAT:encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='coloc',
        scope='26 source-bound appended reviews; exact JSON inverse only. No scientific source overlay or old verifier/hash refresh.',paths=[change]))}
    proof=dict(status=STATUS,**result,base_commit=BASE,source_code=source_pins(),foundation_sources=sources,
        before={n:rawpin(raw) for n,raw in before.items()},after={n:rawpin(raw) for n,raw in after.items()},
        predecessor_audit=prior,component_qualification=q,predecessor_current_manifest=old_manifest,
        evidence={p.relative_to(ROOT).as_posix():pin(p) for p in folder.iterdir() if p.is_file()},
        scientific_engines_changed=False,historical_fingerprints_refreshed=False,earlier_sources_or_verifiers_overlaid=False,
        literal_native_BF_source_parity=False,website_deployed=False,
        historical_full_marker_audit=prior['historical_full_marker_audit'])
    proof['evidence'].update({n:rawpin(raw) for n,raw in extras.items()});proof['evidence'][GUIDE]=pin(ROOT/GUIDE);extras[PROOF]=encoded(proof)
    updates={**after,**extras};updates[DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={**before,**{n:None for n in extras},DATA+'/SHA256SUMS':(ROOT/DATA/'SHA256SUMS').read_bytes()}
    require(clean_manifest()==old_manifest,'Manifest changed before admission')
    def post():
        check(before,{n:(ROOT/n).read_bytes() for n in META},census,q);return clean_manifest()
    current=install(ROOT,out,expected,updates,post)
    (out/'COMPLETED.json').write_bytes(encoded(dict(status=STATUS,**result,current_manifest=current)));return result

def verify(args,out):
    proof=json.loads((ROOT/PROOF).read_bytes())
    require(proof['status']==STATUS and proof['base_commit']==BASE and proof['source_code']==source_pins()
            and proof['foundation_sources']==foundation(),'Admission/source foundation differs')
    for n,wanted in proof['evidence'].items():require(pin(ROOT/n)==wanted,'Changed admission evidence: '+n)
    with zipfile.ZipFile(ROOT/FOLDER/'catalog-before.zip') as archive:
        require(len(archive.namelist())==8 and set(archive.namelist())==set(META),'Incomplete before archive')
        before={n:archive.read(n) for n in META}
    require(before==baseline() and proof['before']=={n:rawpin(raw) for n,raw in before.items()},'Actual predecessor differs')
    require((ROOT/FOLDER/'PREVIOUS-SHA256SUMS').read_bytes()==git('show',BASE+':'+DATA+'/SHA256SUMS')
            and json.loads((ROOT/FOLDER/'REGISTRY.json').read_bytes())==registry(),'Old manifest or registry differs')
    prior,census,q,expected,result=collect(args,out,before)
    require(prior==proof['predecessor_audit'] and q==proof['component_qualification'],'Fresh cumulative qualification differs')
    require(build(census)==json.loads((ROOT/COVERAGE).read_bytes()),'Source-role coverage differs')
    after={n:(ROOT/n).read_bytes() for n in META}
    require(after==expected and proof['after']=={n:rawpin(raw) for n,raw in after.items()},'Actual metadata differs')
    require(all(proof.get(k)==v for k,v in result.items()),'Recorded counts or scope differ')
    from catalog_source_integrity import reverse_delta,verify_source
    compat=json.loads((ROOT/COMPAT).read_bytes());name=META[5]
    require(compat['schema_version']==1 and compat['status']=='passed-dispatch-catalog-only-source-deltas'
            and len(compat['paths'])==1 and compat['paths'][0]['path']==name
            and reverse_delta(after[name],compat['paths'][0])==before[name],'Review compatibility inverse differs')
    verify_source(ROOT,ROOT/DATA,name,rawpin(before[name])['sha256'])
    report=dict(status=STATUS,**result,current_manifest=clean_manifest(),scientific_source_overlay_used=False,
                original_scientific_programs_reexecuted=False,historical_full_marker_audit=prior['historical_full_marker_audit'])
    (out/'COMPLETED.json').write_bytes(encoded(report));return report

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ['plan','inspect','apply','verify']:mode.add_argument('--'+name,action='store_true')
    for name in ['primary-cache','author-archive','supplement-pdf','plan-dir']:parser.add_argument('--'+name,type=Path)
    parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=None;owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Require Python 3.11+ without -O')
        out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Choose NEW workspace build output');out.mkdir(parents=True,exist_ok=False);owned=True
        if args.inspect:
            require(args.plan_dir is not None,'Inspect requires --plan-dir');record=inspect_plan(args.plan_dir);result=record['result'];(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_INSPECTED_COLOC_ADMISSION_PLAN',result=result)))
        else:
            require(all(getattr(args,n) is not None for n in ['primary_cache','author_archive','supplement_pdf']),'Supply exact primary-cache, author-archive and supplement-pdf prerequisites')
            result=verify(args,out) if args.verify else admit(args,out)
        print('WARNING: '+ACTION+' '+HISTORICAL_WARNING,file=sys.stderr,flush=True);print(json.dumps(result),flush=True);return 0
    except Exception as error:
        code=failure_code(error);failure=dict(status='SKIP3_DEPENDENT_ADMISSION_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_ADMISSION',error=repr(error),detail=traceback.format_exc(),required_action=ACTION)
        if owned:(out/'FAILURE.json').write_bytes(encoded(failure))
        print(failure['status']+': '+failure['detail']+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
