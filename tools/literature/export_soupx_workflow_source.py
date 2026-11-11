# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export one exact, separately licensed original SoupX source tree from committed evidence; no network/install."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists(),'Choose a NEW output path'
    folder=ROOT/'docs/literature/tfidf-markers-development';proof=json.loads((folder/'VALIDATION.json').read_bytes());assert pin(folder/'controls.zip')==proof['archive'];files=unpack(folder/'controls.zip',proof)
    prefix='build/literature-review/soupx-runtime/'+('source-intake-1/1.6.2/' if a.version=='1.6.2' else 'source-recovery-1/1.3.0/');receipt=json.loads(files[prefix+'SOURCE.json']);assert receipt['version']==a.version;members=receipt['source_members'];profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/soupx-workflow/SOURCE-PROFILE.json').read_bytes())['editions'][a.version]
    assert all(byte_pin(files[prefix+'source/'+name])==value for name,value in members.items());assert all(members[n]==v for n,v in profile['files'].items())
    out.mkdir(parents=True)
    for name,value in members.items():
        target=out/'source'/name;assert target.resolve().is_relative_to(out/'source');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(files[prefix+'source/'+name]);assert pin(target)==value
    (out/'SOURCE.json').write_bytes(files[prefix+'SOURCE.json']);(out/'GPL-2.txt').write_bytes(files['licenses/upstream-SoupX-GPL-2.txt']);(out/'NOTICE.txt').write_bytes(files['licenses/NOTICE.txt'])
    save(out/'EXPORT.json',dict(status='exported-exact-original-SoupX-source-no-install',version=a.version,source_receipt=pin(out/'SOURCE.json'),members=len(members),archive=proof['archive'],analysis_admitted=False,required_action='Keep the GPL-2 license and notices with this unchanged separate source. Provision the exact permitted R/dependency profile in private libraries, then explicitly request installation or skip dependent analysis. Version labels do not establish a paper commit or historical environment.'))
    print('Exported',a.version,len(members),'original source files into',out/'source','; nothing installed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/archive checks')
    main()
