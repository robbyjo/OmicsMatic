# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create the exact reviewed execution closure/profile from retained source and installed original dependencies."""
import argparse,json,re,subprocess
from pathlib import Path
from posixpath import normpath,dirname,join
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/loftee-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);baseline=base/'workflow-runtime-baseline-1';environment=json.loads((baseline/'environment.json').read_bytes());all_files=environment['files'];files={n:v for n,v in all_files.items() if n.startswith(('share/ensembl-vep-105.0-1/','lib/perl5/','lib/htslib/')) or re.match(r'lib/[^/]+\.so(?:\.|$)',n) or re.fullmatch(r'bin/(?:perl(?:[0-9.]+)?|samtools|bgzip|tabix)',n)}
    while True:
        before=len(files)
        for name,item in list(files.items()):
            if item['kind']!='symlink':continue
            target=item['target'];rel=target.removeprefix(environment['baseline_root']+'/') if target.startswith('/') else normpath(join(dirname(name),target));assert not rel.startswith('../') and rel in all_files,(name,target);files.setdefault(rel,all_files[rel])
        if len(files)==before:break
    host=json.loads((baseline/'host-and-ELF-dependencies.json').read_bytes())['host_files'];commands={}
    for name in ['gzip','gunzip','zcat']:
        command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-c','import sys,pathlib,hashlib,json;p=pathlib.Path(sys.argv[1]).resolve();r=p.read_bytes();print(json.dumps(dict(path=str(p),bytes=len(r),sha256=hashlib.sha256(r).hexdigest())))','/usr/bin/'+name];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);assert r.returncode==0;value=json.loads(r.stdout);host[value['path']]={k:value[k] for k in ['bytes','sha256']};commands[name]=value;files['bin/'+name]=dict(kind='file',mode=493,**{k:value[k] for k in ['bytes','sha256']},copied_host_command=value['path'])
    source=json.loads((base/'source-intake-1/COMPLETED.json').read_bytes());editions={}
    for key,label in [('grch38-1.0.4','loftee-grch38'),('cited-fork-c8fdde00','loftee38-cited-fork')]:
        data=source['sources'][label];editions[key]=dict(source_commit=data['commit'],repository=data['repository'],source_files=data['members'])
    env=dict(baseline_root=environment['baseline_root'],files=dict(sorted(files.items())),source_environment_total_files=environment['regular_files'],source_environment_total_bytes=environment['total_file_bytes'],execution_closure_bytes=sum(v.get('bytes',0) for v in files.values()),execution_closure_files=len(files),unused_compilers_BLAST_and_other_unreachable_programs_not_exported=True);bioperl=json.loads((baseline/'bioperl.json').read_bytes());assert bioperl['prefix_relocated_files']==0;bioperl['only_exported_BioPerl_tree_required']=True
    profile=dict(schema_version=1,profile='original-VEP105.0-LOFTEE-GRCh38-Linux-x86_64',vep_version='105.0',environment=env,bioperl=bioperl,host_files=host,copied_host_commands=commands,editions=editions,perl_program='bin/perl',vep_program='share/ensembl-vep-105.0-1/vep',vep_include='share/ensembl-vep-105.0-1',allowed_PATH='exact exported runtime environment/bin only',runtime_installation_during_analysis=False,prefix_relocation_policy='Exact text reverse replacement or enumerated NUL-terminated binary fields; all normalized bytes must match original baseline SHA256. New prefixes cannot be longer than the baseline; no other source/binary change is accepted.')
    target=ROOT/'src/main/resources/org/jlinalg/omics/loftee-workflow';assert not target.exists();target.mkdir(parents=True);save(target/'SOURCE-PROFILE.json',profile);(target/'APACHE-2.0.txt').write_bytes((ROOT/'docs/literature/loftee-audit-development/APACHE-2.0.txt').read_bytes());(target/'NOTICE.txt').write_text('Original LOFTEE1.0.4_GRCh38 and the cited fork are Apache-2.0 source plugins, separate from Java code. Original VEP/Perl/BioPerl/dependencies remain separately licensed private runtimes, never bundled in the Java JAR. Preserve original licenses and sources with runtime redistribution. Recorded prefixes may change only through exact reviewed reversal to baseline bytes; profile hashes establish integrity, not authenticity or paper reproduction. Assembly/release labels are caller assertions.\n',encoding='utf8',newline='\n');save(out/'COMPLETED.json',dict(status='prepared-exact-original-LOFTEE-execution-closure-and-relocation-profile-only',profile=pin(target/'SOURCE-PROFILE.json'),closure_files=len(files),closure_bytes=env['execution_closure_bytes'],bioperl_files=bioperl['regular_files'],editions=list(editions),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},variant_analysis_executed=False,implementation_credit=False));print('Prepared exact execution profile:',len(files),'entries;',env['execution_closure_bytes'],'bytes;',bioperl['regular_files'],'BioPerl files')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/runtime closure checks')
    main()
