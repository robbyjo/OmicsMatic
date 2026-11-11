# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official CellChat edition metadata before selecting immutable GPL reference archives."""
import argparse,json,traceback,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def fetch(url):
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-CellChat-source-audit/1.0','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(request,timeout=45) as response:
        raw=response.read(16777217);metadata=dict(url=url,resolved_url=response.url,status=response.status)
    assert len(raw)<=16777216,'Metadata response exceeds16MiB';return raw,metadata


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    state=dict(status='SKIP3_CELLCHAT_SOURCE_METADATA_UNAVAILABLE',implementation_credit=False,analysis_admitted=False,whole_goal_complete=False)
    try:
        assert a.allow_download,'Download permission absent; skip source intake without network access';records={}
        for repository in ['sqjin/CellChat','jinworks/CellChat']:
            key=repository.split('/')[0];capture={}
            for name,suffix in [('repository',''),('tags','/tags?per_page=100'),('description-history','/commits?path=DESCRIPTION&per_page=100')]:
                url='https://api.github.com/repos/'+repository+suffix;raw,metadata=fetch(url);path=out/(key+'-'+name+'.json');path.write_bytes(raw);capture[name]=dict(**metadata,**pin(path))
                data=json.loads(raw)
                if name=='repository':assert data['full_name'].lower()==repository.lower();capture['default_branch']=data['default_branch']
                if name=='tags':print(repository+' tags '+json.dumps([(r['name'],r['commit']['sha']) for r in data]),flush=True)
            raw,metadata=fetch('https://raw.githubusercontent.com/'+repository+'/'+capture['default_branch']+'/DESCRIPTION');path=out/(key+'-DESCRIPTION');path.write_bytes(raw)
            text=raw.decode('utf8');fields=dict(line.split(':',1) for line in text.splitlines() if ':' in line and not line.startswith((' ','\t')))
            assert fields['Package'].strip()=='CellChat' and fields['License'].strip()=='GPL-3';capture['current_description']=dict(**metadata,**pin(path),declared_version=fields['Version'].strip());records[repository]=capture
        state.update(status='PASS_OFFICIAL_GPL_CELLCHAT_METADATA_CAPTURE_ONLY',repositories=records,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},original_package_installed=False,original_program_executed=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Only official metadata captured; no package or scientific analysis was run.')+'\n\nRequired action: if download/install is unavailable or forbidden, skip dependent original-reference analysis. Preserve this attempt; supply exact permitted source/dependencies in NEW private paths. Do not substitute current development code for a reported historical edition or infer database-only use as package execution.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source identity/license checks')
    raise SystemExit(main())
