# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Metadata-only checksum and exact-inverse helpers for ChIPseeker catalog integration."""
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import ALLOWED_PATHS,reverse_delta


def refresh_checksums(data=None):
    data=Path(data) if data else ROOT/'docs/literature/omics-2023-2026'
    target=data/'SHA256SUMS';prior=[line.split('  ',1)[1] for line in target.read_text(encoding='utf-8').splitlines()]
    current={p.name:pin(p)['sha256'] for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    order=[n for n in prior if n in current]+sorted(current.keys()-set(prior));assert len(order)==len(set(order))==len(current)
    target.write_text(''.join(current[n]+'  '+n+'\n' for n in order),encoding='utf-8',newline='\n')
    return len(order)


def write_metadata_delta(path,before,after,document,scope):
    relative=path.relative_to(ROOT).as_posix();assert relative in ALLOWED_PATHS and path.read_bytes()==before and not document.exists()
    row=inverse(before,after);row['path']=relative;assert reverse_delta(after,row)==before
    save(document,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='chipseeker',scope=scope,paths=[row]))
    path.write_bytes(after)


if __name__=='__main__':print(refresh_checksums())
