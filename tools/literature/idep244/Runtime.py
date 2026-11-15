# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-pinned iDEP 2.4.4 runtime and complete recording utilities."""
from pathlib import Path
import hashlib
import json
import os
import platform
import stat
import subprocess
import sys
import time
import traceback
import uuid
from WinJob import Job

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ADOPTION = ["Runtime.py", "WinJob.py", "SourceAudit.R", "InspectRuntime.R",
            "adopt_runtime.py", "source-profile.json"]
ACTION = (
    "SKIP3 skips dependent analysis when the named exact permitted runtime, source, "
    "package, reference, Windows process-ownership facility or writable output is unavailable. "
    "Supply that permitted prerequisite and retry a NEW output path. Analysis installs and "
    "downloads nothing; absent reference prerequisites are checked before the original "
    "database loader can enter its automatic-download branch. Networking is NOT isolated "
    "by this Windows adapter. Only use trusted input files and reference databases. "
    "FAIL2 admits no complete result, including original character/NULL error returns, "
    "native crashes, limits and recording failure after computation. Preserve original "
    "inputs, raw consoles, complete RDS stage returns, conditions, RNG and all partials. "
    "Read FAILURE.json and WARNINGS.md, resolve the named cause, and retry NEW paths. "
    "Only final COMPLETED.json admits a recorded run; R exit zero or an RDS alone does not. "
    "The original 500-sample/500-comparison behavior and source identifier transformations "
    "remain unchanged. The returned generated R script is retained as text and NEVER run. "
    "User-selected current 2.4.4 is not the unidentified authors' iDEP.96 deployment. "
    "Whole-tool, author-environment, biological and whole-paper validity remain unqualified. "
    "This development adapter adds no catalog credit. The full250-paper goal remains open.")


class Unavailable(OSError):
    pass


def pin(path):
    h = hashlib.sha256()
    n = 0
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
            n += len(chunk)
    return dict(bytes=n, sha256=h.hexdigest())


def rawpin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def pairs(items):
    answer = {}
    for key, value in items:
        if key in answer:
            raise ValueError("Duplicate JSON key: " + key)
        answer[key] = value
    return answer


def load(path):
    def invalid(value):
        raise ValueError("Non-JSON constant: " + value)
    return json.loads(Path(path).read_text(encoding="utf-8"),
                      object_pairs_hook=pairs, parse_constant=invalid)


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, allow_nan=False, sort_keys=True,
                       indent=2) + "\n").encode("utf-8")


def save(path, value):
    # Never silently replace a previous record or promote through a partial JSON.
    with Path(path).open("xb") as stream:
        stream.write(encoded(value))


def linked(path):
    p = Path(path)
    return p.is_symlink() or (hasattr(p, "is_junction") and p.is_junction())


def walk_error(error):
    raise error


def inventory(root):
    root = Path(root)
    if not root.is_dir():
        raise Unavailable("Required complete directory is unavailable: " + str(root))
    if linked(root):
        raise ValueError("A complete regular directory is required: " + str(root))
    answer = {}
    folded = set()
    for parent, dirs, files in os.walk(root, followlinks=False, onerror=walk_error):
        for name in dirs + files:
            p = Path(parent) / name
            if linked(p):
                raise ValueError("Links/reparse directories are not accepted: " + str(p))
        for name in files:
            p = Path(parent) / name
            if not stat.S_ISREG(p.stat().st_mode):
                raise ValueError("Complete regular files are required: " + str(p))
            rel = p.relative_to(root).as_posix()
            if rel.casefold() in folded:
                raise ValueError("Case-colliding file identity: " + rel)
            folded.add(rel.casefold())
            answer[rel] = pin(p)
    return dict(sorted(answer.items()))


def new_output(path, protected=()):
    out = Path(path).absolute()
    if out != out.resolve():
        raise ValueError("Use a canonical NEW output path")
    for original in protected:
        p = Path(original).resolve()
        if out == p or out.is_relative_to(p) or p.is_relative_to(out):
            raise ValueError("Output overlaps protected input/source/runtime: " + str(p))
    if out.exists():
        raise FileExistsError("Preserve existing output and choose a NEW path: " + str(out))
    out.mkdir(parents=True, exist_ok=False)
    return out


