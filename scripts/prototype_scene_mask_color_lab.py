from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Tuple

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path("/Volumes/Extreme_SSD/project/open_camera")
OUT_DIR = ROOT / "codex" / "visual_checks" / "scene_mask_color_lab"

IMAGES = [
    (
        "day_portrait",
        Path("/Volumes/Extreme_SSD/好图/AIGC/mygo_20260509_china_cute_barefoot_5scenes_84b8d080_001_副本.png"),
    ),
    (
        "night_portrait",
        Path("/Volumes/Extreme_SSD/好图/AIGC/mygo_20260512_night_contemplation_85d04f1a_001_副本.png"),
    ),
]


@dataclass(frozen=True)
class ColorRecipe:
    name: str
    brightness: float
    contrast: float
    saturation: float
    warmth: float
    tint: float
    shadow_lift: float
    highlight_compression: float


WARM_DEEP = ColorRecipe(
    name="warm_deep",
    brightness=-7.0,
    contrast=1.16,
    saturation=1.16,
    warmth=16.0,
    tint=-2.0,
    shadow_lift=0.04,
    highlight_compression=0.12,
)


def clamp(v: float) -> int:
    return int(max(0, min(255, round(v))))


def draw_day_mask(size: Tuple[int, int]) -> Image.Image:
    w, h = size
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)

    def box(x0, y0, x1, y1):
        return [int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)]

    d.ellipse(box(0.45, 0.07, 0.75, 0.29), fill=255)
    d.ellipse(box(0.30, 0.23, 0.86, 0.69), fill=255)
    d.ellipse(box(0.11, 0.10, 0.43, 0.43), fill=255)
    d.line(
        [
            (int(0.37 * w), int(0.34 * h)),
            (int(0.21 * w), int(0.23 * h)),
            (int(0.33 * w), int(0.17 * h)),
        ],
        fill=255,
        width=max(42, int(w * 0.07)),
        joint="curve",
    )
    d.ellipse(box(0.18, 0.57, 0.65, 0.98), fill=255)
    d.ellipse(box(0.44, 0.59, 0.98, 0.94), fill=255)
    d.rectangle(box(0.33, 0.47, 0.83, 0.69), fill=255)
    return mask.filter(ImageFilter.GaussianBlur(radius=max(10, int(w * 0.025))))


def draw_night_mask(size: Tuple[int, int]) -> Image.Image:
    w, h = size
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)

    def box(x0, y0, x1, y1):
        return [int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)]

    d.ellipse(box(0.42, 0.04, 0.94, 0.58), fill=255)
    d.ellipse(box(0.38, 0.38, 1.06, 1.05), fill=255)
    d.ellipse(box(0.38, 0.53, 0.70, 0.83), fill=255)
    d.polygon(
        [
            (int(0.51 * w), int(0.50 * h)),
            (int(0.72 * w), int(0.56 * h)),
            (int(0.71 * w), int(0.77 * h)),
            (int(0.49 * w), int(0.73 * h)),
        ],
        fill=255,
    )
    return mask.filter(ImageFilter.GaussianBlur(radius=max(10, int(w * 0.024))))


def make_mask(label: str, size: Tuple[int, int]) -> Image.Image:
    if label == "day_portrait":
        return draw_day_mask(size)
    return draw_night_mask(size)


def apply_tone(v: float, recipe: ColorRecipe) -> float:
    adjusted = v
    if recipe.highlight_compression > 0 and adjusted > 168:
        target = 168 + (adjusted - 168) * (1 - recipe.highlight_compression)
        adjusted = adjusted * (1 - recipe.highlight_compression) + target * recipe.highlight_compression
    if recipe.shadow_lift > 0 and adjusted < 92:
        target = adjusted + (92 - adjusted) * recipe.shadow_lift
        adjusted = adjusted * (1 - recipe.shadow_lift) + target * recipe.shadow_lift
    return (adjusted - 128) * recipe.contrast + 128 + recipe.brightness


def adjust_pixel(rgb: Tuple[int, int, int], recipe: ColorRecipe, amount: float) -> Tuple[int, int, int]:
    r, g, b = rgb
    gray = r * 0.299 + g * 0.587 + b * 0.114
    rr = gray + (r - gray) * recipe.saturation
    gg = gray + (g - gray) * recipe.saturation
    bb = gray + (b - gray) * recipe.saturation

    rr = apply_tone(rr, recipe) + recipe.warmth + recipe.tint * 0.6
    gg = apply_tone(gg, recipe) - recipe.tint
    bb = apply_tone(bb, recipe) - recipe.warmth + recipe.tint * 0.6

    # Blend with the original according to the local mask-aware strength.
    return (
        clamp(r * (1 - amount) + rr * amount),
        clamp(g * (1 - amount) + gg * amount),
        clamp(b * (1 - amount) + bb * amount),
    )


def apply_global(img: Image.Image, recipe: ColorRecipe) -> Image.Image:
    src = img.convert("RGB")
    out = Image.new("RGB", src.size)
    inp = src.load()
    pix = out.load()
    w, h = src.size
    for y in range(h):
        for x in range(w):
            pix[x, y] = adjust_pixel(inp[x, y], recipe, 1.0)
    return out


