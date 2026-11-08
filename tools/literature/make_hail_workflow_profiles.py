# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package exact complete original wheel-member identities/licenses for both Hail editions; no runtime/software substitution."""
import argparse,hashlib,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);base=ROOT/'build/literature-review/hail-runtime/source-intake-2';intake=json.loads((base/'COMPLETED.json').read_bytes());assert intake['status']=='PASS_TWO_ORIGINAL_HAIL_EDITIONS_SOURCE_CAPTURE_ONLY';target=ROOT/'src/main/resources/org/jlinalg/omics/hail-workflow';editions={};resources={}
    for version in ['0.2.139','0.2.130']:
        source=intake['versions'][version];wheel=ROOT/source['wheel']['file'];assert pin(wheel)=={k:source['wheel'][k] for k in ['bytes','sha256']};members={}
        with zipfile.ZipFile(wheel) as bundle:
            for item in bundle.infolist():
                if item.is_dir() or item.filename.endswith('.dist-info/RECORD'):continue
                sha=hashlib.sha256();size=0
                with bundle.open(item) as stream:
                    while block:=stream.read(1048576):size+=len(block);sha.update(block)
                members[item.filename]=dict(bytes=size,sha256=sha.hexdigest())
        editions[version]=dict(hail_version=version+'-'+source['revision'][:12],revision=source['revision'],python_minor='3.12' if version=='0.2.139' else '3.10',spark='3.5.3' if version=='0.2.139' else '3.3.2',java_major=11,wheel=source['wheel'],wheel_members=members,license=source['license']);resources[version+'/MIT.txt']=(base/version/'upstream-LICENSE').read_bytes()
    resources['profiles.json']=(json.dumps(dict(schema_version=1,scope='Exact original Hail core/hailtop wheel members and source license identities. No workflow proof, cloud backend/scale or paper reproduction is certified.',editions=editions),indent=2)+'\n').encode('utf8')
    for name,raw in resources.items():
        path=target/name;path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists():assert path.read_bytes()==raw,'Never silently replace an existing original source profile/license'
        else:path.write_bytes(raw)
    save(out/'COMPLETED.json',dict(status='PASS_TWO_COMPLETE_ORIGINAL_HAIL_WHEEL_SOURCE_PROFILES_ONLY',resources={p.relative_to(ROOT).as_posix():pin(p) for p in [target/name for name in resources]},original_member_counts={version:len(value['wheel_members']) for version,value in editions.items()},source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False));print('Captured every original installed wheel member except pip-rewritten RECORD:',{v:len(e['wheel_members']) for v,e in editions.items()})

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original profile checks')
    main()
