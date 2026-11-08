# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Make global catalog caches consistent before strict legacy provider checks; retain the failed first render."""
import argparse,json,sys
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from palantir_catalog import palantir_metadata

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/palantir-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';names=['tools/literature/render_review.py','docs/literature/omics-2023-2026/tool-inventory.json','docs/literature/omics-2023-2026/summary.json']
 for name in names:target=out/'before'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
 renderer=ROOT/names[0];raw=renderer.read_bytes();needle=b'        if tool_id not in {"cellphonedb",';assert raw.count(needle)==1;updated=raw.replace(needle,b'        if tool_id not in {"palantir", "cellphonedb",');row=inverse(raw,updated);row['path']=names[0];descriptor=data/'palantir-validation-note-shared-source-compatibility.json';assert not descriptor.exists();save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='palantir',scope='Retain the original adapter validation note in the Palantir metadata branch instead of the generic pending-integration text; no scientific-source change.',paths=[row]));renderer.write_bytes(updated)
 inventory=json.loads((data/'tool-inventory.json').read_bytes());papers=json.loads((data/'papers.json').read_bytes());definitions=json.loads((ROOT/'tools/literature/palantir-named-software-registry.json').read_bytes());assert len(inventory)==395 and not({r['id'] for r in definitions}&{r['id'] for r in inventory});counts=Counter(t['tool_id'] for p in papers for t in p['tools'])
 for d in definitions:
  tool=dict(d,paper_count=counts[d['id']],papers=[p['paper_id'] for p in papers if any(t['tool_id']==d['id'] for t in p['tools'])],implementation_status='missing',implementation_source=None,implementation_scope=d['coverage_note']);
  if d['id']=='palantir':tool.update(palantir_metadata(ROOT,data))
  inventory.append(tool)
 summary=json.loads((data/'summary.json').read_bytes());summary.update(distinct_named_tools=len(inventory),paper_tool_observations=sum(len(p['tools']) for p in papers),implementation_status_counts=dict(Counter(t['implementation_status'] for t in inventory)));assert (summary['distinct_named_tools'],summary['paper_tool_observations'])==(400,2465)
 for name,value in [('tool-inventory.json',inventory),('summary.json',summary)]:save(data/name,value)
 save(out/'COMPLETED.json',dict(status='passed-source-bound-current-catalog-bootstrap-before-full-render',reason='The unchanged FUMA provider requires summary/inventory/paper counts to agree before full rendering. First render correctly refused stale2457-observation cache.',source_pin=pin(Path(__file__)),renderer=pin(renderer),descriptor=pin(descriptor),inventory=pin(data/'tool-inventory.json'),summary=pin(data/'summary.json'),scientific_verifiers_changed=False,whole_goal_complete=False));print('Prepared consistent400-tool/2465-observation caches and retained Palantir validation metadata; full render still required')
if __name__=='__main__':main()
