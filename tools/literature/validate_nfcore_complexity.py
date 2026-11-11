# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original Picard references, full captured marked BAMs and deliberate complete-table discrepancies."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def run(out,label,cmd,expected=0,timeout=1200):
 with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=timeout)
 save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==expected,(label,r.returncode,(out/(label+'.stderr')).read_text(encoding='utf8')[-2000:]);return dict(case=label,passed=True,exit_code=r.returncode,stdout=pin(out/(label+'.stdout')),stderr=pin(out/(label+'.stderr')))

def extract(archive,target):
 target.mkdir()
 with zipfile.ZipFile(archive) as z:
  assert len(z.namelist())==len(set(z.namelist())) and len(z.namelist())<1000
  for item in z.infolist():
   path=(target/item.filename).resolve();assert path.is_relative_to(target) and not item.is_dir() and item.file_size<100000000;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(item))

def mutate(root,name):
 metrics=root/'metrics.txt';lines=metrics.read_text().splitlines();start=next(i for i,s in enumerate(lines) if s.startswith('LIBRARY\t'))
 def metric(column,value):
  f=lines[start+1].split('\t');f[column]=value;lines[start+1]='\t'.join(f);metrics.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
 if name=='counter':metric(1,'777')
 elif name=='paired_duplicates':metric(6,'0')
 elif name=='fraction':metric(8,'0.9')
 elif name=='missing_estimate':metric(9,'')
 elif name=='wrong_estimate':metric(9,'1')
 elif name=='optical_count':metric(7,str(int(lines[start+1].split('\t')[6])+1))
 elif name=='library_alias':metric(0,'INFERRED_ALIAS')
 elif name in ['late_ROI','missing_late_ROI','set_histogram']:
  h=next(i for i,s in enumerate(lines) if s.startswith('BIN\t'));columns=lines[h].split('\t')
  if name=='missing_late_ROI':lines=[s for i,s in enumerate(lines) if not(i>h and s.startswith('100.0\t'))]
  else:
   i=next(i for i in range(h+1,len(lines)) if lines[i].startswith('100.0\t' if name=='late_ROI' else '1.0\t'));f=lines[i].split('\t');j=columns.index('CoverageMult' if name=='late_ROI' else 'all_sets');f[j]='0' if name=='late_ROI' else str(int(f[j])+1);lines[i]='\t'.join(f)
  metrics.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
 elif name in ['truncated_curve','late_grid','late_NaN','late_CI']:
  path=root/'curve.txt';curve=path.read_text().splitlines()
  if name=='truncated_curve':curve=curve[:-1]
  else:
   f=curve[-1].split('\t');f[0 if name=='late_grid' else 1 if name=='late_NaN' else 2]='1' if name=='late_grid' else 'NaN' if name=='late_NaN' else str(float(f[3])*2);curve[-1]='\t'.join(f)
  path.write_text('\n'.join(curve)+'\n',encoding='utf8',newline='\n')
 else:raise AssertionError(name)

