"""On-device PDF & Image processing engine for paperpilot Windows Desktop.
Powered by PyMuPDF (fitz) and Pillow. Zero cloud uploads.
"""

from __future__ import annotations

import io
import os
import re
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

import fitz  # PyMuPDF
from PIL import Image


@dataclass
class ToolDef:
    id: str
    title: str
    category: str
    description: str
    accept: str = "pdf"  # pdf, image, text, any
    multi: bool = False


TOOL_CATEGORIES: List[Tuple[str, str]] = [
    ("Organize", "#F59E0B"),
    ("Convert", "#FF6B6B"),
    ("Optimize", "#10B981"),
    ("Edit & Sign", "#8B5CF6"),
    ("Protect", "#64748B"),
    ("Image Tools", "#14B8A6"),
]

TOOLS: List[ToolDef] = [
    # Organize
    ToolDef("merge", "Merge PDF", "Organize", "Combine multiple PDFs into one document", "pdf", True),
    ToolDef("split", "Split PDF", "Organize", "Extract a page range or split every page into a ZIP", "pdf"),
    ToolDef("remove_pages", "Remove Pages", "Organize", "Delete specific pages (e.g. 2, 4-5)", "pdf"),
    ToolDef("extract_pages", "Extract Pages", "Organize", "Save selected pages as a new PDF", "pdf"),
    ToolDef("rotate", "Rotate PDF", "Organize", "Rotate pages 90°, 180°, or 270°", "pdf"),
    ToolDef("page_numbers", "Page Numbers", "Organize", "Stamp clean page numbers on every page", "pdf"),
    # Convert
    ToolDef("pdf_to_jpg", "PDF to JPG", "Convert", "Render every PDF page as crisp JPEG images", "pdf"),
    ToolDef("image_to_pdf", "Image to PDF", "Convert", "Convert one or more images into a PDF", "image", True),
    ToolDef("extract_text", "Extract Text", "Convert", "Extract all text from a PDF into a .txt file", "pdf"),
    ToolDef("pdf_to_word", "PDF to Word", "Convert", "Export readable text into a Word-compatible .rtf", "pdf"),
    ToolDef("pdf_to_excel", "PDF to Excel", "Convert", "Export tabular PDF text into a .csv spreadsheet", "pdf"),
    ToolDef("pdf_to_ppt", "PDF to PowerPoint", "Convert", "Export pages as slide-ready images in a ZIP", "pdf"),
    ToolDef("text_to_pdf", "Text / HTML to PDF", "Convert", "Convert .txt, .md, or .html files into a PDF", "text"),
    # Optimize
    ToolDef("compress", "Compress PDF", "Optimize", "Reduce PDF file size locally with quality presets", "pdf"),
    ToolDef("repair", "Repair PDF", "Optimize", "Rebuild cross-reference tables and resave cleanly", "pdf"),
    ToolDef("pdf_to_pdfa", "Flatten / Archive", "Optimize", "Flatten pages into a portable archival PDF", "pdf"),
    # Edit & Sign
    ToolDef("edit_pdf", "Edit PDF Text", "Edit & Sign", "Open the inline Content Text Editor & Annotator", "pdf"),
    ToolDef("watermark", "Watermark", "Edit & Sign", "Overlay custom diagonal or centered text watermark", "pdf"),
    ToolDef("fill_sign", "Fill & Sign", "Edit & Sign", "Open Editor to add text, drawings, and signatures", "pdf"),
    # Protect
    ToolDef("protect", "Protect PDF", "Protect", "Encrypt a PDF with AES-256 password protection", "pdf"),
    ToolDef("unlock", "Unlock PDF", "Protect", "Remove password protection using your current password", "pdf"),
    # Image Tools
    ToolDef("compress_img", "Compress Image", "Image Tools", "Shrink JPG/PNG/WebP images locally", "image"),
    ToolDef("resize_img", "Resize Image", "Image Tools", "Scale images to custom pixel dimensions", "image"),
    ToolDef("convert_img", "Convert Image", "Image Tools", "Convert between JPG, PNG, and WebP formats", "image"),
]

