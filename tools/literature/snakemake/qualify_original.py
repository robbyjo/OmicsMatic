#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual selected Snakemake engine on complete synthetic local DAGs."""
from pathlib import Path
import argparse,csv,decimal,hashlib,importlib.metadata,json,os,shutil,signal,subprocess,sys,time,traceback
if not __debug__:raise RuntimeError('Original-engine qualification assertions must remain enabled')

SNAKE='''import sys
configfile: "config.yaml"
PYTHON = sys.executable
include: "rules/count.smk"
rule all:
    input: "results/summary.tsv"
rule gather:
    input: expand("work/{sample}.tsv", sample=config["samples"])
    output: "results/summary.tsv"
    benchmark: BENCH_GATHER
    params: python=PYTHON
    shell: "{params.python:q} scripts/runner.py gather {output:q} {input:q}"
'''
COUNT='''rule count:
    input: "reads/{sample}.fa"
    output: "work/{sample}.tsv"
    benchmark: BENCH_COUNT
    params: python=PYTHON
    shell: "{params.python:q} scripts/runner.py count {output:q} {input:q}"
'''
RUNNER='''from pathlib import Path
import hashlib,sys,time
kind,out,*inputs=sys.argv[1:]
# Keep the measured process alive long enough to be sampled; synthetic workload only.
memory=bytearray(4*1024*1024);start=time.monotonic()
while time.monotonic()-start<1.25: hashlib.sha256(memory[:65536]).digest()
target=Path(out);target.parent.mkdir(parents=True,exist_ok=True)
if kind=="count":
    rows=[line.strip() for line in Path(inputs[0]).read_text().splitlines() if line and not line.startswith(">")]
    target.write_text(Path(inputs[0]).stem+"\\t"+str(sum(len(row) for row in rows))+"\\n")
else: target.write_text("sample\\tbases\\n"+"".join(Path(p).read_text() for p in inputs))
'''
EXPECTED='sample\tbases\nalpha\t10\nbeta\t8\n'
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def process(folder,command,env):
    start=time.monotonic();p=None
    with (folder/'engine.stdout').open('xb') as stdout,(folder/'engine.stderr').open('xb') as stderr:
        p=subprocess.Popen(command,cwd=folder,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True)
        (folder/'LAUNCH.json').write_bytes(encoded(dict(command=command,pid=p.pid,process_group=p.pid,producer=pin(Path(__file__)))))
        timed_out=False
        try:
            p.wait(timeout=120)
        except subprocess.TimeoutExpired:timed_out=True
        finally:
            try:os.killpg(p.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            p.wait()
    # Every descendant inherits the owned group for these local synthetic workflows.
    live=[]
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():continue
        try:
            fields=(proc/'stat').read_text().rpartition(') ')[2].split()
            if int(fields[2])==p.pid and fields[0]!='Z':live.append(int(proc.name))
        except (FileNotFoundError,ProcessLookupError,PermissionError,ValueError):continue
    result=dict(command=command,exit_code=p.returncode,timed_out=timed_out,seconds=round(time.monotonic()-start,3),owned_live_after_cleanup=live)
    (folder/'PROCESS.json').write_bytes(encoded(result));assert not timed_out and not live;return result
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    try:
        assert sys.platform=='linux' and importlib.metadata.version('snakemake')=='9.27.1'
        import snakemake.benchmark
        assert pin(Path(snakemake.benchmark.__file__))['sha256']=='2d584d8aa2ad44e4f9b776d787a9454a31704a91fdd6fac00d1430cd066468d7'
        env=dict(os.environ,PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',LC_ALL='C.UTF-8',LANG='C.UTF-8',TZ='UTC')
        rows=[]
        for label,repeat,extended,scheduler,fail in [('standard',False,False,'ilp',False),('repeated',True,False,'greedy',False),('extended',False,True,'greedy',False),('failed-rule',False,False,'greedy',True)]:
            root=out/label;root.mkdir();
            for name in ['rules','reads','scripts']:(root/name).mkdir()
            count='repeat("benchmarks/count-{sample}.tsv",2)' if repeat else '"benchmarks/count-{sample}.tsv"'
            gather='repeat("benchmarks/gather.tsv",2)' if repeat else '"benchmarks/gather.tsv"'
            (root/'Snakefile').write_text(SNAKE.replace('BENCH_GATHER',gather));(root/'rules/count.smk').write_text(COUNT.replace('BENCH_COUNT',count))
            runner=RUNNER
            if fail:runner=runner.replace('kind,out,*inputs=sys.argv[1:]','kind,out,*inputs=sys.argv[1:]\nif kind=="count" and "beta" in inputs[0]:raise RuntimeError("EXPECTED_SYNTHETIC_UPSTREAM_RULE_FAILURE")')
            (root/'scripts/runner.py').write_text(runner);(root/'config.yaml').write_text('samples: [alpha, beta]\n')
            (root/'reads/alpha.fa').write_text('>a1\nACGTAC\n>a2\nAGCT\n');(root/'reads/beta.fa').write_text('>b1\nTTT\n>b2\nCCCCC\n')
            command=['/usr/bin/prlimit','--as=8589934592','--cpu=120','--fsize=67108864','--nofile=4096','--',sys.executable,'-B','-m','snakemake','--snakefile',str(root/'Snakefile'),'--directory',str(root),'--cores','2','--scheduler',scheduler,'--forceall']
            if extended:command+=['--benchmark-extended']
            command+=['results/summary.tsv'];result=process(root,command,env)
            if fail:
                assert result['exit_code']!=0 and 'EXPECTED_SYNTHETIC_UPSTREAM_RULE_FAILURE' in (root/'engine.stderr').read_text();rows.append(dict(case=label,expected_source_failure=True,process=result));continue
            assert result['exit_code']==0 and (root/'results/summary.tsv').read_text()==EXPECTED
            reports=sorted((root/'benchmarks').glob('*.tsv'));assert [p.name for p in reports]==['count-alpha.tsv','count-beta.tsv','gather.tsv']
            expected={};manifest=['pipeline\treplicate\tjob\tbenchmark\trow']
            for report in reports:
                data=list(csv.DictReader(report.open(),delimiter='\t'));assert len(data)==(2 if repeat else 1)
                assert len(data[0])==(18 if extended else 10)
                for index,row in enumerate(data,1):
                    cpu=decimal.Decimal(row['cpu_time']);rss=decimal.Decimal(row['max_rss']);wall=decimal.Decimal(row['s']);assert cpu.is_finite() and rss.is_finite() and min(cpu,rss,wall)>=0
                    key=str(index);value=expected.setdefault(key,dict(cpu_seconds=decimal.Decimal(0),max_job_rss_mib=decimal.Decimal(0),summed_job_wall_seconds=decimal.Decimal(0),jobs=0));value['cpu_seconds']+=cpu;value['max_job_rss_mib']=max(value['max_job_rss_mib'],rss);value['summed_job_wall_seconds']+=wall;value['jobs']+=1
                    manifest.append(label+'\tper-job-iteration-'+key+'\t'+report.stem+'\t'+report.name+'\t'+key)
            (root/'jobs.tsv').write_text('\n'.join(manifest)+'\n');expected={k:{n:str(v) if isinstance(v,decimal.Decimal) else v for n,v in row.items()} for k,row in expected.items()}
            (root/'EXPECTED.json').write_bytes(encoded(expected));rows.append(dict(case=label,process=result,expected=expected,benchmarks={p.name:pin(p) for p in reports},repeated_job_iterations_are_independent_pipeline_replicates=False))
        result=dict(status='PASS_SELECTED_ORIGINAL_ENGINE_COMPLETE_SYNTHETIC_DAGS_BENCHMARKS_AND_RULE_FAILURE',version='9.27.1',source_commit='f79d9eb5e2b679e1191818f24335907b041578be',cases=rows,producer=pin(Path(__file__)),project_adapter_qualified=False,whole_paper_reproduced=False,new_catalog_credit=0,whole_goal_complete=False)
        (out/'COMPLETED.json').write_bytes(encoded(result));print(result['status']);return 0
    except Exception as error:(out/'FAILURE.json').write_bytes(encoded(dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain the full original logs and partial reports. Fix the named prerequisite/source discrepancy and retry NEW output. Engine controls are not paper reproduction or qualified project-adapter evidence.')));raise
if __name__=='__main__':raise SystemExit(main())
