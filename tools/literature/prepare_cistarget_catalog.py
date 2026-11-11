# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare exact renderer-only source evolution and publish validated root artifacts."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/cistarget-runtime'
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    routed=json.loads((base/'catalog-root-validation-2/COMPLETED.json').read_bytes());assert routed['status']=='PASS108_COMPLETE_CISTARGET_ROOT_FLOWS_12_PREDECESSOR_FLOWS_AND72_DISPATCH_CONTRACTS'
    for name in ['tool-inventory.json','papers.json','summary.json','README.md','WARNINGS.md']:(out/name).write_bytes((data/name).read_bytes())
    (out/'reviewed-uses.json').write_bytes((ROOT/'tools/literature/reviewed-uses.json').read_bytes())
    name='tools/literature/render_review.py';path=ROOT/name;before=path.read_bytes();text=before.decode('utf8');assert text.count('from mrmega_catalog import')==10
    text=text.replace('from mrmega_catalog import','from cistarget_catalog import')
    marker='from cistarget_catalog import mrmega_metadata, MRMEGA_WARNING, MRMEGA_ACTIONS\n';assert text.count(marker)==1
    text=text.replace(marker,'from cistarget_catalog import cistarget_metadata, CISTARGET_WARNING, CISTARGET_ACTIONS\n'+marker)
    marker='        elif tool_id == "mrmega":\n';assert text.count(marker)==1
    text=text.replace(marker,'        elif tool_id == "cistarget":\n            tool.update(cistarget_metadata(ROOT,DATA))\n'+marker)
    for old,new in [('if tool_id not in {"mrmega",','if tool_id not in {"cistarget", "mrmega",'),('write_text(MRMEGA_WARNING +','write_text(CISTARGET_WARNING + MRMEGA_WARNING +'),('warning_body = MRMEGA_ACTIONS +','warning_body = CISTARGET_ACTIONS + MRMEGA_ACTIONS +')]:
        assert text.count(old)==1; text=text.replace(old,new)
    compile(text,name,'exec');after=text.encode();row=inverse(before,after);row['path']=name;assert reverse_delta(after,row)==before
    descriptor=data/'cistarget-renderer-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='cistarget',scope='Ten imports use a source-preserving review-context wrapper; add only cisTarget metadata/validation-note handling and prominent warnings in Markdown/both HTML pages. Exact previous renderer reconstructs; no scientific-source exception.',paths=[row]))
    (out/'render_review.before.py').write_bytes(before);(out/'render_review.after.py').write_bytes(after);path.write_bytes(after);verify_source(ROOT,data,name,row['before_sha256'])
    home=ROOT/'docs/literature/cistarget-catalog-development';assert not home.exists();home.mkdir();published={}
    for compiler,folder in [('javac21','catalog-root-build21-1'),('javac25','catalog-root-build25-1')]:
        record=json.loads((base/folder/'COMPLETED.json').read_bytes());source=ROOT/record['artifact']['path'];assert pin(source)=={k:record['artifact'][k] for k in ['bytes','sha256']}
        dest=home/('cistarget-root-'+compiler+'.jar');dest.write_bytes(source.read_bytes());published[dest.relative_to(ROOT).as_posix()]=pin(dest)
    save(out/'COMPLETED.json',dict(status='PASS_CISTARGET_RENDERER_DERIVATION_AND_VALIDATED_ROOT_PUBLICATION_ONLY',source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),path,descriptor]},published_root_artifacts=published,
        predecessor_provider_files_changed=False,scientific_source_exceptions_added=False,whole_goal_complete=False));print('Prepared cisTarget renderer/current-review context and published source-preserving root supplements')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact renderer/artifact checks')
    main()
