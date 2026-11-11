# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture complete original R package trees, including exact compiled-model wrappers/data, without changing the frozen native-component intake."""
import argparse,concurrent.futures,hashlib,json,traceback,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_COMPLETE_LEAFCUTTER_R_SOURCE_UNAVAILABLE',editions={},implementation_credit=False,whole_goal_complete=False);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());source_pin=pin(Path(__file__));state['source_pin']=source_pin
    try:
        assert a.allow_download,'Required download not allowed; dependent original analysis skipped';prior=json.loads((a.intake/'COMPLETED.json').read_bytes());assert prior['status']=='PASS_THREE_EXACT_ORIGINAL_LEAFCUTTER_SOURCE_EDITIONS_ONLY'
        for edition,profile in prior['editions'].items():
            home=out/edition;home.mkdir();tree_path=a.intake/edition/'official-source-tree.json';tree=json.loads(tree_path.read_bytes());(home/'official-source-tree.json').write_bytes(tree_path.read_bytes());selected=[r for r in tree['tree'] if r['type']=='blob' and (r['path'].startswith('leafcutter/') or r['path'] in ['LICENSE','README.md'])];assert sum(r['size'] for r in selected)<67108864
            def capture(row):
                name=row['path'];safe=PurePosixPath(name);assert not safe.is_absolute() and '..' not in safe.parts and row['mode'] in ['100644','100755'];old=a.intake/edition/'source'/name;destination=home/'source'/name;destination.parent.mkdir(parents=True,exist_ok=True);url='https://raw.githubusercontent.com/davidaknowles/leafcutter/'+profile['revision']+'/'+name
                if old.is_file():raw=old.read_bytes()
                else:
                    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-complete-original-R-source'}),timeout=90) as response:raw=response.read(row['size']+1)
                assert len(raw)==row['size'] and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==row['sha'];destination.write_bytes(raw);return name,dict(url=url,git_blob_sha1=row['sha'],mode=row['mode'],**pin(destination))
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:members=dict(pool.map(capture,selected))
            report=dict(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_R_PACKAGE_SOURCE_ONLY',revision=profile['revision'],R_package_version=profile['R_package_version'],members=members,repository_license=profile['repository_license'],R_package_license=profile['R_package_license']);save(home/'COMPLETED.json',report);state['editions'][edition]=report;print(edition,len(members),'complete R package/license members',flush=True)
        assert pin(Path(__file__))==source_pin;state['status']='PASS_THREE_COMPLETE_ORIGINAL_LEAFCUTTER_R_PACKAGE_TREES_ONLY'
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Source capture only; no R installation or model analysis.')+'\n\nRequired action: preserve original/partial trees and errors; correct the named source/network/permission cause and retry NEW paths. Missing/forbidden exact sources keep dependent analyses skipped. Do not patch originals, substitute a paper version or admit incomplete model outputs.\n',encoding='utf8',newline='\n');print(state['status']);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original source checks')
    raise SystemExit(main())
