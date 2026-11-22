# Exact QTLtools runtime setup

Read [workflow warnings](QTLTOOLS_WORKFLOWS.md) first. Native fdensity needs Java
only. Original execution needs an exact selected executable and compatible Linux
x86_64/Python3.11+/ldd. Windows transport uses an explicit installed WSL distribution.
These are measured build identities, not signatures or whole-tool validation.

**If download, extraction or a required utility is prohibited, skip the dependent
analysis (SKIP3).** State the exact missing package/library/path/permission and
what must be provided. Do not substitute another binary or upgrade the operating
system implicitly. Retain failed setup logs and retry in a NEW private folder.

## Historical1.1 and1.2

The [machine-readable package profiles](qtltools-runtime-profiles.json) give exact
official archive URLs and SHA256, executable identities and source URLs. Download
only where permitted, verify the archive hash, and extract into a NEW private
folder. The corresponding executable names are
`QTLtools_1.1_Ubuntu16.04_x86_64` and `QTLtools_1.2_Ubuntu16.04_x86_64`.
Retain license/source notices. These builds statically include their scientific
libraries but still require compatible host OS libraries; record ldd diagnostics.

## Selected1.3.1 Ubuntu build

The current workspace already has the approved private extraction under
`D:/projects/JLinAlg/build/literature-review/qtltools-runtime/ubuntu-private-1`.
On another compatible Ubuntu/Linux environment, the exact packages below can be
downloaded and privately extracted without a system installation or maintainer
script execution. Package availability can change: verify each downloaded SHA256
against the JSON profile rather than accepting a similarly named update.

| Package | Exact version | Official package |
| --- | --- | --- |
| `libblas3` | `3.12.0-3build1.1` | [deb](https://archive.ubuntu.com/ubuntu/pool/main/l/lapack/libblas3_3.12.0-3build1.1_amd64.deb) |
| `libboost-iostreams1.83.0` | `1.83.0-2.1ubuntu3.2` | [deb](https://archive.ubuntu.com/ubuntu/pool/main/b/boost1.83/libboost-iostreams1.83.0_1.83.0-2.1ubuntu3.2_amd64.deb) |
| `libboost-program-options1.83.0` | `1.83.0-2.1ubuntu3.2` | [deb](https://archive.ubuntu.com/ubuntu/pool/main/b/boost1.83/libboost-program-options1.83.0_1.83.0-2.1ubuntu3.2_amd64.deb) |
| `libgsl27` | `2.7.1+dfsg-6ubuntu2` | [deb](https://archive.ubuntu.com/ubuntu/pool/universe/g/gsl/libgsl27_2.7.1+dfsg-6ubuntu2_amd64.deb) |
| `libgslcblas0` | `2.7.1+dfsg-6ubuntu2` | [deb](https://archive.ubuntu.com/ubuntu/pool/universe/g/gsl/libgslcblas0_2.7.1+dfsg-6ubuntu2_amd64.deb) |
| `libhts3t64` | `1.19+ds-1.1build3` | [deb](https://archive.ubuntu.com/ubuntu/pool/universe/h/htslib/libhts3t64_1.19+ds-1.1build3_amd64.deb) |
| `libhtscodecs2` | `1.6.0-1build1` | [deb](https://archive.ubuntu.com/ubuntu/pool/universe/h/htscodecs/libhtscodecs2_1.6.0-1build1_amd64.deb) |
| `qtltools` | `1.3.1+dfsg-4build3` | [deb](https://archive.ubuntu.com/ubuntu/pool/universe/q/qtltools/qtltools_1.3.1+dfsg-4build3_amd64.deb) |
| `r-mathlib` | `4.3.3-2build2` | [deb](https://archive.ubuntu.com/ubuntu/pool/universe/r/r-base/r-mathlib_4.3.3-2build2_amd64.deb) |

Example INSIDE Linux/WSL, in a NEW folder (not PowerShell):

```bash
mkdir qtltools-runtime-NEW
cd qtltools-runtime-NEW
apt-get download libblas3=3.12.0-3build1.1 \
  libboost-iostreams1.83.0=1.83.0-2.1ubuntu3.2 \
  libboost-program-options1.83.0=1.83.0-2.1ubuntu3.2 \
  libgsl27=2.7.1+dfsg-6ubuntu2 \
  libgslcblas0=2.7.1+dfsg-6ubuntu2 \
  libhts3t64=1.19+ds-1.1build3 \
  libhtscodecs2=1.6.0-1build1 \
  qtltools=1.3.1+dfsg-4build3 \
  r-mathlib=4.3.3-2build2
# Compare each deb's SHA256 with qtltools-runtime-profiles.json before extraction.
sha256sum ./*.deb
mkdir root
for qtl_deb in ./*.deb; do
  dpkg-deb --extract "$qtl_deb" "$PWD/root"
done
LD_LIBRARY_PATH="$PWD/root/usr/lib:$PWD/root/usr/lib/x86_64-linux-gnu:$PWD/root/usr/lib/x86_64-linux-gnu/blas" "$PWD/root/usr/bin/QTLtools" --help
```

If apt metadata no longer offers a version, the table gives its exact official
package URL; the same SHA256 check is required. If the compatible OS loader or
utilities are unavailable and cannot be installed, retain SKIP3. Do not patch
system libraries or remove an existing runtime to make this recipe work.

Set runtime.executable to the absolute `root/usr/bin/QTLtools` and supply all
three absolute library directories shown above. Windows requests use drive paths
and an explicit wsl_distribution; host-native Linux uses Linux paths and no WSL
field. A missing critical-library error reports a required hash; locate it here:

| Critical scientific library file | Required SHA256 |
| --- | --- |
| `libRmath.so.1.0.0` | `96ad6f624799c4d0c7824541b80deb6e79cf74868608f616af1a30607426b325` |
| `libhts.so.1.19` | `69a7acc3c230aae89ed31447632a05b52a2e98c76e0a8eefc72f8c4928b9dda4` |
| `libboost_iostreams.so.1.83.0` | `3e029a8a694b343cacea94d842d16ad76e74e10b5070544de6c9e50551616acc` |
| `libboost_program_options.so.1.83.0` | `15f34bcb75dfb0fa30af13cf2b82fb7a9a8e0fbbadf7cabcd397dd65bde552f3` |
| `libgsl.so.27.0.0` | `99a902f1c0df5c709ba20cb64f014eda51b978305c2923d9397f0b1b902eda18` |
| `libblas.so.3.12.0` | `e748efcae5753fe4a652877fccdb5895ac6f7605668a2db878b19c914e78e3a8` |
| `libhtscodecs.so.2.1.5` | `2f22efa753f97399e81cf9e1e4f01f92c876569b5cc95e82c5546f5ac5945fa7` |
| `libgslcblas.so.0.0.0` | `cbaeb658d6d3fd2dc46695923083c0e8b5ceb5f9fb76b1f607a643e00cb2a1c5` |

The worker records every observed loaded library before/after execution, requires
these exact scientific hashes and preserves loader stderr. Private extraction
does not make an incompatible host loader compatible. Help/load success is not
a scientific analysis; execute only declared prepared-input operations afterward.
Keep original archives, extraction logs and all earlier failures for handover.
