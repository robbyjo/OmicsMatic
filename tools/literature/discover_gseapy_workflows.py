# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect complete original package APIs in one fresh interpreter per captured release."""
import argparse,hashlib,json,sys,traceback
from pathlib import Path

def pin(path):raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--edition',required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True)
    state=dict(status='SKIP3_ORIGINAL_GSEAPY_NAMESPACE_UNAVAILABLE',edition=a.edition,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        import inspect
        source=a.source.resolve();profile=json.loads((source/'SOURCE.json').read_bytes())['versions'][a.edition];home=source/a.edition/'wheel-source';before={n:pin(home/n) for n in profile['members']};assert before==profile['members'];sys.path.insert(0,str(home))
        import gseapy
        import gseapy.gse
        import numpy,scipy,pandas
        assert gseapy.__version__==a.edition and Path(gseapy.__file__).resolve().is_relative_to(home) and Path(gseapy.gse.__file__).resolve().is_relative_to(home)
        functions={}
        for name in ['enrichr','prerank','gsea','ssgsea','gsva']:
            if hasattr(gseapy,name):
                function=getattr(gseapy,name);functions[name]=dict(signature=str(inspect.signature(function)),source=inspect.getsource(function))
        after={n:pin(home/n) for n in profile['members']};assert before==after
        state.update(status='passed-complete-original-GSEApy-namespace-discovery-only',functions=functions,original_namespace=str(gseapy.__file__),original_extension=str(gseapy.gse.__file__),runtime=dict(python=sys.version,numpy=numpy.__version__,scipy=scipy.__version__,pandas=pandas.__version__),all_original_members_unchanged=True,producer=pin(Path(__file__).resolve()))
    except Exception as error:
        state['error']=repr(error);state['required_action']='Preserve this skipped discovery. Supply the named permitted dependency in NEW private storage; do not modify the original bound base or install during analysis. Original-dependent analysis is skipped until the complete namespace loads.';(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'DISCOVERY.json',state);print(state['status'],a.edition,state.get('error',''))
    return 0 if state['status'].startswith('passed-') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original namespace checks')
    raise SystemExit(main())
