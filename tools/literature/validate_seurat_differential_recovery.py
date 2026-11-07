# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real missing-runtime, capacity and owned-worker timeout recovery controls."""
import argparse
import ctypes
from ctypes import wintypes
import json
import subprocess
import time
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_deseq2_inference import JAVA21

BASE = ROOT / 'build/literature-review/signac-runtime'


class ObservedChildren:
    """Read-only Windows process observations with retained handles against PID reuse."""
    def __init__(self, parent):
        self.parent = parent
        self.owned = {}
        self.k = ctypes.WinDLL('kernel32', use_last_error=True)
        self.k.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
        self.k.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
        self.k.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        self.k.OpenProcess.restype = wintypes.HANDLE
        self.k.CloseHandle.argtypes = [wintypes.HANDLE]
        self.k.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
        self.k.GetProcessTimes.argtypes = [wintypes.HANDLE, *([ctypes.POINTER(wintypes.FILETIME)] * 4)]

    def sample(self):
        class Entry(ctypes.Structure):
            _fields_ = [('dwSize', wintypes.DWORD), ('cntUsage', wintypes.DWORD),
                        ('th32ProcessID', wintypes.DWORD), ('th32DefaultHeapID', ctypes.c_size_t),
                        ('th32ModuleID', wintypes.DWORD), ('cntThreads', wintypes.DWORD),
                        ('th32ParentProcessID', wintypes.DWORD), ('pcPriClassBase', wintypes.LONG),
                        ('dwFlags', wintypes.DWORD), ('szExeFile', wintypes.WCHAR * 260)]
        self.k.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(Entry)]
        self.k.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(Entry)]
        snapshot = self.k.CreateToolhelp32Snapshot(2, 0)
        if snapshot == ctypes.c_void_p(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())
        rows = []
        try:
            entry = Entry()
            entry.dwSize = ctypes.sizeof(Entry)
            more = self.k.Process32FirstW(snapshot, ctypes.byref(entry))
            while more:
                rows.append((entry.th32ProcessID, entry.th32ParentProcessID, entry.szExeFile))
                more = self.k.Process32NextW(snapshot, ctypes.byref(entry))
        finally:
            self.k.CloseHandle(snapshot)
        parents = {self.parent, *self.owned}
        changed = True
        while changed:
            changed = False
            for pid, parent, name in rows:
                if parent not in parents or pid in parents:
                    continue
                handle = self.k.OpenProcess(0x1000, False, pid)
                if not handle:
                    continue  # A process that exited before observation is not claimed as observed.
                created, exited, kernel, user = (wintypes.FILETIME() for _ in range(4))
                if not self.k.GetProcessTimes(handle, ctypes.byref(created), ctypes.byref(exited),
                                              ctypes.byref(kernel), ctypes.byref(user)):
                    self.k.CloseHandle(handle)
                    raise ctypes.WinError(ctypes.get_last_error())
                self.owned[pid] = (handle, dict(pid=pid, parent_pid=parent, executable=name,
                                              creation_filetime=(created.dwHighDateTime << 32) | created.dwLowDateTime))
                parents.add(pid)
                changed = True

    def finish(self):
        result = []
        for handle, row in self.owned.values():
            code = wintypes.DWORD()
            try:
                if not self.k.GetExitCodeProcess(handle, ctypes.byref(code)):
                    raise ctypes.WinError(ctypes.get_last_error())
                result.append(dict(row, alive_after_cleanup=code.value == 259, observed_exit_code=code.value))
            finally:
                self.k.CloseHandle(handle)
        self.owned.clear()
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    source = BASE / 'differential-extended-requests-1/pair_sampled_wilcox.json'
    prototype = json.loads(source.read_bytes())
    jar = BASE / 'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
    artifact = pin(jar)
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), source]}
    empty = out / 'empty-private-library'
    empty.mkdir()

    def command(label, request, expected, observe=False):
        path = out / (label + '.json')
        save(path, request)
        argv = list(map(str, [JAVA21, '-Xmx768m', '-cp', jar,
                              'org.jlinalg.cli.SeuratDifferentialCli', '--request', path]))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
            observation = ObservedChildren(process.pid) if observe else None
            deadline = time.monotonic() + 240
            try:
                while process.poll() is None:
                    if observation:
                        observation.sample()
                    if time.monotonic() > deadline:
                        raise TimeoutError('Recovery validation driver deadline exceeded; inspect owned Java PID ' + str(process.pid))
                    time.sleep(.1)
            finally:
                # This observer never kills a process; the Java runtime owns worker cleanup.
                children = observation.finish() if observation else []
        save(out / (label + '.process.json'), dict(command=argv, exit_code=process.returncode,
                                                 expected_exit_code=expected, observed_children=children))
        assert process.returncode == expected, label + ' failed; inspect logs'
        return children

    controls = [
        ('missing-rscript', dict(rscript=str(out / 'absent-Rscript.exe')), 3, 'Declared Rscript'),
        ('missing-library', dict(r_libraries=[str(out / 'absent-private-library')]), 3, 'Declared R library'),
        ('missing-package', dict(r_libraries=[str(empty)]), 3, 'cannot load jsonlite'),
        ('dense-capacity', dict(densify=True, maximum_dense_entries=1), 2, 'maximum_dense_entries'),
        ('summary-work-capacity', dict(maximum_work=1), 2, 'maximum_work'),
        ('marker-row-capacity', dict(maximum_result_rows=1), 2, 'maximum_result_rows'),
        ('worker-output-capacity', dict(maximum_output_bytes=1), 2, 'maximum_output_bytes'),
        ('worker-timeout', dict(timeout_seconds=1), 2, 'timed out')]
    runs = []
    try:
        for name, settings, code, message in controls:
            target = out / (name + '-analysis')
            request = dict(prototype, out=str(target), **settings)
            children = command(name, request, code, name == 'worker-timeout')
            state = json.loads((target / 'analysis-status.json').read_bytes())
            assert state['results_admitted'] is False and message in state['error'], (name, state)
            text = (target / 'WARNINGS.md').read_text(encoding='utf8')
            assert ('SKIP3' if code == 3 else 'FAIL2') in text and 'Required action:' in text
            command(name + '-verify', dict(schema_version=1, mode='VERIFY', analysis=target.name), 2)
            if name == 'worker-timeout':
                manifest = json.loads((target / 'manifest.json').read_bytes())
                assert children and all(not p['alive_after_cleanup'] for p in children)
                assert manifest['owned_worker_pid'] in {p['pid'] for p in children}
                assert manifest['owned_process_cleanup'] and all(not p['alive_after_cleanup'] for p in manifest['owned_process_cleanup'])
            if name == 'missing-rscript':
                pins = {p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}
                command(name + '-refuse-existing-output', request, 2)
                assert pins == {p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}
            runs.append(dict(control=name, expected_exit_code=code, state=state, observed_children=children,
                             files={p.relative_to(target).as_posix(): pin(p) for p in target.rglob('*') if p.is_file()}))
            print(name + ' retained explicit recovery and refused result admission', flush=True)
        assert pin(jar) == artifact and all(pin(ROOT / n) == value for n, value in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-eight-real-Seurat-recovery-controls',
                                         artifact=artifact, source_pins=sources, controls=runs,
                                         failed_or_skipped_capture_verifications_refused=8,
                                         existing_output_preserved=True,
                                         external_timeout_observer_scope='Only observed descendants; retained Windows process handles prevent PID-reuse confusion. No unrelated process terminated.',
                                         partial_tables_admitted=False))
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_SEURAT_RECOVERY_CONTROLS', error=str(error)))
        raise


if __name__ == '__main__':
    main()
