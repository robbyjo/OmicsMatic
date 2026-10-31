"""Check the packaged native FUMA locus module against frozen independent references.

Original outputs with broken locus invariants remain retained, never silently
used as valid golden partitions. Only Python3 standard library and Java are used.
"""
from pathlib import Path
from decimal import Decimal
import argparse,csv,hashlib,json,re,subprocess,sys

HERE=Path(__file__).resolve().parent
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read_table(path):
 with path.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def run_case(fixture,options,java,jar,root):
 name=fixture['name'];input_directory=root/'inputs'/name;input_directory.mkdir(parents=True)
 allowed={'gwas.tsv','ref.tsv','ld.tsv','regions.tsv','seeds.tsv'}
 for filename,data in fixture['files'].items():
  if filename not in allowed:raise ValueError('Unknown fixture file name')
  (input_directory/filename).write_text(data,encoding='utf-8',newline='\n')
 o=dict(fixture['options']);output=root/('result-'+name)
 for key in ['gwas','reference','ld','regions','predefined']:
  if key in o:
   if o[key] not in allowed:raise ValueError('Invalid fixture resource path')
   o[key]=str(input_directory/o[key])
 o['out']=str(output);command=[java,'-jar',str(jar),'fuma-tools']+[str(x) for k,v in o.items() for x in ('--'+k,v)]
 try:process=subprocess.run(command,capture_output=True,timeout=120)
 except FileNotFoundError as failure:raise RuntimeError('JAVA UNAVAILABLE: '+str(failure)) from failure
 (root/(name+'.stdout')).write_bytes(process.stdout);(root/(name+'.stderr')).write_bytes(process.stderr)
 if b'UnsupportedClassVersionError' in process.stderr:raise RuntimeError('JAVA UNAVAILABLE: supply a Java runtime compatible with the compiled Java17 candidate')
 record=dict(name=name,command=command,exit_code=process.returncode);errors=[]
 if process.returncode:record['errors']=['Native analysis did not complete;read retained stderr/WARNINGS.md'];return record
 source=fixture['original_reference'];original={r[0]:r for c in source['chromosomes'] for r in c['snps']};original_inds={r[0]:r for c in source['chromosomes'] for r in c['independent']};original_leads={r[0]:r for c in source['chromosomes'] for r in c['leads']};source_ld={(a,b):float(r2) for c in source['chromosomes'] for a,b,r2 in c['ld']};rs2uid={r[1]:r[0] for r in original_inds.values()};expected_loci=fixture['canonical_reference']['risk_loci'];actual={r['uid']:r for r in read_table(output/'candidates.tsv')};inds={r['uid']:r for r in read_table(output/'independent.tsv')};leads={r['uid']:r for r in read_table(output/'leads.tsv')};raw_rows={r['rsID']:r for r in read_table(input_directory/'gwas.tsv')}
 source_tie_indices=None
 if name=='large-P-ties-coordinate-policy':
  # All P values are exactly tied and LD components are disjoint perfect cliques.
  # The declared coordinate policy therefore chooses each clique's minimum position.
  source_tie_indices=sorted(original_inds);variants=fixture['protocol']['variants'];byid={v['id']:v for v in variants};adj={v['id']:set() for v in variants}
  for a,b,value in fixture['protocol']['pairs']:
   assert value==1;adj[a].add(b);adj[b].add(a)
  remaining=set(adj);groups=[]
  while remaining:
   root=min(remaining,key=lambda k:byid[k]['pos']);component=set();stack=[root]
   while stack:
    vertex=stack.pop()
    if vertex in component:continue
    component.add(vertex);stack.extend(adj[vertex]-component)
   remaining-=component
   assert all(adj[k]&component==component-{k} for k in component)
   groups.append(sorted(component,key=lambda k:byid[k]['pos']))
  groups.sort(key=lambda g:byid[g[0]]['pos']);original_inds={};original_leads={};source_ld={};seeds=[]
  for group in groups:
   v=byid[group[0]];uid=str(v['chrom'])+':'+str(v['pos'])+':A:G';seeds.append(uid);original_inds[uid]=[uid,v['rs'],str(v['chrom']),str(v['pos']),str(v['p']),str(len(group)),str(len(group))];original_leads[uid]=[uid,v['rs'],str(v['chrom']),str(v['pos']),str(v['p']),'1',v['rs']]
   for vertex in group:
    target=byid[vertex];source_ld[uid,str(target['chrom'])+':'+str(target['pos'])+':A:G']=1.0
  rs2uid={r[1]:r[0] for r in original_inds.values()};expected_loci=[dict(index=1,chrom=1,top=seeds[0],start=min(v['pos'] for v in variants),end=max(v['pos'] for v in variants),independent=seeds,lead=seeds,candidates=sorted(original),candidate_count=len(variants),gwas_candidate_count=len(variants))]
 if name=='candidate-p-below':
  assert Decimal(raw_rows['rs32']['p'])<Decimal('.05');original=dict(original);original['1:1100:A:G']=['1:1100:A:G','rs32','1','1100','A','G','0.25',raw_rows['rs32']['p']];original_inds=dict(original_inds);original_inds['1:100:A:G']=list(original_inds['1:100:A:G']);original_inds['1:100:A:G'][5:7]=['2','2'];source_ld=dict(source_ld);source_ld['1:100:A:G','1:1100:A:G']=.9;expected_loci=[dict(index=1,chrom=1,top='1:100:A:G',start=100,end=1100,independent=['1:100:A:G'],lead=['1:100:A:G'],candidates=['1:100:A:G','1:1100:A:G'],candidate_count=2,gwas_candidate_count=2)]
 if actual.keys()!=original.keys():errors.append(dict(field='candidate-identities',expected=sorted(original),actual=sorted(actual)))
 for uid in actual.keys()&original.keys():
  a,e=actual[uid],original[uid]
  for key,index in [('rsID',1),('chr',2),('pos',3),('non_effect_allele',4),('effect_allele',5)]:
   if str(a[key])!=str(e[index]):errors.append(dict(field=key,uid=uid,source=e[index],native=a[key]))
  if float(a['MAF'])!=float(e[6]):errors.append(dict(field='MAF',uid=uid))
  if e[7]=='NA':
   if a['gwasP']!='NA':errors.append(dict(field='reference-only-role',uid=uid))
  else:
   literal=raw_rows[a['rsID']]['p']
   if a['gwasP']!=literal or float(a['parsed_gwasP'])!=float(literal):errors.append(dict(field='unchanged-decimal-P-and-double',uid=uid,expected=literal,actual=a['gwasP']))
 if inds.keys()!=original_inds.keys():errors.append(dict(field='independent-identities'))
 for uid in inds.keys()&original_inds.keys():
  if int(inds[uid]['source_new_candidates'])!=int(original_inds[uid][5]) or int(inds[uid]['source_new_gwas_candidates'])!=int(original_inds[uid][6]):errors.append(dict(field='legacy-new-counts',uid=uid))
 if leads.keys()!=original_leads.keys():errors.append(dict(field='lead-identities'))
 for uid in leads.keys()&original_leads.keys():
  if leads[uid]['independent_uids'].split(';')!=[rs2uid[r] for r in original_leads[uid][6].split(';')]:errors.append(dict(field='lead-direct-memberships',uid=uid))
 actual_ld={(r['independent_uid'],r['candidate_uid']):float(r['r2']) for r in read_table(output/'ld-memberships.tsv')}
 if actual_ld!=source_ld:errors.append(dict(field='complete-source-LD-memberships'))
 by_locus={int(r['locus']):r for r in read_table(output/'loci.tsv')}
 if len(by_locus)!=len(expected_loci):errors.append(dict(field='complete-component-count',actual=len(by_locus),expected=len(expected_loci)))
 for e in expected_loci:
  a=by_locus.get(e['index'])
  if a is None:continue
  for ak,ek in [('chr','chrom'),('start','start'),('end','end'),('n_candidates','candidate_count'),('n_gwas_candidates','gwas_candidate_count')]:
   if int(a[ak])!=e[ek]:errors.append(dict(field='component-'+ak,expected=e[ek],actual=a[ak]))
  if a['top_uid']!=e['top']:errors.append(dict(field='top-lead'))
  for ak,ek in [('independent_uids','independent'),('lead_uids','lead')]:
   if set(a[ak].split(';'))!=set(e[ek]):errors.append(dict(field='complete-component-'+ak))
  assigned={uid for uid,c in actual.items() if int(c['locus'])==e['index']}
  if assigned!=set(e['candidates']):errors.append(dict(field='candidate-locus-assignments'))
 session=json.loads((output/'session.json').read_text());assert session['candidate_rows']==len(actual) and session['risk_loci']==len(by_locus);assert session['locus_rule']=='complete-transitive-interval-components';assert 'CONSISTENT LOCUS COMPONENTS' in (output/'WARNINGS.md').read_text()
 if name=='candidate-p-below':assert 'STRICT P BOUNDARY' in (output/'WARNINGS.md').read_text()
 if name=='large-P-ties-coordinate-policy':assert 'COORDINATE P-TIE POLICY' in (output/'WARNINGS.md').read_text()
 if name in {'mhc-inclusive','MHC-neighbor-exclusion'}:assert 'MHC FILTER IS NOT INTERVAL CLIPPING' in (output/'WARNINGS.md').read_text()
 if name=='shared-first-stage-candidate':assert 'LEGACY INCREMENTAL COUNTS' in (output/'WARNINGS.md').read_text()
 if fixture['protocol']['options']['seeds'] and len(inds)>0:assert 'PREDEFINED SEED BYPASS' in (output/'WARNINGS.md').read_text()
 record.update(candidates=len(actual),independent=len(inds),leads=len(leads),loci=len(by_locus),legacy_parser_boundary_divergence=name=='candidate-p-below',legacy_tie_index_divergence=name=='large-P-ties-coordinate-policy',retained_original_tie_indices=source_tie_indices,errors=errors,outputs_sha256={p.name:sha(p) for p in output.iterdir() if p.is_file()});return record

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--java',default='java');parser.add_argument('--out',type=Path,required=True);parser.add_argument('--case',help='One frozen protocol name;default checks all43');args=parser.parse_args()
 jar=args.jar.resolve()
 if not jar.is_file():parser.error('--jar must name an existing development executable JAR')
 fixture=HERE/'fixtures/fuma-loci/reference-cases.json';provenance=json.loads((fixture.parent/'provenance.json').read_text(encoding='utf-8'))
 if sha(fixture)!=provenance['fixture_sha256']:raise ValueError('Frozen fixture hash changed;inspect provenance before using altered expectations')
 cases=json.loads(fixture.read_text(encoding='utf-8'))['cases'];assert len(cases)==43
 if args.case:cases=[c for c in cases if c['name']==args.case]
 if not cases:parser.error('Unknown --case name')
 for case in cases:
  if not re.fullmatch(r'[A-Za-z0-9_-]+',case['name']):raise ValueError('Invalid fixture protocol name')
 root=args.out.resolve();root.mkdir(parents=True,exist_ok=False);records=[];status='running';code=0
 try:
  for case in cases:records.append(run_case(case,args,args.java,jar,root))
  status='passed-public-native-FUMA-locus-references' if all(r['exit_code']==0 and not r['errors'] for r in records) else 'failed-comparisons-retained';code=0 if status.startswith('passed') else 2
 except RuntimeError as failure:
  status='skipped-unavailable-Java';code=3;message=str(failure)+'; Required action: install an approved compatible JDK or supply its --java executable, then rerun to a new directory. No installation was attempted.';print('VERIFICATION SKIPPED: '+message,file=sys.stderr);records.append(dict(error=message))
 report=dict(status=status,jar_sha256=sha(jar),fixture_sha256=sha(fixture),verifier_sha256=sha(__file__),cases_requested=len(cases),cases_completed=sum('name' in r for r in records),runs=records,scope='Frozen synthetic original-function and independent component references;explicit raw-parser/tie differences and original invalid partitions retained. No original runtime,network orfull-FUMA/historical/paper reconstruction.')
 (root/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(status=status,cases=report['cases_completed'],exit_code=code)));return code
if __name__=='__main__':
 try:raise SystemExit(main())
 except (OSError,ValueError,AssertionError,subprocess.TimeoutExpired) as failure:print('VERIFICATION FAILED: '+str(failure)+';Required action: inspect the named input/output and rerun to a new directory.',file=sys.stderr);raise SystemExit(2)
