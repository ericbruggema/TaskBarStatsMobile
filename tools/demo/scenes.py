import sys, subprocess
from lib import *
from prefs import write_prefs, BASE
CLIPS = os.environ["TEMP"] + "/demo/clips"
os.makedirs(CLIPS, exist_ok=True)
TAB = {"dash": (160, 192), "cockpit": (413, 192), "widget": (636, 192), "tiles": (836, 192)}
ACT = f"{PKG}/com.ericbruggema.taskbarstats.MainActivity"

def open_tab(n): sh(f"am start -n {ACT} --ei tab {n}"); time.sleep(1.2)

def wake():
    sh("input keyevent 224"); time.sleep(0.8); sh("wm dismiss-keyguard"); time.sleep(0.8)

class Rec:
    def __init__(self, name): self.name = name
    def __enter__(self):
        wake()
        sh("settings put system show_touches 1"); time.sleep(0.3)
        self.p = start_rec(self.name); time.sleep(1.2); return self
    def __exit__(self, *a):
        time.sleep(0.8)
        stop_rec(self.p, self.name, f"{CLIPS}/{self.name}.mp4")
        sh("settings put system show_touches 0")

ITEMS_PREFS = ('    <boolean name="setup_done" value="true" />\n    <string name="items">down,up,mem,cpu</string>\n'
               '    <boolean name="icons" value="true" />\n    <boolean name="overlay" value="%s" />\n    <int name="overlay_pos" value="2" />')

def icons_only():
    """Alleen de iconen naast de klok, gebruikt terwijl je een andere app open hebt (geen widget, geen strook)."""
    write_prefs(ITEMS_PREFS % "false"); sh(f"am start -n {ACT}"); time.sleep(6)
    sh("input keyevent 3"); time.sleep(1)
    sh("am start -a android.settings.SETTINGS"); time.sleep(2.5)
    with Rec("s02a_icons"):
        time.sleep(2.5)
        for _ in range(3):
            swipe(540, 1900, 540, 700, 900); time.sleep(1.6)
        swipe(540, 700, 540, 1900, 700); time.sleep(1.2)
        sh("cmd statusbar expand-notifications"); time.sleep(3.2)
        sh("cmd statusbar collapse"); time.sleep(0.8)
    sh("input keyevent 3")

def statusbar():
    write_prefs(BASE); sh(f"am start -n {ACT} --ei tab 0"); time.sleep(6)
    with Rec("s02_statusbar"): time.sleep(11)

def dashboard():
    open_tab(0)
    with Rec("s03_dashboard"):
        time.sleep(1.5)
        swipe(540, 1900, 540, 800, 1100); time.sleep(1.6)
        swipe(540, 1900, 540, 800, 1100); time.sleep(1.8)
        swipe(540, 700, 540, 1900, 700); time.sleep(0.5); swipe(540, 700, 540, 1900, 700); time.sleep(1)

def cockpit():
    open_tab(0)
    with Rec("s04_cockpit"):
        time.sleep(1); tap(*TAB["cockpit"]); time.sleep(6.5); tap(540, 1300); time.sleep(1.5)

def pick_theme(name, tries=7):
    for _ in range(tries):
        p = find(name)
        if p and 20 < p[0] < 1060: tap(*p); return True
        r = find("Default") or find("TaskbarStats") or find("Love") or find("Amber")
        y = r[1] if r else 1968
        swipe(980, y, 220, y, 450); time.sleep(0.5)
    return False

def themes():
    open_tab(2); swipe(540, 1900, 540, 500, 500); time.sleep(1)
    pick_theme("TaskbarStats"); time.sleep(1)           # beginnen bij het standaardthema
    open_tab(0)
    with Rec("s05_themes"):
        time.sleep(0.8); tap(*TAB["widget"]); time.sleep(1.4)
        swipe(540, 1900, 540, 500, 500); time.sleep(1.2)
        for t in ["Dark", "Light", "Matrix", "Dracula", "Amber", "Ocean"]:
            pick_theme(t); time.sleep(1.0)
        time.sleep(0.8)
    pick_theme("TaskbarStats"); time.sleep(1)

