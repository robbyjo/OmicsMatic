# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Insert only the chromVAR provider/recovery sections and retain the exact previous renderer bytes."""
import json
from pathlib import Path
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source,digest
ROOT=Path(__file__).resolve().parents[2]
def main():
    name='tools/literature/render_review.py';before=ROOT/'build/literature-review/signac-runtime/chromvar-catalog-promotion-1/before'/name;old=before.read_bytes();path=ROOT/name;assert path.read_bytes()==old;new=old
    edits=[(b'from stringtie_catalog import stringtie_metadata, STRINGTIE_WARNING, STRINGTIE_ACTIONS\n',b'from stringtie_catalog import stringtie_metadata, STRINGTIE_WARNING, STRINGTIE_ACTIONS\nfrom chromvar_catalog import chromvar_metadata, CHROMVAR_WARNING, CHROMVAR_ACTIONS\n'),(b'        elif tool_id == "stringtie":\n            tool.update(stringtie_metadata(ROOT, DATA))\n',b'        elif tool_id == "stringtie":\n            tool.update(stringtie_metadata(ROOT, DATA))\n        elif tool_id == "chromvar":\n            tool.update(chromvar_metadata(ROOT, DATA))\n'),(b'STRINGTIE_WARNING + FASTP_WARNING',b'STRINGTIE_WARNING + CHROMVAR_WARNING + FASTP_WARNING'),(b'STRINGTIE_ACTIONS + FASTP_ACTIONS',b'STRINGTIE_ACTIONS + CHROMVAR_ACTIONS + FASTP_ACTIONS'),(b'"stringtie", "favor"',b'"stringtie", "chromvar", "favor"')]
    for a,b in edits:assert new.count(a)==(2 if a==b'STRINGTIE_ACTIONS + FASTP_ACTIONS' else 1) and b not in new;new=new.replace(a,b)
    row=inverse(old,new);row['path']=name;assert len(row['edits'])==6 and reverse_delta(new,row)==old;data=ROOT/'docs/literature/omics-2023-2026';target=data/'chromvar-catalog-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='chromvar',scope='Only scoped provider import/branch, preservation of its validation note and recovery guidance in Markdown/both HTML pages. Exact previous renderer reconstructs; no scientific source waiver.',paths=[row]),indent=2)+'\n',encoding='utf8',newline='\n');path.write_bytes(new);verify_source(ROOT,data,name,digest(old));print('Six exact chromVAR catalog-only renderer edits passed')
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog-only source reconstruction')
    main()
