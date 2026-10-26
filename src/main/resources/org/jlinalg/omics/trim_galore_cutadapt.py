"""Stream an actual Cutadapt process to legacy POSIX Perl with LF line endings.

The original argv, binary stdout/stderr, LF-normalized stdout, return code and
hashes are retained. No FASTQ bases, qualities, headers or algorithm options are
changed here. The caller must provide a fixed, fingerprinted executable.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cutadapt", type=Path, required=True)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--audit-dir", type=Path, required=True)
    parser.add_argument("arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    arguments = args.arguments[1:] if args.arguments[:1] == ["--"] else args.arguments
    if not arguments or not args.cutadapt.is_file() or sha(args.cutadapt) != args.expected_sha256:
        print("ERROR: Cutadapt transport dependency is missing or changed; supply the verified runtime and retry. Analysis skipped.", file=sys.stderr)
        return 3
    args.audit_dir.mkdir(parents=True, exist_ok=True)
    record_path = None
    for index in range(1000):
        try:
            record_path = args.audit_dir / ("invocation-%03d.json" % index)
            descriptor = os.open(record_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            os.close(descriptor)
            break
        except FileExistsError:
            record_path = None
    if record_path is None:
        print("ERROR: Cutadapt transport invocation limit exceeded; inspect retained audit files.", file=sys.stderr)
        return 2
    base = record_path.with_suffix("")
    raw_path = base.with_suffix(".stdout-raw")
    normalized_path = base.with_suffix(".stdout-lf")
    error_path = base.with_suffix(".stderr-raw")
    command = [str(args.cutadapt.resolve()), *arguments]
    error = None
    exit_code = 2
    try:
        with raw_path.open("xb") as raw, normalized_path.open("xb") as normalized, error_path.open("xb") as stderr:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=stderr)
            pending = b""
            try:
                for chunk in iter(lambda: process.stdout.read(65536), b""):
                    raw.write(chunk)
                    chunk = pending + chunk
                    pending = b"\r" if chunk.endswith(b"\r") else b""
                    if pending:
                        chunk = chunk[:-1]
                    data = chunk.replace(b"\r\n", b"\n")
                    normalized.write(data)
                    sys.stdout.buffer.write(data)
                if pending:
                    normalized.write(pending)
                    sys.stdout.buffer.write(pending)
                sys.stdout.buffer.flush()
            except BaseException:
                process.kill()
                process.wait()
                raise
            finally:
                process.stdout.close()
            exit_code = process.wait()
        # Legacy Perl drains stdout before stderr. Spooling stderr prevents a
        # pipe deadlock while retaining its exact bytes and the child exit code.
        with error_path.open("rb") as stderr:
            for chunk in iter(lambda: stderr.read(65536), b""):
                sys.stderr.buffer.write(chunk)
        sys.stderr.buffer.flush()
    except Exception as failure:
        error = repr(failure)
        print("ERROR: Cutadapt transport failed: " + error + ". Inspect retained files before retrying.", file=sys.stderr)
    finally:
        record = dict(command=command, exit_code=exit_code, error=error,
                      executable_sha256=sha(args.cutadapt) if args.cutadapt.is_file() else None,
                      files={p.name: sha(p) for p in [raw_path, normalized_path, error_path] if p.is_file()},
                      transformation="Only stdout CRLF pairs become LF; stderr bytes and Cutadapt options are unchanged.")
        record_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
