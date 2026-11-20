"""Plan/inspect/apply/verify the qualified global command's discovery metadata."""
from pathlib import Path
import argparse
import json
import sys
import traceback
import zipfile
import susie_dispatch_catalog as m
from susie_dispatch_transition import DISPATCH,dispatcher_before,pin,require
from susie_replay_control import WORKFLOW,ARCHIVE,check as control_change
from verify_susie_dispatch import verify as qualification
from seurat_catalog.catalog import install,clean_manifest,manifest_bytes

ROOT=m.ROOT
OWN=['tools/literature/'+n for n in ['susie_dispatch_transition.py','susie_replay_control.py','susie_dispatch_catalog.py',
    'close_susie_dispatch.py','verify_susie_dispatch.py','test_susie_dispatch_transition.py','test_susie_dispatch_catalog.py']]


def sources():return {n:pin((ROOT/n).read_bytes()) for n in OWN}


def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()


def inspect(plan):
    record=json.loads((plan/'PLAN.json').read_bytes())
    require(record['status']=='PASS_REVIEWABLE_SUSIE_COMMAND_DISCOVERY_PLAN' and record['sources']==sources(),'Stale command plan/source')
    before={n:(ROOT/n).read_bytes() for n in m.META}
    require(record['before']=={n:pin(v) for n,v in before.items()},'Stale metadata before; preserve edits')
    after={n:(plan/'preview'/n).read_bytes() for n in m.META}
    require(record['after']=={n:pin(v) for n,v in after.items()},'Changed preview')
    require(m.check(before,after)==record['result'],'Different deterministic command preview')
    return record


def plan_or_apply(args,out):
    before={n:(ROOT/n).read_bytes() for n in m.META};prior=m.retained_catalog(before);q,_=qualification()
    after=m.transition(before);result=m.check(before,after);manifest=clean_manifest()
    require(manifest['entries']==818,'Different predecessor manifest')
    root_before=dispatcher_before((ROOT/DISPATCH).read_bytes())
    control=control_change((ROOT/ARCHIVE).read_bytes(),(ROOT/WORKFLOW).read_bytes())
    for n,raw in after.items():
        path=out/'preview'/n;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(status='PASS_REVIEWABLE_SUSIE_COMMAND_DISCOVERY_PLAN',base_commit=m.BASE,sources=sources(),
        before={n:pin(v) for n,v in before.items()},after={n:pin(v) for n,v in after.items()},
        result=result,qualification=q,retained_catalog=prior,replay_control_change=control,
        dispatcher_before=pin(root_before),dispatcher_after=pin((ROOT/DISPATCH).read_bytes()))
    (out/'PLAN.json').write_bytes(encoded(record))
    if not args.apply:
        (out/'COMPLETED.json').write_bytes(encoded(dict(status=record['status'],result=result)));return
    require(args.plan_dir is not None,'Apply needs an inspected --plan-dir')
    planned=inspect(args.plan_dir);require(planned==record,'Fresh proposal differs from inspected plan')
    folder=ROOT/m.FOLDER
    require(not folder.exists() and not (ROOT/m.PROOF).exists() and not (ROOT/m.COMPAT).exists(),'Preserve existing command-discovery evidence')
    folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,raw in before.items():z.writestr(n,raw)
    (folder/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/m.DATA/'SHA256SUMS').read_bytes())
    (folder/'QUALIFICATION.json').write_bytes(encoded(q));(folder/'RETAINED-CATALOG.json').write_bytes(encoded(prior))
    (folder/'WARNINGS.md').write_text('# SuSiE global command warnings\n\n'+m.ACTION+'\n',encoding='utf-8')
    with zipfile.ZipFile(folder/'owner-source.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n in OWN:z.writestr(n,(ROOT/n).read_bytes())
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    change=delta(DISPATCH,root_before,(ROOT/DISPATCH).read_bytes())
    require(reverse_delta((ROOT/DISPATCH).read_bytes(),change)==root_before,'Exact root-source inverse differs')
    extras={m.COMPAT:encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='susie',
        scope='Exact three additive main-route/help lines only. The separately qualified replay-control change is not a generic scientific-source inverse.',paths=[change]))}
    proof=dict(schema_version=1,status='PASS_QUALIFIED_SUSIE_GLOBAL_COMMAND_AND_REPLAY_GUARD_METADATA',base_commit=m.BASE,
        source_code=sources(),before=record['before'],after=record['after'],result=result,qualification=q,
        retained_catalog=prior,replay_control_change=control,dispatcher_before=pin(root_before),
        dispatcher_after=pin((ROOT/DISPATCH).read_bytes()),
        evidence={p.relative_to(ROOT).as_posix():pin(p.read_bytes()) for p in folder.iterdir() if p.is_file()})
    proof['evidence'][m.GUIDE]=pin((ROOT/m.GUIDE).read_bytes());proof['evidence'][m.COMPAT]=pin(extras[m.COMPAT])
    extras[m.PROOF]=encoded(proof);updates={**after,**extras};updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={**before,**{n:None for n in extras},m.DATA+'/SHA256SUMS':(ROOT/m.DATA/'SHA256SUMS').read_bytes()}
    def post():
        m.check(before,{n:(ROOT/n).read_bytes() for n in m.META});return clean_manifest()
    current=install(ROOT,out,expected,updates,post);require(current['entries']==820,'Different updated manifest population')
    (out/'COMPLETED.json').write_bytes(encoded(dict(status=proof['status'],result=result,current_manifest=current)))


