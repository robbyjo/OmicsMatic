#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create a complete synthetic Snakemake request; install or execute nothing."""
from pathlib import Path
import argparse,json,sys

PROFILE='linux-x86_64-snakemake-9.27.1'
IMAGE='docker://condaforge/miniforge3@sha256:d39de5149d9819b186de0748a2117842fc4f7c0a841d6a3e1f99fd9919241dfe'
WARNING='''# Warnings and required actions

Synthetic demonstration only: adding two supplied counts is not an omics
inference or a paper reproduction. Current public-command qualification is
still in progress; do not use this development snapshot for reportable analyses.

This generator executes no software and installs nothing. The actual run follows
the recipe's explicit allow_install permission. False plus missing/incomplete
software means SKIP3: skip the dependent analysis, restore the named permitted
runtime/provider/cache and retry NEW output. If installation is forbidden, keep
the analysis skipped. True permits original deployers to use NEW owned caches;
fresh Conda resolution can change package bytes. No installed Conda prefix is
relocated. Preserve the captured software records and all upstream diagnostics.

FAIL2 admits no completed result for that command. Preserve all inputs, partial
outputs, failed attempts and invalidated markers; correct the named cause and
retry NEW output. Output paths must be separate from input/software roots and
preserve native Linux filesystem semantics. Run trusted workflow code only.
Portable verification checks captured consistency, not biological accuracy or
authenticity. Benchmark time/RSS values are fresh measurements on each replay;
the synthetic summary is compared exactly. Read the command's retained warnings.
'''
COMPUTE='''from pathlib import Path
import csv,json,os,sys
with Path(sys.argv[1]).open(newline="") as stream:
    rows=list(csv.DictReader(stream,delimiter="\\t"))
assert rows==[dict(gene="geneA",count="2"),dict(gene="geneB",count="3")]
assert int(sys.argv[3])==9007199254740993 and float(sys.argv[4])==1e-300
total=sum(int(row["count"]) for row in rows)
Path(sys.argv[2]).write_text("total\\thuge\\ttiny\\n"+str(total)+"\\t"+sys.argv[3]+"\\t"+sys.argv[4]+"\\n")
Path("software.json").write_text(json.dumps(dict(executable=sys.executable,python=sys.version,conda_prefix=os.environ.get("CONDA_PREFIX"),container_fs=Path("/.singularity.d").is_dir()))+"\\n")
'''

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',type=Path,required=True,help='Qualified runtime adoption directory, not its Python environment')
    parser.add_argument('--out',type=Path,required=True,help='NEW native Linux example directory')
    parser.add_argument('--deployment',choices=['preinstalled','conda','apptainer','conda-apptainer'],default='preinstalled')
    parser.add_argument('--providers',type=Path,help='Qualified Conda/Apptainer adoption directory')
    parser.add_argument('--allow-install',action='store_true',help='Explicitly permit original workflow deployers to install into NEW owned caches')
    parser.add_argument('--conda-environments',type=Path,help='Existing complete immutable cache at its original prefix')
    parser.add_argument('--container-images',type=Path,help='Existing complete immutable image cache')
    args=parser.parse_args()
    if sys.platform!='linux':parser.error('SKIP3: create/run the example inside permitted Linux; no output or analysis was created')
    def canonical(path,label,exists=True):
        path=path.absolute()
        if path!=path.resolve() or exists and not path.is_dir():parser.error(label+' must name an existing canonical directory; preserve original installed prefixes')
        return path
    runtime=canonical(args.runtime,'--runtime');ready=json.loads((runtime/'READY.json').read_bytes())
    if ready.get('profile')!=PROFILE:parser.error('Exact selected runtime profile required; no version substitution')
    target=canonical(args.out,'--out',False)
    if target.exists() or target.is_symlink() or not target.parent.is_dir():parser.error('--out requires a NEW named directory with an existing parent')
    managed=args.deployment!='preinstalled'
    if managed and args.providers is None:parser.error('--providers is required for managed deployment')
    if not managed and any([args.providers,args.allow_install,args.conda_environments,args.container_images]):parser.error('Managed provider/installation/cache options require a managed deployment')
    if args.allow_install and (args.conda_environments or args.container_images):parser.error('Installation uses NEW owned caches; omit both borrowed-cache options')
    if args.deployment=='apptainer' and args.conda_environments or args.deployment=='conda' and args.container_images:parser.error('A declared cache must belong to the selected deployment mode')
    providers=canonical(args.providers,'--providers') if managed else None
    environments=canonical(args.conda_environments,'--conda-environments') if args.conda_environments else None
    images=canonical(args.container_images,'--container-images') if args.container_images else None
    python=Path(ready['python'])
    if not managed and not python.is_file():parser.error('SKIP3: declared original Python is unavailable; restore the permitted runtime before creating the preinstalled example')
    protected=[runtime,Path(ready['environment']),*[p for p in [providers,environments,images] if p is not None]]
    if any(target==p or target in p.parents or p in target.parents for p in protected):parser.error('--out overlaps software/cache; choose a separate example directory')
    target.mkdir();work=target/'workflow';work.mkdir();data=target/'data';data.mkdir()
    (data/'counts.tsv').write_text('gene\tcount\ngeneA\t2\ngeneB\t3\n',encoding='utf-8')
    (work/'compute.py').write_text(COMPUTE,encoding='utf-8')
    snake='rule make:\n    input: data=config["counts"], script="compute.py"\n    output: config["destination"]\n    params: huge=config["huge"], tiny=config["tiny"]\n    benchmark: "benchmarks/count.tsv"\n    shadow: "minimal"\n'
    if args.deployment in ['conda','conda-apptainer']:
        (work/'env.yaml').write_text('channels:\n  - conda-forge\ndependencies:\n  - python=3.14.10\n',encoding='utf-8');snake+='    conda: "env.yaml"\n'
    if args.deployment in ['apptainer','conda-apptainer']:snake+='    container: '+json.dumps(IMAGE)+'\n'
    snake+='    shell: "python compute.py {input.data:q} {output:q} {params.huge:q} {params.tiny:q}"\n'
    (work/'Snakefile').write_text(snake,encoding='utf-8')
    request=dict(schema_version=2 if managed else 1,engine_profile=PROFILE,workflow=dict(path=str(work),snakefile='Snakefile',configfiles=[],targets=['make']),resources=dict(data=dict(path=str(data))),parameters=dict(counts={'$input':'data','path':'counts.tsv'},destination={'$output':'summary.tsv'},huge=9007199254740993,tiny=1e-300),environment={},required_tools=[] if managed else [dict(name='python',path=str(python))],outputs=[dict(root='published',path='summary.tsv',kind='file',allow_empty=False,comparison='exact'),dict(root='workflow',path='benchmarks/count.tsv',kind='file',allow_empty=False,comparison='benchmark-measurement')],execution=dict(cores=1,scheduler='greedy',retries=0,latency_wait_seconds=1,notemp=False,benchmark_extended=False,deployment=args.deployment,allow_install=args.allow_install,require_jobs=True),limits=dict(capture_files=200000,capture_bytes=8589934592,file_bytes=1073741824,workspace_bytes=34359738368,wall_seconds=900,address_space_bytes=17179869184,cpu_seconds=900,output_file_bytes=1073741824,open_files=4096,processes=96,threads=512,sampled_rss_bytes=8589934592,poll_millis=25,metadata_bytes=67108864,event_rows=100000,log_bytes=67108864))
    if managed:request['managed_software']=dict(providers=str(providers),conda_environments=str(environments) if environments else None,container_images=str(images) if images else None)
    (target/'recipe.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8')
    (target/'WARNINGS.md').write_text(WARNING,encoding='utf-8')
    print('Created synthetic example: '+str(target)+'\nRead WARNINGS.md, then use the current source build:\njava -jar CURRENT.jar snakemake-tools run --request '+str(target/'recipe.json')+' --runtime '+str(runtime)+' --out '+str(target/'analysis'))
    return 0
if __name__=='__main__':raise SystemExit(main())
