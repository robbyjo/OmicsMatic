from pathlib import Path
import hashlib,json,shutil
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';C=T/'src/main/java/org/jlinalg/cli';B=T/'build/squidpy-1/java-support-intake-1';B.mkdir()
records={}
for old,new in [('FusionWorkflowFiles.java','SquidpyWorkflowFiles.java'),('FusionWorkflowProcess.java','SquidpyWorkflowProcess.java')]:
 raw=(C/old).read_bytes();(B/old).write_bytes(raw)
 text=raw.decode().replace('FusionWorkflow','SquidpyWorkflow').replace('FUSION','Squidpy').replace('fusion-owned-worker-cleanup','squidpy-owned-worker-cleanup').replace('Declared Rscript','Declared Python')
 if new=='SquidpyWorkflowFiles.java':
  text=text.replace('"help","html","doc","demo","tests","unitTests"','"help","html","doc","demo","tests","unitTests","__pycache__"')
  text=text.replace('4294967296L&&files.size()<50000','17179869184L&&files.size()<100000')
 path=C/new;assert not path.exists();path.write_text(text,encoding='utf-8')
 records[old]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),derived_file=new,changes='Names/diagnostics; Python bytecode caches excluded from runtime closure and closure bound raised to16GiB/100000 files. Birth-bound process handles unchanged.')
resources=T/'src/main/resources/org/jlinalg/omics/squidpy-workflow';resources.mkdir(parents=True)
for name in ['SOURCE-PROFILE.json','operations.py','worker.py']:shutil.copyfile(T/'tools/literature/squidpy_workflow'/name,resources/name)
(B/'DERIVATION.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8');print('derived file/process guards and source resources staged for development')