def verify(out):
    proof=json.loads((ROOT/m.PROOF).read_bytes());require(proof['source_code']==sources(),'Changed command owner/source')
    for n,w in proof['evidence'].items():require(pin((ROOT/n).read_bytes())==w,'Changed command evidence: '+n)
    with zipfile.ZipFile(ROOT/m.FOLDER/'metadata-before.zip') as z:
        require(len(z.namelist())==8 and set(z.namelist())==set(m.META),'Different before archive')
        before={n:z.read(n) for n in m.META}
    after={n:(ROOT/n).read_bytes() for n in m.META}
    require(proof['before']=={n:pin(v) for n,v in before.items()} and proof['after']=={n:pin(v) for n,v in after.items()},'Actual command metadata differs')
    require(m.check(before,after)==proof['result'] and m.retained_catalog(before)==proof['retained_catalog'],'Different retained catalog or transition')
    q,_=qualification();require(q==proof['qualification'],'Different current producer qualification')
    from catalog_source_integrity import reverse_delta,verify_source
    compat=json.loads((ROOT/m.COMPAT).read_bytes())
    require(len(compat['paths'])==1 and compat['paths'][0]['path']==DISPATCH
        and pin(reverse_delta((ROOT/DISPATCH).read_bytes(),compat['paths'][0]))==proof['dispatcher_before'],'Dispatcher compatibility differs')
    verify_source(ROOT,ROOT/m.DATA,DISPATCH,proof['dispatcher_before']['sha256'])
    manifest=clean_manifest();require(manifest['entries']==820,'Current manifest differs')
    result=dict(status=proof['status'],result=proof['result'],current_manifest=manifest)
    (out/'COMPLETED.json').write_bytes(encoded(result));return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for n in ['plan','inspect','apply','verify']:mode.add_argument('--'+n,action='store_true')
    parser.add_argument('--plan-dir',type=Path);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
        require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output')
        args.out.mkdir(parents=True,exist_ok=False);owned=True
        if args.inspect:
            require(args.plan_dir is not None,'Inspect needs --plan-dir');record=inspect(args.plan_dir)
            (args.out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_INSPECTED_SUSIE_COMMAND_PLAN',result=record['result'])))
        elif args.verify:verify(args.out)
        else:plan_or_apply(args,args.out)
        print('PASS qualified SuSiE command metadata; no new family/review credit');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        value=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),detail=traceback.format_exc(),required_action=m.ACTION)
        if owned:(args.out/'FAILURE.json').write_bytes(encoded(value))
        print(value['status']+': '+value['detail']+'\n'+m.ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
