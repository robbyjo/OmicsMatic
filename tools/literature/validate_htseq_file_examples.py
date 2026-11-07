# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check runnable examples, genuine source errors and failed-commit quarantine through the final root."""
import argparse
import json
import subprocess
import time
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_htseq_reference import DISTRO,linux
from build_htseq_tools_diagnostics import BASE,JAR
from validate_deseq2_inference import JAVA21,JAVA25


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();artifact=pin(JAR)
    homes={r['version']:r['home'] for r in json.loads((BASE/'count-reference-binding-2/COMPLETED.json').read_bytes())['profiles']}
    def invoke(folder,label,request,jvm,inject=False):
        file=folder/(label+'.json');save(file,request)
        argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/java','-Xmx768m','-jar',linux(JAR),'htseq-tools','--request',linux(file)] if jvm=='linux-java21' else [JAVA21 if jvm=='windows-java21' else JAVA25,'-Xmx768m','-jar',str(JAR),'htseq-tools','--request',str(file)]
        stdout=folder/(label+'.stdout');stderr=folder/(label+'.stderr')
        with stdout.open('xb') as a,stderr.open('xb') as b:
            child=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b)
            try:
                if inject:
                    # The caller gives both canonical Windows host and Linux
                    # guest paths. Interfere only in this NEW owned output.
                    destination=folder/(label+'-run');deadline=time.monotonic()+30
                    while not destination.is_dir():
                        if child.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Commit-refusal output was not created in time')
                        time.sleep(.001)
                    marker=destination/'COMPLETED.json';assert not marker.exists();marker.mkdir()
                code=child.wait(timeout=600)
            except BaseException:
                child.terminate();child.wait(timeout=15);raise
        save(folder/(label+'.process.json'),dict(command=argv,exit_code=code,forced_owned_commit_marker_directory=inject));return code
    sources=[]
    for version in ['0.5.3','2.1.2']:
        folder=out/version;folder.mkdir();request_file=folder/'example-request.json';run=folder/('count-run-\u03b1' if version=='0.5.3' else 'count-run')
        argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/python3',linux(ROOT/'examples/htseq-file-count/make_request.py'),
            '--runtime-home',homes[version],'--version',version,'--request',linux(request_file),'--run-output',linux(run)]
        result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
        (folder/'make-request.stdout').write_bytes(result.stdout);(folder/'make-request.stderr').write_bytes(result.stderr);save(folder/'make-request.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0
        request=json.loads(request_file.read_bytes());assert invoke(folder,'count',request,'linux-java21')==0
        assert (run/'counts.tsv').read_text()=='feature_id\tcount\nA\t2\nB\t1\n'
        values=[row.split('\t') for row in (run/'summary.tsv').read_text().splitlines()[1:]];assert [r[1] for r in values]==['1','0','0','1','1']
        for jvm in ['windows-java21','windows-java25','linux-java21']:
            dest=folder/('verify-'+jvm);req=dict(schema_version=1,operation='VERIFY',run=linux(run) if jvm.startswith('linux') else str(run),out=linux(dest) if jvm.startswith('linux') else str(dest))
            assert invoke(folder,'verify-'+jvm,req,jvm)==0
        sources.append(dict(version=version,run=run,request=request))
    current=sources[-1]
    # A genuine original GTF error must expose HTSeq's name and exact retained
    # source stderr, without a misleading RSEM dependency in root diagnostics.
    folder=out/'source-error';folder.mkdir();bad=folder/'missing-id.gtf';bad.write_text('chr1\tsynthetic\texon\t11\t30\t.\t+\t.\tgene_name "missing-id";\n',encoding='utf8')
    request=dict(current['request'],annotation=linux(bad),out=linux(folder/'count-run'))
    assert invoke(folder,'request',request,'linux-java21')==2
    warning=(folder/'count-run/WARNINGS.md').read_text();assert 'Original HTSeq count failed' in warning and 'RSEM' not in warning
    assert 'gene_id' in (folder/'count-run/count/process.stderr').read_text() and not (folder/'count-run/counts.tsv').exists()
    # A zero-exit backend or successful schema check is insufficient when the
    # output commit fails: every provisional report must be quarantined.
    failures=[]
    for operation,jvm in [('COUNT','linux-java21'),('VERIFY','windows-java21'),('VERIFY','linux-java21')]:
        folder=out/('commit-'+operation.lower()+'-'+jvm);folder.mkdir();label='attempt';destination=folder/(label+'-run')
        if operation=='COUNT':request=dict(current['request'],out=linux(destination))
        else:request=dict(schema_version=1,operation='VERIFY',run=linux(current['run']) if jvm.startswith('linux') else str(current['run']),out=linux(destination) if jvm.startswith('linux') else str(destination))
        assert invoke(folder,label,request,jvm,inject=True)==2
        assert (destination/'INCOMPLETE.json').exists() and not (destination/'COMPLETED.json').exists()
        assert not (destination/'verification.json').exists() and not (destination/'report.json').exists() and not (destination/'counts.tsv').exists()
        expected=['counts.tsv','summary.tsv','report.json'] if operation=='COUNT' else ['verification.json']
        assert all((destination/'partial'/name).is_file() for name in expected)
        assert not json.loads((destination/'analysis-status.json').read_bytes())['result_admitted'];failures.append(dict(operation=operation,JVM=jvm))
    assert pin(JAR)==artifact
    save(out/'COMPLETED.json',dict(status='passed-final-HTSeq-runnable-file-examples-and-failure-quarantine',artifact=artifact,
        successful_example_counts=2,example_versions=['0.5.3','2.1.2'],portable_example_verifications=6,Unicode_Python2_output_path=True,
        genuine_source_failures=1,root_diagnostics_correctly_name_HTSeq=True,forced_commit_refusals=failures,all_provisional_reports_quarantined=True,catalog_credit=False))
    print('Passed2 original example counts/6 portable checks,1 genuine named source failure and3 forced-commit quarantine refusals')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original example and no-partial-admission checks')
    main()
