# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete native MALT RMA I/O, serial reassignment and CLI source controls."""
from pathlib import Path
import argparse,csv,hashlib,io,json,lzma,os,re,shutil,subprocess,sys,time,traceback,zipfile
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
from verify_nfcore_eager_screening_checkpoint import extract
ACTION='Required action: retain the complete inputs and this attempt. Supply the permitted exact MALT0.4.1 reader jars with --runtime, or allow --install-runtime to download the documented public package. Supply JDK21, JDK25 and the named WSL Linux Java21 runtime; retry a NEW --out under build/literature-review/nfcore-runtime. If a prerequisite cannot be installed, skip the dependent verification and analysis. No full MALT, alignment or biological result is admitted.'
PROGRAM={'bytes':4641119,'sha256':'9106de3134a8f9d96bcd35986ee7a61738a1b51fca1683bb32bb108e82e92cf4'}
DATA={'bytes':24731618,'sha256':'718a23ad8defbe34a35c9b0967c5fa616900adba34e8b961fb5531cd2382d2e3'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);group=p.add_mutually_exclusive_group();group.add_argument('--runtime',type=Path);group.add_argument('--install-runtime',action='store_true');p.add_argument('--java21-bin',type=Path,default=JAVA);p.add_argument('--java25-bin',type=Path,default=JAVA25);p.add_argument('--linux-distro',default=DISTRO);a=p.parse_args();out=a.out.resolve()
 if not out.is_relative_to(BASE) or out.exists():p.error('Require a NEW --out under '+str(BASE))
 out.mkdir(parents=True);state=dict(status='FAIL2_SAVED_NATIVE_MALT_RMA_REPLAY',new_tool_credit=0,full_malt_adapter_admitted=False,alignment_executed=False,biological_result_admitted=False,whole_goal_complete=False);env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
 def finish(code):
  state['required_action']=ACTION;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+str(state.get('cause','Saved original references were compared; the original workflow image and original processor were not rerun.'))+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('cause',''),'\n'+ACTION,flush=True);return code
 def run(label,command,expected=0):
  start=time.time()
  with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
   child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,env=env);save(out/(label+'.live.json'),dict(command=command,pid=child.pid,started_unix=start))
   try:code=child.wait(timeout=720)
   except subprocess.TimeoutExpired:
    child.kill();code=child.wait(timeout=30);save(out/(label+'.timeout.json'),dict(terminal_exit_code=code));raise RuntimeError('Timed out and terminated '+label)
  save(out/(label+'.process.json'),dict(command=command,exit_code=code,expected=expected,elapsed_seconds=time.time()-start));
  if expected is not None:assert code==expected,(label,code,expected)
  return code
 try:
  public=ROOT/'docs/literature/malt-rma-reassignment-development';proof=json.loads((public/'VALIDATION.json').read_bytes());assert proof['status']=='VERIFIED_NATIVE_MALT_SERIAL_RMA_REASSIGNMENT_COMPONENT_ONLY' and proof['new_tool_credit']==0
  guards=proof['source_pins'];assert all(pin(ROOT/name)==value for name,value in guards.items())
  archive=public/'controls.zip.xz';assert pin(archive)==proof['evidence_archive'];decoder=lzma.LZMADecompressor();raw=decoder.decompress(archive.read_bytes(),max_length=268435457);assert decoder.eof and not decoder.unused_data and len(raw)<=268435456
  with zipfile.ZipFile(io.BytesIO(raw)) as z:
   manifest=json.loads(z.read('MANIFEST.json'));wanted=dict(manifest['files']);body=z.read('MANIFEST.json');wanted['MANIFEST.json']=dict(bytes=len(body),sha256=hashlib.sha256(body).hexdigest())
  extract(raw,out/'evidence',wanted)
  assert not any(p.suffix.lower() in ['.jar','.class','.sif','.exe','.dll'] or p.name.endswith('.tar.bz2') for p in (out/'evidence').rglob('*') if p.is_file())
  evidence=out/'evidence/evidence';original=evidence/'malt-serial-original-reference-1';assert json.loads((original/'COMPLETED.json').read_bytes())['status']=='PASS_ORIGINAL_MALT_SERIAL_REASSIGNMENT_32_DISPOSABLE_COPY_REFERENCES_ONLY';assert json.loads((original/'original-launch.process.json').read_bytes())['exit_code']==0
  assert json.loads((evidence/'malt-serial-native-control-1/COMPLETED.json').read_bytes())['status']=='PASS_NATIVE_MALT_SERIAL_PIPELINE_32_ORIGINAL_PROFILES_SIX_CONFIGURATIONS_ONLY'
  assert json.loads((evidence/'malt-reassign-cli-control-1/COMPLETED.json').read_bytes())['status']=='PASS_NATIVE_MALT_CLI_30_SOURCE_PROFILES_AND24_REFUSALS_ONLY'
  assert json.loads((evidence/'malt-runtime-setup-control-1/COMPLETED.json').read_bytes())['status']=='PASS_PUBLIC_NETWORK_OFFLINE_MISSING_CORRUPT_EXISTING_MALT_RMA_INSTALLATION_CONTROLS_ONLY'
  import xml.etree.ElementTree as ET
  junit=ET.parse(evidence/'malt-rma-project-check-2/TEST-org.jlinalg.cli.MaltReassignmentCliTest.xml').getroot();assert int(junit.attrib['tests'])==4 and all(int(junit.attrib[k])==0 for k in ['failures','errors','skipped'])
  missing=[];versions={};java21=a.java21_bin.resolve();java25=a.java25_bin.resolve()
  def version(label,command,expected):
   try:
    r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=60,env=env);text=(r.stdout+r.stderr).decode(errors='replace');match=re.search(r'(?:openjdk|java|javac) (?:version )?"?(\d+)[.\-"\s]',text);major=int(match.group(1)) if match else None;versions[label]=dict(command=command,exit_code=r.returncode,major=major,expected=expected)
    if r.returncode!=0 or major!=expected:missing.append(label+' requires major'+str(expected)+'; observed '+str(major))
   except (OSError,subprocess.TimeoutExpired) as error:missing.append(label+': '+type(error).__name__)
  for major,java in [(21,java21),(25,java25)]:
   for name in ['java.exe','javac.exe']:
    if not (java/name).is_file():missing.append(str(java/name))
    else:version(str(major)+'-'+name,[str(java/name),'-version' if name=='java.exe' else '--version'],major)
  if not shutil.which('wsl.exe'):missing.append('wsl.exe with '+a.linux_distro+' /usr/bin/java21')
  else:version('linux21',['wsl.exe','-d',a.linux_distro,'--exec','/usr/bin/java','-version'],21)
  save(out/'VERSIONS.json',versions);state['versions']=versions
  provider=a.runtime.resolve() if a.runtime else out/'runtime'
  if a.install_runtime:
   code=run('install-public-runtime',[sys.executable,str(ROOT/'tools/literature/setup_malt_rma_runtime.py'),'--out',str(provider)],expected=None)
   if code==3:state.update(status='SKIP3_NATIVE_MALT_RMA_REPLAY_MISSING_PREREQUISITES',cause='Public runtime installation unavailable; dependent replay skipped. See install-public-runtime diagnostics.');return finish(3)
   assert code==0,'Runtime installer failed its integrity or setup checks'
  if not a.runtime and not a.install_runtime:missing.append('--runtime or permitted --install-runtime')
  for name,expected in [('MALT.jar',PROGRAM),('data.jar',DATA)]:
   if not (provider/name).is_file():missing.append(str(provider/name))
   else:assert pin(provider/name)==expected,'Runtime integrity mismatch: '+name
  if missing:state.update(status='SKIP3_NATIVE_MALT_RMA_REPLAY_MISSING_PREREQUISITES',missing=missing,cause='Dependent replay was skipped: '+', '.join(missing));return finish(3)
  fixture=out/'fixture'
  with zipfile.ZipFile(ROOT/'src/test/resources/malt-reassignment-controls.zip') as z:
   files={name:dict(bytes=len(z.read(name)),sha256=hashlib.sha256(z.read(name)).hexdigest()) for name in z.namelist()};fixture_raw=(ROOT/'src/test/resources/malt-reassignment-controls.zip').read_bytes()
  extract(fixture_raw,fixture,files)
  for name in ['assignments.tsv','thresholds.tsv']:assert (fixture/'expected'/name).read_bytes()==(original/'reference'/name).read_bytes()
  assert len((fixture/'cases.tsv').read_text().splitlines())==33
  paths=evidence/'malt-rma-original-audit-3/reference/taxonomic-paths.tsv';disabled=out/'disabled.txt';disabled.write_bytes(b'9606\n');results=[];expected={};thresholds={}
  for row in csv.DictReader((fixture/'expected/assignments.tsv').read_text().splitlines(),delimiter='\t'):expected.setdefault(row['case_id'],{})[row['uid']]=int(row['final_taxon'])
  for row in csv.DictReader((fixture/'expected/thresholds.tsv').read_text().splitlines(),delimiter='\t'):thresholds[row['case_id']]=int(row['selected_minimum'])
  names=['src/main/java/org/jlinalg/metagenomics/'+n+'.java' for n in ['MaltTaxonomy','MaltAssignment','MaltSupport','MaltRmaReader','MaltSupportThreshold','MaltReassignment']]+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['MaltReassignCli','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson']]+['tools/literature/java/MaltRmaAdapterProbe.java','tools/literature/java/MaltReassignmentProbe.java']
  for compiler,java in [('21',java21),('25',java25)]:
   classes=out/('classes'+compiler);classes.mkdir();run('javac'+compiler,[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',str(classes),*[str(ROOT/n) for n in names]])
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;convert=linux if runtime=='linux21' else str
    def command(main,args):
     if runtime=='linux21':return ['wsl.exe','-d',a.linux_distro,'--exec','/usr/bin/timeout','--signal=TERM','--kill-after=5s','660s','/usr/bin/java','-Xmx2g','-Djava.awt.headless=true','-cp',linux(classes),main,*args]
     return [str((java21 if runtime=='windows21' else java25)/'java.exe'),'-Xmx2g','-Djava.awt.headless=true','-cp',str(classes),main,*args]
    target=out/(label+'-reader');run(label+'-reader',command('MaltRmaAdapterProbe',[convert(p) for p in [provider,fixture,paths,target]]))
    for name in files:
     if name.startswith('expected-raw/'):assert (target/Path(name).name).read_bytes()==(fixture/name).read_bytes(),(label,name)
    assert (target/'probe-counts.tsv').read_bytes()==b'reads\t172\nmatches\t175\nrefusals\t12\n'
    target=out/(label+'-serial');run(label+'-serial',command('MaltReassignmentProbe',[convert(p) for p in [provider,fixture,fixture/'cases.tsv',target]]))
    for name in ['assignments.tsv','thresholds.tsv']:assert (target/name).read_bytes()==(fixture/'expected'/name).read_bytes(),(label,name)
    cli=[('defaultA','JK2782','4',[]),('defaultB','JK2802','20',[]),('percent50','JK2782','7',['--minimum-support-percent','50']),('integer-override','JK2782','2',['--minimum-support','50','--minimum-support-percent','50']),('disabled','JK2802','30',['--minimum-support','10','--disabled-taxonomy-ids',convert(disabled)])]
    for name,library,case,extra in cli:
     target=out/(label+'-'+name);input=fixture/('data/'+library+'/'+library+'.unmapped.rma6');run(label+'-'+name,command('org.jlinalg.cli.MaltReassignCli',['--rma',convert(input),'--runtime',convert(provider),'--out',convert(target),*extra]));doc=json.loads((target/'reassignment.json').read_bytes());complete=json.loads((target/'COMPONENT_COMPLETED.json').read_bytes());assert doc['selected_minimum_support']==thresholds[case] and doc['input']==pin(input) and doc['getter_profile']==dict(minimum_score=0.0,maximum_expected=10.0,read_sequence=False);assert doc['new_tool_credit']==0 and doc['biological_result_admitted'] is False and doc['full_malt_or_eager_adapter_admitted'] is False
     assert {row['uid']:int(row['final_taxon']) for row in csv.DictReader((target/'assignments.tsv').read_text().splitlines(),delimiter='\t')}==expected[case]
     assert complete['assignments']==pin(target/'assignments.tsv') and complete['report']==pin(target/'reassignment.json') and 'Required action:' in (target/'WARNINGS.md').read_text()
     if name=='integer-override':assert doc['parameters']['support_percent']==0 and 'MINIMUM_SUPPORT_OVERRIDE' in (target/'WARNINGS.md').read_text()
    for name,code,extra in [('missing-runtime',3,[]),('capacity',2,['--maximum-reads','1']),('nonfinite',2,['--coverage','NaN'])]:
     target=out/(label+'-'+name);runtime_path=out/'missing-runtime' if code==3 else provider;run(label+'-'+name,command('org.jlinalg.cli.MaltReassignCli',['--rma',convert(fixture/'data/JK2782/JK2782.unmapped.rma6'),'--runtime',convert(runtime_path),'--out',convert(target),*extra]),code);assert json.loads((target/'FAILURE.json').read_bytes())['status']==('SKIP3' if code==3 else 'FAIL2') and 'Required action:' in (target/'WARNINGS_FAILURE.md').read_text() and not (target/'COMPONENT_COMPLETED.json').exists()
    target=out/(label+'-defaultA');before={p.name:pin(p) for p in target.iterdir()};run(label+'-existing',command('org.jlinalg.cli.MaltReassignCli',['--rma',convert(fixture/'data/JK2782/JK2782.unmapped.rma6'),'--runtime',convert(provider),'--out',convert(target)]),2);assert before=={p.name:pin(p) for p in target.iterdir()}
    results.append(dict(compiler=compiler,runtime=runtime,raw_reads=172,raw_matches=175,reader_refusals=12,serial_profiles=32,serial_read_assignments=2752,cli_profiles=5,cli_refusals=4));print('Saved complete RMA and serial controls matched',label,flush=True)
  assert pin(archive)==proof['evidence_archive'] and all(pin(ROOT/name)==value for name,value in guards.items())
  assert pin(provider/'MALT.jar')==PROGRAM and pin(provider/'data.jar')==DATA and all(pin(fixture/name)==value for name,value in files.items())
  state.update(status='PASS_SAVED_NATIVE_MALT_RMA_SERIAL_REASSIGNMENT_REPLAY_ONLY',results=results,evidence_files=len(manifest['files']),original_runtime_rerun=False,junit_receipt_checked_not_rerun=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc());return finish(2)
 return finish(0)
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source or reference checks')
 raise SystemExit(main())
