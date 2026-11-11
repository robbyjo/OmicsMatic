# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Own deterministic hard-call controls for original KING; no biological cohort or author example data."""
import argparse
import json
import random
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();jar=a.jar.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[]
    state=dict(status='FAIL2_OWN_SYNTHETIC_KING_CONTROLS',new_catalog_credit=False,whole_goal_complete=False)
    try:
        inputs=out/'own-inputs';inputs.mkdir();prefix=inputs/'cohort'
        n,m=16,8192;rng=random.Random(20261110)
        fam=[]
        for i in range(n):
            fid='GROUP_A' if i in [0,2] else 'GROUP_B' if i in [1,3] else 'F'+str(i)
            fam.append(f'{fid} S{i} 0 0 {i%2+1} -9\n')
        prefix.with_suffix('.fam').write_text(''.join(fam),encoding='utf8')
        bed=bytearray(b'\x6c\x1b\x01');bim=[]
        for j in range(m):
            chr=j//2048+1;bp=1000000+(j%2048)*100000
            bim.append(f'{chr}\tv{j}\t0\t{bp}\tA\tG\n')
            frequency=.1+(j%7)*.04
            calls=[]
            for i in range(n):
                dosage=int(rng.random()<frequency)+int(rng.random()<frequency)
                calls.append([3,2,0][dosage] if rng.random()>.005 else 1)
            calls[1]=calls[0] # Declared exact duplicate; no biological degree claim.
            calls[15]=3 # One all-homozygous participant: source-zero must remain unassessable.
            for start in range(0,n,4):bed.append(sum(calls[start+k]<<(2*k) for k in range(4)))
        prefix.with_suffix('.bed').write_bytes(bed);prefix.with_suffix('.bim').write_text(''.join(bim),encoding='utf8')
        input_pins={f.name:pin(f) for f in inputs.iterdir()};java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
        for platform in ['windows','linux']:
            convert=linux if platform=='linux' else lambda p:str(p.resolve())
            launch=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if platform=='linux' else [java]
            for mode,degree in [('kinship',0),('kinship',2),('kinship',3),('unrelated',2),('unrelated',3),('duplicate',0),('ibdseg',0)]:
                if platform=='windows' and mode=='ibdseg':continue
                name=platform+'-'+mode+'-'+str(degree);request=out/(name+'.request.json');bundle=out/(name+'.bundle')
                save(request,dict(schema_version=1,mode=mode,degree=degree,threads=1,bfile=convert(prefix),
                     runtime_profile=convert(BASE/('king-profile-'+platform+'-1/runtime-profile.json')),out=convert(bundle),limits=dict(
                         input_bytes=10000000,output_bytes=50000000,output_files=1000,samples=20,variants=10000,
                         pair_variant_operations=2000000,working_bytes=10000000,wall_seconds=120,rows=100000)))
                command=[*launch,'-Xmx512m','-jar',convert(jar),'--request',convert(request)]
                r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
                (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
                save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode))
                records.append(dict(platform=platform,mode=mode,degree=degree,exit_code=r.returncode))
                print(json.dumps(records[-1]),flush=True)
                assert r.returncode==0,(name,r.stderr[-2500:])
                for host in ['windows','linux']:
                    c=linux if host=='linux' else lambda p:str(p.resolve())
                    launcher=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if host=='linux' else [java]
                    v=subprocess.run([*launcher,'-Xmx512m','-jar',c(jar),'--verify',c(bundle)],capture_output=True,timeout=180)
                    (out/(name+'-verify-'+host+'.stdout')).write_bytes(v.stdout);(out/(name+'-verify-'+host+'.stderr')).write_bytes(v.stderr)
                    assert v.returncode==0,(name,host,v.stderr[-2000:])
                provenance=json.loads((bundle/'provenance.json').read_text())
                assert provenance['complete_native_pairs']==120 and provenance['zero_smaller_heterozygosity_pairs_unassessable']==15
                assert 'UNASSESSABLE PAIRS:' in (bundle/'WARNINGS.md').read_text()
                if mode=='duplicate':assert provenance['analysis']['reported_pairs']>=1
        assert {f.name:pin(f) for f in inputs.iterdir()}==input_pins
        state.update(status='PASS_OWN_SYNTHETIC_KING_ORIGINAL_EXECUTION_AND_REPLAY',records=records,
                     input_pins=input_pins,artifact=pin(jar),producer=pin(Path(__file__)),biological_cohort=False,
                     original_programs_or_author_example_bundled=False)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2500],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original synthetic-population and replay checks')
    raise SystemExit(main())
