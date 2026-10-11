"""
Generates the PlantationLedger launcher icon from one shape list:
  - SVG files + an HTML preview sheet (rendered with headless Chrome)
  - Android adaptive-icon vector drawables (background, foreground, monochrome)

Canvas is the 108x108 adaptive-icon grid; the visible area is the central 72x72 and the
guaranteed-safe zone is a circle of radius 33 around (54, 54).
"""
import math, os, sys

OUT = os.path.dirname(os.path.abspath(__file__))
RES = sys.argv[1] if len(sys.argv) > 1 else None

# ---------------------------------------------------------------- palette (app theme)
BG_TOP, BG_BOTTOM = "#1D7A4D", "#0D3B26"      # EmeraldPrimary -> EmeraldDeep
LEAF_LIGHT, LEAF_DARK, LEAF_RIB = "#5FBF6A", "#2E8B47", "#1E6B36"
POD_LIGHT, POD_DARK, POD_RIB, POD_TIP, POD_OUTLINE = "#D9EDA8", "#8DBB45", "#5E8A27", "#4A6B1E", "#3F6418"
PAGE, PAGE_SHADE, GOLD = "#FFF8E1", "#EFE3BD", "#D4920A"


def f(x):
    return f"{x:.2f}".rstrip("0").rstrip(".")


def rot(px, py, deg, ox=0.0, oy=0.0):
    a = math.radians(deg)
    return (ox + px * math.cos(a) - py * math.sin(a), oy + px * math.sin(a) + py * math.cos(a))


class Shape:
    """A path in local coords, placed by translate (tx, ty) + rotate(deg) around the origin."""

    def __init__(self, d, fill=None, stroke=None, width=0.0, tx=0.0, ty=0.0, deg=0.0, grad=None, mono=True, cap="round"):
        self.d, self.fill, self.stroke, self.width = d, fill, stroke, width
        self.tx, self.ty, self.deg, self.grad, self.mono, self.cap = tx, ty, deg, grad, mono, cap

    def svg(self):
        fill = f"url(#{self.grad[0]})" if self.grad else (self.fill or "none")
        stroke = f' stroke="{self.stroke}" stroke-width="{f(self.width)}" stroke-linecap="{self.cap}" stroke-linejoin="round"' if self.stroke else ""
        return f'<path transform="translate({f(self.tx)} {f(self.ty)}) rotate({f(self.deg)})" d="{self.d}" fill="{fill}"{stroke}/>'

    def vector(self, mono=False):
        attrs = [f'android:pathData="{self.d}"']
        inner = ""
        if mono:
            if self.stroke and not self.fill and not self.grad:
                attrs += ['android:strokeColor="#FF000000"', f'android:strokeWidth="{f(self.width)}"', f'android:strokeLineCap="{self.cap}"']
            else:
                attrs.append('android:fillColor="#FF000000"')
        else:
            if self.grad:
                gid, x1, y1, x2, y2, c1, c2 = self.grad
                inner = (
                    '\n            <aapt:attr name="android:fillColor">\n'
                    f'                <gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" android:endX="{f(x2)}" android:endY="{f(y2)}"\n'
                    f'                    android:startColor="{c1}" android:endColor="{c2}"/>\n'
                    "            </aapt:attr>\n        "
                )
            elif self.fill:
                attrs.append(f'android:fillColor="{self.fill}"')
            if self.stroke:
                attrs += [f'android:strokeColor="{self.stroke}"', f'android:strokeWidth="{f(self.width)}"',
                          f'android:strokeLineCap="{self.cap}"', 'android:strokeLineJoin="round"']
        body = " ".join(attrs)
        path = f"<path {body}>{inner}</path>" if inner else f"<path {body}/>"
        return (f'    <group android:translateX="{f(self.tx)}" android:translateY="{f(self.ty)}" android:rotation="{f(self.deg)}">\n'
                f"        {path}\n    </group>")


# ---------------------------------------------------------------- shapes
def leaf(base_x, base_y, deg, length, width, gid):
    L, W = length, width
    body = (f"M0,0 C{f(W)},{f(-L*0.25)} {f(W*0.85)},{f(-L*0.7)} 0,{f(-L)} "
            f"C{f(-W*0.85)},{f(-L*0.7)} {f(-W)},{f(-L*0.25)} 0,0 Z")
    rib = f"M0,{f(-L*0.04)} Q{f(W*0.12)},{f(-L*0.5)} 0,{f(-L*0.93)}"
    return [
        Shape(body, tx=base_x, ty=base_y, deg=deg, grad=(gid, 0, 0, 0, -L, LEAF_DARK, LEAF_LIGHT)),
        Shape(rib, stroke=LEAF_RIB, width=1.1, tx=base_x, ty=base_y, deg=deg, mono=False),
    ]


