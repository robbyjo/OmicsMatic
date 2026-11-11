# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep corpus summary/inventory coherent before providers validate and render new identities."""
from collections import Counter
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    data=ROOT/'docs/literature/omics-2023-2026'; saved=ROOT/'build/literature-review/meme-runtime/role-curation-apply-1'
    registry={r['id']:r for r in json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes())}
    papers=json.loads((data/'papers.json').read_bytes())
    old=json.loads((data/'tool-inventory.json').read_bytes()); summary=json.loads((data/'summary.json').read_bytes())
    assert len(old)==380 and summary['distinct_named_tools']==380 and summary['paper_tool_observations']==2430
    by={t['id']:t for t in old}; counts=Counter(t['tool_id'] for p in papers for t in p['tools'])
    added={'meme-chip','dreme','centrimo','tomtom','streme','hyphy','bwa-meme'}
    assert set(counts)-set(by)==added and len(counts)==387 and sum(counts.values())==2442
    new=[]
    for key,count in counts.most_common():
        pids=[p['paper_id'] for p in papers if any(t['tool_id']==key for t in p['tools'])]
        if key in by:
            item=dict(by[key]);item.update(paper_count=count,papers=pids)
            if key not in {'bwa','meme'}:
                assert item==by[key]
        else:
            item=dict(registry[key],paper_count=count,papers=pids,implementation_status='missing',implementation_source=None,
                      implementation_scope=registry[key]['coverage_note'],validation_note='No typed integration or original scientific validation is credited by this program-identity correction.')
        new.append(item)
    statuses=dict(Counter(t['implementation_status'] for t in new))
    assert statuses['native-subset-validated']==15 and statuses['upstream-adapter-validated']==50 and statuses['existing-adapter']==3 and statuses['missing']==262
    summary.update(distinct_named_tools=387,paper_tool_observations=2442,implementation_status_counts=statuses)
    for name,value in [('tool-inventory.json',new),('summary.json',summary)]:
        snapshot=saved/('before-'+name+'.gz');assert not snapshot.exists()
        snapshot.write_bytes(gzip.compress((data/name).read_bytes(),mtime=0))
        (data/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print('Coherent387-tool/2442-observation summary staged; all65 new scoped integrations preserved;262 missing.')


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/count preservation checks')
    main()
