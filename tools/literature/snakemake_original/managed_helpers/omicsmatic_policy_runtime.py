# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Per-process original-deployer policy plus recursive original local-executor logging.

This instruments trusted local workflow execution; it is not a security,
filesystem-race, network or independent-program installation sandbox.
"""
from pathlib import Path
from functools import wraps
import atexit,hashlib,json,os,re,sys,threading,traceback
from deployment_policy import InstallationPolicy,InstallationIntegrityError

_ACTIVE=None
_BOOTSTRAP_LEASE=False
LOCAL_SOURCE_SHA256='c76bd06604c9d75ad3b079b75fbefa4cb89a507015df25414c1d1a9cd3b35d3e'

def fingerprint(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def fail_closed(path,error):
    value=dict(status='FAIL2_INSTALLATION_POLICY_INTEGRITY',pid=os.getpid(),error=repr(error),traceback=traceback.format_exc(),required_action='Skip dependent analysis, retain original inputs and all policy/event records, resolve the named discrepancy and retry NEW output. No deployment fallback or historical hash refresh is permitted.')
    if not path.exists():write(path,value)
    os.write(2,('FAIL2: installation policy integrity: '+repr(error)+'\n').encode())

class ProcessPolicy:
    def __init__(self,path):
        self.path=path;self.pin=fingerprint(path);self.pid=os.getpid();self.parent_pid=os.getppid();self.closed=False;self.references=0;self.spawns=[];self.lock=threading.RLock();self.original=None;self.wrapper=None;self.policy=None
        config=json.loads(path.read_bytes())
        if set(config)!={'schema_version','run_id','allow_install','records_directory','logger'} or type(config['schema_version']) is not int or config['schema_version']!=1 or type(config['allow_install']) is not bool or not re.fullmatch('[a-f0-9-]{36}',config['run_id']) or config['logger'] not in ['omicsmatic','omicsmanaged']:raise ValueError('Malformed explicit propagated installation policy')
        if config['run_id']!=os.environ['OMICS_SNAKEMAKE_RUN_ID']:raise InstallationIntegrityError('Propagated installation policy run differs from the event recorder')
        self.config=config;self.directory=Path(config['records_directory'])
        if not self.directory.is_absolute() or self.directory.resolve()!=self.directory or not self.directory.is_dir():raise ValueError('Require a canonical existing private policy-record directory')
        self.record=self.directory/('policy-'+str(self.pid)+'.json');self.failure=self.directory/('policy-'+str(self.pid)+'.FAILURE.json')
        from snakemake.executors import local
        self.owner=local.Executor
        if LOCAL_SOURCE_SHA256 is None or fingerprint(Path(local.__file__))['sha256']!=LOCAL_SOURCE_SHA256:raise InstallationIntegrityError('Selected original local-executor source differs')
        original=self.owner.additional_general_args
        if original.__module__!=local.__name__ or original.__qualname__!='Executor.additional_general_args' or getattr(original,'__omics_propagation__',False):raise InstallationIntegrityError('Original local-executor argument hook was already replaced')
        self.original=original;self.policy=InstallationPolicy(config['allow_install']);self.policy.__enter__()
        @wraps(original)
        def propagated(executor,*args,**kwargs):
            before=original(executor,*args,**kwargs)
            if type(before) is not str:raise InstallationIntegrityError('Original executor argument hook did not return text')
            after=before+' --logger '+config['logger']
            with self.lock:self.spawns.append(dict(sequence=len(self.spawns)+1,original_arguments=before,propagated_arguments=after,logger=config['logger']))
            return after
        propagated.__omics_propagation__=True;self.wrapper=propagated;self.owner.additional_general_args=propagated
        self.arguments=list(sys.argv);self.executable=sys.executable;self.working_directory=os.getcwd()
        write(self.directory/('policy-'+str(self.pid)+'.OPENED.json'),dict(schema_version=1,status='INSTALLATION_POLICY_OPENED_NOT_ANALYSIS',pid=self.pid,parent_pid=self.parent_pid,run_id=config['run_id'],config_file=str(path),config=self.pin,allow_install=config['allow_install'],local_executor_source_sha256=LOCAL_SOURCE_SHA256,arguments=self.arguments,executable=self.executable,working_directory=self.working_directory))
    def acquire(self):
        if self.pid!=os.getpid() or self.closed or fingerprint(self.path)!=self.pin:raise InstallationIntegrityError('Policy process, lifetime or configuration changed')
        self.references+=1;return self
    def release(self):
        if self.closed:return
        self.references-=1
        if self.references>0:return
        errors=[]
        try:
            if self.references!=0 or self.pid!=os.getpid() or fingerprint(self.path)!=self.pin:raise InstallationIntegrityError('Installation policy process, reference count or configuration changed')
            if self.owner.additional_general_args is not self.wrapper:raise InstallationIntegrityError('Original-executor propagation binding changed')
        except BaseException as error:errors.append(error)
        finally:
            self.owner.additional_general_args=self.original
            try:self.policy.__exit__(None,None,None)
            except BaseException as error:errors.append(error)
            self.closed=True
        if errors:
            error=InstallationIntegrityError('; '.join(repr(e) for e in errors));fail_closed(self.failure,error);raise error
        write(self.record,dict(schema_version=1,status='INSTALLATION_POLICY_CLOSED_AFTER_ORIGINAL_EXECUTION_NOT_ANALYSIS',pid=self.pid,parent_pid=self.parent_pid,run_id=self.config['run_id'],config_file=str(self.path),config=self.pin,allow_install=self.policy.allow_install,entered=self.policy.entered,closed=self.policy.closed,blocked=self.policy.blocked,records=self.policy.records,spawn_arguments=self.spawns,local_executor_source_sha256=LOCAL_SOURCE_SHA256,bindings_restored=True,arguments=self.arguments,executable=self.executable,working_directory=self.working_directory,user_analysis_admitted=False))

def acquire_policy():
    global _ACTIVE
    raw=os.environ.get('OMICS_SNAKEMAKE_INSTALL_POLICY')
    if raw is None:raise InstallationIntegrityError('Explicit propagated installation policy is missing; no implicit permission or fallback')
    path=Path(raw)
    if not path.is_absolute() or path.resolve()!=path or path.is_symlink() or not path.is_file() or path.stat().st_size>1_048_576:raise InstallationIntegrityError('Installation policy must be a canonical complete private JSON file')
    if _ACTIVE is None:_ACTIVE=ProcessPolicy(path)
    elif _ACTIVE.path!=path:raise InstallationIntegrityError('A process cannot change its installation policy')
    return _ACTIVE.acquire()

def bootstrap_policy():
    global _BOOTSTRAP_LEASE
    if not _BOOTSTRAP_LEASE:
        acquire_policy();_BOOTSTRAP_LEASE=True;atexit.register(finalize_policy)
    elif _ACTIVE.closed:raise InstallationIntegrityError('The process attempted to reuse a completed installation policy')

def finalize_policy():
    global _BOOTSTRAP_LEASE
    if _BOOTSTRAP_LEASE:
        try:_ACTIVE.release()
        finally:_BOOTSTRAP_LEASE=False
