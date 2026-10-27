"""Packaged private tool installer. Invoked by JLinAlg, not an analysis engine."""
import argparse
import email.parser
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
from datetime import datetime, timezone
import urllib.error
import urllib.parse
import urllib.request
import venv
import zipfile

MARKER = ".jlinalg-variant-tool.json"


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1048576), b""):
            digest.update(block)
    return digest.hexdigest()


def save(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


def download(url, target, maximum, hosts):
    def check(value):
        parsed = urllib.parse.urlsplit(value)
        if parsed.scheme != "https" or parsed.hostname not in hosts or parsed.username or parsed.password or parsed.fragment or parsed.port not in (None, 443):
            raise ValueError("Download requires an authorised official HTTPS host")
    check(url)

    class Redirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, request, response, code, message, headers, newurl):
            check(newurl)
            return super().redirect_request(request, response, code, message, headers, newurl)

    request = urllib.request.Request(url, headers={"User-Agent": "JLinAlg-variant-tools/1"})
    try:
        with urllib.request.build_opener(Redirect()).open(request, timeout=60) as response, target.open("xb") as stream:
            total = 0
            for block in iter(lambda: response.read(1048576), b""):
                total += len(block)
                if total > maximum:
                    raise ValueError("Software download exceeds the configured limit")
                stream.write(block)
    except urllib.error.HTTPError as error:
        # Issued ANNOVAR URLs may contain personal tokens. Never log the URL.
        raise ValueError("Official software download returned HTTP " + str(error.code)) from None
    except urllib.error.URLError:
        raise ValueError("Cannot connect to the official software download host") from None


def run(command, log, timeout=1200):
    environment = os.environ.copy()
    # pip --isolated ignores user settings; devnull also disables site/global
    # pip configuration so a preconfigured extra index cannot change resolution.
    environment["PIP_CONFIG_FILE"] = os.devnull
    with log.open("x", encoding="utf-8") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=timeout, env=environment)
    if result.returncode:
        raise ValueError("Software setup command failed; inspect " + log.name)


def alphagenome(home, lock):
    if sys.version_info < (3, 10):
        raise ValueError("AlphaGenome setup requires Python 3.10 or newer")
    package = lock["alphagenome"]
    downloads = home / "downloads"
    downloads.mkdir()
    wheel = downloads / package["filename"]
    download(package["url"], wheel, package["maximum_bytes"], {"files.pythonhosted.org"})
    if sha(wheel) != package["sha256"]:
        raise ValueError("Official AlphaGenome wheel checksum differs from JLinAlg's pinned release")
    environment = home / "environment"
    venv.EnvBuilder(with_pip=True, symlinks=False).create(environment)
    python = environment / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    wheels = home / "wheels"
    wheels.mkdir()
    pip = [str(python), "-I", "-B", "-m", "pip", "--isolated", "--disable-pip-version-check", "--no-input", "--no-cache-dir"]
    run(pip + ["download", "--index-url", "https://pypi.org/simple", "--only-binary=:all:", "--progress-bar", "off", "--dest", str(wheels), str(wheel)], home / "download.log")
    records = {}
    requirements = []
    for path in sorted(wheels.glob("*.whl")):
        with zipfile.ZipFile(path) as archive:
            metadata = [name for name in archive.namelist() if name.endswith(".dist-info/METADATA")]
            if len(metadata) != 1:
                raise ValueError("Wheel has ambiguous package metadata")
            info = email.parser.Parser().parsestr(archive.read(metadata[0]).decode("utf-8"))
        name, version = info["Name"], info["Version"]
        if not name or not re.fullmatch(r"[A-Za-z0-9_.-]+", name) or not version or not re.fullmatch(r"[A-Za-z0-9_.+!-]+", version):
            raise ValueError("Wheel contains an invalid package identity")
        identity = re.sub(r"[-_.]+", "-", name).lower()
        if identity in records:
            raise ValueError("Multiple wheels for one package")
        digest = sha(path)
        records[identity] = {"version": version, "wheel": path.name, "sha256": digest}
        requirements.append(name + "==" + version + " --hash=sha256:" + digest)
    if records.get("alphagenome", {}).get("sha256") != package["sha256"]:
        raise ValueError("Resolved wheel set changed the pinned AlphaGenome distribution")
    requirements_file = home / "requirements.lock"
    requirements_file.write_text("\n".join(requirements) + "\n", encoding="utf-8")
    run(pip + ["install", "--no-index", "--find-links", str(wheels), "--require-hashes", "--no-compile", "-r", str(requirements_file)], home / "install.log")
    run(pip + ["check"], home / "dependency-check.log")
    probe = "import alphagenome; from alphagenome.atlas import atlas; from alphagenome.models import dna_client; assert alphagenome.__version__ == " + repr(package["version"]) + "; assert callable(atlas.create) and callable(dna_client.create); print(alphagenome.__version__)"
    run([str(python), "-I", "-B", "-c", probe], home / "probe.log", 120)
    save(home / "packages.json", records)
    return {"executable": python.relative_to(home).as_posix(), "version": package["version"], "packages": len(records), "software_scope": "Official AlphaGenome Python client, not model weights or an API credential; no model request is made during installation."}


