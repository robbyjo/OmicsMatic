# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Lossless column-oriented frames and binary numeric arrays. No executable serialization or object reconstruction."""
import base64,json
from pathlib import Path
import numpy as np
import pandas as pd


def array(value):
    value=np.asarray(value)
    if value.dtype.kind in 'biuf' and value.dtype.itemsize in [1,2,4,8]:
        dtype=value.dtype.newbyteorder('<');raw=np.ascontiguousarray(value.astype(dtype,copy=False)).tobytes(order='C');return dict(kind='numeric-array',dtype=dtype.str,shape=list(value.shape),base64=base64.b64encode(raw).decode('ascii'))
    return dict(kind='object-array',dtype=str(value.dtype),shape=list(value.shape),values=clean(value.tolist()))


def clean(value):
    if value is pd.NA:return {'kind':'pandas-NA'}
    if isinstance(value,pd.DataFrame):return dict(kind='dataframe',index=clean(value.index.to_list()),index_names=clean(value.index.names),columns=clean(value.columns.to_list()),column_names=clean(value.columns.names),dtypes=[str(d) for d in value.dtypes],column_data=[array(value.iloc[:,i].to_numpy()) for i in range(len(value.columns))])
    if isinstance(value,pd.Series):return dict(kind='series',index=clean(value.index.to_list()),name=clean(value.name),dtype=str(value.dtype),data=array(value.to_numpy()))
    if isinstance(value,pd.Index):return dict(kind='index',dtype=str(value.dtype),name=clean(value.name),values=clean(value.to_list()))
    if isinstance(value,np.ndarray):return array(value)
    if isinstance(value,np.generic):return clean(value.item())
    if isinstance(value,float) and not np.isfinite(value):return dict(kind='nonfinite-scalar',value='NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf')
    if isinstance(value,dict):
        if all(isinstance(k,str) for k in value):return {k:clean(v) for k,v in value.items()}
        return dict(kind='ordered-mapping',entries=[[clean(k),clean(v)] for k,v in value.items()])
    if isinstance(value,(list,tuple)):return [clean(v) for v in value]
    if isinstance(value,type):return dict(kind='python-class',name=value.__module__+'.'+value.__qualname__)
    if value is None or isinstance(value,(bool,int,float,str)):return value
    raise TypeError('Uncaptured original value type: '+str(type(value)))


def save(path,value):Path(path).write_text(json.dumps(value,ensure_ascii=True,separators=(',',':'),allow_nan=False)+'\n',encoding='utf8',newline='\n')
