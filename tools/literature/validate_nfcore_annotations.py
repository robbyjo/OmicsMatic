# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete original HOMER/R annotation-QC controls and source-specific deliberate discrepancies."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

REFERENCE=ROOT/'src/test/resources/org/jlinalg/cli/nfcore-annotation411/reference.zip'
def change(path,row,column,value):
 lines=path.read_text(encoding='utf8').splitlines();fields=lines[row].split('\t');fields[column]=value;lines[row]='\t'.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
def mutate(root,name):
 path=root/'SPT5_T0_R2_peaks.annotatePeaks.txt'
 if name=='wrong_gene_from_overwritten_TSS':
  lines=path.read_text().splitlines();i=next(i for i,s in enumerate(lines) if s.startswith('SPT5_T0_R2_peak_1\t'));change(path,i,9,'5');change(path,i,10,'GeneA');change(path,i,11,'GeneA')
 elif name=='wrong_TSS_distance':change(path,-1,9,'123456')
 elif name=='wrong_gene_metadata':change(path,-1,11,'SILENT_ALIAS')
 elif name=='wrong_peak_ID':change(path,-1,0,'INFERRED_PEAK')
 elif name=='wrong_peak_coordinate':change(path,-1,2,'99')
 elif name=='wrong_peak_score':change(path,-1,5,'99999')
 elif name=='missing_late_peak':path.write_text('\n'.join(path.read_text().splitlines()[:-1])+'\n',encoding='utf8')
 elif name=='duplicate_peak':
  lines=path.read_text().splitlines();lines[-1]=lines[1];path.write_text('\n'.join(lines)+'\n',encoding='utf8')
 elif name=='missing_TSS_imputation':
  path=root/'SPT5_T15_R2_peaks.annotatePeaks.txt';change(path,-1,9,'0');change(path,-1,10,'GeneA')
 elif name=='absent_category_to_zero':
  path=root/'macs_annotatePeaks.summary.txt';lines=path.read_text().splitlines();row=next(i for i,s in enumerate(lines[1:],1) if 'NA' in s.split('\t'));column=lines[row].split('\t').index('NA');change(path,row,column,'0')
 elif name=='wrong_feature_count':change(root/'macs_annotatePeaks.summary.txt',1,1,'999')
 elif name=='wrong_Type7_quantile':change(root/'macs_peak.summary.txt',1,1,'1.23456789')
 elif name=='wrong_BED_width_mean':
  path=root/'macs_peak.summary.txt';lines=path.read_text().splitlines();i=next(i for i,s in enumerate(lines) if '\tlength\t' in s);change(path,i,3,'12345')
 elif name=='missing_summary_measure':
  path=root/'macs_peak.summary.txt';path.write_text('\n'.join(path.read_text().splitlines()[:-1])+'\n',encoding='utf8')
 elif name=='wrong_consensus_score':change(root/'consensus.annotatePeaks.txt',1,5,'4')
 elif name=='wrong_combined_annotation':change(root/'consensus.boolean.annotatePeaks.txt',-1,-1,'MUTATED')
 elif name=='wrong_multiqc_header':
  path=root/'macs_annotatePeaks.summary_mqc.tsv';path.write_text(path.read_text().replace('#id:', '#wrong-id:',1),encoding='utf8')
 else:raise AssertionError(name)

NEGATIVES=['wrong_gene_from_overwritten_TSS','wrong_TSS_distance','wrong_gene_metadata','wrong_peak_ID','wrong_peak_coordinate','wrong_peak_score','missing_late_peak','duplicate_peak','missing_TSS_imputation','absent_category_to_zero','wrong_feature_count','wrong_Type7_quantile','wrong_BED_width_mean','missing_summary_measure','wrong_consensus_score','wrong_combined_annotation','wrong_multiqc_header']

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_ANNOTATION_QC_CONTROLS',new_tool_credit=0,scientific_result_admitted=False);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
 try:
  fixtures=out/'fixtures';fixtures.mkdir();before=pin(REFERENCE)
  with zipfile.ZipFile(REFERENCE) as z:
   assert len(z.namelist())==len(set(z.namelist()))
   for item in z.infolist():
    target=(fixtures/item.filename).resolve();assert target.is_relative_to(fixtures) and not item.is_dir() and item.file_size<=100000000;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(item))
  source=json.loads((fixtures/'SOURCE.json').read_bytes());assert source['complete_captured_annotation_rows']==4829
  for name,v in source['files'].items():assert pin(fixtures/name)==v
  originals={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.rglob('*') if p.is_file()};jars={'21':a.jar21.resolve(),'25':a.jar25.resolve()};artifact_pins={c:pin(p) for c,p in jars.items()};helper=ROOT/'tools/literature/java/NfcoreAnnotationControl.java';helper_pin=pin(helper);checks=[];refusals=[]
  for compiler,jar in jars.items():
   java=JAVA if compiler=='21' else JAVA25;classes=out/('helper'+compiler);classes.mkdir();cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    for case,rows in [('captured',4829),('sparse',9),('unicode_versioned',9)]:
     label=compiler+'-'+runtime+'-'+case;report=out/(label+'.audit.json')
     if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.NfcoreAnnotationControl',linux(fixtures/case),linux(report)]
     else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.NfcoreAnnotationControl',str(fixtures/case),str(report)]
     with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
     save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,(out/(label+'.stderr')).read_text());data=json.loads(report.read_bytes());assert data['complete_TSS_geometry_and_gene_metadata_rows']==rows and data['gene_ID_mode_merges_transcripts'] is False
     if case!='captured':assert data['active_gene_TSS_rows']==6 and data['overwritten_transcript_TSS_rows_retained']==1 and data['source_dot_strand_exons_assigned_plus']==1
     checks.append(dict(case=case,compiler=compiler,runtime=runtime,passed=True,audit=pin(report)))
  for name in NEGATIVES:
   case='captured' if name in ['wrong_consensus_score','wrong_combined_annotation','wrong_multiqc_header'] else 'sparse';root=out/'negative'/name;shutil.copytree(fixtures/case,root);mutate(root,name);report=out/(name+'.audit.json');cmd=[str(JAVA/'java.exe'),'-Xmx2048m','-cp',str(out/'helper21')+';'+str(jars['21']),'org.jlinalg.cli.NfcoreAnnotationControl',str(root),str(report)]
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
   save(out/(name+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==2 and not report.exists() and 'FAIL2' in (out/(name+'.stderr')).read_text(),name;refusals.append(dict(case=name,passed=True,exit_code=2,stderr=pin(out/(name+'.stderr'))))
  assert pin(REFERENCE)==before and pin(helper)==helper_pin and all(pin(jars[c])==v for c,v in artifact_pins.items()) and all(pin(fixtures/n)==v for n,v in originals.items())
  state.update(status='PASS_COMPLETE_ORIGINAL_ANNOTATION_AND_QC_CONTROLS_ONLY',checks=checks,refusals=refusals,reference=before,artifacts=artifact_pins,helper=helper_pin,source_last_transcript_row_per_contig_gene_preserved=True,source_absent_category_NA_preserved=True,source_gene_version_and_Unicode_sort_preserved=True,complete_reference_populations=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source-specific complete-population checks')
 raise SystemExit(main())
