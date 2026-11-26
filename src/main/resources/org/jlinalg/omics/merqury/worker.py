"""Owned orchestration of unchanged Merqury/Meryl programs; never installs software."""
import json,os,subprocess,sys,traceback
from pathlib import Path

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as f:json.dump(value,f,indent=2,sort_keys=True);f.write('\n')

def main():
    root=Path(sys.argv[1]);spec=json.loads((root/'effective.json').read_bytes());work=root/'engine/work';work.mkdir(parents=True);logs=root/'engine/commands';logs.mkdir();exports=root/'exports';exports.mkdir();context=json.loads((root/'runtime.json').read_bytes())
    env={str(k):str(v)for k,v in context['environment'].items()};env.update(HOME=str(work),TMPDIR=str(work),MERQURY='../../sources/merqury',OMP_NUM_THREADS=str(spec['settings']['threads']),OPENBLAS_NUM_THREADS='1',LC_ALL='C',LANG='C',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null')
    calls=[]
    def run(label,command,stdout=None):
        save(logs/(label+'-COMMAND.json'),command);outpath=logs/(label+'-stdout')if stdout is None else stdout
        with outpath.open('xb')as out,(logs/(label+'-stderr')).open('xb')as err:
            p=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=out,stderr=err,env=env,cwd=work);save(logs/(label+'-PROCESS.json'),dict(pid=p.pid));code=p.wait()
        save(logs/(label+'-RESULT.json'),dict(exit_code=code));calls.append(dict(label=label,exit_code=code))
        if code:raise RuntimeError('Original '+label+' failed with exit '+str(code)+'; inspect all original logs and outputs')
    # Input names are controlled staging names, preserving original bytes and identifiers.
    for item in spec['inputs']:
        source=root/item['captured'];target=work/item['staged'];assert not target.exists();target.symlink_to(Path('../../')/item['captured'],target_is_directory=item['kind']=='meryl')
    mode=spec['mode'];settings=spec['settings']
    if mode=='count-kmers':
        reads=[v for v in spec['inputs']if v['role']=='reads'];fofn=work/'reads.fofn';fofn.write_text(''.join(v['staged']+'\n'for v in reads),encoding='utf-8')
        env['SLURM_MEM_PER_NODE']=str(settings['count_memory_gib']*1024);dbs=[]
        for i,item in enumerate(reads,1):
            run('count-'+str(i),[context['bash'],'../../sources/merqury/build/count.sh',str(settings['k']),'reads.fofn','0',str(i)])
            stem=item['staged']
            for suffix in ['.fastq.gz','.fq.gz','.fasta','.fa','.fasta.gz','.fa.gz']:
                if stem.endswith(suffix):stem=stem[:-len(suffix)]
            dbs.append(stem+'.k'+str(settings['k'])+'.'+str(i)+'.meryl')
        (work/'counted.fofn').write_text(''.join(v+'\n'for v in dbs),encoding='utf-8')
        run('union-counts',[context['bash'],'../../sources/merqury/build/union_sum.sh',str(settings['k']),'counted.fofn','reads'])
    elif mode=='evaluate':
        assemblies=[v['staged']for v in spec['inputs']if v['role'].startswith('assembly')]
        run('evaluate',[context['bash'],'../../sources/merqury/merqury.sh','reads.meryl',*assemblies,'result'])
        required=['result.qv','completeness.stats'if spec['edition']=='1.1'else'result.completeness.stats','result.spectra-asm.hist']
        for i,_ in enumerate(assemblies,1):
            name='asm'+str(i);required += ['result.'+name+'.spectra-cn.hist','result.'+name+'.qv',name+'_only.bed',name+'_only.tdf'if spec['edition']=='1.1'else name+'_only.wig']
            required += ['result.'+name+'.spectra-cn.'+kind+'.png'for kind in ['ln','fl','st']]
            if settings['false_duplications']:run('false-duplications-'+str(i),[context['bash'],'../../sources/merqury/eval/false_duplications.sh','result.'+name+'.spectra-cn.hist'])
        required += ['result.spectra-asm.'+kind+'.png'for kind in ['ln','fl','st']]
        if len(assemblies)==2:required += ['result.spectra-cn.'+kind+'.png'for kind in ['ln','fl','st']]
        missing=[n for n in required if not(work/n).is_file()]
        if missing:raise RuntimeError('Original exit0 left incomplete requested outputs: '+repr(missing))
    elif mode=='build-hapmers':
        args=['mat.meryl','pat.meryl']
        if any(v['role']=='child'for v in spec['inputs']):args+=['child.meryl']
        if not settings['filter_parents']:args+=['-no-filt']
        run('build-hapmers',[context['bash'],'../../sources/merqury/trio/hapmers.sh',*args])
    else:raise RuntimeError('Unknown explicitly typed Merqury mode')
    # Complete fresh logical exports are later compared with Java equations and raw inputs.
    databases=[]
    for n,p in enumerate(sorted(work.glob('*.meryl'))):
        if not p.is_dir():raise RuntimeError('Non-directory Meryl output: '+p.name)
        resolved=p.resolve();assert resolved.is_relative_to(root.resolve()),'Meryl link escapes owned capture'
        output=exports/('db-%04d.tsv'%n);run('export-%04d'%n,[context['meryl'],'print',p.name],stdout=output);databases.append(dict(database='engine/work/'+p.name,table='exports/'+output.name))
    save(exports/'DATABASES.json',databases)
    source_mode=context.get('source_mode','original')
    save(root/'CANDIDATE.json',dict(status='MERQURY_PROCESS_ONLY_PENDING_FULL_NATIVE_AUDIT',mode=mode,edition=spec['edition'],calls=calls,databases=len(databases),analysis_admitted=False,source_mode=source_mode,source_modified=source_mode!='original'))

if __name__=='__main__':
    try:main()
    except BaseException as e:
        root=Path(sys.argv[1])
        if not(root/'WORKER-FAILURE.json').exists():save(root/'WORKER-FAILURE.json',dict(error=repr(e),traceback=traceback.format_exc(),analysis_admitted=False))
        raise
