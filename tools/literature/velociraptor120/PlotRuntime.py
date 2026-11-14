# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""An explicit R-only original plotting profile; no scvelo fitting backend is provided."""
from pathlib import Path
import os
from Runtime import HERE,Unavailable,inventory,load,pin,regular,same,verify_source

PLOT_ACTION=('Required action: provision the exact locked R4.5.3 plotting dependency base '
 'and original velociraptor1.20.0 with install_plots.py in a NEW private library. '
 'This R-only profile provides original figure functions over supplied complete fit '
 'and projected-vector inputs, without a scvelo Python analysis backend; use the separately provisioned numeric workflow for '
 'RNA-velocity fitting or embedding. Missing/forbidden prerequisites/execution SKIP3 '
 'skip dependent figure analysis. FAIL2 admits no complete figure. Preserve the '
 'complete original graph RDS, every available rendered/partial file, caller state, '
 'conditions and raw logs; correct the named cause and retry NEW paths. Original '
 'named-coordinate DFrame errors must remain visible; no axis/cell/gene identifiers '
 'are renamed automatically. The known R namespace stack diagnostic remains '
 'unresolved. Graph environment/pointer identities and opaque RDS byte parity are '
 'not claimed. Trusted R scripts run with account permissions; this is not an OS '
 'sandbox or a CPU/RSS bound. Do not replace nonfinite values or change saved '
 'hashes to obtain a pass. This remains a development candidate: no author '
 'settings, convergence, study validity or catalog admission are inferred, and '
 'the full250-paper objective remains open.')

def environment(ready,out,*,temporary_directory=None):
 out=Path(out);prefix=Path(ready['r_prefix']);library=Path(ready['library'])
 env={k:v for k,v in os.environ.items() if not k.startswith(('R_','PYTHON','BASILISK_','RETICULATE_','PIP_','UV_','LD_')) and k not in ['R_HOME','VIRTUAL_ENV','CONDA_PREFIX']}
 env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',R_LIBS=str(library)+':'+ready['base_library'],R_LIBS_USER=str(library),R_LIBS_SITE='',HOME=str(out/'owned-home'),TMPDIR=str(temporary_directory if temporary_directory is not None else out/'tmp'),
  BASILISK_USE_SYSTEM_DIR='1',BASILISK_NO_PYENV='1',BASILISK_NO_DESTROY='1',BASILISK_EXTERNAL_DIR=str(out/'unused-external-cache'),PIP_NO_INDEX='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
 return env

def provider(directory):
 directory=Path(directory);regular(directory/'READY.json');ready=load(directory/'READY.json')
 if ready.get('status')!='READY_ORIGINAL_VELOCIRAPTOR120_R_ONLY_FIGURE_PROFILE_NOT_STUDY_QUALIFICATION' or ready.get('source_profile')!=pin(HERE/'source-profile.json') or ready.get('provides_Python_fitting_backend') is not False:raise ValueError('Exact R-only figure provider identity differs')
 prefix=Path(ready['r_prefix']);library=Path(ready['library'])
 if Path(ready['rscript'])!=prefix/'bin/Rscript' or Path(ready['base_library'])!=prefix/'lib/R/library':raise ValueError('Original R-only runtime paths differ')
 if not Path(ready['rscript']).is_file() or not os.access(ready['rscript'],os.X_OK):raise Unavailable('Original permitted Rscript missing')
 if (library/'velociraptor/basilisk/env').exists():raise ValueError('R-only figure provider changed by adding an undeclared Python backend')
 for name,wanted in ready['preparation_sources'].items():same(HERE/name,wanted)
 verify_source(ready['source_root'])
 if inventory(ready['runtime_roots'])!=ready['runtime_inventory']:raise ValueError('Complete R-only runtime file/link population or content differs')
 same(directory/'PLOT-READINESS.json',ready['readiness']);return ready
