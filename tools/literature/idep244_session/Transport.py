# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Owned Windows R process and actual loopback Shiny WebSocket message capture."""
from pathlib import Path
import base64
import importlib
import json
import os
import socket
import subprocess
import sys
import threading
import time

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.append(str(HERE / "legacy" if (HERE / "legacy").is_dir() else HERE.parent / "idep244"))
from Runtime import Job, Unavailable, environment, footprint, inventory, load, pin, save
from install_transport import VERSION, SHA256


def transport_check(path):
    path = Path(path).resolve()
    if not (path / "COMPLETED.json").is_file():
        raise Unavailable("Complete pinned WebSocket transport is missing: " + str(path))
    ready, done = load(path / "READY.json"), load(path / "COMPLETED.json")
    if (done != dict(status="PASS_PINNED_LOCAL_WEBSOCKET_CLIENT_SETUP_NOT_ANALYSIS", ready=pin(path / "READY.json"))
            or ready["schema"] != "omicsmatic-idep244-websocket-client-v1" or ready["version"] != VERSION
            or ready["python"] != sys.version or ready["executable"] != pin(sys.executable)
            or ready["wheel"]["sha256"] != SHA256 or not ready["site_files"]
            or inventory(path / "python-site") != ready["site_files"]):
        raise ValueError("Pinned transport records, Python or complete installed files differ")
    sys.path.insert(0, str(path / "python-site"))
    ws = importlib.import_module("websocket")
    if ws.__version__ != VERSION or not Path(ws.__file__).resolve().is_relative_to(path / "python-site"):
        raise ValueError("Another WebSocket installation was imported")
    return ws


