from pathlib import Path
import json,math,random
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/squidpy-1/native-inputs-1';B.mkdir();rng=random.Random(129106);graphs=[]
for name,n in [('ring',6),('directed',8),('signed',9),('unequal-isolate',11)]:
 matrix=[[0.0]*n for _ in range(n)]
 for i in range(n-(name=='unequal-isolate')):
  limit=n-(name=='unequal-isolate')
  matrix[i][(i+1)%limit]=(i%3+1)/4
  matrix[i][(i+3)%limit]=(i%4+1)/8
  if name=='ring':matrix[(i+1)%limit][i]=(i%3+1)/4
  elif name=='directed' and i%3==0:matrix[i][i]=.125
  elif name=='signed' and i%3==1:matrix[i][(i+3)%limit]*=-.5
  elif name=='unequal-isolate' and i%4==0:matrix[i][i]=.2
 pointers=[0];columns=[];weights=[]
 for row in matrix:
  for j,value in enumerate(row):
   if value:columns.append(j);weights.append(value)
  pointers.append(len(columns))
 values=[[float(i-2) for i in range(n)],[rng.randint(-8,8)/4 for i in range(n)],[3.0]*n]
 graphs.append(dict(name=name,n=n,row_pointers=pointers,columns=columns,weights=weights,features=dict(zip(['gradient','mixed','constant'],values))))
result=dict(schema_version=1,synthetic_inputs_only=True,seed=129106,graphs=graphs)
(B/'inputs.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(graphs=len(graphs),features_per_graph=3,source_equivalence_candidates_per_profile=64,source_constant_feature_rows_per_profile=32)),flush=True)
