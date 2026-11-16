#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Download exact official Linux-amd64 Sambamba releases into a NEW private directory."""
import argparse,gzip,hashlib,json,os,tarfile,urllib.error,urllib.request,uuid
from pathlib import Path

RELEASES={
 '0.6.6':dict(asset='sambamba_v0.6.6_linux.tar.bz2',archive_sha256='c2b292a521359a0d6f2da58d512894543601865df0d6cb2086d925b8cd0fade9',
  executable_sha256='8b06691f80bc89c733267c523dbbb3a4c36b51cae42ef7b2a171e17c68516dc2',
  source_sha256='212a9224da41df6bbb40c8210a9bba26f27c3a75f8e4193c3d3c40afdecbd6f2',header_version='0.6.6'),
 '0.8.2':dict(asset='sambamba-0.8.2-linux-amd64-static.gz',archive_sha256='2518b957feaf0e75df25aa34342fa19c116dfd3dffcecb75ccd302dce7790d22',
  executable_sha256='bbccbd914ebf8c2b09a5bb6a922dba99ac24c09d25ddd4925f5b2cc8dc359543',
  source_sha256='10f7160db5a1c50296b32e94f7d7570739aa7d90c93fe0981246fe89eab6dd98',header_version='1.0')}
NOTICE="""# Warnings and required actions

This downloads exact original source and Linux-amd64 binary artifacts only.
It does not launch Sambamba, alter system packages, qualify a runtime, execute
an analysis, or establish binary authenticity. Files remain private/local.
The source is GPL-2.0-or-later; the original license is retained unchanged.

Use a permitted Linux x86-64 execution environment, including an explicitly
selected WSL distribution from Windows. Native Windows execution is absent.
Sambamba0.6.6 needs its original Linux shared-library runtime; the selected
0.8.2 asset is static. Supply permitted missing prerequisites rather than
substituting another edition. A project execution adapter is still pending.

The original0.8.2 executable reports version0.8.2 but deliberately writes
header VN:1.0, as its source specifies. Preserve both; do not repair the header
or infer the execution edition from VN alone. Source hashes show exact stored
content, not a reproducible source-to-binary build or a signature.

SKIP3: download unavailable. Skip dependent analysis, supply the exact permitted
artifacts/runtime and retry a NEW directory. FAIL2: no valid download is admitted
for altered artifacts, recording failures or an existing output directory.
Retain all failed attempts; never refresh hashes or overwrite historical files.
"""
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def fetch(url,target,expected):
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-Sambamba-intake'})
    with urllib.request.urlopen(request,timeout=30) as response,target.open('xb') as stream:
        size=0
        while True:
            chunk=response.read(65536)
            if not chunk:break
            size+=len(chunk)
            if size>32*1024*1024:raise ValueError('Original download exceeds 32 MiB')
            stream.write(chunk)
    result=pin(target.read_bytes())
    if result['sha256']!=expected:raise ValueError('Exact official artifact differs: '+target.name)
    return dict(url=url,**result)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog='Download only; no runtime or scientific-analysis admission.')
    p.add_argument('--edition',choices=RELEASES,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    owned=None
    try:
        out=a.out.absolute();out.mkdir(parents=True,exist_ok=False);owned=out
        (out/'WARNINGS.md').write_text(NOTICE,encoding='utf-8',newline='\n')
        spec=RELEASES[a.edition];tag='v'+a.edition
        binary=out/spec['asset'];source=out/'source.tar.gz'
        source_record=fetch('https://codeload.github.com/biod/sambamba/tar.gz/refs/tags/'+tag,source,spec['source_sha256'])
        component_licenses=[]
        with tarfile.open(source) as archive:
            prefix='sambamba-'+a.edition+'/'
            root_license=archive.getmember(prefix+'LICENSE')
            if not root_license.isfile():raise ValueError('Exact root source license is not a regular file')
            license_raw=archive.extractfile(root_license).read();(out/'LICENSE-upstream.txt').write_bytes(license_raw)
            for member in archive.getmembers():
                if member.isfile() and member.name.startswith(prefix) and member.name.endswith('/LICENSE'):
                    relative=Path(member.name[len(prefix):])
                    if relative.is_absolute() or '..' in relative.parts:raise ValueError('Unsafe component license path')
                    payload=archive.extractfile(member).read();target=out/'licenses'/relative
                    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(payload)
                    component_licenses.append(dict(path=target.relative_to(out).as_posix(),**pin(payload)))
        binary_record=fetch('https://github.com/biod/sambamba/releases/download/'+tag+'/'+spec['asset'],binary,spec['archive_sha256'])
        if a.edition=='0.6.6':
            with tarfile.open(binary) as archive:
                members=[m for m in archive.getmembers() if m.isfile()]
                if len(members)!=1 or members[0].name!='sambamba_v0.6.6':raise ValueError('Exact binary archive membership differs')
                raw=archive.extractfile(members[0]).read()
        else:raw=gzip.decompress(binary.read_bytes())
        if raw[:4]!=b'\x7fELF' or pin(raw)['sha256']!=spec['executable_sha256']:raise ValueError('Exact original executable differs')
        (out/'bin').mkdir();executable=out/'bin/sambamba';executable.write_bytes(raw)
        if os.name=='posix':executable.chmod(0o755)
        record=dict(status='DOWNLOADED_EXACT_ORIGINALS_NOT_RUNTIME_OR_ANALYSIS_QUALIFICATION',edition=a.edition,
            header_version=spec['header_version'],binary_archive=binary_record,source_archive=source_record,
            executable=dict(path='bin/sambamba',**pin(raw)),license=dict(path='LICENSE-upstream.txt',**pin(license_raw)),
            component_licenses=component_licenses,
            analysis_executed=False,system_packages_changed=False,new_catalog_credit=0)
        save(out/'DOWNLOADED.json',record);print(json.dumps(record,indent=2),flush=True);return 0
    except Exception as e:
        code=3 if isinstance(e,(urllib.error.URLError,TimeoutError,PermissionError)) else 2
        label='SKIP3_EXACT_SAMBAMBA_DOWNLOAD_UNAVAILABLE' if code==3 else 'FAIL2_EXACT_SAMBAMBA_DOWNLOAD_UNADMITTED'
        if owned is not None:
            try:
                marker=owned/'DOWNLOADED.json'
                if marker.exists():marker.rename(owned/('DOWNLOAD-INVALIDATED-'+uuid.uuid4().hex+'.json'))
                save(owned/'FAILURE.json',dict(status=label,cause=repr(e),required_action=NOTICE))
            except Exception as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        print(label,repr(e),'Read WARNINGS.md/FAILURE.json when present. Preserve artifacts, supply permitted prerequisites and retry a NEW directory. Skip dependent analysis.',flush=True)
        return code
if __name__=='__main__':raise SystemExit(main())
