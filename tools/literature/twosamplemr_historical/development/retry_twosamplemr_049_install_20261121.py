from pathlib import Path
import json,os,subprocess
R=Path('D:/projects/JLinAlg');O=R/'build/twosamplemr-historical-runtime-20261121-1';source=O/'versions/0.4.9/upstream';row=O/'versions/0.4.9-no-help-attempt-2';assert source.is_dir() and not row.exists();row.mkdir();lib=row/'library';lib.mkdir()
command=['C:/Program Files/R/R-4.6.1/bin/R.exe','CMD','INSTALL','--no-multiarch','--no-help','--no-html','--library='+str(lib),str(source)]
environment=dict(os.environ,LANG='C',LC_ALL='C',R_LIBS=os.pathsep.join([str(O/'library'),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')]),R_LIBS_USER=str(lib),R_PROFILE_USER=str(row/'no-user-profile'),R_ENVIRON_USER=str(row/'no-user-environ'))
(row/'COMMAND.json').write_text(json.dumps(dict(command=command,reason='Original Rd help parser failed on embedded nul under R4.6.1. Skip help/html generation in a new private installation; no original R code, DESCRIPTION, namespace, data or estimator is edited.',source_modified=False,first_attempt_retained=str(O/'versions/0.4.9')),indent=2)+'\n',encoding='utf-8',newline='\n')
with (row/'stdout').open('xb') as stdout,(row/'stderr').open('xb') as stderr:
    p=subprocess.Popen(command,cwd=row,env=environment,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);(row/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid))+'\n');print('Started no-help original0.4.9 install',p.pid,flush=True);code=p.wait()
(row/'TERMINAL.json').write_text(json.dumps(dict(exit_code=code,installed=(lib/'TwoSampleMR/DESCRIPTION').exists(),source_modified=False,help_html_installed=False),indent=2)+'\n',encoding='utf-8',newline='\n');print('Install0.4.9 no-help exit',code,flush=True)
