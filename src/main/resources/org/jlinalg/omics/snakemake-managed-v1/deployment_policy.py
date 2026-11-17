#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Scoped installation permission checks at pinned upstream deployer entrypoints.

Permitted calls always execute the original function. No environment hash,
activation, solver, archive installation or container algorithm is replaced.
This is trusted-workflow policy instrumentation, not a security/network sandbox.
It is not yet connected to the supported Java analysis interface.
"""
from pathlib import Path
from contextlib import AbstractContextManager
from functools import wraps
import hashlib,os

PINS={'snakemake.deployment.conda':'ad43a8b87ed5c04739bda1233945e64291372718cb27e12b3bceed3325526798',
      'snakemake.deployment.singularity':'225a2de2b3e6e2b91a24de7e902f3542ae7471223147410ad2ea9bb81e30d80b'}
ACTION='SKIP3: installation is forbidden and the original deployer needs missing/incomplete software. Skip this dependent analysis; restore a complete qualified environment/image at the original selected location, or enable installation only where permitted and retry NEW output. Do not relocate an installed Conda prefix, silently substitute software or refresh historical hashes.'

class InstallationForbidden(Exception):
    """The original deployer would create/recreate an environment or pull an image."""

class InstallationIntegrityError(RuntimeError):
    """The scoped permission instrumentation changed during execution."""

class InstallationPolicy(AbstractContextManager):
    def __init__(self,allow_install):
        if type(allow_install) is not bool:raise ValueError('Require an explicit installation permission boolean')
        self.allow_install=allow_install;self.records=[];self.bindings=[];self.entered=False;self.closed=False
    @property
    def blocked(self):return any(row['blocked'] for row in self.records)
    def __enter__(self):
        if self.entered:raise RuntimeError('Installation policy contexts cannot be nested or reused')
        import snakemake.deployment.conda as conda
        import snakemake.deployment.singularity as singularity
        for module in (conda,singularity):
            if hashlib.sha256(Path(module.__file__).read_bytes()).hexdigest()!=PINS[module.__name__]:raise ValueError('Selected original deployer source differs: '+module.__name__)
        selected=[(conda.Env,'create',conda.Env.create),(singularity.Image,'pull',singularity.Image.pull)]
        for owner,name,original in selected:
            if original.__module__!=owner.__module__ or original.__qualname__!=owner.__name__+'.'+name or getattr(original,'__omics_installation_guard__',False):raise ValueError('Original deployer entrypoint has already been wrapped or substituted')
        self.entered=True
        for owner,name,original in selected:
            wrapped=self._wrap(owner,name,original,conda);setattr(owner,name,wrapped);self.bindings.append((owner,name,original,wrapped))
        return self
    def _wrap(self,owner,name,original,conda):
        @wraps(original)
        def guarded(target,*args,**kwargs):
            # The selected original functions have exactly one optional dryrun
            # argument. Do not invent a second argument convention.
            if len(args)>1 or set(kwargs)-{'dryrun'} or args and 'dryrun' in kwargs:raise TypeError('Unexpected selected deployer argument signature')
            dryrun=args[0] if args else kwargs.get('dryrun',False)
            if type(dryrun) is not bool:raise TypeError('Require original boolean dryrun declaration')
            if owner is conda.Env:
                external=target.is_externally_managed
                embedded=False if external else target.is_containerized
                address=None if external else str(target.address)
                marker=None if external or embedded else str(conda.get_env_setup_done_flag_file(Path(address)))
                # These are the unchanged source's exact existence predicates:
                # an existing prefix without its sibling setup flag is removed
                # and recreated; an archive still requires installation.
                needed=not (dryrun or external or embedded) and not (os.path.exists(address) and os.path.exists(marker))
                row=dict(kind='conda-environment',address=address,setup_done_flag=marker,externally_managed=external,embedded_container_environment=embedded,container_url=target.container_img_url if not external else None)
            else:
                address=str(target.path);needed=not (dryrun or target.is_local or os.path.exists(address))
                row=dict(kind='container-image',address=address,url=target.url,local=target.is_local)
            row.update(sequence=len(self.records)+1,allow_install=self.allow_install,dryrun=dryrun,installation_required=needed,blocked=needed and not self.allow_install,upstream_method_called=False,upstream_module=original.__module__,upstream_function=original.__qualname__,upstream_file_sha256=PINS[original.__module__])
            self.records.append(row)
            if row['blocked']:
                row['required_action']=ACTION
                raise InstallationForbidden(ACTION+' Missing/incomplete '+row['kind']+': '+str(address))
            row['upstream_method_called']=True
            try:
                result=original(target,*args,**kwargs);row['upstream_returned']=True;return result
            except BaseException as error:
                row['upstream_returned']=False;row['upstream_error_type']=type(error).__module__+'.'+type(error).__qualname__;raise
        guarded.__omics_installation_guard__=True
        return guarded
    def __exit__(self,kind,value,traceback):
        changed=[]
        for owner,name,original,wrapped in reversed(self.bindings):
            if getattr(owner,name) is not wrapped:changed.append(owner.__name__+'.'+name)
            setattr(owner,name,original)
        self.closed=True
        if changed:raise InstallationIntegrityError('Installation-policy binding changed during original execution: '+', '.join(changed))
        return False
