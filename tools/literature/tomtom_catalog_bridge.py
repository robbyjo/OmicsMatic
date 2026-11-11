# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only the Tomtom provider and recovery sections, preserving exact historical renderer bytes."""
import argparse
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source,digest


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',type=Path,required=True);args=parser.parse_args();before=args.before.resolve();assert before.is_relative_to((ROOT/'build/literature-review/meme-runtime').resolve());path=ROOT/'tools/literature/render_review.py';old=before.read_bytes();data=ROOT/'docs/literature/omics-2023-2026';target=data/'tomtom-catalog-shared-source-compatibility.json'
    if target.exists():
        previous=target.read_bytes();record=json.loads(previous);assert len(record['paths'])==1 and reverse_delta(path.read_bytes(),record['paths'][0])==old
        for name,raw in [('initial-renderer-compatibility.json',previous),('renderer-at-first-ui-check.py',path.read_bytes())]:
            saved=before.parent/name;assert not saved.exists();saved.write_bytes(raw)
    else:assert path.read_bytes()==old
    new=old
    replacements=[(b'from salmon_current_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\n',b'from salmon_current_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\nfrom tomtom_catalog import tomtom_metadata, TOMTOM_WARNING, TOMTOM_ACTIONS\n'),(b'        elif tool_id == "salmon":\n            tool.update(salmon_metadata(ROOT, DATA))\n',b'        elif tool_id == "salmon":\n            tool.update(salmon_metadata(ROOT, DATA))\n        elif tool_id == "tomtom":\n            tool.update(tomtom_metadata(ROOT, DATA))\n'),(b'SALMON_WARNING + FASTP_WARNING',b'SALMON_WARNING + TOMTOM_WARNING + FASTP_WARNING'),(b'SALMON_ACTIONS + FASTP_ACTIONS',b'SALMON_ACTIONS + TOMTOM_ACTIONS + FASTP_ACTIONS'),(b'"nextflow", "salmon", "favor"',b'"nextflow", "salmon", "tomtom", "favor"')]
    for a,b in replacements:
        assert new.count(a)==(2 if a==b'SALMON_ACTIONS + FASTP_ACTIONS' else 1) and b not in new;new=new.replace(a,b)
    row=inverse(old,new);row['path']='tools/literature/render_review.py';assert len(row['edits'])==6 and reverse_delta(new,row)==old
    target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='tomtom',scope='Only one verified Tomtom metadata provider import/branch, preservation of its verified validation note and explicit recovery section in Markdown and both HTML pages. The complete prior renderer reconstructs exactly; no scientific source hash waiver or prior provider edit.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n');path.write_bytes(new);verify_source(ROOT,data,row['path'],digest(old));print('Six exact Tomtom catalog-only renderer edits verified; prior renderer reconstructs.')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact renderer reconstruction')
    main()
