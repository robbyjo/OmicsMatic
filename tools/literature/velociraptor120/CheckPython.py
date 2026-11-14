# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original Python module/metadata readiness without executing an analysis."""
from pathlib import Path
import importlib.metadata as md,json,sys
from Runtime import HERE,load,pin,save

out=Path(sys.argv[1]);profile=load(HERE/'source-profile.json')
import scvelo,scanpy,anndata,numpy,pandas,scipy
if sys.version_info[:3]!=(3,12,10):raise ValueError('Required original Python 3.12.10 unavailable')
versions={dist.metadata['Name'].lower().replace('_','-'):dist.version for dist in md.distributions()}
if versions!=profile['Python_distributions']:raise ValueError('Complete declared original Python dependency graph differs')
package=Path(scvelo.__file__).parent
observed={p.relative_to(package).as_posix():pin(p) for p in package.rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.suffix not in ['.pyc','.pyo']}
if observed!=profile['scvelo_source_files']:raise ValueError('Complete original scvelo package source differs from the original wheel')
maps=Path('/proc/self/maps')
if maps.is_file():(out/'original-python-native-maps.txt').write_bytes(maps.read_bytes())
save(out/'python-readiness.json',dict(status='READY_ORIGINAL_SCVELO033_NAMESPACE_NOT_ANALYSIS',python=sys.version,executable=sys.executable,base_prefix=sys.base_prefix,sys_path=sys.path,distributions=versions,original_scvelo_files=len(observed),modules={name:str(module.__file__) for name,module in [('scvelo',scvelo),('scanpy',scanpy),('anndata',anndata),('numpy',numpy),('pandas',pandas),('scipy',scipy)]},analysis_executed=False))
print('READY_ORIGINAL_SCVELO033_NAMESPACE_NOT_ANALYSIS',flush=True)
