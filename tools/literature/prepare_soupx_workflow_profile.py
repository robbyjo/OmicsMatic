# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pin exact original source files and tested runtime editions; original code stays in supplied sources."""
import json
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/soupx-runtime';target=ROOT/'src/main/resources/org/jlinalg/omics/soupx-workflow';path=target/'SOURCE-PROFILE.json';assert not path.exists();editions={}
    for version,attempt,source_path in [('1.6.2',1,'source-intake-1/1.6.2'),('1.3.0',2,'source-recovery-1/1.3.0')]:
        receipt=base/source_path/'SOURCE.json';source=receipt.parent/'source';record=json.loads(receipt.read_bytes());assert record['version']==version and all(pin(source/n)==v for n,v in record['source_members'].items());setup=json.loads((base/f'reference-{version}-{attempt}/COMPLETED.json').read_bytes());assert setup['status']=='installed-source-bound-original-SoupX-reference-only' and setup['preexisting_libraries_unchanged'];profile=setup['profile'];assert profile['R_version']=='4.6.1' and profile['platform']=='x86_64-w64-mingw32';names=['DESCRIPTION','NAMESPACE',*[p.relative_to(source).as_posix() for p in sorted((source/'R').glob('*.R'))]];editions[version]=dict(packages=profile['versions'],files={n:pin(source/n) for n in names},original_receipt=pin(receipt),paper_commit_identified=False)
    save(path,dict(schema_version=1,status='pinned-original-SoupX-workflow-profiles-not-execution-validation',R_version='4.6.1',platform='x86_64-w64-mingw32',editions=editions));(target/'NOTICE.txt').write_text('Separate original SoupX execution program, GPL-2.0-or-later. Original SoupX by Matthew Daniel Young is GPL-2 and is supplied separately through an exact pinned source tree and installed package; no original SoupX code is copied into the Java classes. Standard files and JSON connect these separate programs. Source version labels do not identify paper commits. Source bodies/formals and selected package versions are checked; complete dependency/native-binary identity and historical paper environments are not certified.\n',encoding='utf8',newline='\n');(target/'GPL-2.txt').write_bytes(__import__('pathlib').Path('C:/Program Files/R/R-4.6.1/share/licenses/GPL-2').read_bytes());print('Pinned2 exact SoupX workflow profiles; no execution validation claimed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/profile checks')
    main()