TOOL_BY_ID: Dict[str, ToolDef] = {t.id: t for t in TOOLS}


def parse_page_ranges(spec: str, max_pages: int) -> List[int]:
    """Parse 1-based range string like '1-3, 5' into 0-based page indices."""
    indices: List[int] = []
    if not spec.strip():
        return list(range(max_pages))
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            a_str, b_str = part.split("-", 1)
            a = max(1, int(a_str.strip()))
            b = min(max_pages, int(b_str.strip()))
            for p in range(a, b + 1):
                if (p - 1) not in indices:
                    indices.append(p - 1)
        else:
            p = int(part)
            if 1 <= p <= max_pages and (p - 1) not in indices:
                indices.append(p - 1)
    return indices


def create_sample_welcome_pdf(output_path: Path) -> Path:
    doc = fitz.open()
    # Page 1
    p1 = doc.new_page(width=595, height=842)
    p1.draw_rect(fitz.Rect(0, 0, 595, 290), color=None, fill=(1.0, 0.42, 0.42))
    p1.insert_text((48, 120), "paperpilot", fontsize=40, fontname="helv", color=(1, 1, 1))
    p1.insert_text((48, 165), "Welcome Guide  •  Private On-Device PDF Toolkit for Windows", fontsize=14, color=(1, 1, 1))
    p1.insert_text((48, 210), "100% OFFLINE  •  ZERO CLOUD UPLOADS  •  NO SIGN-IN", fontsize=11, color=(1, 0.94, 0.85))
    p1.insert_text((48, 345), "WHAT YOU CAN DO", fontsize=13, color=(0.82, 0.23, 0.27))
    bullets = [
        "• Merge, split, rotate, extract, remove, and number PDF pages.",
        "• Click any text block inside Edit PDF to modify PDF text directly.",
        "• Compress large PDFs or protect sensitive documents with AES-256.",
        "• Convert PDFs to JPG, Word-compatible RTF, CSV, or plain text.",
        "• Manage Local Storage, Downloads, Processed files, Favorites, and Bin.",
    ]
    y = 380
    for line in bullets:
        p1.insert_text((48, y), line, fontsize=12.5, color=(0.1, 0.08, 0.14))
        y += 28
    p1.insert_text((48, 806), "paperpilot Desktop Welcome Guide  •  Page 1 of 2", fontsize=10, color=(0.42, 0.39, 0.46))

    # Page 2
    p2 = doc.new_page(width=595, height=842)
    p2.draw_rect(fitz.Rect(0, 0, 595, 130), color=None, fill=(0.08, 0.72, 0.65))
    p2.insert_text((48, 66), "PRIVATE BY DESIGN", fontsize=24, color=(1, 1, 1))
    p2.insert_text((48, 98), "All PDF & image processing runs locally on your Windows PC", fontsize=12, color=(1, 1, 1))
    p2.insert_text((48, 190), "TRY EDITING THIS PARAGRAPH", fontsize=13, color=(0.82, 0.23, 0.27))
    p2.insert_text((48, 224), "Open this file in the Editor and switch to Edit Text mode.", fontsize=12.5, color=(0.1, 0.08, 0.14))
    p2.insert_text((48, 250), "Click any blue dashed text box to update its wording, font size, or color.", fontsize=12.5, color=(0.1, 0.08, 0.14))
    p2.insert_text((48, 806), "paperpilot Desktop Welcome Guide  •  Page 2 of 2", fontsize=10, color=(0.42, 0.39, 0.46))

    doc.save(str(output_path))
    doc.close()
    return output_path


