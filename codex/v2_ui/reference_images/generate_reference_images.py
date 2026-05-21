from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parent
W, H = 1080, 1920


COLORS = {
    "bg": (10, 13, 16, 255),
    "panel": (27, 31, 36, 232),
    "panel_alt": (36, 42, 48, 214),
    "scrim": (0, 0, 0, 110),
    "text": (248, 250, 252, 255),
    "secondary": (203, 213, 225, 255),
    "muted": (148, 163, 184, 255),
    "accent": (85, 214, 190, 255),
    "record": (255, 59, 48, 255),
    "warning": (251, 191, 36, 255),
    "line": (255, 255, 255, 42),
}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/STHeiti Medium.ttc" if bold else "/System/Library/Fonts/STHeiti Light.ttc",
        "/System/Library/Fonts/Hiragino Sans GB.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/SFNS.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def latin_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/HelveticaNeue.ttc",
        "/System/Library/Fonts/Helvetica.ttc",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size=size)
        except OSError:
            continue
    return font(size, bold)


F = {
    "title": font(38, True),
    "brand": latin_font(38, True),
    "status": font(28),
    "label": font(30, True),
    "body": font(28),
    "small": font(24),
    "tiny": font(21),
    "mono": font(22),
}


def rounded(draw: ImageDraw.ImageDraw, xy, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def text(draw: ImageDraw.ImageDraw, xy, value, fill=COLORS["text"], f=None, anchor=None):
    draw.text(xy, value, font=f or F["body"], fill=fill, anchor=anchor)


def phone_base() -> Image.Image:
    img = Image.new("RGBA", (W, H), COLORS["bg"])
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        r = int(21 + 10 * t)
        g = int(35 + 34 * t)
        b = int(42 + 48 * (1 - t))
        draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

    # Abstract but camera-like preview: horizon, light, subtle grid.
    draw.rectangle((0, 0, W, H), fill=(0, 0, 0, 38))
    draw.polygon([(0, 1120), (360, 840), (710, 1070), (1080, 790), (1080, 1920), (0, 1920)], fill=(23, 64, 62, 180))
    draw.polygon([(0, 1200), (460, 980), (1080, 1190), (1080, 1920), (0, 1920)], fill=(41, 88, 76, 170))
    draw.ellipse((690, 250, 930, 490), fill=(255, 220, 146, 42))
    draw.line((0, 1180, W, 1180), fill=(255, 255, 255, 34), width=2)
    for x in (W // 3, 2 * W // 3):
        draw.line((x, 160, x, 1500), fill=(255, 255, 255, 18), width=1)
    for y in (520, 900, 1280):
        draw.line((70, y, W - 70, y), fill=(255, 255, 255, 18), width=1)
    return img.filter(ImageFilter.GaussianBlur(0.2))


def top_status(draw, mode="拍照", status="预览就绪", recording=False):
    rounded(draw, (36, 58, 1044, 176), 28, (0, 0, 0, 72))
    text(draw, (70, 88), "OpenCamera", f=F["brand"])
    text(draw, (294, 88), "·", f=F["title"])
    text(draw, (326, 88), mode, f=F["title"])
    if recording:
        draw.ellipse((70, 139, 90, 159), fill=COLORS["record"])
        text(draw, (104, 130), status, fill=COLORS["secondary"], f=F["status"])
    else:
        text(draw, (70, 130), status, fill=COLORS["secondary"], f=F["status"])
    rounded(draw, (790, 82, 1010, 148), 22, (12, 18, 22, 150), outline=COLORS["line"], width=2)
    text(draw, (900, 114), "镜头实验室", f=F["small"], anchor="mm")


def right_rail(draw, dev=True, active=None):
    labels = ["色调", "快捷"] + (["开发"] if dev else [])
    top = 540
    for idx, label in enumerate(labels):
        y = top + idx * 112
        fill = (0, 0, 0, 116)
        outline = COLORS["accent"] if active == label else COLORS["line"]
        rounded(draw, (942, y, 1030, y + 82), 22, fill, outline=outline, width=2)
        text(draw, (986, y + 42), label, f=F["small"], anchor="mm")


def zoom_strip(draw, selected="1x"):
    labels = ["0.7x", "1x", "2x", "5x"]
    x0 = 286
    y = 1320
    for i, label in enumerate(labels):
        x = x0 + i * 132
        active = label == selected
        fill = (230, 255, 249, 232) if active else (0, 0, 0, 106)
        outline = COLORS["accent"] if active else COLORS["line"]
        rounded(draw, (x, y, x + 96, y + 54), 27, fill, outline=outline, width=2)
        text(draw, (x + 48, y + 27), label, fill=(10, 18, 20, 255) if active else COLORS["text"], f=F["small"], anchor="mm")


def mode_track(draw, active="拍照"):
    labels = ["拍照", "文档", "人文", "人像", "专业", "视频"]
    x0 = 147
    y = 1404
    for i, label in enumerate(labels):
        x = x0 + i * 132
        if label == active:
            rounded(draw, (x - 18, y - 12, x + 90, y + 48), 22, (85, 214, 190, 48), outline=COLORS["accent"], width=2)
            fill = COLORS["text"]
        else:
            fill = COLORS["secondary"]
        text(draw, (x + 36, y + 18), label, fill=fill, f=F["small"], anchor="mm")


def bottom_cockpit(draw, recording=False):
    rounded(draw, (30, 1510, 1050, 1878), 42, (0, 0, 0, 142), outline=(255, 255, 255, 34), width=1)
    status = "录制中 00:12" if recording else "上次保存  IMG_160053.jpg"
    text(draw, (70, 1548), status, fill=COLORS["secondary"], f=F["status"])
    rounded(draw, (72, 1618, 208, 1754), 22, (240, 248, 255, 32), outline=COLORS["line"], width=2)
    rounded(draw, (88, 1634, 192, 1738), 18, (72, 120, 112, 210))
    draw.line((92, 1718, 188, 1645), fill=(255, 255, 255, 54), width=4)
    cx, cy = 540, 1690
    draw.ellipse((cx - 84, cy - 84, cx + 84, cy + 84), fill=(255, 255, 255, 230))
    if recording:
        draw.ellipse((cx - 68, cy - 68, cx + 68, cy + 68), fill=(255, 59, 48, 255))
        rounded(draw, (cx - 28, cy - 28, cx + 28, cy + 28), 10, (255, 255, 255, 245))
        text(draw, (cx, cy + 112), "停止", f=F["small"], anchor="mm")
    else:
        draw.ellipse((cx - 65, cy - 65, cx + 65, cy + 65), outline=(15, 23, 25, 255), width=5)
        text(draw, (cx, cy + 112), "快门", f=F["small"], anchor="mm")
    rounded(draw, (858, 1622, 1002, 1748), 28, (0, 0, 0, 110), outline=COLORS["line"], width=2)
    text(draw, (930, 1660), "镜头", f=F["small"], anchor="mm")
    text(draw, (930, 1702), "切换", fill=COLORS["secondary"], f=F["tiny"], anchor="mm")


def draw_main(filename, mode="拍照", active_mode="拍照", status="预览就绪", selected_zoom="1x", recording=False):
    img = phone_base()
    draw = ImageDraw.Draw(img)
    top_status(draw, mode=mode, status=status, recording=recording)
    right_rail(draw)
    zoom_strip(draw, selected=selected_zoom)
    mode_track(draw, active=active_mode)
    bottom_cockpit(draw, recording=recording)
    img.convert("RGB").save(ROOT / filename, quality=95)


def draw_panel_base(title, subtitle):
    img = phone_base()
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 88))
    img.alpha_composite(overlay)
    draw = ImageDraw.Draw(img)
    rounded(draw, (30, 580, 1050, 1888), 42, COLORS["panel"], outline=(255, 255, 255, 38), width=1)
    text(draw, (74, 630), title, f=F["title"])
    text(draw, (74, 684), subtitle, fill=COLORS["secondary"], f=F["status"])
    rounded(draw, (926, 622, 1010, 692), 24, (0, 0, 0, 96), outline=COLORS["line"], width=1)
    text(draw, (968, 657), "关闭", f=F["tiny"], anchor="mm")
    draw.line((70, 730, 1010, 730), fill=COLORS["line"], width=2)
    return img, draw


