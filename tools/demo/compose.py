import subprocess, os, sys, json
from PIL import Image, ImageDraw
import design as D
from design import font, W, H, ACC, GRN, ORG, DIM, WHITE

T = os.environ["TEMP"] + "/demo"
A = D.OUT
OUTD = T + "/out"
os.makedirs(OUTD, exist_ok=True)
FF = os.environ.get("FFMPEG", "ffmpeg")   # pad naar ffmpeg, of laat hem in PATH staan

# ---------- bijschriften ----------
def caption(name, title, sub, accent=ACC):
    im = Image.new("RGBA", (W, 290), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    size = 66
    while size > 40:
        ft = font(size, True)
        if d.textlength(title, font=ft) <= 960: break
        size -= 2
    tw = d.textlength(title, font=ft)
    d.text(((W - tw) / 2, 22 + (66 - size) // 2), title, font=ft, fill=WHITE)
    d.rounded_rectangle(((W - 120) / 2, 122, (W + 120) / 2, 128), 3, fill=accent)
    fs = font(36); y = 146
    for ln in D.wrap(d, sub, fs, 920)[:3]:
        d.text(((W - d.textlength(ln, font=fs)) / 2, y), ln, font=fs, fill=DIM); y += 46
    im.save(f"{A}/cap_{name}.png")

CAPS = {
    "setup":     ("Ready in seconds", "Pick what to show and where. Done.", ACC),
    "icons":     ("Just the icons. Nothing else.", "No widget, no app open: live download, upload, RAM and CPU sit right next to the clock — on every screen.", GRN),
    "statusbar": ("Or a text strip", "Live download, upload, RAM and CPU in your status bar \u2014 as tiny icons or one neat strip.", GRN),
    "dashboard": ("Your phone at a glance", "Memory, network, storage, CPU, Wi-Fi signal and uptime \u2014 with live graphs.", ACC),
    "cockpit":   ("Cockpit mode", "Give an old phone a second life as a full-screen system monitor.", ORG),
    "themes":    ("Make it yours", "14 colour themes \u2014 or import your own.", D.PUR),
    "tiles":     ("Your tiles, your order", "Show what you care about. Hide the rest.", ACC),
    "gallery":   ("Widgets as buttons", "Six widget types, from a tiny 1x1 to a big 4x3. Tap a picture to put it on your home screen.", GRN),
    "data":      ("Wi-Fi and mobile data", "Today, this week or this month \u2014 right on your dashboard.", ACC),
    "home":      ("Widgets, Quick Settings tile & lock screen", "Check your stats from anywhere.", GRN),
    "cache":     ("See your cache. Clear it.", "Cache per app, the total, and one tap to clear it all — or jump to the app’s own storage page.", ORG),
    "apps":      ("What eats your data and space?", "Data use, traffic per app, storage per app, screen time and autostart apps.", ORG),
    "procs":     ("Live processes", "CPU and memory per process, refreshed every 3 seconds \u2014 with Shizuku.", D.PUR),
    "history":   ("History", "The last 24 hours of memory, network and ping.", ACC),
    "alerts":    ("Alerts that matter", "Get warned when memory, storage, ping or temperature goes over your limit.", ORG),
    "perms":     ("Nothing hidden", "Every extra permission is optional \u2014 and one tap takes you to the right Android setting.", GRN),
}
for k, (t, s, c) in CAPS.items(): caption(k, t, s, c)

# ---------- kaarten ----------
def with_logo(name, lines, logo_y=380):
    first = D.card(name, lines)
    logo_y = first - 270
    im = Image.open(f"{A}/card_{name}.png").convert("RGBA")
    lg = Image.open(f"{A}/logo.png").resize((200, 200))
    im.alpha_composite(lg, ((W - 200) // 2, logo_y)); im.convert("RGB").save(f"{A}/card_{name}.png")

D.logo("logo")
with_logo("intro", [("", 40, WHITE, False), ("TaskBarStats", 108, WHITE, True), ("Mobile", 108, ACC, True),
                    ("Your phone's vital signs.", 46, WHITE, False), ("Always in view.", 46, GRN, True)], 330)
with_logo("outro", [("", 40, WHITE, False), ("TaskBarStatsMobile", 72, WHITE, True),
                    ("Open source  \u00B7  No ads  \u00B7  No tracking", 36, GRN, True),
                    ("Everything stays on your phone.", 34, DIM, False),
                    ("Status bar \u00B7 Dashboard \u00B7 Cockpit \u00B7 Widgets \u00B7 Quick Settings \u00B7 Lock screen \u00B7 Themes \u00B7 Alerts", 30, DIM, False),
                    ("github.com/ericbruggema/TaskBarStatsMobile", 40, ACC, True)], 280)

# ---------- stukken ----------
def run(args):
    r = subprocess.run([FF, "-v", "error", "-y", *args], capture_output=True, text=True)
    if r.returncode: print(r.stderr[-1500:]); raise SystemExit(1)

def screen_piece(out, clip, ss, dur, speed, cap, inset, fin, fout):
    od = dur / speed
    fl = [f"[0:v]trim=start={ss}:duration={dur},setpts=(PTS-STARTPTS)/{speed},fps=30,split=2[v0][v1]",
          "[v0]scale=720:1600,format=rgba[ph0]", "[ph0][2:v]alphamerge[ph]", "[1:v][ph]overlay=180:300[b1]"]
    last = "b1"
    if inset:
        fl += [f"[v1]crop={inset if isinstance(inset, int) and inset > 1 else 860}:148:0:0,scale=1000:172,format=rgba[in0]", "[in0][5:v]alphamerge[in]",
               "[b1][in]overlay=40:312[b2]", "[b2][4:v]overlay=34:306[b3]"]
        last = "b3"
    else:
        fl += ["[v1]nullsink"]
    capf = "format=rgba" + (",fade=t=in:st=0:d=0.45:alpha=1" if fin else "")
    fl += [f"[3:v]{capf}[cp]", f"[{last}][cp]overlay=0:14[o]"]
    fade = []
    if fin: fade.append("fade=t=in:st=0:d=0.3")
    if fout: fade.append(f"fade=t=out:st={od - 0.3:.2f}:d=0.3")
    fl += [f"[o]{','.join(fade) if fade else 'null'},format=yuv420p[final]"]
    args = ["-i", clip, "-loop", "1", "-framerate", "30", "-t", f"{od:.3f}", "-i", f"{A}/bg.png",
            "-loop", "1", "-framerate", "30", "-t", f"{od:.3f}", "-i", f"{A}/mask.png",
            "-loop", "1", "-framerate", "30", "-t", f"{od:.3f}", "-i", f"{A}/cap_{cap}.png",
            "-loop", "1", "-framerate", "30", "-t", f"{od:.3f}", "-i", f"{A}/inset_border.png",
            "-loop", "1", "-framerate", "30", "-t", f"{od:.3f}", "-i", f"{A}/inset_mask.png",
            "-filter_complex", ";".join(fl), "-map", "[final]", "-t", f"{od:.3f}", "-r", "30",
            "-c:v", "libx264", "-preset", "medium", "-crf", "19", out]
    # alphamerge wil een grijs masker
    run(args)

def still_piece(out, png, dur, cap, fin, fout):
    n = int(dur * 30)
    fl = [f"[0:v]scale=2160:4800,zoompan=z='1+0.00035*on':x='iw/2-(iw/zoom/2)':y='0':d={n}:s=720x1600:fps=30,format=rgba[ph0]",
          "[ph0][2:v]alphamerge[ph]", "[1:v][ph]overlay=180:300[b1]",
          "[3:v]format=rgba" + (",fade=t=in:st=0:d=0.45:alpha=1" if fin else "") + "[cp]", "[b1][cp]overlay=0:14[o]"]
    fade = []
    if fin: fade.append("fade=t=in:st=0:d=0.3")
    if fout: fade.append(f"fade=t=out:st={dur - 0.3:.2f}:d=0.3")
    fl += [f"[o]{','.join(fade) if fade else 'null'},format=yuv420p[final]"]
    run(["-loop", "1", "-framerate", "30", "-i", png,
         "-loop", "1", "-framerate", "30", "-t", f"{dur}", "-i", f"{A}/bg.png",
         "-loop", "1", "-framerate", "30", "-t", f"{dur}", "-i", f"{A}/mask.png",
         "-loop", "1", "-framerate", "30", "-t", f"{dur}", "-i", f"{A}/cap_{cap}.png",
         "-filter_complex", ";".join(fl), "-map", "[final]", "-t", f"{dur}", "-r", "30",
         "-c:v", "libx264", "-preset", "medium", "-crf", "19", out])

def card_piece(out, name, dur):
    n = int(dur * 30)
    fl = [f"[0:v]scale=1188:2112,zoompan=z='1+0.0004*on':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={n}:s={W}x{H}:fps=30,"
          f"fade=t=in:st=0:d=0.5,fade=t=out:st={dur - 0.5:.2f}:d=0.5,format=yuv420p[final]"]
    run(["-loop", "1", "-framerate", "30", "-i", f"{A}/card_{name}.png", "-filter_complex", ";".join(fl),
         "-map", "[final]", "-t", f"{dur}", "-r", "30", "-c:v", "libx264", "-preset", "medium", "-crf", "19", out])

def clip(n): return f"{T}/clips/{n}.mp4"

# (scene, kind, params...)  kind: "s" = schermopname-stuk (clip, ss, dur, speed, inset)  "p" = stilstaand (png, dur)
FULL = [
    ("intro", "card", 3.6),
    ("setup", "s", "s01_setup", 1.5, 13.6, 2.7, False), ("setup", "s", "s01_setup", 14.4, 5.0, 1.0, True),
    ("icons", "s", "s02a_icons", 1.0, 15.0, 1.6, 600),
    ("statusbar", "s", "s02_statusbar", 1.5, 7.5, 1.0, True),
    ("dashboard", "s", "s03_dashboard", 1.0, 11.0, 1.5, False),
    ("gallery", "s", "s06b_gallery", 1.0, 28.0, 2.2, False),
    ("home", "s", "s07_home", 0.5, 16.5, 1.45, False),
    ("cockpit", "s", "s04_cockpit", 0.8, 8.6, 1.5, False),
    ("themes", "s", "s05_themes", 6.0, 44.0, 4.6, False),
    ("tiles", "s", "s06_tiles", 0.5, 19.0, 1.7, False),
    ("data", "s", "s06c_data", 0.5, 21.0, 1.7, False),
    ("apps", "s", "s08_apps", 1.5, 8.0, 1.4, False),
    ("cache", "s", "s08b_cache", 1.0, 29.0, 2.9, False),
    ("procs", "s", "s08c_procs", 1.0, 19.0, 1.8, False),
    ("history", "s", "s09_history", 0.3, 4.8, 1.1, False),
    ("alerts", "s", "s09_history", 5.0, 7.8, 1.3, False),
    ("perms", "s", "s10_permissions", 0.8, 10.0, 1.25, False),
    ("outro", "card", 6.0),
]
SHORT = [
    ("intro", "card", 2.8),
    ("icons", "s", "s02a_icons", 1.0, 12.0, 2.2, 600),
    ("statusbar", "s", "s02_statusbar", 1.5, 4.5, 1.0, True),
    ("dashboard", "s", "s03_dashboard", 1.0, 10.5, 2.6, False),
    ("gallery", "s", "s06b_gallery", 1.0, 28.0, 4.0, False),
    ("home", "s", "s07_home", 0.5, 16.5, 2.5, False),
    ("themes", "s", "s05_themes", 8.0, 38.0, 6.5, False),
    ("cache", "s", "s08b_cache", 1.0, 29.0, 4.5, False),
    ("procs", "s", "s08c_procs", 1.0, 17.0, 2.6, False),
    ("alerts", "s", "s09_history", 5.0, 7.8, 2.1, False),
    ("outro", "card", 4.2),
]

def build(name, plan):
    parts = []
    for i, step in enumerate(plan):
        scene, kind = step[0], step[1]
        out = f"{OUTD}/{name}_{i:02d}.mp4"
        prev = plan[i - 1][0] if i else None; nxt = plan[i + 1][0] if i + 1 < len(plan) else None
        fin, fout = scene != prev, scene != nxt
        if kind == "card": card_piece(out, scene, step[2])
        elif kind == "p": still_piece(out, step[2], step[3], scene, fin, fout)
        else:
            _, _, clipn, ss, dur, sp, inset = step
            screen_piece(out, clip(clipn), ss, dur, sp, scene, inset, fin, fout)
        parts.append(out); print("ok", out, flush=True)
    lst = f"{OUTD}/{name}_list.txt"
    open(lst, "w").write("".join(f"file '{p}'\n" for p in parts))
    final = f"{OUTD}/{name}.mp4"
    run(["-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", "-movflags", "+faststart", final])
    return final

if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "short"
    print(build("android-" + which, FULL if which == "full" else SHORT))
