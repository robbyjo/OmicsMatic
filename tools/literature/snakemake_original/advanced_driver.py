#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise run/script/checkpoint/retry/pipe semantics of the original engine."""
from pathlib import Path
import argparse,json,os,shutil,sys,traceback,uuid
if not __debug__:raise RuntimeError('Advanced source-control assertions must remain enabled')
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from prototype_driver import ROOT,encoded,pin,run

CASES={
'run':('''from pathlib import Path
rule make:
    output: "result.txt"
    run:
        Path(output[0]).write_text("A\\nB\\n")
''','A\nB\n'),
'script':('''rule make:
    output: "result.txt"
    script: "pipeline.py"
''','SCRIPT\n'),
'checkpoint':('''from pathlib import Path
def downstream(wildcards):
    directory = checkpoints.split.get().output[0]
    return expand("work/{part}.txt", part=glob_wildcards(str(directory)+"/{part}.txt").part)
rule gather:
    input: downstream
    output: "result.txt"
    shell: "cat {input:q} > {output:q}"
rule copy:
    input: lambda wildcards: str(checkpoints.split.get().output[0])+"/"+wildcards.part+".txt"
    output: "work/{part}.txt"
    shell: "mkdir -p work; cat {input:q} > {output:q}"
checkpoint split:
    output: directory("chunks")
    run:
        target=Path(output[0]);target.mkdir(parents=True,exist_ok=True)
        (target/"a.txt").write_text("A\\n")
        (target/"b.txt").write_text("B\\n")
''','A\nB\n'),
'retry':('''from pathlib import Path
rule make:
    output: "result.txt"
    run:
        flag=Path("attempted")
        if not flag.exists():
            flag.write_text("first attempt\\n")
            Path(output[0]).write_text("PARTIAL\\n")
            raise RuntimeError("EXPECTED_FIRST_ATTEMPT_FAILURE")
        Path(output[0]).write_text("RECOVERED\\n")
''','RECOVERED\n'),
'pipe':('''rule consume:
    input: "stream.fifo"
    output: "result.txt"
    shell: "cat {input:q} > {output:q}"
rule produce:
    output: pipe("stream.fifo")
    shell: "printf 'A\\nB\\n' > {output:q}"
''','A\nB\n'),
}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    try:
        rows=[]
        for case,(snake,expected) in CASES.items():
            root=out/case;root.mkdir();work=root/'working';work.mkdir();(work/'Snakefile').write_text(snake)
            if case=='script':(work/'pipeline.py').write_text('from pathlib import Path\nPath(snakemake.output[0]).write_text("SCRIPT\\n")\n')
            helpers=root/'helpers';shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1',helpers);events=root/'events';events.mkdir()
            argv=['--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','2','--scheduler','greedy','--forceall','--keep-incomplete','--retries','1' if case=='retry' else '0','--','result.txt']
            request=dict(schema_version=1,run_id=str(uuid.uuid4()),events_directory=str(events),receipt=str(root/'DRIVER.json'),argv=argv);(root/'COMMAND.json').write_bytes(encoded(request))
            env=dict(os.environ,PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',LC_ALL='C.UTF-8',LANG='C.UTF-8',TZ='UTC',XDG_CACHE_HOME=str(root/'cache'))
            result=run(['/usr/bin/prlimit','--as=8589934592','--cpu=120','--fsize=67108864','--nofile=4096','--',sys.executable,'-I','-B',str(helpers/'driver.py'),'--command',str(root/'COMMAND.json')],root,env)
            assert result['exit_code']==0,(case,'Inspect retained original diagnostics')
            observed=(work/'result.txt').read_text()
            # The authored checkpoint input function uses unsorted glob_wildcards.
            # Validate the two tags without imposing a filesystem enumeration order;
            # keep the actual bytes and source input order unchanged in all records.
            if case=='checkpoint':assert sorted(observed.splitlines())==['A','B']
            else:assert observed==expected,(case,'Unexpected exact output')
            receipt=json.loads((root/'DRIVER.json').read_bytes());assert not receipt['collector_errors'];controller=[];counts={}
            for path in events.glob('*.jsonl'):
                data=[json.loads(line) for line in path.read_text().splitlines()];assert data[0]['record_type']=='collector_opened' and data[-1]['record_type']=='collector_closed' and not data[-1]['collector_failed']
                for row in data:
                    if row['record_type']=='original_log_record' and row['pid']==receipt['driver_pid']:
                        record=row['record'];event=record.get('event');counts[str(event)]=counts.get(str(event),0)+1
                        if event in ['job_started','group_info','job_info','job_finished','job_error','group_error','progress']:controller.append(record)
            (root/'CONTROLLER-JOBS.json').write_bytes(encoded(controller));row=dict(case=case,process=result,events=counts,streams=len(receipt['collector_streams']),observed_output_sha256=pin(work/'result.txt')['sha256'],output_contract='Exactly the A/B tag multiset; retain source filesystem enumeration order' if case=='checkpoint' else 'Exact declared synthetic output bytes');rows.append(row);print(json.dumps(row),flush=True)
        (out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_ORIGINAL_ADVANCED_DAG_DRIVER_BEHAVIOR_NOT_JAVA_ADMISSION',cases=rows,producer=pin(Path(__file__)),new_catalog_credit=0,whole_goal_complete=False)));return 0
    except Exception as error:(out/'FAILURE.json').write_bytes(encoded(dict(reason=repr(error),traceback=traceback.format_exc(),required_action='Keep the exact original Snakefile, partial outputs, event streams and diagnostics. Resolve the named source/protocol issue and retry NEW output.')));raise
if __name__=='__main__':raise SystemExit(main())