def apply_mask_aware(img: Image.Image, mask: Image.Image, recipe: ColorRecipe) -> Image.Image:
    src = img.convert("RGB")
    out = Image.new("RGB", src.size)
    inp = src.load()
    mp = mask.load()
    pix = out.load()
    w, h = src.size
    for y in range(h):
        for x in range(w):
            subject = mp[x, y] / 255.0
            # Stronger background render, protected subject render, feathered by mask.
            amount = 0.28 * subject + 1.0 * (1.0 - subject)
            pix[x, y] = adjust_pixel(inp[x, y], recipe, amount)
    return out


def mask_preview(img: Image.Image, mask: Image.Image) -> Image.Image:
    base = img.convert("RGB")
    overlay = Image.new("RGB", base.size, (255, 180, 40))
    alpha = mask.point(lambda v: int(v * 0.42))
    return Image.composite(overlay, base, alpha)


def resize_for_sheet(img: Image.Image, target_w: int = 360) -> Image.Image:
    w, h = img.size
    target_h = int(h * target_w / w)
    return img.resize((target_w, target_h), Image.Resampling.LANCZOS)


def add_label(img: Image.Image, label: str) -> Image.Image:
    pad = 34
    out = Image.new("RGB", (img.width, img.height + pad), (18, 18, 18))
    out.paste(img, (0, pad))
    d = ImageDraw.Draw(out)
    try:
        font = ImageFont.truetype("Arial.ttf", 16)
    except Exception:
        font = ImageFont.load_default()
    d.text((10, 9), label, fill=(245, 245, 245), font=font)
    return out


def avg_delta(before: Image.Image, after: Image.Image, mask: Image.Image, foreground: bool) -> float:
    b = before.convert("RGB").resize((180, int(before.height * 180 / before.width)), Image.Resampling.BILINEAR)
    a = after.convert("RGB").resize(b.size, Image.Resampling.BILINEAR)
    m = mask.resize(b.size, Image.Resampling.BILINEAR)
    bp = b.load()
    ap = a.load()
    mp = m.load()
    total = 0.0
    count = 0
    for y in range(b.height):
        for x in range(b.width):
            is_fg = mp[x, y] >= 150
            if is_fg == foreground:
                r0, g0, b0 = bp[x, y]
                r1, g1, b1 = ap[x, y]
                total += (abs(r1 - r0) + abs(g1 - g0) + abs(b1 - b0)) / 3.0
                count += 1
    return total / max(1, count)


def hstack(images: Iterable[Image.Image]) -> Image.Image:
    imgs = list(images)
    h = max(img.height for img in imgs)
    w = sum(img.width for img in imgs)
    out = Image.new("RGB", (w, h), (24, 24, 24))
    x = 0
    for img in imgs:
        out.paste(img, (x, 0))
        x += img.width
    return out


def vstack(images: Iterable[Image.Image]) -> Image.Image:
    imgs = list(images)
    w = max(img.width for img in imgs)
    h = sum(img.height for img in imgs)
    out = Image.new("RGB", (w, h), (24, 24, 24))
    y = 0
    for img in imgs:
        out.paste(img, (0, y))
        y += img.height
    return out


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = []
    report_lines = [
        "# Scene Mask Color Lab Prototype",
        "",
        "This is an offline PIL prototype. It uses hand-drawn approximate masks, not a real ML model.",
        "The purpose is to compare global color rendering with mask-aware subject protection.",
        "",
    ]

    for label, path in IMAGES:
        img = Image.open(path).convert("RGB")
        mask = make_mask(label, img.size)
        global_img = apply_global(img, WARM_DEEP)
        aware_img = apply_mask_aware(img, mask, WARM_DEEP)
        mask_img = mask_preview(img, mask)

        for suffix, output in [
            ("mask_preview", mask_img),
            ("global_warm_deep", global_img),
            ("mask_aware_warm_deep", aware_img),
        ]:
            output.save(OUT_DIR / f"{label}_{suffix}.jpg", quality=94)

        global_fg = avg_delta(img, global_img, mask, True)
        global_bg = avg_delta(img, global_img, mask, False)
        aware_fg = avg_delta(img, aware_img, mask, True)
        aware_bg = avg_delta(img, aware_img, mask, False)
        report_lines += [
            f"## {label}",
            "",
            f"- Global avg delta subject/background: {global_fg:.1f} / {global_bg:.1f}",
            f"- Mask-aware avg delta subject/background: {aware_fg:.1f} / {aware_bg:.1f}",
            "",
        ]

        row = hstack(
            [
                add_label(resize_for_sheet(img), f"{label} original"),
                add_label(resize_for_sheet(mask_img), "approx subject mask"),
                add_label(resize_for_sheet(global_img), "global warm deep"),
                add_label(resize_for_sheet(aware_img), "mask-aware warm deep"),
            ]
        )
        rows.append(row)

    sheet = vstack(rows)
    sheet.save(OUT_DIR / "scene_mask_color_lab_contact_sheet.jpg", quality=94)
    (OUT_DIR / "report.md").write_text("\n".join(report_lines), encoding="utf-8")


if __name__ == "__main__":
    main()