def pod(base_x, base_y, deg, length, width, gid):
    """A plump cardamom pod on a short stem; local origin is the stem base, pod points up."""
    h, w = length / 2, width / 2
    k = w / 0.75
    gap = 2.4                     # stem length
    cy = -(gap + h)               # pod centre (local)
    top, bot = cy - h, cy + h
    body = (f"M0,{f(top)} C{f(k)},{f(cy - h*0.72)} {f(k)},{f(cy + h*0.58)} 0,{f(bot)} "
            f"C{f(-k)},{f(cy + h*0.58)} {f(-k)},{f(cy - h*0.72)} 0,{f(top)} Z")
    rib1 = f"M{f(w*0.05)},{f(top + 1.6)} C{f(w*0.62)},{f(cy - h*0.35)} {f(w*0.62)},{f(cy + h*0.35)} {f(w*0.05)},{f(bot - 1.2)}"
    rib2 = f"M{f(-w*0.05)},{f(top + 1.6)} C{f(-w*0.42)},{f(cy - h*0.35)} {f(-w*0.42)},{f(cy + h*0.35)} {f(-w*0.05)},{f(bot - 1.2)}"
    shine = (f"M{f(-w*0.55)},{f(cy - h*0.35)} C{f(-w*0.75)},{f(cy - h*0.05)} {f(-w*0.7)},{f(cy + h*0.3)} "
             f"{f(-w*0.45)},{f(cy + h*0.45)}")
    stem = f"M0,0 L0,{f(-gap - 0.6)}"
    tip = f"M{f(-1.1)},{f(top + 0.6)} L0,{f(top - 1.6)} L{f(1.1)},{f(top + 0.6)} Z"
    return [
        Shape(stem, stroke=POD_TIP, width=1.6, tx=base_x, ty=base_y, deg=deg),
        Shape(body, stroke=POD_OUTLINE, width=0.9, tx=base_x, ty=base_y, deg=deg, grad=(gid, -w, cy, w, cy, POD_LIGHT, POD_DARK)),
        Shape(rib1, stroke=POD_RIB, width=0.75, tx=base_x, ty=base_y, deg=deg, mono=False),
        Shape(rib2, stroke=POD_RIB, width=0.6, tx=base_x, ty=base_y, deg=deg, mono=False),
        Shape(shine, stroke="#F4FBE3", width=1.0, tx=base_x, ty=base_y, deg=deg, mono=False),
        Shape(tip, fill=POD_TIP, tx=base_x, ty=base_y, deg=deg),
    ]


def ledger(dy=-3.0, sx=0.88):
    """An open ledger book with gold ruled lines, centred under the pods."""
    X = lambda x: f(54 + (x - 54) * sx)
    Y = lambda y: f(y + dy)
    left = f"M{X(54)},{Y(67.5)} C{X(47.5)},{Y(63.8)} {X(40)},{Y(63.6)} {X(33.5)},{Y(65.6)} L{X(33.5)},{Y(78.6)} C{X(40)},{Y(76.8)} {X(47.5)},{Y(77.2)} {X(54)},{Y(80.6)} Z"
    right = f"M{X(54)},{Y(67.5)} C{X(60.5)},{Y(63.8)} {X(68)},{Y(63.6)} {X(74.5)},{Y(65.6)} L{X(74.5)},{Y(78.6)} C{X(68)},{Y(76.8)} {X(60.5)},{Y(77.2)} {X(54)},{Y(80.6)} Z"
    cover = f"M{X(31.6)},{Y(66.4)} L{X(31.6)},{Y(80.4)} C{X(40)},{Y(78.6)} {X(47.5)},{Y(79.4)} {X(54)},{Y(82.6)} C{X(60.5)},{Y(79.4)} {X(68)},{Y(78.6)} {X(76.4)},{Y(80.4)} L{X(76.4)},{Y(66.4)}"
    lines = []
    for off in (3.6, 6.6, 9.6):
        lines.append(Shape(f"M{X(37)},{Y(65.9 + off)} C{X(42)},{Y(64.9 + off)} {X(46.5)},{Y(65.3 + off)} {X(50.5)},{Y(66.8 + off)}", stroke=GOLD, width=0.85, mono=False))
        lines.append(Shape(f"M{X(71)},{Y(65.9 + off)} C{X(66)},{Y(64.9 + off)} {X(61.5)},{Y(65.3 + off)} {X(57.5)},{Y(66.8 + off)}", stroke=GOLD, width=0.85, mono=False))
    return [
        Shape(cover, stroke=GOLD, width=1.8),
        Shape(left, fill=PAGE),
        Shape(right, fill=PAGE_SHADE),
        Shape(f"M{X(54)},{Y(67.5)} L{X(54)},{Y(80.6)}", stroke="#C9B98A", width=0.8, mono=False),
        *lines,
    ]


