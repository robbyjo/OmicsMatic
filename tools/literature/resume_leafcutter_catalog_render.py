# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resume only the recorded LeafCutter metadata audit/render after a failed wrapper conversion; retain the failed attempt and exact pre-delta baseline."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from catalog_source_integrity import verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/leafcutter-runtime';out=a.out.resolve();baseline=a.baseline.resolve();assert out.is_relative_to(base) and baseline.is_relative_to(base) and not out.exists();out.mkdir();data=ROOT/'docs/literature/omics-2023-2026';started=json.loads((baseline/'STARTED.json').read_bytes());failed=json.loads((baseline/'FAILED.json').read_bytes());assert failed['status']=='FAIL2_CATALOG_METADATA_ROOT_PATH_CONVERSION' and failed['render_executed'] is False and failed['scientific_source_or_results_changed'] is False and json.loads((baseline/'source-audit.process.json').read_bytes())['exit_code']==1 and not (baseline/'COMPLETED.json').exists()
    from leafcutter_workflow_evidence import verify
    assert verify(emit=False)['implementation_credit'] is True;before=started['before'];reviews=started['reviews'];assert pin(data/'papers.json')==before['papers.json'] and pin(ROOT/'tools/literature/reviewed-uses.json')==reviews and pin(data/'tool-inventory.json')==before['tool-inventory.json']
    for name in ['before-tool-inventory.json','before-SHA256SUMS','before-render_review.py','before-README.md']:(out/name).write_bytes((baseline/name).read_bytes())
    assert pin(out/'before-tool-inventory.json')==before['tool-inventory.json'];verify_source(ROOT,data,'tools/literature/render_review.py',pin(out/'before-render_review.py')['sha256']);assert json.loads((data/'leafcutter-root-readme-evolution.json').read_bytes())['after']==pin(ROOT/'README.md');save(out/'STARTED.json',dict(status='RESUMING_EXACT_METADATA_BASELINE_AFTER_WRAPPER_FIX',baseline=baseline.relative_to(ROOT).as_posix(),baseline_failure=pin(baseline/'FAILED.json'),before=before,reviews=reviews))
    for label,command in [('source-audit',[sys.executable,'-B',str(ROOT/'tools/literature/audit_leafcutter_catalog.py')]),('render',[sys.executable,'-B',str(ROOT/'tools/literature/render_review.py')])]:
        with (out/(label+'.stdout')).open('xb') as first,(out/(label+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1200,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect retained '+label+' failure; preserve this attempt and retry NEW paths'
    readme=data/'README.md';readme.write_bytes(readme.read_bytes().replace(b'\r\n',b'\n'));old={t['id']:t for t in json.loads((out/'before-tool-inventory.json').read_bytes())};current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};assert len(old)==len(current)==390 and {n for n in old if old[n]!=current[n]}=={'leafcutter'} and old['leafcutter']['implementation_status']=='missing' and current['leafcutter']['implementation_status']=='upstream-adapter-validated';counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,67,247);assert pin(data/'papers.json')==before['papers.json'] and pin(data/'loftee-source-audit.json')==before['loftee-source-audit.json'] and pin(ROOT/'tools/literature/reviewed-uses.json')==reviews
    save(out/'COMPLETED.json',dict(status='passed-exact-one-LeafCutter-availability-delta-with-six-reviewed-roles',source_pins={n:pin(ROOT/n) for n in ['tools/literature/resume_leafcutter_catalog_render.py','tools/literature/prepare_leafcutter_catalog_render.py','tools/literature/render_review.py','tools/literature/leafcutter_catalog.py','tools/literature/audit_leafcutter_catalog.py']},baseline_failed_attempt=baseline.relative_to(ROOT).as_posix(),baseline_failure=pin(baseline/'FAILED.json'),papers_pin=pin(data/'papers.json'),reviews_pin=reviews,new_scoped_integrations=84,including_existing_adapters=87,missing=247,unrelated_tool_rows_preserved=389,new_reviews_added=6,whole_goal_complete=False));print('Rendered84 new scoped integrations;389 other tool rows and960 prior reviews preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact metadata/source checks')
    main()
