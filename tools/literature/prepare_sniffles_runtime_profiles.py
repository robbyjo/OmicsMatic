# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record exact original sources and the tested Python/private-library/legacy-binary profile.

This prepares data for a pre-Python Java guard, not an admitted runtime binding.
"""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    base=ROOT/'build/literature-review/sniffles-runtime';runtime=base/'reference-setup-3'
    source=json.loads((base/'source-intake-3/COMPLETED.json').read_bytes());setup=json.loads((runtime/'COMPLETED.json').read_bytes())
    assert source['status']=='passed-five-exact-Sniffles-source-editions-only' and setup['status']=='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY'
    assert pin(runtime/'RUNTIME-FILES.json')==setup['runtime_files']
    population=json.loads((runtime/'RUNTIME-FILES.json').read_bytes());prefix='lib/python3.12/site-packages/'
    dependencies={'pysam','pysam.libs','pysam-0.23.3.dist-info','edlib.cpython-312-x86_64-linux-gnu.so',
                  'edlib-1.3.9.post1.dist-info','psutil','psutil-7.1.0.dist-info'}
    site={name[len(prefix):]:value for name,value in population.items() if name.startswith(prefix)
          and name[len(prefix):].split('/')[0] in dependencies}
    assert site and all(set(value)=={'bytes','sha256'} for value in site.values())
    shared=json.loads((runtime/'shared-before.json').read_bytes())
    assert shared==json.loads((runtime/'shared-after.json').read_bytes())
    assert pin(runtime/'venv/bin/python')==shared['python']
    profile=dict(schema_version=1,status='EXACT_TESTED_SNIFFLES_SOURCE_AND_DEPENDENCY_PROFILE_ONLY',
        editions={tag:dict(commit=row['commit'],members=row['members']) for tag,row in source['editions'].items()},
        python=shared['python'],python_version=[3,12,3],python_home='/usr/bin',
        python_executable='/usr/bin/python3.12',stdlib_root='/usr/lib/python3.12',
        stdlib_members=shared['standard_library'],stdlib_zip='/usr/lib/python312.zip',stdlib_zip_required_absent=True,
        private_site_members=site,private_site_relative='site',
        packages=dict(pysam='0.23.3',edlib='1.3.9.post1',psutil='7.1.0'),
        legacy_binary=setup['legacy_binary'],legacy_cpp_language_standard=11,
        original_paper_binaries_identified=False,execution_adapter_validation_pending=True)
    save(out/'profiles.json',profile)
    if a.apply:
        destination=ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution'
        destination.mkdir(parents=True,exist_ok=True)
        (destination/'profiles.json').write_bytes((out/'profiles.json').read_bytes())
    save(out/'COMPLETED.json',dict(status='PASS_EXACT_SNIFFLES_PREEXECUTION_PROFILE_PREPARATION_ONLY',
        applied=a.apply,profile=pin(out/'profiles.json'),source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
        source_intake=pin(base/'source-intake-3/COMPLETED.json'),runtime_setup=pin(runtime/'COMPLETED.json'),
        implementation_credit=False,whole_goal_complete=False))
    print('Prepared five original source profiles and',len(site),'private-site files; execution binding remains pending')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/dependency profile checks')
    main()
