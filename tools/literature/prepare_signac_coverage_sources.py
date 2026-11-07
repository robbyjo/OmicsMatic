# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the coverage source profiles, original controls and license notices."""
import gzip
import json
import shutil
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/signac-runtime';original=base/'coverage-original-2'
    out=ROOT/'src/main/resources/org/jlinalg/omics/signac-coverage'
    fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-coverage'
    assert not out.exists() and not fixture.exists();out.mkdir();fixture.mkdir()
    for folder in [out,fixture]:(folder/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    result=original/'original/RESULT.json';controls=json.loads(result.read_bytes())
    assert len(controls['coverage'])==285 and len(controls['annotations'])==270
    assert not any('error' in x['result']['value'] for x in controls['annotations'])
    (fixture/'controls.json.gz').write_bytes(gzip.compress(result.read_bytes(),mtime=0))
    save(fixture/'CAPTURE.json',dict(controls=pin(result),compressed=pin(fixture/'controls.json.gz'),source_profiles=controls['sources'],versions=controls['versions'],historical_whole_environments=False))
    for v in ['1.9.0','1.14.0','1.17.1']:
        for n in ['utilities.R','visualization.R']:shutil.copyfile(original/'original'/v/n,out/(v+'-'+n))
    for n in ['RNG.c','random.c']:shutil.copyfile(original/'official-R'/n,out/('R4.6.1-'+n))
    shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics/signac-peaks/Signac-MIT-LICENSE.txt',out/'Signac-MIT-LICENSE.txt')
    for n in ['GPL-2','GPL-3']:shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics/signac-peaks'/(n+'-LICENSE.txt'),out/(n+'-LICENSE.txt'))
    save(out/'SOURCE-PROFILES.json',dict(schema_version=1,status='native-original-coverage-data-profile-not-historical-whole-environments',
         source_versions=['1.9.0','1.14.0','1.17.1'],dependencies=controls['versions'],R_downloads=json.loads((original/'DOWNLOADS.json').read_bytes()),
         files={p.name:pin(p) for p in out.iterdir() if p.is_file() and p.name!='.gitattributes'},execution_authentication=False))
    print('Prepared3 original coverage source profiles and622 original controls')


if __name__=='__main__':main()
