# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete typed-adapter raw tables with separately executed original KING reports."""
import argparse
import collections
import gzip
import json
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE


def payload(path):
    raw=path.read_bytes()
    if path.suffix=='.gz':raw=gzip.decompress(raw)
    return raw


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[]
    state=dict(status='FAIL2_COMPLETE_ORIGINAL_KING_REPORT_PARITY',new_catalog_credit=False,whole_goal_complete=False)
    try:
        for suite in ['king-adapter-gates-4','king-adapter-gates-25-1']:
            gates=json.loads((BASE/suite/'COMPLETED.json').read_text());assert gates['status'].startswith('PASS_')
            for platform in ['windows','linux']:
                for i,name in [(0,'kinship'),(1,'kinship-degree2'),(2,'kinship-degree3'),(3,'unrelated-degree2'),
                               (4,'unrelated-degree3'),(5,'duplicate'),(6,'appended-duplicate'),(8,'ibdseg')]:
                    if platform=='windows' and name=='ibdseg':continue
                    bundle=next((BASE/suite).glob(platform+'-'+str(i)+'-*.bundle'))
                    direct=BASE/'king-workflows-2'/(platform+'-'+name)
                    first={f.name:f for f in direct.glob('original*') if f.is_file()}
                    second={f.name:f for f in (bundle/'results').glob('original*') if f.is_file()}
                    assert first.keys()==second.keys(),(suite,platform,name,'Complete original file population differs')
                    for member in sorted(first):
                        left,right=payload(first[member]),payload(second[member])
                        assert left==right,(suite,platform,name,member,'Original complete payload differs')
                    records.append(dict(suite=suite,platform=platform,mode=name,files=list(sorted(first)),
                                        complete_original_payload_equal=True,original_table_pins={f:pin(first[f]) for f in first},
                                        adapter_table_pins={f:pin(second[f]) for f in second}))
        state.update(status='PASS_COMPLETE_ORIGINAL_KING_REPORT_POPULATIONS_AND_BYTES',records=records,producer=pin(Path(__file__)),
                     gzip_payloads_compared_after_decompression=True,raw_files_rewritten=False)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],len(records),state.get('error','')[:1600],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original-report population and byte checks')
    raise SystemExit(main())