def source_profile():
    value = load(HERE / "source-profile.json")
    if value["edition"] != "2.4.4" or value["commit"] != "60479c4375ea07bc930dc40b3c85230856f4ad50":
        raise ValueError("Exact current iDEP source profile is required")
    return value


def check_source(path):
    expected = {n: {k: v[k] for k in ["bytes", "sha256"]}
                for n, v in source_profile()["original_source_members"].items()}
    if len(expected) != 336 or inventory(path) != expected:
        raise ValueError("All336 original iDEP source members must match the selected commit")


def engine_sources():
    return {p.name: pin(p) for p in sorted(HERE.iterdir())
            if p.suffix in [".py", ".R", ".json"] and p.is_file()}


def adoption_sources():
    return {n: pin(HERE / n) for n in ADOPTION}


def environment(out):
    # A known caller environment, not an inferred author environment or sandbox.
    out = Path(out)
    env = {k: v for k, v in os.environ.items()
           if not k.upper().startswith(("R_", "RSTUDIO", "OMP_", "OPENBLAS_", "MKL_",
                                         "RCPP_", "LANG", "LC_"))}
    env.update(LANG="C", LC_ALL="C", TZ="UTC", R_DEFAULT_PACKAGES="datasets,utils,grDevices,graphics,stats,methods",
               R_ENVIRON_USER=str(out / "empty-environ"), R_PROFILE_USER=str(out / "empty-profile"),
               R_LIBS_USER=str(out / "empty-library"), R_LIBS_SITE=str(out / "empty-library"),
               HOME=str(out / "home"), R_USER=str(out / "home"),
               TMPDIR=str(out / "tmp"), TMP=str(out / "tmp"), TEMP=str(out / "tmp"),
               OMP_NUM_THREADS="1", OPENBLAS_NUM_THREADS="1", MKL_NUM_THREADS="1",
               RCPP_PARALLEL_NUM_THREADS="1", PYTHONDONTWRITEBYTECODE="1")
    for name in ["home", "tmp", "empty-library"]:
        (out / name).mkdir(exist_ok=True)
    for name in ["empty-environ", "empty-profile"]:
        p = out / name
        if not p.exists():
            p.write_bytes(b"")
    return env


def footprint(root):
    count = total = 0
    for parent, dirs, files in os.walk(root, followlinks=False, onerror=walk_error):
        for n in dirs + files:
            if linked(Path(parent) / n):
                raise ValueError("Output contains a link/reparse directory")
        for n in files:
            p = Path(parent) / n
            if p.is_file():
                total += p.stat().st_size
                count += 1
    return count, total


def launch(command, out, label, limits):
    """Bound time/recording space and own every R descendant before execution."""
    if os.name != "nt":
        raise Unavailable("The qualified installed runtime is Windows R4.6.1; Linux is not yet qualified")
    out = Path(out)
    env = environment(out)
    command = list(map(str, command))
    start = time.monotonic()
    reason = None
    process = None
    code = None
    caught = None
    job = Job()
    resumed = False
    try:
        with (out / (label + ".stdout")).open("xb") as so, (out / (label + ".stderr")).open("xb") as se:
            process = subprocess.Popen(command, cwd=out, env=env, stdin=subprocess.DEVNULL,
                                       stdout=so, stderr=se, creationflags=0x00000004 | 0x08000000)
            try:
                job.assign_and_resume(process)
                resumed = True
            except OSError as error:
                # The owned suspended launcher cannot have spawned an R child.
                process.kill()
                process.wait(timeout=30)
                raise Unavailable("Cannot assign/resume the owned Windows job: " + str(error)) from error
            while process.poll() is None:
                elapsed = time.monotonic() - start
                count, size = footprint(out)
                if elapsed > limits["wall_seconds"]:
                    reason = "wall_seconds"
                elif count > limits["files"]:
                    reason = "files"
                elif size > limits["bundle_bytes"]:
                    reason = "bundle_bytes"
                if reason:
                    job.terminate()
                    break
                time.sleep(0.2)
            code = process.wait(timeout=30)
    except BaseException as error:
        caught = error
        if process is not None and process.poll() is None:
            job.terminate() if resumed else process.kill()
            code = process.wait(timeout=30)
    finally:
        job.close()
    try:
        count, size = footprint(out)
    except BaseException as error:
        if caught is None:
            caught = error
        count = size = None
    if reason is None and count is not None and (count > limits["files"] or size > limits["bundle_bytes"]):
        reason = "files" if count > limits["files"] else "bundle_bytes"
    result = dict(command=command, launcher_pid=process.pid if process else None, exit_code=code,
                  elapsed_seconds=round(time.monotonic() - start, 6),
                  limit_exceeded=reason, owned_suspended_launcher_assigned_before_execution=True,
                  native_execution_resumed=resumed, requested_limits=limits,
                  observed_files=count, observed_bytes=size, observer_or_launcher_error=repr(caught) if caught else None,
                  descendant_job_closed=True, network_isolated=False,
                  workspace_limits_are_monitored_not_OS_disk_quotas=True,
                  OS=platform.platform(), Python=sys.version)
    save(out / (label + ".process.json"), result)
    if caught is not None:
        raise caught
    if reason:
        raise ValueError("Recording/time limit exceeded: " + reason + "; retain all partials")
    return result


