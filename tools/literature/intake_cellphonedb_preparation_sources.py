# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the actual pandas2.2.3 group-mean implementation and BSD notice used by CellPhoneDB5.0.1 raw-gene aggregation."""
import argparse,hashlib,json,traceback,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='FAIL2_PANDAS_PREPARATION_SOURCE_UNBOUND',implementation_credit=False,whole_goal_complete=False)
    def fetch(url,path):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
        with urllib.request.urlopen(request,timeout=45) as response:raw=response.read(4194305);assert len(raw)<=4194304
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);return raw
    try:
        base='https://api.github.com/repos/pandas-dev/pandas';tag=json.loads(fetch(base+'/git/ref/tags/v2.2.3',out/'official-tag.json'));revision=tag['object']['sha'];kind=tag['object']['type']
        if kind=='tag':annotation=json.loads(fetch(base+'/git/tags/'+revision,out/'official-annotated-tag.json'));assert annotation['object']['type']=='commit';revision=annotation['object']['sha']
        else:assert kind=='commit'
        assert len(revision)==40;members={}
        for name in ['pandas/_libs/groupby.pyx','LICENSE']:
            raw=fetch('https://raw.githubusercontent.com/pandas-dev/pandas/'+revision+'/'+name,out/'source'/name);members[name]=dict(**pin(out/'source'/name),git_blob_sha1=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
        assert b'BSD 3-Clause License' in (out/'source/LICENSE').read_bytes() and b'def group_mean(' in (out/'source/pandas/_libs/groupby.pyx').read_bytes();state.update(status='PASS_EXACT_PANDAS_223_GROUP_MEAN_SOURCE_ONLY',revision=revision,version='2.2.3',source_members=members,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/license checks')
    raise SystemExit(main())
