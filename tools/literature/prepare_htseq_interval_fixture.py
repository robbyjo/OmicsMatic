# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the exact original interval fixture and GPL source profile for native comparison."""
import gzip
import json
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,pin


def main():
    controls=BASE/'interval-original-3'
    report=json.loads((controls/'COMPLETED.json').read_bytes())
    raw=(controls/'RESULT.json').read_bytes()
    assert pin(raw)==report['result'] and report['status']=='collected-original-HTSeq-interval-step-controls'
    fixture=ROOT/'src/test/resources/org/jlinalg/sequence/htseq-intervals'
    profile=ROOT/'src/main/resources/org/jlinalg/omics/htseq-intervals'
    assert not fixture.exists() and not profile.exists()
    fixture.mkdir(parents=True)
    profile.mkdir(parents=True)
    (fixture/'.gitattributes').write_text('*.gz binary\n*.json text eol=lf\n',encoding='ascii')
    (fixture/'original-controls.json.gz').write_bytes(gzip.compress(raw,mtime=0))
    source=BASE/'source-intake-2/2.1.2/source/htseq-2.1.2'
    files={}
    for name,target in [('LICENSE','LICENSE'),('HTSeq/__init__.py','HTSeq-init.py'),('src/HTSeq/_HTSeq.pyx','HTSeq-core.pyx')]:
        body=(source/name).read_bytes()
        (profile/target).write_bytes(body)
        files[target]=dict(original_path=name,**pin(body))
    record=dict(schema_version=1,source_profile='HTSeq-2.1.2-GenomicArrayOfSets-steps',
        official_project='https://github.com/htseq/htseq',
        documentation='https://htseq.readthedocs.io/en/latest/htseqcount.html',
        license='GPL-3.0',upstream_version='2.1.2',files=files,
        source_archive=json.loads((BASE/'source-intake-2/COMPLETED.json').read_bytes())['editions'][-1]['archive'],
        original_controls_result=pin(raw),original_steps=True,documented_modes_over_original_steps=True,
        original_htseq_count_executed=False,full_HTSeq_API=False,historical_whole_packages=False,
        scope='Immutable native interval-set index and documented set resolution for explicitly supplied aligned blocks. SAM/GTF intake, pairing, flags/MAPQ/NH, nonunique policies and custom paper pipelines remain separate. Zero-length set-array intervals are refused as in the original.')
    (profile/'SOURCE-PROFILE.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8',newline='\n')
    (profile/'.gitattributes').write_text('* -text\n',encoding='ascii')
    (fixture/'PROVENANCE.json').write_text(json.dumps(dict(schema_version=1,profile=record['source_profile'],
        result=pin(raw),compressed=pin((fixture/'original-controls.json.gz').read_bytes()),populations=43,
        candidate_queries=919,original_successful_queries=538,original_query_refusals=44,
        original_insertion_refusals=15,queries_not_run_after_insertion_refusal=337,original_steps=1214,
        mode_resolutions=1614,original_count_SAM_GTF_run=False),indent=2)+'\n',encoding='utf8',newline='\n')
    print('Retained43 original populations:538 successful queries/1214 spans,44 query refusals and15 insertion refusals;337 candidate queries unexecuted after insertion refusal')


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable original fixture hash checks')
    main()
