#!/usr/bin/env python3
"""Admit original generated-background operation health on a pinned genomic home.

Runs original full known+de novo+comparison reports on synthetic custom FASTA.
Never changes a home, installs packages, downloads data or patches scientific
source. Old and new algorithms have separate admissions. Preserve failed output.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import install_homer_genome as genome

ROOT=Path(__file__).resolve().parents[2]
RAW=Path(__file__).with_name('homer-generated-lock.json').read_bytes()
LOCK=json.loads(RAW);LOCK_SHA=hashlib.sha256(RAW).hexdigest()
MARKER='.jlinalg-homer-generated.json'
RECOVERY=('Required action: use an already admitted Linux x86_64 genomic home for the exact version; '
    'core and annotation homes do not admit genomic backgrounds. Run python3 tools/literature/admit_homer_generated.py '
    '--home PRIVATE_GENOMIC_HOME --profile linux-x86_64-genome-5.1 --mode generated-old --out NEW_ADMISSION. '
    'Select generated-new only for5.1;use separate NEW admission paths for each mode/version. '
    'Old preparsing additionally needs /usr/bin/chmod and its libraries. If these dependencies are unavailable '
    'or forbidden, SKIP the affected generated analysis,exit3;continue independent supplied-background analyses. '
    'This command never installs or downloads. Read admission.json,WARNINGS.md andhealth/original.stderr. '
    'Preserve failed reports and homes;correct the named cause and use NEW paths. '+genome.RECOVERY)


def bind(path):
    path=Path(path);real=path.resolve(strict=True)
    if not str(path).startswith(('/usr/','/lib/','/lib64/','/bin/')) or not real.is_file():
        raise genome.core.Unavailable('Unsupported generated-background dependency: '+str(path))
    return dict(real_path=str(real),bytes=real.stat().st_size,sha256=genome.core.sha(real))


def additional(home,out,mode):
    files={}
    for name in LOCK['modes'][mode]['additional_tools']:
        path=Path(name)
        if not path.is_file() or not os.access(path,os.X_OK):
            raise genome.core.Unavailable('Generated-background helper unavailable: '+name)
        files[name]=bind(path)
        label='extra-'+path.name+'-libraries'
        genome.execute(['/usr/bin/ldd',name],home,out,label)
        output=(out/(label+'.stdout')).read_text()
        if 'not found' in output:raise genome.core.Unavailable('Generated-background shared library unavailable')
        for line in output.splitlines():
            for item in re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)',line):files[item]=bind(item)
    return files


def verify(out,home,profile,mode,host):
    genome.verify_home(home,profile,host)
    meta=json.loads((out/MARKER).read_text())
    expected=dict(schema_version=1,tool=LOCK['tool'],home=str(home),admission=str(out),profile=profile,
                  mode=mode,version=genome.LOCK['profiles'][profile]['version'],host=host,
                  lock_sha256=LOCK_SHA,base_lock_sha256=genome.LOCK_SHA,
                  base_marker_sha256=genome.core.sha(home/genome.MARKER))
    if any(meta.get(k)!=value for k,value in expected.items()):
        raise genome.core.Unavailable('Generated operation profile,mode,host,path orbase admission changed')
    files=meta.get('additional_files',{})
    if len(files)>32 or not set(LOCK['modes'][mode]['additional_tools'])<=set(files):
        raise genome.core.Unavailable('Generated operation host utility closure missing')
    for name,value in files.items():
        if bind(name)!=value:raise genome.core.Unavailable('Generated operation host binding changed: '+name)
    h=meta.get('health',{})
    if h.get('status')!=LOCK['health']['status'] or h.get('targets')!=64 or h.get('known_models')!=2 or h.get('selected_background',0)<2 or not h.get('learned_and_comparison_reports') or h.get('original_call',{}).get('exit_code')!=0:
        raise genome.core.Unavailable('Original generated operation health incomplete')
    for key in ['genome_sha256','targets_sha256','models_sha256']:
        if h.get(key)!=LOCK['health'][key]:raise genome.core.Unavailable('Generated health fixture identity differs')
    return meta


def health(out,home,mode,version):
    work=out/'health';work.mkdir()
    generator=ROOT/'examples/homer-genome-workflow/prepare_inputs.py'
    spec=importlib.util.spec_from_file_location('generated_health_fixtures',generator)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    inputs=module.prepare(work/'fixtures')
    for source,target in [('genome.fa','genome.fa'),('targets.bed','targets.bed')]:
        (work/target).write_bytes((inputs/source).read_bytes())
    (work/'models.motif').write_bytes(base64.b64decode(LOCK['health']['models_base64']))
    identities={name+'_sha256':genome.core.sha(work/file) for name,file in [('genome','genome.fa'),('targets','targets.bed'),('models','models.motif')]}
    if any(identities[k]!=LOCK['health'][k] for k in identities):raise ValueError('Generated health fixture hash differs')
    command=['/usr/bin/perl',str(home/'bin/findMotifsGenome.pl'),'targets.bed','genome.fa','original-out',
        '-size','given','-len','8','-S','1','-mis','1','-mknown','models.motif','-mcheck','models.motif',
        '-gc','-nlen','1','-cache','8','-p','1','-nofacts','-keepFiles','-dumpFasta','-preparsedDir','private-preparsed']
    if mode=='generated-old':
        command.extend(['-N','512','-preparse'])
        if version=='5.1':command.append('-useOldBg')
    else:command.extend(['-useNewBg','-N','256','-NN','5000','-numBins','5','-numSubBins','5','-ikmer','2'])
    call=genome.execute(command,home,work,'original',seconds=300,maximum=1073741824)
    directory=work/'original-out'
    for name in ['seq.tsv','group.adj','knownResults.txt','knownResults.html','homerResults.html','homerMotifs.motifs8','homerMotifs.all.motifs']:
        path=directory/name
        if not path.is_file() or not path.stat().st_size:raise ValueError('Original generated health omitted final output: '+name)
    stderr=(work/'original.stderr').read_text()
    if 'Job finished' not in stderr or any(token in stderr for token in ['Illegal division','Could not open',"Can't open",'not found','uninitialized','ERROR: outstripped','ERROR IN TREE']):
        raise ValueError('Original generated health did not finalize cleanly;inspect original.stderr')
    table=[line.split('\t') for line in (directory/'knownResults.txt').read_text().splitlines()]
    if len(table)!=3 or not any(row[0]=='common-implanted-core' and float(row[5])>=48 for row in table[1:]):
        raise ValueError('Original generated health did not recover known implanted signal')
    if not list((directory/'homerResults').glob('*.svg')):raise ValueError('Original generated comparison SVGs missing')
    population={'0':0,'1':0}
    for line in (directory/'group.adj').read_text().splitlines():
        fields=line.split('\t');population[fields[1]]+=1
    if population['1']!=64 or population['0']<2:raise ValueError('Original generated health population invalid')
    return dict(status=LOCK['health']['status'],targets=64,selected_background=population['0'],known_models=2,
        learned_and_comparison_reports=True,original_call=call,**identities,
        scope='Actual original full pipeline health;unseeded selected count is observed,not guaranteed. OldN means target+background;newN means background. Shortfalls retained explicitly. No Java generated CLI or nativepopulation audit is established by this setup health alone.')


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',type=Path,required=True);parser.add_argument('--profile',required=True,choices=list(genome.LOCK['profiles']))
    parser.add_argument('--mode',required=True,choices=list(LOCK['modes']));parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args(argv);owns=False;code=0;record={};out=None
    try:
        out=genome.core.private(args.out);home=genome.core.private(args.home);host=genome.core.host()
        version=genome.LOCK['profiles'][args.profile]['version']
        if version not in LOCK['modes'][args.mode]['versions']:raise genome.core.Unavailable('Requested original generated algorithm is unavailable in '+version)
        if genome.LOCK_SHA!=LOCK['base_lock_sha256']:raise ValueError('Generated operation expects a different basegenomic lock')
        if out==home or out in home.parents or home in out.parents:raise ValueError('Admission and runtime home must be nonnested')
        if out.exists():
            meta=verify(out,home,args.profile,args.mode,host);print(json.dumps(dict(status='verified-existing-generated-operation-admission',marker_sha256=genome.core.sha(out/MARKER),home=str(home),mode=args.mode)));return 0
        out.mkdir(parents=True);owns=True;genome.verify_home(home,args.profile,host);base_marker=genome.core.sha(home/genome.MARKER)
        files=additional(home,out,args.mode);observed=health(out,home,args.mode,version)
        genome.verify_home(home,args.profile,host)
        if base_marker!=genome.core.sha(home/genome.MARKER) or any(bind(n)!=v for n,v in files.items()):raise ValueError('Generated operation dependency changed during admission')
        meta=dict(schema_version=1,tool=LOCK['tool'],home=str(home),admission=str(out),profile=args.profile,mode=args.mode,
            version=version,host=host,lock_sha256=LOCK_SHA,base_lock_sha256=genome.LOCK_SHA,
            base_marker_sha256=base_marker,additional_files=files,health=observed)
        genome.core.write(out/MARKER,meta);verify(out,home,args.profile,args.mode,host)
        record=dict(status='admitted-original-generated-background-operation',home=str(home),mode=args.mode,
            profile=args.profile,marker_sha256=genome.core.sha(out/MARKER),health=observed,
            scope='No basehome mutation,installation,download,scientificsource patch or Java-generated-CLI claim.')
    except (genome.core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-unavailable-generated-operation',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY)
    if owns:
        genome.core.write(out/'admission.json',record)
        (out/'WARNINGS.md').write_text('# HOMER GENERATED '+('ADMISSION SKIPPED' if code==3 else 'ADMISSION FAILED' if code else 'ADMISSION SCOPE')+'\n\n'+record.get('cause',record.get('scope',''))+'\n\n'+RECOVERY+'\n')
    print(json.dumps({k:v for k,v in record.items() if k!='health'}),file=sys.stderr if code else sys.stdout,flush=True)
    return code


if __name__=='__main__':raise SystemExit(main())
