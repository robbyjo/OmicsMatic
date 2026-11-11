# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact official LeafCutter tagged/current sources and Git blob identities. No quantification/model execution or tool credit."""
import argparse,concurrent.futures,hashlib,json,re,shutil,traceback,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

REPO='https://api.github.com/repos/davidaknowles/leafcutter'
RAW='https://raw.githubusercontent.com/davidaknowles/leafcutter/'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--reuse-intake',type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());source_pin=pin(Path(__file__));state=dict(status='SKIP3_LEAFCUTTER_ORIGINAL_SOURCE_UNAVAILABLE',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():source_pin},editions={},implementation_credit=False,whole_goal_complete=False)
    def fetch(url,path,maximum=8388608):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-source-intake'}),timeout=90) as r:raw=r.read(maximum+1)
        assert len(raw)<=maximum,'Original source exceeds capture capacity';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);return raw
    try:
        assert a.allow_download,'Required source download is not permitted for this invocation; dependent original analysis remains skipped';tags=json.loads(fetch(REPO+'/tags?per_page=100',out/'official-tags.json',1048576));head=json.loads(fetch(REPO+'/commits/master',out/'official-master-commit.json',1048576));commit=head['sha'];assert re.fullmatch('[0-9a-f]{40}',commit);editions=[('v0.2.6',next(t['commit']['sha'] for t in tags if t['name']=='v0.2.6')),('v0.2.7',next(t['commit']['sha'] for t in tags if t['name']=='v0.2.7')),('source-'+commit[:12],commit)]
        for edition,revision in editions:
            home=out/edition;home.mkdir()
            if edition.startswith('v'):
                tag=json.loads(fetch(REPO+'/git/ref/tags/'+edition,home/'official-git-ref.json',131072));obj=tag['object']
                if obj['type']=='tag':obj=json.loads(fetch(obj['url'],home/'official-annotated-tag.json',131072))['object']
                assert obj['type']=='commit' and obj['sha']==revision,'Tag list/ref commit identity differs'
            git_commit=json.loads(fetch(REPO+'/git/commits/'+revision,home/'official-git-commit.json',1048576));assert git_commit['sha']==revision;tree=json.loads(fetch(REPO+'/git/trees/'+git_commit['tree']['sha']+'?recursive=1',home/'official-source-tree.json',8388608));assert tree['sha']==git_commit['tree']['sha'] and tree['truncated'] is False
            selected=[]
            for row in tree['tree']:
                name=row['path'];path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
                if row['type']=='blob' and (path.suffix in ['.py','.R','.r','.stan','.cpp','.hpp','.h','.c','.sh'] or path.name in ['LICENSE','LICENSE.txt','COPYING','NOTICE','DESCRIPTION','NAMESPACE','Makevars','Makevars.win','README.md']) and not name.startswith(('docs/','leafviz/')):selected.append(row)
            assert 20<len(selected)<1000 and sum(r.get('size',0) for r in selected)<67108864;members={}
            def capture(row):
                assert row['mode'] in ['100644','100755'],'Linked/nonregular original source requires explicit treatment: '+row['path'];name=row['path'];old=a.reuse_intake/edition/'source'/name if a.reuse_intake else None
                if old and old.is_file():raw=old.read_bytes();destination=home/'source'/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(raw)
                else:raw=fetch(RAW+revision+'/'+name,home/'source'/name,16777216)
                assert len(raw)==row['size'] and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==row['sha'],'Original Git blob differs: '+name;return name,dict(git_blob_sha1=row['sha'],mode=row['mode'],url=RAW+revision+'/'+name,**pin(home/'source'/name))
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:members=dict(pool.map(capture,selected))
            for required in ['LICENSE','leafcutter/DESCRIPTION','clustering/leafcutter_cluster.py','scripts/prepare_phenotype_table.py','scripts/leafcutter_ds.R','leafcutter/R/differential_splicing.R']:assert required in members,required
            description=(home/'source/leafcutter/DESCRIPTION').read_text(encoding='utf8');package_version=re.search(r'^Version:\s*(.+)$',description,re.M).group(1).strip();package_license=re.search(r'^License:\s*(.+)$',description,re.M).group(1).strip();assert b'Apache License' in (home/'source/LICENSE').read_bytes() and package_license=='GPL (>= 3)';report=dict(status='PASS_EXACT_ORIGINAL_LEAFCUTTER_SOURCE_EDITION_ONLY',revision=revision,edition=edition,R_package_version=package_version,R_package_license=package_license,repository_license='Apache-2.0',members=members,scope='Exact tagged/current source, not a claim that a paper package version identifies these clustering/phenotype scripts or a historical source commit. No R/Stan or junction workflow was executed.');save(home/'COMPLETED.json',report);state['editions'][edition]=report;print(edition,revision,'R package',package_version,len(members),'original source files captured',flush=True)
        assert pin(Path(__file__))==source_pin;state['status']='PASS_THREE_EXACT_ORIGINAL_LEAFCUTTER_SOURCE_EDITIONS_ONLY'
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    action='Required action: dependent original analysis remains skipped until exact permitted source/runtime capture succeeds. Preserve this attempt and partial downloads. Correct the named permission/network/source mismatch and retry NEW paths. Do not substitute a release, change an original script or refresh a saved scientific hash. Source capture alone grants no implementation credit.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Source identity only; no original/native analysis admitted.')+'\n\n'+action+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact original source capture checks')
    raise SystemExit(main())
