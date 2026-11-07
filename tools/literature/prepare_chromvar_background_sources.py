# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact original background sampler functions and C++ sources, without modifying installed libraries."""
import json
from pathlib import Path
from prepare_signac_link_sources import extract,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime/motif-dependency-source-2';DEST=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-background'
def main():
    assert not DEST.exists();DEST.mkdir();records=[]
    for edition in ['1.16.0','1.34.1']:
        package=BASE/f'chromVAR-{edition}/source/chromVAR';source=package/'R/background_peaks.R';function=extract(source.read_text(encoding='utf8'),'get_background_peaks_core');cpp=(package/'src/utils.cpp').read_bytes();folder=DEST/edition;folder.mkdir()
        (folder/'get_background_peaks_core.R').write_bytes(function);(folder/'utils.cpp').write_bytes(cpp)
        records.append(dict(profile=edition+('-source' if edition=='1.16.0' else '-package'),edition=edition,function=pin(function),original_R_source=dict(path=source.relative_to(ROOT).as_posix(),**pin(source.read_bytes())),cpp_source=dict(path=(package/'src/utils.cpp').relative_to(ROOT).as_posix(),**pin(cpp)),historical_whole_package=False))
    for name in ['get_background_peaks_core.R','utils.cpp']:assert (DEST/'1.16.0'/name).read_bytes()==(DEST/'1.34.1'/name).read_bytes(),name
    (DEST/'MIT-LICENSE.txt').write_bytes((ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations/MIT-LICENSE.txt').read_bytes());(DEST/'.gitattributes').write_text('*.R -text\n*.cpp -text\n*.json -text\n*.txt -text\n',encoding='utf8',newline='\n')
    (DEST/'SOURCE-PROFILES.json').write_text(json.dumps(dict(schema_version=1,records=records,identical_R_core=True,identical_full_utils_cpp=True,installed_DLL_source_authentication=False,current_dependencies_explicit=True),indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps(dict(status='retained-exact-original-background-core-and-Cpp',profiles=2,identical_R_core=True,identical_full_utils_cpp=True)))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable background source guards')
    main()
