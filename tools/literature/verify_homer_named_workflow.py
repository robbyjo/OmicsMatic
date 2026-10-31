#!/usr/bin/env python3
"""Run two packaged named-annotation examples and two fresh original comparisons.

Full named annotation needs several GB of RAM and minutes per run. Java handles
its process and workspace bounds. The verifier avoids walking Java's live staging
directory during atomic output publication; independent originals use live bounds.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
import install_homer_named as named

ROOT=Path(__file__).resolve().parents[2]


def options(home,genome,kind,stats,peaks,out):
    return dict(home=str(home),profile=named.LOCK['profile'],out=str(out),genome=genome,
        **{'reference-build':genome,'annotation-release':'v7.0','peaks':str(peaks),'peak-format':kind,
           'peak-unknown-strand':'original-plus','duplicate-ids':'retain-ordinal','resource-bounds':'retain-original',
           'tss-resource-anchors':'retain-original','feature-ties':'retain-original','tss-ties':'retain-original',
           'missing-tss-contig':'retain-NA','annotation-stats':stats,'timeout':'600',
           'maximum-upstream-memory-bytes':'8589934592','workspace-bytes':'1073741824'})


def table(path):
    lines=path.read_text().splitlines();header=lines[0].split('\t');rows=[s.split('\t') for s in lines[1:]]
    if len(header)!=19 or any(len(row)!=19 for row in rows):raise ValueError('Incomplete original19-column schema')
    return header[1:],sorted(rows,key=lambda row:row[0])


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['home','jar','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--java',default='/usr/bin/java');args=parser.parse_args(argv);out=named.core.private(args.out)
    if out.exists():raise ValueError('Verifier output exists; choose NEW directory')
    out.mkdir(parents=True);code=0;record={}
    try:
        host=named.core.host();home=named.safe(args.home);jar=named.core.private(args.jar);meta=json.loads((home/named.MARKER).read_text());resources=named.safe(Path(meta['resource_root']));named.verify_home(home,resources,host);jar_hash=named.core.sha(jar);marker_hash=named.core.sha(home/named.MARKER)
        path=ROOT/'examples/homer-named-annotation/prepare_inputs.py';spec=importlib.util.spec_from_file_location('named_workflow_examples',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);cases=module.prepare(resources,out/'inputs');proofs=[]
        for case in cases:
            genome=case['genome'];work=out/genome;work.mkdir();output=work/'result';o=options(home,genome,case['peak_format'],case['annotation_stats'],out/'inputs'/case['peaks'],output);named.core.write(work/'options.json',o)
            command=[args.java,'-Xmx1536m','-jar',str(jar),'homer-tools','named-annotate','--options-file',str(work/'options.json')];began=time.monotonic()
            with (work/'java.stdout').open('xb') as stdout,(work/'java.stderr').open('xb') as stderr:
                result=subprocess.run(command,cwd=work,stdout=stdout,stderr=stderr,timeout=900)
            session=json.loads((output/'session.json').read_text());status=json.loads((output/'analysis-status.json').read_text())
            if result.returncode==3 or status['exit_code']==3:raise named.core.Unavailable('Packaged named workflow skipped: '+session.get('error','inspect '+str(output/'WARNINGS.md')))
            if result.returncode!=0 or status['exit_code']!=0 or session.get('complete_native_feature_TSS_and_category_population_audit')!='passed':raise ValueError('Packaged named workflow failed; inspect '+str(output/'WARNINGS.md'))
            if session['audited_original_rows']!=case['records']:raise ValueError('Packaged named workflow omitted input records')
            # Replay the exact admitted staged population through unchanged original code.
            reference=work/'fresh-original';reference.mkdir();shutil.copyfile(output/'stage-peaks.pos',reference/'peaks.pos');call=named.execute(home,reference,genome,600,8589934592,1073741824)
            if table(output/'original.stdout')!=table(reference/'original.stdout'):raise ValueError('Complete original named rows differ from fresh original reference')
            if case['annotation_stats']=='original-query-contigs' and (output/'original-annotation-stats.tsv').read_bytes()!=(reference/'annotation-stats.tsv').read_bytes():raise ValueError('Complete original named category-statistics bytes differ')
            if named.core.sha(jar)!=jar_hash or named.core.sha(home/named.MARKER)!=marker_hash:raise ValueError('Verifier executable/admission changed')
            named.verify_home(home,resources,host);proofs.append(dict(genome=genome,peak_format=case['peak_format'],records=case['records'],status='passed-complete-packaged-native-audit-and-fresh-original-reference',seconds=time.monotonic()-began,command=command,reference_call=call,original_stdout_sha256=named.core.sha(output/'original.stdout'),statistics=case['annotation_stats']));named.core.write(out/'progress.json',proofs);print(genome+' complete packaged/original named annotation matched',flush=True)
        record=dict(status='passed-two-packaged-named-protocols-and-two-fresh-original-references',jar_sha256=jar_hash,marker_sha256=marker_hash,protocols=proofs,scope='Explicit5.1/v7.0 default named region annotation. Every19-column row and optional complete original statistics bytes match; native complete resource feature/TSS/count/bp audits pass. This does not certify P-value calibration, metadata namespace, paper database edition or other HOMER operations.')
    except (named.core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=named.RECOVERY);named.core.write(out/'verification.json',record);(out/'WARNINGS.md').write_text('# NAMED WORKFLOW '+('SKIPPED' if code==3 else 'FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',record.get('scope',''))+'\n\n'+named.RECOVERY+'\n');print(json.dumps({k:v for k,v in record.items() if k!='protocols'}),file=sys.stderr if code else sys.stdout,flush=True);return code


if __name__=='__main__':raise SystemExit(main())
