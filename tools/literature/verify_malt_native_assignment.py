# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay the native MALT assignment component against complete saved original-runtime references."""
import argparse,hashlib,io,json,lzma,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
from verify_nfcore_eager_screening_checkpoint import extract

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_SAVED_NATIVE_MALT_ASSIGNMENT_REPLAY',new_tool_credit=0,full_malt_adapter_admitted=False,biological_result_admitted=False,whole_goal_complete=False)
 def run(label,command):
  result=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=600);(out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=result.returncode));assert result.returncode==0,(label,result.returncode)
 try:
  public=ROOT/'docs/literature/malt-native-assignment-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['status']=='VERIFIED_NATIVE_MALT_TAXONOMY_AND_ASSIGNMENT_COMPONENT_ONLY' and proof['new_tool_credit']==0
  for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value,name
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];raw=lzma.decompress(archive.read_bytes());assert len(raw)<=268435456
  with zipfile.ZipFile(io.BytesIO(raw)) as z:manifest=json.loads(z.read('MANIFEST.json'));wanted=dict(manifest['files']);body=z.read('MANIFEST.json');wanted['MANIFEST.json']=dict(bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
  extract(raw,out/'evidence',wanted);assert not any(path.suffix.lower() in ['.jar','.sif','.class'] for path in (out/'evidence').rglob('*') if path.is_file());replay=out/'evidence/replay';evidence=out/'evidence/evidence'
  audit=json.loads((evidence/'malt-rma-original-audit-3/COMPLETED.json').read_bytes());assert audit['status']=='PASS_COMPLETE_ORIGINAL_MALT_BUNDLED_TAXONOMY_RMA_ASSIGNMENTS_ALL172_READS_175_MATCHES_ONLY';assert audit['bundled_runtime_taxonomy_used'] and not audit['database_taxonomy_substituted'];assert json.loads((evidence/'malt-rma-original-audit-3/original-api.process.json').read_bytes())['exit_code']==0
  matrix=evidence/'malt-native-control-3';record=json.loads((matrix/'COMPLETED.json').read_bytes());assert record['status']=='PASS_NATIVE_COMPLETE_MALT_TAXONOMY_AND_SOURCE_ASSIGNMENT_CONTROLS_ONLY';assert len(record['results'])==6 and record['synthetic_cases_per_configuration']==12275;assert json.loads((matrix/'original-oracle.process.json').read_bytes())['exit_code']==0;assert (replay/'expected/disabled-set-alias.txt').read_text().strip()=='true'
  for number in [1,2]:assert json.loads((evidence/('malt-native-control-'+str(number))/'COMPLETED.json').read_bytes())['status']=='FAIL2_NATIVE_MALT_ASSIGNMENT_CONTROLS'
  assert json.loads((evidence/'malt-native-control-1/FAILURE_SOURCE.json').read_bytes())['synthetic_mismatches']==918
  fail2=evidence/'malt-native-control-2';old=(fail2/'rma-expected.tsv').read_bytes();observed=(fail2/'21-windows21/rma-assignments.tsv').read_bytes();assert old!=observed and old.replace(b'\r\n',b'\n')==observed
  import xml.etree.ElementTree as ET
  junit=ET.parse(evidence/'malt-native-junit-1/TEST-org.jlinalg.metagenomics.MaltAssignmentTest.xml').getroot();assert int(junit.attrib['tests'])==3 and all(int(junit.attrib[key])==0 for key in ['failures','errors','skipped'])
  expected='library\tuid\tactive_indices\tassigned_taxon\n';reads=matches=0
  for path in sorted((replay/'rma').glob('*.reads.tsv')):
   label=path.name.removesuffix('.reads.tsv')
   for line in path.read_text().splitlines()[1:]:
    fields=line.split('\t');assert len(fields)==11 and fields[9]==fields[10];expected+=label+'\t'+fields[0]+'\t'+fields[8]+'\t'+fields[9]+'\n';reads+=1;matches+=int(fields[7])
  assert reads==172 and matches==175;expected=expected.encode('utf8');assert expected==(replay/'expected/rma-assignments.tsv').read_bytes()
  missing=[str(java/name) for java in [JAVA,JAVA25] for name in ['java.exe','javac.exe'] if not (java/name).is_file()]
  if not shutil.which('wsl.exe'):missing.append('wsl.exe with '+DISTRO+' and /usr/bin/java (Linux Java21)')
  if missing:state.update(status='SKIP3_NATIVE_MALT_CROSS_RUNTIME_REPLAY_MISSING_PREREQUISITES',warnings=['SKIPPED: native cross-runtime verification was not performed. Required action: supply permitted JDK21/JDK25 runtimes and the named Linux Java21 environment, then retry a NEW output directory.'],missing=missing);save(out/'COMPLETED.json',state);print(state['status'],state['warnings'][0],missing);return 3
  results=[]
  for compiler,java in [('21',JAVA),('25',JAVA25)]:
   classes=out/('classes'+compiler);classes.mkdir();sources=[ROOT/'src/main/java/org/jlinalg/metagenomics/MaltTaxonomy.java',ROOT/'src/main/java/org/jlinalg/metagenomics/MaltAssignment.java',ROOT/'tools/literature/java/MaltAssignmentProbe.java'];run('javac'+compiler,[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',str(classes),*[str(path) for path in sources]])
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;target=out/label;args=[replay/'ncbi.tre',replay/'cases.tsv',replay/'rma',target,replay/'wide.tre']
    if runtime=='linux21':command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/timeout','--signal=TERM','--kill-after=5s','480s','/usr/bin/java','-Xmx2g','-cp',linux(classes),'MaltAssignmentProbe',*[linux(path) for path in args]]
    else:command=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2g','-cp',str(classes),'MaltAssignmentProbe',*[str(path) for path in args]]
    run(label,command)
    for name in ['tree-summary.tsv','synthetic.tsv','wide-digest.txt','wide-assignments.tsv','rma-assignments.tsv']:assert (target/name).read_bytes()==(replay/'expected'/name).read_bytes(),(label,name)
    results.append(dict(compiler=compiler,runtime=runtime,complete_tree_nodes=1703609,wide_tree_nodes=65539,synthetic_cases=12275,all_rma_reads=reads,all_rma_matches=matches));print('Saved native MALT references matched',label,flush=True)
  assert pin(archive)==proof['evidence_archive'] and all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
  state.update(status='PASS_SAVED_NATIVE_MALT_ASSIGNMENT_COMPONENT_REPLAY_ONLY',results=results,retained_failed_control_attempts=2,source_image_or_original_bytecode_rerun=False,junit_evidence_checked_not_rerun=True,evidence_files=len(manifest['files']))
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc(),warnings=['ERROR: no new validation result is admitted. Required action: inspect the named failure and retained diagnostics; fix the prerequisite or input and retry a NEW output directory.'])
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/reference comparisons')
 raise SystemExit(main())
