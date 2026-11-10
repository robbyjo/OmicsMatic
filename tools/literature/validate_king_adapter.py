# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute exact original KING through the typed adapter; replay every complete bundle on both hosts."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--windows-java', choices=['21', '25'], default='21')
    a = p.parse_args()
    out, jar = a.out.resolve(), a.jar.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
    before, artifact = pin(canonical), pin(jar)
    original = BASE/'king-source-intake-1/ex-original/ex'
    controls = BASE/'king-workflows-2'
    java = 'C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if a.windows_java == '21'
                              else 'Common Files/Oracle/Java/javapath/java.exe')
    records = []
    state = dict(status='FAIL2_ORIGINAL_KING_ADAPTER_GATES', new_catalog_credit=False, whole_goal_complete=False)
    try:
        def invoke(platform, args, stem):
            convert = linux if platform == 'linux' else lambda x: str(x.resolve())
            command = (['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java'] if platform == 'linux' else [java])
            command += ['-Xmx1g', '-jar', convert(jar), *args]
            r = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=240)
            (out/(stem+'.stdout')).write_bytes(r.stdout); (out/(stem+'.stderr')).write_bytes(r.stderr)
            save(out/(stem+'.process.json'), dict(command=command, exit_code=r.returncode))
            return r
        for platform in ['windows','linux']:
            convert = linux if platform == 'linux' else lambda x: str(x.resolve())
            profile = BASE/('king-profile-'+platform+'-1/runtime-profile.json')
            cases = [('kinship',0,original,0), ('kinship',2,original,0), ('kinship',3,original,0),
                     ('unrelated',2,original,0),('unrelated',3,original,0),
                     ('duplicate',0,original,0),('duplicate',0,controls/'appended-duplicate',0),
                     ('kinship',0,controls/'first-four-founders',0),
                     ('ibdseg',0,original,3 if platform=='windows' else 0),
                     ('ibdseg',0,controls/'first-four',3 if platform=='windows' else 2),
                     ('ibdseg',0,controls/'first-four-founders',3 if platform=='windows' else 2)]
            for i,(mode,degree,prefix,expected) in enumerate(cases):
                stem=platform+'-'+str(i)+'-'+mode+'-'+str(degree)
                request=out/(stem+'.request.json'); bundle=out/(stem+'.bundle')
                save(request,dict(schema_version=1,mode=mode,degree=degree,threads=1,bfile=convert(prefix),
                                  runtime_profile=convert(profile),out=convert(bundle),limits=dict(
                                      input_bytes=100000000,output_bytes=200000000,output_files=1000,samples=500,
                                      variants=20000,pair_variant_operations=2000000000,working_bytes=200000000,
                                      wall_seconds=120,rows=200000)))
                r=invoke(platform,['--request',convert(request)],stem)
                record=dict(platform=platform,mode=mode,degree=degree,input=prefix.name,expected=expected,actual=r.returncode)
                records.append(record); print(json.dumps(record),flush=True)
                assert r.returncode==expected,(record,r.stderr[-2200:])
                disposition=json.loads((bundle/'COMPLETED.json').read_text())
                assert disposition['result_admitted']==(expected==0)
                for host in ['windows','linux']:
                    convert_host=linux if host=='linux' else lambda x:str(x.resolve())
                    v=invoke(host,['--verify',convert_host(bundle)],stem+'-verify-'+host)
                    assert v.returncode==(0 if expected==0 else 2),(record,host,v.stderr[-2200:])
                if expected:
                    assert b'REQUIRED ACTION:' in r.stderr and not (bundle/'results').exists()
                record['portable_complete_verifications']=2
        assert pin(canonical)==before and pin(jar)==artifact
        state.update(status='PASS_ORIGINAL_KING_ADAPTER_EXECUTION_AND_PORTABLE_REPLAY',records=records,
                     artifact=artifact,canonical_executable_unchanged=before,producer=pin(Path(__file__)))
    except Exception as error:
        state.update(error=repr(error),records=records)
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error','')[:2500],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-runtime/complete-replay gates')
    raise SystemExit(main())
