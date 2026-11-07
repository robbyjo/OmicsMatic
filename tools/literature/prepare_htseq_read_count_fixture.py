# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain complete actual-count tables and original parsed inputs for native prepared-read tests."""
import gzip
import json
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,pin


def main():
    primary=BASE/'count-original-1';extra=BASE/'count-fraction-original-1'
    report=json.loads((primary/'COMPLETED.json').read_bytes());additional=json.loads((extra/'COMPLETED.json').read_bytes())
    assert (report['total_original_calls'],report['successful_original_calls'],report['retained_original_failures'],report['native_prepared_cases'])==(42,40,2,38)
    assert additional['actual_original_calls']==additional['successful_original_calls']==additional['prepared_cases']==2
    cases=[]
    for row in report['cases']:
        if row['native_deterministic_audit']:
            folder=primary/row['id'];cases.append(dict(id=row['id'],counts=row['counts'],
                prepared=json.loads((folder/'prepared-units.json').read_bytes()),source_count_stdout=pin((folder/'original-count.stdout').read_bytes())))
    for row in additional['cases']:
        folder=extra/row['source_folder'];cases.append(dict(id=row['id'],counts=row['counts'],
            prepared=json.loads((folder/'prepared-units.json').read_bytes()),source_count_stdout=pin((folder/'original-count.stdout').read_bytes())))
    assert len(cases)==40
    result=dict(schema_version=1,HTSeq='2.1.2',cases=cases,actual_original_count_calls=44,
        successful_original_count_calls=42,retained_source_failures=2,original_seeded_random_cases_not_natively_replayed=2,
        independent_native_SAM_GTF_parser=False,independent_native_pairing=False,
        scope='Actual original SAM/GTF count tables. Native inputs were parsed/paired by original HTSeq; only downstream classification and deterministic counting are independently replayed.')
    raw=(json.dumps(result,indent=2,ensure_ascii=False)+'\n').encode('utf8')
    folder=ROOT/'src/test/resources/org/jlinalg/sequence/htseq-read-count';assert not folder.exists();folder.mkdir(parents=True)
    (folder/'original-counts.json.gz').write_bytes(gzip.compress(raw,mtime=0))
    (folder/'.gitattributes').write_text('*.gz binary\n*.json text eol=lf\n',encoding='ascii')
    statistics=dict(cases=40,source_gene_fields=sum(len(c['counts']['genes']) for c in cases),
        source_summary_fields=sum(len(c['counts']['summary']) for c in cases),
        prepared_units=sum(len(c['prepared']['units']) for c in cases))
    (folder/'PROVENANCE.json').write_text(json.dumps(dict(schema_version=1,result=pin(raw),compressed=pin((folder/'original-counts.json.gz').read_bytes()),
        actual_original_calls=44,original_successes=42,original_failures=2,**statistics,
        original_source_preparation=True,native_SAM_GTF_parser_or_pairing=False),indent=2)+'\n',encoding='utf8',newline='\n')
    profile=ROOT/'src/main/resources/org/jlinalg/omics/htseq-read-count';assert not profile.exists();profile.mkdir(parents=True)
    source=BASE/'source-intake-2/2.1.2/source/htseq-2.1.2'
    files={}
    for name,target in [('LICENSE','LICENSE'),('HTSeq/scripts/count.py','count.py'),
        ('HTSeq/scripts/count_features/count_features_per_file.py','count_features_per_file.py'),
        ('HTSeq/scripts/count_features/reads_io_processor.py','reads_io_processor.py'),
        ('HTSeq/scripts/count_features/reads_stats.py','reads_stats.py'),('HTSeq/features.py','features.py'),('HTSeq/scripts/utils.py','count-utils.py')]:
        body=(source/name).read_bytes();(profile/target).write_bytes(body);files[target]=dict(original_path=name,**pin(body))
    (profile/'.gitattributes').write_text('* -text\n',encoding='ascii')
    (profile/'SOURCE-PROFILE.json').write_text(json.dumps(dict(schema_version=1,source_profile='HTSeq-2.1.2-prepared-read-counting',
        files=files,license='GPL-3.0',upstream_version='2.1.2',original_control_fixture=pin(raw),actual_original_SAM_GTF_count=True,
        native_prepared_read_classification_and_deterministic_count=True,native_SAM_GTF_parser=False,native_pairing=False,
        native_random_assignment=False,historical_whole_packages=False,
        scope='Original filter precedence, mate strand transforms, missing-NH short circuit, unknown-chromosome unit refusal, NONE/ALL/FRACTION and overlapping summary categories. Prepared input parsing/pairing remain original-source capture.'),indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps(dict(status='retained-actual-original-count-and-prepared-input-fixture',**statistics,result_sha256=pin(raw)['sha256'])))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original control fixture checks')
    main()
