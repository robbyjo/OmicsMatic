from pathlib import Path
import os,subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-3/compile-1';B.mkdir();classes=B/'classes';classes.mkdir()
parent=T/'build/smartpca-2/handover-normal-build-1/project-build/cli/omicsmatic-0.4.0.jar';assert pin(parent)['sha256']=='f2a4c8255196adc0cba8a0d72200601fb01ccb446022907f96ee86944dec62f6'
sources=[T/'src/main/java/org/jlinalg/cli'/name for name in ('SmartpcaWorkflowAudit.java','SmartpcaToolsCli.java')]
args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',str(parent),'-d',str(classes),*map(str,sources)]
p=subprocess.run(args,cwd=T,capture_output=True);(B/'stdout').write_bytes(p.stdout);(B/'stderr').write_bytes(p.stderr);save(B/'RESULT.json',dict(command=args,exit_code=p.returncode,sources={x.relative_to(T).as_posix():pin(x) for x in sources}));print(p.stderr.decode(errors='replace'));raise SystemExit(p.returncode)