def create_template_pdf(template_name: str, output_dir: Path) -> Path:
    stamp = int(time.time())
    safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", template_name)
    out = output_dir / f"{safe}_{stamp}.pdf"
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)

    if template_name == "Resume":
        page.draw_rect(fitz.Rect(0, 0, 595, 125), color=None, fill=(0.10, 0.08, 0.14))
        page.insert_text((48, 62), "ALEX RIVERA", fontsize=26, color=(1, 1, 1))
        page.insert_text((48, 92), "Senior Product Designer  |  alex@example.com  |  Portland, OR", fontsize=11.5, color=(0.95, 0.85, 0.85))
        page.insert_text((48, 175), "SUMMARY", fontsize=13, color=(0.82, 0.23, 0.27))
        page.insert_text((48, 202), "Product designer focused on accessible, local-first desktop and mobile tools.", fontsize=12, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 265), "EXPERIENCE", fontsize=13, color=(0.82, 0.23, 0.27))
        page.insert_text((48, 294), "Lead Designer, Northstar Studio (2023 - Present)", fontsize=12, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 318), "• Redesigned cross-platform document editing workflows used daily by teams.", fontsize=11.5, color=(0.25, 0.23, 0.30))
    elif template_name == "Invoice":
        page.draw_rect(fitz.Rect(0, 0, 595, 115), color=None, fill=(0.82, 0.23, 0.27))
        page.insert_text((48, 62), "INVOICE #2026-0042", fontsize=24, color=(1, 1, 1))
        page.insert_text((48, 90), "Issued via paperpilot  •  Due in 14 days", fontsize=11.5, color=(1, 0.92, 0.92))
        page.insert_text((48, 170), "Bill To: Studio Horizon LLC", fontsize=12.5, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 215), "1. UI Architecture & Prototyping              $1,400.00", fontsize=12, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 245), "2. PDF Engine Integration                     $1,100.00", fontsize=12, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 300), "Total Due: $2,500.00", fontsize=15, color=(0.82, 0.23, 0.27))
    elif template_name == "Meeting Notes":
        page.draw_rect(fitz.Rect(0, 0, 595, 110), color=None, fill=(0.08, 0.72, 0.65))
        page.insert_text((48, 60), "MEETING NOTES", fontsize=24, color=(1, 1, 1))
        page.insert_text((48, 88), "Date: Today  •  Attendees: Product & Engineering", fontsize=11.5, color=(1, 1, 1))
        page.insert_text((48, 165), "AGENDA", fontsize=13, color=(0.08, 0.55, 0.50))
        page.insert_text((48, 195), "1. Review weekly release milestones", fontsize=12, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 222), "2. Confirm QA sign-off and next sprint scope", fontsize=12, color=(0.12, 0.10, 0.16))
        page.insert_text((48, 280), "ACTION ITEMS", fontsize=13, color=(0.08, 0.55, 0.50))
        page.insert_text((48, 310), "[ ] Share final specification PDF with stakeholders", fontsize=12, color=(0.12, 0.10, 0.16))
    else:  # Lined Sheet
        page.insert_text((48, 54), "NOTES", fontsize=18, color=(0.82, 0.23, 0.27))
        for y in range(96, 790, 28):
            page.draw_line((44, y), (551, y), color=(0.88, 0.85, 0.84), width=0.8)

    doc.save(str(out))
    doc.close()
    return out


