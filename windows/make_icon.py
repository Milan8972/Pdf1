"""Generate windows/paperpilot.ico from the Android launcher foreground art (or draw Coral->Amber fallback)."""

from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw


def build_ico() -> Path:
    win_dir = Path(__file__).resolve().parent
    out_ico = win_dir / "paperpilot.ico"
    fg_png = win_dir.parent / "app" / "src" / "main" / "res" / "drawable" / "ic_paperpilot_fg_art.png"

    size = 256
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    # Coral (#FF6B6B) to Amber (#FFB020) vertical/diagonal gradient rounded square
    grad = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * (size - 1))
            r = int(255)
            g = int(107 + (176 - 107) * t)
            b = int(107 + (32 - 107) * t)
            grad.putpixel((x, y), (r, g, b, 255))

    mask = Image.new("L", (size, size), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle((8, 8, size - 8, size - 8), radius=64, fill=255)
    canvas.paste(grad, (0, 0), mask)

    if fg_png.exists():
        try:
            fg = Image.open(fg_png).convert("RGBA")
            fg = fg.resize((int(size * 0.88), int(size * 0.88)), Image.Resampling.LANCZOS)
            offset = (size - fg.width) // 2
            canvas.alpha_composite(fg, (offset, offset))
        except Exception:
            _draw_paper_plane(draw, size)
    else:
        _draw_paper_plane(draw, size)

    canvas.save(str(out_ico), format="ICO", sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
    return out_ico


def _draw_paper_plane(draw: ImageDraw.ImageDraw, size: int) -> None:
    s = size / 100.0
    draw.polygon([(20 * s, 50 * s), (82 * s, 22 * s), (48 * s, 60 * s)], fill=(255, 255, 255, 255))
    draw.polygon([(48 * s, 60 * s), (82 * s, 22 * s), (62 * s, 80 * s)], fill=(255, 245, 240, 235))


if __name__ == "__main__":
    p = build_ico()
    print(f"Generated icon: {p}")
