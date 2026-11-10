"""Build mistri's icons from the GITAV logo plus a gear badge. Run: python branding/make_icons.py"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DARK = (35, 114, 40, 255)      # GITAV dark green
LIGHT = (158, 188, 64, 255)    # GITAV light green
S = 2048                        # master size

logo = Image.open(ROOT / "branding" / "gitav-logo-source.png").convert("RGBA")
logo = logo.crop(logo.getbbox())


def gear(size, teeth=8, fill=(255, 255, 255, 255)):
    """Gear with a hole, drawn supersampled."""
    g = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    c = size / 2
    r_out, r_in, r_hole = size * .48, size * .36, size * .15
    pts = []
    steps = teeth * 4
    for i in range(steps):
        a0 = 2 * math.pi * i / steps - math.pi / 2
        r = r_out if i % 4 in (1, 2) else r_in
        # slightly narrower tooth tips for a crisp silhouette
        da = (math.pi / steps) * (0.18 if i % 4 in (1, 2) else -0.18)
        a = a0 + (da if i % 4 == 1 else -da if i % 4 == 2 else 0)
        pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    d.polygon(pts, fill=fill)
    d.ellipse([c - r_in * .98, c - r_in * .98, c + r_in * .98, c + r_in * .98], fill=fill)
    d.ellipse([c - r_hole, c - r_hole, c + r_hole, c + r_hole], fill=(0, 0, 0, 0))
    return g


def master(badge=True):
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    pad = int(S * .02)
    lg = logo.resize((S - 2 * pad, S - 2 * pad), Image.LANCZOS)
    canvas.alpha_composite(lg, (pad, pad))
    if badge:
        b = int(S * .46)                       # badge diameter
        x0 = y0 = S - b
        ring = int(b * .09)                    # white ring separates badge from logo
        disc = Image.new("RGBA", (b, b), (0, 0, 0, 0))
        dd = ImageDraw.Draw(disc)
        dd.ellipse([0, 0, b - 1, b - 1], fill=(255, 255, 255, 255))
        dd.ellipse([ring, ring, b - 1 - ring, b - 1 - ring], fill=DARK)
        gs = int(b * .62)
        disc.alpha_composite(gear(gs), ((b - gs) // 2, (b - gs) // 2))
        canvas.alpha_composite(disc, (x0, y0))
    return canvas


def small(size):
    """At 16–24 px the full logo turns to mush, so small sizes show the gear badge
    on a simplified GITAV mark: four light-green rings."""
    m = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(m)
    r, w = S * .2, S * .085
    for cx, cy in ((S * .26, S * .26), (S * .74, S * .26), (S * .26, S * .74)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=LIGHT, width=int(w))
    b = int(S * .56)
    x0 = y0 = S - b
    d.ellipse([x0, y0, S - 1, S - 1], fill=(255, 255, 255, 255))
    ring = int(b * .08)
    d.ellipse([x0 + ring, y0 + ring, S - 1 - ring, S - 1 - ring], fill=DARK)
    gs = int(b * .64)
    m.alpha_composite(gear(gs, teeth=7), (x0 + (b - gs) // 2, y0 + (b - gs) // 2))
    return m.resize((size, size), Image.LANCZOS)


full = master()
out = ROOT / "mistri" / "assets"
out.mkdir(parents=True, exist_ok=True)
sizes = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
frames = [small(s) if s <= 24 else full.resize((s, s), Image.LANCZOS) for s in sizes]
frames[-1].save(out / "mistri.ico", sizes=[(s, s) for s in sizes], append_images=frames[:-1])
for s in (32, 48, 64, 96, 128, 256):
    (small(s) if s <= 24 else full.resize((s, s), Image.LANCZOS)).save(out / f"mistri-{s}.png")
full.resize((1024, 1024), Image.LANCZOS).save(ROOT / "branding" / "mistri-icon-1024.png")
ext = ROOT / "extension" / "icons"
for s in (16, 32, 48, 128):
    (small(s) if s <= 24 else full.resize((s, s), Image.LANCZOS)).save(ext / f"{s}.png")
# Installer sidebar art (164x314 BMP) and header (150x57 BMP)
side = Image.new("RGB", (164, 314), (255, 255, 255))
side.paste(full.resize((124, 124), Image.LANCZOS), (20, 40), full.resize((124, 124), Image.LANCZOS))
side.save(ROOT / "branding" / "installer-side.bmp")
head = Image.new("RGB", (150, 57), (255, 255, 255))
h = full.resize((49, 49), Image.LANCZOS)
head.paste(h, (97, 4), h)
head.save(ROOT / "branding" / "installer-header.bmp")
print("icons written")
