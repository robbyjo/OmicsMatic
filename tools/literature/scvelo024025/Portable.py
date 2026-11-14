#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain independent NumPy/pandas/AnnData stage graphs beside untouched original objects; no Torch dependency."""
from pathlib import Path
import json,random,types
import anndata as ad
import numpy as np
import pandas as pd
from scipy import sparse

class CompleteGraph:
 def __init__(self,out):
  self.out=Path(out);(self.out/'arrays').mkdir(exist_ok=False);self.records=[];self.nonfinite=[];self.opaque=[];self.references={};self.live=[];self.depth=0;self.snapshots=[]
 def row(self,key,kind,**metadata):
  self.records.append(dict(key=key,kind=kind,**metadata))
 def add(self,value,key):
  if self.depth==0:
   # A repeated object may have changed between model stages. Preserve aliases
   # and cycles within each snapshot, and capture values again at the next one.
   self.references={};self.live=[];self.snapshots.append(key)
  self.depth+=1
  try:self._add(value,key)
  finally:self.depth-=1
 def _add(self,value,key):
  # Every scalar is typed. Floating values retain NaN/Inf and signed zero verbatim.
  if value is None or isinstance(value,(str,bool,np.bool_)):
   self.row(key,'NoneType' if value is None else 'bool' if isinstance(value,(bool,np.bool_)) else 'str',value=value.item() if isinstance(value,np.generic) else value);return
  if isinstance(value,(int,np.integer)):self.row(key,'int',value=str(value));return
  if isinstance(value,(float,np.floating)):self.row(key,'float',value=float(value).hex());return
  if isinstance(value,(complex,np.complexfloating)):self.row(key,'complex',real=float(value.real).hex(),imag=float(value.imag).hex());return
  if isinstance(value,(types.FunctionType,type,types.BuiltinFunctionType)):
   self.row(key,'qualified-name',value=value.__module__+'.'+value.__qualname__);return
  identity=id(value)
  if identity in self.references:self.row(key,'reference',target=self.references[identity]);return
  self.references[identity]=key;self.live.append(value)
  if isinstance(value,np.ndarray):
   if value.dtype.kind=='O':
    self.row(key,'object-array',shape=list(value.shape),dtype=str(value.dtype))
    for idx in np.ndindex(value.shape):self.add(value[idx],key+[list(idx)])
   else:
    name='arrays/'+str(len(self.records)).zfill(6)+'.npy';np.save(self.out/name,value,allow_pickle=False)
    self.row(key,'array',shape=list(value.shape),dtype=str(value.dtype),file=name)
    if value.dtype.kind in 'fc' and not np.isfinite(value).all():
     self.nonfinite.append(dict(key=key,file=name,nan=int(np.isnan(value).sum()),positive_infinity=int(np.isposinf(value).sum()),negative_infinity=int(np.isneginf(value).sum())))
   return
  if isinstance(value,pd.Categorical):
   self.row(key,'pandas.Categorical',ordered=bool(value.ordered),ordered_scalar_type=type(value.ordered).__module__+'.'+type(value.ordered).__qualname__);self.add(value.categories,key+['categories']);self.add(value.codes,key+['codes']);return
  if isinstance(value,pd.Index):
   self.row(key,'pandas.'+type(value).__name__,dtype=str(value.dtype));self.add(value.name,key+['name'])
   if isinstance(value,pd.CategoricalIndex):self.add(value.array,key+['categorical'])
   elif isinstance(value,pd.MultiIndex):self.add(list(value.names),key+['names']);self.add(list(value.levels),key+['levels']);self.add(list(value.codes),key+['codes'])
   else:self.add(value.to_numpy(),key+['values'])
   return
  if isinstance(value,pd.DataFrame):
   self.row(key,'pandas.DataFrame',shape=list(value.shape));self.add(value.index,key+['index']);self.add(value.columns,key+['columns'])
   for i in range(value.shape[1]):self.add(value.iloc[:,i],key+['column',i])
   self.add(value.attrs,key+['attrs']);return
  if isinstance(value,pd.Series):
   self.row(key,'pandas.Series',dtype=str(value.dtype));self.add(value.index,key+['index']);self.add(value.name,key+['name']);self.add(value.attrs,key+['attrs'])
   self.add(value.array if isinstance(value.dtype,pd.CategoricalDtype) else value.to_numpy(),key+['values']);return
  if sparse.issparse(value):
   self.row(key,'scipy.sparse.'+type(value).__name__,shape=list(value.shape),format=value.format)
   for name in ['data','indices','indptr','row','col','offsets']:
    if hasattr(value,name):self.add(getattr(value,name),key+[name])
   return
  if isinstance(value,ad.AnnData):
   self.row(key,'anndata.AnnData',shape=list(value.shape))
   for name in ['X','obs','var','obsm','varm','obsp','varp','layers','uns']:
    x=getattr(value,name);self.add(dict(x) if name in ['obsm','varm','obsp','varp','layers'] else x,key+[name])
   if value.raw is not None:self.add(value.raw.to_adata(),key+['raw'])
   else:self.add(None,key+['raw'])
   return
  if isinstance(value,dict):
   keys=list(value);self.row(key,type(value).__module__+'.'+type(value).__qualname__,length=len(value));self.add(keys,key+['keys'])
   for i,k in enumerate(keys):self.add(value[k],key+['item',i])
   return
  if isinstance(value,(list,tuple,set,frozenset)):
   if isinstance(value,(set,frozenset)):
    # Retain original iteration order; do not silently sort labels or scientific objects.
    self.row(key,type(value).__name__,length=len(value),order='original-iteration-order')
   else:self.row(key,type(value).__name__,length=len(value))
   for i,x in enumerate(value):self.add(x,key+[i])
   return
  if isinstance(value,np.random.RandomState):self.row(key,'numpy.random.RandomState');self.add(value.get_state(),key+['state']);return
  if isinstance(value,np.random.Generator):self.row(key,'numpy.random.Generator');self.add(value.bit_generator.state,key+['state']);return
  if hasattr(value,'__dict__'):
   self.row(key,type(value).__module__+'.'+type(value).__qualname__);self.add(vars(value),key+['fields']);return
  row=dict(key=key,kind=type(value).__module__+'.'+type(value).__qualname__,representation=repr(value));self.records.append(row);self.opaque.append(row)
 def write(self,name):
  (self.out/name).write_bytes((json.dumps(dict(schema_version=2,snapshot_scope='independent-top-level-stage-values',snapshot_roots=self.snapshots,records=self.records,nonfinite_arrays=self.nonfinite,opaque_values=self.opaque,nonfinite_values_repaired=False),indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))
