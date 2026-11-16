#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one scoped LDpred2 family after complete current-source and lineage checks."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from ldpred2_catalog.lineage import *
from ldpred2_catalog.model import transition,check,registry,NOTE,WARNING
from ldpred2_primary.source import census
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
if not __debug__:raise RuntimeError('Source/scientific assertions must remain enabled')
STATUS='PASS_SCOPED_LDPRED2_NATIVE_AND_ORIGINAL_CATALOG_ADMISSION'
REVISION=3
ACTION='Build current source and use ldpred2-tools. Missing permitted prerequisites require SKIP3 and skipping dependent analysis; forbidden installation stays skipped. FAIL2 admits no complete result. Retain raw inputs, original objects, source failures and historical evidence; retry NEW paths after correcting causes. Finite chains are not convergence; do not pool automatically. LDpred1, unresolved author parameters and full-paper reproduction remain unqualified. Full250-paper goal remains active.'
def write(path,value):
    with path.open('xb') as f:f.write(encoded(value))
def load_before():
    with zipfile.ZipFile(ROOT/EVIDENCE/'catalog-before.zip') as z:
        assert len(z.namelist())==8 and set(z.namelist())==set(META);return {n:z.read(n) for n in META}
def own_sources():return [p.relative_to(ROOT).as_posix() for p in (ROOT/OWN).rglob('*') if p.is_file()]+[GUIDE]
def load_catalog_registry(root=ROOT):
    assert Path(root).resolve()==ROOT,'Use the actual current source tree'
    return registry()
def compatibility(before,after):
    d=delta('tools/literature/reviewed-uses.json',before['tools/literature/reviewed-uses.json'],after['tools/literature/reviewed-uses.json']);assert reverse_delta(after['tools/literature/reviewed-uses.json'],d)==before['tools/literature/reviewed-uses.json']
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='ldpred',scope='Five LDpred2 roles only; exact inverse metadata journal, no scientific-source overlay.',paths=[d])
def apply(args,out):
    assert not (ROOT/EVIDENCE).exists() and not (ROOT/PROOF).exists()
    initial_sources={n:pin(ROOT/n) for n in own_sources()}
    historical,before=parent_state();assert {n:(ROOT/n).read_bytes() for n in META}==before
    source=source_inventory();guard=source_guard(source)
    qout=out/'lineage';qout.mkdir();science=qualification(historical,before,args.catalog_parent_checkout,args.previous_parent_checkout,args.baseline_checkout,qout,True)
    primary=census();assert len(registry())==485
    after=transition(before);result=check(before,after)
    assert initial_sources=={n:pin(ROOT/n) for n in own_sources()},'Consumer implementation changed during execution'
    folder=ROOT/EVIDENCE;folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,raw in before.items():z.writestr(n,raw)
    for n,value in [('SOURCE-TREE.json',source),('SOURCE-GUARD.json',guard),('QUALIFICATION.json',science),('CENSUS.json',primary),('REGISTRY.json',registry())]:write(folder/n,value)
    (folder/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());(ROOT/COMPATIBILITY).write_bytes(encoded(compatibility(before,after)))
    write(out/'PREPARED.json',dict(before={n:rawpin(raw) for n,raw in before.items()},after={n:rawpin(raw) for n,raw in after.items()},**result))
    assert all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Concurrent metadata mutation'
    for n,raw in after.items():(ROOT/n).write_bytes(raw)
    assert source_guard(source)==guard
    evidence={p.relative_to(ROOT).as_posix():pin(p) for p in folder.rglob('*') if p.is_file()};evidence[COMPATIBILITY]=pin(ROOT/COMPATIBILITY)
    assert initial_sources=={n:pin(ROOT/n) for n in own_sources()}
    proof=dict(status=STATUS,consumer_revision=REVISION,first_unadmitted_candidate_retained=True,second_candidate_retained_for_mobile_layout_fix=True,**result,before={n:rawpin(raw) for n,raw in before.items()},after={n:pin(ROOT/n) for n in META},sources=initial_sources,evidence=evidence,old_verifiers_or_Git_monkeypatched=False,old_scientific_sources_overridden=False,source_build_required=True,whole_paper_reproduced=False,finite_source_outputs_are_not_convergence=True)
    write(ROOT/PROOF,proof);return proof
def verify(args,out):
    proof=read_json(ROOT/PROOF);assert proof['status']==STATUS and proof['consumer_revision']==REVISION
    for group in ['sources','evidence','after']:
        for n,v in proof[group].items():assert not (ROOT/n).is_symlink() and pin(ROOT/n)==v,n
    assert set(own_sources())==set(proof['sources'])
    before=load_before();historical,erratum_after=parent_state();assert before==erratum_after and {n:rawpin(raw) for n,raw in before.items()}==proof['before']
    after={n:(ROOT/n).read_bytes() for n in META};result=check(before,after);assert read_json(ROOT/COMPATIBILITY)==compatibility(before,after)
    assert registry()==read_json(ROOT/EVIDENCE/'REGISTRY.json')
    primary=census();assert primary==read_json(ROOT/EVIDENCE/'CENSUS.json')
    source=read_json(ROOT/EVIDENCE/'SOURCE-TREE.json');guard=source_guard(source);assert guard==read_json(ROOT/EVIDENCE/'SOURCE-GUARD.json')
    qout=out/'lineage';qout.mkdir();science=qualification(historical,before,args.catalog_parent_checkout,args.previous_parent_checkout,args.baseline_checkout,qout,args.rescan_sources)
    assert source_guard(source)==guard
    for n,v in proof['after'].items():assert pin(ROOT/n)==v,n
    return dict(status=STATUS,**result,current_source_guard=guard,complete_predecessor_catalog_reverified=True,primary_sources_rescanned=250,current_scientific_sources_match_snapshots=True,original_verifiers_or_Git_overlaid=False,scientific_calls_freshly_repeated_by_this_catalog_check=False,qualification=science,whole_paper_reproduced=False)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);m=p.add_mutually_exclusive_group(required=True);m.add_argument('--apply',action='store_true');m.add_argument('--verify',action='store_true');p.add_argument('--catalog-parent-checkout',required=True,type=Path);p.add_argument('--previous-parent-checkout',required=True,type=Path);p.add_argument('--baseline-checkout',required=True,type=Path);p.add_argument('--rescan-sources',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=None
    try:
        target=a.out.absolute();assert target.resolve().is_relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target;(out/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+ACTION+'\n').encode())
        result=apply(a,out) if a.apply else verify(a,out);write(out/('APPLIED.json' if a.apply else 'COMPLETED.json'),result);print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,FileNotFoundError) else 2
        if out is not None:write(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        print('SKIP3_LDPRED2_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_LDPRED2_CATALOG_UNADMITTED',repr(error),ACTION);return code
if __name__=='__main__':raise SystemExit(main())
