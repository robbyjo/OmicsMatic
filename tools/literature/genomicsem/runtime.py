"""Pinned original-source staging helpers. No user analysis or catalog credit."""
from pathlib import Path,PurePosixPath
import hashlib,json,os,stat,subprocess,tarfile,urllib.request

HERE=Path(__file__).resolve().parent
ACTION=('SKIP3: dependent work was skipped. Obtain the exact named permitted R4.6.1 x86_64 Windows runtime, Matrix1.7-5, source archives or dependency files using an installation method allowed by your environment. If installation/download is forbidden, keep the analysis skipped. '
        'FAIL2: no qualified runtime/result. Preserve the directory and logs, resolve the named identity, dependency or numerical failure and retry a NEW output. '
        'Runtime staging and synthetic tests do not implement the Java adapter, qualify historical paper editions or reproduce a paper.')

class Unavailable(Exception):pass

def require(value,message):
    if not value:raise ValueError(message)

def pin(path):
    p=Path(path);require(p.is_file() and not p.is_symlink(),'Require a regular file: '+str(p))
    h=hashlib.sha256();size=0
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
    require(p.stat().st_size==size,'File changed while hashing: '+str(p))
    return dict(bytes=size,sha256=h.hexdigest())

def identity(row):return {k:row[k] for k in ('bytes','sha256')}

def write(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')

def profile():
    raw=(HERE/'PROFILE.json').read_bytes()
    require(hashlib.sha256(raw).hexdigest()=='85f01c311b765f229968f38bad4f680149718880f91e4c12ad6ea8608cad7db9','Changed reviewed source/dependency profile bytes')
    value=json.loads(raw)
    require(value['profile']=='genomicsem-da95d43-r461-win64-lavaan0619-v1' and value['upstream']['commit']=='da95d431a4709693d07eae55e172ade55526948b','Unsupported source/runtime profile')
    require(value['upstream']['archive']==dict(bytes=3261351,sha256='cf5f1a7f2ba2fd74a586734fe2138c9f3d88d5a4a73319bb20b736501dbdcaaa'),'Changed upstream archive profile')
    require(value['lavaan']['archive']==dict(bytes=948608,sha256='47b137e3c164d33a433698bd5da82157cc8d9bfa8b04711d10a1745a57e8b785'),'Changed lavaan source profile')
    require(len(value['dependencies'])==52 and len({p['package'] for p in value['dependencies']})==52,'Changed dependency profile')
    return value

def new_output(path):
    p=Path(os.path.abspath(path));require(not p.exists() and not p.is_symlink(),'Output must be NEW; existing evidence is preserved')
    require(p.parent.is_dir() and p.parent.resolve()==p.parent,'Output parent must already exist and be physical')
    p.mkdir();return p

def source_inventory(root):
    root=Path(root);require(root.resolve()==root.absolute(),'Source/runtime root must be physical')
    found={}
    for base,dirs,files in os.walk(root,followlinks=False):
        for name in dirs+files:
            p=Path(base)/name;s=p.lstat()
            require(not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400,'Source/runtime reparse or symlink member')
            require(stat.S_ISDIR(s.st_mode) or stat.S_ISREG(s.st_mode),'Nonregular source/runtime member')
        for name in files:
            p=Path(base)/name;found[p.relative_to(root).as_posix()]=pin(p)
    return dict(sorted(found.items()))

def extract_source(archive,target,expected):
    target=Path(target);target.mkdir();seen=set();top=None
    with tarfile.open(archive,'r:gz') as tf:
        for member in tf:
            rel=PurePosixPath(member.name);parts=rel.parts
            require(parts and not rel.is_absolute() and all(p not in ('..','.') for p in parts) and '\\' not in member.name and ':' not in member.name,'Unsafe source archive path')
            if top is None:top=parts[0]
            require(parts[0]==top,'Multiple source roots')
            if len(parts)==1:
                require(member.isdir(),'Unexpected source root node');continue
            if member.isdir():continue
            require(member.isfile(),'Source archive links/special members are refused')
            name=PurePosixPath(*parts[1:]).as_posix()
            require(name in expected and name not in seen,'Unexpected/duplicate source member: '+name)
            require(member.size==expected[name]['bytes'],'Changed source member length')
            f=tf.extractfile(member);raw=f.read(member.size+1)
            require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected[name],'Changed source member digest: '+name)
            p=target.joinpath(*parts[1:]);p.parent.mkdir(parents=True,exist_ok=True)
            with p.open('xb') as out:out.write(raw)
            seen.add(name)
    require(seen==set(expected) and source_inventory(target)==expected,'Incomplete source archive')

def artifact(url,expected,destination,supplied=None,offline=False):
    if supplied is not None:
        p=Path(supplied).absolute()
        if not p.is_file():raise Unavailable('Missing supplied artifact: '+str(p))
        require(pin(p)==expected,'Artifact digest/length differs: '+str(p));return p
    if offline:raise Unavailable('Offline mode: supply the exact cached artifact for '+url)
    p=Path(destination)
    try:
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-source-runtime/1'}),timeout=90) as response:
            data=response.read(expected['bytes']+1)
    except (OSError,TimeoutError) as e:raise Unavailable('Artifact download unavailable or forbidden: '+url+'; '+str(e)) from e
    with p.open('xb') as f:f.write(data)
    require(pin(p)==expected,'Downloaded artifact digest/length differs: '+url);return p

def environment(root,rscript,libraries):
    root=Path(root);tmp=root/'worker-temp'
    if not tmp.exists():tmp.mkdir()
    env={k:os.environ[k] for k in ('SystemRoot','SystemDrive','WINDIR','COMSPEC','PATHEXT','USERPROFILE','APPDATA','LOCALAPPDATA','OS','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE','ProgramFiles','ProgramW6432','CommonProgramFiles','ALLUSERSPROFILE','ProgramData') if k in os.environ}
    system=Path(env.get('SystemRoot','C:/Windows'))/'System32'
    env.update(PATH=os.pathsep.join([str(Path(rscript).parent),str(Path(rscript).parent/'x64'),str(system)]),TEMP=str(tmp),TMP=str(tmp),LC_ALL='C',LANG='C',TZ='UTC',R_LIBS=os.pathsep.join(map(str,libraries)),R_LIBS_USER=os.pathsep.join(map(str,libraries)),R_LIBS_SITE='',R_ENVIRON_USER='NUL',R_PROFILE_USER='NUL')
    for key in ('OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS','VECLIB_MAXIMUM_THREADS'):env[key]='1'
    return env

def run_r(root,rscript,args,libraries,label):
    root=Path(root);command=[str(rscript),'--vanilla',*map(str,args)]
    write(root/(label+'-command.json'),dict(argv=command,qualification_only=True,automatic_install_in_analysis=False))
    try:
        with (root/(label+'.stdout')).open('xb') as out,(root/(label+'.stderr')).open('xb') as err:
            p=subprocess.Popen(command,cwd=root,env=environment(root,rscript,libraries),stdout=out,stderr=err,stdin=subprocess.DEVNULL)
            write(root/(label+'-process.json'),dict(pid=p.pid,observed_process_handle_owned=True,automatic_timeout_or_process_termination=False))
            code=p.wait()
    except OSError as e:raise Unavailable('Declared R executable could not start or execution is prohibited: '+str(e)) from e
    write(root/(label+'-exit.json'),dict(exit_code=code))
    return code
