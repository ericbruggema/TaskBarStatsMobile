import subprocess, re, time, os, sys
ADB = os.environ.get("ADB", "adb")   # pad naar adb, of laat hem in PATH staan
PKG = "com.ericbruggema.taskbarstatsmobile"
os.environ["MSYS_NO_PATHCONV"] = "1"
def adb(*a, out=True):
    r = subprocess.run([ADB, *a], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.stdout
def sh(cmd): return adb("shell", cmd)
def tap(x, y): sh(f"input tap {int(x)} {int(y)}")
def swipe(x1, y1, x2, y2, ms=300): sh(f"input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {ms}")
def dump():
    sh("uiautomator dump /sdcard/u.xml")
    return adb("exec-out", "cat", "/sdcard/u.xml")
def find(text, exact=True, nth=0):
    x = dump(); hits = []
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        t = m.group(1)
        if (t == text) if exact else (text in t):
            hits.append(((int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2))
    return hits[nth] if len(hits) > nth else None
def tap_text(text, exact=True, nth=0):
    p = find(text, exact, nth)
    if not p: print("NOT FOUND:", text); return False
    tap(*p); return True
def shot(path):
    with open(path, "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
def start_rec(name):
    sh(f"rm -f /sdcard/{name}.mp4")
    return subprocess.Popen([ADB, "shell", f"screenrecord --bit-rate 14000000 --time-limit 170 /sdcard/{name}.mp4"])
def stop_rec(p, name, dest):
    sh("pkill -2 screenrecord"); time.sleep(1.5)
    try: p.wait(timeout=10)
    except Exception: p.kill()
    adb("pull", f"/sdcard/{name}.mp4", dest)
