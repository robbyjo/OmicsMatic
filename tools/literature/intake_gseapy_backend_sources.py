# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official source-distribution backends for the four already pinned paper editions."""
import argparse,json,sys,tarfile,traceback,urllib.request
from pathlib import Path,PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_GSEAPY_BACKEND_SOURCE',versions={},implementation_credit=False,whole_goal_complete=False)
    try:
        intake=ROOT/'build/literature-review/gseapy-runtime/source-intake-1';profile=json.loads((intake/'SOURCE.json').read_bytes())
        for version,wheel in profile['versions'].items():
            metadata=intake/version/'pypi.json';assert pin(metadata)==wheel['metadata'];record=json.loads(metadata.read_bytes());entries=[r for r in record['urls'] if r['packagetype']=='sdist'];assert len(entries)==1;entry=entries[0];assert entry['url'].startswith('https://files.pythonhosted.org/');home=out/version;home.mkdir();archive=home/entry['filename']
            with urllib.request.urlopen(entry['url'],timeout=90) as response,archive.open('xb') as stream:stream.write(response.read(entry['size']+1))
            assert pin(archive)==dict(bytes=entry['size'],sha256=entry['digests']['sha256']);members={}
            with tarfile.open(archive) as tar:
                for member in tar.getmembers():
                    name=PurePosixPath(member.name);assert not name.is_absolute() and '..' not in name.parts and '\\' not in member.name
                    if not member.isfile():continue
                    relative=PurePosixPath(*name.parts[1:])
                    if not(relative.suffix in {'.rs','.toml','.lock'} or relative.name in {'LICENSE','LICENSE.md','setup.py','pyproject.toml'}):continue
                    assert relative.as_posix() not in members;target=home/'source'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(member).read());members[relative.as_posix()]=pin(target)
            assert 'src/algorithm.rs' in members and 'src/stats.rs' in members;state['versions'][version]=dict(metadata=pin(metadata),archive=dict(filename=entry['filename'],url=entry['url'],**pin(archive)),members=members);print('Captured original Rust backend',version,len(members),'source members',flush=True)
        state.update(status='passed-four-official-GSEApy-backend-source-distributions',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'SOURCE.json',state);return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official source checks')
    raise SystemExit(main())