def annovar(home, lock, args):
    if not args.accept_license:
        raise ValueError("ANNOVAR requires the user's licence acknowledgement and authorised registered download")
    config = lock["annovar"]
    if bool(args.source_file) == bool(args.source_url_env):
        raise ValueError("Supply exactly one registered ANNOVAR archive or download-URL environment-variable name")
    archive = home / "annovar.tar.gz"
    if args.source_file:
        source = Path(args.source_file).resolve()
        if not source.is_file() or source.stat().st_size > config["maximum_bytes"]:
            raise ValueError("ANNOVAR archive is missing or exceeds the download limit")
        shutil.copyfile(source, archive)
        origin = {"kind": "user_supplied_registered_archive"}
    else:
        url = os.environ.get(args.source_url_env, "")
        if not url:
            raise ValueError("The ANNOVAR download-URL environment variable is not set")
        download(url, archive, config["maximum_bytes"], set(config["download_hosts"]))
        origin = {"kind": "registered_official_download", "source_uri_sha256": hashlib.sha256(url.encode()).hexdigest()}
    digest = sha(archive)
    if args.expected_sha256 and digest != args.expected_sha256.lower():
        raise ValueError("ANNOVAR archive checksum differs from the supplied checksum")
    destination = home / "package"
    destination.mkdir()
    with tarfile.open(archive, "r:gz") as package:
        members, total = [], 0
        for member in package:
            total += member.size
            if len(members) >= config["maximum_members"] or total > config["maximum_extracted_bytes"]:
                raise ValueError("ANNOVAR archive exceeds extraction limits")
            members.append(member)
        for member in members:
            name = Path(member.name)
            target = destination.joinpath(name).resolve()
            if name.is_absolute() or ".." in name.parts or "\\" in member.name or ":" in member.name or not target.is_relative_to(destination) or not (member.isdir() or member.isfile()):
                raise ValueError("ANNOVAR archive contains an unsafe path, link or special member")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with package.extractfile(member) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)
    candidates = [p for p in destination.rglob("table_annovar.pl") if p.with_name("annotate_variation.pl").is_file()]
    if len(candidates) != 1:
        raise ValueError("Archive must contain one ANNOVAR installation with table_annovar.pl and annotate_variation.pl")
    return {"executable": candidates[0].relative_to(home).as_posix(), "version": "archive-sha256:" + digest, "archive_sha256": digest, "source": origin, "license_acknowledged_by_user": True, "software_scope": "Registered ANNOVAR scripts; Perl and chosen annotation databases are separate prerequisites. The licensed archive is not redistributed by JLinAlg."}


def install(args):
    home = args.home.resolve()
    if (home / MARKER).exists():
        raise ValueError("Existing tool homes are verified by JLinAlg, never overwritten by the installer")
    lock = json.loads(args.lock.read_text(encoding="utf-8"))
    result = alphagenome(home, lock) if args.tool == "alphagenome" else annovar(home, lock, args)
    files, links = {}, {}
    for path in sorted(home.rglob("*")):
        if path.is_symlink():
            if not path.resolve().is_relative_to(home):
                raise ValueError("Installed runtime contains a link outside its private home")
            links[path.relative_to(home).as_posix()] = os.readlink(path)
        elif path.is_file():
            files[path.relative_to(home).as_posix()] = sha(path)
    marker = {"schema_version": 1, "tool": args.tool, "profile": lock["profile"], "lock_sha256": sha(args.lock), "created_utc": datetime.now(timezone.utc).isoformat(), "bootstrap_python_version": sys.version.split()[0], "files_sha256": files, "symlinks": links, **result}
    save(home / MARKER, marker)
    print(json.dumps({"status": "installed", "tool": args.tool, "home": str(home), "version": result["version"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tool", choices=("alphagenome", "annovar"), required=True)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--lock", type=Path, required=True)
    parser.add_argument("--source-file")
    parser.add_argument("--source-url-env")
    parser.add_argument("--expected-sha256")
    parser.add_argument("--accept-license", action="store_true")
    arguments = parser.parse_args()
    try:
        install(arguments)
    except Exception as error:
        # Do not include arbitrary exception strings: a registered URL can be personal.
        message = str(error) if isinstance(error, ValueError) else type(error).__name__ + " during software setup"
        save(arguments.home / "setup-failure.json", {"status": "setup_failed", "reason": message, "recovery": "Inspect retained setup logs and choose a new private home; no incomplete runtime is admitted."})
        print(json.dumps({"status": "setup_failed", "reason": message}))
        sys.exit(2)