def tabs(draw, labels, active, y=760):
    x = 72
    for label in labels:
        w = 112 if len(label) <= 2 else 148
        is_active = label == active
        fill = (85, 214, 190, 52) if is_active else (255, 255, 255, 18)
        outline = COLORS["accent"] if is_active else COLORS["line"]
        rounded(draw, (x, y, x + w, y + 58), 26, fill, outline=outline, width=2)
        text(draw, (x + w / 2, y + 29), label, f=F["small"], anchor="mm")
        x += w + 16


def row(draw, y, label, value, state="支持", support="", state_color=None, enabled=True):
    fill = COLORS["panel_alt"] if enabled else (32, 36, 40, 180)
    rounded(draw, (70, y, 1010, y + 118), 18, fill, outline=(255, 255, 255, 26), width=1)
    text(draw, (102, y + 24), label, f=F["body"], fill=COLORS["text"] if enabled else COLORS["muted"])
    text(draw, (738, y + 24), value, f=F["small"], fill=COLORS["secondary"], anchor=None)
    badge_color = state_color or (85, 214, 190, 44)
    rounded(draw, (890, y + 18, 982, y + 56), 18, badge_color, outline=None)
    text(draw, (936, y + 37), state, f=F["tiny"], anchor="mm", fill=COLORS["text"] if enabled else COLORS["muted"])
    if support:
        text(draw, (102, y + 72), support, fill=COLORS["muted"], f=F["tiny"])


