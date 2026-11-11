# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check packaged native screening CLI reports, complete-source populations and prominent failure actions."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['out','jar21','jar25','reference','expected','mutations']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();out=a.out.resolve();reference=a.reference.resolve();expected=a.expected.resolve();mutations=a.mutations.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_PACKAGED_NATIVE_EAGER_SCREENING_CLI',new_tool_credit=0,full_eager_adapter_admitted=False,biological_result_admitted=False)
 try:
  cases=json.loads((reference/'cases.json').read_bytes());wanted=json.loads((expected/'expected-populations.json').read_bytes());assert len(cases)==len(wanted)==2;guards={str(p):pin(p) for folder in [reference,expected,mutations] for p in folder.rglob('*') if p.is_file()};guards[str(Path(__file__).resolve())]=pin(Path(__file__).resolve());results=[]
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();guards[str(jar)]=pin(jar)
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;root=out/label;root.mkdir();convert=linux if runtime=='linux21' else str
    prefix=(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if runtime=='linux21' else [str((JAVA if runtime=='windows21' else JAVA25)/'java.exe')])+['-Xmx512m','-cp',convert(jar),'org.jlinalg.cli.NfcoreEagerScreeningCli']
    def run(name,options,code):
     cmd=[*prefix,*options];r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(root/(name+'.stdout')).write_bytes(r.stdout);(root/(name+'.stderr')).write_bytes(r.stderr);save(root/(name+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==code,(label,name,r.stderr);return r
    def options(row,target,replacements=None):
     replace=replacements or {};values={key:replace.get(key,reference/row[key]) for key in ['mapped_bam','filtered_bam','unmapped_fastq','malt_sam_gzip']};values.update(malt_reference_db=reference/'ref.db',malt_reference_index=reference/'ref.idx',malt_reference_info=reference/'ref.inf');args=[]
     for key,value in values.items():args+=['--'+key.replace('_','-'),convert(value)]
     return args+['--minimum-mapq','37','--minimum-length','0','--out',convert(target)]
    for i,row in enumerate(cases):
     name='complete-'+row['library'];target=root/name;run(name,options(row,target),0);assert (target/'COMPONENT_COMPLETED.json').is_file() and not (target/'FAILURE.json').exists();audit=json.loads((target/'audit.json').read_bytes());assert audit['host']==wanted[i]['host'] and audit['malt']==wanted[i]['malt'];assert audit['new_tool_credit']==0 and audit['full_eager_adapter_admitted'] is False and audit['biological_result_admitted'] is False
    for name,extra,replacements in [('capacity',['--maximum-records','1'],{}),('last-quality',[],dict(unmapped_fastq=mutations/'last-quality.fastq.gz'))]:
     target=root/name;r=run(name,options(cases[0],target,replacements)+extra,2);assert (target/'FAILURE.json').is_file() and not (target/'COMPONENT_COMPLETED.json').exists();assert 'FAIL2 NO RESULT ADMITTED' in r.stderr.decode('utf8') and 'Required action:' in (target/'WARNINGS.md').read_text()
    sentinel=root/'existing';sentinel.mkdir();(sentinel/'keep.txt').write_text('preserve existing output\n');r=run('existing-output',options(cases[0],sentinel),2);assert list(sentinel.iterdir())==[sentinel/'keep.txt'] and (sentinel/'keep.txt').read_text()=='preserve existing output\n';assert 'Required action:' in r.stderr.decode('utf8')
    target=root/'unknown-option';r=run('unknown-option',options(cases[0],target)+['--unknown','true'],2);assert not target.exists() and 'Required action:' in r.stderr.decode('utf8');help_result=run('help',['--help'],0);assert '--malt-reference-info' in help_result.stdout.decode('utf8');results.append(dict(compiler=compiler,runtime=runtime,complete_libraries=2,refusals=4,help=1));print('Packaged native screening CLI passed',label,flush=True)
  assert all(pin(Path(p))==v for p,v in guards.items());state.update(status='PASS_12_PACKAGED_NATIVE_SCREENING_LIBRARY_CHECKS_24_CLI_REFUSALS_AND_6_HELP_CHECKS_ONLY',results=results,complete_library_checks=12,cli_refusals=24,help_checks=6,originals_unchanged=guards)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/input/report checks')
 raise SystemExit(main())
