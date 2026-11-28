# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create an explicit request from complete local sample/contrast sheets; no analysis or install."""
import argparse,csv,json
from pathlib import Path

def read(path,columns):
    with Path(path).open(encoding='utf-8',newline='')as stream:
        reader=csv.DictReader(stream)
        if reader.fieldnames!=columns:raise ValueError('Require exact columns: '+','.join(columns))
        rows=list(reader)
    if not rows or any(None in row or any(value is None for value in row.values())for row in rows):raise ValueError('Require complete nonempty unmodified rows')
    return rows

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['samples','contrasts','source-directory','plugins-directory','engine-directory','container-home','image-set','fasta','gtf','out']:
        parser.add_argument('--'+name,required=True)
    parser.add_argument('--miso-read-length',type=int,required=True);parser.add_argument('--miso-plot-gene',action='append',required=True);parser.add_argument('--rmats-read-length',type=int,required=True)
    pairing=parser.add_mutually_exclusive_group(required=True);pairing.add_argument('--unpaired-statistics',action='store_true');pairing.add_argument('--rmats-pairings',help='JSON map contrast ID -> [{treatment: biological ID, control: biological ID}, ...]')
    parser.add_argument('--maximum-raw-p',default='0.05');parser.add_argument('--minimum-absolute-psi',default='0.05');parser.add_argument('--rmats-maximum-rows',type=int,default=1000000);parser.add_argument('--quantification-maximum-rows',type=int,default=1000000)
    parser.add_argument('--executor-cpus',type=int,default=4);parser.add_argument('--executor-memory-bytes',type=int,default=17179869184);parser.add_argument('--task-cpus',type=int,default=2);parser.add_argument('--task-memory-bytes',type=int,default=6442450944);parser.add_argument('--task-seconds',type=int,default=21600);parser.add_argument('--limits',help='Complete JSON object of engine/process/capture capacities; no automatic data reduction')
    args=parser.parse_args();rows=read(args.samples,['sample','fastq_1','fastq_2','strandedness','condition']);samples={}
    for row in rows:
        sample=samples.setdefault(row['sample'],dict(id=row['sample'],condition=row['condition'],strandedness=row['strandedness'],libraries=[]))
        if sample['condition']!=row['condition']or sample['strandedness']!=row['strandedness']:raise ValueError('Conflicting biological metadata; identifiers are never repaired')
        sample['libraries'].append(dict(fastq_1=row['fastq_1'],fastq_2=row['fastq_2']))
    contrasts=read(args.contrasts,['contrast','treatment','control']);contrasts=[dict(id=row['contrast'],treatment=row['treatment'],control=row['control'])for row in contrasts]
    limits=dict(wall_seconds=21600,address_space_bytes=68719476736,cpu_seconds=21600,workspace_bytes=274877906944,capture_bytes=274877906944,file_bytes=34359738368,capture_files=200000,metadata_bytes=67108864,java_heap_mib=2048,java_cpus=4,processes=256,threads=8192,sampled_rss_bytes=34359738368,poll_millis=1000)
    if args.limits:limits=json.loads(Path(args.limits).read_text(encoding='utf-8'))
    result=dict(schema_version=1,pipeline_profile='nf-core/rnasplice-1.0.4',engine_profile='linux-x86_64-nextflow-24.04.4')
    for key in ['source_directory','plugins_directory','engine_directory','container_home','image_set','fasta','gtf']:
        value=getattr(args,key)
        if not value.startswith('/')or'\\'in value:raise ValueError(key+' must retain its absolute Linux producer path')
        result[key]=value
    result.update(samples=list(samples.values()),contrasts=contrasts,executor_cpus=args.executor_cpus,executor_memory_bytes=args.executor_memory_bytes,task_cpus=args.task_cpus,task_memory_bytes=args.task_memory_bytes,task_seconds=args.task_seconds,miso_read_length=args.miso_read_length,miso_plot_genes=args.miso_plot_gene,rmats_read_length=args.rmats_read_length,rmats_paired_statistics=not args.unpaired_statistics,rmats_pairings={}if args.unpaired_statistics else json.loads(Path(args.rmats_pairings).read_text(encoding='utf-8')),rmats_maximum_rows=args.rmats_maximum_rows,quantification_maximum_rows=args.quantification_maximum_rows,limits=limits,rmats_maximum_p=args.maximum_raw_p,rmats_minimum_absolute_psi=args.minimum_absolute_psi)
    with Path(args.out).open('x',encoding='utf-8',newline='\n')as stream:json.dump(result,stream,ensure_ascii=False,indent=2,allow_nan=False);stream.write('\n')
    print('Request created; no analysis performed. Review every sample, contrast, model choice and capacity before execution.')

if __name__=='__main__':main()
