# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact peak-workflow profiles and original geometry controls."""
import hashlib,json,shutil,urllib.request,tarfile,io
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/signac-runtime';out=ROOT/'src/main/resources/org/jlinalg/omics/signac-peaks';fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-peak-output'
    assert not out.exists() and not fixture.exists();out.mkdir();fixture.mkdir()
    for folder in [out,fixture]:(folder/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    ranking=json.loads((base/'peaks-ordering-original-3/original/RESULT.json').read_bytes());assert len(ranking['ranking'])==1830 and len(ranking['combined'])==208;save(fixture/'ordering.json',ranking)
    parser=json.loads((base/'peaks-preparation-original-2/original/RESULT.json').read_bytes())['parse'];save(fixture/'parser.json',parser)
    files={};profiles={};versions={}
    for version in ['1.9.0','1.14.0']:
        source=base/('source-intake-1/'+version+'/source/Signac/R/peaks.R');name='peaks-'+version+'.R';shutil.copyfile(source,out/name);files[name]=pin(out/name);profiles['Signac-'+version]=name
    shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi/MIT-LICENSE.txt',out/'Signac-MIT-LICENSE.txt');files['Signac-MIT-LICENSE.txt']=pin(out/'Signac-MIT-LICENSE.txt')
    for version in ['2.2.7.1','2.2.9','2.2.9.1']:
        source=base/('peaks-source-intake-1/'+version);python={p.relative_to(source/'MACS2').as_posix():pin(p)['sha256'] for p in (source/'MACS2').rglob('*') if p.is_file() and p.suffix in ['.py','.pxd']};guard=json.loads((ROOT/'docs/literature/signac-peak-runtime-development/VALIDATION.json').read_bytes())['runtime_guards'][version];versions[version]=dict(python_files=python,compiled_names=sorted(n for n in guard['package_files'] if n.endswith('.so')),original_script_body_sha256=hashlib.sha256((source/'bin/macs2').read_bytes().split(b'\n',1)[1]).hexdigest())
        license=source/'LICENSE';name='MACS2-'+version+'-LICENSE.txt';shutil.copyfile(license,out/name);files[name]=pin(out/name)
    # Original dependency source licenses/attribution accompany the native ordering port.
    for name,url in [('Seqinfo','https://bioconductor.org/packages/3.23/bioc/src/contrib/Seqinfo_1.2.0.tar.gz'),('GenomicRanges','https://bioconductor.org/packages/3.23/bioc/src/contrib/GenomicRanges_1.64.0.tar.gz')]:
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-validation/1.0'})
        with urllib.request.urlopen(request,timeout=90) as response:raw=response.read(20_000_001)
        assert len(raw)<=20_000_000
        target=base/'peaks-ordering-original-3'/(name+'-official.tar.gz');target.write_bytes(raw)
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
            desc=archive.extractfile(name+'/DESCRIPTION').read();(out/(name+'-DESCRIPTION.txt')).write_bytes(desc);files[name+'-DESCRIPTION.txt']=pin(out/(name+'-DESCRIPTION.txt'))
        save(base/'peaks-ordering-original-3'/(name+'-SOURCE.json'),dict(url=url,archive=pin(target),publisher_signature_checked=False))
    licenses=Path('C:/Program Files/R/R-4.6.1/share/licenses')
    for name in ['Artistic-2.0','GPL-2','GPL-3']:
        path=licenses/name
        if path.is_file():shutil.copyfile(path,out/(name+'-LICENSE.txt'));files[name+'-LICENSE.txt']=pin(out/(name+'-LICENSE.txt'))
    save(out/'SOURCE-PROFILES.json',dict(schema_version=1,Signac=profiles,MACS2=versions,required_python='3.9.20',required_packages=dict(numpy='1.24.4'),ordering=ranking['versions'],files=files,source_authentication=False,historical_whole_environment=False))
    print('Prepared exact Signac/MACS profiles, dependency attribution and original peak-output fixtures')


if __name__=='__main__':main()
