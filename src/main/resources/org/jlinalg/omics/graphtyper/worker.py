# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original integrated callers or unchanged edition-matched legacy scripts; no model reimplementation."""
import json,os,shlex,subprocess,sys,traceback
from pathlib import Path

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')

def main():
    root=Path(sys.argv[1]);engine=root/'engine';request=json.loads((root/'request.json').read_bytes());effective=json.loads((engine/'effective.json').read_bytes());runtime=json.loads((root/'runtime.json').read_bytes());profile=json.loads((engine/'profile.json').read_bytes());s=request['settings'];programs=runtime['programs'];env=dict(os.environ);env.update(runtime['environment']);env.update(HOME=str(engine/'home'),TMPDIR=str(engine/'tmp'),LC_ALL='C',LANG='C');outputs=[];commands=[]
    (engine/'home').mkdir();(engine/'tmp').mkdir();logs=engine/'logs';logs.mkdir()
    def run(label,argv,cwd):
        label=f'{len(commands):06d}-'+label;save(logs/(label+'-COMMAND.json'),dict(argv=list(map(str,argv)),cwd=str(cwd)))
        with(logs/(label+'-stdout')).open('xb')as out,(logs/(label+'-stderr')).open('xb')as err:result=subprocess.run(list(map(str,argv)),cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
        save(logs/(label+'-RESULT.json'),dict(exit_code=result.returncode));commands.append(label)
        if result.returncode:raise RuntimeError('Original GraphTyper/helper failed: '+label+' exit '+str(result.returncode)+'; preserve every log and all provisional output; no result admitted')
    inputs=engine/'inputs';fasta=inputs/'reference.fa';bamlist=engine/'bamlist';bamlist.write_text(''.join(str(inputs/name)+'\n'for name in effective['alignment_files']))
    # The exact supplied indexes are captured beside the owned literal alignment paths.
    for target_i,region in enumerate(request['regions']):
        contig,start,end=region['contig'],region['start'],region['end'];text=f'{contig}:{start}-{end}';target=engine/f'target-{target_i:05d}';target.mkdir();final=target/'original-results'
        if not profile['legacy']:
            argv=[programs['graphtyper'],'genotype',str(fasta),'--sams='+str(bamlist),'--region='+text,'--threads='+str(s['threads']),'--output='+str(final),'--no_cleanup']
            if s['mode']!='discovery':argv+=[('--vcf='if s['mode']=='genotype-only'else'--prior_vcf=')+str(inputs/'sites.vcf.gz')]
            run('integrated-genotype',argv,target)
            for role,paths in [('genotypes',sorted(final.glob('*/*.vcf.gz'))),('input_sites',sorted((final/'input_sites').glob('*/*.vcf.gz')))]:
                if role=='genotypes'and not paths:raise RuntimeError('Complete original genotype output population absent')
                for path in paths:outputs.append(dict(target=target_i,role=role,path=path.relative_to(root).as_posix()))
        else:
            pipeline=Path(runtime['pipeline']);slice_size=s['legacy_slice_bases'];pad=s['legacy_pad_bases'];is_v14=profile['tag']=='v1.4';roles=profile['final_roles'];opts=f"--threads={s['threads']} --minimum_variant_support={s['minimum_variant_support']} --minimum_variant_support_ratio={s['minimum_variant_support_ratio']}";config=dict(GENOME=str(fasta),VCF=''if s['mode']=='discovery'else str(inputs/'sites.vcf.gz'),GRAPHTYPER=programs['graphtyper'],TABIX=runtime['environment']['PATH'].split(':')[0]+'/tabix',SAMTOOLS=runtime['environment']['PATH'].split(':')[0]+'/samtools',PARALLEL=runtime['environment']['PATH'].split(':')[0]+'/parallel',VT='',TMP_FORMAT=str(target/'tmp/calling.XXXXXX'),TOP_DIR=str(pipeline),REGION_START=f'{contig}:{start}',REGION_FILE='',REGION_SIZE=end-start+1,NUM_THREADS=s['threads'],NUM_THREADS_SV_CALLING=1,NUM_SLICES_RUNNING=s['threads'],CLEAN_UP=0,CHROMOSOMES=contig,GENOTYPE_ONLY=int(s['mode']=='genotype-only'),INITIALIZE_GRAPH_WITH_VCF=int(s['mode']!='discovery'),RESULTS=str(final/'results'),GRAPHTYPER_THREADS=s['threads'],GRAPHTYPER_COMMON_OPTS=opts,SLICE_SIZE=slice_size,PAD_SIZE=pad,EXTRA_SV_PADDING=200000,SV_RESULTS_DIR='sv_results',SV_RESULTS_DIR_SUFFIX_SAMPLE1=0,BAMSHRINK='',SMALL_SAMPLE_SIZE=2,GRAPHTYPER_SMALL_SAMPLE_SIZE_OPTS=opts,GRAPHTYPER_POPULATION_OPTS=opts)
            cfg=target/'actual-config.sh';cfg.write_text('set -e\nset -o pipefail\n'+''.join(k+'='+shlex.quote(str(v))+'\n'for k,v in config.items()));save(target/'CONFIGURATION.json',config);(target/'tmp').mkdir()
            if s['legacy_merge_policy']=='original-node-no-sort':
                run('original-node',['/usr/bin/bash',pipeline/'node_script.sh',cfg,bamlist,f'{contig}:{start}'],target)
                for role in roles:
                    directory=final/'results'if is_v14 else target/role
                    paths=sorted(directory.glob('*/*.vcf.gz'))
                    if not paths:raise RuntimeError('Complete original legacy final population absent: '+role)
                    for path in paths:outputs.append(dict(target=target_i,role=role,path=path.relative_to(root).as_posix()))
            else:
                # The per-slice scripts place their outputs relative to dirname(bamlist).
                local_list=target/'bamlist';local_list.write_bytes(bamlist.read_bytes())
                for role in roles:(target/role/contig).mkdir(parents=True)
                for position in range(start,end+1,slice_size):run('original-slice',['/usr/bin/bash',pipeline/'call_script.sh',cfg,local_list,f'{contig}:{position}'],target)
                for role in roles:
                    paths=[target/role/contig/f'{p:09d}-{p+slice_size-1:09d}.vcf.gz'for p in range(start,end+1,slice_size)]
                    if any(not p.is_file()for p in paths):raise RuntimeError('Complete original legacy slice population absent: '+role)
                    output=final/role/contig/'joint.vcf.gz';output.parent.mkdir(parents=True)
                    run('original-sorted-merge-'+role,[programs['graphtyper'],'vcf_concatenate',*paths,'--output='+str(output)],target)
                    run('index-'+role,[programs['tabix'],str(output)],target);outputs.append(dict(target=target_i,role=role,path=output.relative_to(root).as_posix()))
    save(engine/'OUTPUTS.json',dict(status='ORIGINAL_GRAPH_CALLER_EXIT_SUCCESS_PROVISIONAL_ONLY',outputs=outputs,commands=commands,author_recipe_qualified=False,analysis_admitted=False))
if __name__=='__main__':
    try:main()
    except BaseException as error:save(Path(sys.argv[1])/'WORKER-FAILURE.json',dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),analysis_admitted=False));raise
