# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check packaged native HOPS reports, explicit nonfinite tokens and actionable CLI refusals."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['out','jar21','jar25','source']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();out=a.out.resolve();source=a.source.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_PACKAGED_NATIVE_HOPS_CLI',new_tool_credit=0,full_hops_adapter_admitted=False,biological_result_admitted=False)
 try:
  guards={str(p):pin(p) for p in source.rglob('*') if p.is_file()};guards[str(Path(__file__).resolve())]=pin(Path(__file__).resolve());results=[];nodes=source/'literal-nodes.txt';duplicate=out/'duplicate-nodes.txt';duplicate.write_text(nodes.read_text().splitlines()[0]+'\n'+nodes.read_text().splitlines()[0]+'\n')
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();guards[str(jar)]=pin(jar)
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;root=out/label;root.mkdir();convert=linux if runtime=='linux21' else str;prefix=(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if runtime=='linux21' else [str((JAVA if runtime=='windows21' else JAVA25)/'java.exe')])+['-Xmx512m','-jar',convert(jar)]
    def run(name,arguments,code):
     cmd=[*prefix,*arguments];r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(root/(name+'.stdout')).write_bytes(r.stdout);(root/(name+'.stderr')).write_bytes(r.stderr);save(root/(name+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==code,(label,name,r.stderr);return r
    def arguments(target,selected=nodes):return ['--results',convert(source),'--nodes-file',convert(selected),'--out',convert(target)]
    target=root/'complete';run('complete',arguments(target),0);report=json.loads((target/'statistics.json').read_bytes());assert len(report['pairs'])==6 and report['reduced_matrix']=={'Homo_sapiens':{'JK2782.unmapped.rma6':2}} and report['new_tool_credit']==0 and report['biological_result_admitted'] is False;assert (target/'COMPONENT_COMPLETED.json').is_file() and 'not read counts' in (target/'WARNINGS.md').read_text()
    for name,args in [('capacity',arguments(root/'capacity')+['--maximum-samples','1']),('duplicate',arguments(root/'duplicate',duplicate))]:
     r=run(name,args,2);assert (root/name/'FAILURE.json').is_file() and not (root/name/'COMPONENT_COMPLETED.json').exists() and 'Required action:' in (root/name/'WARNINGS.md').read_text() and 'FAIL2 NO RESULT ADMITTED' in r.stderr.decode('utf8')
    existing=root/'existing';existing.mkdir();(existing/'keep.txt').write_text('keep complete existing output\n');r=run('existing',arguments(existing),2);assert list(existing.iterdir())==[existing/'keep.txt'] and (existing/'keep.txt').read_text()=='keep complete existing output\n';target=root/'unsupported-option';r=run('unsupported-option',arguments(target)+['--mode','default'],2);assert not target.exists() and 'Required action:' in r.stderr.decode('utf8');help_result=run('help',['--help'],0);assert 'no quote stripping' in help_result.stdout.decode('utf8').lower();results.append(dict(compiler=compiler,runtime=runtime,complete_source_pairs=6,refusals=4,help=1));print('Packaged native HOPS CLI passed',label,flush=True)
  assert all(pin(Path(p))==v for p,v in guards.items());state.update(status='PASS_36_PACKAGED_NATIVE_HOPS_SOURCE_PAIRS_24_CLI_REFUSALS_AND_6_HELP_CHECKS_ONLY',complete_source_pairs=36,cli_refusals=24,help_checks=6,results=results,originals_unchanged=guards)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/report checks')
 raise SystemExit(main())
