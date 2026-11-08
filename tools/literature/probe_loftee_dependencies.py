# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only health checks of an already installed private VEP105 environment and exact original plugin source."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import BASE,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--environment',required=True,type=Path);p.add_argument('--plugin',required=True,type=Path);p.add_argument('--bioperl',type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();environment=a.environment.resolve();plugin=a.plugin.resolve();assert out.is_relative_to(BASE) and environment.is_relative_to(BASE) and plugin.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();(out/'probe-at-attempt.py').write_bytes(source.read_bytes());pins={source.relative_to(ROOT).as_posix():pin(source)};state=dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',analysis_executed=False,LOFTEE_annotation_admitted=False,implementation_credit=False,whole_goal_complete=False,source_pins=pins,required_action='Inspect retained original stdout/stderr and module identities. Supply a permitted working private VEP105.0/LOFTEE environment without replacing shared Perl libraries. Missing/forbidden dependencies skip annotation. Dependency health is not successful variant annotation or whole-tool/paper validation.')
    files={f.relative_to(environment).as_posix():pin(f) for f in (environment/'conda-meta').glob('*.json')};save(out/'installed-package-records.json',files);assert files
    shares=list((environment/'share').glob('ensembl-vep-105.0-*'));assert len(shares)==1;share=shares[0];includes=[plugin,share]
    if a.bioperl:
        bioperl=a.bioperl.resolve();assert bioperl.is_relative_to(BASE) and (bioperl/'Bio/Perl.pm').is_file();includes.append(bioperl)
    prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-i','HOME='+linux(out),'PATH='+linux(environment/'bin')+':/usr/bin:/bin','LC_ALL=C','PERL5LIB='+':'.join(linux(p) for p in includes),'LD_LIBRARY_PATH='+linux(environment/'lib')]
    checks={}
    def run(name,command):
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
        save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));checks[name]=r.returncode;return r.returncode
    try:
        assert run('vep-help',prefix+[linux(environment/'bin/vep'),'--help'])==0
        assert '105' in (out/'vep-help.stdout').read_text(encoding='utf8')
        code=r'''use JSON::PP; use LoF; use Bio::DB::BigFile; use DBD::SQLite; use List::MoreUtils; use Digest::SHA;
my %pins;
for my $name (sort keys %INC) {
    open my $f,"<:raw",$INC{$name} or die "Cannot read loaded module $name: $!";
    my $sha=Digest::SHA->new(256)->addfile($f)->hexdigest;
    $pins{$name}={path=>$INC{$name},sha256=>$sha,bytes=>-s $INC{$name}};
}
print JSON::PP->new->canonical->encode({perl=>$^V->normal,modules=>{map {$_=>$INC{$_}} sort keys %INC},module_file_pins=>\%pins}),"\n";
'''
        helper=out/'module-health.pl';helper.write_text(code,encoding='utf8',newline='\n')
        assert run('plugin-modules',prefix+[linux(environment/'bin/perl'),linux(helper)])==0
        metadata=json.loads((out/'plugin-modules.stdout').read_bytes());assert metadata['modules']['LoF.pm']==linux(plugin/'LoF.pm') and len(metadata['module_file_pins'])==len(metadata['modules']);assert {k:metadata['module_file_pins']['LoF.pm'][k] for k in ['bytes','sha256']}==pin(plugin/'LoF.pm')
        save(out/'loaded-module-identities.json',metadata);state.update(status='PASS_PRIVATE_VEP105_AND_EXACT_LOFTEE_MODULE_HEALTH_ONLY',environment=environment.relative_to(ROOT).as_posix(),plugin=plugin.relative_to(ROOT).as_posix(),plugin_sha256=pin(plugin/'LoF.pm'),dependency_checks=checks,loaded_module_identity=pin(out/'loaded-module-identities.json'),installed_package_records=pin(out/'installed-package-records.json'))
        print('VEP105 help and exact original LOFTEE module loads passed; no variant annotation executed')
    except Exception as error:state.update(error=repr(error),dependency_checks=checks);print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable dependency/source identity checks')
    raise SystemExit(main())
