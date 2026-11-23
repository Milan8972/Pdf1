"""paperpilot — Private On-Device PDF Toolkit for Windows (.exe)
Matches paperpilot Android design system: Coral #FF6B6B, Deep Coral #D03A44, Amber #F59E0B, Teal #14B8A6.
100% local processing — zero accounts, zero login/sign-up, zero cloud storage.
"""

from __future__ import annotations

import os
import subprocess
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog
from typing import List, Optional

import customtkinter as ctk

import time
from datetime import datetime
from editor_window import PdfEditorWindow
from membership_ads import DesktopAdModal, DesktopMembershipManager, PLANS
from pdf_engine import (
    TOOL_BY_ID,
    TOOL_CATEGORIES,
    TOOLS,
    ToolDef,
    create_sample_welcome_pdf,
    create_template_pdf,
    run_tool,
)
from storage import DocumentRecord, LocalStorage, format_file_size

BRAND_CORAL = "#FF6B6B"
PRIMARY_CORAL = "#D03A44"
PRIMARY_HOVER = "#B52F38"
BRAND_AMBER = "#F59E0B"
BRAND_TEAL = "#14B8A6"


class ToolDialog(ctk.CTkToplevel):
    """Unified 4-step tool flow: Pick files -> Options -> Progress -> Result."""

    def __init__(
        self,
        master: "PaperPilotDesktopApp",
        tool: ToolDef,
        preselected: Optional[List[Path]] = None,
    ) -> None:
        super().__init__(master)
        self.app = master
        self.tool = tool
        self.selected_files: List[Path] = list(preselected or [])
        self.output_path: Optional[Path] = None

        self.title(f"paperpilot — {tool.title}")
        self.geometry("620x540")
        self.minsize(540, 460)
        self.grab_set()

        self.container = ctk.CTkFrame(self, fg_color="transparent")
        self.container.pack(fill="both", expand=True, padx=22, pady=18)

        self._render_config_step()

    def _clear(self) -> None:
        for w in self.container.winfo_children():
            w.destroy()

    def _pick_files(self) -> None:
        if self.tool.accept == "image":
            ftypes = [("Images", "*.jpg *.jpeg *.png *.webp *.bmp"), ("All files", "*.*")]
        elif self.tool.accept == "text":
            ftypes = [("Text / HTML", "*.txt *.md *.html *.htm"), ("All files", "*.*")]
        else:
            ftypes = [("PDF files", "*.pdf"), ("All files", "*.*")]

        if self.tool.multi:
            chosen = filedialog.askopenfilenames(title=f"Select files for {self.tool.title}", filetypes=ftypes, parent=self)
            for p in chosen:
                path = Path(p)
                if path not in self.selected_files:
                    self.selected_files.append(path)
        else:
            chosen = filedialog.askopenfilename(title=f"Select file for {self.tool.title}", filetypes=ftypes, parent=self)
            if chosen:
                self.selected_files = [Path(chosen)]
        self._render_config_step()

    def _render_config_step(self) -> None:
        self._clear()
        ctk.CTkLabel(self.container, text=self.tool.title, font=ctk.CTkFont(size=22, weight="bold")).pack(anchor="w")
        ctk.CTkLabel(
            self.container,
            text=self.tool.description,
            text_color=("gray35", "gray70"),
            font=ctk.CTkFont(size=13),
        ).pack(anchor="w", pady=(2, 14))

        # Step 1: Pick files
        file_card = ctk.CTkFrame(self.container, corner_radius=16)
        file_card.pack(fill="x", pady=(0, 12))
        hdr = ctk.CTkFrame(file_card, fg_color="transparent")
        hdr.pack(fill="x", padx=14, pady=(12, 6))
        ctk.CTkLabel(hdr, text="1. Selected Files", font=ctk.CTkFont(size=14, weight="bold")).pack(side="left")
        ctk.CTkButton(
            hdr,
            text="+ Pick from PC" if self.tool.multi else "Pick File",
            width=110,
            height=30,
            corner_radius=15,
            fg_color=PRIMARY_CORAL,
            hover_color=PRIMARY_HOVER,
            command=self._pick_files,
        ).pack(side="right")

        if not self.selected_files:
            ctk.CTkLabel(file_card, text="No files chosen yet.", text_color=("gray45", "gray65")).pack(
                anchor="w", padx=14, pady=(0, 12)
            )
        else:
            for p in self.selected_files[:6]:
                ctk.CTkLabel(file_card, text=f"• {p.name}", anchor="w").pack(fill="x", padx=14, pady=1)
            if len(self.selected_files) > 6:
                ctk.CTkLabel(file_card, text=f"  + {len(self.selected_files) - 6} more file(s)").pack(anchor="w", padx=14)
            ctk.CTkLabel(file_card, text="", height=6).pack()

        # Step 2: Options
        opts_card = ctk.CTkFrame(self.container, corner_radius=16)
        opts_card.pack(fill="x", pady=(0, 14))
        ctk.CTkLabel(opts_card, text="2. Options", font=ctk.CTkFont(size=14, weight="bold")).pack(
            anchor="w", padx=14, pady=(12, 8)
        )

        self.opt_widgets: dict[str, object] = {}
        tid = self.tool.id
        if tid in {"split", "remove_pages", "extract_pages"}:
            ctk.CTkLabel(opts_card, text="Page range (e.g. 1-3, 5; blank = split all):").pack(anchor="w", padx=14)
            e = ctk.CTkEntry(opts_card, placeholder_text="1-3, 5")
            e.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["page_range"] = e
        elif tid == "rotate":
            seg = ctk.CTkSegmentedButton(opts_card, values=["90", "180", "270"])
            seg.set("90")
            seg.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["rotation"] = seg
        elif tid == "compress":
            seg = ctk.CTkSegmentedButton(opts_card, values=["Light", "Balanced", "Strong"])
            seg.set("Balanced")
            seg.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["compression"] = seg
        elif tid == "watermark":
            e = ctk.CTkEntry(opts_card, placeholder_text="Watermark text (e.g. CONFIDENTIAL)")
            e.insert(0, "CONFIDENTIAL")
            e.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["watermark_text"] = e
        elif tid == "page_numbers":
            seg = ctk.CTkSegmentedButton(opts_card, values=["Bottom Right", "Bottom Center", "Top Right"])
            seg.set("Bottom Right")
            seg.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["position"] = seg
        elif tid in {"protect", "unlock"}:
            e = ctk.CTkEntry(opts_card, show="*", placeholder_text="Enter PDF password")
            e.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["password"] = e
        elif tid in {"compress_img", "resize_img", "convert_img"}:
            if tid == "resize_img":
                e = ctk.CTkEntry(opts_card, placeholder_text="Target width in px (e.g. 1280)")
                e.insert(0, "1280")
                e.pack(fill="x", padx=14, pady=(4, 8))
                self.opt_widgets["resize_width"] = e
            seg = ctk.CTkSegmentedButton(opts_card, values=["JPEG", "PNG", "WEBP"])
            seg.set("JPEG")
            seg.pack(fill="x", padx=14, pady=(4, 12))
            self.opt_widgets["image_format"] = seg
        else:
            ctk.CTkLabel(
                opts_card,
                text="Ready to process locally on your Windows PC — no server upload.",
                text_color=("gray35", "gray70"),
            ).pack(anchor="w", padx=14, pady=(0, 12))

        ctk.CTkButton(
            self.container,
            text=f"Run {self.tool.title}",
            height=46,
            corner_radius=23,
            fg_color=PRIMARY_CORAL,
            hover_color=PRIMARY_HOVER,
            font=ctk.CTkFont(size=14, weight="bold"),
            command=self._start_processing,
        ).pack(fill="x", side="bottom", pady=(6, 0))

    def _collect_options(self) -> dict:
        opts: dict = {"image_quality": self.app.storage.settings.get("default_image_quality", 85)}
        for k, widget in self.opt_widgets.items():
            if hasattr(widget, "get"):
                val = widget.get()
                if k in {"rotation", "resize_width"}:
                    try:
                        opts[k] = int(val)
                    except ValueError:
                        opts[k] = 90 if k == "rotation" else 1280
                else:
                    opts[k] = val
        return opts

    def _start_processing(self) -> None:
        if not self.selected_files:
            messagebox.showwarning("Select File", "Please pick at least one file first.", parent=self)
            return
        if self.tool.id in {"edit_pdf", "fill_sign"}:
            first = self.selected_files[0]
            self.destroy()
            self.app.open_editor_path(first, "sign" if self.tool.id == "fill_sign" else "edit_text")
            return

        opts = self._collect_options()
        self._clear()
        ctk.CTkLabel(self.container, text=f"Running {self.tool.title}...", font=ctk.CTkFont(size=20, weight="bold")).pack(
            pady=(70, 12)
        )
        progress = ctk.CTkProgressBar(self.container, width=380, progress_color=PRIMARY_CORAL)
        progress.set(0.1)
        progress.pack(pady=10)
        status_lbl = ctk.CTkLabel(self.container, text="Processing locally on your device...")
        status_lbl.pack(pady=6)

        def worker() -> None:
            try:
                out_dir = Path(self.app.storage.settings.get("default_save_folder", str(self.app.storage.processed_dir)))
                out_file = run_tool(
                    self.tool.id,
                    self.selected_files,
                    opts,
                    out_dir,
                    progress_cb=lambda pct, msg: self.after(0, lambda: (progress.set(pct), status_lbl.configure(text=msg))),
                )
                rec = self.app.storage.register_file(out_file, source="CREATED", copy_into_workspace=False)
                self.output_path = Path(rec.file_path)
                self.after(
                    0,
                    lambda: (
                        self.app.refresh_current_view(),
                        self._render_result(rec),
                        self.app.maybe_show_tool_ad(self.tool.id),
                    ),
                )
            except Exception as exc:
                self.after(0, lambda: (messagebox.showerror("Processing Failed", str(exc), parent=self), self._render_config_step()))

        threading.Thread(target=worker, daemon=True).start()

    def _render_result(self, rec: DocumentRecord) -> None:
        self._clear()
        ctk.CTkLabel(self.container, text="Ready!", font=ctk.CTkFont(size=24, weight="bold"), text_color=BRAND_TEAL).pack(
            pady=(28, 6)
        )
        ctk.CTkLabel(
            self.container,
            text="Processed on your device • Zero cloud upload",
            text_color=BRAND_TEAL,
            font=ctk.CTkFont(size=12, weight="bold"),
        ).pack(pady=(0, 16))

        info = ctk.CTkFrame(self.container, corner_radius=16)
        info.pack(fill="x", pady=8)
        ctk.CTkLabel(info, text=rec.name, font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=16, pady=(14, 4))
        ctk.CTkLabel(
            info,
            text=f"{format_file_size(rec.size_bytes)}  •  {rec.file_path}",
            wraplength=520,
            justify="left",
            text_color=("gray35", "gray70"),
        ).pack(anchor="w", padx=16, pady=(0, 14))

        actions = ctk.CTkFrame(self.container, fg_color="transparent")
        actions.pack(fill="x", pady=16)
        if rec.file_path.lower().endswith(".pdf"):
            ctk.CTkButton(
                actions,
                text="Open in Editor",
                fg_color=PRIMARY_CORAL,
                hover_color=PRIMARY_HOVER,
                height=42,
                corner_radius=21,
                command=lambda: (self.destroy(), self.app.open_document(rec)),
            ).pack(side="left", fill="x", expand=True, padx=(0, 6))

        ctk.CTkButton(
            actions,
            text="Show in Folder",
            fg_color=BRAND_TEAL,
            hover_color="#0F9485",
            height=42,
            corner_radius=21,
            command=lambda: self.app.reveal_in_explorer(Path(rec.file_path)),
        ).pack(side="left", fill="x", expand=True, padx=6)

        ctk.CTkButton(
            self.container,
            text="Process Another",
            fg_color="transparent",
            border_width=1,
            height=40,
            corner_radius=20,
            command=lambda: (self.selected_files.clear(), self._render_config_step()),
        ).pack(fill="x", pady=(4, 0))


class PaperPilotDesktopApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        self.storage = LocalStorage()
        self.membership = DesktopMembershipManager(self.storage.settings, self.storage.save_settings)
        if self.storage.count_all() == 0:
            welcome = create_sample_welcome_pdf(self.storage.files_dir / "paperpilot_Welcome_Guide.pdf")
            self.storage.register_file(welcome, display_name="paperpilot Welcome Guide.pdf", source="OPENED", is_favorite=True)

        ctk.set_appearance_mode(self.storage.settings.get("theme", "System"))
        ctk.set_default_color_theme("blue")

        self.title("paperpilot — Private On-Device PDF Toolkit")
        self.geometry("1160x760")
        self.minsize(920, 620)

        self.active_tab = "home"
        self.active_storage_folder: Optional[str] = None
        self.recent_filter = "Opened"

        self._build_shell()
        self.switch_tab("home")

    def _build_shell(self) -> None:
        # Left navigation rail
        self.sidebar = ctk.CTkFrame(self, width=220, corner_radius=0)
        self.sidebar.pack(side="left", fill="y")
        self.sidebar.pack_propagate(False)

        brand = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        brand.pack(fill="x", padx=18, pady=(22, 16))
        badge = ctk.CTkFrame(brand, width=34, height=34, corner_radius=10, fg_color=BRAND_CORAL)
        badge.pack(side="left")
        badge.pack_propagate(False)
        ctk.CTkLabel(badge, text="✈", font=ctk.CTkFont(size=18, weight="bold"), text_color="white").pack(expand=True)
        ctk.CTkLabel(brand, text="paper", font=ctk.CTkFont(size=20, weight="bold")).pack(side="left", padx=(10, 0))
        ctk.CTkLabel(brand, text="pilot", font=ctk.CTkFont(size=20, weight="bold"), text_color=BRAND_CORAL).pack(side="left")

        self.status_badge = ctk.CTkLabel(
            self.sidebar,
            text="",
            font=ctk.CTkFont(size=11, weight="bold"),
            corner_radius=10,
            padx=10,
            pady=4,
        )
        self.status_badge.pack(fill="x", padx=14, pady=(0, 12))

        self.nav_buttons: dict[str, ctk.CTkButton] = {}
        for key, label in [
            ("home", "Home"),
            ("files", "Files & Storage"),
            ("tools", "All PDF Tools"),
            ("templates", "Templates"),
            ("premium", "★ Premium (No Ads)"),
            ("settings", "Settings"),
        ]:
            btn = ctk.CTkButton(
                self.sidebar,
                text=label,
                anchor="w",
                height=42,
                corner_radius=14,
                font=ctk.CTkFont(size=14, weight="bold"),
                command=lambda k=key: self.switch_tab(k),
            )
            btn.pack(fill="x", padx=14, pady=4)
            self.nav_buttons[key] = btn

        ctk.CTkButton(
            self.sidebar,
            text="+ Import from PC",
            height=42,
            corner_radius=21,
            fg_color=PRIMARY_CORAL,
            hover_color=PRIMARY_HOVER,
            font=ctk.CTkFont(size=13, weight="bold"),
            command=lambda: self.import_from_pc("OPENED"),
        ).pack(side="bottom", fill="x", padx=14, pady=18)

        # Right side container: main_area + conditional bottom Banner Ad (hidden for Premium!)
        self.right_shell = ctk.CTkFrame(self, fg_color="transparent")
        self.right_shell.pack(side="right", fill="both", expand=True)

        self.main_area = ctk.CTkFrame(self.right_shell, fg_color="transparent")
        self.main_area.pack(side="top", fill="both", expand=True)

        self.ad_banner_host = ctk.CTkFrame(self.right_shell, fg_color="transparent")
        self._refresh_ad_chrome()

    def _refresh_ad_chrome(self) -> None:
        if self.membership.is_premium_active:
            self.status_badge.configure(
                text="★ PREMIUM • AD-FREE",
                fg_color=("#D7F5F1", "#103936"),
                text_color=BRAND_TEAL,
            )
        else:
            self.status_badge.configure(
                text="FREE PLAN • AD-SUPPORTED",
                fg_color=("#FFF2DC", "#3A2B12"),
                text_color=BRAND_AMBER,
            )

        for w in self.ad_banner_host.winfo_children():
            w.destroy()

        if self.membership.are_ads_suppressed:
            self.ad_banner_host.pack_forget()
        else:
            self.ad_banner_host.pack(side="bottom", fill="x", padx=16, pady=(0, 10))
            bar = ctk.CTkFrame(
                self.ad_banner_host,
                corner_radius=14,
                border_width=1,
                border_color=("#E8DDD8", "#2E293B"),
            )
            bar.pack(fill="x")
            ctk.CTkLabel(
                bar,
                text="AD",
                fg_color=BRAND_AMBER,
                text_color="#1A1523",
                corner_radius=6,
                font=ctk.CTkFont(size=10, weight="bold"),
                width=28,
            ).pack(side="left", padx=(12, 8), pady=10)
            ctk.CTkLabel(
                bar,
                text="Sponsored Banner • Remove all ads on Windows EXE & Android APK with Premium (₹100/mo or ₹1,100/yr)",
                font=ctk.CTkFont(size=12),
                anchor="w",
            ).pack(side="left", fill="x", expand=True, pady=10)
            ctk.CTkButton(
                bar,
                text="Watch Ad (30m Free)",
                width=136,
                height=30,
                corner_radius=15,
                fg_color="transparent",
                border_width=1,
                command=self.launch_rewarded_ad,
            ).pack(side="right", padx=(6, 10), pady=8)
            ctk.CTkButton(
                bar,
                text="Go Premium",
                width=102,
                height=30,
                corner_radius=15,
                fg_color=PRIMARY_CORAL,
                hover_color=PRIMARY_HOVER,
                command=lambda: self.switch_tab("premium"),
            ).pack(side="right", padx=4, pady=8)

    def maybe_show_tool_ad(self, tool_id: str) -> None:
        if not self.membership.should_show_interstitial():
            return
        heavy_tools = {"merge", "image_to_pdf", "pdf_to_jpg", "compress", "ocr"}
        kind = "rewarded_interstitial" if tool_id in heavy_tools else "interstitial"
        DesktopAdModal(
            self,
            ad_kind=kind,
            on_upgrade=lambda: self.switch_tab("premium"),
            on_reward_earned=lambda: (
                self.membership.grant_rewarded_ad_free(15),
                self._refresh_ad_chrome(),
            ),
        )

    def launch_rewarded_ad(self) -> None:
        if self.membership.is_premium_active:
            return
        DesktopAdModal(
            self,
            ad_kind="rewarded",
            on_upgrade=lambda: self.switch_tab("premium"),
            on_reward_earned=lambda: (
                self.membership.grant_rewarded_ad_free(30),
                self._refresh_ad_chrome(),
                messagebox.showinfo("Reward Unlocked", "Ads paused for 30 minutes on this PC!", parent=self),
            ),
        )

    def switch_tab(self, tab: str) -> None:
        self.active_tab = tab
        if tab != "files":
            self.active_storage_folder = None
        for key, btn in self.nav_buttons.items():
            if key == tab:
                btn.configure(fg_color=PRIMARY_CORAL, text_color="white")
            else:
                btn.configure(fg_color="transparent", text_color=("gray15", "gray88"))
        self.refresh_current_view()

    def refresh_current_view(self) -> None:
        self._refresh_ad_chrome()
        for w in self.main_area.winfo_children():
            w.destroy()
        if self.active_tab == "home":
            self._render_home()
        elif self.active_tab == "files":
            self._render_files()
        elif self.active_tab == "tools":
            self._render_tools()
        elif self.active_tab == "templates":
            self._render_templates()
        elif self.active_tab == "premium":
            self._render_premium()
        elif self.active_tab == "settings":
            self._render_settings()

    def import_from_pc(self, source: str = "OPENED") -> None:
        paths = filedialog.askopenfilenames(
            title="Import PDFs or Images into paperpilot",
            filetypes=[("PDF & Images", "*.pdf *.jpg *.jpeg *.png *.webp *.txt"), ("All files", "*.*")],
            parent=self,
        )
        if not paths:
            return
        for p in paths:
            self.storage.register_file(Path(p), source=source, copy_into_workspace=True)
        self.refresh_current_view()

    def open_document(self, doc: DocumentRecord) -> None:
        self.storage.touch_opened(doc.id)
        if doc.file_path.lower().endswith(".pdf"):
            self.open_editor_path(Path(doc.file_path), "edit_text", doc.name, doc.id)
        else:
            self.reveal_in_explorer(Path(doc.file_path))

    def open_editor_path(
        self, path: Path, mode: str = "edit_text", title: Optional[str] = None, doc_id: Optional[int] = None
    ) -> None:
        def on_saved(saved_path: Path, overwrote: bool) -> None:
            if not overwrote:
                self.storage.register_file(saved_path, source="CREATED", copy_into_workspace=False)
            self.refresh_current_view()

        PdfEditorWindow(self, path, title or path.name, initial_mode=mode, on_saved=on_saved)

    def reveal_in_explorer(self, path: Path) -> None:
        try:
            if sys.platform.startswith("win"):
                subprocess.Popen(["explorer", "/select,", str(path)])
            elif sys.platform == "darwin":
                subprocess.Popen(["open", "-R", str(path)])
            else:
                subprocess.Popen(["xdg-open", str(path.parent)])
        except Exception:
            messagebox.showinfo("File Location", str(path), parent=self)

    def _render_home(self) -> None:
        scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
        scroll.pack(fill="both", expand=True, padx=24, pady=20)

        hdr = ctk.CTkFrame(scroll, fg_color="transparent")
        hdr.pack(fill="x", pady=(0, 14))
        ctk.CTkLabel(hdr, text="Recent files", font=ctk.CTkFont(size=24, weight="bold")).pack(side="left")
        seg = ctk.CTkSegmentedButton(
            hdr,
            values=["Opened", "Created"],
            command=lambda v: (setattr(self, "recent_filter", v), self.refresh_current_view()),
        )
        seg.set(self.recent_filter)
        seg.pack(side="right")

        docs = self.storage.list_documents(trashed=False)
        if self.recent_filter == "Created":
            docs = [d for d in docs if d.source == "CREATED"]

        if not docs:
            empty = ctk.CTkFrame(scroll, corner_radius=20)
            empty.pack(fill="x", pady=8)
            ctk.CTkLabel(empty, text="No files yet — import a PDF or create one from a template.", pady=24).pack()
        else:
            for d in docs[:6]:
                self._doc_row(scroll, d)

        # Quick tools
        q_hdr = ctk.CTkFrame(scroll, fg_color="transparent")
        q_hdr.pack(fill="x", pady=(22, 10))
        ctk.CTkLabel(q_hdr, text="Quick tools", font=ctk.CTkFont(size=20, weight="bold")).pack(side="left")
        ctk.CTkButton(q_hdr, text="See all tools", fg_color="transparent", command=lambda: self.switch_tab("tools")).pack(
            side="right"
        )

        quick_grid = ctk.CTkFrame(scroll, fg_color="transparent")
        quick_grid.pack(fill="x")
        quick_ids = ["edit_pdf", "merge", "split", "compress", "pdf_to_jpg", "image_to_pdf"]
        for i, tid in enumerate(quick_ids):
            t = TOOL_BY_ID[tid]
            card = ctk.CTkButton(
                quick_grid,
                text=f"{t.title}",
                height=54,
                corner_radius=16,
                fg_color=("white", "#1A1722"),
                text_color=("#1A1523", "#EDE9F2"),
                border_width=1,
                border_color=("#E8DDD8", "#2E293B"),
                hover_color=("#FFE9E9", "#282236"),
                command=lambda tool=t: ToolDialog(self, tool),
            )
            card.grid(row=i // 3, column=i % 3, sticky="ew", padx=6, pady=6)
            quick_grid.grid_columnconfigure(i % 3, weight=1)

        # Favorites
        favs = [d for d in self.storage.list_documents(trashed=False) if d.is_favorite]
        if favs:
            ctk.CTkLabel(scroll, text="Favorites", font=ctk.CTkFont(size=20, weight="bold")).pack(anchor="w", pady=(22, 10))
            for d in favs[:5]:
                self._doc_row(scroll, d)

    def _render_files(self) -> None:
        docs = self.storage.list_documents(trashed=False)
        trashed = self.storage.list_documents(trashed=True)

        if self.active_storage_folder is None:
            # Local-only My storage + Others hub (zero cloud storage)
            scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
            scroll.pack(fill="both", expand=True, padx=24, pady=20)

            ctk.CTkLabel(scroll, text="My storage", font=ctk.CTkFont(size=24, weight="bold")).pack(anchor="w", pady=(0, 12))

            downloads_cnt = sum(1 for d in docs if d.source == "DOWNLOADS")
            processed_cnt = sum(1 for d in docs if d.source == "CREATED")
            fav_cnt = sum(1 for d in docs if d.is_favorite)

            self._storage_row(scroll, "Internal Storage", str(len(docs)), lambda: self._open_storage_folder("Internal Storage"))
            self._storage_row(scroll, "Downloads", str(downloads_cnt), lambda: self._open_storage_folder("Downloads"))
            self._storage_row(scroll, "Processed files", str(processed_cnt), lambda: self._open_storage_folder("Processed files"))
            self._storage_row(
                scroll,
                "Import from storage",
                "Add files",
                lambda: self.import_from_pc("DOWNLOADS"),
                accent_right=True,
            )

            ctk.CTkLabel(scroll, text="Others", font=ctk.CTkFont(size=24, weight="bold")).pack(anchor="w", pady=(24, 12))
            self._storage_row(scroll, "Favorites", str(fav_cnt), lambda: self._open_storage_folder("Favorites"))
            self._storage_row(scroll, "Bin", str(len(trashed)), lambda: self._open_storage_folder("Bin"))
            return

        # Folder drill-down
        folder = self.active_storage_folder
        top = ctk.CTkFrame(self.main_area, fg_color="transparent")
        top.pack(fill="x", padx=24, pady=(18, 10))
        ctk.CTkButton(
            top,
            text="‹ My storage",
            width=110,
            fg_color="transparent",
            border_width=1,
            command=lambda: (setattr(self, "active_storage_folder", None), self.refresh_current_view()),
        ).pack(side="left")
        ctk.CTkLabel(top, text=folder, font=ctk.CTkFont(size=22, weight="bold")).pack(side="left", padx=14)

        if folder == "Bin" and trashed:
            ctk.CTkButton(
                top,
                text="Empty Bin",
                fg_color="#DC2626",
                hover_color="#B91C1C",
                command=self._confirm_empty_bin,
            ).pack(side="right")
        elif folder in {"Internal Storage", "Downloads"}:
            src = "DOWNLOADS" if folder == "Downloads" else "OPENED"
            ctk.CTkButton(
                top,
                text="+ Import",
                fg_color=PRIMARY_CORAL,
                hover_color=PRIMARY_HOVER,
                command=lambda: self.import_from_pc(src),
            ).pack(side="right")

        scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
        scroll.pack(fill="both", expand=True, padx=24, pady=(4, 20))

        if folder == "Bin":
            items = trashed
        elif folder == "Downloads":
            items = [d for d in docs if d.source == "DOWNLOADS"]
        elif folder == "Processed files":
            items = [d for d in docs if d.source == "CREATED"]
        elif folder == "Favorites":
            items = [d for d in docs if d.is_favorite]
        else:
            items = docs

        if not items:
            ctk.CTkLabel(scroll, text=f"No files in {folder}.", text_color=("gray45", "gray65")).pack(pady=40)
        else:
            for d in items:
                self._doc_row(scroll, d, in_bin=(folder == "Bin"))

    def _open_storage_folder(self, folder: str) -> None:
        self.active_storage_folder = folder
        self.refresh_current_view()

    def _confirm_empty_bin(self) -> None:
        if messagebox.askyesno("Empty Bin", "Permanently delete all files in Bin?", parent=self):
            self.storage.empty_bin()
            self.refresh_current_view()

    def _storage_row(
        self, parent: ctk.CTkBaseClass, title: str, right_text: str, cmd, accent_right: bool = False
    ) -> None:
        row = ctk.CTkFrame(parent, corner_radius=16, height=58)
        row.pack(fill="x", pady=5)
        row.pack_propagate(False)

        inner = ctk.CTkButton(
            row,
            text=f"  {title}",
            anchor="w",
            fg_color="transparent",
            text_color=("#1A1523", "#EDE9F2"),
            hover_color=("#F4ECE8", "#25212F"),
            font=ctk.CTkFont(size=15, weight="bold"),
            command=cmd,
        )
        inner.pack(side="left", fill="both", expand=True, padx=8)

        ctk.CTkLabel(
            row,
            text=right_text,
            text_color=PRIMARY_CORAL if accent_right else ("gray40", "gray65"),
            font=ctk.CTkFont(size=14, weight="bold" if accent_right else "normal"),
        ).pack(side="right", padx=18)

    def _doc_row(self, parent: ctk.CTkBaseClass, doc: DocumentRecord, in_bin: bool = False) -> None:
        card = ctk.CTkFrame(parent, corner_radius=16)
        card.pack(fill="x", pady=5)

        left = ctk.CTkFrame(card, fg_color="transparent")
        left.pack(side="left", fill="both", expand=True, padx=14, pady=10)
        ctk.CTkLabel(left, text=doc.name, font=ctk.CTkFont(size=14, weight="bold"), anchor="w").pack(fill="x")
        ctk.CTkLabel(
            left,
            text=f"{format_file_size(doc.size_bytes)}  •  {doc.page_count} page(s)  •  {doc.source.title()}",
            text_color=("gray40", "gray65"),
            font=ctk.CTkFont(size=12),
            anchor="w",
        ).pack(fill="x")

        btns = ctk.CTkFrame(card, fg_color="transparent")
        btns.pack(side="right", padx=12, pady=10)

        if in_bin:
            ctk.CTkButton(
                btns, text="Restore", width=76, fg_color=BRAND_TEAL, hover_color="#0F9485",
                command=lambda d=doc: (self.storage.restore_from_bin(d.id), self.refresh_current_view()),
            ).pack(side="left", padx=4)
            ctk.CTkButton(
                btns, text="Delete", width=74, fg_color="#DC2626", hover_color="#B91C1C",
                command=lambda d=doc: (self.storage.permanently_delete(d.id), self.refresh_current_view()),
            ).pack(side="left", padx=4)
        else:
            ctk.CTkButton(
                btns, text="Open", width=68, fg_color=PRIMARY_CORAL, hover_color=PRIMARY_HOVER,
                command=lambda d=doc: self.open_document(d),
            ).pack(side="left", padx=4)
            ctk.CTkButton(
                btns, text="★" if doc.is_favorite else "☆", width=38, fg_color="transparent", border_width=1,
                command=lambda d=doc: (self.storage.toggle_favorite(d.id), self.refresh_current_view()),
            ).pack(side="left", padx=3)
            ctk.CTkButton(
                btns, text="Rename", width=70, fg_color="transparent", border_width=1,
                command=lambda d=doc: self._rename_doc(d),
            ).pack(side="left", padx=3)
            ctk.CTkButton(
                btns, text="Bin", width=52, fg_color="transparent", border_width=1,
                command=lambda d=doc: (self.storage.move_to_bin(d.id), self.refresh_current_view()),
            ).pack(side="left", padx=3)

    def _rename_doc(self, doc: DocumentRecord) -> None:
        new_name = simpledialog.askstring("Rename File", "New filename:", initialvalue=doc.name, parent=self)
        if new_name and new_name.strip():
            self.storage.rename_document(doc.id, new_name.strip())
            self.refresh_current_view()

    def _render_tools(self) -> None:
        top = ctk.CTkFrame(self.main_area, fg_color="transparent")
        top.pack(fill="x", padx=24, pady=(18, 10))
        ctk.CTkLabel(top, text="All PDF & Image Tools", font=ctk.CTkFont(size=24, weight="bold")).pack(side="left")

        search_var = ctk.StringVar()
        entry = ctk.CTkEntry(top, textvariable=search_var, placeholder_text="Search tools...", width=260, corner_radius=18)
        entry.pack(side="right")

        scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
        scroll.pack(fill="both", expand=True, padx=24, pady=(0, 20))

        def populate(*_args) -> None:
            for w in scroll.winfo_children():
                w.destroy()
            q = search_var.get().strip().lower()
            for cat_name, tint in TOOL_CATEGORIES:
                cat_tools = [t for t in TOOLS if t.category == cat_name and (not q or q in t.title.lower() or q in t.description.lower())]
                if not cat_tools:
                    continue
                ctk.CTkLabel(scroll, text=cat_name, font=ctk.CTkFont(size=17, weight="bold"), text_color=tint).pack(
                    anchor="w", pady=(14, 6)
                )
                grid = ctk.CTkFrame(scroll, fg_color="transparent")
                grid.pack(fill="x")
                for idx, tool in enumerate(cat_tools):
                    btn = ctk.CTkButton(
                        grid,
                        text=f"{tool.title}\n{tool.description[:44]}",
                        height=68,
                        corner_radius=16,
                        fg_color=("white", "#1A1722"),
                        text_color=("#1A1523", "#EDE9F2"),
                        border_width=1,
                        border_color=("#E8DDD8", "#2E293B"),
                        hover_color=("#FFF0EC", "#272133"),
                        command=lambda t=tool: ToolDialog(self, t),
                    )
                    btn.grid(row=idx // 3, column=idx % 3, sticky="ew", padx=6, pady=6)
                    grid.grid_columnconfigure(idx % 3, weight=1)

        search_var.trace_add("write", populate)
        populate()

    def _render_templates(self) -> None:
        scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
        scroll.pack(fill="both", expand=True, padx=24, pady=20)
        ctk.CTkLabel(scroll, text="Create from Template", font=ctk.CTkFont(size=24, weight="bold")).pack(
            anchor="w", pady=(0, 14)
        )
        for name, desc in [
            ("Resume", "Clean two-section CV ready for inline text editing"),
            ("Invoice", "Itemized client billing invoice with totals"),
            ("Meeting Notes", "Structured agenda, decisions, and action items"),
            ("Lined Sheet", "Crisp ruled notebook page for notes and sketches"),
        ]:
            card = ctk.CTkFrame(scroll, corner_radius=18)
            card.pack(fill="x", pady=6)
            ctk.CTkLabel(card, text=name, font=ctk.CTkFont(size=16, weight="bold")).pack(anchor="w", padx=16, pady=(12, 2))
            ctk.CTkLabel(card, text=desc, text_color=("gray40", "gray65")).pack(anchor="w", padx=16, pady=(0, 10))
            ctk.CTkButton(
                card,
                text="Create & Open in Editor",
                fg_color=PRIMARY_CORAL,
                hover_color=PRIMARY_HOVER,
                corner_radius=18,
                command=lambda n=name: self._create_template(n),
            ).pack(anchor="w", padx=16, pady=(0, 14))

    def _create_template(self, name: str) -> None:
        out = create_template_pdf(name, self.storage.processed_dir)
        rec = self.storage.register_file(out, display_name=f"{name}.pdf", source="CREATED")
        self.open_document(rec)

    def _render_premium(self) -> None:
        scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
        scroll.pack(fill="both", expand=True, padx=24, pady=20)

        ctk.CTkLabel(scroll, text="Premium Membership (Ad-Free)", font=ctk.CTkFont(size=24, weight="bold")).pack(
            anchor="w", pady=(0, 6)
        )
        ctk.CTkLabel(
            scroll,
            text="Google Mail Login / Sign-Up is ONLY for Premium Membership. Free users can use all PDF tools with ads without signing in.",
            text_color=("gray35", "gray70"),
            wraplength=760,
            justify="left",
        ).pack(anchor="w", pady=(0, 14))

        # Status / Google Account card
        acct_card = ctk.CTkFrame(scroll, corner_radius=18)
        acct_card.pack(fill="x", pady=8)
        ctk.CTkLabel(
            acct_card,
            text="Google Mail Account (Premium Only)",
            font=ctk.CTkFont(size=15, weight="bold"),
            text_color=PRIMARY_CORAL,
        ).pack(anchor="w", padx=16, pady=(14, 6))

        if self.membership.is_signed_in:
            plan = self.membership.active_plan
            status_line = (
                f"Active: {plan.title} ({plan.price_display} / {plan.billing_period}) • Valid until "
                f"{datetime.fromtimestamp(self.membership.premium_until).strftime('%d %b %Y')} • ALL ADS REMOVED"
                if plan
                else "Status: Free Plan (Ads Enabled)"
            )
            ctk.CTkLabel(acct_card, text=f"Google Mail ID: {self.membership.email}", font=ctk.CTkFont(size=14, weight="bold")).pack(
                anchor="w", padx=16, pady=(2, 2)
            )
            ctk.CTkLabel(
                acct_card,
                text=status_line,
                text_color=BRAND_TEAL if plan else BRAND_AMBER,
                font=ctk.CTkFont(size=13, weight="bold"),
            ).pack(anchor="w", padx=16, pady=(0, 10))
            btn_row = ctk.CTkFrame(acct_card, fg_color="transparent")
            btn_row.pack(fill="x", padx=16, pady=(0, 14))
            if self.membership.is_premium_active:
                ctk.CTkButton(
                    btn_row,
                    text="Switch to Free Plan (Show Ads)",
                    fg_color="transparent",
                    border_width=1,
                    command=lambda: (self.membership.cancel_to_free(), self.refresh_current_view()),
                ).pack(side="left", padx=(0, 10))
            ctk.CTkButton(
                btn_row,
                text="Sign Out",
                width=100,
                fg_color="#DC2626",
                hover_color="#B91C1C",
                command=lambda: (self.membership.sign_out(), self.refresh_current_view()),
            ).pack(side="left")
        else:
            email_row = ctk.CTkFrame(acct_card, fg_color="transparent")
            email_row.pack(fill="x", padx=16, pady=(4, 14))
            email_entry = ctk.CTkEntry(email_row, placeholder_text="Enter your Google Mail ID (e.g. name@gmail.com)", height=40)
            email_entry.pack(side="left", fill="x", expand=True, padx=(0, 10))

            def do_google_login() -> None:
                try:
                    self.membership.sign_in_google_email(email_entry.get())
                    self.refresh_current_view()
                except ValueError as err:
                    messagebox.showwarning("Google Mail Required", str(err), parent=self)

            ctk.CTkButton(
                email_row,
                text="Login / Sign Up with Google",
                height=40,
                corner_radius=20,
                fg_color=PRIMARY_CORAL,
                hover_color=PRIMARY_HOVER,
                command=do_google_login,
            ).pack(side="right")

        # Plans Grid: ₹100 / 1 Month & ₹1,100 / 1 Year
        plans_row = ctk.CTkFrame(scroll, fg_color="transparent")
        plans_row.pack(fill="x", pady=10)
        for idx, plan in enumerate(PLANS):
            is_current = self.membership.is_premium_active and self.membership.plan_tier == plan.tier_id
            card = ctk.CTkFrame(
                plans_row,
                corner_radius=18,
                border_width=2 if (idx == 1 or is_current) else 1,
                border_color=BRAND_TEAL if is_current else (PRIMARY_CORAL if idx == 1 else ("#E8DDD8", "#2E293B")),
            )
            card.grid(row=0, column=idx, sticky="nsew", padx=8, pady=4)
            plans_row.grid_columnconfigure(idx, weight=1)

            ctk.CTkLabel(card, text=plan.badge, text_color=BRAND_TEAL if is_current else PRIMARY_CORAL, font=ctk.CTkFont(size=12, weight="bold")).pack(
                anchor="w", padx=16, pady=(14, 2)
            )
            ctk.CTkLabel(card, text=plan.title, font=ctk.CTkFont(size=18, weight="bold")).pack(anchor="w", padx=16)
            ctk.CTkLabel(
                card,
                text=f"{plan.price_display} / {plan.billing_period}",
                font=ctk.CTkFont(size=24, weight="bold"),
                text_color=PRIMARY_CORAL,
            ).pack(anchor="w", padx=16, pady=(6, 4))
            ctk.CTkLabel(
                card,
                text="• Zero Ads (Banner, Interstitial, Rewarded)\n• Works on Windows EXE & Android APK",
                justify="left",
                text_color=("gray35", "gray70"),
            ).pack(anchor="w", padx=16, pady=(0, 12))

            def on_activate(p=plan) -> None:
                if not self.membership.is_signed_in:
                    messagebox.showinfo("Google Login Required", "Enter your Google Mail ID above first to activate Premium.", parent=self)
                    return
                if messagebox.askyesno(
                    f"Activate {p.title}",
                    f"Account: {self.membership.email}\nPlan: {p.price_display} for {p.billing_period}\n\nActivate Premium Membership and remove all ads?",
                    parent=self,
                ):
                    self.membership.activate_plan(p)
                    self.refresh_current_view()
                    messagebox.showinfo("Premium Active", f"{p.title} ({p.price_display}) is now active! All ads have been removed.", parent=self)

            ctk.CTkButton(
                card,
                text="Active Plan" if is_current else f"Activate {plan.price_display} / {plan.billing_period}",
                state="disabled" if is_current else "normal",
                height=42,
                corner_radius=21,
                fg_color=BRAND_TEAL if is_current else PRIMARY_CORAL,
                hover_color=PRIMARY_HOVER,
                command=on_activate,
            ).pack(fill="x", padx=16, pady=(0, 16))

        if not self.membership.is_premium_active:
            reward_card = ctk.CTkFrame(scroll, corner_radius=18)
            reward_card.pack(fill="x", pady=10)
            ctk.CTkLabel(
                reward_card,
                text="Free User Bonus — Watch Rewarded Ad (30 min Ad-Free)",
                font=ctk.CTkFont(size=14, weight="bold"),
            ).pack(anchor="w", padx=16, pady=(14, 4))
            ctk.CTkLabel(
                reward_card,
                text="Watch a short sponsored ad to temporarily hide all ads in paperpilot Windows EXE for 30 minutes.",
                text_color=("gray35", "gray70"),
            ).pack(anchor="w", padx=16, pady=(0, 10))
            ctk.CTkButton(
                reward_card,
                text="Watch Rewarded Ad",
                height=38,
                corner_radius=19,
                fg_color=BRAND_TEAL,
                hover_color="#0F9485",
                command=self.launch_rewarded_ad,
            ).pack(anchor="w", padx=16, pady=(0, 14))

    def _render_settings(self) -> None:
        scroll = ctk.CTkScrollableFrame(self.main_area, fg_color="transparent")
        scroll.pack(fill="both", expand=True, padx=24, pady=20)
        ctk.CTkLabel(scroll, text="Settings", font=ctk.CTkFont(size=24, weight="bold")).pack(anchor="w", pady=(0, 16))

        # Premium Membership summary
        prem_card = ctk.CTkFrame(scroll, corner_radius=18)
        prem_card.pack(fill="x", pady=8)
        ctk.CTkLabel(prem_card, text="Premium Membership", font=ctk.CTkFont(size=15, weight="bold"), text_color=PRIMARY_CORAL).pack(
            anchor="w", padx=16, pady=(14, 6)
        )
        prem_row = ctk.CTkFrame(prem_card, fg_color="transparent")
        prem_row.pack(fill="x", padx=16, pady=(0, 14))
        prem_status = (
            f"★ Premium Active ({self.membership.email}) — All ads removed"
            if self.membership.is_premium_active
            else "Free Plan (Ad-Supported) • Upgrade for ₹100/month or ₹1,100/year"
        )
        ctk.CTkLabel(prem_row, text=prem_status).pack(side="left")
        ctk.CTkButton(
            prem_row,
            text="Manage Premium",
            fg_color=PRIMARY_CORAL,
            hover_color=PRIMARY_HOVER,
            command=lambda: self.switch_tab("premium"),
        ).pack(side="right")

        # Appearance
        app_card = ctk.CTkFrame(scroll, corner_radius=18)
        app_card.pack(fill="x", pady=8)
        ctk.CTkLabel(app_card, text="Appearance", font=ctk.CTkFont(size=15, weight="bold"), text_color=PRIMARY_CORAL).pack(
            anchor="w", padx=16, pady=(14, 8)
        )
        seg = ctk.CTkSegmentedButton(
            app_card,
            values=["System", "Light", "Dark"],
            command=self._set_theme,
        )
        seg.set(self.storage.settings.get("theme", "System"))
        seg.pack(fill="x", padx=16, pady=(0, 16))

        # Preferences
        pref_card = ctk.CTkFrame(scroll, corner_radius=18)
        pref_card.pack(fill="x", pady=8)
        ctk.CTkLabel(pref_card, text="Preferences", font=ctk.CTkFont(size=15, weight="bold"), text_color=PRIMARY_CORAL).pack(
            anchor="w", padx=16, pady=(14, 8)
        )
        folder_row = ctk.CTkFrame(pref_card, fg_color="transparent")
        folder_row.pack(fill="x", padx=16, pady=6)
        ctk.CTkLabel(folder_row, text=f"Output folder: {self.storage.settings['default_save_folder']}").pack(side="left")
        ctk.CTkButton(folder_row, text="Change", width=84, command=self._choose_save_folder).pack(side="right")

        ctk.CTkLabel(
            pref_card,
            text="Privacy: PDF files stay 100% on-device. Google login is optional and used strictly for Premium Membership.",
            text_color=BRAND_TEAL,
        ).pack(anchor="w", padx=16, pady=(8, 16))

    def _set_theme(self, mode: str) -> None:
        self.storage.settings["theme"] = mode
        self.storage.save_settings()
        ctk.set_appearance_mode(mode)

    def _choose_save_folder(self) -> None:
        chosen = filedialog.askdirectory(title="Select Default Save Folder", parent=self)
        if chosen:
            self.storage.settings["default_save_folder"] = chosen
            self.storage.save_settings()
            self.refresh_current_view()


def main() -> None:
    app = PaperPilotDesktopApp()
    app.mainloop()


if __name__ == "__main__":
    main()
