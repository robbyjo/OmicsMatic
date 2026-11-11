# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package six exact MIT-licensed hifiasm source editions; unresolved yak source is excluded."""
import json,re
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    home=ROOT/'src/main/resources/org/jlinalg/omics/hifiasm-source';assert not home.exists();home.mkdir(parents=True);(home/'original').mkdir();base=ROOT/'build/literature-review/hifiasm-runtime';intake=json.loads((base/'source-intake-2/COMPLETED.json').read_bytes());assert intake['status']=='passed-official-five-edition-hifiasm-source-intake-only';profiles={};members={}
    records=[(base/'source-intake-2/hifiasm'/v,r) for key,r in intake['versions'].items() for v in [key.split('/')[1]]];r=json.loads((base/'revision-r592-1/SOURCE.json').read_bytes());records.append((base/'revision-r592-1',r))
    for source,r in records:
        assert all(pin(source/'source'/n)==v for n,v in r['members'].items());header=(source/'source/CommandLines.h').read_bytes();version=re.search(rb'#define HA_VERSION "([^"]+)"',header).group(1).decode();assert version not in profiles;archive=source/r['archive']['filename'];assert pin(archive)=={k:r['archive'][k] for k in ['bytes','sha256']};name='original/'+version+'.tar.gz';(home/name).write_bytes(archive.read_bytes());members[name]=pin(home/name);profiles[version]=dict(commit=r['commit'],repository=r['repository'],archive=dict(path=name,**members[name]),members=r['members'],license='MIT',license_member='LICENSE',source_namespace_patched=False)
    assert len(profiles)==6 and set(profiles)=={'0.14.2-r315','0.15.1-r328','0.16.1-r375','0.19.5-r587','0.19.5-r592','0.22.0-r689'};save(home/'profiles.json',dict(schema_version=1,status='six-exact-original-hifiasm-source-editions',editions=profiles,files=members,yak_runtime_included=False,paper_binary_identified=False,whole_package_implemented=False,whole_goal_complete=False));out=base/'source-resources-1';assert not out.exists();out.mkdir();save(out/'COMPLETED.json',dict(status='prepared-six-original-hifiasm-source-resources-only',profile=pin(home/'profiles.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},implementation_credit=False,whole_goal_complete=False));print('Prepared six exact MIT hifiasm source editions; no yak runtime or installation')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source profile checks')
    main()
