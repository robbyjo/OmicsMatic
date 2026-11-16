#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare packaged Java aggregation/replay with actual original-engine benchmarks."""
from pathlib import Path
import argparse,csv,datetime,hashlib,json,shutil,subprocess,time,traceback
from decimal import Decimal

ROOT=Path(__file__).resolve().parents[3]
if not __debug__:raise RuntimeError('Native benchmark qualification assertions must remain enabled')
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for raw in iter(lambda:stream.read(1048576),b''):digest.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=digest.hexdigest())
def linux(path):
    path=path.resolve();return '/mnt/'+path.drive[0].lower()+path.as_posix()[2:]
def inventory(path):return {p.relative_to(path).as_posix():pin(p) for p in path.rglob('*') if p.is_file()}
def run(out,name,command):
    start=time.monotonic()
    with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
    (out/(name+'.process.json')).write_bytes(encoded(dict(command=command,exit_code=result.returncode,seconds=round(time.monotonic()-start,3))))
    assert result.returncode==0,'Inspect retained command logs: '+name
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--original-controls',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--java',type=Path,required=True);p.add_argument('--wsl',required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    try:
        source=json.loads((a.original_controls/'COMPLETED.json').read_bytes());assert source['status']=='PASS_SELECTED_ORIGINAL_ENGINE_COMPLETE_SYNTHETIC_DAGS_BENCHMARKS_AND_RULE_FAILURE'
        before={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'src/main/java/org/jlinalg/workflow/SnakemakeBenchmarks.java',*sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('SnakemakeBenchmark*.java'))]}
        candidate=out/'candidate.jar';shutil.copyfile(a.jar,candidate);assert pin(candidate)==pin(a.jar)
        (out/'SOURCE-BEFORE.json').write_bytes(encoded(before));results=[]
        for case in ['standard','repeated','extended']:
            original=a.original_controls/case;caller=out/('caller-'+case);caller.mkdir();shutil.copytree(original/'benchmarks',caller/'benchmarks');shutil.copyfile(original/'jobs.tsv',caller/'jobs.tsv')
            request=caller/'request.properties';request.write_text('profile=snakemake-benchmark-9.27.1-v1\nmanifest=jobs.tsv\nbenchmark_directory=benchmarks\nmax_input_bytes=10000000\nmax_manifest_bytes=1000000\nmax_work_bytes=20000000\nmax_files=1000\nmax_rows=10000\nmax_line_characters=1048576\nwall_ms=60000\n',encoding='utf-8',newline='\n')
            native=out/('native-'+case);run(out,case+'-native',[str(a.java),'-jar',str(candidate),'snakemake-tools','benchmark','--request',str(request),'--out',str(native)])
            expected=json.loads((original/'EXPECTED.json').read_bytes());actual=list(csv.DictReader((native/'SUMMARY.tsv').open(encoding='utf-8'),delimiter='\t'));assert len(actual)==len(expected)
            for group in actual:
                key=group['replicate'].removeprefix('per-job-iteration-');reference=expected[key];assert int(group['jobs'])==reference['jobs']
                for metric in ['cpu_seconds','max_job_rss_mib','summed_job_wall_seconds']:assert Decimal(group[metric])==Decimal(reference[metric]),(case,metric)
            preserved=out/('preserved-caller-'+case);assert caller.resolve().is_relative_to(out) and preserved.resolve().is_relative_to(out) and not preserved.exists();old=inventory(caller);caller.rename(preserved);assert old==inventory(preserved)
            win=out/('windows-replay-'+case);run(out,case+'-windows-replay',[str(a.java),'-jar',str(candidate),'snakemake-tools','benchmark','--verify',str(native),'--out',str(win)])
            lin=out/('linux-replay-'+case);run(out,case+'-linux-replay',['wsl.exe','-d',a.wsl,'--','/usr/bin/java','-jar',linux(candidate),'snakemake-tools','benchmark','--verify',linux(native),'--out',linux(lin)])
            again=out/('windows-replay-of-linux-'+case);run(out,case+'-windows-replay-of-linux',[str(a.java),'-jar',str(candidate),'snakemake-tools','benchmark','--verify',str(lin),'--out',str(again)])
            for replay in [win,lin,again]:
                assert (replay/'COMPLETED.properties').is_file()
                for name in ['SUMMARY.tsv','MEASUREMENTS.tsv']:assert (native/name).read_bytes()==(replay/name).read_bytes(),(case,replay,name)
            results.append(dict(case=case,benchmark_files=3,assigned_job_rows=6 if case=='repeated' else 3,groups=len(actual),all_expected_decimals_exact=True,windows_replay=True,linux_replay=True,windows_replay_of_linux_replay=True,original_caller_paths_unavailable=True,original_caller_bytes_preserved=True,per_job_repeat_groups_are_independent_pipeline_replicates=False))
        assert before=={name:pin(ROOT/name) for name in before}
        record=dict(status='PASS_NATIVE_BENCHMARK_ORIGINAL_DAG_REPORT_PARITY_AND_WINDOWS_LINUX_RECOMPUTATION',candidate=pin(candidate),cases=results,producer=pin(Path(__file__)),source_inputs_unchanged=True,original_engine_execution_is_project_adapter_execution=False,whole_paper_reproduced=False,new_catalog_credit=0,whole_goal_complete=False,utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
        (out/'COMPLETED.json').write_bytes(encoded(record));print(record['status']);return 0
    except Exception as error:(out/'FAILURE.json').write_bytes(encoded(dict(reason=repr(error),traceback=traceback.format_exc(),required_action='Retain raw original benchmarks, native outputs and command logs. Resolve the exact source/platform discrepancy and retry NEW output; do not change expected metrics or refresh bundle code identities.')));raise
if __name__=='__main__':raise SystemExit(main())
