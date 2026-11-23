"""Interactive PDF Viewer & Content Text Editor window for paperpilot Windows Desktop.
Supports direct in-place PDF text block editing (dashed blue boxes -> solid selection -> redact & re-render),
plus Add Text, Highlight, Freehand Draw, Signature, Undo/Redo, Zoom, and Save/Save Copy.
"""

from __future__ import annotations

import copy
import time
import tkinter as tk
from dataclasses import dataclass, field
from pathlib import Path
from tkinter import colorchooser, messagebox, simpledialog
from typing import Callable, List, Optional, Tuple

import customtkinter as ctk
import fitz  # PyMuPDF
from PIL import Image, ImageTk


@dataclass
class TextBlockEdit:
    page_index: int
    orig_rect: Tuple[float, float, float, float]
    rect: Tuple[float, float, float, float]
    original_text: str
    text: str
    font_size: float = 12.0
    color_hex: str = "#1A1523"
    bg_hex: str = "#FFFFFF"
    bold: bool = False
    align: str = "left"  # left, center, right
    modified: bool = False
    deleted: bool = False


@dataclass
class FreehandStroke:
    page_index: int
    points: List[Tuple[float, float]] = field(default_factory=list)
    color_hex: str = "#D03A44"
    width: float = 2.5


@dataclass
class HighlightRect:
    page_index: int
    rect: Tuple[float, float, float, float]
    color_hex: str = "#F59E0B"


def hex_to_rgb01(hex_color: str) -> Tuple[float, float, float]:
    h = hex_color.lstrip("#")
    if len(h) != 6:
        return (0.1, 0.08, 0.14)
    return (int(h[0:2], 16) / 255.0, int(h[2:4], 16) / 255.0, int(h[4:6], 16) / 255.0)


def int_color_to_hex(color_int: int) -> str:
    r = (color_int >> 16) & 255
    g = (color_int >> 8) & 255
    b = color_int & 255
    return f"#{r:02X}{g:02X}{b:02X}"


