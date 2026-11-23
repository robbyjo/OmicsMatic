from pathlib import Path
import json,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'pre-dispatch-baseline-1';D.mkdir()
args=[sys.executable,'-X','utf8','-B',str(T/'tools/literature/verify_squidpy_catalog_checkpoint.py'),'--jar',str(T/'build/squidpy-1/workflow-normal-build-145/project-build/cli/omicsmatic-0.4.0.jar')]
p=subprocess.run(args,cwd=T,capture_output=True,timeout=60);(D/'stdout').write_bytes(p.stdout);(D/'stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr
save(D/'RESULT.json',dict(exit_code=0,measured=json.loads(p.stdout),scientific_source_change=False,new_family_credit=0,new_reviews=0))
source=T/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';shutil.copyfile(source,D/'JLinAlgCli.java');raw=source.read_bytes()
old=b'            if (arguments.length > 0 && arguments[0].equals("squidpy-tools"))\n'
assert raw.count(old)==1 and b'mrpresso-tools' not in raw
addition=b'            if (arguments.length > 0 && arguments[0].equals("mrpresso-tools"))\n                return MrPressoToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
raw=raw.replace(old,addition+old,1)
help=b'              java -jar omicsmatic-<version>.jar squidpy-tools --help\n';assert raw.count(help)==1
raw=raw.replace(help,help+b'              java -jar omicsmatic-<version>.jar mrpresso-tools --help\n',1);source.write_bytes(raw)
save(D/'DISPATCHER-EXTENSION.json',dict(before=pin(D/'JLinAlgCli.java'),after=pin(source),scope='Two route lines plus one global help line; original qualified dispatcher bytes retained. New native workflow remains unqualified pending normal/package controls.'))
print('PRESERVED_VALID_SQUIDPY_BASELINE_AND_EXTENDED_LOCAL_DISPATCHER',flush=True)