def free_port():
    # Reservation is released before R binds. An intervening bind is an error,
    # never permission to contact an arbitrary nonmatching application.
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def launch_session(command, out, limits, port, token, websocket):
    out = Path(out)
    env = environment(out)
    process = None
    job = Job()
    stopped = threading.Event()
    observed = dict(connected=False, finished_received=False, acknowledgement_sent=False,
                    server_closed=False, error=None, messages=0, notifications=0)
    start = time.monotonic()
    deadline = start + limits["wall_seconds"]
    records = out / "websocket-messages.jsonl"

    def client():
        ws = None
        try:
            with records.open("xb", buffering=0) as log:
                def record(direction, opcode, raw):
                    if isinstance(raw, str):
                        raw = raw.encode("utf-8")
                    row = dict(direction=direction, opcode=opcode, bytes=len(raw),
                               payload_base64=base64.b64encode(raw).decode("ascii"))
                    log.write((json.dumps(row, sort_keys=True) + "\n").encode("ascii"))

                # Only retry refusal before a connection exists, never reconnect
                # a scientific run or replay an init after a broken connection.
                while not stopped.is_set() and time.monotonic() < deadline:
                    try:
                        ws = websocket.create_connection("ws://127.0.0.1:" + str(port) + "/websocket/",
                            timeout=0.5, origin="http://127.0.0.1:" + str(port),
                            http_no_proxy=["127.0.0.1", "localhost"], enable_multithread=True)
                        break
                    except (ConnectionRefusedError, socket.timeout):
                        stopped.wait(0.1)
                if ws is None:
                    raise TimeoutError("Original Shiny server did not become available")
                observed["connected"] = True
                ws.settimeout(0.5)
                message = json.dumps(dict(method="init", data={"start_token": token}), separators=(",", ":"))
                record("sent", 1, message)
                ws.send(message)
                while not stopped.is_set() and time.monotonic() < deadline:
                    try:
                        opcode, raw = ws.recv_data(control_frame=True)
                    except websocket.WebSocketTimeoutException:
                        continue
                    except websocket.WebSocketConnectionClosedException:
                        observed["server_closed"] = True
                        break
                    record("received", opcode, raw)
                    observed["messages"] += 1
                    if opcode == websocket.ABNF.OPCODE_CLOSE:
                        observed["server_closed"] = True
                        break
                    if opcode != websocket.ABNF.OPCODE_TEXT:
                        continue
                    value = json.loads(raw)
                    if "notification" in value:
                        observed["notifications"] += 1
                    done = value.get("custom", {}).get("omicsmatic_done")
                    if done is not None:
                        if done != {"token": token, "return_recorded": True} or observed["finished_received"]:
                            raise ValueError("Contradictory or duplicate session completion receipt")
                        observed["finished_received"] = True
                        ack = json.dumps(dict(method="update", data={"finish_ack": token}), separators=(",", ":"))
                        record("sent", 1, ack)
                        ws.send(ack)
                        observed["acknowledgement_sent"] = True
                if not observed["finished_received"] or not observed["acknowledgement_sent"]:
                    raise ValueError("No complete authenticated session completion/acknowledgement exchange")
        except BaseException as error:
            observed["error"] = repr(error)
        finally:
            if ws is not None:
                try:
                    ws.close(timeout=0.5)
                except Exception:
                    pass

    client_thread = None
    reason = None
    error = None
    code = None
    resumed = False
    try:
        with (out / "worker.stdout").open("xb") as so, (out / "worker.stderr").open("xb") as se:
            process = subprocess.Popen(command, cwd=out, env=env, stdin=subprocess.DEVNULL,
                stdout=so, stderr=se, creationflags=0x00000004 | 0x08000000)
            job.assign_and_resume(process)
            resumed = True
            client_thread = threading.Thread(target=client, name="owned-idep-shiny-client", daemon=True)
            client_thread.start()
            while process.poll() is None:
                count, size = footprint(out)
                if time.monotonic() >= deadline:
                    reason = "wall_seconds"
                elif count > limits["files"]:
                    reason = "files"
                elif size > limits["bundle_bytes"]:
                    reason = "bundle_bytes"
                elif observed["error"] is not None:
                    reason = "websocket_transport_failure"
                if reason:
                    job.terminate()
                    break
                time.sleep(0.1)
            code = process.wait(timeout=30)
    except BaseException as caught:
        error = caught
        if process is not None and process.poll() is None:
            job.terminate() if resumed else process.kill()
            code = process.wait(timeout=30)
    finally:
        job.close()
        if client_thread is not None:
            client_thread.join(timeout=2)
            stopped.set()
            client_thread.join(timeout=2)
    native_pid_file = out / "worker-rterm-pid.txt"
    native_pid = None
    try:
        if native_pid_file.is_file():
            native_pid = int(native_pid_file.read_text(encoding="ascii").strip())
            if native_pid <= 0:
                raise ValueError("Invalid native R process identity")
    except BaseException as caught:
        if error is None:
            error = caught
    result = dict(command=command, launcher_pid=process.pid if process else None, native_r_pid=native_pid, exit_code=code,
        elapsed_seconds=round(time.monotonic()-start, 6), limit_exceeded=reason,
        native_execution_resumed=resumed, owned_suspended_launcher_assigned_before_execution=True,
        descendant_job_closed=True, client_thread_stopped=client_thread is None or not client_thread.is_alive(),
        transport=observed, host="127.0.0.1", port=port, network_isolated=False,
        workspace_limits_are_monitored_not_OS_disk_quotas=True, requested_limits=limits,
        observer_or_launcher_error=repr(error) if error else None)
    save(out / "worker.process.json", result)
    if error is not None:
        raise error
    if reason or observed["error"] or not observed["finished_received"] or not observed["acknowledgement_sent"]:
        raise ValueError("Incomplete original Shiny session: " + str(reason or observed["error"]))
    if not result["client_thread_stopped"]:
        raise ValueError("Owned WebSocket observer did not finish")
    count, size = footprint(out)
    if count > limits["files"] or size > limits["bundle_bytes"]:
        raise ValueError("Session recording limit exceeded after process exit")
    return result
