from pathlib import Path
import json,os,subprocess
from prepare_genomicranges_20261122_114 import B,T
from handover_inventory_20261122_111 import pin,save
source=B/'legacy-r410-intake-1';D=B/'private-r410-extract-1';D.mkdir();out=D/'extracted';tool=source/'innoextract/innoextract.exe';setup=source/'R-4.1.0-win.exe'
assert pin(setup)==json.loads((source/'RESULT.json').read_bytes())['R_installer'];command=[str(tool),'--extract','--no-extract-unknown','--output-dir',str(out),str(setup)]
save(D/'STARTED.json',{'command':command,'extractor':pin(tool),'installer':pin(setup),'R_installer_is_not_executed':True,'registry_or_existing_runtime_changes':False})
with (D/'extract.stdout').open('xb') as stdout,(D/'extract.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
save(D/'EXTRACT-PROCESS.json',{'exit_code':p.returncode});assert p.returncode==0,(D/'extract.stderr').read_text(errors='replace')[-2000:]
candidates=list(out.rglob('Rscript.exe'));rs=next(p for p in candidates if p.parent.name=='x64');assert rs.resolve().is_relative_to(out.resolve());env={k:v for k,v in os.environ.items() if not k.upper().startswith('LC_') and k.upper() not in ['LANG','LANGUAGE','R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER']};env.update(LANG='C',LC_ALL='C')
script=D/'check.R';script.write_text('stopifnot(getRversion()=="4.1.0");cat("VERSION",R.version.string,"\\nHOME",R.home(),"\\nPLATFORM",R.version$platform,"\\n");saveRDS(list(version=R.version,home=R.home(),session=sessionInfo()),commandArgs(TRUE)[[1]])\n',encoding='utf-8');command=[str(rs),'--vanilla',str(script),str(D/'RUNTIME.rds')]
with (D/'version.stdout').open('xb') as stdout,(D/'version.stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=60)
save(D/'VERSION-PROCESS.json',{'command':command,'exit_code':p.returncode});assert p.returncode==0,(D/'version.stderr').read_text(errors='replace')[-1500:]
save(D/'RESULT.json',{'status':'PRIVATE_PORTABLE_R410_EXTRACTED_AND_VERSION_CHECKED','rscript':str(rs),'rscript_pin':pin(rs),'installer_executed':False,'system_registry_or_existing_R_modified':False,'analysis_run':False,'new_catalog_credit':0});print((D/'version.stdout').read_text(errors='replace'),flush=True)
