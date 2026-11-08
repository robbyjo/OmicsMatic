# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect both unchanged original namespaces in separate processes, recording effective exact imports."""
import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--prefix',required=True,type=Path);p.add_argument('--profile',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--edition');a=p.parse_args();profile=json.loads(a.profile.read_bytes());assert sys.version_info[:3]==(3,11,17) and sys.flags.safe_path and sys.flags.no_user_site and not sys.flags.ignore_environment and not sys.flags.hash_randomization and os.environ['PYTHONHASHSEED']=='0'
    if not a.edition:
        editions={}
        for edition in profile['editions']:
            command=[sys.executable,'-B','-P','-s',str(Path(__file__).resolve()),'--prefix',str(a.prefix),'--profile',str(a.profile),'--out',str(a.out.parent/('health-'+edition+'.json')),'--edition',edition];run=subprocess.run(command,capture_output=True,timeout=180);(a.out.parent/('health-'+edition+'.stdout')).write_bytes(run.stdout);(a.out.parent/('health-'+edition+'.stderr')).write_bytes(run.stderr);assert run.returncode==0,edition+' full import health failed';editions[edition]=json.loads((a.out.parent/('health-'+edition+'.json')).read_bytes())
        a.out.write_text(json.dumps(dict(status='PASS_COMPLETE_ORIGINAL_PALANTIR_NAMESPACES_ONLY',editions=editions,namespace_patched=False),indent=2)+'\n',encoding='utf8');return 0
    edition=a.edition;target=a.prefix/'editions'/edition;dependencies=a.prefix/'dependencies';sys.path[:0]=[str(target),str(dependencies)];import importlib.metadata as metadata
    import palantir,numpy,pandas,scipy,sklearn,scanpy,anndata,mellon,jax
    assert palantir.__version__==edition and Path(palantir.__file__).resolve().is_relative_to(target)
    for module in [numpy,pandas,scipy,sklearn,scanpy,anndata,mellon,jax]:assert Path(module.__file__).resolve().is_relative_to(dependencies)
    for entry in profile['dependency_archives']:
        if entry['name'].lower()!='palantir':assert metadata.version(entry['name'])==entry['version'],'Dependency version differs: '+entry['name']
    members=profile['editions'][edition]['members'];seen={p.relative_to(target).as_posix() for p in (target/'palantir').rglob('*') if p.is_file()};assert seen=={n for n in members if n.startswith('palantir/')}
    for name,want in members.items():
        if name.endswith('.dist-info/RECORD'):continue
        file=target/name;raw=file.read_bytes();assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==want,'Original source/license/metadata changed: '+name
    a.out.write_text(json.dumps(dict(status='PASS_ORIGINAL_PALANTIR_NAMESPACE_ONLY',edition=edition,python_version='3.11.17',python_hash_seed='0',numpy=numpy.__version__,pandas=pandas.__version__,scipy=scipy.__version__,namespace_patched=False,installer_record_retained_separately=True),indent=2)+'\n',encoding='utf8');return 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original namespace checks')
    raise SystemExit(main())
