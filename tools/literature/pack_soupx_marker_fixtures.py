# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve complete original marker controls as deterministic portable test fixtures."""
import gzip,json
from setup_deseq2_reference import ROOT,pin,save


def main():
    out=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-markers';assert out.exists() and not (out/'ORIGINS-v2.json').exists()
    records={}
    for version in ['1.6.2','1.3.0']:
        source=ROOT/f'build/literature-review/soupx-runtime/marker-controls-{version}-3';proof=json.loads((source/'COMPLETED.json').read_bytes());assert proof['status']=='captured-original-SoupX-marker-controls' and proof['preexisting_libraries_unchanged'] and pin(source/'controls.json')==proof['controls'];raw=(source/'controls.json').read_bytes();target=out/f'original-{version}-v2.json.gz';assert not target.exists();target.write_bytes(gzip.compress(raw,mtime=0));records[version]=dict(fixture=pin(target),original=proof['controls'],original_objects=pin(source/'original-objects.rds'),original_completion=pin(source/'COMPLETED.json'))
    save(out/'ORIGINS-v2.json',dict(status='complete-original-marker-reference-fixtures',records=records,scope='Original calls under R4.6.1 and Matrix1.7-5, not the papers exact historical environment. Diagnostic N=Inf,FDR=Inf calls expose complete statistics; user marker choices remain separate. Original RDS objects and 17-significant-digit statistic strings preserve values hidden by initial JSON display rounding. The v1 fixtures remain as historical evidence. No original code is translated or embedded in Java.',whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable fixture/source checks')
    main()