def foreground():
    shapes = []
    shapes += leaf(48, 66, 42, 44, 8, "leafR")
    shapes += leaf(60, 66, -44, 37, 7, "leafL")
    base_x, base_y = 54, 62.5
    shapes += pod(base_x, base_y, -40, 23, 12, "podL")
    shapes += pod(base_x, base_y, 40, 23, 12, "podR")
    shapes += pod(base_x, base_y, 0, 25, 12.5, "podC")
    shapes += ledger()
    return shapes


# ---------------------------------------------------------------- writers
def defs(shapes):
    out = []
    for s in shapes:
        if s.grad:
            gid, x1, y1, x2, y2, c1, c2 = s.grad
            # gradient coords are in the shape's local space -> userSpaceOnUse inside the transformed path
            out.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">'
                       f'<stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/></linearGradient>')
    return "".join(out)


def background_svg():
    return (f'<linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient>'
            f'<radialGradient id="glow" cx="54" cy="50" r="40" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="#FFFFFF" stop-opacity="0.16"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>',
            '<rect width="108" height="108" fill="url(#bg)"/><rect width="108" height="108" fill="url(#glow)"/>')


def full_svg(mono=False):
    fg = foreground()
    if mono:
        paths = "".join(
            Shape(s.d, fill=None if (s.stroke and not s.fill and not s.grad) else "#FFFFFF",
                  stroke="#FFFFFF" if (s.stroke and not s.fill and not s.grad) else None,
                  width=s.width, tx=s.tx, ty=s.ty, deg=s.deg).svg()
            for s in fg if s.mono)
        return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" fill="#33473A"/>{paths}</svg>'
    bg_defs, bg = background_svg()
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>{bg_defs}{defs(fg)}</defs>'
            f'{bg}{"".join(s.svg() for s in fg)}</svg>')


VEC_HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by make_icon.py: cardamom pods and leaves over an open ledger. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')


def vector_foreground(mono=False):
    shapes = [s for s in foreground() if (s.mono or not mono)]
    return VEC_HEAD + "\n".join(s.vector(mono) for s in shapes) + "\n</vector>\n"


def vector_background():
    return (VEC_HEAD +
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108"\n'
            f'                android:startColor="{BG_TOP}" android:endColor="{BG_BOTTOM}"/>\n'
            '        </aapt:attr>\n    </path>\n'
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr name="android:fillColor">\n'
            '            <gradient android:type="radial" android:centerX="54" android:centerY="50" android:gradientRadius="40"\n'
            '                android:startColor="#29FFFFFF" android:endColor="#00FFFFFF"/>\n'
            '        </aapt:attr>\n    </path>\n</vector>\n')


if __name__ == "__main__":
    color, mono = full_svg(), full_svg(mono=True)
    open(os.path.join(OUT, "icon.svg"), "w", encoding="utf-8").write(color)
    open(os.path.join(OUT, "icon_mono.svg"), "w", encoding="utf-8").write(mono)
    tile = lambda svg, size, radius, bg: (
        f'<div style="display:inline-block;margin:10px;text-align:center;font:12px sans-serif;color:#555">'
        f'<div style="width:{size}px;height:{size}px;border-radius:{radius};overflow:hidden;box-shadow:0 2px 6px #0004;background:{bg}">'
        f'<div style="width:{size*1.5}px;height:{size*1.5}px;margin:-{size*0.25}px">{svg}</div></div></div>')
    html = ('<html><body style="margin:0;padding:16px;background:#ECEFF1">'
            '<div>' + tile(color, 200, "50%", "#000") + tile(color, 200, "28%", "#000") + tile(color, 200, "12px", "#000") + '</div>'
            '<div>' + "".join(tile(color, s, "50%", "#000") for s in (96, 64, 48, 36)) + tile(mono, 96, "50%", "#000") + '</div>'
            '<div style="background:#1C1B1F;padding:6px;display:inline-block">' + "".join(tile(color, s, "50%", "#000") for s in (64, 48)) + tile(mono, 64, "50%", "#000") + '</div>'
            '<div style="margin-top:10px">' + f'<div style="width:216px;height:216px;display:inline-block;margin:10px">{color}</div>'
            '<svg width="216" height="216" viewBox="0 0 108 108" style="position:relative;left:-242px;top:10px">'
            '<circle cx="54" cy="54" r="33" fill="none" stroke="red" stroke-width="0.4" stroke-dasharray="1.5 1"/>'
            '<circle cx="54" cy="54" r="36" fill="none" stroke="#fff" stroke-width="0.4" stroke-dasharray="1.5 1"/></svg></div>'
            '</body></html>')
    open(os.path.join(OUT, "preview.html"), "w", encoding="utf-8").write(html)
    if RES:
        open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w", encoding="utf-8").write(vector_foreground())
        open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w", encoding="utf-8").write(vector_background())
        open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w", encoding="utf-8").write(vector_foreground(mono=True))
        print("wrote Android resources to", RES)
    print("wrote preview to", OUT)
