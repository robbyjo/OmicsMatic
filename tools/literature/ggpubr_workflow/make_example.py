#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare explicit synthetic ggpubr inputs and a version-specific trusted-script request."""
from pathlib import Path
import argparse,json
HERE=Path(__file__).resolve().parent

def request(version,provider,script,inputs,outputs,marker,out):
 return dict(schema_version=1,version=version,mode='SCRIPT',script=str(script),provider_directory=str(provider),
  inputs={n:str(p) for n,p in inputs.items()},expected_outputs=outputs,
  required_exports=['compare_means','stat_compare_means','stat_cor','ggboxplot','ggscatter','ggviolin'],required_packages={},
  expected_return=marker,input_provenance='Explicit synthetic project example; not recovered author data or figure settings.',
  analysis_population='All24 declared synthetic subjects; one explicit unpaired two-sided Welch t comparison, one pooled Pearson correlation and three complete plots.',
  execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(out),
  limits=dict(timeout_seconds=900,maximum_files=20000,maximum_workspace_bytes=1073741824,maximum_file_bytes=536870912))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['0.4.0','0.6.0'],required=True);p.add_argument('--provider',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();provider=a.provider.resolve()
 for protected in [provider,HERE]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected provider/source')
 out.mkdir(parents=True,exist_ok=False);rows=['sample\tgroup\tvalue\tother'];values=[3,4,5,7,8,10,12,14,17,19,22,25]
 for i in range(24):
  value=values[i%12]+(3 if i>=12 else 0);other=value*.7+((i*7)%11)/3
  rows.append('sample-α-'+str(i+1)+'\t'+('control' if i<12 else 'treatment')+'\t'+str(value)+'\t'+str(other))
 (out/'data.tsv').write_text('\n'.join(rows)+'\n',encoding='utf8',newline='\n')
 outputs={n:1 for n in ['complete-original-objects.rds','complete-layer-data.rds','complete-statistics.tsv','construction-device.pdf']}
 outputs.update({name+'.'+extension:1 for name in ['boxplot','scatter','violin'] for extension in ['svg','png']})
 value=request(a.version,provider,HERE/'ExampleAnalysis.R',{'data.tsv':out/'data.tsv'},outputs,'ALL_THREE_ORIGINAL_GGPUBR_EXAMPLE_PLOTS_AND_STATISTICS_RETURNED',out/'analysis')
 (out/'request.json').write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
 print('READY_EXPLICIT_GGPUBR_'+a.version+'_EXAMPLE_NOT_ANALYSIS',flush=True)
if __name__=='__main__':main()
