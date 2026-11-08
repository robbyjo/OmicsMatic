# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Data-only content-addressed primitive arrays and lossless categorical frames."""
import hashlib,json
from pathlib import Path
import numpy as np
import pandas as pd

class Writer:
    def __init__(self,home,maximum_values):self.home=Path(home);self.objects=self.home/'objects';self.objects.mkdir();self.maximum_values=maximum_values
    def array(self,value):
        value=np.asarray(value);assert value.size<=self.maximum_values,'Complete original array capacity exceeded'
        if value.dtype.kind not in 'biuf':return dict(kind='object-array',dtype=str(value.dtype),shape=list(value.shape),values=self.clean(value.tolist()))
        dtype=value.dtype.newbyteorder('<');assert dtype.itemsize in [1,2,4,8];raw=np.ascontiguousarray(value.astype(dtype,copy=False)).tobytes(order='C');sha=hashlib.sha256(raw).hexdigest();path=self.objects/(sha+'.bin')
        if path.exists():assert path.read_bytes()==raw
        else:
            with path.open('xb') as stream:stream.write(raw)
        return dict(kind='binary-numeric-array',dtype=dtype.str,shape=list(value.shape),path='objects/'+path.name,bytes=len(raw),sha256=sha)
    def dtype(self,value):
        if isinstance(value,pd.CategoricalDtype):return dict(name=str(value),categories=self.clean(value.categories.tolist()),ordered=value.ordered)
        return dict(name=str(value))
    def clean(self,value):
        if value is pd.NA:return dict(kind='pandas-NA')
        if isinstance(value,pd.DataFrame):return dict(kind='dataframe',index=self.clean(value.index.tolist()),index_names=self.clean(value.index.names),columns=self.clean(value.columns.tolist()),column_names=self.clean(value.columns.names),dtypes=[self.dtype(d) for d in value.dtypes],column_data=[self.array(value.iloc[:,i].to_numpy()) for i in range(len(value.columns))])
        if isinstance(value,pd.Series):return dict(kind='series',index=self.clean(value.index.tolist()),name=self.clean(value.name),dtype=self.dtype(value.dtype),data=self.array(value.to_numpy()))
        if isinstance(value,pd.Index):return dict(kind='index',values=self.clean(value.tolist()))
        if isinstance(value,np.ndarray):return self.array(value)
        if isinstance(value,np.generic):return self.clean(value.item())
        if isinstance(value,float) and not np.isfinite(value):return dict(kind='nonfinite-scalar',value='NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf')
        if isinstance(value,dict):
            if all(isinstance(k,str) for k in value):return {k:self.clean(v) for k,v in value.items()}
            return dict(kind='ordered-mapping',entries=[[self.clean(k),self.clean(v)] for k,v in value.items()])
        if isinstance(value,(set,frozenset)):return dict(kind='unordered-set',values=[self.clean(v) for v in value])
        if isinstance(value,(list,tuple)):return [self.clean(v) for v in value]
        if value is None or isinstance(value,(bool,int,float,str)):return value
        raise TypeError('Uncaptured original data type: '+str(type(value)))
    def save(self,name,value):
        path=self.home/name;assert not path.exists()
        with path.open('xb') as stream:stream.write((json.dumps(self.clean(value),ensure_ascii=True,allow_nan=False,separators=(',',':'))+'\n').encode())
