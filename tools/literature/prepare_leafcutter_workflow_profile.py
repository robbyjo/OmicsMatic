# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare exact original source/setup profiles for the full LeafCutter adapter without changing frozen component sources."""
import json,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/leafcutter-runtime';dest=ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-workflow';intake=json.loads((base/'r-source-intake-1/COMPLETED.json').read_bytes());edition='source-2c9907ef66ad';source=intake['editions'][edition]
    def write(name,raw):
        path=dest/name;path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists():assert path.read_bytes()==raw,'Never overwrite captured original/setup source: '+name
        else:path.write_bytes(raw)
    for name,want in source['members'].items():file=base/'r-source-intake-1'/edition/'source'/name;assert pin(file)=={k:want[k] for k in ['bytes','sha256']};write('original/'+name,file.read_bytes())
    prior=base/'source-intake-2'/edition/'source'
    for name in ['scripts/leafcutter_ds.R','scripts/prepare_phenotype_table.py','clustering/leafcutter_cluster_regtools.py']:write('original/'+name,(prior/name).read_bytes())
    records=json.loads((base/'r-python-setup-1/complete-conda-package-records.json').read_bytes());packages=[]
    for row in sorted(records.values(),key=lambda r:r['name']):packages.append(dict(name=row['name'],version=row['version'],build=row['build'],filename=row['fn'],url=row['url'],bytes=row['size'],sha256=row['sha256'],md5=row['md5'],license=row.get('license','unspecified')))
    assert len(packages)==239 and all(len(p['sha256'])==64 for p in packages);write('setup/dependency-locks.json',(json.dumps(dict(packages=packages),indent=2)+'\n').encode());write('setup/inventory.py',(ROOT/'tools/literature/inventory_hail_runtime_linux.py').read_bytes());addons={}
    compat=json.loads((base/'r-compatibility-source-intake-1/COMPLETED.json').read_bytes());tail=json.loads((base/'r-addon-source-intake-1/COMPLETED.json').read_bytes())
    for name,row,home in [('Biobase',compat['packages']['Biobase'],base/'r-compatibility-source-intake-1/Biobase'),('oompaData',compat['packages']['oompaData'],base/'r-compatibility-source-intake-1/oompaData'),('TailRank',tail,base/'r-addon-source-intake-1')]:
        archive=row['archive'];file=home/archive['filename'];assert pin(file)=={k:archive[k] for k in ['bytes','sha256']};member='setup/addons/'+archive['filename'];write(member,file.read_bytes());addons[name]=dict(version=row['version'],path=member,**pin(file),url=archive['url'])
    write('NOTICE.txt',b'Original LeafCutter source from David Knowles, Yang Li and contributors, revision2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb. Repository Apache-2.0; R package GPL (>=3). Original source is captured unchanged. Runtime preparation/worker/audit additions are GPL-3.0-or-later as part of OmicsMatic. Packaged Biobase2.58.0, oompaData3.1.3 and TailRank3.2.6 source archives retain their original descriptions and licenses. Conda dependencies are separately installed from exact archive locks; no compiler, installed dependency environment or proprietary software is bundled. Native clustering/preprocessing/model APIs have their own source profiles. No historical paper-runtime or whole-paper parity is inferred.\n')
    workers=['worker.py','worker.R','supervisor.py','phenotype-launcher.py'];files={file.relative_to(dest).as_posix():pin(file) for file in sorted(dest.rglob('*')) if file.is_file() and file.name not in ['profiles.json','worker-profile.json',*workers]};save(dest/'profiles.json',dict(schema_version=1,edition=edition,revision=source['revision'],R_package_version=source['R_package_version'],source_members=source['members'],addons=addons,files=files,implementation_credit=False,whole_goal_complete=False));save(dest/'worker-profile.json',dict(schema_version=1,edition=edition,revision=source['revision'],files={name:pin(dest/name) for name in workers}));print('Prepared',len(files),'exact original/setup profile members and',len(workers),'separately pinned execution workers')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source profile checks')
    main()
