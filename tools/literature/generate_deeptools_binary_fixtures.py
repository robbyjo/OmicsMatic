"""Write tiny deterministic BigWigs using the already prepared original runtime.

Run with the managed profile's Python launcher and font-cache environment.
Never installs dependencies or edits the upstream source.
"""
import importlib.util
import json
import os
from pathlib import Path
import sys

root=Path(__file__).resolve().parents[2]
marker=Path(os.environ['MPLCONFIGDIR']).parent/'binary-fixture-bootstrap.json'
spec=importlib.util.spec_from_file_location('jlinalg_original_bridge',root/'src/main/resources/org/jlinalg/omics/deeptools_bridge.py')
bridge=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
sys.argv=[str(spec.origin),'--report',str(marker)]
assert bridge.main()==0
identity=json.loads(marker.read_text())
lock=json.loads((root/'tools/literature/deeptools-runtime-lock.json').read_text())
assert identity['status']=='probe-completed' and identity['versions']==lock['critical_versions'] and identity['source_sha256']==lock['source_modules_sha256']
import pyBigWig
for name,starts,ends,values in [('gapped.bw',[0,20],[10,1000],[2.,2.]),('negative-two.bw',[0],[1000],[-2.])]:
    target=root/'src/test/resources/deeptools'/name
    if target.exists():
        raise FileExistsError('Preserve existing fixture; compare before any regeneration: '+str(target))
    with pyBigWig.open(str(target),'w') as output:
        output.addHeader([('chr1',1000)])
        output.addEntries(['chr1']*len(starts),starts,ends=ends,values=values)
print('binary-fixtures-completed',flush=True)
