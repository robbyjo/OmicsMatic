# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the complete preceding catalog and prepare an exact renderer-only metadata extension."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    paths=['tools/literature/render_review.py','tools/literature/check_review_ui.cjs','tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']+['docs/literature/omics-2023-2026/'+n for n in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','SHA256SUMS']]
    before={name:(ROOT/name).read_bytes() for name in paths}
    for name,raw in before.items():file=out/'before'/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(raw)
    name='tools/literature/render_review.py';raw=before[name];assert raw.count(b'from hifiasm_catalog import ')==8;updated=raw.replace(b'from hifiasm_catalog import ',b'from rsubread_catalog import ')
    old=b'hifiasm_metadata, HIFIASM_WARNING, HIFIASM_ACTIONS';assert updated.count(old)==1;updated=updated.replace(old,old+b', rsubread_metadata, RSUBREAD_WARNING, RSUBREAD_ACTIONS');nl=b'\r\n' if b'\r\n' in raw else b'\n';old=b'        elif tool_id == "hifiasm":';assert updated.count(old)==1;updated=updated.replace(old,b'        elif tool_id == "rsubread":'+nl+b'            tool.update(rsubread_metadata(ROOT,DATA))'+nl+old)
    for old,new in [(b'if tool_id not in {"hifiasm",',b'if tool_id not in {"rsubread", "hifiasm",'),(b'write_text(HIFIASM_WARNING +',b'write_text(RSUBREAD_WARNING + HIFIASM_WARNING +'),(b'warning_body = HIFIASM_ACTIONS +',b'warning_body = RSUBREAD_ACTIONS + HIFIASM_ACTIONS +')]:assert updated.count(old)==1;updated=updated.replace(old,new)
    descriptor=data/'rsubread-render-shared-source-compatibility.json';assert not descriptor.exists();row=inverse(raw,updated);row['path']=name
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='rsubread',scope='Only provider imports, one Rsubread availability branch, validation-note preservation and prominent warnings. No scientific source, assertion or allowed-path change.',paths=[row]));(ROOT/name).write_bytes(updated)
    save(out/'COMPLETED.json',dict(status='passed-prepared-Rsubread-renderer-only-catalog-delta',before_pins={n:pin(out/'before'/n) for n in paths},renderer=pin(ROOT/name),descriptor=pin(descriptor),scientific_verifiers_changed=False,whole_goal_complete=False));print('Retained prior catalog; prepared exact Rsubread renderer-only metadata extension')


if __name__=='__main__':main()
