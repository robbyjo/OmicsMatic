# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original phenotype script and supporting NumPy/scikit-learn source/notices. No analysis credit."""
import argparse,hashlib,json,traceback,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_LEAFCUTTER_PHENOTYPE_SUPPORT_SOURCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,records={})
    try:
        assert a.allow_download,'Required downloads forbidden for this invocation';original=ROOT/'build/literature-review/leafcutter-runtime/source-intake-2/source-2c9907ef66ad';report=json.loads((original/'COMPLETED.json').read_bytes());name='scripts/prepare_phenotype_table.py';raw=(original/'source'/name).read_bytes();want=report['members'][name];assert len(raw)==want['bytes'] and hashlib.sha256(raw).hexdigest()==want['sha256'];(out/'prepare_phenotype_table.py').write_bytes(raw);state['original_script']=dict(revision=report['revision'],**want)
        for repo,tag,label,members in [('numpy/numpy','v1.26.4','numpy',['numpy/core/src/umath/loops_utils.h.src','LICENSE.txt']),('scikit-learn/scikit-learn','1.3.2','sklearn',['sklearn/preprocessing/_data.py','COPYING'])]:
            home=out/label;home.mkdir();url='https://api.github.com/repos/'+repo+'/git/ref/tags/'+tag
            def fetch(url,path,maximum=1048576):
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-phenotype-source-intake'}),timeout=60) as r:data=r.read(maximum+1)
                assert len(data)<=maximum;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);return data
            ref=json.loads(fetch(url,home/'official-ref.json'));item=ref['object']
            if item['type']=='tag':item=json.loads(fetch(item['url'],home/'official-annotated-tag.json'))['object']
            assert item['type']=='commit';revision=item['sha'];records={}
            for member in members:
                url='https://raw.githubusercontent.com/'+repo+'/'+revision+'/'+member;path=home/member;fetch(url,path);records[member]=dict(url=url,**pin(path))
            state['records'][label]=dict(revision=revision,tag=tag,members=records)
        state.update(status='PASS_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_NUMERIC_SUPPORT_SOURCES_ONLY',source_pin=pin(Path(__file__)))
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Source/license identities only; no native/original phenotype analysis admitted.')+'\n\nRequired action: preserve failures and original source; restore exact permitted downloads into NEW paths. Do not change a source/hash to pass. Missing/forbidden exact sources keep dependent analysis skipped.\n',encoding='utf8',newline='\n');print(state['status']);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':main()
