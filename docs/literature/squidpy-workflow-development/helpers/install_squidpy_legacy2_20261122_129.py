from pathlib import Path
import json,subprocess,time
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/squidpy-1/runtime-legacy-2';B.mkdir();old=T/'build/squidpy-1/runtime-legacy-1/install.py';source=old.read_text()
source=source.replace("runtime-legacy-1');uv=", "runtime-legacy-2');uv=")
start=source.index("execute('python-install'");end=source.index('requirements=',start)
source=source[:start]+"python=Path((B.parent/'runtime-legacy-1/python-find.stdout').read_text().strip());assert python.is_file() and python.resolve().is_relative_to((B.parent/'runtime-legacy-1/python').resolve())\n"+source[end:]
source=source.replace('numpy==1.21.6','setuptools==65.5.0\nnumpy==1.21.6')
script=B/'install.py';script.write_text(source,encoding='utf-8');linux='/mnt/'+script.drive[0].lower()+script.as_posix()[2:];args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3',linux];started=time.time()
with (B/'launcher.stdout').open('xb') as out,(B/'launcher.stderr').open('xb') as err:
 p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'LAUNCHER.json').write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started,correction='Explicit setuptools65.5.0 supplies pkg_resources; the failed environment and original source remain unchanged.'),indent=2)+'\n');code=p.wait()
(B/'LAUNCHER-RESULT.json').write_text(json.dumps(dict(exit_code=code,seconds=time.time()-started),indent=2)+'\n');print((B/'launcher.stdout').read_text(errors='replace')[-4000:],flush=True)
if code:print((B/'launcher.stderr').read_text(errors='replace')[-2000:],flush=True)
raise SystemExit(code)
