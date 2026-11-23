from pathlib import Path
import hashlib,json,shutil
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=T/'src/main/resources/org/jlinalg/omics/mrpresso-3e3c92d';D.mkdir(parents=True)
upstream=B/'upstream-intake-1/source';intake=json.loads((B/'upstream-intake-1/STATUS.json').read_bytes());members={}
for name,expected in intake['files'].items():
    source=upstream/name;assert pin(source)==expected;destination=D/name;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,destination);members[name]=pin(destination)
licensefile=T/'LICENSE';assert licensefile.exists();shutil.copyfile(licensefile,D/'COPYING-GPL-3.0.txt');members['COPYING-GPL-3.0.txt']=pin(D/'COPYING-GPL-3.0.txt')
profile=dict(schema_version=1,profile='MRPRESSO_1.0_3e3c92d',repository=intake['repository'],commit=intake['commit'],package_version='1.0',license='GPL-3',files=members,
    purpose='Original source provenance for the native Java numerical implementation; R source is retained but not executed by the native workflow.',
    supported_rng_profiles=['Mersenne-Twister/Inversion/Rejection','Mersenne-Twister/Inversion/Rounding'],
    reference_R_version='4.6.1',author_paper_edition_established=False,whole_tool_qualified=False,whole_paper_reproduced=False)
save(D/'SOURCE.json',profile);print(json.dumps(dict(resource=str(D),profile_sha256=pin(D/'SOURCE.json')['sha256'])),flush=True)
