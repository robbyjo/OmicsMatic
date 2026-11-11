# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check each full original edition in a fresh interpreter; never combine compiled backends in one namespace."""
import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prefix','profile','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--edition');a=p.parse_args();profile=json.loads(a.profile.read_bytes());assert sys.version_info[:3]==(3,11,17) and sys.flags.dont_write_bytecode and not sys.flags.optimize
    if not a.edition:
        reports={}
        for edition in profile['editions']:
            target=a.out.parent/('health-'+edition+'.json');command=[sys.executable,'-B','-P','-s',str(Path(__file__).resolve()),'--prefix',str(a.prefix),'--profile',str(a.profile),'--out',str(target),'--edition',edition]
            run=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(a.out.parent/('health-'+edition+'.stdout')).write_bytes(run.stdout);(a.out.parent/('health-'+edition+'.stderr')).write_bytes(run.stderr);assert run.returncode==0,'Complete namespace health failed: '+edition;reports[edition]=json.loads(target.read_bytes())
        assert not a.out.exists();a.out.write_text(json.dumps(dict(status='PASS_COMPLETE_ORIGINAL_GSEAPY_NAMESPACES_ONLY',editions=reports,python_version='3.11.17',namespace_patched=False),indent=2)+'\n',encoding='utf8');return 0
    assert a.edition in profile['editions'];assert sys.flags.safe_path and sys.flags.no_user_site and not sys.flags.ignore_environment and os.environ['PYTHONHASHSEED']=='0' and not sys.flags.hash_randomization
    home=a.prefix/'editions'/a.edition;sys.path.insert(0,str(home))
    import gseapy,gseapy.gse,numpy,pandas,scipy,matplotlib,requests
    import importlib.metadata as metadata
    assert gseapy.__version__==a.edition and metadata.version('gseapy')==a.edition
    assert numpy.__version__=='1.26.4' and pandas.__version__=='2.2.3' and scipy.__version__=='1.11.4'
    assert Path(gseapy.__file__).resolve().is_relative_to(home) and Path(gseapy.gse.__file__).resolve().is_relative_to(home)
    package=profile['editions'][a.edition];seen={p.relative_to(home).as_posix() for p in (home/'gseapy').rglob('*') if p.is_file()};assert seen=={n for n in package['members'] if n.startswith('gseapy/')}
    records={}
    for name,want in package['members'].items():
        actual=pin(home/name)
        if name.endswith('.dist-info/RECORD'):records[name]=dict(original=want,installer_generated=actual)
        else:assert actual==want,'Original source/license/binary/metadata changed: '+name
    functions=[n for n in ['enrichr','prerank','gsea','ssgsea','gsva'] if callable(getattr(gseapy,n,None))];assert functions==(['enrichr','prerank','gsea','ssgsea'] if a.edition=='1.0.4' else ['enrichr','prerank','gsea','ssgsea','gsva'])
    assert not a.out.exists();a.out.write_text(json.dumps(dict(status='PASS_COMPLETE_ORIGINAL_GSEAPY_EDITION_ONLY',edition=a.edition,python_version='3.11.17',python_hash_seed='0',original_functions=functions,original_namespace=str(gseapy.__file__),original_extension=str(gseapy.gse.__file__),numpy=numpy.__version__,pandas=pandas.__version__,scipy=scipy.__version__,namespace_patched=False,installer_records=records),indent=2)+'\n',encoding='utf8');return 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original namespace checks')
    raise SystemExit(main())
