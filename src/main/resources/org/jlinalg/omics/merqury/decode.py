"""Fresh complete original Meryl decode only; never counts inputs, fits models or runs a workflow."""
import json,os,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]);scratch=Path(sys.argv[2]);context=json.loads((root/'runtime.json').read_bytes());database=json.loads((root/'exports/DATABASES.json').read_bytes())
env=dict(context['environment'],HOME=str(scratch),TMPDIR=str(scratch),LC_ALL='C',LANG='C')
for i,row in enumerate(database):
    path=root/row['database'];assert path.resolve(strict=True).is_relative_to(root.resolve(strict=True))
    with(scratch/('db-%04d.tsv'%i)).open('xb')as out,(scratch/('db-%04d.stderr'%i)).open('xb')as err:
        code=subprocess.call([context['meryl'],'print',str(path)],stdin=subprocess.DEVNULL,stdout=out,stderr=err,env=env,cwd=scratch)
    if code:raise RuntimeError('Original fresh decoder failed with exit'+str(code)+' for '+row['database'])
