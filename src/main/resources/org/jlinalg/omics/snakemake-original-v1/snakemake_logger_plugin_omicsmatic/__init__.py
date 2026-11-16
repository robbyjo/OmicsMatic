# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Project-owned recorder using the original Snakemake logger-plugin interface.

This package is loaded from the captured helper directory, not installed into or
represented as an upstream Snakemake distribution. It changes no DAG/job behavior.
"""
from pathlib import Path
import enum,json,math,os,threading,time,traceback
from snakemake_interface_logger_plugins.base import LogHandlerBase

def recorded(value,depth=0):
    if depth>30:return {'recorded_type':type(value).__name__,'repr':repr(value),'depth_limit':True}
    if value is None or isinstance(value,(str,int,bool)):return value
    if isinstance(value,float):return value if math.isfinite(value) else {'recorded_type':'nonfinite-float','repr':repr(value)}
    if isinstance(value,enum.Enum):return str(value)
    if isinstance(value,dict):return {str(key):recorded(item,depth+1) for key,item in value.items()}
    if isinstance(value,(list,tuple)):return [recorded(item,depth+1) for item in value]
    if isinstance(value,(set,frozenset)):return [recorded(item,depth+1) for item in sorted(value,key=repr)]
    return {'recorded_type':type(value).__module__+'.'+type(value).__name__,'repr':repr(value)}

class LogHandler(LogHandlerBase):
    writes_to_stream=False
    writes_to_file=True
    has_filter=True
    has_formatter=True
    needs_rulegraph=False

    def __post_init__(self):
        directory=Path(os.environ['OMICS_SNAKEMAKE_EVENTS']);assert directory.is_absolute() and directory.is_dir()
        self._destination=directory/('events-'+str(os.getpid())+'.jsonl')
        self.baseFilename=str(self._destination)
        self._stream=self._destination.open('x',encoding='utf-8',newline='\n')
        self._mutex=threading.RLock();self._sequence=0;self._finished=False;self._failed=False
        self._write(dict(record_type='collector_opened',collector_version=1,pid=os.getpid(),run_id=os.environ['OMICS_SNAKEMAKE_RUN_ID']))

    def _write(self,value):
        self._sequence+=1;value=dict(value,sequence=self._sequence)
        self._stream.write(json.dumps(value,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n');self._stream.flush()

    def emit(self,record):
        with self._mutex:
            try:
                self._write(dict(record_type='original_log_record',pid=os.getpid(),run_id=os.environ['OMICS_SNAKEMAKE_RUN_ID'],record=recorded(record.__dict__)))
            except Exception as failure:
                self._failed=True
                try:
                    path=self._destination.with_suffix('.FAILURE.json')
                    if not path.exists():path.write_text(json.dumps(dict(error=repr(failure),traceback=traceback.format_exc()))+'\n')
                finally:os.write(2,('FAIL2: Snakemake event capture failed: '+repr(failure)+'\n').encode())

    def close(self):
        if not hasattr(self,'_mutex'):return super().close()
        with self._mutex:
            if not self._finished:
                self._finished=True
                try:self._write(dict(record_type='collector_closed',pid=os.getpid(),run_id=os.environ['OMICS_SNAKEMAKE_RUN_ID'],collector_failed=self._failed))
                finally:self._stream.close()
        return super().close()
