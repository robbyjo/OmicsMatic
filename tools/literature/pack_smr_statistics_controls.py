# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package complete original kernel controls and full original result tables as byte-pinned native test fixtures."""
import argparse,csv,gzip,io,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference-home',required=True,type=Path);a=p.parse_args();reference=a.reference_home.resolve();proof=json.loads((reference/'COMPLETED.json').read_bytes());assert proof['status']=='captured72-unchanged-original-SMR-kernel-calls-not-Java-validation';target=ROOT/'src/test/resources/org/jlinalg/mr/smr';assert not target.exists();target.mkdir(parents=True);(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');inputs=json.loads((reference/'inputs.json').read_bytes());results=json.loads((reference/'results.json').read_bytes());assert len(inputs)==len(results)==72;tables=[];base=ROOT/'build/literature-review/smr-runtime'
    for version in ['1.3.1','1.4.3']:
        home=base/('original-probe-'+version+'-1');p=json.loads((home/'COMPLETED.json').read_bytes());assert p['status']=='captured-full-original-SMR-workflows-not-adapter-validation'
        for name in ['single-new','single-old','single-off','single-dense','multi']:
            path=home/name/('original.msmr' if name=='multi' else 'original.smr');raw=path.read_bytes();rows=list(csv.reader(io.StringIO(raw.decode('utf8')),delimiter='\t'));tables.append(dict(version=version,case=name,header=rows[0],rows=rows[1:],original_file=pin(path),program=p['program']))
    data=dict(schema_version=1,kernel_inputs=inputs,kernel_original=results,original_tables=tables,synthetic_inputs=json.loads((base/'synthetic-controls-1/GENERATED.json').read_bytes()),original_qtl_text=(base/'synthetic-controls-1/qtl.qfile').read_text(encoding='utf8'),original_GWAS_text=(base/'synthetic-controls-1/gwas.ma').read_text(encoding='utf8'));path=target/'statistics-controls-v1.json.gz';path.write_bytes(gzip.compress((json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf8'),mtime=0));save(target/'ORIGINS.json',dict(status='captured-original-SMR-controls-not-Java-validation',fixture=pin(path),kernel_cases=72,complete_original_tables=10,source_commits={'1.3.1':'21587e8091b37183e0d7bda418723fafb36c28d0','1.4.3':'eedb1a62ba4c66ab0b09acd0205876955a803f18'},original_kernel_reference_receipt=pin(reference/'COMPLETED.json'),actual_original_calls=proof['actual_original_functions_called'],ratio_diagnostic_arithmetic_is_owned_harness_not_independent_original_function=True,original_HEIDI_tail_not_a_native_implementation=True,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},whole_paper_reproduced=False,whole_goal_complete=False));print('Packed72 complete original covariance cases and10 complete original SMR tables;',path.stat().st_size,'bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original fixture/source checks')
    main()
