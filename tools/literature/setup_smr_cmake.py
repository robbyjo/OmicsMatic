# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a publisher-hash-checked CMake Linux wheel in a NEW private workspace; no global install."""
import argparse,json,subprocess,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def linux(path):
    p=path.resolve();return '/mnt/'+p.drive[0].lower()+'/'+p.as_posix().split(':/',1)[1]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_CMAKE_UNAVAILABLE',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},analysis_admitted=False,implementation_credit=False,required_action='If permitted, rerun to a NEW output with --allow-install; otherwise skip dependent source compilation and analysis. This helper only unpacks the declared CMake3.31.10 manylinux x86_64 wheel into the workspace and health-checks its original binary. No pip, shared environment or global install is modified.')
    try:
        assert a.allow_install,'Installation not enabled';url='https://pypi.org/pypi/cmake/3.31.10/json'
        with urllib.request.urlopen(url,timeout=60) as r:raw=r.read(2000001);assert len(raw)<=2000000;(out/'pypi.json').write_bytes(raw)
        meta=json.loads(raw);choices=[f for f in meta['urls'] if f['filename'].endswith('.whl') and 'manylinux2014_x86_64' in f['filename']];assert len(choices)==1;f=choices[0]
        with urllib.request.urlopen(f['url'],timeout=90) as r:raw=r.read(60000001);assert len(raw)<=60000000
        assert byte_pin(raw)['sha256']==f['digests']['sha256'] and len(raw)==f['size'];wheel=out/f['filename'];wheel.write_bytes(raw);target=out/'wheel';target.mkdir();members={}
        with zipfile.ZipFile(wheel) as z:
            assert len(z.namelist())==len(set(z.namelist()))
            for entry in z.infolist():
                name=PurePosixPath(entry.filename);assert not name.is_absolute() and '..' not in name.parts and ':' not in entry.filename and '\\' not in entry.filename
                if entry.is_dir():continue
                path=target/entry.filename;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(entry));members[entry.filename]=pin(path)
        binary=target/'cmake/data/bin/cmake';assert binary.is_file();command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--',linux(binary),'--version'];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=30);(out/'health.stdout').write_bytes(r.stdout);(out/'health.stderr').write_bytes(r.stderr);save(out/'health-process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0 and b'cmake version 3.31.10' in r.stdout;state.update(status='prepared-original-private-CMake-only',wheel=pin(wheel),publisher_record=f,members=members,binary=binary.relative_to(ROOT).as_posix())
    except Exception as error:state['detail']=str(error)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+state['required_action']+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('detail',''));return 0 if state['status']=='prepared-original-private-CMake-only' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable dependency/archive checks')
    raise SystemExit(main())