def tiles():
    open_tab(3)
    for _ in range(4): swipe(540, 700, 540, 1900, 250); time.sleep(0.3)
    open_tab(0)
    with Rec("s06_tiles"):
        time.sleep(0.8); tap(*TAB["tiles"]); time.sleep(1.8)
        for name in ("Ping", "Temperature"):                         # twee tegels uit
            p = find(name); tap(100, p[1]) if p else None; time.sleep(0.9)
        p = find("Network"); tap(946, p[1]) if p else None; time.sleep(0.9)      # netwerk omlaag
        p = find("Memory"); tap(946, p[1]) if p else None; time.sleep(0.9)       # geheugen omlaag
        tap(*TAB["dash"]); time.sleep(3.2)
    open_tab(3); time.sleep(1)
    for _ in range(3): swipe(540, 1800, 540, 600, 300); time.sleep(0.4)
    p = find("Reset tiles"); tap(*p) if p else None; time.sleep(1)

def place(label):
    """Zet een widgetsoort via de galerij op het beginscherm (niet opgenomen)."""
    open_tab(2); time.sleep(1.5)
    p = None
    for _ in range(14):
        p = find(label)
        if p and 330 < p[1] < 2150: break
        swipe(540, 1500, 540, 1000, 350); time.sleep(0.6)
    if not p: print("niet gevonden", label); return
    tap(*p); time.sleep(2.5)
    q = find("Add to home screen")
    if q: tap(*q); time.sleep(4)
    q = find("Save")
    if q: tap(*q); time.sleep(2)

def gallery():
    """De widgetgalerij: elk soort is een knop; Large wordt tijdens de opname toegevoegd."""
    sh("pm clear com.google.android.apps.nexuslauncher"); time.sleep(4); sh("input keyevent 3"); time.sleep(2)
    for lab in ("Strip (compact)", "Duo (2x1, two items)", "Mini (1x1, one number)"): place(lab)
    wake(); open_tab(2); time.sleep(1.5)
    for _ in range(6): swipe(540, 600, 540, 1900, 250); time.sleep(0.3)
    for _ in range(20):
        h = find("Home-screen widgets")
        if h and 380 < h[1] < 900: break
        swipe(540, 1500, 540, 1000, 350); time.sleep(0.6)
    with Rec("s06b_gallery"):
        time.sleep(1.5)
        for _ in range(6): swipe(540, 1650, 540, 1050, 800); time.sleep(1.4)
        p = find("Large (4x3, all info)"); tap(*p) if p else None; time.sleep(2.8)
        p = find("Add to home screen"); tap(*p) if p else None; time.sleep(3.5)
        p = find("Save"); tap(*p) if p else None; time.sleep(1.5)
        sh("input keyevent 3"); time.sleep(3.5)

def datatiles():
    """Wi-Fi- en mobiele-datategels met de periodekeuze."""
    open_tab(3)
    for _ in range(4): swipe(540, 700, 540, 1900, 250); time.sleep(0.3)
    with Rec("s06c_data"):
        time.sleep(1.5)
        for lab in ("Last 7 days", "Last 30 days", "Today"):
            p = find(lab); tap(*p) if p else None; time.sleep(1.8)
        tap(*TAB["dash"]); time.sleep(1.5)
        for _ in range(3): swipe(540, 1800, 540, 600, 500); time.sleep(1.0)
        time.sleep(1.5)

def home():
    wake(); open_tab(0)
    sh("input keyevent 3"); time.sleep(1.2); swipe(150, 1200, 900, 1200, 250); time.sleep(0.6)
    with Rec("s07_home"):
        time.sleep(0.6); swipe(900, 1200, 150, 1200, 300); time.sleep(3.6)
        sh("cmd statusbar expand-settings"); time.sleep(3.6)
        sh("cmd statusbar collapse"); time.sleep(0.8)
        sh("input keyevent 26"); time.sleep(1.2); sh("input keyevent 26"); time.sleep(4.2)
    sh("wm dismiss-keyguard"); time.sleep(1); open_tab(0)

def apps():
    open_tab(4); time.sleep(1.5); tap(431, 340); time.sleep(5)       # verkeer eerst laden (traag) voor de opname
    open_tab(4)
    with Rec("s08_apps"):
        time.sleep(2.4)                                   # Data use
        tap(431, 340); time.sleep(3.6)                    # Traffic per app
        swipe(540, 1700, 540, 900, 700); time.sleep(1.0); swipe(540, 900, 540, 1700, 500); time.sleep(0.6)

