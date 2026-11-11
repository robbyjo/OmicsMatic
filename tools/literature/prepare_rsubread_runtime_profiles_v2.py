# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Strengthen original runtime profiles with the previously observed complete pre-execution file populations."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_rsubread_reference import inventory


def main():
    old=ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference';out=ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference-v2';assert not out.exists();out.mkdir(parents=True)
    base=ROOT/'build/literature-review/rsubread-runtime';profiles={}
    for edition in ['2260','2232-R441']:
        binding=json.loads((base/'runtime-binding-tests-1'/(edition+'-bound/binding.json')).read_bytes());rhome=Path(binding['r_home']);lib=Path(binding['r_library']);observed=binding['runtime_files'];home=out/edition;home.mkdir()
        actual={}
        for name in ['bin','modules','etc','share','library']:
            for rel,value in inventory(rhome/name).items():actual[str(rhome/name/rel)]=value
        for rel,value in inventory(lib/'Rsubread').items():actual[str(lib/'Rsubread'/rel)]=value
        assert actual==observed,'Retain any changed runtime; never refresh known original runtime hashes'
        profile=json.loads((old/edition/'profile.json').read_bytes());profile['known_r_home_files']={Path(path).relative_to(rhome).as_posix():value for path,value in observed.items() if Path(path).is_relative_to(rhome)}
        profile['known_package_files']={Path(path).relative_to(lib/'Rsubread').as_posix():value for path,value in observed.items() if Path(path).is_relative_to(lib/'Rsubread')}
        profile['runtime_scope']='Before execution, require the exact previously recorded original R home and private package file populations plus known Rscript/DLL identities. Recheck all bound files and source helper definitions. Not complete OS/library source authentication or hard OS containment.'
        profile['runtime_population_scope']='R home bin/modules/etc/share/library and the private Rsubread package, including all filenames, sizes and hashes; selected observed Windows editions only. Do not refresh hashes after changes.'
        for name in ['R/featureCounts.R','R/cellCounts.R','DESCRIPTION']:
            dest=home/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((old/edition/name).read_bytes())
        save(home/'profile.json',profile);profiles[edition]=dict(profile=profile['profile'],files={p.relative_to(home).as_posix():pin(p) for p in home.rglob('*') if p.is_file()})
    save(out/'profiles.json',dict(schema_version=2,profiles=profiles,prior_profiles=dict(path=old.relative_to(ROOT).as_posix(),index=pin(old/'profiles.json')),known_population_binding_receipt=dict(path=(base/'runtime-binding-tests-1/COMPLETED.json').relative_to(ROOT).as_posix(),**pin(base/'runtime-binding-tests-1/COMPLETED.json')),implementation_credit=False,whole_goal_complete=False))
    print('Prepared exact pre-execution runtime populations from unchanged previously recorded original bindings; no hash refresh')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never refresh or bypass known runtime populations')
    main()
