# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the official Presto repository/tag metadata needed by the pinned intake."""
import argparse,json,traceback,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    state=dict(status='SKIP3_PRESTO_METADATA_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_download,'Download is not authorized; skip dependent intake'
        records={}
        for name,url in [('repository.json','https://api.github.com/repos/immunogenomics/presto'),('tags.json','https://api.github.com/repos/immunogenomics/presto/tags?per_page=100')]:
            request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-presto-intake/1.0'})
            with urllib.request.urlopen(request,timeout=90) as response:raw=response.read(16777217);record=dict(url=url,resolved_url=response.url,status=response.status)
            assert len(raw)<=16777216;(out/name).write_bytes(raw);records[name]=dict(**record,**pin(out/name))
        tags=json.loads((out/'tags.json').read_bytes());assert any(t['name']=='1.0.0' and t['commit']['sha']=='7636b3d0465c468c35853f82f1717d3a64b3c8f6' for t in tags)
        state.update(status='PASS_OFFICIAL_PRESTO_TAG_METADATA_ONLY',records=records,source=pin(Path(__file__)))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official source-tag checks')
    raise SystemExit(main())
