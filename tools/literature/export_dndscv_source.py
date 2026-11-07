# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export the sealed original GPL-3 dndscv source into a NEW tree; no network or installation."""
import argparse,io,json,tarfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists(),'Choose a NEW output path';home=ROOT/'docs/literature/dndscv-source';proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']=='captured-exact-official-dndscv-source-only' and pin(home/'source.zip')==proof['archive'];files=unpack(home/'source.zip',proof);receipt=json.loads(files['SOURCE.json']);profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow/SOURCE-PROFILE.json').read_bytes());assert receipt['commit']==profile['commit'] and receipt['source_members']==profile['files'] and byte_pin(files['source.tar.gz'])==receipt['archive'];members={}
    with tarfile.open(fileobj=io.BytesIO(files['source.tar.gz']),mode='r:gz') as tar:
        for entry in tar:
            path=PurePosixPath(entry.name);assert path.parts[0]=='dndscv-'+profile['commit'] and not path.is_absolute() and '..' not in path.parts
            if entry.isdir():continue
            assert entry.isfile() and len(path.parts)>1 and entry.size<=200000000;name=PurePosixPath(*path.parts[1:]).as_posix();assert name in profile['files'] and name not in members;blob=tar.extractfile(entry).read();assert byte_pin(blob)==profile['files'][name];members[name]=blob
    assert set(members)==set(profile['files']);out.mkdir(parents=True)
    for name,value in profile['files'].items():
        path=out/'source'/name;assert path.resolve().is_relative_to(out/'source');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(members[name]);assert pin(path)==value
    (out/'SOURCE.json').write_bytes(files['SOURCE.json']);save(out/'EXPORT.json',dict(status='exported-exact-official-dndscv-source-no-install',commit=profile['commit'],version=profile['version'],members=len(profile['files']),archive=proof['archive'],analysis_admitted=False,required_action='Keep LICENSE.txt and original notices. Supply the exact permitted R/dependency profile in private libraries, explicitly prepare the runtime or SKIP dependent analysis. Source version0.0.1.0 does not establish P056 reported0.1.0 or any paper commit.'));print('Exported',len(profile['files']),'unchanged original files to',out/'source','; nothing installed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/archive checks')
    main()
