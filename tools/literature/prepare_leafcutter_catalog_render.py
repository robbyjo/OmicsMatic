# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render one admitted LeafCutter availability delta with six exact source-role updates and prominent recovery actions."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from signac_scan_evidence import unpack,byte_pin

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    from leafcutter_workflow_evidence import verify
    proof=verify(emit=False);assert proof['implementation_credit'] and proof['admitted_named_tools']==['leafcutter'];before={n:pin(data/n) for n in ['papers.json','tool-inventory.json','summary.json','loftee-source-audit.json']};reviews=pin(ROOT/'tools/literature/reviewed-uses.json');(out/'before-tool-inventory.json').write_bytes((data/'tool-inventory.json').read_bytes());save(out/'STARTED.json',dict(status='RUNNING_EXACT_HAIL_CATALOG_METADATA_DELTA',before=before,reviews=reviews))
    (out/'before-SHA256SUMS').write_bytes((data/'SHA256SUMS').read_bytes())
    path=ROOT/'tools/literature/render_review.py';raw=path.read_bytes();(out/'before-render_review.py').write_bytes(raw);s=raw.decode('utf8')
    assert s.count('from hail_catalog import ')==6;s=s.replace('from hail_catalog import ','from leafcutter_catalog import ')
    changes=[('from leafcutter_catalog import hail_metadata, HAIL_WARNING, HAIL_ACTIONS','from leafcutter_catalog import hail_metadata, HAIL_WARNING, HAIL_ACTIONS\nfrom leafcutter_catalog import leafcutter_metadata, LEAFCUTTER_WARNING, LEAFCUTTER_ACTIONS'),('        elif tool_id == "hail":','        elif tool_id == "leafcutter":\n            tool.update(leafcutter_metadata(ROOT,DATA))\n        elif tool_id == "hail":'),('if tool_id not in {"hail",','if tool_id not in {"leafcutter", "hail",'),('write_text(HAIL_WARNING +','write_text(LEAFCUTTER_WARNING + HAIL_WARNING +'),('warning_body = HAIL_ACTIONS +','warning_body = LEAFCUTTER_ACTIONS + HAIL_ACTIONS +')]
    for old,new in changes:assert s.count(old)==1,old;s=s.replace(old,new)
    updated=s.encode('utf8');row=inverse(raw,updated);row['path']=path.relative_to(ROOT).as_posix();descriptor=data/'leafcutter-render-shared-source-compatibility.json';assert not descriptor.exists();save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='leafcutter',scope='One availability provider and prominent warning/action presentation. No scientific source exception.',paths=[row]));path.write_bytes(updated)
    readme=ROOT/'README.md';raw=readme.read_bytes();(out/'before-README.md').write_bytes(raw);s=raw.decode('utf8')
    for old,new in [('83 new scoped integrations (17 native Java methods and 66 upstream','84 new scoped integrations (17 native Java methods and 67 upstream'),('adapters), or 86 including three existing adapters; 248 named tools still need','adapters), or 87 including three existing adapters; 247 named tools still need')]:assert s.count(old)==1;s=s.replace(old,new)
    marker='## License';assert s.count(marker)==1;s=s.replace(marker,'The [LeafCutter workflow guide](docs/literature/LEAFCUTTER_WORKFLOWS.md) covers\noriginal junction clustering, phenotype/PCA preparation and differential-splicing\nfits, with native Java audits, a separate exact private installer and explicit\ncase-sensitive filesystem, skipped-analysis and atomic-failure recovery.\n\n'+marker);readme.write_bytes(s.encode('utf8'));history=[]
    for name in ['hail-catalog-support.zip','biomart-catalog-support.zip','loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip']:
        old=json.loads((data/name.replace('-support.zip','-validation.json')).read_bytes());history.append(dict(archive=name,archive_pin=pin(data/name),document=byte_pin(unpack(data/name,old)['current-source/README.md'])))
    save(data/'leafcutter-root-readme-evolution.json',dict(schema_version=1,status='passed-exact-README-documentation-evolution',path='README.md',after=pin(readme),historical_documents=history,scientific_source_exception_added=False))
    for label,command in [('source-audit',[sys.executable,str(ROOT/'tools/literature/audit_leafcutter_catalog.py')]),('render',[sys.executable,str(path)])]:
        with (out/(label+'.stdout')).open('xb') as first,(out/(label+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdout=first,stderr=second,timeout=1200)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect retained '+label+' failure; do not overwrite this attempt'
    # Retain each baseline text format: data README/SHA manifest LF; generated data/site CRLF.
    readme=data/'README.md';readme.write_bytes(readme.read_bytes().replace(b'\r\n',b'\n'))
    old={t['id']:t for t in json.loads((out/'before-tool-inventory.json').read_bytes())};current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};assert len(old)==len(current)==390 and {n for n in old if old[n]!=current[n]}=={'leafcutter'} and old['leafcutter']['implementation_status']=='missing' and current['leafcutter']['implementation_status']=='upstream-adapter-validated';counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,67,247);assert pin(data/'papers.json')==before['papers.json'] and pin(data/'loftee-source-audit.json')==before['loftee-source-audit.json'] and pin(ROOT/'tools/literature/reviewed-uses.json')==reviews;save(out/'COMPLETED.json',dict(status='passed-exact-one-LeafCutter-availability-delta-with-six-reviewed-roles',source_pins={n:pin(ROOT/n) for n in ['tools/literature/prepare_leafcutter_catalog_render.py','tools/literature/render_review.py','tools/literature/leafcutter_catalog.py','tools/literature/audit_leafcutter_catalog.py']},papers_pin=pin(data/'papers.json'),reviews_pin=reviews,new_scoped_integrations=84,including_existing_adapters=87,missing=247,unrelated_tool_rows_preserved=389,new_reviews_added=6,whole_goal_complete=False));print('Rendered84 new scoped integrations;389 other tool rows and960 prior reviews preserved')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact catalog/source checks')
    main()
