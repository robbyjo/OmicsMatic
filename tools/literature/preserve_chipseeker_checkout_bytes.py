# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve new evidence/example bytes with a metadata-only exact source inverse."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    path=ROOT/'.gitattributes';before=path.read_bytes();data=ROOT/'docs/literature/omics-2023-2026'
    assert b'examples/chipseeker/' not in before
    (out/'gitattributes-before').write_bytes(before);(out/'SHA256SUMS-before').write_bytes((data/'SHA256SUMS').read_bytes())
    eol=b'\r\n' if before.count(b'\r\n')==before.count(b'\n') else b'\n'
    addition=eol+b'# Preserve the byte-pinned ChIPseeker evidence and executed examples.'+eol+b'docs/literature/chipseeker-annotation-development/** -text whitespace=cr-at-eol'+eol+b'examples/chipseeker/** -text whitespace=cr-at-eol'+eol
    after=before+addition;row=inverse(before,after);row['path']='.gitattributes';assert reverse_delta(after,row)==before
    document=data/'chipseeker-checkout-shared-source-compatibility.json';assert not document.exists()
    save(document,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='chipseeker',
        scope='Only two new byte-preservation namespaces and their explanatory comment. Exact prior attributes reconstruct; no scientific source guard or analysis changes.',paths=[row]))
    path.write_bytes(after);verify_source(ROOT,data,'.gitattributes',row['before_sha256'])
    manifest={p.name:pin(p)['sha256'] for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    prior=[line.split('  ',1)[1] for line in (data/'SHA256SUMS').read_text().splitlines()]
    order=[n for n in prior if n in manifest]+sorted(manifest.keys()-set(prior))
    (data/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order),encoding='utf-8',newline='\n')
    save(out/'COMPLETED.json',dict(status='passed-exact-checkout-metadata-inverse',attributes=pin(path),inverse=pin(document),source=pin(Path(__file__)),scientific_source_or_guard_changed=False))
    print('Added only ChIPseeker byte-preservation namespaces; prior attributes restore exactly')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact byte-preservation checks')
    main()