def draw_tone_panel():
    img, draw = draw_panel_base("色调", "当前色调 · 人文")
    tabs(draw, ["拍照", "人文", "人像", "视频"], "人文")
    row(draw, 850, "滤镜", "自然", "已选", "轻微暖色和柔和对比")
    text(draw, (78, 1008), "调色板", f=F["label"])
    px = (74, 1062, 1006, 1338)
    rounded(draw, px, 24, (0, 0, 0, 90), outline=COLORS["line"], width=1)
    # Palette gradient.
    palette = Image.new("RGBA", (px[2] - px[0], px[3] - px[1]), (0, 0, 0, 0))
    pd = ImageDraw.Draw(palette)
    for x in range(palette.width):
        for y in range(palette.height):
            tx = x / max(1, palette.width - 1)
            ty = y / max(1, palette.height - 1)
            r = int(70 + 110 * tx + 40 * (1 - ty))
            g = int(128 + 55 * (1 - abs(tx - 0.5) * 2) + 30 * (1 - ty))
            b = int(165 + 70 * (1 - tx) - 60 * ty)
            pd.point((x, y), fill=(r, g, max(40, b), 255))
    mask = Image.new("L", palette.size, 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((0, 0, palette.width, palette.height), radius=24, fill=255)
    palette.putalpha(mask)
    img.alpha_composite(palette, (px[0], px[1]))
    draw.ellipse((518, 1168, 562, 1212), outline=(255, 255, 255, 240), width=5)
    draw.ellipse((529, 1179, 551, 1201), fill=COLORS["accent"])
    row(draw, 1390, "进阶", "亮度 +4", "支持", "对比、饱和、色温、暗角等")
    row(draw, 1528, "保存", "自定义副本", "可用", "保存为自定义色调")
    text(draw, (74, 1818), "外部点击关闭 · 面板内滑动只调整色调", fill=COLORS["muted"], f=F["tiny"])
    img.convert("RGB").save(ROOT / "tone_lab_v2_panel.png", quality=95)


def draw_lens_panel():
    img, draw = draw_panel_base("镜头实验室", "拍照设置 · 当前镜头支持")
    tabs(draw, ["拍照", "录像", "通用"], "拍照")
    row(draw, 850, "比例", "4:3", "支持", "影响照片构图和预览裁切")
    row(draw, 986, "Live", "开启", "支持", "拍照时同时保存动态片段")
    row(draw, 1122, "倒计时", "关闭", "支持", "可用于自拍或稳定拍摄")
    row(draw, 1258, "色调", "自然", "支持", "打开色调面板调整滤镜和调色板")
    row(draw, 1394, "水印", "当前默认", "支持", "选择模板或调整样式")
    row(draw, 1530, "设备能力", "后置主摄", "降级", "部分高倍率按设备能力降级", state_color=(251, 191, 36, 72))
    text(draw, (74, 1818), "部分设置会重新配置预览。录制中暂不可调整。", fill=COLORS["muted"], f=F["tiny"])
    img.convert("RGB").save(ROOT / "lens_lab_v2_panel.png", quality=95)


def draw_pro_panel():
    img, draw = draw_panel_base("专业控制", "当前设备能力")
    tabs(draw, ["曝光", "对焦", "输出"], "曝光")
    row(draw, 850, "RAW", "关闭", "不支持", "当前设备未提供 RAW 输出能力", state_color=(248, 113, 113, 72), enabled=False)
    row(draw, 986, "ISO", "Auto", "支持", "自动或手动感光度")
    slider(draw, 1126, "S", "1/120", 0.58)
    slider(draw, 1282, "EV", "0.0", 0.50)
    row(draw, 1438, "Focus", "Auto", "支持", "支持自动对焦，手动对焦按设备能力显示")
    row(draw, 1574, "WB", "自动", "支持", "白平衡自动或预设")
    text(draw, (74, 1818), "专业参数按当前设备能力显示。", fill=COLORS["muted"], f=F["tiny"])
    img.convert("RGB").save(ROOT / "pro_controls_v2_panel.png", quality=95)


def slider(draw, y, label, value, pos):
    rounded(draw, (70, y, 1010, y + 132), 18, COLORS["panel_alt"], outline=(255, 255, 255, 26), width=1)
    text(draw, (102, y + 22), label, f=F["body"])
    text(draw, (870, y + 22), value, f=F["small"], fill=COLORS["secondary"])
    x1, x2 = 218, 940
    yy = y + 86
    draw.line((x1, yy, x2, yy), fill=(255, 255, 255, 48), width=8)
    draw.line((x1, yy, x1 + int((x2 - x1) * pos), yy), fill=COLORS["accent"], width=8)
    knob_x = x1 + int((x2 - x1) * pos)
    draw.ellipse((knob_x - 18, yy - 18, knob_x + 18, yy + 18), fill=COLORS["text"])


def main():
    draw_main("camera_cockpit_v2_main.png")
    draw_main(
        "camera_cockpit_v2_recording.png",
        mode="视频",
        active_mode="视频",
        status="录制 00:12",
        selected_zoom="1x",
        recording=True,
    )
    draw_tone_panel()
    draw_lens_panel()
    draw_pro_panel()


if __name__ == "__main__":
    main()
