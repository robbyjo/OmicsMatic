# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review pinned primary distance sources, reusing exact retained intake when requested."""
import argparse,hashlib,json,sys,traceback,urllib.request
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def fetch(url,maximum):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review'}),timeout=60) as response:
        raw=response.read(maximum+1);assert len(raw)<=maximum;return raw

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--intake',type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);intake=a.intake.resolve() if a.intake else out;assert intake.is_relative_to(ROOT/'build/literature-review/scfates-runtime');sources={};state=dict(status='FAIL2_SCFATES_ROOT_DISTANCE_SOURCE_REVIEW',implementation_credit=False,whole_goal_complete=False)
    try:
        for name,repo,tag,members in [('scipy','scipy/scipy','v1.11.4',['scipy/sparse/csgraph/_shortest_path.pyx','LICENSE.txt']),('sklearn','scikit-learn/scikit-learn','1.3.2',['sklearn/metrics/pairwise.py','COPYING'])]:
            home=intake/name
            if not a.intake:home.mkdir()
            def obtain(path,url,maximum):
                if a.intake:return path.read_bytes()
                raw=fetch(url,maximum);path.parent.mkdir(parents=True,exist_ok=True)
                with path.open('xb') as stream:stream.write(raw)
                return raw
            raw=obtain(home/'official-tag.json','https://api.github.com/repos/'+repo+'/git/ref/tags/'+tag,1048576);tag_value=json.loads(raw);assert tag_value['ref']=='refs/tags/'+tag;ref=tag_value['object']
            if ref['type']=='tag':ref=json.loads(obtain(home/'official-tag-object.json',ref['url'],1048576))['object']
            assert ref['type']=='commit' and len(ref['sha'])==40;revision=ref['sha'];records={}
            for member in members:
                info_raw=obtain(home/(Path(member).name+'.github.json'),'https://api.github.com/repos/'+repo+'/contents/'+member+'?ref='+revision,4194304);metadata=json.loads(info_raw);assert metadata['type']=='file';path=home/member;raw=obtain(path,'https://raw.githubusercontent.com/'+repo+'/'+revision+'/'+member,2097152);assert len(raw)==metadata['size'] and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==metadata['sha'];records[member]=dict(**pin(path),git_blob=metadata['sha'])
            sources[name]=dict(repository=repo,tag=tag,revision=revision,members=records)
        scipy=(intake/'scipy/scipy/sparse/csgraph/_shortest_path.pyx').read_text();pairwise=intake/'sklearn/sklearn/metrics/pairwise.py';assert '_dijkstra_undirected' in scipy and 'csrT_weights, csrT_indices, csrT_indptr' in scipy and 'indices is not None or Nk < N * N / 4' in scipy;assert 'not be exactly symmetric' in ' '.join(pairwise.read_text().split());installed=ROOT/'build/literature-review/scfates-runtime/reference-setup-1/dependencies/sklearn/metrics/pairwise.py';assert pin(installed)==pin(pairwise),'Tagged sklearn distance source differs from actual original wheel member'
        state.update(status='PASS_OFFICIAL_DIRECTIONAL_DISTANCE_AND_UNDIRECTED_TREE_SOURCE_REVIEW_ONLY',intake=intake.relative_to(ROOT).as_posix(),sources=sources,installed_sklearn_source=pin(installed),finding='Original sklearn Euclidean evaluation may return distinct directional rounding values. SciPy1.11.4 auto selects Dijkstra when indices is supplied and scans CSR plus its transpose. In the selected positive unique-tree-path profile, the smaller directional weight controls an edge. Preserve both original values; do not average or rewrite the graph. SciPy cautions against interpreting general asymmetric directed=False graphs; no general-graph claim is made.',network_used=not bool(a.intake),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
