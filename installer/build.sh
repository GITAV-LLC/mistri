#!/usr/bin/env bash
# Builds dist/mistri-setup-<version>.exe
#
# Needs: makensis (NSIS 3), python3.12 (to precompile), uv (fetches the Windows Python runtime),
#        pip (fetches psutil's Windows wheel). Runs on Linux or macOS; no Windows needed.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
WORK="${WORK:-$ROOT/build}"
PYVER="${PYVER:-3.12}"
VERSION="$(sed -n 's/^__version__ = "\(.*\)"/\1/p' "$ROOT/mistri/__init__.py")"
STAGE="$WORK/stage"

echo "==> mistri $VERSION"
rm -rf "$STAGE" && mkdir -p "$STAGE" "$WORK/cache" "$ROOT/dist"

# 1. Windows Python runtime (python-build-standalone: includes Tk and the VC++ runtime DLLs)
RT_CACHE="$WORK/cache/py"
if ! ls -d "$RT_CACHE"/cpython-$PYVER*-windows-x86_64-none >/dev/null 2>&1; then
  echo "==> Fetching Windows Python $PYVER runtime"
  UV_PYTHON_INSTALL_DIR="$RT_CACHE" uv python install --no-bin "cpython-$PYVER-windows-x86_64-none"
fi
RT_SRC="$(ls -d "$RT_CACHE"/cpython-$PYVER*-windows-x86_64-none | sort -V | tail -1)"
echo "    using $(basename "$RT_SRC")"
cp -r "$RT_SRC" "$STAGE/runtime"
R="$STAGE/runtime"
# trim what an end-user app never needs (~45 MB)
rm -rf "$R"/include "$R"/libs "$R"/Scripts "$R"/BUILD "$R"/Lib/test "$R"/Lib/idlelib "$R"/Lib/lib2to3 \
       "$R"/Lib/ensurepip "$R"/Lib/pydoc_data "$R"/Lib/turtledemo "$R"/Lib/tkinter/test \
       "$R"/Lib/unittest/test "$R"/Lib/site-packages/pip* "$R"/tcl/nmake "$R"/tcl/*.lib "$R"/tcl/*.sh \
       "$R"/DLLs/_test*.pyd "$R"/DLLs/xxlimited*.pyd "$R"/DLLs/_ctypes_test.pyd
find "$R" -name "__pycache__" -type d -prune -exec rm -rf {} +

# 2. psutil (prebuilt Windows wheel)
echo "==> Fetching psutil for Windows"
rm -rf "$WORK/wheels" && mkdir -p "$WORK/wheels"
python3 -m pip download --quiet psutil --only-binary=:all: --platform win_amd64 \
    --python-version "$PYVER" --implementation cp -d "$WORK/wheels"
python3 -m zipfile -e "$WORK"/wheels/psutil-*.whl "$R/Lib/site-packages/"

# 3. The app; a .pth file puts it on the runtime's path so "-m mistri" works from anywhere
mkdir -p "$STAGE/app"
cp -r "$ROOT/mistri" "$ROOT/extension" "$STAGE/app/"
rm -rf "$STAGE/app/extension/_metadata"
find "$STAGE/app" -name "__pycache__" -type d -prune -exec rm -rf {} +
printf '..\\..\\..\\app\n' > "$R/Lib/site-packages/mistri.pth"
cp "$ROOT/mistri/assets/mistri.ico" "$STAGE/mistri.ico"
cp "$ROOT/README.md" "$STAGE/README.md"
cp "$HERE/THIRD-PARTY-NOTICES.txt" "$STAGE/"
cp "$R/LICENSE.txt" "$STAGE/PYTHON-LICENSE.txt"
printf '@echo off\r\n"%%~dp0runtime\\python.exe" -m mistri %%*\r\n' > "$STAGE/mistri.cmd"

# 4. Precompile (hash-checked .pyc never go stale and start faster; Program Files isn't writable)
echo "==> Precompiling"
python$PYVER -m compileall -q -j0 --invalidation-mode unchecked-hash "$R/Lib" "$STAGE/app" >/dev/null || true

# 5. Build the installer
echo "==> Building installer"
SIGNARG=()
if [[ -n "${AZURE_SIGNING_TOKEN:-}" ]]; then
  echo "    signing with Azure Artifact Signing ($AZURE_SIGNING_ACCOUNT/$AZURE_SIGNING_PROFILE)"
  SIGNARG=(-DSIGN="$HERE/sign.sh")
else
  echo "    (unsigned build: no signing credentials in the environment)"
fi
makensis -V2 -DVERSION="$VERSION" -DSTAGE="$STAGE" -DROOT="$ROOT" -DOUTFILE="$ROOT/dist/mistri-setup-$VERSION.exe" \
    "${SIGNARG[@]}" "$HERE/mistri.nsi"
OUT="$ROOT/dist/mistri-setup-$VERSION.exe"
( cd "$ROOT/dist" && sha256sum "mistri-setup-$VERSION.exe" > "mistri-setup-$VERSION.exe.sha256" )
( cd "$ROOT/dist" && rm -f "mistri-setup-$VERSION.zip" && python3 -m zipfile -c "mistri-setup-$VERSION.zip" "mistri-setup-$VERSION.exe" )
ls -la "$ROOT/dist"