def run_tool(
    tool_id: str,
    input_files: List[Path],
    options: Dict[str, object],
    output_dir: Path,
    progress_cb: Optional[Callable[[float, str], None]] = None,
) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    if not input_files:
        raise ValueError("Please select at least one file.")

    def report(pct: float, msg: str) -> None:
        if progress_cb:
            progress_cb(pct, msg)

    stamp = int(time.time())
    first = input_files[0]
    stem = re.sub(r"[^a-zA-Z0-9._-]+", "_", first.stem)

    if tool_id == "merge":
        if len(input_files) < 2:
            raise ValueError("Select at least 2 PDF files to merge.")
        out = output_dir / f"merged_{stamp}.pdf"
        merged = fitz.open()
        for idx, path in enumerate(input_files):
            report((idx + 0.3) / len(input_files), f"Merging {path.name}...")
            with fitz.open(path) as src:
                merged.insert_pdf(src)
        merged.save(str(out), deflate=True)
        merged.close()
        report(1.0, "Done")
        return out

    if tool_id == "split":
        range_spec = str(options.get("page_range", "")).strip()
        with fitz.open(first) as src:
            if range_spec:
                pages = parse_page_ranges(range_spec, src.page_count)
                if not pages:
                    raise ValueError("Invalid page range.")
                out = output_dir / f"{stem}_split_{stamp}.pdf"
                dst = fitz.open()
                for idx, p in enumerate(pages):
                    report((idx + 1) / max(1, len(pages)), f"Extracting page {p + 1}...")
                    dst.insert_pdf(src, from_page=p, to_page=p)
                dst.save(str(out), deflate=True)
                dst.close()
                return out
            out = output_dir / f"{stem}_pages_{stamp}.zip"
            with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
                for p in range(src.page_count):
                    report((p + 1) / max(1, src.page_count), f"Splitting page {p + 1}/{src.page_count}...")
                    single = fitz.open()
                    single.insert_pdf(src, from_page=p, to_page=p)
                    zf.writestr(f"{stem}_page_{p + 1}.pdf", single.tobytes(deflate=True))
                    single.close()
            return out

    if tool_id == "remove_pages":
        spec = str(options.get("page_range", "")).strip()
        if not spec:
            raise ValueError("Enter page numbers to remove (e.g. 2, 4-5).")
        with fitz.open(first) as src:
            to_remove = set(parse_page_ranges(spec, src.page_count))
            keep = [i for i in range(src.page_count) if i not in to_remove]
            if not keep:
                raise ValueError("Cannot remove every page from the PDF.")
            out = output_dir / f"{stem}_trimmed_{stamp}.pdf"
            dst = fitz.open()
            for idx, p in enumerate(keep):
                report((idx + 1) / len(keep), f"Keeping page {p + 1}...")
                dst.insert_pdf(src, from_page=p, to_page=p)
            dst.save(str(out), deflate=True)
            dst.close()
            return out

    if tool_id == "extract_pages":
        spec = str(options.get("page_range", "1")).strip()
        with fitz.open(first) as src:
            pages = parse_page_ranges(spec, src.page_count)
            if not pages:
                raise ValueError("No valid pages specified.")
            out = output_dir / f"{stem}_extracted_{stamp}.pdf"
            dst = fitz.open()
            for idx, p in enumerate(pages):
                report((idx + 1) / len(pages), f"Extracting page {p + 1}...")
                dst.insert_pdf(src, from_page=p, to_page=p)
            dst.save(str(out), deflate=True)
            dst.close()
            return out

    if tool_id == "rotate":
        angle = int(options.get("rotation", 90))
        out = output_dir / f"{stem}_rotated_{stamp}.pdf"
        with fitz.open(first) as doc:
            for i, page in enumerate(doc):
                report((i + 1) / max(1, doc.page_count), f"Rotating page {i + 1}...")
                page.set_rotation((page.rotation + angle) % 360)
            doc.save(str(out), deflate=True)
        return out

    if tool_id == "page_numbers":
        position = str(options.get("position", "Bottom Right"))
        out = output_dir / f"{stem}_numbered_{stamp}.pdf"
        with fitz.open(first) as doc:
            total = doc.page_count
            for i, page in enumerate(doc):
                report((i + 1) / max(1, total), f"Numbering page {i + 1}/{total}...")
                rect = page.rect
                label = f"{i + 1} / {total}"
                if "Center" in position:
                    x = rect.width / 2 - 20
                elif "Left" in position:
                    x = 36
                else:
                    x = rect.width - 78
                y = 32 if "Top" in position else rect.height - 24
                page.insert_text((x, y), label, fontsize=10.5, color=(0.35, 0.33, 0.40))
            doc.save(str(out), deflate=True)
        return out

    if tool_id == "watermark":
        wm_text = str(options.get("watermark_text", "CONFIDENTIAL")).strip() or "CONFIDENTIAL"
        out = output_dir / f"{stem}_watermarked_{stamp}.pdf"
        with fitz.open(first) as doc:
            for i, page in enumerate(doc):
                report((i + 1) / max(1, doc.page_count), f"Watermarking page {i + 1}...")
                rect = page.rect
                center = fitz.Point(rect.width * 0.18, rect.height * 0.54)
                page.insert_text(
                    center,
                    wm_text,
                    fontsize=42,
                    color=(0.82, 0.23, 0.27),
                    fill_opacity=0.22,
                    morph=(fitz.Point(rect.width / 2, rect.height / 2), fitz.Matrix(35)),
                )
            doc.save(str(out), deflate=True)
        return out

    if tool_id == "compress":
        level = str(options.get("compression", "Balanced"))
        dpi = 96 if level == "Strong" else 130 if level == "Balanced" else 170
        quality = 55 if level == "Strong" else 72 if level == "Balanced" else 85
        out = output_dir / f"{stem}_compressed_{stamp}.pdf"
        with fitz.open(first) as src:
            dst = fitz.open()
            for i, page in enumerate(src):
                report((i + 1) / max(1, src.page_count), f"Compressing page {i + 1}/{src.page_count}...")
                pix = page.get_pixmap(dpi=dpi)
                img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
                buf = io.BytesIO()
                img.save(buf, format="JPEG", quality=quality, optimize=True)
                new_page = dst.new_page(width=page.rect.width, height=page.rect.height)
                new_page.insert_image(new_page.rect, stream=buf.getvalue())
            dst.save(str(out), garbage=4, deflate=True)
            dst.close()
        return out

    if tool_id in {"repair", "pdf_to_pdfa"}:
        suffix = "repaired" if tool_id == "repair" else "archive"
        out = output_dir / f"{stem}_{suffix}_{stamp}.pdf"
        report(0.4, "Rebuilding PDF structure on device...")
        with fitz.open(first) as doc:
            doc.save(str(out), garbage=4, deflate=True, clean=True)
        report(1.0, "Done")
        return out

    if tool_id == "protect":
        pwd = str(options.get("password", "")).strip()
        if len(pwd) < 3:
            raise ValueError("Enter a password with at least 3 characters.")
        out = output_dir / f"{stem}_protected_{stamp}.pdf"
        report(0.5, "Encrypting PDF with AES-256...")
        with fitz.open(first) as doc:
            doc.save(
                str(out),
                encryption=fitz.PDF_ENCRYPT_AES_256,
                user_pw=pwd,
                owner_pw=pwd,
                permissions=fitz.PDF_PERM_PRINT | fitz.PDF_PERM_ACCESSIBILITY,
            )
        report(1.0, "Done")
        return out

    if tool_id == "unlock":
        pwd = str(options.get("password", "")).strip()
        out = output_dir / f"{stem}_unlocked_{stamp}.pdf"
        report(0.5, "Unlocking PDF locally...")
        with fitz.open(first) as doc:
            if doc.needs_pass and not doc.authenticate(pwd):
                raise ValueError("Incorrect password for this PDF.")
            doc.save(str(out), encryption=fitz.PDF_ENCRYPT_NONE, deflate=True)
        report(1.0, "Done")
        return out

    if tool_id in {"pdf_to_jpg", "pdf_to_ppt"}:
        quality = int(options.get("image_quality", 85))
        with fitz.open(first) as doc:
            if doc.page_count == 1 and tool_id == "pdf_to_jpg":
                out = output_dir / f"{stem}_page1_{stamp}.jpg"
                pix = doc[0].get_pixmap(dpi=150)
                Image.frombytes("RGB", [pix.width, pix.height], pix.samples).save(str(out), "JPEG", quality=quality)
                return out
            out = output_dir / f"{stem}_{'slides' if tool_id == 'pdf_to_ppt' else 'images'}_{stamp}.zip"
            with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
                for i, page in enumerate(doc):
                    report((i + 1) / max(1, doc.page_count), f"Rendering page {i + 1}/{doc.page_count}...")
                    pix = page.get_pixmap(dpi=150)
                    img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
                    buf = io.BytesIO()
                    img.save(buf, format="JPEG", quality=quality)
                    zf.writestr(f"{stem}_page_{i + 1}.jpg", buf.getvalue())
            return out

    if tool_id == "image_to_pdf":
        out = output_dir / f"images_{stamp}.pdf"
        dst = fitz.open()
        for i, img_path in enumerate(input_files):
            report((i + 1) / len(input_files), f"Adding {img_path.name}...")
            with Image.open(img_path) as im:
                rgb = im.convert("RGB")
                buf = io.BytesIO()
                rgb.save(buf, format="JPEG", quality=88)
                page = dst.new_page(width=595, height=842)
                margin = 28
                avail_w = 595 - 2 * margin
                avail_h = 842 - 2 * margin
                scale = min(avail_w / rgb.width, avail_h / rgb.height)
                w, h = rgb.width * scale, rgb.height * scale
                rect = fitz.Rect((595 - w) / 2, (842 - h) / 2, (595 + w) / 2, (842 + h) / 2)
                page.insert_image(rect, stream=buf.getvalue())
        dst.save(str(out), deflate=True)
        dst.close()
        return out

    if tool_id == "extract_text":
        out = output_dir / f"{stem}_text_{stamp}.txt"
        chunks: List[str] = []
        with fitz.open(first) as doc:
            for i, page in enumerate(doc):
                report((i + 1) / max(1, doc.page_count), f"Extracting text from page {i + 1}...")
                chunks.append(f"--- Page {i + 1} ---\n{page.get_text('text')}")
        out.write_text("\n\n".join(chunks), encoding="utf-8")
        return out

    if tool_id == "pdf_to_word":
        out = output_dir / f"{stem}_{stamp}.rtf"
        lines: List[str] = [r"{\rtf1\ansi\deff0 {\fonttbl {\f0 Segoe UI;}}\f0\fs22 "]
        with fitz.open(first) as doc:
            for i, page in enumerate(doc):
                report((i + 1) / max(1, doc.page_count), f"Converting page {i + 1}...")
                txt = page.get_text("text").replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("\n", "\\par\n")
                lines.append(txt + r"\page ")
        lines.append("}")
        out.write_text("".join(lines), encoding="utf-8")
        return out

    if tool_id == "pdf_to_excel":
        out = output_dir / f"{stem}_{stamp}.csv"
        rows: List[str] = []
        with fitz.open(first) as doc:
            for i, page in enumerate(doc):
                report((i + 1) / max(1, doc.page_count), f"Extracting rows from page {i + 1}...")
                for line in page.get_text("text").splitlines():
                    if line.strip():
                        cols = [c.strip().replace('"', '""') for c in re.split(r"\s{2,}|\t", line.strip())]
                        rows.append(",".join(f'"{c}"' for c in cols))
        out.write_text("\n".join(rows), encoding="utf-8")
        return out

    if tool_id == "text_to_pdf":
        raw = first.read_text(encoding="utf-8", errors="replace")
        if first.suffix.lower() in {".html", ".htm"}:
            raw = re.sub(r"<[^>]+>", " ", raw)
        out = output_dir / f"{stem}_{stamp}.pdf"
        doc = fitz.open()
        lines = raw.splitlines() or ["(Empty file)"]
        y = 54
        page = doc.new_page(width=595, height=842)
        for line in lines:
            if y > 790:
                page = doc.new_page(width=595, height=842)
                y = 54
            page.insert_text((44, y), line[:110], fontsize=11, color=(0.1, 0.08, 0.14))
            y += 16
        doc.save(str(out), deflate=True)
        doc.close()
        return out

    if tool_id in {"compress_img", "resize_img", "convert_img"}:
        quality = int(options.get("image_quality", 80))
        fmt = str(options.get("image_format", "JPEG")).upper()
        target_w = int(options.get("resize_width", 1280))
        ext = "jpg" if fmt == "JPEG" else fmt.lower()
        out = output_dir / f"{stem}_{tool_id}_{stamp}.{ext}"
        report(0.4, "Processing image locally...")
        with Image.open(first) as im:
            work = im.convert("RGB") if fmt == "JPEG" else im.copy()
            if tool_id == "resize_img" and target_w > 32:
                ratio = target_w / max(1, work.width)
                work = work.resize((target_w, max(1, int(work.height * ratio))), Image.Resampling.LANCZOS)
            save_kwargs = {"quality": quality, "optimize": True} if fmt in {"JPEG", "WEBP"} else {}
            work.save(str(out), format=fmt, **save_kwargs)
        report(1.0, "Done")
        return out

    raise ValueError(f"Unsupported tool: {tool_id}")
