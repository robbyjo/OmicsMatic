#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real owned R children, limits and an unrelated protected R process."""
from pathlib import Path
import argparse
import ctypes
from ctypes import wintypes
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "idep244"))
from Runtime import launch, new_output, save, environment, load


def alive(pid):
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    k.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    k.OpenProcess.restype = wintypes.HANDLE
    k.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    k.GetExitCodeProcess.restype = wintypes.BOOL
    k.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = k.OpenProcess(0x1000, False, pid)
    if not handle:
        if ctypes.get_last_error() == 87:
            return False
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        status = wintypes.DWORD()
        if not k.GetExitCodeProcess(handle, ctypes.byref(status)):
            raise ctypes.WinError(ctypes.get_last_error())
        return status.value == 259
    finally:
        k.CloseHandle(handle)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--rscript", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    a = p.parse_args()
    out = new_output(a.out)
    unrelated = out / "unrelated"
    unrelated.mkdir()
    command = [str(a.rscript), "--vanilla", "-e",
               "cat(Sys.getpid(),'\\n');flush(stdout());Sys.sleep(7);cat('UNRELATED_COMPLETED\\n')"]
    other = subprocess.Popen(command, cwd=unrelated, env=environment(unrelated),
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, stdin=subprocess.DEVNULL)
    first = other.stdout.readline()
    other_pid = int(first.strip())
    assert alive(other_pid)
    controls = [
        ("wall", "wall_seconds", "cat(Sys.getpid(),'\\n');flush(stdout());Sys.sleep(60)",
         dict(wall_seconds=2, files=100, bundle_bytes=16*1024*1024)),
        ("bytes", "bundle_bytes",
         "cat(Sys.getpid(),'\\n');flush(stdout());writeBin(raw(2097152),'complete-grown.bin');Sys.sleep(60)",
         dict(wall_seconds=30, files=100, bundle_bytes=256*1024)),
        ("files", "files",
         "cat(Sys.getpid(),'\\n');flush(stdout());for(i in 1:50)file.create(paste0('file',i));Sys.sleep(60)",
         dict(wall_seconds=30, files=20, bundle_bytes=16*1024*1024))]
    results = []
    for label, expected, expression, limits in controls:
        case = out / label
        case.mkdir()
        try:
            launch([a.rscript, "--vanilla", "-e", expression], case, "owned", limits)
        except ValueError as error:
            process = load(case / "owned.process.json")
            assert process["limit_exceeded"] == expected, process
            assert process["owned_suspended_launcher_assigned_before_execution"]
            child = int((case / "owned.stdout").read_text().split()[0])
            assert not alive(child), child
            if label == "wall":
                assert other.poll() is None and alive(other_pid)
            results.append(dict(case=label, expected_limit=expected, cause=str(error),
                                launcher=process["launcher_pid"], R_child=child,
                                owned_R_child_terminal=True))
        else:
            raise AssertionError("Owned limit did not refuse: " + label)
    stdout, stderr = other.communicate(timeout=20)
    (unrelated / "complete.stdout").write_bytes(first + stdout)
    (unrelated / "complete.stderr").write_bytes(stderr)
    assert other.returncode == 0 and b"UNRELATED_COMPLETED" in stdout
    proof = dict(status="PASS_ACTUAL_WINDOWS_R_PROCESS_OWNERSHIP_AND_THREE_LIMITS",
                 controls=results, unrelated_R_child=other_pid,
                 unrelated_process_completed_normally=True, source_algorithms_executed=False)
    save(out / "COMPLETED.json", proof)
    print(proof["status"])


if __name__ == "__main__":
    main()
