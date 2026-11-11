# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the single catalog-only import selecting two audited historical shared-build contexts."""
import json
from pathlib import Path
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source,digest
ROOT=Path(__file__).resolve().parents[2]
def main():
    name='tools/literature/render_review.py';path=ROOT/name;old=path.read_bytes();marker=b'from quantification_catalog import quantification_metadata, QUANT_WARNING, QUANT_ACTIONS\n';addition=b'# Contextualize only two historical shared-build guards; scientific checks stay strict.\nfrom catalog_shared_build_context import fuma_metadata, mashr_metadata\n';assert old.count(marker)==1 and addition not in old;new=old.replace(marker,marker+addition);row=inverse(old,new);row['path']=name;assert len(row['edits'])==1 and reverse_delta(new,row)==old;data=ROOT/'docs/literature/omics-2023-2026';target=data/'shared-build-catalog-shared-source-compatibility.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',scope='Only one explicit metadata import rebinds two named provider namespaces with strict scientific-class/resource/dependency audits. No original provider/scientific source is changed; exact prior renderer reconstructs.',paths=[row]),indent=2)+'\n',encoding='utf8',newline='\n');out=ROOT/'build/literature-review/signac-runtime/chromvar-catalog-promotion-1';(out/'render-before-shared-context.py').write_bytes(old);path.write_bytes(new);verify_source(ROOT,data,name,digest(old));print('One exact metadata-context renderer insertion verified')
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact context import checks')
    main()
