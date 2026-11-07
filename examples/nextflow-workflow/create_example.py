# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export a tiny declared Nextflow count workflow; does not execute or install software."""
import argparse,json,sys
from pathlib import Path

WORKFLOW=r'''
nextflow.enable.dsl=2
params.inputs=null
params.outdir=null
process COUNTS {
    tag { sample }
    input:
    tuple val(sample),path(sequence)
    output:
    tuple val(sample),path("${sample}.tsv")
    script:
    """
    awk 'BEGIN{n=0;gc=0} !/^>/{s=toupper(\$0);n+=length(s);gsub(/[^GC]/,"",s);gc+=length(s)} END{printf "%s\\t%d\\t%d\\n","$sample",n,gc}' "$sequence" > "${sample}.tsv"
    """
}
process GATHER {
    publishDir params.outdir,mode:'copy'
    input:
    path tables
    output:
    path 'counts.tsv'
    script:
    'cat *.tsv | LC_ALL=C sort > counts.tsv'
}
workflow {
    samples=Channel.of(tuple('a',file(params.inputs+'/a.fa',checkIfExists:true)),tuple('b',file(params.inputs+'/b.fa',checkIfExists:true)),tuple('c',file(params.inputs+'/c.fa',checkIfExists:true)))
    COUNTS(samples)
    GATHER(COUNTS.out.map{sample,table -> table}.collect())
}
'''

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--engine-profile',required=True,choices=['linux-x86_64-nextflow-'+v for v in ('21.04.1','22.10.6','24.04.4')]);args=parser.parse_args()
    if sys.platform!='linux':raise SystemExit('Run this exporter with Linux Python, including WSL, so all recipe paths are native Linux paths. No analysis was run.')
    root=args.out.absolute();assert root.parent.resolve()==root.parent and not root.exists(),'Use a NEW directory with a canonical existing parent';root.mkdir();project=root/'project';project.mkdir();inputs=project/'inputs';inputs.mkdir()
    (project/'main.nf').write_text(WORKFLOW,encoding='utf-8',newline='\n');(project/'local.config').write_text("process.executor='local'\nprocess.cpus=1\nprocess.memory='64 MB'\nprocess.maxForks=2\nexecutor.cpus=2\nexecutor.memory='1 GB'\n",encoding='utf-8',newline='\n')
    for sample,sequence in [('a','ACGTacgt'),('b','AAATTT'),('c','GCGCgcgcNN')]: (inputs/(sample+'.fa')).write_text('>'+sample+'\n'+sequence+'\n',encoding='utf-8',newline='\n')
    recipe=dict(schema_version=1,engine_profile=args.engine_profile,workflow=dict(path=str(project),entrypoint='main.nf',configs=['local.config'],profiles=[]),resources=dict(reads=dict(path=str(inputs))),parameters=dict(inputs={'$input':'reads'},outdir={'$output':''}),required_tools=[dict(name='awk',path='/usr/bin/awk')],outputs=[dict(path='counts.tsv',kind='file',allow_empty=False)],require_tasks=True,limits={})
    (root/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n',encoding='utf-8',newline='\n');(root/'expected-counts.tsv').write_text('a\t8\t4\nb\t6\t0\nc\t10\t8\n',encoding='utf-8',newline='\n')
    print('Exported synthetic example only: '+str(root/'recipe.json')+'. No runtime installation or analysis was performed.')
if __name__=='__main__':main()
