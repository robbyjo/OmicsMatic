# Original package/profile probe; no model is run.
import sys,json,platform
from pathlib import Path
sys.path.insert(0,sys.argv[1])
sys.prefix=sys.exec_prefix=str(Path(sys.argv[1]).parents[2])
import importlib.metadata as m
import torch,pyro,numpy,scipy,pandas,anndata,h5py,tables,cellbender
print(json.dumps(dict(python=list(sys.version_info[:3]),implementation=platform.python_implementation(),packages={d.metadata['Name'].lower().replace('_','-'):d.version for d in m.distributions()},cuda_available=torch.cuda.is_available(),cellbender=m.version('cellbender'),torch=torch.__version__)))
