# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact installed-source and complete locked-distribution import health; no analysis or original function replacement."""
import hashlib,importlib.metadata as metadata,inspect,json,sys
from pathlib import Path

kit=Path(sys.argv[1]);out=Path(sys.argv[2]);profile=json.loads((kit/'profiles.json').read_bytes());locks=json.loads((kit/'setup/dependency-locks.json').read_bytes());assert tuple(sys.version_info[:3])==tuple(locks['python_version']);versions={name:metadata.version(name) for name in locks['python_versions']};assert versions==locks['python_versions']
import numpy,pandas,scipy,sklearn,numpy_groupies,scanpy,geosketch,fbpca
import cellphonedb
from cellphonedb.src.core.methods import cpdb_analysis_method,cpdb_statistical_analysis_method,cpdb_degs_analysis_method,cpdb_statistical_analysis_helper
from cellphonedb.utils import scoring_utils
site=Path(cellphonedb.__file__).resolve().parent.parent;checked={}
for name,want in profile['original_members'].items():
    if not name.startswith('cellphonedb/'):continue
    path=site/name;raw=path.read_bytes();actual=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert actual==want,'Original installed CellPhoneDB source differs: '+name;checked[name]=actual
modules=[cpdb_analysis_method,cpdb_statistical_analysis_method,cpdb_degs_analysis_method,cpdb_statistical_analysis_helper,scoring_utils];functions={}
for module in modules:
    rows=[]
    for name,function in inspect.getmembers(module,inspect.isfunction):
        if function.__module__!=module.__name__:continue
        assert Path(function.__code__.co_filename).resolve()==Path(module.__file__).resolve() and function.__name__==name;rows.append(name)
    functions[module.__name__]=rows
record=dict(status='PASS_ORIGINAL_CELLPHONEDB_NAMESPACE_ONLY',CellPhoneDB_version=metadata.version('cellphonedb'),python_version=list(sys.version_info[:3]),python_distributions=versions,original_source_members=checked,namespace_functions=functions,namespace_patched=False,analysis_executed=False,original_UserWarning_filter_retained=True)
out.write_text(json.dumps(record,indent=2)+'\n',encoding='utf8',newline='\n');print('PASS_ORIGINAL_CELLPHONEDB_NAMESPACE_ONLY')
