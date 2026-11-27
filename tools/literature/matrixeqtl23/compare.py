"""Compare complete original MatrixEQTL tables and independent R lm/GLS fits.

Inputs are private synthetic controls captured by reference.R and the native CLI.
No original scientific data, author code or article text is published here.
"""
import argparse,csv,json,math
from pathlib import Path

def read(path):return json.loads(Path(path).read_bytes())
def table(path):
 with Path(path).open(encoding='utf-8-sig',newline='')as stream:return list(csv.DictReader(stream,delimiter='\t'))
def numeric(value):return float('nan')if value in ['NA','NaN']else float(value)
def near(actual,expected,label,log=False):
 a,b=numeric(actual),numeric(expected)
 if not math.isfinite(a)or not math.isfinite(b):assert a==b or math.isnan(a)and math.isnan(b),(label,a,b);return 0.
 if log:
  assert a>0 and b>0,(label,a,b);error=abs(math.log(a)-math.log(b));assert error<=2e-10,(label,a,b,error)
 else:
  error=abs(a-b);assert error<=2e-12+2e-10*abs(b),(label,a,b,error)
 return error
def compare(root):
 root=Path(root);native=read(root/'capture/results.json');counts=dict(associations=0,independent_lm=0,minima=0);maximum={};results={}
 for family in ['all','cis','trans']:
  expected=root/('original-'+family+'.tsv')
  if not expected.exists():continue
  rows=table(expected);actual=native[family];assert actual['ntests']==int(table(root/('original-'+family+'-count.tsv'))[0]['ntests'])
  original={(r['snps'],r['gene']):r for r in rows};observed={(r['snp'],r['gene']):r for r in actual['eqtls']};assert len(rows)==len(original)and len(actual['eqtls'])==len(observed)and original.keys()==observed.keys(),('association population',root.name,family,original.keys()^observed.keys())
  for key in original:
   for field in ['beta','statistic','pvalue','FDR']:
    error=near(observed[key][field],original[key][field],(root.name,family,key,field),field in ['pvalue','FDR']);maximum[field]=max(maximum.get(field,0),error)
   results[key]=observed[key];counts['associations']+=1
  for kind,field in [('snps','minimum_by_snp'),('gene','minimum_by_gene')]:
   filename=root/('original-'+family+'-minimum-'+kind+'.tsv')
   if not filename.exists():continue
   values={r['id']:r['p']for r in table(filename)};assert values.keys()==actual[field].keys()
   for key,value in values.items():near(actual[field][key],value,(root.name,family,kind,key),True);counts['minima']+=1
 for row in table(root/'independent-lm.tsv'):
  key=(row['snp'],row['gene'])
  if key not in results:continue
  actual=results[key]
  if actual['zero_snp']or actual['zero_gene']:continue
  near(actual['beta'],row['beta'],(root.name,key,'independent coefficient'))
  near(actual['derived_SE'],row['SE'],(root.name,key,'independent SE'))
  assert native['residual_df']==int(row['df']);counts['independent_lm']+=1
 assert read(root/'capture/analysis-status.json')['result_admitted']and read(root/'replay/analysis-status.json')['status']=='PASS_COMPLETE_SAVED_INPUT_RECOMPUTATION'
 return dict(case=root.name,counts=counts,maximum_errors=maximum,status='PASS_COMPLETE_ORIGINAL_TABLES_AND_INDEPENDENT_LM')
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('cases',type=Path);args=parser.parse_args();manifest=read(args.cases/'CAPTURES.json');results=[compare(args.cases/name)for name in manifest['cases']];print(json.dumps(results,indent=2))
