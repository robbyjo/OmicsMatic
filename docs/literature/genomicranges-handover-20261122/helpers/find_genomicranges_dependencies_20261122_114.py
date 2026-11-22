from pathlib import Path
import json,re
from prepare_genomicranges_20261122_114 import R,B
from handover_inventory_20261122_111 import pin,save
D=B/'dependency-screening-1';D.mkdir();start=R/'build/literature-review/dndscv-runtime/reference-setup-3/STARTED.json';libs=[Path(p) for p in json.loads(start.read_bytes())['libraries']];found={}
def dcf(path):
 fields={};key=None
 for line in path.read_text(encoding='utf-8').splitlines():
  if line and line[0].isspace():fields[key]+=' '+line.strip()
  elif ':' in line:key,value=line.split(':',1);fields[key]=value.strip()
 return fields
def find(name):
 if name in found or name=='R':return
 for lib in libs:
  path=lib/name/'DESCRIPTION'
  if path.is_file():
   fields=dcf(path);found[name]={'package':name,'version':fields['Version'],'library':str(lib),'path':str(lib/name),'description':pin(path),'Built':fields.get('Built'),'License':fields.get('License')}
   dependencies=[]
   for key in ['Depends','Imports','LinkingTo']:
    for value in fields.get(key,'').split(','):
     dep=re.split(r'\s|\(',value.strip())[0]
     if dep and dep!='R' and dep not in dependencies:dependencies.append(dep)
   found[name]['dependencies']=dependencies
   for dep in dependencies:find(dep)
   return
 raise FileNotFoundError(name)
find('GenomicRanges');save(D/'RESULT.json',{'libraries':list(map(str,libs)),'selected':found,'installed_or_modified':False,'new_catalog_credit':0});print(json.dumps(found,indent=2),flush=True)
