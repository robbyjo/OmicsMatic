#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate source-written benchmark controls and independent Decimal expectations."""
from pathlib import Path
import argparse,csv,hashlib,io,json,traceback,zipfile
from decimal import Decimal
if not __debug__:raise RuntimeError('Original-source reference assertions must remain enabled')

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    try:
        import importlib.metadata as metadata
        import snakemake.benchmark as original
        assert metadata.version('snakemake')=='9.27.1'
        source=Path(original.__file__).read_bytes();assert pin(source)['sha256']=='2d584d8aa2ad44e4f9b776d787a9454a31704a91fdd6fac00d1430cd066468d7'
        cases=[];files={}
        for index,(cpu,rss,wall) in enumerate([(0.1,32.25,1.5),(0.2,17.5,4.25),(1.005,5.125,1.9999),(0,0,0.001),(12.345,1234.567,90061.999),(1e-5,1e-5,2e-4),(1e20,2e18,60),(None,None,1)]):
            record=original.BenchmarkRecord(running_time=wall,max_rss=rss,max_vms=100,max_uss=10,max_pss=8,io_in=None,io_out=0,cpu_usage=0.5,cpu_time=cpu)
            record.data_collected=cpu is not None
            buffer=io.StringIO();original.print_benchmark_tsv([record],buffer,False);raw=buffer.getvalue().encode();name='case-'+str(index)+'.tsv';files[name]=raw
            parsed=list(csv.DictReader(io.StringIO(raw.decode()),delimiter='\t'));assert len(parsed)==1
            if cpu is None:expected=dict(outcome='reject-missing-required-metrics')
            else:expected=dict(outcome='valid',wall=parsed[0]['s'],cpu=parsed[0]['cpu_time'],rss=parsed[0]['max_rss'])
            cases.append(dict(file=name,**expected))
        repeated=[]
        for cpu,rss,wall in [(0.1,32.25,1.5),(0.2,17.5,4.25)]:
            record=original.BenchmarkRecord(running_time=wall,max_rss=rss,max_vms=100,max_uss=10,max_pss=8,io_in=0,io_out=0,cpu_usage=0.5,cpu_time=cpu);record.data_collected=True;repeated.append(record)
        buffer=io.StringIO();original.print_benchmark_tsv(repeated,buffer,False);files['two-repeats.tsv']=buffer.getvalue().encode()
        parsed=[next(csv.DictReader(io.StringIO(files[name].decode()),delimiter='\t')) for name in ['case-0.tsv','case-1.tsv']]
        group=dict(cpu_seconds=str(sum((Decimal(row['cpu_time']) for row in parsed),Decimal(0))),max_job_rss_mib=str(max(Decimal(row['max_rss']) for row in parsed)),summed_job_wall_seconds=str(sum((Decimal(row['s']) for row in parsed),Decimal(0))))
        zero=original.BenchmarkRecord(running_time=0,max_rss=1,cpu_usage=1,cpu_time=1);zero.data_collected=True
        try:zero.to_tsv(False)
        except ZeroDivisionError as error:files['source-zero-time-failure.txt']=(repr(error)+'\n').encode()
        else:raise AssertionError('Selected source zero-time behavior changed')
        result=dict(status='SOURCE_WRITTEN_BENCHMARK_CONTROLS_NOT_USER_ANALYSIS',source_commit='f79d9eb5e2b679e1191818f24335907b041578be',source=pin(source),cases=cases,two_jobs_files=['case-0.tsv','case-1.tsv'],two_jobs_same_group=group,repeated_rows_are_separate_jobs=False,unrounded_input_precision_recovered=False,whole_goal_complete=False)
        files['REFERENCE.json']=encoded(result);files['PRODUCER.py']=Path(__file__).read_bytes()
        license_file=Path(original.__file__).parent.parent/'snakemake-9.27.1.dist-info/licenses/LICENSE.md'
        if license_file.is_file():files['LICENSE.md']=license_file.read_bytes()
        with zipfile.ZipFile(args.out/'controls.zip','x',zipfile.ZIP_DEFLATED) as archive:
            for name,raw in files.items():archive.writestr(name,raw)
        (args.out/'COMPLETED.json').write_bytes(encoded(dict(**result,archive=pin((args.out/'controls.zip').read_bytes()),members={name:pin(raw) for name,raw in files.items()})))
        print(result['status']);return 0
    except Exception as error:(args.out/'FAILURE.json').write_bytes(encoded(dict(error=repr(error),traceback=traceback.format_exc())));raise
if __name__=='__main__':raise SystemExit(main())
