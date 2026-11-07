# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual native prepared-file analyses against original component references and recovery controls."""
import argparse
import csv
import io
import json
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin,linux
from setup_deseq2_reference import save


def text_table(rows,features):
    header='feature_id\tcontig\tstart\tend\tstrand\n' if features else 'peak_id\tcontig\tstart\tend\n'
    return header+''.join(('duplicate gene' if features else 'peak Δ '+str(i+1))+'\t'+r['contig']+'\t'+str(r['start'])+'\t'+str(r['end'])+('\t'+r['strand'] if features else '')+'\n' for i,r in enumerate(rows))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact-build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--jvms',nargs='+',choices=['windows-java21','windows-java25','linux-java21'],default=['windows-java21','windows-java25'])
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    proof=json.loads((args.artifact_build/'COMPLETED.json').read_bytes());jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    controls=ROOT/'build/literature-review/chipseeker-runtime/nearest-original-3';plan=json.loads((controls/'PLAN.json').read_bytes());original=json.loads((controls/'ORIGINAL.json').read_bytes())
    by_id={r['id']:r for r in plan['cases']};references={r['case_id']:r for r in original['cases'] if r['version']=='1.48.1'}
    sources=[Path(__file__),controls/'PLAN.json',controls/'ORIGINAL.json'];bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    for name in bound:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    records=[];comparisons=0
    for jvm in args.jvms:
        root=out/jvm;root.mkdir();to_path=linux if jvm=='linux-java21' else str
        java=[str(JAVA21/'java.exe')] if jvm=='windows-java21' else ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'] if jvm=='windows-java25' else ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java']
        def run(label,peaks,features,extra,expected):
            folder=root/label;folder.mkdir();primary=folder/'peaks Δ input.tsv';annotation=folder/'features original.tsv'
            primary.write_bytes(peaks.encode('utf8') if isinstance(peaks,str) else peaks);annotation.write_bytes(features.encode('utf8') if isinstance(features,str) else features)
            source_before=[pin(primary),pin(annotation)];result=folder/'result'
            if label=='existing-output':result.mkdir();(result/'sentinel').write_bytes(b'original output\n')
            options=dict(input=primary.name,features=annotation.name,**{'reference-build':'declared-reference','coordinates':'one-based-inclusive','out':'result'})
            for key,value in zip(extra[::2],extra[1::2]):options[key.removeprefix('--')]=value
            request=folder/'request.json';body=json.dumps(dict(schema_version=1,options=options),ensure_ascii=False,indent=2)+'\n'
            if label=='duplicate-option':body=body.replace('"options": {','"options": {"coordinates":"one-based-inclusive",',1)
            request.write_text(body,encoding='utf8',newline='\n');request_before=pin(request)
            command=[*java,'-Xmx256m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-cp',to_path(jar),'org.jlinalg.cli.ChipSeekerNearestCli','--request',to_path(request)]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
            (folder/'stdout.log').write_bytes(r.stdout);(folder/'stderr.log').write_bytes(r.stderr)
            row=dict(JVM=jvm,label=label,command=command,expected_exit=expected,exit_code=r.returncode);records.append(row);save(folder/'process.json',row)
            assert r.returncode==expected,(jvm,label,r.returncode,r.stderr.decode('utf8',errors='replace'))
            assert [pin(primary),pin(annotation)]==source_before
            assert pin(request)==request_before
            if expected:
                assert b'REQUIRED ACTION:' in r.stderr and (b'SKIP3' if expected==3 else b'FAIL2') in r.stderr
                assert b'component completed' not in r.stdout
                if label=='existing-output':assert sorted(p.name for p in result.iterdir())==['sentinel'] and (result/'sentinel').read_bytes()==b'original output\n'
                elif result.exists():
                    assert not json.loads((result/'analysis-status.json').read_bytes())['result_admitted']
                    assert not (result/'annotations.tsv').exists() and not (result/'source-retained.tsv').exists()
            else:
                assert b'WARNING:' in r.stderr and json.loads((result/'analysis-status.json').read_bytes())['result_admitted']
                assert (result/'inputs/peaks.tsv').read_bytes()==primary.read_bytes() and (result/'inputs/features.tsv').read_bytes()==annotation.read_bytes()
                assert (result/'inputs/options-request.json').read_bytes()==request.read_bytes()
            return result
        for case_id in ['unstranded-0000-TSS','random-unstranded-0000-all','unstranded-0010-TSS']:
            case=by_id[case_id];options=case['options'];extra=[]
            for option,key in [('ignore-overlap','ignore_overlap'),('ignore-upstream','ignore_upstream'),('ignore-downstream','ignore_downstream')]:extra.extend(['--'+option,str(options[key]).lower()])
            extra.extend(['--overlap',options['overlap'].upper()])
            result=run(case_id,text_table(case['peaks'],False),text_table(case['features'],True),extra,0)
            report=json.loads((result/'report.json').read_bytes())
            assert report['settings']==dict(same_strand=False,overlap=options['overlap'].upper(),ignore_overlap=options['ignore_overlap'],ignore_upstream=options['ignore_upstream'],ignore_downstream=options['ignore_downstream'])
            assert report['capacities']==dict(input_bytes_per_file=67108864,peaks=100000,features=100000,operations=50000000)
            assert report['R_runtime_used_by_this_analysis'] is False and report['coordinate_system']=='one-based-inclusive'
            oracle=references[case_id]['output'];expected={i:(int(f),d) for i,f,d in zip(oracle['retained_input_ordinals'],oracle['index_tokens'],oracle['distance_tokens'])}
            ledger=list(csv.DictReader(io.StringIO((result/'annotations.tsv').read_text(encoding='utf8')),delimiter='\t'))
            assert len(ledger)==len(case['peaks'])
            for i,row in enumerate(ledger,1):
                peak=case['peaks'][i-1];assert row['input_ordinal']==str(i) and row['peak_id']=='peak Δ '+str(i)
                assert (row['contig'],row['start'],row['end'])==(peak['contig'],str(peak['start']),str(peak['end']))
                if i not in expected:assert row['disposition']=='NO_FEATURE' and row['distance_to_tss']=='NaN' and row['source_feature_ordinal']=='0'
                else:
                    feature,distance=expected[i];assert row['source_feature_ordinal']==str(feature) and row['distance_to_tss']==distance
                    if feature>len(case['features']):assert row['disposition']=='BOUNDARY_DUMMY' and row['feature_id']==''
                    else:assert row['disposition']=='MATCHED' and row['feature_id']=='duplicate gene'
            retained=list(csv.DictReader(io.StringIO((result/'source-retained.tsv').read_text(encoding='utf8')),delimiter='\t'))
            assert [int(r['input_ordinal']) for r in retained]==oracle['retained_input_ordinals'];comparisons+=len(oracle['index_tokens'])
        peaks='peak_id\tcontig\tstart\tend\np\tx\t10\t20\n';features='feature_id\tcontig\tstart\tend\tstrand\ng\tx\t100\t200\t+\n'
        cases=[('wrong-header',peaks.replace('peak_id','name'),features,[],2),('BED-zero',peaks.replace('\t10\t20','\t0\t20'),features,[],2),
            ('wildcard-feature',peaks,features.replace('\t+','\t*'),[],3),('integer-boundary',peaks.replace('\t10\t20','\t2147483640\t2147483647'),features,[],3),
            ('operation-capacity',peaks,features,['--max-operations','1'],2),('empty-query','peak_id\tcontig\tstart\tend\n',features,[],2),
            ('malformed-UTF8',b'peak_id\tcontig\tstart\tend\np\t\xff\t10\t20\n',features,[],2),('unknown-option',peaks,features,['--arbitrary-command','bad'],2),
            ('duplicate-option',peaks,features,['--coordinates','one-based-inclusive'],2),('existing-output',peaks,features,[],2)]
        for label,p,f,extra,expected in cases:run(label,p,f,extra,expected)
    assert all(pin(ROOT/name)==wanted for name,wanted in bound.items()) and pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='passed-actual-native-ChIPseeker-prepared-file-workflows-and-recovery-controls',artifact=proof['artifact'],source_pins=bound,JVMs=args.jvms,
        successful_analyses=3*len(args.jvms),expected_skips=2*len(args.jvms),expected_failures=8*len(args.jvms),original_retained_row_comparisons=comparisons,records=records,
        full_package_or_paper_reproduced=False,implementation_credit=False))
    print(json.dumps(dict(JVMs=args.jvms,successful_analyses=3*len(args.jvms),expected_skips=2*len(args.jvms),expected_failures=8*len(args.jvms),compared_original_rows=comparisons)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/source/file-result checks')
    main()
