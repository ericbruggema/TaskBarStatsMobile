from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os
W, H = 1080, 1920
OUT = os.environ["TEMP"] + "/demo/assets"
os.makedirs(OUT, exist_ok=True)
F = "C:/Windows/Fonts/"
def font(size, bold=False): return ImageFont.truetype(F + ("segoeuib.ttf" if bold else "segoeui.ttf"), size)
ACC = (79, 195, 247); GRN = (129, 199, 132); ORG = (255, 183, 77); PUR = (186, 104, 200); DIM = (159, 179, 192); WHITE = (240, 246, 250)

PH_W, PH_H = 720, 1600           # de schermopname wordt hierop geschaald (1080x2400 -> 2/3)
PH_X, PH_Y = (W - PH_W) // 2, 300

def gradient():
    im = Image.new("RGB", (W, H))
    px = im.load()
    for y in range(H):
        t = y / H
        c = (int(9 + 10 * t), int(16 + 14 * t), int(22 + 22 * t))
        for x in range(W): px[x, y] = c
    # zachte gloed achter de telefoon
    glow = Image.new("RGB", (W, H), (0, 0, 0)); d = ImageDraw.Draw(glow)
    d.ellipse((W // 2 - 520, 700, W // 2 + 520, 1700), fill=(18, 60, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    from PIL import ImageChops
    return ImageChops.add(im, glow)

def bg():
    im = gradient().convert("RGBA")
    d = ImageDraw.Draw(im)
    # telefoonframe
    d.rounded_rectangle((PH_X - 18, PH_Y - 18, PH_X + PH_W + 18, PH_Y + PH_H + 18), 84, fill=(34, 42, 50), outline=(70, 86, 100), width=3)
    d.rounded_rectangle((PH_X - 6, PH_Y - 6, PH_X + PH_W + 6, PH_Y + PH_H + 6), 72, fill=(0, 0, 0))
    # camera-gaatje
    d.ellipse((W // 2 - 11, PH_Y + 16, W // 2 + 11, PH_Y + 38), fill=(12, 14, 18))
    im.save(OUT + "/bg.png")
    m = Image.new("L", (PH_W, PH_H), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, PH_W - 1, PH_H - 1), 66, fill=255)
    m.save(OUT + "/mask.png")
    # rand voor het vergrootglas
    ins = Image.new("RGBA", (1000 + 12, 172 + 12), (0, 0, 0, 0))
    ImageDraw.Draw(ins).rounded_rectangle((0, 0, 1011, 183), 26, outline=ACC + (255,), width=5, fill=(0, 0, 0, 0))
    ins.save(OUT + "/inset_border.png")
    im2 = Image.new("L", (1000, 172), 0)
    ImageDraw.Draw(im2).rounded_rectangle((0, 0, 999, 171), 22, fill=255)
    im2.save(OUT + "/inset_mask.png")

def wrap(draw, text, fnt, maxw):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=fnt) <= maxw: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur)
    return lines

def caption(name, title, sub, accent=ACC):
    im = Image.new("RGBA", (W, 290), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    ft = font(66, True)
    tw = d.textlength(title, font=ft)
    x = (W - tw) / 2
    d.text((x, 22), title, font=ft, fill=WHITE)
    d.rounded_rectangle(((W - 120) / 2, 108, (W + 120) / 2, 114), 3, fill=accent)
    fs = font(36)
    y = 134
    for ln in wrap(d, sub, fs, 900)[:3]:
        d.text(((W - d.textlength(ln, font=fs)) / 2, y), ln, font=fs, fill=DIM)
        y += 46
    im.save(OUT + f"/cap_{name}.png")

def card(name, lines, size=None):
    """Titelkaart (intro/outro): lijst van (tekst, grootte, kleur, vet)."""
    im = gradient().convert("RGBA"); d = ImageDraw.Draw(im)
    total = sum(sz + 26 for _, sz, _, _ in lines)
    y = (H - total) // 2 + 60
    first = None
    for text, sz, col, bold in lines:
        f = font(sz, bold)
        if text and first is None: first = y
        for ln in wrap(d, text, f, 940):
            d.text(((W - d.textlength(ln, font=f)) / 2, y), ln, font=f, fill=col)
            y += int(sz * 1.18)
        y += 26
    im.save(OUT + f"/card_{name}.png")
    return first

def logo(name):
    """Klein merkteken: de lijngrafiek uit het app-icoon."""
    im = Image.new("RGBA", (220, 220), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 219, 219), 52, fill=(17, 26, 34))
    d.line([(40, 150), (80, 105), (110, 125), (150, 65), (180, 95)], fill=ACC, width=16, joint="curve")
    im.save(OUT + f"/{name}.png")

if __name__ == "__main__":
    bg(); logo("logo")
