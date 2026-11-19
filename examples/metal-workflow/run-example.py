#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run synthetic paired-DNA/post-GC or non-allelic gene examples through captured METAL."""
import argparse,json,platform,shutil,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path,help='Private setup RUNTIME.tsv');p.add_argument('--jar',required=True,type=Path);p.add_argument('--out',required=True,type=Path,help='NEW workspace build directory');p.add_argument('--java',default='java');p.add_argument('--mode',choices=['dna','gene'],default='dna');a=p.parse_args();out=None
 try:
  if platform.system()!='Linux' or platform.machine() not in ['x86_64','amd64']:print('SKIP3: run original examples inside permitted Linux x86_64/WSL; source-bundle verification remains available on Windows.',file=sys.stderr);return 3
  java=shutil.which(a.java)
  if java is None:print('SKIP3: Java17+ is unavailable; supply --java with a permitted Java runtime or leave dependent analysis skipped.',file=sys.stderr);return 3
  if not a.jar.is_file() or not a.runtime.is_file():print('SKIP3: supply the complete current source-build JAR and private runtime setup RUNTIME.tsv; no analysis was executed.',file=sys.stderr);return 3
  target=a.out.absolute()
  if not target.resolve().is_relative_to(ROOT/'build'):raise ValueError('Choose NEW workspace build output')
  target.mkdir(parents=True,exist_ok=False);out=target
  lines=a.runtime.read_text(encoding='utf-8').splitlines()
  if lines[0]!='key\tvalue':raise ValueError('Runtime metadata needs the literal key/value TSV header')
  runtime=dict(line.split('\t',1) for line in lines[1:])
  if runtime['release']!='2020-05-05':raise ValueError('Use the explicitly supported runtime edition')
  header='MARKER\tALLELE1\tALLELE2\tEFFECT\tPVALUE\tN\tSTDERR\tFREQ\tSTRAND\n';supplied=out/'supplied';supplied.mkdir()
  if a.mode=='dna':
   rows=['M1\tA\tC\t.2\t-\t100\t.1\t.2\t+\nM2\tC\tG\t-.1\t-\t100\t.2\t.3\t+\n','M1\tC\tA\t-.3\t-\t200\t.2\t.6\t+\nM3\tG\tT\t.1\t-\t200\t.2\t.4\t+\n']
  else:rows=['ENSG1\t-\t-\t+\t.05\t100\t-\t-\t+\nENSG2\t-\t-\t-\t.01\t100\t-\t-\t+\n','ENSG1\t-\t-\t+\t.1\t200\t-\t-\t+\nENSG3\t-\t-\t-\t.2\t200\t-\t-\t+\n']
  for i,row in enumerate(rows):(supplied/f'study{i}.tsv').write_bytes((header+row).encode())
  (supplied/'STUDIES.tsv').write_bytes(b'study\tfile\tgenomic_control\nfirst\tstudy0.tsv\tOFF\nsecond\tstudy1.tsv\tOFF\n')
  c=dict(executable=runtime['executable'],executable_sha256=runtime['executable_sha256'],notice=runtime['notice'],release=runtime['release'],execution='allow',studies='STUDIES.tsv',scheme='STDERR' if a.mode=='dna' else 'SAMPLESIZE',allele_mode='paired' if a.mode=='dna' else 'none',strand='ON' if a.mode=='dna' else 'OFF',frequency='ON' if a.mode=='dna' else 'OFF',overlap='OFF',z_cutoff='-',heterogeneity='ON',output_probability='log10',post_genomic_control='1.2' if a.mode=='dna' else 'OFF',warning_policy='forbid' if a.mode=='dna' else 'allow-recorded',hypothesis='Synthetic independently generated cohorts with declared common effect orientation and effective N; not biological data',max_studies='10',max_rows='10000',max_metadata_bytes='10000000',max_bytes='50000000',max_files='500',max_line_bytes='65536',wall_ms='30000')
  request=supplied/'REQUEST.tsv';request.write_bytes(('key\tvalue\n'+''.join(k+'\t'+v+'\n' for k,v in sorted(c.items()))).encode());command=[java,'-cp',str(a.jar.absolute()),'org.jlinalg.cli.MetalWorkflowCli','run','--request',str(request),'--out',str(out/'result')];(out/'command.json').write_bytes((json.dumps(command,indent=2)+'\n').encode())
  with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:r=subprocess.run(command,cwd=ROOT,stdout=so,stderr=se,stdin=subprocess.DEVNULL)
  (out/'PROCESS.json').write_bytes((json.dumps(dict(exit_code=r.returncode),indent=2)+'\n').encode());print((out/'stdout').read_text(),end='');print((out/'stderr').read_text(),end='',file=sys.stderr)
  if r.returncode:return r.returncode
  (out/'COMPLETED.json').write_bytes((json.dumps(dict(status='PASS_SYNTHETIC_CAPTURED_METAL_EXAMPLE',mode=a.mode,rows=3,biological_validation=False,private_runtime_redistribution_permitted=False),indent=2)+'\n').encode());return 0
 except Exception as e:
  message='FAIL2: no complete example result: '+repr(e)+'; preserve partials and use NEW output after correcting the cause.'
  if out is not None:(out/'FAILURE.txt').write_text(message+'\n',encoding='utf-8')
  print(message,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
