# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install an isolated Linux HTSeq2.1.2 reference from publisher-hash-verified wheels."""
import argparse
import hashlib
import json
import subprocess
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/htseq-runtime'
DISTRO='JLinAlg-Omics-SAIGE-20261026'
PACKAGES=[('pip','25.3'),('numpy','2.2.6'),('pysam','0.23.3'),('HTSeq','2.1.2')]


def pin(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def linux(path):
    path=path.resolve()
    assert path.drive.lower()=='d:'
    return '/mnt/d/'+path.as_posix()[3:]


def runtime_inventory(prefix,out):
    """Read Linux symlinks on Linux; Windows cannot stat WSL Python links."""
    code='''import hashlib,json,sys
from pathlib import Path
root=Path(sys.argv[1]);files={};links={}
for path in sorted(root.rglob('*')):
 if '__pycache__' in path.parts:continue
 name=path.relative_to(root).as_posix()
 if path.is_symlink():
  resolved=path.resolve(strict=True)
  if resolved.is_dir():links[name]=dict(kind='directory',target=str(path.readlink()),resolved=str(resolved))
  else:
   with resolved.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
   links[name]=dict(kind='file',target=str(path.readlink()),resolved=str(resolved),bytes=resolved.stat().st_size,sha256=digest)
 elif path.is_file():
  with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
  files[name]=dict(bytes=path.stat().st_size,sha256=digest)
print(json.dumps(dict(files=files,symlinks=links,excluded_mutable_bytecode_caches=True),sort_keys=True))
'''
    argv=['wsl.exe','-d',DISTRO,'--',linux(prefix/'bin/python'),'-I','-c',code,linux(prefix)]
    with (out/'inventory.stdout').open('xb') as stdout,(out/'inventory.stderr').open('xb') as stderr:
        result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    (out/'inventory.process.json').write_text(json.dumps(dict(command=argv,exit_code=result.returncode),indent=2)+'\n',encoding='utf8',newline='\n')
    assert result.returncode==0,'Linux runtime inventory failed; retain logs'
    inventory=json.loads((out/'inventory.stdout').read_bytes())
    assert inventory['files'] and inventory['symlinks']
    return inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--allow-download',action='store_true')
    parser.add_argument('--allow-install',action='store_true')
    args=parser.parse_args()
    out=args.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    def save(path,value):
        path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
    warning='SKIP3: reference setup or dependency unavailable. Skip dependent HTSeq analyses. Where permitted, rerun setup with --allow-download and --allow-install into NEW workspace paths; otherwise provide the exact permitted profile and preserve the skip. This reference setup does not install into shared/system Python or admit analysis results.'
    if not(args.allow_download and args.allow_install):
        save(out/'SKIPPED.json',dict(status='SKIP3_SETUP_NOT_AUTHORIZED',required_action=warning))
        print(warning)
        raise SystemExit(3)
    cache=out/'wheel-cache'
    cache.mkdir()
    receipts=[]
    def fetch(url,target,maximum):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-method-reference/1.0'})
        with urllib.request.urlopen(request,timeout=60) as response:
            raw=response.read(maximum+1)
            assert len(raw)<=maximum
            receipt=dict(url=url,final_url=response.geturl(),**pin(raw))
        target.write_bytes(raw)
        receipts.append(receipt)
        save(out/'DOWNLOAD-PROGRESS.json',receipts)
        return raw
    def command(label,argv,timeout=180):
        argv=list(map(str,argv))
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode))
        assert result.returncode==0,label+': inspect retained process logs'
    save(out/'INCOMPLETE.json',dict(status='isolated-HTSeq-reference-install-in-progress'))
    try:
        wheels=[]
        for name,version in PACKAGES:
            metadata=json.loads(fetch('https://pypi.org/pypi/'+name+'/'+version+'/json',out/(name+'-PyPI.json'),16777216))
            assert metadata['info']['version']==version
            candidates=[r for r in metadata['urls'] if r['packagetype']=='bdist_wheel' and (
                name=='pip' and r['filename'].endswith('-py3-none-any.whl') or
                '-cp312-cp312-' in r['filename'] and 'manylinux' in r['filename'] and 'x86_64' in r['filename'])]
            assert candidates,(name,version,'No permitted Linux x86_64 Python3.12 wheel; do not substitute an edition')
            row=sorted(candidates,key=lambda r:r['filename'])[0]
            assert row['url'].startswith('https://files.pythonhosted.org/')
            raw=fetch(row['url'],cache/row['filename'],134217728)
            assert pin(raw)['sha256']==row['digests']['sha256'] and len(raw)==row['size']
            wheels.append(dict(package=name,version=version,filename=row['filename'],**pin(raw)))
        prefix=out/'venv'
        command('venv',['wsl.exe','-d',DISTRO,'--','/usr/bin/python3','-m','venv','--without-pip',linux(prefix)])
        pipwheel=cache/next(r['filename'] for r in wheels if r['package']=='pip')
        code='import runpy,sys;sys.path.insert(0,'+repr(linux(pipwheel))+');sys.argv='+repr(['pip','--isolated','--python',linux(prefix/'bin/python'),
            'install','--no-index','--no-deps','--find-links',linux(cache),*[name+'=='+version for name,version in PACKAGES]])+';runpy.run_module("pip",run_name="__main__")'
        command('install',['wsl.exe','-d',DISTRO,'--','/usr/bin/python3','-c',code],300)
        probe='import HTSeq,numpy,pysam,sys,json;print(json.dumps(dict(python=sys.version,executable=sys.executable,HTSeq=HTSeq.__version__,numpy=numpy.__version__,pysam=pysam.__version__)));assert HTSeq.__version__=="2.1.2";assert numpy.__version__=="2.2.6";assert pysam.__version__=="0.23.3"'
        command('probe',['wsl.exe','-d',DISTRO,'--',linux(prefix/'bin/python'),'-I','-c',probe])
        inventory=runtime_inventory(prefix,out)
        save(out/'RUNTIME-FILES.json',inventory)
        save(out/'COMPLETED.json',dict(schema_version=1,status='installed-private-exact-HTSeq2.1.2-reference',wheels=wheels,
            python=linux(prefix/'bin/python'),runtime_files=len(inventory['files']),runtime_symlinks=len(inventory['symlinks']),runtime_file_inventory=pin((out/'RUNTIME-FILES.json').read_bytes()),
            source_intake='build/literature-review/htseq-runtime/source-intake-2',shared_python_modified=False,
            system_packages_installed=False,analysis_results_admitted=False,historical_whole_packages_reproduced=False,
            original_method_controls_pending=True,implementation_credit=False))
        print((out/'probe.stdout').read_text(encoding='utf8').strip())
    except BaseException as failure:
        save(out/'FAILURE.json',dict(status='FAIL2_HTSEQ_REFERENCE_SETUP',error=repr(failure),required_action=warning))
        raise


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-runtime hash checks')
    main()
