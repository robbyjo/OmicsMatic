# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay every complete original checker population on three JVMs, without scientific software."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_NATIVE_CHIPSEQ200_MAPPING_CONTROLS',new_tool_credit=0,scientific_analysis_executed=False)
 try:
  reference=ROOT/'src/test/resources/org/jlinalg/workflow/nfcore-chipseq200/reference.zip';source=ROOT/'src/test/resources/org/jlinalg/workflow/nfcore-chipseq200/SOURCE.json';before=pin(reference);metadata=json.loads(source.read_bytes());fixtures=out/'reference';fixtures.mkdir()
  with zipfile.ZipFile(reference) as z:
   assert len(z.namelist())==len(set(z.namelist()))
   for info in z.infolist():
    path=(fixtures/info.filename).resolve();assert path.is_relative_to(fixtures) and not info.is_dir() and info.file_size<1048576;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(info));assert pin(path)==metadata['reference_files'][info.filename]
  originals={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.rglob('*') if p.is_file()};cases=metadata['source']['cases'];assert len(cases)==27;helper=ROOT/'tools/literature/java/NfcoreChipseq200MappingControl.java';helper_pin=pin(helper);parent=ROOT/'docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac21.jar';jars={c:Path(getattr(a,'jar'+c)).resolve() for c in ['21','25']};artifact_pins={c:pin(p) for c,p in jars.items()};checks=[]
  for compiler,jar in jars.items():
   classes=out/('helper'+compiler);classes.mkdir();java=JAVA if compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar)+';'+str(parent),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    for case in cases:
     name=case['name'];label=compiler+'-'+runtime+'-'+name;report=out/(label+'.audit.json');first=fixtures/name/'input.csv';second=fixtures/name/'checked.csv'
     if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx256m','-cp',linux(classes)+':'+linux(jar)+':'+linux(parent),'org.jlinalg.cli.NfcoreChipseq200MappingControl',linux(first),linux(second),linux(report)]
     else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx256m','-cp',str(classes)+';'+str(jar)+';'+str(parent),'org.jlinalg.cli.NfcoreChipseq200MappingControl',str(first),str(second),str(report)]
     r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=90);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));expected=0 if case['exit_code']==0 else 2;assert r.returncode==expected,(label,expected,r.returncode,r.stderr)
     if expected==0:
      audit=json.loads(report.read_bytes());assert audit['source_bytes_match'] and audit['literal_scientific_identifiers']==case['literal_scientific_identifiers'] and audit['consistent_library_metadata']==case['consistent_library_metadata'] and not audit['scientific_analysis_executed'];identity=pin(report)
     else:assert not report.exists() and b'FAIL2' in r.stderr;identity=None
     checks.append(dict(compiler=compiler,runtime=runtime,case=name,expected_exit=expected,passed=True,audit=identity))
  assert pin(reference)==before and pin(helper)==helper_pin and all(pin(fixtures/n)==v for n,v in originals.items()) and all(pin(jars[c])==v for c,v in artifact_pins.items());state.update(status='PASS_COMPLETE_NATIVE_CHIPSEQ200_MAPPING_CONTROLS_ONLY',checks=checks,source_reference=before,artifacts=artifact_pins,helper=helper_pin,original_populations=27,original_positive_populations=12,original_refused_populations=15,source_cells_and_bytes_compared_completely=True,reference_inputs_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable full source/control fidelity checks')
 raise SystemExit(main())
