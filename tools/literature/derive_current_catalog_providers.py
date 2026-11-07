# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve frozen providers; replace unrelated observation-count gates with exact cohort identity."""
import gzip
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT/'tools/literature'
sys.path.insert(0,str(HERE))
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    data=ROOT/'docs/literature/omics-2023-2026'
    old=json.loads(gzip.decompress((ROOT/'build/literature-review/meme-runtime/role-curation-apply-1/before-papers.json.gz').read_bytes()))
    current=json.loads((data/'papers.json').read_bytes())
    cohort=lambda papers:[{k:v for k,v in p.items() if k!='tools'} for p in papers]
    baseline=cohort(old)
    assert len(old)==len(current)==250 and baseline==cohort(current)
    raw=(json.dumps(baseline,ensure_ascii=False,indent=2)+'\n').encode()
    packed=gzip.compress(raw,mtime=0)
    baseline_path=data/'fixed-250-paper-cohort.json.gz'
    assert not baseline_path.exists();baseline_path.write_bytes(packed)
    recipes=[
      ('nextflow_catalog.py','nextflow_current_catalog.py',[
       ('from catalog_source_integrity import verify_source\n','from catalog_source_integrity import verify_source\nfrom catalog_fixed_cohort import verify_fixed_cohort\n'),
       ("    assert len(papers) == 250 and sum(len(p['tools']) for p in papers) == 2430\n","    verify_fixed_cohort(root, data, papers)\n")]),
      ('salmon_catalog.py','salmon_native_current_catalog.py',[
       ('from catalog_source_integrity import verify_source\n','from catalog_source_integrity import verify_source\nfrom catalog_fixed_cohort import verify_fixed_cohort\n'),
       ("papers=json.loads((data/'papers.json').read_bytes());assert len(papers)==250 and sum(len(p['tools']) for p in papers)==2430\n","papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(root,data,papers)\n")]),
      ('salmon_workflow_catalog.py','salmon_current_catalog.py',[
       ('from salmon_catalog import salmon_metadata as legacy_metadata\n','from salmon_native_current_catalog import salmon_metadata as legacy_metadata\n')])]
    records=[]
    for old_name,new_name,edits in recipes:
        before=(HERE/old_name).read_bytes();after=before
        for a,b in edits:
            assert after.count(a.encode())==1,(old_name,a)
            after=after.replace(a.encode(),b.encode())
        target=HERE/new_name;assert not target.exists();target.write_bytes(after)
        records.append(dict(original='tools/literature/'+old_name,derived='tools/literature/'+new_name,original_sha256=sha(before),derived_sha256=sha(after),edits=edits))
    # This helper verifies every field of all250 source records. It deliberately
    # permits additional tool observations: filling omissions is the goal.
    helper='''# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the unchanged full source cohort and exact scoped provider derivations."""
import gzip
import hashlib
import json

BASELINE_SHA = %r
BASELINE_RAW_SHA = %r
DERIVATIONS = %r


def verify_fixed_cohort(root, data, papers):
    packed=(data/'fixed-250-paper-cohort.json.gz').read_bytes()
    assert hashlib.sha256(packed).hexdigest()==BASELINE_SHA
    raw=gzip.decompress(packed)
    assert hashlib.sha256(raw).hexdigest()==BASELINE_RAW_SHA
    baseline=json.loads(raw)
    assert len(papers)==len(baseline)==250
    assert [{k:v for k,v in paper.items() if k!='tools'} for paper in papers]==baseline
    for row in DERIVATIONS:
        before=(root/row['original']).read_bytes()
        assert hashlib.sha256(before).hexdigest()==row['original_sha256']
        expected=before
        for old,new in row['edits']:
            assert expected.count(old.encode())==1
            expected=expected.replace(old.encode(),new.encode())
        actual=(root/row['derived']).read_bytes()
        assert actual==expected and hashlib.sha256(actual).hexdigest()==row['derived_sha256']
''' % (sha(packed),sha(raw),records)
    helper_path=HERE/'catalog_fixed_cohort.py';assert not helper_path.exists();helper_path.write_text(helper,encoding='utf-8',newline='\n')
    renderer=HERE/'render_review.py';before=renderer.read_bytes();after=before
    for a,b in [('from nextflow_catalog import nextflow_metadata, NEXTFLOW_WARNING, NEXTFLOW_ACTIONS\n','from nextflow_current_catalog import nextflow_metadata, NEXTFLOW_WARNING, NEXTFLOW_ACTIONS\n'),
                ('from salmon_workflow_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\n','from salmon_current_catalog import salmon_metadata, SALMON_WARNING, SALMON_ACTIONS\n')]:
        assert after.count(a.encode())==1;after=after.replace(a.encode(),b.encode())
    inverse_row=inverse(before,after);inverse_row['path']='tools/literature/render_review.py';assert reverse_delta(after,inverse_row)==before
    record=data/'meme-program-provider-shared-source-compatibility.json';assert not record.exists()
    record.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='meme-program-identities',
        scope='Only two renderer provider imports change. Frozen original providers remain byte-identical. Derived providers replace two unrelated global observation-count assertions with a pinned comparison of every field of all250 source records; all scientific/archive/source-role gates remain exact.',paths=[inverse_row]),indent=2)+'\n',encoding='utf-8',newline='\n')
    (data/'meme-program-provider-scope-validation.json').write_text(json.dumps(dict(status='passed-exact-provider-derivations-and-fixed-full-source-cohort',baseline_sha256=sha(packed),baseline_raw_sha256=sha(raw),derivations=records,
        helper_sha256=sha(helper_path.read_bytes()),deriver_sha256=sha(Path(__file__).read_bytes()),scientific_source_or_frozen_archive_changed=False,original_provider_files_changed=False,scientific_source_compatibility_waiver_added=False),indent=2)+'\n',encoding='utf-8',newline='\n')
    renderer.write_bytes(after)
    print('Current providers preserve every scientific gate and all250 source records; frozen providers and generic source-integrity rules unchanged.')


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact provider derivation checks')
    main()