class PdfEditorWindow(ctk.CTkToplevel):
    def __init__(
        self,
        master: ctk.CTk,
        pdf_path: Path,
        display_name: str,
        initial_mode: str = "edit_text",  # view, edit_text, add_text, highlight, draw, sign
        on_saved: Optional[Callable[[Path, bool], None]] = None,
    ) -> None:
        super().__init__(master)
        self.pdf_path = Path(pdf_path)
        self.display_name = display_name
        self.on_saved = on_saved

        self.title(f"paperpilot — {display_name}")
        self.geometry("1120x780")
        self.minsize(880, 620)

        self.doc = fitz.open(str(self.pdf_path))
        if self.doc.needs_pass:
            pwd = simpledialog.askstring("Protected PDF", "Enter PDF password:", show="*", parent=self)
            if not pwd or not self.doc.authenticate(pwd):
                messagebox.showerror("Locked PDF", "Could not unlock this PDF.", parent=self)
                self.doc.close()
                self.destroy()
                return

        self.page_index = 0
        self.zoom = 1.25
        self.mode = initial_mode
        self.selected_block_idx: Optional[int] = None
        self.active_color = "#D03A44"

        self.blocks_by_page: dict[int, List[TextBlockEdit]] = {}
        self.strokes: List[FreehandStroke] = []
        self.highlights: List[HighlightRect] = []
        self.undo_stack: List[tuple] = []
        self.redo_stack: List[tuple] = []

        self._drag_points: List[Tuple[float, float]] = []
        self._highlight_start: Optional[Tuple[float, float]] = None
        self._photo_ref: Optional[ImageTk.PhotoImage] = None
        self._page_render_scale: float = 1.0

        self._build_ui()
        self._extract_blocks_for_page(0)
        self._render_current_page()
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    def _on_close(self) -> None:
        try:
            self.doc.close()
        except Exception:
            pass
        self.destroy()

    def _snapshot(self) -> None:
        self.undo_stack.append(
            (
                copy.deepcopy(self.blocks_by_page),
                copy.deepcopy(self.strokes),
                copy.deepcopy(self.highlights),
            )
        )
        if len(self.undo_stack) > 40:
            self.undo_stack.pop(0)
        self.redo_stack.clear()

    def _undo(self) -> None:
        if not self.undo_stack:
            return
        self.redo_stack.append(
            (
                copy.deepcopy(self.blocks_by_page),
                copy.deepcopy(self.strokes),
                copy.deepcopy(self.highlights),
            )
        )
        self.blocks_by_page, self.strokes, self.highlights = self.undo_stack.pop()
        self.selected_block_idx = None
        self._sync_inspector()
        self._render_current_page()

    def _redo(self) -> None:
        if not self.redo_stack:
            return
        self.undo_stack.append(
            (
                copy.deepcopy(self.blocks_by_page),
                copy.deepcopy(self.strokes),
                copy.deepcopy(self.highlights),
            )
        )
        self.blocks_by_page, self.strokes, self.highlights = self.redo_stack.pop()
        self.selected_block_idx = None
        self._sync_inspector()
        self._render_current_page()

    def _build_ui(self) -> None:
        # Top toolbar
        top = ctk.CTkFrame(self, height=54, corner_radius=0)
        top.pack(fill="x", side="top")

        ctk.CTkLabel(
            top,
            text=self.display_name,
            font=ctk.CTkFont(size=15, weight="bold"),
        ).pack(side="left", padx=16, pady=10)

        # Save / Undo / Redo on right
        ctk.CTkButton(
            top,
            text="Save Copy",
            width=100,
            fg_color="#D03A44",
            hover_color="#B32E38",
            command=lambda: self._save_pdf(overwrite=False),
        ).pack(side="right", padx=(6, 14), pady=10)

        ctk.CTkButton(
            top,
            text="Save",
            width=76,
            fg_color="#14B8A6",
            hover_color="#0F9485",
            command=lambda: self._save_pdf(overwrite=True),
        ).pack(side="right", padx=4, pady=10)

        ctk.CTkButton(top, text="Redo", width=64, fg_color="transparent", border_width=1, command=self._redo).pack(
            side="right", padx=4, pady=10
        )
        ctk.CTkButton(top, text="Undo", width=64, fg_color="transparent", border_width=1, command=self._undo).pack(
            side="right", padx=4, pady=10
        )

        # Tool mode bar
        mode_bar = ctk.CTkFrame(self, height=48, corner_radius=0)
        mode_bar.pack(fill="x", side="top")

        self.mode_buttons: dict[str, ctk.CTkButton] = {}
        modes = [
            ("view", "View"),
            ("edit_text", "Edit Text"),
            ("add_text", "+ Add Text"),
            ("highlight", "Highlight"),
            ("draw", "Draw"),
            ("sign", "Signature"),
        ]
        for key, label in modes:
            btn = ctk.CTkButton(
                mode_bar,
                text=label,
                width=92,
                height=32,
                corner_radius=16,
                command=lambda m=key: self._set_mode(m),
            )
            btn.pack(side="left", padx=6, pady=8)
            self.mode_buttons[key] = btn

        # Zoom & Page controls on right of mode bar
        ctk.CTkButton(mode_bar, text="+", width=34, height=30, command=lambda: self._change_zoom(0.2)).pack(
            side="right", padx=(2, 14), pady=8
        )
        self.zoom_label = ctk.CTkLabel(mode_bar, text="125%", width=48)
        self.zoom_label.pack(side="right", padx=2)
        ctk.CTkButton(mode_bar, text="-", width=34, height=30, command=lambda: self._change_zoom(-0.2)).pack(
            side="right", padx=2, pady=8
        )

        ctk.CTkButton(mode_bar, text="Next ›", width=66, height=30, command=lambda: self._go_page(1)).pack(
            side="right", padx=(4, 14), pady=8
        )
        self.page_label = ctk.CTkLabel(mode_bar, text=f"1 / {self.doc.page_count}", width=64)
        self.page_label.pack(side="right", padx=4)
        ctk.CTkButton(mode_bar, text="‹ Prev", width=66, height=30, command=lambda: self._go_page(-1)).pack(
            side="right", padx=4, pady=8
        )

        # Main split: Canvas + Right Inspector
        body = ctk.CTkFrame(self, fg_color="transparent")
        body.pack(fill="both", expand=True)

        canvas_wrap = ctk.CTkFrame(body, corner_radius=0)
        canvas_wrap.pack(side="left", fill="both", expand=True)

        self.v_scroll = tk.Scrollbar(canvas_wrap, orient="vertical")
        self.h_scroll = tk.Scrollbar(canvas_wrap, orient="horizontal")
        self.canvas = tk.Canvas(
            canvas_wrap,
            bg="#1E1B26",
            highlightthickness=0,
            xscrollcommand=self.h_scroll.set,
            yscrollcommand=self.v_scroll.set,
        )
        self.v_scroll.config(command=self.canvas.yview)
        self.h_scroll.config(command=self.canvas.xview)

        self.v_scroll.pack(side="right", fill="y")
        self.h_scroll.pack(side="bottom", fill="x")
        self.canvas.pack(side="left", fill="both", expand=True)

        self.canvas.bind("<ButtonPress-1>", self._on_canvas_down)
        self.canvas.bind("<B1-Motion>", self._on_canvas_drag)
        self.canvas.bind("<ButtonRelease-1>", self._on_canvas_up)

        # Right inspector panel for inline Content Text Editing formatting
        self.inspector = ctk.CTkFrame(body, width=290, corner_radius=0)
        self.inspector.pack(side="right", fill="y")
        self.inspector.pack_propagate(False)

        ctk.CTkLabel(
            self.inspector,
            text="Content Text Editor",
            font=ctk.CTkFont(size=15, weight="bold"),
        ).pack(anchor="w", padx=16, pady=(16, 4))

        self.inspector_hint = ctk.CTkLabel(
            self.inspector,
            text="Click any blue dashed text box on the page to edit it directly.",
            wraplength=256,
            justify="left",
            text_color=("gray35", "gray70"),
            font=ctk.CTkFont(size=12),
        )
        self.inspector_hint.pack(anchor="w", padx=16, pady=(0, 10))

        self.text_box = ctk.CTkTextbox(self.inspector, height=130, corner_radius=12)
        self.text_box.pack(fill="x", padx=16, pady=(0, 10))

        row1 = ctk.CTkFrame(self.inspector, fg_color="transparent")
        row1.pack(fill="x", padx=16, pady=4)
        ctk.CTkLabel(row1, text="Font size:").pack(side="left")
        self.size_var = ctk.StringVar(value="12")
        self.size_entry = ctk.CTkEntry(row1, textvariable=self.size_var, width=62)
        self.size_entry.pack(side="left", padx=8)

        self.bold_var = ctk.BooleanVar(value=False)
        ctk.CTkCheckBox(row1, text="Bold", variable=self.bold_var, width=60).pack(side="right")

        row2 = ctk.CTkFrame(self.inspector, fg_color="transparent")
        row2.pack(fill="x", padx=16, pady=6)
        ctk.CTkLabel(row2, text="Align:").pack(side="left")
        self.align_seg = ctk.CTkSegmentedButton(row2, values=["left", "center", "right"])
        self.align_seg.set("left")
        self.align_seg.pack(side="right", fill="x", expand=True, padx=(8, 0))

        row3 = ctk.CTkFrame(self.inspector, fg_color="transparent")
        row3.pack(fill="x", padx=16, pady=6)
        ctk.CTkLabel(row3, text="Text color:").pack(side="left")
        self.color_btn = ctk.CTkButton(row3, text="#1A1523", width=110, fg_color="#1A1523", command=self._pick_color)
        self.color_btn.pack(side="right")

        ctk.CTkButton(
            self.inspector,
            text="Apply Text Edit",
            fg_color="#D03A44",
            hover_color="#B32E38",
            height=38,
            corner_radius=19,
            command=self._apply_selected_block_edit,
        ).pack(fill="x", padx=16, pady=(12, 6))

        btn_row = ctk.CTkFrame(self.inspector, fg_color="transparent")
        btn_row.pack(fill="x", padx=16, pady=4)
        ctk.CTkButton(
            btn_row,
            text="Reset",
            fg_color="transparent",
            border_width=1,
            command=self._reset_selected_block,
        ).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ctk.CTkButton(
            btn_row,
            text="Delete Block",
            fg_color="#DC2626",
            hover_color="#B91C1C",
            command=self._delete_selected_block,
        ).pack(side="right", fill="x", expand=True, padx=(4, 0))

        self._set_mode(self.mode)

    def _set_mode(self, mode: str) -> None:
        self.mode = mode
        for key, btn in self.mode_buttons.items():
            if key == mode:
                btn.configure(fg_color="#D03A44", text_color="white")
            else:
                btn.configure(fg_color=("gray84", "gray24"), text_color=("gray15", "gray90"))
        self._render_current_page()

    def _change_zoom(self, delta: float) -> None:
        self.zoom = max(0.6, min(2.6, self.zoom + delta))
        self.zoom_label.configure(text=f"{int(self.zoom * 100)}%")
        self._render_current_page()

    def _go_page(self, delta: int) -> None:
        nxt = max(0, min(self.doc.page_count - 1, self.page_index + delta))
        if nxt != self.page_index:
            self.page_index = nxt
            self.selected_block_idx = None
            self._extract_blocks_for_page(nxt)
            self._sync_inspector()
            self._render_current_page()

    def _extract_blocks_for_page(self, page_idx: int) -> None:
        if page_idx in self.blocks_by_page:
            return
        page = self.doc[page_idx]
        data = page.get_text("dict")
        extracted: List[TextBlockEdit] = []
        for b in data.get("blocks", []):
            if b.get("type") != 0:
                continue
            lines_text: List[str] = []
            max_size = 11.0
            first_color = "#1A1523"
            is_bold = False
            for ln in b.get("lines", []):
                spans = ln.get("spans", [])
                if not spans:
                    continue
                line_str = "".join(s.get("text", "") for s in spans).strip()
                if line_str:
                    lines_text.append(line_str)
                for s in spans:
                    sz = float(s.get("size", 11.0))
                    if sz > max_size:
                        max_size = sz
                    if "bold" in str(s.get("font", "")).lower():
                        is_bold = True
                    if first_color == "#1A1523" and isinstance(s.get("color"), int):
                        first_color = int_color_to_hex(s["color"])
            full_text = "\n".join(lines_text).strip()
            if not full_text:
                continue
            x0, y0, x1, y1 = b["bbox"]
            # Sample background color from corner of block on rendered pixmap
            bg_hex = "#FFFFFF"
            try:
                clip = fitz.Rect(max(0, x0 - 2), max(0, y0 - 2), min(page.rect.width, x0 + 2), min(page.rect.height, y0 + 2))
                pix = page.get_pixmap(clip=clip)
                if pix.n >= 3 and len(pix.samples) >= 3:
                    bg_hex = f"#{pix.samples[0]:02X}{pix.samples[1]:02X}{pix.samples[2]:02X}"
            except Exception:
                pass
            extracted.append(
                TextBlockEdit(
                    page_index=page_idx,
                    orig_rect=(x0, y0, x1, y1),
                    rect=(x0, y0, x1, y1),
                    original_text=full_text,
                    text=full_text,
                    font_size=round(max_size, 1),
                    color_hex=first_color,
                    bg_hex=bg_hex,
                    bold=is_bold,
                )
            )
        self.blocks_by_page[page_idx] = extracted

    def _render_current_page(self) -> None:
        self.page_label.configure(text=f"{self.page_index + 1} / {self.doc.page_count}")
        page = self.doc[self.page_index]
        mat = fitz.Matrix(self.zoom, self.zoom)
        self._page_render_scale = self.zoom
        pix = page.get_pixmap(matrix=mat)
        img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
        self._photo_ref = ImageTk.PhotoImage(img)

        self.canvas.delete("all")
        pad = 24
        self.canvas.create_image(pad, pad, anchor="nw", image=self._photo_ref)
        self.canvas.configure(scrollregion=(0, 0, pix.width + pad * 2, pix.height + pad * 2))

        s = self._page_render_scale
        blocks = self.blocks_by_page.get(self.page_index, [])

        # Draw highlights
        for hl in self.highlights:
            if hl.page_index != self.page_index:
                continue
            x0, y0, x1, y1 = hl.rect
            self.canvas.create_rectangle(
                pad + x0 * s, pad + y0 * s, pad + x1 * s, pad + y1 * s,
                fill="#F59E0B", stipple="gray25", outline=""
            )

        # Draw edited or visible text blocks
        for idx, blk in enumerate(blocks):
            ox0, oy0, ox1, oy1 = blk.orig_rect
            rx0, ry0, rx1, ry1 = blk.rect
            if blk.modified or blk.deleted:
                self.canvas.create_rectangle(
                    pad + ox0 * s - 1, pad + oy0 * s - 1, pad + ox1 * s + 1, pad + oy1 * s + 1,
                    fill=blk.bg_hex, outline=""
                )
            if blk.modified and not blk.deleted:
                anchor_map = {"left": "nw", "center": "n", "right": "ne"}
                tx = rx0 if blk.align == "left" else (rx0 + rx1) / 2 if blk.align == "center" else rx1
                font_style = ("Segoe UI", max(8, int(blk.font_size * s * 0.78)), "bold" if blk.bold else "normal")
                self.canvas.create_text(
                    pad + tx * s,
                    pad + ry0 * s,
                    text=blk.text,
                    anchor=anchor_map.get(blk.align, "nw"),
                    fill=blk.color_hex,
                    font=font_style,
                    width=max(40, (rx1 - rx0) * s),
                )

            if self.mode == "edit_text" and not blk.deleted:
                is_sel = idx == self.selected_block_idx
                self.canvas.create_rectangle(
                    pad + rx0 * s - 2,
                    pad + ry0 * s - 2,
                    pad + rx1 * s + 2,
                    pad + ry1 * s + 2,
                    outline="#1D8CF8" if is_sel else "#2563EB",
                    width=2 if is_sel else 1,
                    dash=() if is_sel else (4, 3),
                )
                if is_sel:
                    mid_y = pad + ((ry0 + ry1) / 2) * s
                    r = 6
                    self.canvas.create_oval(pad + rx0 * s - 2 - r, mid_y - r, pad + rx0 * s - 2 + r, mid_y + r, fill="#1D8CF8", outline="white")
                    self.canvas.create_oval(pad + rx1 * s + 2 - r, mid_y - r, pad + rx1 * s + 2 + r, mid_y + r, fill="#1D8CF8", outline="white")

        # Draw freehand strokes
        for st in self.strokes:
            if st.page_index != self.page_index or len(st.points) < 2:
                continue
            flat: List[float] = []
            for px, py in st.points:
                flat.extend([pad + px * s, pad + py * s])
            self.canvas.create_line(*flat, fill=st.color_hex, width=max(1.5, st.width * s), smooth=True, capstyle="round")

    def _canvas_to_pdf(self, event: tk.Event) -> Tuple[float, float]:
        pad = 24
        cx = self.canvas.canvasx(event.x)
        cy = self.canvas.canvasy(event.y)
        return ((cx - pad) / self._page_render_scale, (cy - pad) / self._page_render_scale)

    def _on_canvas_down(self, event: tk.Event) -> None:
        px, py = self._canvas_to_pdf(event)
        if self.mode == "edit_text":
            blocks = self.blocks_by_page.get(self.page_index, [])
            hit: Optional[int] = None
            for idx, blk in enumerate(blocks):
                if blk.deleted:
                    continue
                x0, y0, x1, y1 = blk.rect
                if (x0 - 6) <= px <= (x1 + 6) and (y0 - 6) <= py <= (y1 + 6):
                    hit = idx
                    break
            self.selected_block_idx = hit
            self._sync_inspector()
            self._render_current_page()
        elif self.mode in {"add_text", "sign"}:
            prompt = "Enter signature text:" if self.mode == "sign" else "Enter text to add:"
            txt = simpledialog.askstring("Add Text", prompt, parent=self)
            if txt and txt.strip():
                self._snapshot()
                blocks = self.blocks_by_page.setdefault(self.page_index, [])
                blocks.append(
                    TextBlockEdit(
                        page_index=self.page_index,
                        orig_rect=(px, py, px + 1, py + 1),
                        rect=(px, py, px + 220, py + 32),
                        original_text="",
                        text=txt.strip(),
                        font_size=18.0 if self.mode == "sign" else 13.0,
                        color_hex="#1E293B" if self.mode == "sign" else "#D03A44",
                        bg_hex="#FFFFFF",
                        bold=self.mode == "sign",
                        modified=True,
                    )
                )
                self.selected_block_idx = len(blocks) - 1
                self.mode = "edit_text"
                self._set_mode("edit_text")
                self._sync_inspector()
        elif self.mode == "draw":
            self._drag_points = [(px, py)]
        elif self.mode == "highlight":
            self._highlight_start = (px, py)

    def _on_canvas_drag(self, event: tk.Event) -> None:
        if self.mode == "draw" and self._drag_points:
            px, py = self._canvas_to_pdf(event)
            self._drag_points.append((px, py))
            pad = 24
            s = self._page_render_scale
            x0, y0 = self._drag_points[-2]
            self.canvas.create_line(
                pad + x0 * s, pad + y0 * s, pad + px * s, pad + py * s,
                fill=self.active_color, width=max(1.5, 2.5 * s), capstyle="round"
            )

    def _on_canvas_up(self, event: tk.Event) -> None:
        if self.mode == "draw" and len(self._drag_points) >= 2:
            self._snapshot()
            self.strokes.append(FreehandStroke(self.page_index, list(self._drag_points), self.active_color, 2.5))
            self._drag_points = []
            self._render_current_page()
        elif self.mode == "highlight" and self._highlight_start is not None:
            px, py = self._canvas_to_pdf(event)
            sx, sy = self._highlight_start
            self._highlight_start = None
            if abs(px - sx) > 8 and abs(py - sy) > 4:
                self._snapshot()
                self.highlights.append(
                    HighlightRect(self.page_index, (min(sx, px), min(sy, py), max(sx, px), max(sy, py)))
                )
                self._render_current_page()

    def _sync_inspector(self) -> None:
        self.text_box.delete("1.0", "end")
        if self.selected_block_idx is None:
            self.inspector_hint.configure(text="Click any blue dashed text box on the page to edit it directly.")
            return
        blk = self.blocks_by_page.get(self.page_index, [])[self.selected_block_idx]
        self.inspector_hint.configure(text="Editing selected PDF text block:")
        self.text_box.insert("1.0", blk.text)
        self.size_var.set(str(blk.font_size))
        self.bold_var.set(blk.bold)
        self.align_seg.set(blk.align)
        self.color_btn.configure(text=blk.color_hex, fg_color=blk.color_hex)

    def _pick_color(self) -> None:
        _, hex_val = colorchooser.askcolor(title="Choose text color", parent=self)
        if hex_val:
            self.active_color = hex_val.upper()
            self.color_btn.configure(text=self.active_color, fg_color=self.active_color)

    def _apply_selected_block_edit(self) -> None:
        if self.selected_block_idx is None:
            return
        blocks = self.blocks_by_page.get(self.page_index, [])
        if self.selected_block_idx >= len(blocks):
            return
        self._snapshot()
        blk = blocks[self.selected_block_idx]
        blk.text = self.text_box.get("1.0", "end").rstrip("\n")
        try:
            blk.font_size = max(6.0, min(72.0, float(self.size_var.get())))
        except ValueError:
            pass
        blk.bold = bool(self.bold_var.get())
        blk.align = self.align_seg.get()
        blk.color_hex = self.color_btn.cget("text")
        blk.modified = True
        self._render_current_page()

    def _reset_selected_block(self) -> None:
        if self.selected_block_idx is None:
            return
        blocks = self.blocks_by_page.get(self.page_index, [])
        if self.selected_block_idx >= len(blocks):
            return
        self._snapshot()
        blk = blocks[self.selected_block_idx]
        blk.text = blk.original_text
        blk.modified = False
        blk.deleted = False
        self._sync_inspector()
        self._render_current_page()

    def _delete_selected_block(self) -> None:
        if self.selected_block_idx is None:
            return
        blocks = self.blocks_by_page.get(self.page_index, [])
        if self.selected_block_idx >= len(blocks):
            return
        self._snapshot()
        blocks[self.selected_block_idx].deleted = True
        self.selected_block_idx = None
        self._sync_inspector()
        self._render_current_page()

    def _save_pdf(self, overwrite: bool) -> None:
        out_path = (
            self.pdf_path
            if overwrite
            else self.pdf_path.with_name(f"{self.pdf_path.stem}_edited_{int(time.time())}.pdf")
        )
        tmp_path = self.pdf_path.with_name(f".tmp_save_{int(time.time())}.pdf")
        try:
            save_doc = fitz.open(str(self.pdf_path))
            for p_idx, blocks in self.blocks_by_page.items():
                if p_idx >= save_doc.page_count:
                    continue
                page = save_doc[p_idx]
                for blk in blocks:
                    if not (blk.modified or blk.deleted):
                        continue
                    if blk.original_text:
                        orig = fitz.Rect(*blk.orig_rect)
                        page.draw_rect(orig, color=None, fill=hex_to_rgb01(blk.bg_hex))
                    if not blk.deleted and blk.text.strip():
                        target = fitz.Rect(blk.rect[0], blk.rect[1], blk.rect[2] + 12, blk.rect[3] + 36)
                        align_code = {"left": 0, "center": 1, "right": 2}.get(blk.align, 0)
                        page.insert_textbox(
                            target,
                            blk.text,
                            fontsize=blk.font_size,
                            fontname="hebo" if blk.bold else "helv",
                            color=hex_to_rgb01(blk.color_hex),
                            align=align_code,
                        )
            for hl in self.highlights:
                if hl.page_index < save_doc.page_count:
                    save_doc[hl.page_index].draw_rect(
                        fitz.Rect(*hl.rect), color=None, fill=(0.96, 0.62, 0.04), fill_opacity=0.35
                    )
            for st in self.strokes:
                if st.page_index < save_doc.page_count and len(st.points) >= 2:
                    page = save_doc[st.page_index]
                    rgb = hex_to_rgb01(st.color_hex)
                    for i in range(1, len(st.points)):
                        page.draw_line(st.points[i - 1], st.points[i], color=rgb, width=st.width)

            save_doc.save(str(tmp_path), deflate=True)
            save_doc.close()

            if overwrite:
                self.doc.close()
                tmp_path.replace(out_path)
                self.doc = fitz.open(str(out_path))
            else:
                tmp_path.replace(out_path)

            if self.on_saved:
                self.on_saved(out_path, overwrite)
            messagebox.showinfo(
                "Saved on your device",
                f"Saved successfully:\n{out_path}\n\nProcessed 100% locally on your Windows PC.",
                parent=self,
            )
        except Exception as exc:
            if tmp_path.exists():
                tmp_path.unlink(missing_ok=True)
            messagebox.showerror("Save Failed", str(exc), parent=self)
