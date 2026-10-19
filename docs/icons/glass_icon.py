"""Renders the liquid glass launcher layers from the BitChord Dial logo grid.

Usage: python3 docs/icons/glass_icon.py app/src/main/res docs/icons
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SS = 864  # supersampled canvas for 108 dp (8 px / dp)
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# The logo: 3 x 4 cells, 1 = lit, 0 = dim.
GRID = [
    [0, 1, 1],
    [0, 1, 0],
    [1, 1, 0],
    [1, 1, 0],
]
CELL_U, GAP_U = 40.0, 20.0
GRID_W_U, GRID_H_U = 3 * CELL_U + 2 * GAP_U, 4 * CELL_U + 3 * GAP_U
GRID_H_DP = 50.0  # tall enough to read, short enough to sit inside the 66 dp safe zone
U = GRID_H_DP * (SS / 108.0) / GRID_H_U
RADIUS = 8.0 * U


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def vgradient(size, top, bottom):
    """RGBA vertical gradient between two RGBA tuples."""
    w, h = size
    col = Image.new("RGBA", (1, h))
    px = col.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(4))
    return col.resize((w, h))


def radial(size, cx, cy, r, rgb, alpha):
    """A soft radial wash: rgb at alpha in the centre, fading to nothing at r."""
    w, h = size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    steps = 64
    for i in range(steps, 0, -1):
        rr = r * i / steps
        a = round(255 * alpha * (1 - i / steps) ** 1.6)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=a)
    mask = mask.filter(ImageFilter.GaussianBlur(r / 20))
    layer = Image.new("RGBA", (w, h), rgb + (0,))
    layer.putalpha(mask)
    return layer


def cell_boxes():
    ox = SS / 2 - GRID_W_U * U / 2
    oy = SS / 2 - GRID_H_U * U / 2
    for r, row in enumerate(GRID):
        for c, lit in enumerate(row):
            x0 = ox + c * (CELL_U + GAP_U) * U
            y0 = oy + r * (CELL_U + GAP_U) * U
            yield (x0, y0, x0 + CELL_U * U, y0 + CELL_U * U), bool(lit)


def rr_mask(box, inset=0.0, radius=RADIUS):
    m = Image.new("L", (SS, SS), 0)
    x0, y0, x1, y1 = box
    ImageDraw.Draw(m).rounded_rectangle(
        [x0 + inset, y0 + inset, x1 - inset, y1 - inset], radius=max(0, radius - inset), fill=255
    )
    return m


def scale_alpha(mask, a):
    return mask.point(lambda v: round(v * a))


def glass_tile(canvas, box, body_top, body_bottom, rim_top_a, rim_bottom_a,
               shadow_rgb, shadow_a, glow_a, tint_rgb, tint_a):
    x0, y0, x1, y1 = box
    h = y1 - y0
    full = rr_mask(box)

    # Soft glow around lit glass, the logo's own signature.
    if glow_a > 0:
        g = Image.new("RGBA", (SS, SS), (255, 255, 255, 0))
        g.putalpha(scale_alpha(full, glow_a).filter(ImageFilter.GaussianBlur(4.5 * U)))
        canvas.alpha_composite(g)

    # Drop shadow: the tile floating a little above the surface.
    if shadow_a > 0:
        s = Image.new("RGBA", (SS, SS), shadow_rgb + (0,))
        sm = Image.new("L", (SS, SS), 0)
        sm.paste(scale_alpha(full, shadow_a), (0, round(2.2 * U)))
        s.putalpha(sm.filter(ImageFilter.GaussianBlur(2.6 * U)))
        canvas.alpha_composite(s)

    # Body: frosted fill, brighter at the top where the light comes from.
    grad = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    strip = vgradient((1, round(h)), body_top, body_bottom).resize((SS, round(h)))
    grad.paste(strip, (0, round(y0)))
    a = ImageChops.multiply(grad.getchannel("A"), full)
    grad.putalpha(a)
    canvas.alpha_composite(grad)

    # Refraction: a tinted band along the lower inside edge.
    if tint_a > 0:
        band = ImageChops.subtract(full, rr_mask((x0, y0 - 3.2 * U, x1, y1 - 3.2 * U)))
        band = band.filter(ImageFilter.GaussianBlur(1.4 * U))
        band = ImageChops.multiply(band, full)
        t = Image.new("RGBA", (SS, SS), tint_rgb + (0,))
        t.putalpha(scale_alpha(band, tint_a))
        canvas.alpha_composite(t)

    # Specular: a soft highlight sweeping across the top-left of the pane.
    spec = Image.new("L", (SS, SS), 0)
    ImageDraw.Draw(spec).ellipse(
        [x0 - 0.35 * (x1 - x0), y0 - 0.9 * h, x1 + 0.15 * (x1 - x0), y0 + 0.42 * h], fill=255
    )
    spec = ImageChops.multiply(spec.filter(ImageFilter.GaussianBlur(2.0 * U)), rr_mask(box, 1.0 * U))
    sp = Image.new("RGBA", (SS, SS), (255, 255, 255, 0))
    sp.putalpha(scale_alpha(spec, 0.38))
    canvas.alpha_composite(sp)

    # Rim: a thin bright edge, strongest on top, catching the light.
    rim = ImageChops.subtract(full, rr_mask(box, 1.1 * U))
    rim_grad = Image.new("L", (SS, SS), 0)
    rs = vgradient((1, round(h)), (0, 0, 0, round(255 * rim_top_a)), (0, 0, 0, round(255 * rim_bottom_a)))
    rim_grad.paste(rs.getchannel("A").resize((SS, round(h))), (0, round(y0)))
    rl = Image.new("RGBA", (SS, SS), (255, 255, 255, 0))
    rl.putalpha(ImageChops.multiply(rim, rim_grad))
    canvas.alpha_composite(rl)


def light_background():
    bg = vgradient((SS, SS), hex_rgb("#fa5a72") + (255,), hex_rgb("#f9253d") + (255,))
    bg.alpha_composite(radial((SS, SS), SS * 0.18, SS * 0.08, SS * 0.75, (255, 255, 255), 0.30))
    bg.alpha_composite(radial((SS, SS), SS * 0.95, SS * 0.95, SS * 0.7, hex_rgb("#c8102e"), 0.45))
    bg.alpha_composite(radial((SS, SS), SS * 0.98, SS * 0.15, SS * 0.45, hex_rgb("#ff9fb0"), 0.30))
    return bg


def dark_background():
    bg = Image.new("RGBA", (SS, SS), hex_rgb("#121214") + (255,))
    bg.alpha_composite(radial((SS, SS), 0, SS * 0.15, SS * 0.65, hex_rgb("#fa5a72"), 0.30))
    bg.alpha_composite(radial((SS, SS), SS, SS * 0.85, SS * 0.7, hex_rgb("#8b5cf6"), 0.28))
    bg.alpha_composite(radial((SS, SS), SS, SS * 0.10, SS * 0.5, hex_rgb("#38bdf8"), 0.14))
    return bg


def light_foreground():
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    for box, lit in cell_boxes():
        if lit:
            glass_tile(fg, box, (255, 255, 255, 250), (255, 240, 243, 215), 1.0, 0.55,
                       hex_rgb("#8e0a22"), 0.40, 0.30, hex_rgb("#ff8fa3"), 0.55)
        else:
            glass_tile(fg, box, (255, 255, 255, 80), (255, 255, 255, 30), 0.75, 0.25,
                       hex_rgb("#8e0a22"), 0.18, 0.0, hex_rgb("#ffffff"), 0.25)
    return fg


def dark_foreground():
    fg = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    for box, lit in cell_boxes():
        if lit:
            glass_tile(fg, box, (255, 255, 255, 252), (238, 236, 244, 225), 1.0, 0.5,
                       (0, 0, 0), 0.55, 0.45, hex_rgb("#fa5a72"), 0.45)
        else:
            glass_tile(fg, box, (255, 255, 255, 34), (255, 255, 255, 12), 0.40, 0.10,
                       (0, 0, 0), 0.45, 0.0, hex_rgb("#8b5cf6"), 0.25)
    return fg


def save_all(res_dir, name, img):
    for dens, px in DENSITIES.items():
        d = os.path.join(res_dir, f"mipmap-{dens}")
        os.makedirs(d, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(os.path.join(d, f"{name}.webp"), "WEBP", lossless=True, quality=100, method=6)


def preview(bg, fg, path):
    """The icon as a squircle-ish launcher would show it, for review only."""
    full = bg.copy()
    full.alpha_composite(fg)
    mask = Image.new("L", (SS, SS), 0)
    inset = SS * (18 / 108)
    ImageDraw.Draw(mask).rounded_rectangle([inset, inset, SS - inset, SS - inset], radius=SS * 0.17, fill=255)
    out = Image.new("RGBA", (SS, SS), (0, 0, 0, 0))
    out.paste(full, (0, 0), mask)
    out.crop((round(inset), round(inset), round(SS - inset), round(SS - inset))).resize((360, 360), Image.LANCZOS).save(path)


if __name__ == "__main__":
    res, prev = sys.argv[1], sys.argv[2]
    os.makedirs(prev, exist_ok=True)
    lb, lf, db, df = light_background(), light_foreground(), dark_background(), dark_foreground()
    save_all(res, "ic_glass_light_background", lb)
    save_all(res, "ic_glass_light_foreground", lf)
    save_all(res, "ic_glass_dark_background", db)
    save_all(res, "ic_glass_dark_foreground", df)
    preview(lb, lf, os.path.join(prev, "glass_light.png"))
    preview(db, df, os.path.join(prev, "glass_dark.png"))
