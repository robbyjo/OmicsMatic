# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# VCF consistency logic follows CrossMap cmmodule.mapvcf0.5.2/0.6.3,
# Copyright (C) 2018 Liguo Wang; original distributions include GPLv3-or-later
# LICENSE.txt. The original source programs are installed separately unchanged.
"""Run original CrossMap and account for every selected source record and output."""
from pathlib import Path
import argparse,collections,contextlib,hashlib,io,json,math,os,re,runpy,struct,subprocess,sys
sys.dont_write_bytecode=True
RUNNER="import sys,runpy;root=sys.argv.pop(1);entry=sys.argv.pop(1);sys.path.insert(0,root);sys.argv[0]=entry;runpy.run_path(entry,run_name='__main__')"
def put(p,v):p.write_bytes((json.dumps(v,indent=2,allow_nan=False)+'\n').encode())
def digest(line):return hashlib.sha256(line.encode()).hexdigest()
def file_pin(path):
 with path.open('rb')as stream:return path.stat().st_size,hashlib.file_digest(stream,'sha256').hexdigest()
def require(value,message):
 if not value:raise ValueError(message)
def signal_key(c,s,e,v):return hashlib.sha256(c.encode()+b'\0'+struct.pack('<qqf',s,e,v)).hexdigest()
def main(root,home):
 spec=json.loads((root/'request.json').read_bytes());version=spec['edition'];mode=spec['format'];limit=spec['capture']['records'];source=home/'sources'/version;entries=list(source.glob('*.data/scripts/CrossMap.py'));require(len(entries)==1,'Missing unique original entrypoint');entry=entries[0];sys.path.insert(0,str(source));ns=runpy.run_path(str(entry),run_name='original_source_probe');require(ns['__version__']==version,'Original source version differs')
 from cmmodule import ireader,utils,mapbed,annoGene,bgrMerge
 inputfile=root/('inputs/input.'+('bw'if mode=='bigwig'else mode)+('.gz'if spec['input_encoding']=='gzip'else''));chain=root/('inputs/mapping.chain'+('.gz'if spec['chain_encoding']=='gzip'else''));fasta=root/'inputs/reference.fa';prefix=root/'outputs/mapped';output=Path(str(prefix)+('.'+mode if mode!='bigwig'else''));out=root/'audit';out.mkdir();maximum_bytes=spec['capture']['input_bytes']
 args=[mode,str(chain),str(inputfile)]
 if mode=='vcf':args.append(str(fasta))
 args.append(str(output))
 if mode=='vcf'and not spec['compare_alleles']:args.append('--no-comp-alleles')
 if version=='0.6.3':args+=['--chromid',spec['chrom_id_style']]
 command=[str(home/'bin/python'),'-I','-B','-c',RUNNER,str(source),str(entry),*args];put(out/'original-command.json',command)
 with(out/'original.stdout').open('xb')as stdout,(out/'original.stderr').open('xb')as stderr:child=subprocess.run(command,cwd=root/'outputs',stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
 put(out/'original-process.json',dict(exit_code=child.returncode,source_version=version,original_source_modified=False));require(child.returncode==0,'Original CrossMap exited '+str(child.returncode)+'; retain original.stderr and every incomplete output. Known original limitations include missing VCF contig metadata, empty input BigWig chromosomes and all-unmapped signal tracks.')
 mapping,target_sizes,source_sizes=utils.read_chain_file(str(chain));style=spec['chrom_id_style'];kwargs={'chrom_style':style}if version=='0.6.3'else{};expected=collections.Counter();rejected=collections.Counter();totals={id(expected):0,id(rejected):0};counts=collections.Counter({k:0 for k in('source_records','mapped_inputs','rejected_inputs','partially_mapped_inputs','inputs_with_multiple_output_blocks','bed20_metadata_omissions','mapped_records','rejected_records','ledger_records')});decoded=[0]
 def bounded_lines(path):
  for line in ireader.reader(str(path)):
   decoded[0]+=len(line.encode())+1;require(decoded[0]<=maximum_bytes,'Complete decoded text exceeds capture.input_bytes; no truncation is allowed');require(len(line.encode())<=1048576,'Source logical line exceeds1MiB capacity');yield line
 def add(counter,line):
  counter[digest(line)]+=1;totals[id(counter)]+=1;require(totals[id(counter)]<=limit,'Complete expected output records exceed capture.records')
 def covered(query,maps):
  start,end=query;segments=sorted((max(start,maps[i][1]),min(end,maps[i][2]))for i in range(0,len(maps),2));total=0;at=start
  for s,e in segments:
   if e>max(at,s):total+=e-max(at,s)
   at=max(at,e)
  return total
 ledger=(root/'OUTPUT-LEDGER.jsonl').open('x',encoding='utf-8',newline='\n')
 def record(row):
  counts['ledger_records']+=1;require(counts['ledger_records']<=limit,'Complete input ledger exceeds capture.records');ledger.write(json.dumps(row,separators=(',',':'),allow_nan=False)+'\n')
 def rowbase(index,line,kind):return dict(index=index,input_sha256=digest(line),kind=kind,mapped_hashes=[],rejected_hashes=[])
 try:
  if mode=='bed':
   for index,line in enumerate(bounded_lines(inputfile)):
    row=rowbase(index,line,'ignored'if not line or line.startswith(('#','track','browser'))else'data')
    if row['kind']=='ignored':record(row);continue
    counts['source_records']+=1;fields=line.split();row['source_fields']=len(fields);stdout=io.StringIO();stderr=io.StringIO()
    with contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):mapbed.crossmap_bed_file(mapping,io.BytesIO((line+'\n').encode()),None,**({'cstyle':style}if version=='0.6.3'else{}))
    mapped=[];unmapped=[]
    for result in stdout.getvalue().splitlines():
     require(result.startswith(line+'\t'),'Original per-record BED witness has an unknown prefix');tail=result[len(line):]
     if tail.startswith('\t->\t'):mapped.append(tail[4:])
     elif tail.startswith('\t(split.'):
      at=tail.find(')\t');require(at>=0,'Unknown original BED split witness');mapped.append(tail[at+2:])
     elif tail in('\tUnmap','\tFail'):unmapped.append(line if len(fields)in(12,20)else line+tail)
     else:raise ValueError('Unknown original BED witness disposition: '+tail[:100])
    if not mapped and not unmapped:
     reason=None
     if len(fields)<3:reason='InvalidBedFormat'
     else:
      try:start=int(fields[1])
      except ValueError:reason='InvalidStartPosition'
      try:end=int(fields[2])
      except ValueError:reason=reason or'InvalidEndPosition'
      if reason is None and start>end:reason='Start>End'
     require(reason is not None,'Original BED source silently emitted no complete disposition; field count '+str(len(fields)));unmapped=[line+'\t'+reason]
    require(not(mapped and unmapped),'Original BED emitted both mapped and rejected outcomes for one input; no complete result admitted')
    for value in mapped:add(expected,value);row['mapped_hashes'].append(digest(value))
    for value in unmapped:add(rejected,value);row['rejected_hashes'].append(digest(value))
    counts['mapped_inputs'if mapped else'rejected_inputs']+=1
    if len(mapped)>1:counts['inputs_with_multiple_output_blocks']+=1
    if mapped:
     strand='+'
     for value in fields:
      if value in('+','-'):strand=value
     queries=[(fields[0],int(fields[1]),int(fields[2]))]if len(fields)<12 else annoGene.getExonFromLine(line)
     bases=0;covered_bases=0
     for chromosome,start,end in queries:
      bases+=end-start;maps=utils.map_coordinates(mapping,chromosome,start,end,strand,**kwargs);covered_bases+=covered((start,end),maps or[])
     row['source_span_bases']=bases;row['source_covered_bases']=covered_bases
     if covered_bases<bases:counts['partially_mapped_inputs']+=1
     if len(fields)==20:counts['bed20_metadata_omissions']+=1
    row['source_disposition']='mapped'if mapped else'original-rejected';row['original_diagnostic']=stderr.getvalue();record(row)
  elif mode=='vcf':
   import pysam
   ref=pysam.FastaFile(str(fasta));require(bool(ref.references),'Empty target reference');header=0
   for index,line in enumerate(bounded_lines(inputfile)):
    row=rowbase(index,line,'ignored'if not line or line.startswith('#')else'data')
    if row['kind']=='ignored':
     if line.startswith('#CHROM'):header+=1
     record(row);continue
    counts['source_records']+=1;fields=line.split(maxsplit=7);require(len(fields)==8,'VCF source row lacks complete fixed fields');start=int(fields[1])-1;require(start>=0 and fields[3],'Invalid source VCF position/reference allele');end=start+len(fields[3]);maps=utils.map_coordinates(mapping,fields[0],start,end,'+',**kwargs);reason=None
    if maps is None:reason='Unmap'
    elif len(maps)!=2:reason='Multiple_hits'
    else:
     target_chr=maps[1][0];target_start,target_end=maps[1][1:3];fields[0]=target_chr;fields[1]=target_start+1;fetch_chr=utils.update_chromID(ref.references[0],target_chr)
     try:fields[3]=ref.fetch(fetch_chr,target_start,target_end).upper()
     except Exception:reason='KeyError'
     if reason is None:
      if version=='0.6.3':fields[4]=','.join(fields[3][0]+a[1:]if len(fields[3])!=len(a)else a for a in fields[4].split(','))
      fields[7]=re.sub(r'END\=\d+','END='+str(target_end),fields[7])
      if maps[1][3]=='-':fields[4]=utils.revcomp_DNA(fields[4],True)
      if spec['compare_alleles']and fields[3]==fields[4]:reason='REF==ALT'
    if reason:
     value=line+'\tFail('+reason+')';add(rejected,value);row['rejected_hashes']=[digest(value)];counts['rejected_inputs']+=1;row['source_disposition']=reason
    else:
     value='\t'.join(map(str,fields));add(expected,value);row['mapped_hashes']=[digest(value)];counts['mapped_inputs']+=1;row['source_disposition']='mapped';row['reference_contig']=fetch_chr;row['source_covered_bases']=covered((start,end),maps);row['source_span_bases']=end-start
     if row['source_covered_bases']<end-start:counts['partially_mapped_inputs']+=1
    record(row)
   ref.close();require(header==1,'Require one complete original #CHROM header; no header is invented')
  else:
   import pyBigWig
   witness=out/'source-mapped.bgr';witness_file=witness.open('x',encoding='utf-8',newline='\n')
   for index,(chrom,start,end,value)in enumerate(utils.bigwigReader(str(inputfile))):
    require(math.isfinite(value),'Nonfinite source signal is unassessable in this finite raw-interval profile');counts['source_records']+=1;row=dict(index=index,kind='signal',input_sha256=signal_key(chrom,start,end,value),source_interval=[chrom,start,end,value],mapped_hashes=[],rejected_hashes=[]);maps=utils.map_coordinates(mapping,chrom,start,end,'+',**kwargs)
    if maps is not None and len(maps)==2:
     target=maps[1];witness_file.write('\t'.join(map(str,[target[0],target[1],target[2],value]))+'\n');row['target_interval']=[target[0],target[1],target[2],value];row['source_disposition']='mapped';row['source_covered_bases']=covered((start,end),maps);row['source_span_bases']=end-start;counts['mapped_inputs']+=1
     if row['source_covered_bases']<end-start:counts['partially_mapped_inputs']+=1
    else:row['source_disposition']='discarded-no-single-mapped-block';counts['rejected_inputs']+=1
    record(row)
   witness_file.close();expected_graph=out/'source-expected.sorted.bgr'
   with expected_graph.open('x',encoding='utf-8',newline='\n')as f:
    for row in bgrMerge.merge(str(witness)):f.write('\t'.join(map(str,row))+'\n')
   actual_graph=Path(str(output)+'.sorted.bgr');require(file_pin(expected_graph)==file_pin(actual_graph),'Complete original merged signal differs from all source-record witnesses');counts['mapped_records']=sum(1 for _ in actual_graph.open());counts['rejected_records']=0
   bw=pyBigWig.open(str(output)+'.bw');binary_count=0;binary_sum=0;graph_count=0;graph_sum=0;modulus=1<<256;dictionary=bw.chroms()
   for chromosome in dictionary:
    for start,end,value in bw.intervals(chromosome)or[]:
     require(math.isfinite(value),'Nonfinite original binary signal');binary_count+=1;binary_sum=(binary_sum+int(signal_key(chromosome,start,end,value),16))%modulus;require(binary_count<=limit,'Complete binary output exceeds record capacity')
   with actual_graph.open()as graph:
    for line in graph:
     c,s,e,v=line.split();number=float(v);require(math.isfinite(number),'Nonfinite target signal');graph_count+=1;graph_sum=(graph_sum+int(signal_key(c,int(s),int(e),number),16))%modulus
   require(binary_count==graph_count and binary_sum==graph_sum,'Complete original BigWig raw intervals differ from sorted bedGraph float32 values');dictionary_sum=sum(int.from_bytes(hashlib.sha256(c.encode()+b'\0'+struct.pack('<q',n)).digest(),'big')for c,n in dictionary.items())%modulus
   put(root/'BINARY-OUTPUT.json',dict(dictionary_records=len(dictionary),dictionary_sha256_sum_mod_2_256=format(dictionary_sum,'064x'),interval_records=binary_count,intervals_sha256_sum_mod_2_256=format(binary_sum,'064x'),all_raw_intervals_checked=True,source_zoom_summaries_used=False));bw.close()
  if mode!='bigwig':
   actual=collections.Counter();failed=collections.Counter()
   for destination,counter in((output,actual),(Path(str(output)+'.unmap'),failed)):
    emitted=0
    for value in ireader.reader(str(destination)):
     if not value or value.startswith('#'):continue
     counter[digest(value)]+=1;emitted+=1;require(emitted<=limit,'Complete emitted output exceeds record capacity')
   require(actual==expected and failed==rejected,'Complete original mapped/unmapped populations differ from all per-record witnesses');counts['mapped_records']=sum(actual.values());counts['rejected_records']=sum(failed.values())
  require(counts['source_records']==counts['mapped_inputs']+counts['rejected_inputs'],'Complete source input dispositions differ')
  require(spec['unmapped_policy']=='retain'or not(counts['rejected_inputs']or counts['partially_mapped_inputs']),'Explicit unmapped_policy=error refuses source rejections/discards/partial spans; original outputs remain retained, no complete result admitted')
 finally:ledger.close()
 result=dict(schema_version=1,status='COMPLETE_ORIGINAL_SOURCE_OUTPUT_CONSISTENCY',edition=version,format=mode,counts=dict(counts),unmapped_policy=spec['unmapped_policy'],original_source_modified=False,author_edition_inferred=False,author_Python37_reproduced=False,whole_paper_reproduced=False,independent_source_model_reimplementation=False)
 put(root/'OUTPUT-AUDIT.json',result);print(json.dumps(result))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,required=True);p.add_argument('--home',type=Path,required=True);a=p.parse_args()
 try:main(a.root,a.home)
 except Exception as e:
  try:put(a.root/'BRIDGE-FAILURE.json',dict(status='FAIL2_NO_COMPLETE_RESULT',error=repr(e)))
  except OSError:pass
  print('FAIL2: original CrossMap conversion/output contract failed: '+repr(e)+'. Read retained original stderr and per-record evidence. Preserve all inputs and attempts; use new paths after correcting the cause. No fallback source edition or generic mapper is used.',file=sys.stderr);sys.exit(2)
