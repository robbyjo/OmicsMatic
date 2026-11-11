# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Insert only the verified native bigWig provider and recovery sections; reconstruct the old renderer exactly."""
import argparse
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source,digest


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path,required=True);args=parser.parse_args();before=args.before.resolve();assert before.is_relative_to((ROOT/'build/literature-review/bigwig-native').resolve());path=ROOT/'tools/literature/render_review.py';old=before.read_bytes();assert path.read_bytes()==old;new=old
    replacements=[(b'from tomtom_catalog import tomtom_metadata, TOMTOM_WARNING, TOMTOM_ACTIONS\n',b'from tomtom_catalog import tomtom_metadata, TOMTOM_WARNING, TOMTOM_ACTIONS\nfrom bigwig_catalog import bigwig_metadata, BIGWIG_WARNING, BIGWIG_ACTIONS\n'),(b'        elif tool_id == "tomtom":\n            tool.update(tomtom_metadata(ROOT, DATA))\n',b'        elif tool_id == "tomtom":\n            tool.update(tomtom_metadata(ROOT, DATA))\n        elif tool_id == "ucsc-bedgraphtobigwig":\n            tool.update(bigwig_metadata(ROOT, DATA))\n'),(b'TOMTOM_WARNING + FASTP_WARNING',b'TOMTOM_WARNING + BIGWIG_WARNING + FASTP_WARNING'),(b'TOMTOM_ACTIONS + FASTP_ACTIONS',b'TOMTOM_ACTIONS + BIGWIG_ACTIONS + FASTP_ACTIONS'),(b'"tomtom", "favor"',b'"tomtom", "ucsc-bedgraphtobigwig", "favor"')]
    for a,b in replacements:assert new.count(a)==(2 if a==b'TOMTOM_ACTIONS + FASTP_ACTIONS' else 1) and b not in new;new=new.replace(a,b)
    row=inverse(old,new);row['path']='tools/literature/render_review.py';assert len(row['edits'])==6 and reverse_delta(new,row)==old;data=ROOT/'docs/literature/omics-2023-2026';target=data/'bigwig-catalog-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='ucsc-bedgraphtobigwig',scope='Only native bigWig metadata import/branch, preservation of its verified validation note and explicit recovery guidance in Markdown and both HTML pages. Complete earlier renderer bytes reconstruct; no scientific source exception or prior provider change.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n');path.write_bytes(new);verify_source(ROOT,data,row['path'],digest(old));print('Six exact native bigWig catalog-only renderer edits verified; previous renderer reconstructs.')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact catalog-only reconstruction')
    main()
