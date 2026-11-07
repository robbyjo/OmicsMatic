# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact renderer/UI inverses for scoped DESeq2 metadata and two recovered uses."""
import json
from pathlib import Path
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source,digest
ROOT=Path(__file__).resolve().parents[2]

def main():
    data=ROOT/'docs/literature/omics-2023-2026';rows=[]
    for name in ['tools/literature/render_review.py','tools/literature/check_review_ui.cjs']:
        old=(ROOT/'build/literature-review/deseq2-runtime/catalog-promotion-1/before'/name).read_bytes();path=ROOT/name;assert path.read_bytes()==old;new=old
        if name.endswith('.py'):
            edits=[(b'from chromvar_catalog import chromvar_metadata, CHROMVAR_WARNING, CHROMVAR_ACTIONS\n',b'from chromvar_catalog import chromvar_metadata, CHROMVAR_WARNING, CHROMVAR_ACTIONS\nfrom deseq2_catalog import deseq2_metadata, DESEQ2_WARNING, DESEQ2_ACTIONS\n'),(b'        elif tool_id == "chromvar":\n            tool.update(chromvar_metadata(ROOT, DATA))\n',b'        elif tool_id == "chromvar":\n            tool.update(chromvar_metadata(ROOT, DATA))\n        elif tool_id == "deseq2":\n            tool.update(deseq2_metadata(ROOT, DATA))\n'),(b'CHROMVAR_WARNING + FASTP_WARNING',b'CHROMVAR_WARNING + DESEQ2_WARNING + FASTP_WARNING'),(b'CHROMVAR_ACTIONS + FASTP_ACTIONS',b'CHROMVAR_ACTIONS + DESEQ2_ACTIONS + FASTP_ACTIONS'),(b'"chromvar", "favor"',b'"chromvar", "deseq2", "favor"')]
        else:edits=[(b'/2442 tool observations/',b'/2444 tool observations/')]
        for a,b in edits:assert new.count(a)==(2 if a==b'CHROMVAR_ACTIONS + FASTP_ACTIONS' else 1) and b not in new;new=new.replace(a,b)
        row=inverse(old,new);row['path']=name;assert len(row['edits'])==(6 if name.endswith('.py') else 1) and reverse_delta(new,row)==old;rows.append(row);path.write_bytes(new)
    target=data/'deseq2-catalog-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='deseq2',scope='Only six scoped renderer insertions and one UI observation-count correction2442 to2444 from two checked declarations. Every previous byte reconstructs; no scientific source waiver.',paths=rows),indent=2)+'\n',encoding='utf8',newline='\n')
    for row in rows:verify_source(ROOT,data,row['path'],row['before_sha256'])
    print('Six exact DESeq2 renderer edits and one observation-count assertion passed')

if __name__=='__main__':main()
