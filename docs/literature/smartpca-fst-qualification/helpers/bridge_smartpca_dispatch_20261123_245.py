from pathlib import Path
import json,subprocess,sys
from handover_inventory_20261122_111 import pin,save,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-4/pre-dispatch-baseline-1';B.mkdir()
relative='src/main/java/org/jlinalg/cli/JLinAlgCli.java';source=T/relative;before=source.read_bytes();assert before==git('show','eb167472e9e71cc51483e338ee3b8d32ca7bdcd8:'+relative,cwd=T)
(B/'JLinAlgCli.java').write_bytes(before);readers=[]
for name in ('verify_mrpresso_catalog_checkpoint.py','verify_smartpca_workflow_checkpoint.py'):
 command=[sys.executable,'-X','utf8','-B',str(T/'tools/literature'/name)];p=subprocess.run(command,cwd=T,capture_output=True,timeout=180);(B/(name+'.stdout')).write_bytes(p.stdout);(B/(name+'.stderr')).write_bytes(p.stderr);save(B/(name+'.json'),dict(command=command,exit_code=p.returncode));assert p.returncode==0,p.stderr;readers.append(dict(reader=name,measured=json.loads(p.stdout),exit_code=p.returncode))
save(B/'RESULT.json',dict(status='PASS_PRE_SMARTPCA_DISPATCH_FROZEN_READERS',readers=readers,dispatcher=pin(source),historical_hashes_refreshed=False,current_source_context_at_execution=True))
ending=b'\r\n' if b'\r\n' in before else b'\n'
route=b'            if (arguments.length > 0 && arguments[0].equals("smartpca-tools"))'+ending+b'                return SmartpcaCommandCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);'+ending
anchor=b'            if (arguments.length > 0 && arguments[0].equals("mrpresso-tools"))'+ending
help_line=b'              java -jar omicsmatic-<version>.jar smartpca-tools --help'+ending
help_anchor=b'              java -jar omicsmatic-<version>.jar mrpresso-tools --help'+ending
assert before.count(anchor)==before.count(help_anchor)==1
after=before.replace(anchor,route+anchor).replace(help_anchor,help_line+help_anchor);source.write_bytes(after)
allowed={line.strip() for line in (route+help_line).splitlines()}
assert len(allowed)==3 and b''.join(line for line in after.splitlines(keepends=True) if line.strip() not in allowed)==before
save(B/'DISPATCHER-EXTENSION.json',dict(before=dict(bytes=len(before),sha256=__import__('hashlib').sha256(before).hexdigest()),after=pin(source),allowed_added_lines=[x.decode() for x in sorted(allowed)],exact_source_extension_lines=3,predecessor_commit='eb167472e9e71cc51483e338ee3b8d32ca7bdcd8',historical_hashes_refreshed=False))
print('Added exactly three public dispatcher lines; preserved successful prior-context reader evidence and original source bytes.')
