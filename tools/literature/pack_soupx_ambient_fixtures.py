# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve identical original ambient inputs and both unchanged API editions as portable references."""
import gzip,json
from setup_deseq2_reference import ROOT,pin,save


def main():
    target=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-ambient';assert not target.exists();target.mkdir(parents=True);records={};inputs=None
    for version,attempt in [('1.6.2',2),('1.3.0',3)]:
        out=ROOT/f'build/literature-review/soupx-runtime/ambient-controls-{version}-{attempt}';proof=json.loads((out/'COMPLETED.json').read_bytes());assert proof['status']=='captured-original-SoupX-ambient-controls-not-adapter-validation' and proof['preexisting_libraries_unchanged'];raw=(out/'inputs.json').read_bytes();assert pin(out/'inputs.json')==proof['original_inputs']
        if inputs is None:inputs=raw;(target/'inputs-v1.json.gz').write_bytes(gzip.compress(raw,mtime=0))
        else:assert inputs==raw
        controls=(out/'controls.json').read_bytes();assert pin(out/'controls.json')==proof['controls'];path=target/f'original-{version}-v1.json.gz';path.write_bytes(gzip.compress(controls,mtime=0));records[version]=dict(controls=pin(path),original=proof['controls'],completion=pin(out/'COMPLETED.json'),input_objects=proof['original_input_objects'])
    (target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');save(target/'ORIGINS.json',dict(status='original-SoupX-ambient-reference-controls-not-adapter-validation',records=records,inputs=pin(target/'inputs-v1.json.gz'),scope='Identical official1.6.2 toyData counts/labels tested through both unchanged source APIs under R4.6.1/Matrix1.7-5. Exact original RDS returns and warnings are retained privately pending adapter evidence sealing. No paper environment, biological validation, automatic density/gamma equivalence or count-correction implementation is implied.',implementation_credit=False,whole_goal_complete=False));print('Preserved identical inputs and55 original ambient controls')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/fixture checks')
    main()
