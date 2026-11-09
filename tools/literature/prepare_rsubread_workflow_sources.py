# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture immutable original helper modules and known Windows runtime identities for the execution adapter."""
import csv,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    from rsubread_result_evidence import verify
    previous,_=verify(False);assert previous['original_results']==50
    base=ROOT/'build/literature-review/rsubread-runtime';out=ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference'
    assert not out.exists();out.mkdir(parents=True);profiles={}
    for edition,setup,source,rversion in [('2260','reference-setup-3','reference-intake-1/source/unpacked','4.6.1'),('2232-R441','r441-reported-build-1','reported-source-2/source','4.4.1')]:
        home=out/edition;home.mkdir();receipt=json.loads((base/setup/'COMPLETED.json').read_bytes());rscript=Path(receipt['rscript']['path'])
        if not rscript.is_absolute():rscript=ROOT/rscript
        rhome=rscript.parents[2] if edition=='2232-R441' else rscript.parents[1]
        with (base/setup/'loaded-dlls.tsv').open(encoding='utf8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
        dlls={row['name']:pin(rhome/'bin/x64/R.dll' if row['path']=='base' else Path(row['path'])) for row in rows}
        resources={}
        for name in ['R/featureCounts.R','R/cellCounts.R','DESCRIPTION']:
            path=base/source/name;dest=home/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(path.read_bytes());resources[name]=pin(dest)
        profile=dict(profile='Rsubread-'+('2.26.0' if edition=='2260' else '2.23.2-666b63d')+'-R'+rversion+'-Windows-x86_64',edition=edition,
                     package_version='2.26.0' if edition=='2260' else '2.23.2',R_version=rversion,platform='Windows-x86_64',
                     source_commit='2c0bab4' if edition=='2260' else '666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6',
                     original_helpers=resources,known_rscript=pin(rscript),known_dlls=dlls,
                     paper_binary_identified=False,source_scope='Original featureCounts/cellCounts helper definitions are guarded; only declared bulk counting is exposed. Matching version labels does not identify a paper binary.',
                     runtime_scope='Exact known Rscript and loaded DLL identities plus helper body/formal guards; full bound file populations are rechecked. Not complete OS/library source authentication or hard OS containment.',
                     upstream_development_warning_retained=edition=='2232-R441',license='GPL (>=3)')
        save(home/'profile.json',profile);profiles[edition]=dict(profile=profile['profile'],files={q.relative_to(home).as_posix():pin(q) for q in home.rglob('*') if q.is_file()})
    save(out/'profiles.json',dict(schema_version=1,profiles=profiles,source_receipts={name:dict(path=(base/name/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(base/name/'COMPLETED.json')) for name in ['reference-setup-3','r441-reported-build-1']},implementation_credit=False,whole_goal_complete=False))
    print('Prepared two immutable original helper/runtime profiles; no installation or analysis')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source/runtime guards')
    main()