def provider_check(path):
    path = Path(path).resolve()
    if not (path / "READY.json").is_file() or not (path / "COMPLETED.json").is_file():
        raise Unavailable("Complete adopted iDEP provider is unavailable: " + str(path))
    ready = load(path / "READY.json")
    done = load(path / "COMPLETED.json")
    if ready.get("schema") != "omicsmatic-idep244-windows-r461-provider-v1":
        raise ValueError("Exact iDEP2.4.4 provider schema required")
    if done.get("status") != "PASS_ORIGINAL_IDEP244_RUNTIME_AUDIT_NOT_ANALYSIS" or done["ready"] != pin(path / "READY.json"):
        raise ValueError("Provider completion does not admit the exact READY file")
    if ready["adoption_sources"] != adoption_sources():
        raise ValueError("Runtime adoption code/profile changed; preserve this provider and audit a NEW provider")
    if ready["runtime_index"] != pin(path / "runtime-files.json"):
        raise ValueError("Complete runtime index differs")
    check_source(ready["source"])
    records = load(path / "runtime-files.json")
    for root, expected in records.items():
        if inventory(root) != expected:
            raise ValueError("Protected installed runtime changed: " + root)
    return ready


def warning(out):
    (Path(out) / "WARNINGS.md").write_text("# Warnings and required actions\n\n" + ACTION + "\n",
                                        encoding="utf-8", newline="\n")


def attempted(out, label):
    if out is None:
        return False
    path = Path(out) / (label + ".process.json")
    if path.is_file():
        try:
            return load(path)["native_execution_resumed"] is True
        except BaseException:
            return True
    return (Path(out) / (label + ".stdout")).exists()


def failure(out, error, context, analysis_started=False):
    code = 3 if (not analysis_started and isinstance(error, OSError)
                 and not isinstance(error, FileExistsError)) else 2
    label = ("SKIP3_IDEP244_PREREQUISITE_UNAVAILABLE" if code == 3
             else "FAIL2_IDEP244_NO_COMPLETE_RESULT")
    record = dict(status=label, cause=repr(error), context=context,
                  traceback=traceback.format_exc(), required_action=ACTION,
                  original_process_execution_attempted=analysis_started,
                  analysis_admitted=False, catalog_credit_added=0)
    if out is not None:
        try:
            out = Path(out)
            if (out / "COMPLETED.json").exists():
                # A later failure must not leave an apparently admitted result.
                (out / "COMPLETED.json").rename(out / ("COMPLETION-INVALIDATED-" + uuid.uuid4().hex + ".json"))
            save(out / "FAILURE.json", record)
            with (out / "WARNINGS.md").open("a", encoding="utf-8", newline="\n") as f:
                f.write("\n# " + label + "\n\n" + str(error) + "\n\n" + ACTION + "\n")
        except BaseException as recording:
            code = 2
            print("FAIL2_RECORDING_UNAVAILABLE", repr(recording), flush=True)
    print(label, repr(error), "\n" + ACTION, flush=True)
    return code
