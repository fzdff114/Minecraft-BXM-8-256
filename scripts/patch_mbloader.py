#!/usr/bin/env python3
from pathlib import Path
import shutil,sys,tempfile,zipfile
def main():
    if len(sys.argv)!=4:
        print("usage: patch_mbloader.py <input.apks> <wrapper.so> <output.apks>"); return 2
    src,wrapper,out=map(Path,sys.argv[1:])
    if not src.exists() or not wrapper.exists(): raise SystemExit("input package or wrapper.so not found")
    with tempfile.TemporaryDirectory() as td:
        root=Path(td)
        with zipfile.ZipFile(src) as z:z.extractall(root)
        candidates=[root/"splits/config.arm64_v8a.apk",root/"apks/split_config.arm64_v8a.apk",root/"split_config.arm64_v8a.apk"]
        split=next((p for p in candidates if p.exists()),None)
        if not split: raise SystemExit("arm64-v8a split not found")
        unpack=root/"arm64"; unpack.mkdir()
        with zipfile.ZipFile(split) as z:z.extractall(unpack)
        libdir=unpack/"lib/arm64-v8a"; original=libdir/"libmods.so"
        if not original.exists(): raise SystemExit("libmods.so not found")
        original.rename(libdir/"libmods_original.so"); shutil.copy2(wrapper,libdir/"libmods.so")
        rebuilt=split.with_suffix(".patched.apk")
        with zipfile.ZipFile(rebuilt,"w",zipfile.ZIP_DEFLATED) as z:
            for p in unpack.rglob("*"):
                if p.is_file(): z.write(p,p.relative_to(unpack))
        shutil.move(rebuilt,split)
        with zipfile.ZipFile(out,"w",zipfile.ZIP_STORED) as z:
            for p in root.rglob("*"):
                if p.is_file(): z.write(p,p.relative_to(root))
    print(out); return 0
if __name__=="__main__": raise SystemExit(main())
