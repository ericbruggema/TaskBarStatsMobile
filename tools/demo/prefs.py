from lib import *
def write_prefs(body):
    xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + body + "\n</map>\n"
    p = os.environ["TEMP"] + "/demo/settings.xml"
    open(p, "w", encoding="utf-8", newline="\n").write(xml)
    sh(f"am force-stop {PKG}")
    adb("push", p, "/data/local/tmp/settings.xml")
    sh(f"run-as {PKG} sh -c 'mkdir -p shared_prefs; cp /data/local/tmp/settings.xml shared_prefs/settings.xml'")
BASE = ('    <boolean name="setup_done" value="true" />\n    <string name="items">down,up,mem,cpu</string>\n'
        '    <boolean name="icons" value="true" />\n    <boolean name="overlay" value="true" />\n    <int name="overlay_pos" value="2" />')
