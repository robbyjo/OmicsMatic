#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply or independently recheck one PoPS family and six source-specific roles."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from pops_catalog.primary import census
from pops_catalog.model import META,GUIDE,transition,check,registry,WARNING,NOTE
from pops_catalog.lineage import ROOT,BASE,EVIDENCE,PROOF,COMPATIBILITY,encoded,pin,rawpin,git,source_inventory,source_guard,qualify
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta

STATUS='PASS_SCOPED_NATIVE_AND_ORIGINAL_POPS_CATALOG_ADMISSION'
ACTION='Build current source. Missing permitted original-runtime prerequisites mean SKIP3 and dependent analysis skipped; forbidden installation stays skipped. FAIL2 admits no complete result. Retain inputs, raw reports and failed attempts; correct named causes and retry NEW paths. Never infer author settings, substitute editions/methods, repair nonfinite values or refresh old fingerprints. Source-score reuse is not a new fit. The full 250-paper objective remains active.'
if not __debug__:raise RuntimeError('Catalog/scientific assertions must remain enabled')
def write(path,value):
    with path.open('xb') as f:f.write(encoded(value))
def own_sources():return sorted([p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature/pops_catalog').glob('*.py')]+[GUIDE])
def compatibility(before,after):
    changes=[delta(name,before[name],after[name]) for name in META]
    for entry in changes:assert reverse_delta(after[entry['path']],entry)==before[entry['path']]
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='pops',scope='Exact inverse journal of the PoPS-only eight-file metadata transition; no scientific-source or Git overlay.',paths=changes)
def before_archive():
    with zipfile.ZipFile(ROOT/EVIDENCE/'catalog-before.zip') as z:
        assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(META);return {name:z.read(name) for name in META}
def apply(args,out):
    folder=ROOT/EVIDENCE;assert not folder.exists() and not (ROOT/PROOF).exists() and not (ROOT/COMPATIBILITY).exists()
    initial={name:pin(ROOT/name) for name in own_sources()};before={name:(ROOT/name).read_bytes() for name in META}
    for name,raw in before.items():assert raw==git('show',BASE+':'+name),name
    source=source_inventory();guard=source_guard(source);qout=out/'lineage';qout.mkdir();science=qualify(args,qout)
    primary=census(papers_before=json.loads(before[META[0]]));after=transition(before,primary);result=check(before,after,primary);assert len(registry())==485
    assert initial=={name:pin(ROOT/name) for name in own_sources()} and all((ROOT/name).read_bytes()==raw for name,raw in before.items())
    folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in before.items():z.writestr(name,raw)
    for name,value in [('SOURCE-TREE.json',source),('SOURCE-GUARD.json',guard),('QUALIFICATION.json',science),('CENSUS.json',primary),('REGISTRY.json',registry())]:write(folder/name,value)
    (folder/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());write(ROOT/COMPATIBILITY,compatibility(before,after))
    write(out/'PREPARED.json',dict(before={n:rawpin(v) for n,v in before.items()},after={n:rawpin(v) for n,v in after.items()},**result))
    for name,raw in after.items():(ROOT/name).write_bytes(raw)
    assert source_guard(source)==guard
    evidence={p.relative_to(ROOT).as_posix():pin(p) for p in folder.rglob('*') if p.is_file()};evidence[COMPATIBILITY]=pin(ROOT/COMPATIBILITY)
    proof=dict(status=STATUS,consumer_revision=1,**result,before={n:rawpin(v) for n,v in before.items()},after={n:pin(ROOT/n) for n in META},sources=initial,evidence=evidence,existing_source_guard=guard,old_verifiers_or_Git_overlaid=False,source_build_required=True,released_binary_contains_adapter=False,whole_paper_reproduced=False,source_scores_reuse_is_estimator_execution=False)
    write(ROOT/PROOF,proof);return proof
def verify(args,out):
    proof=json.loads((ROOT/PROOF).read_bytes());assert proof['status']==STATUS and proof['consumer_revision']==1
    assert set(own_sources())==set(proof['sources'])
    for group in ['sources','evidence','after']:
        for name,expected in proof[group].items():assert pin(ROOT/name)==expected,name
    before=before_archive();assert {n:rawpin(v) for n,v in before.items()}==proof['before']
    for name,raw in before.items():assert raw==git('show',BASE+':'+name),name
    primary=census(papers_before=json.loads(before[META[0]]));assert primary==json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes())
    after={n:(ROOT/n).read_bytes() for n in META};result=check(before,after,primary);assert compatibility(before,after)==json.loads((ROOT/COMPATIBILITY).read_bytes())
    assert registry()==json.loads((ROOT/EVIDENCE/'REGISTRY.json').read_bytes())
    source=json.loads((ROOT/EVIDENCE/'SOURCE-TREE.json').read_bytes());guard=source_guard(source);assert guard==json.loads((ROOT/EVIDENCE/'SOURCE-GUARD.json').read_bytes())
    qout=out/'lineage';qout.mkdir();science=qualify(args,qout);assert source_guard(source)==guard
    for name,expected in proof['after'].items():assert pin(ROOT/name)==expected,name
    return dict(status=STATUS,**result,current_source_guard=guard,complete_predecessor_catalog_reverified=True,primary_sources_rescanned=250,current_scientific_sources_match_snapshot=True,old_verifiers_or_Git_overlaid=False,fresh_scientific_execution=False,qualification=science,whole_paper_reproduced=False)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);m=p.add_mutually_exclusive_group(required=True);m.add_argument('--apply',action='store_true');m.add_argument('--verify',action='store_true')
    for name in ['workflow-checkout','catalog-checkout','catalog-parent-checkout','previous-parent-checkout','baseline-checkout']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        target=a.out.resolve();target.relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target;(out/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+ACTION+'\n').encode())
        result=apply(a,out) if a.apply else verify(a,out);write(out/('APPLIED.json' if a.apply else 'COMPLETED.json'),result);print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None:write(out/'FAILURE.json',dict(reason=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        print(('SKIP3' if code==3 else 'FAIL2')+'_Pops_CATALOG_UNADMITTED',repr(error),ACTION);return code
if __name__=='__main__':raise SystemExit(main())
