# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise packaged nf-core source setup on all seven unchanged original archives."""
from pathlib import Path
import argparse,hashlib,importlib.util,io,json,os,shutil,subprocess,sys,tarfile

def require(value,message):
    if not value:raise ValueError(message)
def enc(v):return(json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode()
def pin(path):
    with path.open('rb')as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def run(a):
    out=a.out.absolute();out.mkdir(exist_ok=False);calls=[]
    def call(label,argv,code):
        p=subprocess.run([str(x)for x in argv],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        (out/(label+'.stdout')).write_bytes(p.stdout);(out/(label+'.stderr')).write_bytes(p.stderr)
        calls.append(dict(label=label,exit_code=p.returncode,expected=code))
        require(p.returncode==code,label+': '+p.stderr.decode(errors='replace'))
        if code:require(('SKIP3'if code==3 else'FAIL2').encode()in p.stderr,'Missing prominent refusal: '+label)
        return p
    java=[a.java,'-jar',a.jar,'nfcore-tools'];export=out/'export'
    call('export',java+['--export-source-setup',export],0)
    lock=json.loads((export/'source-lock.json').read_bytes());profiles=lock['profiles'];homes={};files=0
    cache=out/'cache';cache.mkdir();setup=[sys.executable,'-X','utf8','-B',export/'setup.py']
    def args(key,home,report,cache_path=cache):return setup+['--profile',key,'--home',home,'--report',out/report,'--cache',cache_path]
    for index,(key,p)in enumerate(profiles.items()):
        archive=a.intake/(p['pipeline'].split('/')[1]+'-'+p['version'])/'source.tar.gz'
        require(pin(archive)==p['archive'],'Not the complete original archive: '+key)
        target=cache/(key.replace('/','-')+'-'+p['archive']['sha256']+'.tar.gz');shutil.copyfile(archive,target)
        home=out/('source-'+str(index)+'-é space');homes[key]=home
        call('install-'+str(index),args(key,home,'report-install-'+str(index))+['--allow-install'],0)
        call('recheck-'+str(index),args(key,home,'report-recheck-'+str(index)),0)
        call('java-'+str(index),java+['--inspect-source',key,home],0)
        measured=json.loads((out/('report-install-'+str(index))/'COMPLETED.json').read_bytes());require(measured['source_files']==len(p['files'])and not measured['analysis_admitted'],'Expanded source admission')
        files+=len(p['files'])
    require(files==998 and len(homes)==7,'Incomplete original source population')
    key='nf-core/chipseq-1.2.1';home=homes[key]
    call('no-install',args(key,out/'not-installed','report-no-install'),3)
    empty=out/'empty-cache';empty.mkdir()
    call('no-download',args(key,out/'not-downloaded','report-no-download',empty)+['--allow-install'],3)
    call('unsupported',args('nf-core/chipseq-latest',home,'report-unsupported'),3)
    call('wrong-edition',args('nf-core/chipseq-2.0.0',home,'report-wrong-edition'),2)
    call('java-wrong-edition',java+['--inspect-source','nf-core/chipseq-2.0.0',home],2)
    call('existing-report',args(key,home,'report-install-2'),2)
    call('java-no-execution',java+['--request',out/'request.json'],2)
    call('java-unknown-edition',java+['--inspect-source','nf-core/chipseq-latest',home],3)
    # Mutate only owned disposable copies, retain every invalid fixture and report.
    for label,mutation in [
        ('extra-file',lambda p:(p/'source/extra.txt').write_text('extra')),
        ('extra-dir',lambda p:(p/'source/extra-dir').mkdir()),
        ('missing-file',lambda p:(p/'source/main.nf').rename(p/'source/main.nf.retained-original')),
        ('changed-file',lambda p:(p/'source/main.nf').write_text('changed')),
        ('changed-marker',lambda p:(p/'.omicsmatic-nfcore-source.json').write_text('{}')),
    ]:
        damaged=out/('invalid-'+label);shutil.copytree(home,damaged);mutation(damaged)
        call(label,args(key,damaged,'report-'+label),2);call('java-'+label,java+['--inspect-source',key,damaged],2)
    corrupt=out/'corrupt-cache';corrupt.mkdir();filename=key.replace('/','-')+'-'+profiles[key]['archive']['sha256']+'.tar.gz'
    (corrupt/filename).write_bytes(b'not the original')
    call('corrupt-cache',args(key,out/'corrupt-home','report-corrupt-cache',corrupt)+['--allow-install'],2)
    require(not(out/'corrupt-home').exists(),'Bad archive created a home')
    # Production lock admission happens before these extraction routines. Synthetic
    # archives let the path/type defenses execute with a declared test identity.
    spec=importlib.util.spec_from_file_location('exported_nfcore_source_setup',export/'setup.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    extraction=[]
    for label in('traversal','absolute','backslash','symlink','hardlink','duplicate','case-collision','wrong-prefix','extra-empty-dir','missing-file'):
        archive=out/('invalid-archive-'+label+'.tar.gz')
        with tarfile.open(archive,'w:gz')as t:
            root=tarfile.TarInfo('root');root.type=tarfile.DIRTYPE;t.addfile(root)
            if label!='missing-file':
                item=tarfile.TarInfo('root/ok.txt');item.size=2;item.mode=0o644;t.addfile(item,io.BytesIO(b'ok'))
            path={'traversal':'root/../escape','absolute':'/escape','backslash':'root/back\\slash','wrong-prefix':'other/extra'}.get(label,'root/extra')
            extra=tarfile.TarInfo('root/ok.txt'if label=='duplicate'else'root/OK.txt'if label=='case-collision'else path)
            if label in('symlink','hardlink'):extra.type=tarfile.SYMTYPE if label=='symlink'else tarfile.LNKTYPE;extra.linkname='../../escape'
            elif label=='extra-empty-dir':extra.type=tarfile.DIRTYPE
            else:extra.size=2
            if label!='missing-file':t.addfile(extra,io.BytesIO(b'xx')if extra.isfile()else None)
        profile=dict(archive=pin(archive),archive_prefix='root',files={'ok.txt':dict(bytes=2,sha256=hashlib.sha256(b'ok').hexdigest(),executable=False)})
        target=out/('invalid-archive-home-'+label)
        try:module.extract(archive,target,profile,'test-profile')
        except ValueError:pass
        else:raise ValueError('Accepted unsafe archive: '+label)
        require(not target.exists(),'Rejected archive created output: '+label);extraction.append(label)
    if a.online:
        online_cache=out/'online-cache';online_cache.mkdir()
        call('permitted-online-install',args(key,out/'online-home','report-online',online_cache)+['--allow-install','--allow-download'],0)
        call('java-online-home',java+['--inspect-source',key,out/'online-home'],0)
    result=dict(status='PASS_PACKAGED_NFCORE_SOURCE_SETUP_ONLY',platform=sys.platform,producer=pin(a.jar),profiles=7,source_files=files,offline_installations=7,source_rechecks=7,java_original_source_inspections=7,online_installation=a.online,unsafe_archive_refusals=extraction,calls=calls,analysis_admitted=False,new_family_credit=0,whole_goal_complete=False)
    (out/'RESULT.json').write_bytes(enc(result));print(json.dumps({k:v for k,v in result.items()if k not in('calls','producer')},indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in('jar','intake','out'):p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--java',required=True);p.add_argument('--online',action='store_true');a=p.parse_args();run(a)
