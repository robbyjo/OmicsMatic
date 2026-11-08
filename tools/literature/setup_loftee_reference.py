# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a private VEP105/LOFTEE reference runtime; no shared environment or variant analysis is modified."""
import argparse,io,json,os,subprocess,tarfile,urllib.error,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/loftee-runtime'


def linux(path):
    text=path.resolve().as_posix();assert text[1:3]==':/';return '/mnt/'+text[0].lower()+text[2:]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--wsl-distribution',default='JLinAlg-Omics-SAIGE-20261026');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',analysis_executed=False,implementation_credit=False,whole_goal_complete=False,shared_environment_modified=False,required_action='Where installation is permitted, supply a private working VEP105.0/Perl/BioPerl/BioBigFile/SQLite/samtools environment and exact original LOFTEE GRCh38 sources. Preserve installer logs and retry NEW paths after correcting the named network/package/permission issue. Skip LOFTEE-dependent analysis until real plugin execution and complete output/warning validation succeed. This helper prepares dependencies only; it does not download human annotation/reference data or registered dbNSFP resources.')
    state['source_pins']={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))}
    (out/'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    if not a.allow_install:
        save(out/'COMPLETED.json',state);print(state['status']+': installation not requested; dependent analysis skipped');return 3
    save(out/'STARTED.json',dict(status='RUNNING_PRIVATE_REFERENCE_PREPARATION',analysis_executed=False,source_pins=state['source_pins']))
    prefix=['wsl','-d',a.wsl_distribution,'--']
    env=dict(os.environ)
    def run(label,command,timeout=1800):
        save(out/(label+'.command.json'),dict(command=command))
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));return r.returncode
    try:
        host=prefix+['/usr/bin/env','-i','PATH=/usr/bin:/bin','LC_ALL=C','/usr/bin/uname','-sm'];assert run('host',host,60)==0;assert (out/'host.stdout').read_text(encoding='utf8').strip()=='Linux x86_64'
        url='https://micro.mamba.pm/api/micromamba/linux-64/latest';request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-private-reference-setup/1.0'})
        with urllib.request.urlopen(request,timeout=90) as response:
            raw=response.read(16777217);assert len(raw)<=16777216;archive=out/'micromamba.tar.bz2';archive.write_bytes(raw);save(out/'micromamba-download.json',dict(url=url,final_url=response.url,http_status=response.status,headers=dict(response.headers.items()),archive=pin(archive)))
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:bz2') as archive:
            members=[m for m in archive.getmembers() if m.name=='bin/micromamba'];assert len(members)==1 and members[0].isfile() and members[0].size<=33554432;data=archive.extractfile(members[0]).read();assert len(data)==members[0].size;binary=out/'micromamba';binary.write_bytes(data)
        assert run('chmod-private-micromamba',prefix+['/bin/chmod','700',linux(binary)],60)==0
        assert run('micromamba-version',prefix+[linux(binary),'--version'],60)==0
        common=prefix+['/usr/bin/env','-i','PATH=/usr/bin:/bin','HOME='+linux(out),'LC_ALL=C','MAMBA_ROOT_PREFIX='+linux(out/'mamba-root'),'MAMBA_NO_BANNER=1',linux(binary),'--no-rc','create','--yes','--prefix',linux(out/'environment'),'--override-channels','-c','conda-forge','-c','bioconda','--strict-channel-priority','ensembl-vep=105.0','perl-bio-bigfile','perl-dbd-sqlite','perl-list-moreutils','samtools=1.19.2']
        code=run('environment-plan',common+['--dry-run','--json'],1200)
        if code:raise RuntimeError('Private dependency solve failed; inspect environment-plan.stderr/stdout')
        code=run('environment-install',common+['--json'],1800)
        if code:raise RuntimeError('Private dependency installation failed; inspect environment-install.stderr/stdout')
        listing=prefix+[linux(binary),'--no-rc','list','--prefix',linux(out/'environment'),'--json'];assert run('installed-packages',listing,60)==0
        listing_json=json.loads((out/'installed-packages.stdout').read_bytes());entries=listing_json['packages'] if isinstance(listing_json,dict) else listing_json;assert isinstance(entries,list) and any(e['name']=='ensembl-vep' and e['version']=='105.0' for e in entries)
        state.update(status='PASS_PRIVATE_VEP105_DEPENDENCIES_PREPARED_ONLY',environment=linux(out/'environment'),packages=entries,micromamba=pin(binary),micromamba_version=(out/'micromamba-version.stdout').read_text(encoding='utf8').strip(),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
        print('Prepared private VEP105 dependencies; LOFTEE model/resource execution is still unvalidated')
    except Exception as error:state['error']=repr(error);print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable installation/profile checks')
    raise SystemExit(main())
