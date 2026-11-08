# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render one admitted Hail availability delta with eight exact source-role updates and prominent recovery actions."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from signac_scan_evidence import unpack,byte_pin

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    from hail_workflow_evidence import verify
    proof=verify(emit=False);assert proof['implementation_credit'] and proof['admitted_named_tools']==['hail'];before={n:pin(data/n) for n in ['papers.json','tool-inventory.json','summary.json','loftee-source-audit.json']};reviews=pin(ROOT/'tools/literature/reviewed-uses.json');(out/'before-tool-inventory.json').write_bytes((data/'tool-inventory.json').read_bytes());save(out/'STARTED.json',dict(status='RUNNING_EXACT_HAIL_CATALOG_METADATA_DELTA',before=before,reviews=reviews))
    path=ROOT/'tools/literature/render_review.py';raw=path.read_bytes();(out/'before-render_review.py').write_bytes(raw);s=raw.decode('utf8')
    assert s.count('from biomart_catalog import ')==5;s=s.replace('from biomart_catalog import ','from hail_catalog import ')
    changes=[('from hail_catalog import biomart_metadata, BIOMART_WARNING, BIOMART_ACTIONS','from hail_catalog import biomart_metadata, BIOMART_WARNING, BIOMART_ACTIONS\nfrom hail_catalog import hail_metadata, HAIL_WARNING, HAIL_ACTIONS'),('        elif tool_id == "biomart":','        elif tool_id == "hail":\n            tool.update(hail_metadata(ROOT,DATA))\n        elif tool_id == "biomart":'),('if tool_id not in {"biomart",','if tool_id not in {"hail", "biomart",'),('write_text(BIOMART_WARNING +','write_text(HAIL_WARNING + BIOMART_WARNING +'),('warning_body = BIOMART_ACTIONS +','warning_body = HAIL_ACTIONS + BIOMART_ACTIONS +')]
    for old,new in changes:assert s.count(old)==1,old;s=s.replace(old,new)
    updated=s.encode('utf8');row=inverse(raw,updated);row['path']=path.relative_to(ROOT).as_posix();descriptor=data/'hail-render-shared-source-compatibility.json';assert not descriptor.exists();save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='hail',scope='One availability provider and prominent warning/action presentation. No scientific source exception.',paths=[row]));path.write_bytes(updated)
    readme=ROOT/'README.md';raw=readme.read_bytes();(out/'before-README.md').write_bytes(raw);s=raw.decode('utf8')
    for old,new in [('82 new scoped integrations (17 native Java methods and 65 upstream','83 new scoped integrations (17 native Java methods and 66 upstream'),('adapters), or 85 including three existing adapters; 249 named tools still need','adapters), or 86 including three existing adapters; 248 named tools still need')]:assert s.count(old)==1;s=s.replace(old,new)
    marker='## License';assert s.count(marker)==1;s=s.replace(marker,'The [Hail workflow guide](docs/literature/HAIL_WORKFLOWS.md) covers original\nQC/filtering, MatrixTable reuse, LD/PCA, liftover and LOCAL Batch DAGs, with a\nseparate exact private installer and prominent skip/failure recovery. The native\nJava sample-QC component needs no Hail runtime. Cloud execution remains skipped.\n\n'+marker);readme.write_bytes(s.encode('utf8'));history=[]
    for name in ['biomart-catalog-support.zip','loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip']:
        old=json.loads((data/name.replace('-support.zip','-validation.json')).read_bytes());history.append(dict(archive=name,archive_pin=pin(data/name),document=byte_pin(unpack(data/name,old)['current-source/README.md'])))
    save(data/'hail-root-readme-evolution.json',dict(schema_version=1,status='passed-exact-README-documentation-evolution',path='README.md',after=pin(readme),historical_documents=history,scientific_source_exception_added=False))
    for label,command in [('source-audit',[sys.executable,str(ROOT/'tools/literature/audit_hail_catalog.py')]),('render',[sys.executable,str(path)])]:
        with (out/(label+'.stdout')).open('xb') as first,(out/(label+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdout=first,stderr=second,timeout=1200)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect retained '+label+' failure; do not overwrite this attempt'
    # Retain each baseline text format: data README/SHA manifest LF; generated data/site CRLF.
    readme=data/'README.md';readme.write_bytes(readme.read_bytes().replace(b'\r\n',b'\n'))
    old={t['id']:t for t in json.loads((out/'before-tool-inventory.json').read_bytes())};current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};assert len(old)==len(current)==390 and {n for n in old if old[n]!=current[n]}=={'hail'} and old['hail']['implementation_status']=='missing' and current['hail']['implementation_status']=='upstream-adapter-validated';counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,66,248);assert pin(data/'papers.json')==before['papers.json'] and pin(data/'loftee-source-audit.json')==before['loftee-source-audit.json'] and pin(ROOT/'tools/literature/reviewed-uses.json')==reviews;save(out/'COMPLETED.json',dict(status='passed-exact-one-Hail-availability-delta-with-eight-reviewed-roles',source_pins={n:pin(ROOT/n) for n in ['tools/literature/prepare_hail_catalog_render.py','tools/literature/render_review.py','tools/literature/hail_catalog.py','tools/literature/audit_hail_catalog.py']},papers_pin=pin(data/'papers.json'),reviews_pin=reviews,new_scoped_integrations=83,including_existing_adapters=86,missing=248,unrelated_tool_rows_preserved=389,new_reviews_added=8,whole_goal_complete=False));print('Rendered83 new scoped integrations;389 other tool rows and952 prior reviews preserved')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact catalog/source checks')
    main()
