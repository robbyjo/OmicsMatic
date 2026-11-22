from pathlib import Path
import json,os,subprocess,sys
from prepare_genomicranges_20261122_114 import R,T,B
from handover_inventory_20261122_111 import pin,save
D=B/'geometry-feasibility-1';D.mkdir();profile=json.loads((B/'dependency-screening-1/RESULT.json').read_bytes());packages=profile['selected'];inputs={}
for package in packages.values():
 for path in Path(package['path']).rglob('*'):
  if path.is_file():inputs[str(path)]=pin(path)
save(D/'PACKAGES-BEFORE.json',inputs);libs=[]
for package in packages.values():
 if package['library'] not in libs:libs.append(package['library'])
script=R/'build/probe_genomicranges_geometry_20261122_114.R';command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(D/'result'),*libs];env={k:v for k,v in os.environ.items() if not k.upper().startswith('LC_') and k.upper() not in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_HOME']};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
save(D/'STARTED.json',{'command':command,'script':pin(script),'existing_packages_read_only':True,'selected_versions':{k:v['version'] for k,v in packages.items()},'scope':'Original1.64.0 feasibility only;not qualification of reported1.44/1.58/1.60 or Java implementation.'})
with (D/'stdout').open('xb') as out,(D/'stderr').open('xb') as err:p=subprocess.run(command,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err,timeout=180)
assert all(pin(Path(path))==value for path,value in inputs.items());save(D/'PROCESS.json',{'exit_code':p.returncode,'all_selected_package_files_unchanged':True});print((D/'stdout').read_text(errors='replace')+(D/'stderr').read_text(errors='replace')[-1500:],flush=True);raise SystemExit(p.returncode)