NEGATIVES=['counter','paired_duplicates','fraction','missing_estimate','wrong_estimate','optical_count','library_alias','late_ROI','missing_late_ROI','set_histogram','truncated_curve','late_grid','late_NaN','late_CI']

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build21',type=Path,required=True);p.add_argument('--build25',type=Path,required=True);p.add_argument('--complete-attempt',type=Path,required=True);p.add_argument('--complete-library-fixture',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_NATIVE_COMPLEXITY_CONTROLS',new_tool_credit=0,scientific_result_admitted=False)
 try:
  builds={'21':a.build21.resolve(),'25':a.build25.resolve()};artifacts={c:{n:pin(path/n) for n in ['picard-complexity-library.jar','nfcore-complexity-development.jar']} for c,path in builds.items()}
  reference=ROOT/'src/test/resources/org/jlinalg/sequence/picard2231/reference.zip';synthetic=ROOT/'src/test/resources/org/jlinalg/sequence/picard2231/markdup-controls-v2.zip';refs=out/'reference';extract(reference,refs);small=out/'small-fixtures';extract(synthetic,small)
  metadata=json.loads((refs/'SOURCE.json').read_bytes());assert metadata['complete_cases']==20
  for name,v in metadata['files'].items():assert pin(refs/name)==v
  fixture=a.complete_library_fixture.resolve();before={p.relative_to(fixture).as_posix():pin(p) for p in fixture.rglob('*') if p.is_file()};positive=[];complete=[];negative=[];first_report=None
  for compiler,folder in builds.items():
   java=JAVA if compiler=='21' else JAVA25;classes=out/('helpers'+compiler);classes.mkdir();full=folder/'nfcore-complexity-development.jar';library=folder/'picard-complexity-library.jar';helpers=[ROOT/'tools/literature/java/PicardComplexityControl.java',ROOT/'tools/literature/java/NfcoreComplexityControl.java'];cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(full),'-d',str(classes),*map(str,helpers)];r=subprocess.run(cmd,capture_output=True,timeout=60);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    def command(jar,main,*args):
     if runtime=='linux21':return ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-cp',linux(classes)+':'+linux(jar),main,*[linux(Path(v)) for v in args]]
     return [str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-cp',str(classes)+';'+str(jar),main,*map(str,args)]
    label=compiler+'-'+runtime;positive.append(run(out,label+'-numerics',command(library,'PicardComplexityControl',refs/'reference.tsv')))
    report=out/(label+'-optical.audit.json');positive.append(run(out,label+'-optical',command(full,'org.jlinalg.cli.NfcoreComplexityControl',small/'optical_and_flags',report)));data=json.loads(report.read_bytes());assert data['complete_marked_BAM_records']==16 and data['metric_libraries'][0]['source_optical_duplicates']==1 and data['metric_libraries'][0]['unmapped']==2 and data['metric_libraries'][0]['secondary_or_supplementary']==2 and data['complete_ROI_bins_checked']==100
   for runtime in ['windows'+compiler,'linux21']:
    label=compiler+'-'+runtime+'-complete';dest=out/(label+'-audit')
    if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-jar',linux(full),'--engine-result',linux(a.complete_attempt.resolve()),'--out',linux(dest)]
    else:cmd=[str(java/'java.exe'),'-Xmx2048m','-jar',str(full),'--engine-result',str(a.complete_attempt.resolve()),'--out',str(dest)]
    result=run(out,label,cmd);data=json.loads((dest/'audit.json').read_bytes());assert len(data['complete_libraries'])==6 and sum(r['complete_marked_BAM_records'] for r in data['complete_libraries'])==1389840 and sum(r['complete_ROI_bins_checked'] for r in data['complete_libraries'])==600 and sum(r['original_Preseq']['complete_curve_rows'] for r in data['complete_libraries'])==60000
    if first_report is None:first_report=data
    else:assert data==first_report
    complete.append(result)
  invalid_report=out/'invalid-secondary-unmapped.audit.json';invalid_cmd=[str(JAVA/'java.exe'),'-Xmx2048m','-cp',str(out/'helpers21')+';'+str(builds['21']/'nfcore-complexity-development.jar'),'org.jlinalg.cli.NfcoreComplexityControl',str(small/'original_invalid_secondary_unmapped'),str(invalid_report)];negative.append(run(out,'invalid-secondary-unmapped',invalid_cmd,2));assert not invalid_report.exists() and 'INVALID_FLAG_NOT_PRIM_ALIGNMENT' in (out/'invalid-secondary-unmapped.stderr').read_text()
  for name in NEGATIVES:
   root=out/'negative'/name;shutil.copytree(fixture,root);mutate(root,name);report=out/(name+'.audit.json');cmd=[str(JAVA/'java.exe'),'-Xmx2048m','-cp',str(out/'helpers21')+';'+str(builds['21']/'nfcore-complexity-development.jar'),'org.jlinalg.cli.NfcoreComplexityControl',str(root),str(report)];negative.append(run(out,name,cmd,2));assert not report.exists() and 'FAIL2' in (out/(name+'.stderr')).read_text()
  assert all(pin(fixture/n)==v for n,v in before.items()) and all(pin(builds[c]/n)==v for c,rows in artifacts.items() for n,v in rows.items())
  state.update(status='PASS_COMPLETE_PICARD2231_COMPLEXITY_COMPONENT_CONTROLS_ONLY',native_and_optical_replays=positive,complete_captured_replays=complete,negative_controls=negative,artifacts=artifacts,reference=pin(reference),synthetic_reference=pin(synthetic),complete_library_fixture=before,complete_marked_BAM_records=1389840,original_Preseq_rows=60000,original_ROI_bins=600,full_reports_identical=True,full_workflow_or_biological_admission=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original reference or population checks')
 raise SystemExit(main())
