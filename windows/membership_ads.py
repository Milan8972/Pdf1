"""Premium Membership (Google Mail ID only) & Ad-Supported experience for paperpilot Windows (.exe).

- Login / Sign-Up is ONLY required for Premium Membership (Rs 100 / 1 Month, Rs 1,100 / 1 Year).
- Active Premium members see ZERO ads anywhere in the Windows app.
- Non-premium users can use all PDF tools freely without logging in, supported by Banner,
  Interstitial, Rewarded Interstitial, and Rewarded Ad placements.
"""

from __future__ import annotations

import re
import time
from dataclasses import dataclass
from typing import Callable, Optional

import customtkinter as ctk

PRIMARY_CORAL = "#D03A44"
PRIMARY_HOVER = "#B52F38"
BRAND_AMBER = "#F59E0B"
BRAND_TEAL = "#14B8A6"

GOOGLE_EMAIL_RE = re.compile(r"^[A-Za-z0-9._%+\-]+@(gmail\.com|googlemail\.com|[A-Za-z0-9.\-]+\.[A-Za-z]{2,})$")


@dataclass(frozen=True)
class DesktopPlan:
    tier_id: str
    title: str
    price_inr: int
    duration_days: int
    price_display: str
    billing_period: str
    badge: str


PLANS = (
    DesktopPlan(
        tier_id="MONTHLY_99",
        title="Monthly Premium",
        price_inr=99,
        duration_days=30,
        price_display="₹99",
        billing_period="1 Month",
        badge="Flexible Monthly",
    ),
    DesktopPlan(
        tier_id="YEARLY_999",
        title="Yearly Premium",
        price_inr=999,
        duration_days=365,
        price_display="₹999",
        billing_period="1 Year",
        badge="Best Value • Save ₹189",
    ),
)


def is_valid_google_email(email: str) -> bool:
    clean = email.strip()
    return 5 <= len(clean) <= 254 and bool(GOOGLE_EMAIL_RE.match(clean))


class DesktopMembershipManager:
    """Manages Google Mail Premium Membership state and ad visibility for Windows EXE."""

    def __init__(self, settings: dict, save_cb: Callable[[], None]) -> None:
        self.settings = settings
        self.save_cb = save_cb
        self.last_interstitial_at: float = 0.0

    @property
    def email(self) -> str:
        return str(self.settings.get("premium_email", "")).strip()

    @property
    def is_signed_in(self) -> bool:
        return bool(self.email)

    @property
    def plan_tier(self) -> str:
        return str(self.settings.get("premium_plan", "FREE"))

    @property
    def premium_until(self) -> float:
        try:
            return float(self.settings.get("premium_until", 0.0))
        except (TypeError, ValueError):
            return 0.0

    @property
    def reward_ad_free_until(self) -> float:
        try:
            return float(self.settings.get("reward_ad_free_until", 0.0))
        except (TypeError, ValueError):
            return 0.0

    @property
    def is_premium_active(self) -> bool:
        return (
            self.is_signed_in
            and self.plan_tier in {"MONTHLY_100", "YEARLY_1100"}
            and self.premium_until > time.time()
        )

    @property
    def are_ads_suppressed(self) -> bool:
        return self.is_premium_active or self.reward_ad_free_until > time.time()

    @property
    def active_plan(self) -> Optional[DesktopPlan]:
        if not self.is_premium_active:
            return None
        for p in PLANS:
            if p.tier_id == self.plan_tier:
                return p
        return None

    def sign_in_google_email(self, email: str) -> None:
        clean = email.strip().lower()
        if not is_valid_google_email(clean):
            raise ValueError("Please enter a valid Google Mail ID (e.g. name@gmail.com).")
        self.settings["premium_email"] = clean
        self.save_cb()

    def sign_out(self) -> None:
        self.settings["premium_email"] = ""
        self.settings["premium_plan"] = "FREE"
        self.settings["premium_until"] = 0.0
        self.save_cb()

    def activate_plan(self, plan: DesktopPlan) -> None:
        if not self.is_signed_in:
            raise ValueError("Sign in with your Google Mail ID first to activate Premium.")
        self.settings["premium_plan"] = plan.tier_id
        self.settings["premium_until"] = time.time() + plan.duration_days * 86400
        self.save_cb()

    def cancel_to_free(self) -> None:
        self.settings["premium_plan"] = "FREE"
        self.settings["premium_until"] = 0.0
        self.save_cb()

    def grant_rewarded_ad_free(self, minutes: int = 30) -> None:
        until = time.time() + minutes * 60
        self.settings["reward_ad_free_until"] = max(self.reward_ad_free_until, until)
        self.save_cb()

    def should_show_interstitial(self, cooldown_sec: float = 45.0) -> bool:
        if self.are_ads_suppressed:
            return False
        now = time.time()
        if now - self.last_interstitial_at < cooldown_sec:
            return False
        self.last_interstitial_at = now
        return True


