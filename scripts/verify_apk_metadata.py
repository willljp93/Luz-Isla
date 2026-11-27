#!/usr/bin/env python3
import os
import subprocess
import sys
import glob
import re

def find_aapt():
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not android_home:
        for p in ["/usr/local/android-sdk", "/opt/android-sdk", "/root/android/sdk", os.path.expanduser("~/Android/Sdk")]:
            if os.path.exists(p):
                android_home = p
                break
    if not android_home:
        print("Error: ANDROID_HOME or ANDROID_SDK_ROOT not set and SDK not found in common locations.")
        return None

    build_tools_dir = os.path.join(android_home, "build-tools")
    if not os.path.exists(build_tools_dir):
        print(f"Error: build-tools directory not found at {build_tools_dir}")
        return None

    versions = sorted(os.listdir(build_tools_dir), reverse=True)
    for v in versions:
        aapt_path = os.path.join(build_tools_dir, v, "aapt")
        if os.path.exists(aapt_path):
            return aapt_path
        aapt2_path = os.path.join(build_tools_dir, v, "aapt2")
        if os.path.exists(aapt2_path):
            return aapt2_path
    return None

def main():
    if len(sys.argv) < 4:
        print("Usage: verify_apk_metadata.py <apk_dir> <expected_version_name> <expected_version_code> [expected_app_id]")
        sys.exit(1)

    apk_dir = sys.argv[1]
    expected_vname = sys.argv[2]
    expected_vcode = sys.argv[3]
    expected_appid = sys.argv[4] if len(sys.argv) > 4 else "com.aistudio.luzisla.ijvapp"

    aapt = find_aapt()
    if not aapt:
        print("Error: Could not find aapt/aapt2 tool in Android SDK build-tools.")
        sys.exit(1)

    print(f"Using aapt tool: {aapt}")
    apks = glob.glob(os.path.join(apk_dir, "**/*.apk"), recursive=True) + glob.glob(os.path.join(apk_dir, "*.apk"))
    apks = list(set(apks))

    if not apks:
        print(f"Error: No APK files found in directory {apk_dir}")
        sys.exit(1)

    failed = False
    for apk in apks:
        print(f"\nInspecting APK: {apk}")
        cmd = [aapt, "dump", "badging", apk]
        res = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if res.returncode != 0:
            print(f"Error running aapt on {apk}: {res.stderr}")
            failed = True
            continue

        output = res.stdout
        pkg_match = re.search(r"package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'", output)
        if not pkg_match:
            name_m = re.search(r"package:\s+name='([^']+)'", output)
            code_m = re.search(r"versionCode='([^']+)'", output)
            vname_m = re.search(r"versionName='([^']+)'", output)
            pkg_name = name_m.group(1) if name_m else "UNKNOWN"
            v_code = code_m.group(1) if code_m else "UNKNOWN"
            v_name = vname_m.group(1) if vname_m else "UNKNOWN"
        else:
            pkg_name, v_code, v_name = pkg_match.groups()

        print(f"  - Package Name: {pkg_name} (Expected: {expected_appid})")
        print(f"  - Version Code: {v_code} (Expected: {expected_vcode})")
        print(f"  - Version Name: {v_name} (Expected: {expected_vname})")

        if pkg_name != expected_appid:
            print(f"  => MISMATCH: Package name mismatch!")
            failed = True
        if str(v_code) != str(expected_vcode):
            print(f"  => MISMATCH: Version code mismatch!")
            failed = True
        if str(v_name) != str(expected_vname):
            print(f"  => MISMATCH: Version name mismatch!")
            failed = True

        if pkg_name == expected_appid and str(v_code) == str(expected_vcode) and str(v_name) == str(expected_vname):
            print("  => OK: APK metadata matches expected values.")

    if failed:
        print("\nVerification FAILED: One or more APKs have incorrect metadata.")
        sys.exit(1)
    else:
        print("\nVerification SUCCESSFUL: All APKs passed metadata inspection.")
        sys.exit(0)

if __name__ == "__main__":
    main()
