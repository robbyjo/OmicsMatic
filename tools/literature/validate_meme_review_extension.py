# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refusal controls for the narrowly derived MEME metadata provider; alter no workspace input."""
import argparse
import copy
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from meme_discovery_provider_compatibility import validate_review_extension,verify_provider_derivation


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    prior=ROOT/'docs/literature/omics-2023-2026/chipseeker-prior-reviews.json.gz'
    base=gzip.decompress(prior.read_bytes()) if prior.exists() else (ROOT/'tools/literature/reviewed-uses.json').read_bytes();old=json.loads(base)
    drafts=json.loads((ROOT/'tools/literature/chipseeker-use-reviews.json').read_bytes());current=copy.deepcopy(old);current['reviews'].extend(d['review'] for d in drafts)
    encode=lambda x:(json.dumps(x,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    verify_provider_derivation(ROOT);assert validate_review_extension(base,encode(current),drafts)==current['reviews']
    cases=[]
    def refuse(name,change):
        candidate=copy.deepcopy(current);change(candidate)
        try:validate_review_extension(base,encode(candidate),drafts)
        except AssertionError:cases.append(dict(case=name,refused=True));return
        raise AssertionError('Metadata extension accepted altered input: '+name)
    refuse('old-review-modified',lambda x:x['reviews'][0].update(use_summary='ALTERED'))
    refuse('old-MEME-role-modified',lambda x:next(r for r in x['reviews'] if r['tool_id']=='meme').update(use_summary='ALTERED'))
    refuse('extra-MEME-role',lambda x:x['reviews'].append(dict(x['reviews'][0],tool_id='meme')))
    refuse('new-review-impersonates-MEME',lambda x:x['reviews'][898].update(tool_id='meme'))
    refuse('missing-prior-review',lambda x:x['reviews'].pop(2))
    refuse('prior-review-reordered',lambda x:x['reviews'].__setitem__(slice(0,2),list(reversed(x['reviews'][:2]))))
    refuse('unreviewed-extension-text',lambda x:x['reviews'][898].update(use_summary='ALTERED'))
    refuse('unrelated-root-field',lambda x:x.update(unreviewed_field=True))
    save(out/'COMPLETED.json',dict(status='passed-narrow-MEME-provider-derivation-and-review-refusal-controls',positive_cases=1,negative_cases=cases,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/meme_discovery_provider_compatibility.py',ROOT/'tools/literature/meme_discovery_current_catalog.py',ROOT/'tools/literature/meme_discovery_catalog.py',ROOT/'tools/literature/verify_meme_discovery_catalog.py']},workspace_reviews_modified=False,scientific_source_waiver_added=False))
    print('Exact derived provider and eight altered-review refusals pass; original MEME science/verifier untouched')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable metadata refusal checks')
    main()
