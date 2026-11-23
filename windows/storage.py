"""Local SQLite storage and workspace management for paperpilot Windows Desktop.
100% on-device — zero accounts, zero sign-ins, zero cloud uploads.
"""

from __future__ import annotations

import json
import os
import shutil
import sqlite3
import time
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

import fitz  # PyMuPDF


@dataclass
class DocumentRecord:
    id: int
    name: str
    file_path: str
    mime_type: str
    size_bytes: int
    page_count: int
    source: str  # OPENED, DOWNLOADS, CREATED
    is_favorite: bool
    is_protected: bool
    is_trashed: bool
    created_at: float
    last_opened_at: float
    thumbnail_path: Optional[str] = None


class LocalStorage:
    def __init__(self) -> None:
        docs_root = Path.home() / "Documents" / "paperpilot"
        self.root_dir = docs_root
        self.files_dir = docs_root / "files"
        self.thumbs_dir = docs_root / "thumbnails"
        self.processed_dir = docs_root / "processed"
        for d in (self.root_dir, self.files_dir, self.thumbs_dir, self.processed_dir):
            d.mkdir(parents=True, exist_ok=True)

        self.db_path = self.root_dir / "paperpilot_desktop.db"
        self.settings_path = self.root_dir / "settings.json"
        self._init_db()
        self.settings = self._load_settings()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    mime_type TEXT NOT NULL DEFAULT 'application/pdf',
                    size_bytes INTEGER NOT NULL DEFAULT 0,
                    page_count INTEGER NOT NULL DEFAULT 1,
                    source TEXT NOT NULL DEFAULT 'CREATED',
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    is_protected INTEGER NOT NULL DEFAULT 0,
                    is_trashed INTEGER NOT NULL DEFAULT 0,
                    created_at REAL NOT NULL,
                    last_opened_at REAL NOT NULL,
                    thumbnail_path TEXT
                )
                """
            )
            conn.commit()

    def _load_settings(self) -> dict:
        defaults = {
            "theme": "System",
            "default_save_folder": str(self.processed_dir),
            "default_image_quality": 85,
            "keep_original_files": True,
            "premium_email": "",
            "premium_plan": "FREE",
            "premium_until": 0.0,
            "reward_ad_free_until": 0.0,
        }
        if self.settings_path.exists():
            try:
                data = json.loads(self.settings_path.read_text(encoding="utf-8"))
                defaults.update(data)
            except Exception:
                pass
        return defaults

    def save_settings(self) -> None:
        try:
            self.settings_path.write_text(json.dumps(self.settings, indent=2), encoding="utf-8")
        except Exception:
            pass

    def _row_to_doc(self, row: sqlite3.Row) -> DocumentRecord:
        return DocumentRecord(
            id=row["id"],
            name=row["name"],
            file_path=row["file_path"],
            mime_type=row["mime_type"],
            size_bytes=row["size_bytes"],
            page_count=row["page_count"],
            source=row["source"],
            is_favorite=bool(row["is_favorite"]),
            is_protected=bool(row["is_protected"]),
            is_trashed=bool(row["is_trashed"]),
            created_at=row["created_at"],
            last_opened_at=row["last_opened_at"],
            thumbnail_path=row["thumbnail_path"],
        )

    def count_all(self) -> int:
        with self._connect() as conn:
            return int(conn.execute("SELECT COUNT(*) FROM documents").fetchone()[0])

    def list_documents(self, trashed: bool = False) -> List[DocumentRecord]:
        with self._connect() as conn:
            rows = conn.execute(
                "SELECT * FROM documents WHERE is_trashed = ? ORDER BY last_opened_at DESC",
                (1 if trashed else 0,),
            ).fetchall()
        return [self._row_to_doc(r) for r in rows if Path(r["file_path"]).exists()]

    def get_document(self, doc_id: int) -> Optional[DocumentRecord]:
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM documents WHERE id = ?", (doc_id,)).fetchone()
        return self._row_to_doc(row) if row else None

    def generate_thumbnail(self, file_path: Path) -> Optional[str]:
        try:
            thumb_file = self.thumbs_dir / f"thumb_{int(time.time() * 1000)}_{file_path.stem[:24]}.png"
            if file_path.suffix.lower() == ".pdf":
                with fitz.open(file_path) as doc:
                    if doc.page_count == 0 or doc.needs_pass:
                        return None
                    pix = doc[0].get_pixmap(matrix=fitz.Matrix(0.45, 0.45))
                    pix.save(str(thumb_file))
                    return str(thumb_file)
            if file_path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".bmp"}:
                return str(file_path)
        except Exception:
            return None
        return None

    def inspect_pdf_meta(self, file_path: Path) -> tuple[int, bool]:
        if file_path.suffix.lower() != ".pdf":
            return 1, False
        try:
            with fitz.open(file_path) as doc:
                return max(1, doc.page_count), bool(doc.needs_pass)
        except Exception:
            return 1, False

    def register_file(
        self,
        file_path: Path,
        display_name: Optional[str] = None,
        source: str = "CREATED",
        is_favorite: bool = False,
        copy_into_workspace: bool = False,
    ) -> DocumentRecord:
        src = Path(file_path)
        target = src
        if copy_into_workspace:
            safe_name = f"{int(time.time() * 1000)}_{src.name}"
            target = self.files_dir / safe_name
            shutil.copy2(src, target)

        name = display_name or src.name
        ext = target.suffix.lower()
        mime = (
            "application/pdf"
            if ext == ".pdf"
            else "image/jpeg"
            if ext in {".jpg", ".jpeg"}
            else "image/png"
            if ext == ".png"
            else "application/zip"
            if ext == ".zip"
            else "text/plain"
        )
        pages, protected = self.inspect_pdf_meta(target)
        thumb = self.generate_thumbnail(target)
        now = time.time()
        size = target.stat().st_size if target.exists() else 0

        with self._connect() as conn:
            cur = conn.execute(
                """
                INSERT INTO documents
                (name, file_path, mime_type, size_bytes, page_count, source, is_favorite, is_protected, is_trashed, created_at, last_opened_at, thumbnail_path)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """,
                (name, str(target), mime, size, pages, source, 1 if is_favorite else 0, 1 if protected else 0, now, now, thumb),
            )
            doc_id = int(cur.lastrowid)
            conn.commit()
        return self.get_document(doc_id)  # type: ignore[return-value]

    def touch_opened(self, doc_id: int) -> None:
        with self._connect() as conn:
            conn.execute("UPDATE documents SET last_opened_at = ? WHERE id = ?", (time.time(), doc_id))
            conn.commit()

    def toggle_favorite(self, doc_id: int) -> None:
        with self._connect() as conn:
            row = conn.execute("SELECT is_favorite FROM documents WHERE id = ?", (doc_id,)).fetchone()
            if row is not None:
                conn.execute("UPDATE documents SET is_favorite = ? WHERE id = ?", (0 if row["is_favorite"] else 1, doc_id))
                conn.commit()

    def rename_document(self, doc_id: int, new_name: str) -> None:
        clean = new_name.strip()
        if not clean:
            return
        with self._connect() as conn:
            conn.execute("UPDATE documents SET name = ? WHERE id = ?", (clean, doc_id))
            conn.commit()

    def move_to_bin(self, doc_id: int) -> None:
        with self._connect() as conn:
            conn.execute("UPDATE documents SET is_trashed = 1 WHERE id = ?", (doc_id,))
            conn.commit()

    def restore_from_bin(self, doc_id: int) -> None:
        with self._connect() as conn:
            conn.execute("UPDATE documents SET is_trashed = 0 WHERE id = ?", (doc_id,))
            conn.commit()

    def permanently_delete(self, doc_id: int) -> None:
        doc = self.get_document(doc_id)
        with self._connect() as conn:
            conn.execute("DELETE FROM documents WHERE id = ?", (doc_id,))
            conn.commit()
        if doc:
            for p in (doc.file_path, doc.thumbnail_path):
                if p and Path(p).exists() and str(self.root_dir) in str(Path(p).resolve()):
                    try:
                        os.remove(p)
                    except OSError:
                        pass

    def empty_bin(self) -> None:
        for doc in self.list_documents(trashed=True):
            self.permanently_delete(doc.id)


def format_file_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    if size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes / (1024 * 1024):.2f} MB"
