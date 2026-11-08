# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Import complete original CPU namespaces and verify every original distribution member."""
import argparse,hashlib,json,os,sys
from pathlib import Path

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prefix','profile','out']:p.add_argument('--'+name,required=True,type=Path)
    a=p.parse_args();assert sys.version_info[:3]==(3,11,17) and sys.flags.safe_path and sys.flags.no_user_site and not sys.flags.ignore_environment and not sys.flags.hash_randomization and sys.flags.dont_write_bytecode and not sys.flags.optimize and os.environ['PYTHONHASHSEED']=='0';profile=json.loads(a.profile.read_bytes());dependencies=a.prefix/'dependencies';sys.path.insert(0,str(dependencies))
    import importlib.metadata as metadata
    import scFates,simpleppt,numpy,pandas,scipy,sklearn,scanpy,anndata,igraph,numba,skmisc,elpigraph,leidenalg,adjustText,plotly
    assert scFates.__version__=='1.2.5' and simpleppt.__version__=='1.1.7'
    for module in [scFates,simpleppt,numpy,pandas,scipy,sklearn,scanpy,anndata,igraph,numba,skmisc,elpigraph,leidenalg,adjustText,plotly]:assert Path(module.__file__).resolve().is_relative_to(dependencies)
    for entry in profile['dependency_archives']:assert metadata.version(entry['name'])==entry['version'],'Dependency version differs: '+entry['name']
    records={}
    for name,package in profile['packages'].items():
        scientific='scFates' if name=='scfates' else 'simpleppt';seen={p.relative_to(dependencies).as_posix() for p in (dependencies/scientific).rglob('*') if p.is_file()};assert seen=={n for n in package['members'] if n.startswith(scientific+'/')},'Original package inventory differs'
        for member,want in package['members'].items():
            actual=pin(dependencies/member)
            if member.endswith('.dist-info/RECORD'):records[member]=dict(original=want,installer_generated=actual)
            else:assert actual==want,'Original source/license/metadata changed: '+member
    assert not a.out.exists();a.out.write_text(json.dumps(dict(status='PASS_COMPLETE_ORIGINAL_SCFATES_NAMESPACES_ONLY',edition=profile['edition'],python_version='3.11.17',python_hash_seed='0',numpy=numpy.__version__,pandas=pandas.__version__,scipy=scipy.__version__,namespace_patched=False,installer_records=records),indent=2)+'\n',encoding='utf8');return 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original namespace checks')
    raise SystemExit(main())
