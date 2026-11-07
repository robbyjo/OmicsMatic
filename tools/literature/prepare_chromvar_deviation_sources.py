# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain unchanged original chromVAR expectation/deviation functions for explicit source profiles."""
import json
from pathlib import Path
from prepare_signac_link_sources import extract,pin
ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations'
BASE=ROOT/'build/literature-review/signac-runtime/motif-dependency-source-2'
def main():
    assert not DEST.exists();DEST.mkdir();records=[]
    for edition in ['1.16.0','1.34.1']:
        source=BASE/('chromVAR-'+edition)/'source/chromVAR/R/compute_deviations.R';raw=source.read_bytes();(DEST/edition).mkdir();functions=[]
        for name in ['compute_deviations_core','compute_expectations_core','compute_deviations_single']:
            function=extract(raw.decode('utf8'),name);resource=edition+'/'+name+'.R';(DEST/resource).write_bytes(function);functions.append(dict(name=name,resource=resource,function=pin(function),original_source=source.relative_to(ROOT).as_posix(),original_source_pin=pin(raw)))
        records.append(dict(profile=edition+('-source' if edition=='1.16.0' else '-package'),edition=edition,functions=functions,current_dependencies_explicit=True,historical_whole_package=False))
    (DEST/'SOURCE-PROFILES.json').write_text(json.dumps(dict(schema_version=1,records=records),indent=2)+'\n',encoding='utf8',newline='\n')
    license_text='''Original chromVAR functions: Copyright (c) 2017 Stanford University.
Authors include Alicia Schep, Jason Buenrostro, Caleb Lareau and William Greenleaf.
The original package declares MIT + file LICENSE; LICENSE names YEAR 2017 and COPYRIGHT Stanford University.

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
    (DEST/'MIT-LICENSE.txt').write_text(license_text,encoding='utf8',newline='\n');(DEST/'.gitattributes').write_text('*.R -text\n*.json -text\n*.txt -text\n',encoding='utf8',newline='\n')
    equal=[(DEST/'1.16.0'/f['name']).with_suffix('.R').read_bytes()==(DEST/'1.34.1'/f['name']).with_suffix('.R').read_bytes() for f in records[0]['functions']]
    print(json.dumps(dict(status='retained-original-chromVAR-expectation-deviation-functions',profiles=2,functions_per_profile=3,functions_byte_identical=equal)))
if __name__=='__main__':main()
