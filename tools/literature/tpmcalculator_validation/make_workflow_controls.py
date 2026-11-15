#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual original option/identifier tables and GNU numeric-format controls."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,zipfile
p=argparse.ArgumentParser(description=__doc__)
for name in ['build','core-controls','out','archive']:p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();out=a.out.resolve();out.mkdir(exist_ok=False);prefix=a.build.resolve()/'runtime/.pixi/envs/default'
root=Path(__file__).resolve().parents[3];env=dict(os.environ,LD_LIBRARY_PATH=str(prefix/'lib'),PATH=str(prefix/'bin')+':/usr/bin:/bin')
def pin(path):
 b=path.read_bytes();return dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
def run(folder,name,command):
 r=subprocess.run(list(map(str,command)),cwd=folder,env=env,capture_output=True)
 (folder/(name+'.stdout')).write_bytes(r.stdout);(folder/(name+'.stderr')).write_bytes(r.stderr)
 write(folder/(name+'.json'),dict(command=list(map(str,command)),exit_code=r.returncode))
 if r.returncode:raise RuntimeError((name,r.returncode,r.stderr))
 return r
shutil.copyfile(Path(__file__),out/Path(__file__).name)
number=Path(__file__).with_name('OriginalNumber.cpp');shutil.copyfile(number,out/number.name)
run(out,'compile-number',[prefix/'bin/x86_64-conda_cos6-linux-gnu-g++','-std=c++14','-O2',number,'-o',out/'OriginalNumber'])
run(out,'original-number',[out/'OriginalNumber'])
cases=[]
for name,base,extended,all_features,custom,identifiers in [
 ('default-tables','empty',False,False,False,False),('default-flags','flags',False,False,False,False),
 ('extended-filtered','sample-state',True,False,False,False),('all-without-transcripts','sample-state',False,True,False,False),
 ('custom-keys','alternative-isoforms',True,True,True,False),('raw-control-identifiers','flags',True,True,False,True),
 ('custom-filtered','alternative-isoforms',False,False,True,False)]:
 folder=out/name;folder.mkdir();(folder/'bams').mkdir();(folder/'original-main').mkdir()
 old=a.core_controls/base;gtf=(old/'annotation.gtf').read_text()
 if custom:gtf=gtf.replace('gene_id ','selected_gene ').replace('transcript_id ','selected_transcript ')
 if identifiers:gtf=gtf.replace('"g"','"gene α\rwith-control"').replace('"t"','"transcript 漢字"')
 (folder/'annotation.gtf').write_text(gtf,encoding='utf-8',newline='')
 for b in sorted((old/'bams').glob('*.bam')):shutil.copyfile(b,folder/'bams'/b.name)
 metadata=json.loads((old/'CASE.json').read_bytes());gene_key='selected_gene' if custom else 'gene_id';tx_key='selected_transcript' if custom else 'transcript_id'
 command=[a.build/'build-original/TPMCalculator/bin/TPMCalculator','-g',folder/'annotation.gtf','-d',folder/'bams','-k',gene_key,'-t',tx_key,
  '-c',metadata['cutoff'],'-q',metadata['mapq'],'-o',metadata['overlap']]+(['-p'] if metadata['proper'] else [])+(['-e'] if extended else [])+(['-a'] if all_features else [])
 r=run(folder/'original-main','process',command);order=re.findall(r'Processing sample: ([^\s]+)',r.stderr.decode())
 value=dict(name=name,cutoff=metadata['cutoff'],mapq=metadata['mapq'],overlap=metadata['overlap'],proper=metadata['proper'],sample_order=order,
  extended=extended,all=all_features,gene_key=gene_key,transcript_key=tx_key,table_files=sorted(p.name for p in (folder/'original-main').iterdir() if p.suffix in ['.out','.ent','.uni']))
 write(folder/'CASE.json',value);cases.append(value);print('ORIGINAL_WORKFLOW_CASE_COMPLETE',name,flush=True)
write(out/'CASES.json',cases)
write(out/'SOURCE.json',dict(original_build_completion=pin(a.build/'COMPLETED.json'),producer=pin(Path(__file__)),number_source=pin(number),number_binary=pin(out/'OriginalNumber'),
 numeric_records=len((out/'original-number.stdout').read_bytes().splitlines()),original_algorithms_patched=False))
a.archive.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for f in sorted(out.rglob('*')):
  if f.is_file() and f.name!='OriginalNumber':z.write(f,f.relative_to(out).as_posix())
print(json.dumps(dict(status='PASS_REAL_ORIGINAL_WORKFLOW_OPTION_IDENTIFIER_AND_FORMATTING_REFERENCES_NOT_NATIVE_QUALIFICATION',archive=pin(a.archive)),indent=2))
