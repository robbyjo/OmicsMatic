# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Vendor exact RunSVD functions from retained official CRAN archives, with explicit source-only profiles."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime/source-intake-1'
DEST=ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi'
def pin(data):return dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
def main():
    assert not DEST.exists();DEST.mkdir(parents=True);records=[]
    for version in ['1.6.0','1.7.0','1.9.0','1.14.0','1.17.1']:
        source=BASE/version/'source/Signac/R/dimension_reduction.R';text=source.read_text();start=text.index('RunSVD.default <- function(');brace=text.index('{',start);depth=0;end=None
        for i in range(brace,len(text)):
            if text[i]=='{':depth+=1
            elif text[i]=='}':
                depth-=1
                if depth==0:end=i+1;break
        assert end is not None;body=(text[start:end]+'\n').encode();name=f'RunSVD-{version}.R';(DEST/name).write_bytes(body)
        records.append(dict(edition=version,resource=name,function=pin(body),original_source=source.relative_to(ROOT).as_posix(),original_source_pin=pin(source.read_bytes()),
                            profile='full-installed-package' if version=='1.17.1' else 'unmodified-extracted-function-current-dependencies-not-historical-package'))
    license_text='''Copyright (c) 2021 Tim Stuart

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
'''
    (DEST/'MIT-LICENSE.txt').write_text(license_text,encoding='utf-8',newline='\n')
    (DEST/'SOURCE-PROFILES.json').write_text(json.dumps(dict(schema_version=1,records=records,historical_packages_installed=False),indent=2)+'\n',encoding='utf-8',newline='\n')
    (DEST/'.gitattributes').write_text('*.R -text\n*.json -text\nMIT-LICENSE.txt -text\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(resources=len(records),status='retained-exact-functions-not-historical-package-installations')))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable source intake checks')
    main()
