# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete source DESeq2 postfit populations and deliberate inconsistencies against packaged Java."""
import argparse,json,os,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

REFERENCE=ROOT/'src/test/resources/org/jlinalg/cli/nfcore-deseq126/reference.zip'
CASES=['captured','three_groups','outlier_replacement']

def mutate(path,row,column,value):
 lines=path.read_text(encoding='utf8').splitlines();fields=lines[row].split('\t');fields[column]=value;lines[row]='\t'.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')

def derivative(name,root):
 worker=root/'worker';results=next(p for p in (root/'published').rglob('*.deseq2.results.txt') if not '.FDR' in p.name)
 if name=='missing_marker':(worker/'WORKER_COMPLETED.txt').unlink()
 elif name=='wrong_design':
  p=worker/'model-metadata.tsv';p.write_text(p.read_text().replace('design\t~condition','design\t~1'),encoding='utf8')
 elif name=='late_raw_count':mutate(worker/'raw-counts.tsv',-1,1,'314159')
 elif name=='missing_late_gene':
  p=worker/'normalized-counts.tsv';p.write_text('\n'.join(p.read_text().splitlines()[:-1])+'\n',encoding='utf8')
 elif name=='wrong_sample_order':
  p=worker/'samples.tsv';lines=p.read_text().splitlines();lines[1],lines[2]=lines[2],lines[1];p.write_text('\n'.join(lines)+'\n',encoding='utf8')
 elif name=='wrong_matrix':mutate(worker/'model-matrix.tsv',-1,2,'0')
 elif name=='contrast_direction':mutate(worker/'comparisons.tsv',1,1,'SPT5_T15')
 elif name=='alpha_005':mutate(worker/'comparison-000001.selection.tsv',1,0,'0.05')
 elif name=='late_metric':mutate(results,-1,7,'91')
 elif name=='native_padj':
  mutate(worker/'comparison-000001.results.tsv',-1,6,'0.000001');mutate(results,-1,11,'0.000001')
 elif name=='late_filter_family':mutate(worker/'comparison-000001.filter.tsv',-1,2,'1000000')
 elif name=='negative_dispersion':mutate(worker/'gene-diagnostics.tsv',-1,4,'-1')
 elif name=='NA_nonzero_replacement':mutate(worker/'replacement.tsv',-1,1,'NA')
 elif name=='fractional_count':mutate(root/'featureCounts.txt',-1,6,'1.5')
 elif name=='integer_overflow':mutate(root/'featureCounts.txt',-1,6,'2147483648')
 elif name=='changed_source_SAF':mutate(root/'featureCounts.txt',-1,2,'12345')
 elif name=='missing_FDR_row':
  p=next((root/'published').rglob('*.FDR0.01.results.txt'));p.write_text('\n'.join(p.read_text().splitlines()[:-1])+'\n',encoding='utf8')
 elif name=='wrong_BED_start':mutate(next((root/'published').rglob('*.FDR0.05.results.bed')),-1,1,'12345')
 elif name=='unrecorded_effective_count':mutate(worker/'effective-fit-counts.tsv',-1,1,'55')
 else:raise AssertionError(name)

NEGATIVES=['missing_marker','wrong_design','late_raw_count','missing_late_gene','wrong_sample_order','wrong_matrix','contrast_direction','alpha_005','late_metric','native_padj','late_filter_family','negative_dispersion','NA_nonzero_replacement','fractional_count','integer_overflow','changed_source_SAF','missing_FDR_row','wrong_BED_start','unrecorded_effective_count']

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args()
 out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_POSTFIT_CONTROLS',nfcore_tool_credit=0,scientific_result_admitted=False)
 try:
  reference_before=pin(REFERENCE);jars={'21':a.jar21.resolve(),'25':a.jar25.resolve()};jar_pins={k:pin(v) for k,v in jars.items()};fixtures=out/'fixtures';fixtures.mkdir()
  with zipfile.ZipFile(REFERENCE) as z:
   assert len(z.namelist())==len(set(z.namelist())) and len(z.namelist())<1000
   for item in z.infolist():
    target=(fixtures/item.filename).resolve();assert target.is_relative_to(fixtures) and not item.is_dir() and item.file_size<=100000000
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(item))
  source=json.loads((fixtures/'SOURCE.json').read_bytes());assert source['scientific_result_admitted'] is False
  fixture_pins={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.rglob('*') if p.is_file()}
  for name,v in source['files'].items():assert pin(fixtures/name)==v
  helper=ROOT/'tools/literature/java/NfcoreDeseq126Control.java';before=pin(helper);positives=[];negatives=[]
  for compiler,jar in jars.items():
   classes=out/('helper'+compiler);classes.mkdir();java=JAVA if compiler=='21' else JAVA25
   cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)]
   r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('helper'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    for case in CASES:
     label=compiler+'-'+runtime+'-'+case;report=out/(label+'.audit.json')
     if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1024m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.NfcoreDeseq126Control',linux(fixtures/case),linux(report)]
     else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx1024m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.NfcoreDeseq126Control',str(fixtures/case),str(report)]
     with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
     save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,(out/(label+'.stderr')).read_text())
     actual=json.loads(report.read_bytes());assert actual['scientific_result_admitted'] is False and actual['nfcore_tool_credit']==0
     if case=='outlier_replacement':assert actual['replaced_gene_ids']==['Interval_2'] and actual['original_NA_replacement_flag_gene_ids']==['Interval_1']
     positives.append(dict(compiler=compiler,runtime=runtime,case=case,passed=True,audit=pin(report)))
  for name in NEGATIVES:
   root=out/'negative'/name;shutil.copytree(fixtures/'captured',root);derivative(name,root);report=out/(name+'.audit.json')
   cmd=[str(JAVA/'java.exe'),'-Xmx1024m','-cp',str(out/'helper21')+';'+str(jars['21']),'org.jlinalg.cli.NfcoreDeseq126Control',str(root),str(report)]
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
   save(out/(name+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==2 and not report.exists() and 'FAIL2' in (out/(name+'.stderr')).read_text(),name
   negatives.append(dict(case=name,passed=True,exit_code=2,stderr=pin(out/(name+'.stderr'))))
  assert all(pin(fixtures/n)==v for n,v in fixture_pins.items()) and pin(helper)==before and pin(REFERENCE)==reference_before and all(pin(jars[k])==v for k,v in jar_pins.items())
  state.update(status='PASS_COMPLETE_ORIGINAL_POSTFIT_NATIVE_COMPONENT_CONTROLS_ONLY',positive_controls=positives,negative_controls=negatives,reference=reference_before,artifacts=jar_pins,helper=before,source_populations=source['populations'],original_fixtures_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original-population assertions')
 raise SystemExit(main())