class DesktopAdModal(ctk.CTkToplevel):
    """Non-premium Interstitial / Rewarded Interstitial / Rewarded ad modal for Windows EXE.
    Never shown when Premium membership is active.
    """

    def __init__(
        self,
        master: ctk.CTk,
        ad_kind: str,  # "interstitial", "rewarded_interstitial", "rewarded"
        on_upgrade: Callable[[], None],
        on_reward_earned: Optional[Callable[[], None]] = None,
        on_closed: Optional[Callable[[], None]] = None,
    ) -> None:
        super().__init__(master)
        self.ad_kind = ad_kind
        self.on_upgrade = on_upgrade
        self.on_reward_earned = on_reward_earned
        self.on_closed = on_closed
        self.seconds_left = 8 if ad_kind == "rewarded" else 5
        self.reward_granted = False

        label_map = {
            "interstitial": "Sponsored Interstitial Ad",
            "rewarded_interstitial": "Rewarded Interstitial Ad (+15m Ad-Free)",
            "rewarded": "Rewarded Video Ad (+30m Ad-Free)",
        }
        self.title(f"paperpilot — {label_map.get(ad_kind, 'Sponsored Ad')}")
        self.geometry("480x330")
        self.resizable(False, False)
        self.grab_set()
        self.protocol("WM_DELETE_WINDOW", self._handle_close_attempt)

        card = ctk.CTkFrame(self, corner_radius=20)
        card.pack(fill="both", expand=True, padx=18, pady=18)

        badge_text = {
            "interstitial": "AD • INTERSTITIAL",
            "rewarded_interstitial": "AD • REWARDED INTERSTITIAL",
            "rewarded": "AD • REWARDED",
        }.get(ad_kind, "AD")

        ctk.CTkLabel(
            card,
            text=badge_text,
            text_color=BRAND_AMBER,
            font=ctk.CTkFont(size=11, weight="bold"),
        ).pack(anchor="w", padx=18, pady=(16, 4))

        ctk.CTkLabel(
            card,
            text="Sponsored Break — Free Ad-Supported Plan",
            font=ctk.CTkFont(size=18, weight="bold"),
        ).pack(anchor="w", padx=18, pady=(0, 6))

        body = (
            "Watch until the timer finishes to unlock 30 minutes ad-free, or upgrade to paperpilot Premium (₹100/mo or ₹1,100/yr) to remove all ads permanently."
            if ad_kind == "rewarded"
            else "paperpilot is free with ads. Complete this short break or switch to Premium Membership (₹100/month or ₹1,100/year) for a 100% ad-free desktop & Android experience."
        )
        ctk.CTkLabel(
            card,
            text=body,
            wraplength=400,
            justify="left",
            text_color=("gray30", "gray75"),
        ).pack(anchor="w", padx=18, pady=(4, 16))

        self.progress = ctk.CTkProgressBar(card, progress_color=BRAND_TEAL)
        self.progress.set(0.0)
        self.progress.pack(fill="x", padx=18, pady=(0, 8))

        self.timer_lbl = ctk.CTkLabel(
            card,
            text=f"Continue in {self.seconds_left}s...",
            font=ctk.CTkFont(size=13, weight="bold"),
            text_color=BRAND_TEAL,
        )
        self.timer_lbl.pack(anchor="w", padx=18, pady=(0, 16))

        actions = ctk.CTkFrame(card, fg_color="transparent")
        actions.pack(fill="x", side="bottom", padx=18, pady=(0, 16))

        ctk.CTkButton(
            actions,
            text="Remove Ads (₹100/mo)",
            fg_color=PRIMARY_CORAL,
            hover_color=PRIMARY_HOVER,
            height=40,
            corner_radius=20,
            command=self._go_premium,
        ).pack(side="left", fill="x", expand=True, padx=(0, 8))

        self.close_btn = ctk.CTkButton(
            actions,
            text=f"Wait ({self.seconds_left}s)",
            state="disabled",
            height=40,
            corner_radius=20,
            command=self._finish_and_close,
        )
        self.close_btn.pack(side="right", fill="x", expand=True)

        self.total_seconds = self.seconds_left
        self.after(1000, self._tick)

    def _tick(self) -> None:
        if not self.winfo_exists():
            return
        self.seconds_left -= 1
        done_ratio = min(1.0, (self.total_seconds - max(0, self.seconds_left)) / max(1, self.total_seconds))
        self.progress.set(done_ratio)
        if self.seconds_left <= 0:
            self.reward_granted = True
            if self.ad_kind in {"rewarded", "rewarded_interstitial"} and self.on_reward_earned:
                self.on_reward_earned()
            self.timer_lbl.configure(
                text="Reward unlocked! You can continue now."
                if self.ad_kind in {"rewarded", "rewarded_interstitial"}
                else "Ad finished — ready to continue."
            )
            self.close_btn.configure(state="normal", text="Continue")
        else:
            self.timer_lbl.configure(text=f"Continue in {self.seconds_left}s...")
            self.close_btn.configure(text=f"Wait ({self.seconds_left}s)")
            self.after(1000, self._tick)

    def _handle_close_attempt(self) -> None:
        if self.seconds_left <= 0:
            self._finish_and_close()

    def _go_premium(self) -> None:
        self.destroy()
        self.on_upgrade()

    def _finish_and_close(self) -> None:
        self.destroy()
        if self.on_closed:
            self.on_closed()