def fill_caches():
    """Apps even gebruiken zodat er cache ontstaat om te wissen (alleen voor de opname)."""
    for pkgname in ("com.google.android.apps.photos", "com.google.android.gm", "com.google.android.youtube", "com.android.vending", "com.google.android.apps.messaging", "com.google.android.apps.maps"):
        sh(f"monkey -p {pkgname} -c android.intent.category.LAUNCHER 1"); time.sleep(3.5)
    sh("am start -a android.intent.action.VIEW -d https://en.wikipedia.org/wiki/Android_(operating_system) com.android.chrome"); time.sleep(9)
    sh("input keyevent 3"); time.sleep(1)

def storage_dialog():
    open_tab(4); time.sleep(1.5); tap(769, 340); time.sleep(9)
    with Rec("s08b_cache"):
        time.sleep(1.4)
        p = find("Most cache first"); tap(*p) if p else None; time.sleep(2.0)
        p = find("Chrome"); tap(*p) if p else None; time.sleep(3.6)                 # opslag van een app met de opties
        p = find("Close"); tap(*p) if p else sh("input keyevent 4"); time.sleep(1.2)
        p = find("Clear all caches"); tap(*p) if p else None; time.sleep(16.0)       # alle caches in een keer

def procs():
    open_tab(4); time.sleep(1.5)
    swipe(900, 340, 100, 340, 300); time.sleep(0.8)
    p = find("Processes"); tap(*p) if p else tap(916, 340); time.sleep(7)
    # eerst zonder opname proberen tot het goede proces opengaat (de lijst ververst elke 3 s)
    def open_chrome():
        for _ in range(8):
            p = find("Chrome") or find("Pixel Launcher")
            if p: tap(*p); time.sleep(1.8)
            x = dump()
            if "Open app (bring to front)" in x: return True
            q = find("Close"); tap(*q) if q else sh("input keyevent 4"); time.sleep(0.8)
        return False
    ok = open_chrome(); print("chrome dialog:", ok)
    q = find("Close"); tap(*q) if q else sh("input keyevent 4"); time.sleep(1.0)
    with Rec("s08c_procs"):
        time.sleep(0.8)
        open_chrome(); time.sleep(3.2)
        swipe(540, 1500, 540, 1000, 700); time.sleep(1.6)
        p = find("Open app (bring to front)"); tap(*p) if p else None; time.sleep(3.6)
    sh("input keyevent 3"); time.sleep(1); open_tab(0)

def history_alerts():
    write_prefs(BASE + '\n    <int name="alert_mem_v" value="40" />'); wake(); sh(f"am start -n {ACT}"); time.sleep(240)   # grafieken per minuut vullen zich pas na een paar minuten
    open_tab(5)
    with Rec("s09_history"):
        time.sleep(3.6)
        open_tab(6); time.sleep(1.2)
        tap(142, 619); time.sleep(3.2)
        sh("cmd statusbar expand-notifications"); time.sleep(3)
        sh("cmd statusbar collapse"); time.sleep(0.5)
    open_tab(6); tap(142, 619); sh("cmd notification cancel_all 2>/dev/null")

def permissions():
    open_tab(7)
    with Rec("s10_permissions"):
        time.sleep(2.2)
        swipe(540, 1900, 540, 1000, 1500); time.sleep(2.2)
        p = find("Open settings"); tap(*p) if p else None; time.sleep(3)
        sh("input keyevent 4"); time.sleep(1.5)

def setup():
    write_prefs('    <int name="alert_mem_v" value="40" />')
    sh(f"am start -n {ACT}"); time.sleep(5)
    with Rec("s01_setup"):
        time.sleep(1.8)
        p = find("CPU"); tap(*p) if p else None; time.sleep(1)
        p = find("Text strip in the status bar"); tap(142, p[1] if p else 1182); time.sleep(1.2)
        p = find("After clock"); tap(*p) if p else None; time.sleep(1.2)
        p = find("Start"); tap(*p) if p else None; time.sleep(7)

if __name__ == "__main__":
    for n in sys.argv[1:]: globals()[n]()
