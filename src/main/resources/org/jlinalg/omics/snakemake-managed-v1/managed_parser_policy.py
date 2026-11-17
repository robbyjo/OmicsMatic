#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read effective original parser settings after workflow-profile reparse; never infer them from argv."""
import json
import original_driver as base

CONTROLLED=(*base.CONTROLLED,'software_deployment_method','conda_prefix','apptainer_prefix','apptainer_args','conda_frontend')
DISABLED={key:value for key,value in base.DISABLED.items() if key!='software_deployment_method'}
METHODS={'conda':['CONDA'],'apptainer':['APPTAINER'],'conda-apptainer':['APPTAINER','CONDA']}

def effective_policy(args):return {key:base.recorded(getattr(args,key)) for key in (*CONTROLLED,*DISABLED)}

def validate_policy(policy,effective):
    if not isinstance(policy,dict) or set(policy)!={'schema_version','deployment','allow_install','expected'} or type(policy['schema_version']) is not int or policy['schema_version']!=1 or type(policy['allow_install']) is not bool or policy['deployment'] not in METHODS:raise ValueError('Malformed explicit managed execution policy')
    expected=policy['expected']
    if not isinstance(expected,dict) or set(expected)!=set(CONTROLLED) or expected['software_deployment_method']!=METHODS[policy['deployment']] or expected['logger']!=['omicsmanaged'] or expected['executor']!='local' or expected['conda_frontend']!='conda' or expected['forceall'] is not True or expected['keep_incomplete'] is not True:raise ValueError('Incomplete or unqualified managed recipe/parser policy')
    for key,value in {**expected,**DISABLED}.items():
        if json.dumps(effective[key],sort_keys=True,allow_nan=False)!=json.dumps(value,sort_keys=True,allow_nan=False):
            raise base.PolicyUnavailable('Effective original managed parser setting '+key+'='+repr(effective[key])+' conflicts with captured analysis policy '+repr(value)+'. No original API/workflow was called. Remove the conflicting profile setting or request a separately qualified mode; retain this skip and retry NEW output.')
