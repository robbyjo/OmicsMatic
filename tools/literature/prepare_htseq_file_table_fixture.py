# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain185 literal count-table outcomes from the sealed five-edition reference checkpoint."""
import gzip
import json
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin


def main():
    checkpoint=ROOT/'docs/literature/htseq-read-count-development';proof=json.loads((checkpoint/'VALIDATION.json').read_bytes())
    assert pin(checkpoint/'controls.zip')==proof['archive'];files=unpack(checkpoint/'controls.zip',proof)
    prefix='build/literature-review/htseq-runtime/';cases=[]
    for name in ['count-original-1','count-fraction-original-1','historical-py27-counts-2','historical-py39-counts-1']:
        for row in json.loads(files[prefix+name+'/COMPLETED.json'])['cases']:
            version=row.get('version','2.1.2');sub=version+'-'+row['id'] if 'version' in row else row.get('source_folder',row['id'])
            raw=files[prefix+name+'/'+sub+'/original-count.stdout']
            cases.append(dict(id=version+'-'+row['id'],version=version,nonunique=row['config']['nonunique'],
                source_exit_code=row.get('exit_code',0),raw=raw.decode('utf8'),counts=row['counts'],raw_pin=byte_pin(raw)))
    assert len(cases)==185 and sum(c['source_exit_code']==0 for c in cases)==171
    folder=ROOT/'src/test/resources/org/jlinalg/sequence/htseq-file-count';assert not folder.exists();folder.mkdir(parents=True)
    raw=(json.dumps(dict(schema_version=1,cases=cases),indent=2)+'\n').encode('utf8')
    (folder/'original-tables.json.gz').write_bytes(gzip.compress(raw,mtime=0))
    save(folder/'PROVENANCE.json',dict(parent_validation=pin(checkpoint/'VALIDATION.json'),parent_archive=proof['archive'],
        fixture=byte_pin(raw),original_outcomes=185,successful_source_tables=171,original_source_errors=14,
        scope='Literal tables from reused tiny populations/options, not185independent biological datasets. Table-format validation does not independently replay SAM/GTF parsing/pairing.'))
    (folder/'.gitattributes').write_text('*.gz binary\n*.json text eol=lf\n',encoding='ascii')
    print('Retained171 actual original source tables and14 original error outputs for the closed table audit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable sealed reference checks')
    main()
