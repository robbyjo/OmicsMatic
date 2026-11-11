# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run original VEP105's API/BioPerl installer into a NEW private directory; no shared Perl or existing environment changes."""
import argparse,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import BASE,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--environment',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();env=a.environment.resolve();assert out.is_relative_to(BASE) and env.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);share=list((env/'share').glob('ensembl-vep-105.0-*'));assert len(share)==1;share=share[0];source=Path(__file__).resolve();(out/'installer-at-attempt.py').write_bytes(source.read_bytes());state=dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',analysis_executed=False,implementation_credit=False,whole_goal_complete=False,source_pins={source.relative_to(ROOT).as_posix():pin(source)},required_action='Missing Bio::Perl prevents original LOFTEE module loading. Where permitted prepare the BioPerl release selected by original VEP105 in a NEW private directory. Preserve installer logs and skip dependent annotation until module and model validation pass. Do not stub Bio::Perl or change shared Perl libraries.')
    if not a.allow_install:save(out/'COMPLETED.json',state);print(state['status']);return 3
    command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(out),'--','/usr/bin/env','-i','HOME='+linux(out),'PATH='+linux(env/'bin')+':/usr/bin:/bin','LC_ALL=C','PERL5LIB='+linux(share),'LD_LIBRARY_PATH='+linux(env/'lib'),linux(env/'bin/vep_install'),'--AUTO','a','--NO_UPDATE','--NO_HTSLIB','--DESTDIR',linux(out/'api')]
    save(out/'STARTED.json',dict(status='RUNNING_PRIVATE_ORIGINAL_VEP105_BIOPERL_INSTALLER',command=command,source_pins=state['source_pins']))
    try:
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        save(out/'installer.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0
        assert (out/'api/Bio/Perl.pm').is_file();state.update(status='PASS_PRIVATE_ORIGINAL_VEP105_BIOPERL_PREPARATION_ONLY',bioperl=linux(out/'api'),Bio_Perl=pin(out/'api/Bio/Perl.pm'))
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status']);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable dependency/source checks')
    raise SystemExit(main())
