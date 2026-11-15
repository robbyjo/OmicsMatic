# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Own an initially suspended Windows launcher and every descendant.

The launcher cannot create its R child before assignment to the job. The job
does not allow breakaway. Closing the sole job handle kills any remaining child.
This is process ownership, not filesystem/network isolation or a memory quota.
Win32 contracts: CreateProcess flags, THREADENTRY32, ResumeThread, and
JOBOBJECT_EXTENDED_LIMIT_INFORMATION at learn.microsoft.com/windows/win32/.
"""
import ctypes as C
from ctypes import wintypes as W
import os


class Job:
    def __init__(self):
        if os.name != "nt":
            raise OSError("This provider currently requires Windows job objects")
        self.k = C.WinDLL("kernel32", use_last_error=True)
        k = self.k
        k.CreateJobObjectW.argtypes = [C.c_void_p, W.LPCWSTR]
        k.CreateJobObjectW.restype = W.HANDLE
        k.SetInformationJobObject.argtypes = [W.HANDLE, C.c_int, C.c_void_p, W.DWORD]
        k.SetInformationJobObject.restype = W.BOOL
        k.AssignProcessToJobObject.argtypes = [W.HANDLE, W.HANDLE]
        k.AssignProcessToJobObject.restype = W.BOOL
        k.TerminateJobObject.argtypes = [W.HANDLE, W.UINT]
        k.TerminateJobObject.restype = W.BOOL
        k.CloseHandle.argtypes = [W.HANDLE]
        k.CloseHandle.restype = W.BOOL
        k.CreateToolhelp32Snapshot.argtypes = [W.DWORD, W.DWORD]
        k.CreateToolhelp32Snapshot.restype = W.HANDLE
        k.OpenThread.argtypes = [W.DWORD, W.BOOL, W.DWORD]
        k.OpenThread.restype = W.HANDLE
        k.ResumeThread.argtypes = [W.HANDLE]
        k.ResumeThread.restype = W.DWORD

        class Basic(C.Structure):
            _fields_ = [
                ("process_time", C.c_int64), ("job_time", C.c_int64),
                ("flags", W.DWORD), ("min_working_set", C.c_size_t),
                ("max_working_set", C.c_size_t), ("active_limit", W.DWORD),
                ("affinity", C.c_size_t), ("priority", W.DWORD),
                ("scheduling", W.DWORD)]

        class Extended(C.Structure):
            _fields_ = [("basic", Basic), ("io", C.c_uint64 * 6),
                        ("process_memory", C.c_size_t), ("job_memory", C.c_size_t),
                        ("peak_process_memory", C.c_size_t), ("peak_job_memory", C.c_size_t)]

        self.handle = k.CreateJobObjectW(None, None)
        if not self.handle:
            raise C.WinError(C.get_last_error())
        info = Extended()
        info.basic.flags = 0x00002000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not k.SetInformationJobObject(self.handle, 9, C.byref(info), C.sizeof(info)):
            error = C.WinError(C.get_last_error())
            self.close()
            raise error

    def assign_and_resume(self, process):
        if not self.k.AssignProcessToJobObject(self.handle, W.HANDLE(int(process._handle))):
            raise C.WinError(C.get_last_error())

        class Thread(C.Structure):
            _fields_ = [(n, W.DWORD) for n in ["size", "usage", "id", "owner"]]
            _fields_ += [("base_priority", W.LONG), ("delta_priority", W.LONG),
                         ("flags", W.DWORD)]

        k = self.k
        k.Thread32First.argtypes = [W.HANDLE, C.POINTER(Thread)]
        k.Thread32First.restype = W.BOOL
        k.Thread32Next.argtypes = [W.HANDLE, C.POINTER(Thread)]
        k.Thread32Next.restype = W.BOOL
        snapshot = k.CreateToolhelp32Snapshot(4, 0)  # TH32CS_SNAPTHREAD
        if snapshot == C.c_void_p(-1).value:
            raise C.WinError(C.get_last_error())
        matches = []
        try:
            entry = Thread()
            entry.size = C.sizeof(entry)
            ok = k.Thread32First(snapshot, C.byref(entry))
            while ok:
                if entry.owner == process.pid:
                    matches.append(entry.id)
                ok = k.Thread32Next(snapshot, C.byref(entry))
        finally:
            k.CloseHandle(snapshot)
        if len(matches) != 1:
            raise OSError("Suspended launcher must have exactly one owned primary thread")
        thread = k.OpenThread(0x0002, False, matches[0])  # THREAD_SUSPEND_RESUME
        if not thread:
            raise C.WinError(C.get_last_error())
        try:
            if k.ResumeThread(thread) != 1:
                raise OSError("Owned primary thread was not suspended exactly once")
        finally:
            k.CloseHandle(thread)

    def terminate(self):
        if self.handle and not self.k.TerminateJobObject(self.handle, 2):
            raise C.WinError(C.get_last_error())

    def close(self):
        if self.handle:
            self.k.CloseHandle(self.handle)
            self.handle = None
