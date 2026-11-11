# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the entire previous catalog and record an exact renderer-only metadata delta."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    paths=['tools/literature/render_review.py','tools/literature/check_review_ui.cjs','tools/literature/reviewed-uses.json','docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/tool-inventory.json','docs/literature/omics-2023-2026/summary.json','docs/literature/omics-2023-2026/README.md','docs/literature/omics-2023-2026/WARNINGS.md','docs/literature/omics-2023-2026/SHA256SUMS','site/literature-review.html','site/literature-warnings.html']
    before={n:(ROOT/n).read_bytes() for n in paths}
    for name,raw in before.items():copy=out/'before'/name;copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(raw)
    name='tools/literature/render_review.py';raw=before[name];assert raw.count(b'from gseapy_catalog import ')==8;updated=raw.replace(b'from gseapy_catalog import ',b'from hifiasm_catalog import ');old=b'gseapy_metadata, GSEAPY_WARNING, GSEAPY_ACTIONS';assert updated.count(old)==1;updated=updated.replace(old,old+b', hifiasm_metadata, HIFIASM_WARNING, HIFIASM_ACTIONS');newline=b'\r\n' if b'\r\n' in raw else b'\n';old=b'        elif tool_id == "gseapy":';assert updated.count(old)==1;updated=updated.replace(old,b'        elif tool_id == "hifiasm":'+newline+b'            tool.update(hifiasm_metadata(ROOT,DATA))'+newline+old)
    for old,new in [(b'if tool_id not in {"gseapy",',b'if tool_id not in {"hifiasm", "gseapy",'),(b'write_text(GSEAPY_WARNING +',b'write_text(HIFIASM_WARNING + GSEAPY_WARNING +'),(b'warning_body = GSEAPY_ACTIONS +',b'warning_body = HIFIASM_ACTIONS + GSEAPY_ACTIONS +')]:assert updated.count(old)==1,old;updated=updated.replace(old,new)
    descriptor=data/'hifiasm-render-shared-source-compatibility.json';assert not descriptor.exists();row=inverse(raw,updated);row['path']=name;save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='hifiasm',scope='Only metadata-provider imports, one hifiasm availability branch, validation-note preservation and prominent warnings. No scientific source or browser assertion changes.',paths=[row]));(ROOT/name).write_bytes(updated);save(out/'COMPLETED.json',dict(status='passed-prepared-hifiasm-renderer-only-catalog-delta',before_pins={n:pin(out/'before'/n) for n in paths},renderer=pin(ROOT/name),descriptor=pin(descriptor),scientific_verifiers_changed=False,whole_goal_complete=False));print('Preserved prior catalog and prepared exact hifiasm renderer-only delta')


if __name__=='__main__':main()
