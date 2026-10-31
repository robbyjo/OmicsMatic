"""Pinned provider identity and complete owned generated-import snapshots."""
from pathlib import Path
import csv,hashlib,importlib.metadata as md,json,os,platform,shutil,sys,sysconfig
PINNED={'scvi-tools':'1.3.0','torch':'2.6.0+cpu','lightning':'2.5.0.post0','torchmetrics':'1.6.1','anndata':'0.11.4','numpy':'2.0.2','pandas':'2.2.3','scipy':'1.15.2','scikit-learn':'1.6.1','h5py':'3.13.0','jax':'0.4.35','jaxlib':'0.4.35','flax':'0.10.2','optax':'0.2.4','chex':'0.1.87','orbax-checkpoint':'0.6.4','tensorstore':'0.1.67','numpyro':'0.15.3','numba':'0.61.0','llvmlite':'0.44.0','mudata':'0.3.1','pyro-ppl':'1.9.1'}
PROFILE='scvi-tools-1.3.0-linux-x86_64-py3123-cpu'
class UnavailableProfile(RuntimeError):pass
def sha(path):
 with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def write_json(path,value):
 with Path(path).open('x',encoding='utf-8') as stream:stream.write(json.dumps(value,sort_keys=True,ensure_ascii=False,allow_nan=False)+'\n')
def versions():
 values={name:md.version(name) for name in PINNED}
 differences={name:dict(expected=expected,actual=values[name]) for name,expected in PINNED.items() if values[name]!=expected}
 if differences:raise UnavailableProfile('Pinned CPU profile differs: '+repr(differences))
 if platform.system()!='Linux' or platform.machine()!='x86_64' or sys.version_info[:3]!=(3,12,3) or sys.flags.isolated!=1 or not sys.dont_write_bytecode:raise UnavailableProfile('Requires isolated Linux x86_64 Python3.12.3 with bytecode writes disabled')
 return values
def runtime_identity(out):
 import torch
 out=Path(out)
 loaded=[]
 for line in Path('/proc/self/maps').read_text().splitlines():
  fields=line.split(None,5)
  if len(fields)==6 and 'x' in fields[1] and fields[5].startswith('/'):
   p=Path(fields[5]);real=p.resolve(strict=True)
   if not real.is_file():raise RuntimeError('Nonordinary mapped executable '+str(p))
   loaded.append(str(real))
 generated=[];work=Path.cwd().resolve();prefix=Path(sys.prefix).resolve();stdlib=Path(sysconfig.get_path('stdlib')).resolve();zip_path=Path(sys.base_prefix)/'lib/python312.zip'
 for value in sys.path:
  path=Path(value).absolute()
  if path.is_relative_to(prefix) or path.is_relative_to(stdlib) or path==zip_path:continue
  if not path.is_relative_to(work) or path==work or path.is_symlink():raise RuntimeError('Unowned generated import path '+str(path))
  if not path.exists():generated.append(dict(original=str(path),disposition='absent',snapshot=None,members={}));continue
  if path.resolve()!=path or not path.is_dir():raise RuntimeError('Noncanonical generated import directory')
  relative='generated-imports/'+str(len(generated));snapshot=out/relative;snapshot.mkdir(parents=True,exist_ok=False);members={'':dict(kind='directory',bytes=0,sha256='',link='',resolved='')};total=0
  for original in sorted(path.rglob('*')):
   if original.is_symlink():raise RuntimeError('Generated import symlink needs explicit review')
   name=original.relative_to(path).as_posix();target=snapshot/name
   if original.is_dir():target.mkdir();members[name]=dict(kind='directory',bytes=0,sha256='',link='',resolved='')
   elif original.is_file():
    if total+original.stat().st_size>67108864:raise RuntimeError('Generated import bytes exceed capacity before copying')
    before=sha(original);shutil.copyfile(original,target);after=sha(target)
    if before!=after or before!=sha(original):raise RuntimeError('Generated import changed during capture')
    total+=target.stat().st_size;members[name]=dict(kind='file',bytes=target.stat().st_size,sha256=after,link='',resolved='')
   else:raise RuntimeError('Nonordinary generated import member')
   if len(members)>1024 or total>67108864:raise RuntimeError('Complete generated import capture exceeds health capacity')
  generated.append(dict(original=str(path),disposition='snapshot-preserved-before-original-temporary-cleanup',snapshot=relative,members=members))
 return dict(profile=PROFILE,versions=versions(),python=platform.python_version(),python_executable=sys.executable,python_real=str(Path(sys.executable).resolve()),prefix=sys.prefix,base_prefix=sys.base_prefix,stdlib=sysconfig.get_path('stdlib'),sys_path=sys.path,generated_import_dirs=generated,isolated=sys.flags.isolated,bytecode_disabled=bool(sys.dont_write_bytecode),cuda_available=torch.cuda.is_available(),torch_cuda=torch.version.cuda,torch_threads=torch.get_num_threads(),torch_interop_threads=torch.get_num_interop_threads(),host=dict(os=platform.system(),architecture=platform.machine(),kernel=platform.release()),mapped_executable_files=sorted(set(loaded)))
