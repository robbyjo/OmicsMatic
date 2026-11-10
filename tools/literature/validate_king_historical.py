# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run exact historical KING profiles, including public non-word-aligned missing-call counterexamples."""
import argparse
import json
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def boundary_control(source,target,variants):
    """New declared synthetic data, with last-word real missing calls and untouched original controls."""
    fam=source.with_suffix('.fam').read_bytes();n=len(fam.splitlines());assert n==16
    target.with_suffix('.fam').write_bytes(fam)
    bim=[r.split() for r in source.with_suffix('.bim').read_text().splitlines()]
    data=source.with_suffix('.bed').read_bytes();width=4
    bed=bytearray(data[:3]+data[3:3+min(variants,8192)*width])
    while len(bim)<variants:
        j=len(bim);bim.append(['4','v'+str(j),'0',str(1000000+(j-6144)*100000),'A','G'])
        bed.extend(b'\xff'*width)
    bim=bim[:variants]
    start=(variants-1)//16*16
    for j in range(start,variants):
        calls=[3]*n
        if j==start:calls[0]=1
        if j==min(start+1,variants-1):calls[1]=1
        # Some heterozygotes keep the declared SNP alleles represented where possible.
        calls[2]=2
        row=bytes(sum(calls[k+t]<<(2*t) for t in range(4)) for k in range(0,n,4))
        bed[3+j*width:3+(j+1)*width]=row
    assert len(bed)==3+variants*width
    target.with_suffix('.bed').write_bytes(bed)
    target.with_suffix('.bim').write_text(''.join('\t'.join(r)+'\n' for r in bim),encoding='utf8')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--jar',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();jar=a.jar.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[]
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);artifact=pin(jar)
    source=BASE/'king-own-controls-1/own-inputs/cohort';official=BASE/'king-source-intake-1/ex-original/ex'
    java=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')
    inputs=out/'own-boundaries';inputs.mkdir()
    boundaries={m:inputs/('variants-'+str(m)) for m in [8191,8193,8194,8207,8208]}
    for m,prefix in boundaries.items():boundary_control(source,prefix,m)
    preserved={str(p):pin(p) for prefix in [source,official,*boundaries.values()] for e in ['bed','bim','fam'] for p in [prefix.with_suffix('.'+e)]}
    state=dict(status='FAIL2_HISTORICAL_KING_ADAPTER_GATES',new_catalog_credit=False,whole_goal_complete=False)
    try:
        def invoke(platform,arguments,name):
            convert=linux if platform=='linux' else lambda p:str(p.resolve())
            launch=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if platform=='linux' else [str(java)]
            command=[*launch,'-Xmx1g','-jar',convert(jar),*arguments]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return r
        profiles=[('2.2.7-windows','2.2.7','windows','king-historical-profile-227-windows-1'),
                  ('2.2.7-linux','2.2.7','linux','king-historical-profile-227-linux-1'),
                  ('2.0-linux','2.0','linux','king-historical-profile-20-linux-1'),
                  ('2.0-ubuntu','2.0','linux','king-historical-profile-20-ubuntu-1')]
        for profile,version,platform,profile_home in profiles:
            convert=linux if platform=='linux' else lambda p:str(p.resolve())
            cases=[('official-kinship',official,'kinship',2 if version=='2.0' else 0),
                   ('own-kinship',source,'kinship',0),('own-duplicate',source,'duplicate',0),
                   ('official-duplicate',official,'duplicate',0)]
            cases += [('boundary-'+str(m),prefix,'kinship',2 if version=='2.0' and m%16 else 0) for m,prefix in boundaries.items()]
            for name,prefix,mode,expected in cases:
                stem=profile+'-'+name;bundle=out/(stem+'.bundle');request=out/(stem+'.request.json')
                spec=dict(schema_version=1,version=version,mode=mode,degree=0,threads=1,bfile=convert(prefix),
                    runtime_profile=convert(BASE/profile_home/'runtime-profile.json'),out=convert(bundle),limits=dict(
                        input_bytes=100000000,output_bytes=200000000,output_files=1000,samples=500,variants=20000,
                        pair_variant_operations=2000000000,working_bytes=200000000,wall_seconds=120,rows=200000))
                save(request,spec);r=invoke(platform,['--request',convert(request)],stem)
                record=dict(profile=profile,version=version,case=name,expected=expected,actual=r.returncode,bundle=bundle.name)
                records.append(record);print(json.dumps(record),flush=True)
                assert r.returncode==expected,(record,r.stderr[-3000:])
                done=json.loads((bundle/'COMPLETED.json').read_bytes());assert done['result_admitted']==(expected==0)
                audit=json.loads((bundle/'scientific-audit.json').read_bytes())
                if expected:
                    assert audit['scientific_discrepancies'] and not audit['all_scientific_fields_passed']
                    assert b'ORIGINAL KING2.0 SCIENTIFIC DISCREPANCY' in r.stderr
                    assert (bundle/'unadmitted-worker/original.kin0').exists() and not (bundle/'results').exists()
                    record['discrepant_fields']=len(audit['scientific_discrepancies'])
                    if name.startswith('boundary-'):
                        differences=[d for d in audit['scientific_discrepancies'] if d['field']=='between.N_SNP']
                        assert any(set(d['source_pair'][1::2])=={'S0','S1'} and int(d['original_value'])-int(d['native_expected'])==16-int(name.split('-')[1])%16 for d in differences)
                else:
                    assert audit['all_scientific_fields_passed'] and not audit['scientific_discrepancies']
                    if platform=='windows':assert (bundle/'results/original$TMP$.ped').exists() and (bundle/'results/original$TMP$.dat').exists()
                # Compare every separately captured original kinship/concordance payload where a direct reference exists.
                if name in ['official-kinship','own-kinship','own-duplicate']:
                    direct=BASE/'king-historical-probe-2'/profile/name
                    result=bundle/('results' if expected==0 else 'unadmitted-worker')
                    names=['original.kin','original.kin0'] if mode=='kinship' else ['original.con']
                    for member in names:assert (result/member).read_bytes()==(direct/member).read_bytes(),(profile,name,member)
                    record['complete_separate_original_payload_equal']=True
                for host in ['windows','linux']:
                    c=linux if host=='linux' else lambda p:str(p.resolve())
                    v=invoke(host,['--verify',c(bundle)],stem+'-verify-'+host)
                    assert v.returncode==(0 if expected==0 else 2),(record,host,v.stderr[-2200:])
                record['portable_saved_checks']=2
        assert all(pin(Path(p))==v for p,v in preserved.items()) and pin(canonical)==before and pin(jar)==artifact
        state.update(status='PASS_HISTORICAL_KING_ADAPTER_WITH_PRESERVED_KING20_DISCREPANCIES',records=records,artifact=artifact,
                     complete_inputs_unchanged=preserved,canonical_executable_unchanged=before,producer=pin(Path(__file__)),
                     source_discrepancies_repaired=False,version_or_flag_substitution_applied=False)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],len(records),state.get('error','')[:3000],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/census discrepancy and skip/admission checks')
    raise SystemExit(main())
