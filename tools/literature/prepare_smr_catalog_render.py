# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply exact SMR/HEIDI catalog presentation deltas after scientific admission."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();data=ROOT/'docs/literature/omics-2023-2026'
    assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists()
    proof=json.loads((ROOT/'docs/literature/smr-workflow-development/VALIDATION.json').read_bytes());assert proof['adapter_admitted'] and proof['implementation_credit']
    assert json.loads((data/'smr-role-coverage.json').read_bytes())['applied'];out.mkdir()
    (out/'before-tool-inventory.json').write_bytes((data/'tool-inventory.json').read_bytes())
    path=ROOT/'tools/literature/render_review.py';before=path.read_bytes();text=before.decode('utf8');(out/'before-render_review.py').write_bytes(before)
    replacements=[
        ('from dndscv_catalog import (load_catalog_registry,','from smr_catalog import (load_catalog_registry,'),
        ('from dndscv_catalog import soupx_metadata,','from smr_catalog import soupx_metadata,'),
        ('from fuma_catalog import fuma_metadata,','from smr_catalog import smr_metadata, SMR_WARNING, SMR_ACTIONS\nfrom fuma_catalog import fuma_metadata,'),
        ('        elif tool_id == "dndscv":','        elif tool_id in {"smr", "heidi"}:\n            tool.update(smr_metadata(ROOT,DATA,tool_id))\n        elif tool_id == "dndscv":'),
        ('if tool_id not in {"dndscv",','if tool_id not in {"smr", "heidi", "dndscv",'),
        ('write_text(DNDSCV_WARNING +','write_text(SMR_WARNING + DNDSCV_WARNING +'),
        ('warning_body = DNDSCV_ACTIONS +','warning_body = SMR_ACTIONS + DNDSCV_ACTIONS +')]
    for old,new in replacements:assert text.count(old)==1,old;text=text.replace(old,new)
    after=text.encode('utf8');row=inverse(before,after);row['path']=path.relative_to(ROOT).as_posix();descriptor=data/'smr-render-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='smr-and-heidi',scope='Two metadata providers and prominent warnings only; scientific source checks stay strict.',paths=[row]));path.write_bytes(after)
    readme=ROOT/'README.md';old=readme.read_bytes();(out/'before-README.md').write_bytes(old);text=old.decode('utf8')
    for a,b in [('78 new scoped integrations (17 native Java methods and 61 upstream','80 new scoped integrations (17 native Java methods and 63 upstream'),('adapters), or 81 including three existing adapters; 253 named tools still need','adapters), or 83 including three existing adapters; 251 named tools still need')]:assert text.count(a)==1;text=text.replace(a,b)
    marker='## License';assert text.count(marker)==1;text=text.replace(marker,'The [SMR/HEIDI guide](docs/literature/SMR_WORKFLOWS.md) covers the tested original\nSMR 1.3.1/1.4.3 adapter, native input and result audits, private runtime setup,\nand a runnable synthetic example. Read its version, overlap and capacity limits.\n\n'+marker);readme.write_bytes(text.encode('utf8'))
    import hashlib
    save(data/'smr-root-readme-evolution.json',dict(schema_version=1,status='passed-exact-README-documentation-evolution',path='README.md',before=dict(bytes=len(old),sha256=hashlib.sha256(old).hexdigest()),after=pin(readme),historical_archive=pin(data/'dndscv-catalog-support.zip'),scientific_source_exception_added=False))
    command=[sys.executable,str(path)]
    with (out/'render.stdout').open('xb') as a,(out/'render.stderr').open('xb') as b:r=subprocess.run(command,cwd=ROOT,stdout=a,stderr=b,timeout=1200)
    save(out/'render.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect retained renderer error; do not overwrite this attempt'
    prior={t['id']:t for t in json.loads((out/'before-tool-inventory.json').read_bytes())};current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};assert len(prior)==len(current)==390 and set(k for k in prior if prior[k]!=current[k])=={'smr','heidi'}
    counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,63,251)
    save(out/'COMPLETED.json',dict(status='passed-exact-two-tool-SMR-HEIDI-catalog-render',source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),path]},new_scoped_integrations=80,including_existing_adapters=83,missing=251,other_tool_rows_preserved=388,whole_goal_complete=False));print('Rendered80 new scoped integrations;388 other tool rows preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact catalog/source checks')
    main()
