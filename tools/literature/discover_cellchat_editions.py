# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resolve exact reported CellChat version declarations from official immutable DESCRIPTION history."""
import argparse,concurrent.futures,json,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from intake_cellchat_metadata import fetch


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args();base=ROOT/'build/literature-review/cellchat-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);state=dict(status='SKIP3_CELLCHAT_EDITION_DISCOVERY_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_download,'Download not permitted; skip edition discovery'
        metadata=base/'source-metadata-1';ready=json.loads((metadata/'COMPLETED.json').read_bytes());assert ready['status']=='PASS_OFFICIAL_GPL_CELLCHAT_METADATA_CAPTURE_ONLY';jobs=[]
        for repository,record in ready['repositories'].items():
            owner=repository.split('/')[0];path=metadata/(owner+'-description-history.json');assert pin(path)=={k:record['description-history'][k] for k in ['bytes','sha256']}
            rows=json.loads(path.read_bytes());jobs.extend((repository,row['sha'],row['commit']['committer']['date']) for row in rows)
            tags=json.loads((metadata/(owner+'-tags.json')).read_bytes());jobs.extend((repository,row['commit']['sha'],'official-tag:'+row['name']) for row in tags)
        jobs=list(dict.fromkeys(jobs))
        def capture(item):
            repository,commit,date=item;url='https://raw.githubusercontent.com/'+repository+'/'+commit+'/DESCRIPTION';raw,receipt=fetch(url);name=repository.split('/')[0]+'-'+commit+'-DESCRIPTION';path=out/name
            # A tag and history row can identify the same byte source. The name
            # is immutable, and duplicate capture must agree exactly.
            if path.exists():assert path.read_bytes()==raw
            else:path.write_bytes(raw)
            fields=dict(line.split(':',1) for line in raw.decode('utf8').splitlines() if ':' in line and not line.startswith((' ','\t')))
            assert fields['Package'].strip()=='CellChat' and fields['License'].strip()=='GPL-3'
            return dict(repository=repository,commit=commit,date_or_tag=date,version=fields['Version'].strip(),description=pin(path),description_file=name,url=url)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(capture,jobs))
        save(out/'editions.json',records);matches={v:[r for r in records if r['version']==v] for v in ['1.0.0','1.1.3','2.1.2','2.2.0']}
        for version,rows in matches.items():print(version,json.dumps([(r['commit'],r['date_or_tag']) for r in rows]),flush=True)
        state.update(status='PASS_OFFICIAL_CELLCHAT_IMMUTABLE_VERSION_DECLARATIONS_ONLY',captured=len(records),editions=pin(out/'editions.json'),reported_versions=matches,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/intake_cellchat_metadata.py']},original_program_executed=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable immutable source-version checks')
    raise SystemExit(main())
