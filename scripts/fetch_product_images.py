#!/usr/bin/env python3
"""Search the web for a photo matching each product name, download it, and attach it
to the product through the product-service image-upload API.

Image sources (all keyless, tried in order):
  1. Wikipedia page thumbnail
  2. Wikimedia Commons file search
  3. Openverse

Products that already reference an uploaded (non-generated) image are skipped unless
--force is given, so the script is safe to re-run.

Usage:
    python3 scripts/fetch_product_images.py [--api URL] [--only-id ID] [--limit N] [--force] [--dry-run]
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import io
import json
import re
import sys
import time
import urllib.parse

import requests
from PIL import Image

JWT_SECRET = b"7a4f0c9b2e5d8a1c3f6b9e2d4a7c0f3b6e9a2d4c7f0b3e6a9d2c4f7b0e3a6c"
JWT_ISSUER = "auth-service"
ADMIN_EMAIL = "admin@example.com"
USER_AGENT = "ProductImageFetcher/1.0 (dev script)"
MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024
GENERATED_IMAGE_RE = re.compile(r"/product-\d+\.png$")

# Product name is matched against these keywords (first hit wins) to build a search term.
# More specific phrases must come before broader words (e.g. "display protector" before "mobile").
KEYWORDS = [
    ("iphone", "iPhone"),
    ("ipad", "iPad (tablet)"),
    ("laptop", "laptop"),
    ("back cover", "smartphone case"),
    ("display protector", "screen protector"),
    ("screen paper", "screen protector"),
    ("screen protector", "screen protector"),
    ("napa", "paracetamol"),
    ("android phone", "smartphone"),
    ("button phone", "feature phone"),
    ("mobile", "mobile phone"),
    ("tab", "tablet computer"),
    ("earphone", "headphones"),
    ("led-light", "LED lamp"),
    ("led light", "LED lamp"),
    ("charger", "battery charger"),
    ("battery", "battery (electricity)"),
    ("cable", "electrical cable"),
    ("sensodyne", "toothpaste"),
    ("colgate", "toothpaste"),
    ("colget", "toothpaste"),
    ("vigna", "mung bean"),
    ("mung", "mung bean"),
    ("mug-dal", "mung bean"),
    ("mug dal", "mung bean"),
    ("shukna", "dried chili pepper"),
    ("morich", "chili pepper"),
    ("piyaj", "onion"),
    ("onion", "onion"),
    ("alu", "potato"),
    ("potato", "potato"),
    ("soyabin", "soybean oil"),
    ("soybean", "soybean oil"),
    ("chal", "rice"),
    ("rice", "rice"),
    ("dal", "lentil"),
    ("lentil", "lentil"),
    ("ata", "wheat flour"),
    ("wheat", "wheat flour"),
    ("moyda", "flour"),
    ("flour", "flour"),
    ("chini", "sugar"),
    ("sugar", "sugar"),
    ("moshla", "spice"),
    ("spice", "spice"),
    ("lobon", "salt"),
    ("salt", "salt"),
    ("hair oil", "hair oil"),
    ("shampoo", "shampoo"),
    ("cream", "cream"),
    ("shaban", "soap"),
    ("detol", "soap"),
    ("dettol", "soap"),
    ("lux", "soap"),
    ("soap", "soap"),
    ("oil", "cooking oil"),
]

JUNK_NAMES = {"abc", "asdf", "sf", "new product xyz"}
JUNK_PREFIXES = ("approval product", "target test", "cleanname", "testmulti")


def make_token() -> str:
    def b64(raw: bytes) -> bytes:
        return base64.urlsafe_b64encode(raw).rstrip(b"=")

    header = b64(json.dumps({"alg": "HS384", "typ": "JWT"}).encode())
    now = int(time.time())
    payload = b64(json.dumps({
        "sub": ADMIN_EMAIL, "email": ADMIN_EMAIL, "name": "Admin", "role": "ADMIN",
        "iss": JWT_ISSUER, "iat": now, "exp": now + 3600,
    }).encode())
    signature = b64(hmac.new(JWT_SECRET, header + b"." + payload, hashlib.sha384).digest())
    return (header + b"." + payload + b"." + signature).decode()


def search_term(name: str) -> str | None:
    cleaned = name.strip()
    low = cleaned.lower()
    if low in JUNK_NAMES or low.startswith(JUNK_PREFIXES):
        return None
    for keyword, term in KEYWORDS:
        if keyword in low:
            return term
    # Fall back to the name with quantities/sizes removed.
    base = re.sub(r"[- ]?\d.*$", "", clean_name(cleaned)).strip(" -_")
    return base or None


def clean_name(name: str) -> str:
    return re.sub(r"\s+", " ", name.replace("-", " ")).strip()


def candidates(term: str) -> list[str]:
    urls: list[str] = []
    quoted = urllib.parse.quote(term.replace(" ", "_"))
    try:
        r = requests.get(f"https://en.wikipedia.org/api/rest_v1/page/summary/{quoted}",
                         headers={"User-Agent": USER_AGENT}, timeout=15)
        if r.ok:
            source = (r.json().get("thumbnail") or {}).get("source")
            if source:
                urls.append(source)
    except requests.RequestException:
        pass

    try:
        r = requests.get("https://commons.wikimedia.org/w/api.php", params={
            "action": "query", "generator": "search", "gsrsearch": f"filetype:bitmap {term}",
            "gsrnamespace": "6", "gsrlimit": "5", "prop": "imageinfo",
            "iiprop": "url", "iiurlwidth": "800", "format": "json",
        }, headers={"User-Agent": USER_AGENT}, timeout=20)
        if r.ok:
            for page in r.json().get("query", {}).get("pages", {}).values():
                info = page.get("imageinfo") or []
                if info and info[0].get("thumburl"):
                    urls.append(info[0]["thumburl"])
    except requests.RequestException:
        pass

    try:
        r = requests.get("https://api.openverse.org/v1/images/",
                         params={"q": term, "page_size": "5", "license_type": "all"},
                         headers={"User-Agent": USER_AGENT}, timeout=20)
        if r.ok:
            for item in r.json().get("results", []):
                if item.get("url"):
                    urls.append(item["url"])
    except requests.RequestException:
        pass

    return urls


def download_image(url: str) -> bytes | None:
    try:
        with requests.get(url, headers={"User-Agent": USER_AGENT}, timeout=30, stream=True) as r:
            if not r.ok or "image" not in r.headers.get("Content-Type", ""):
                return None
            data = bytearray()
            for chunk in r.iter_content(65536):
                data.extend(chunk)
                if len(data) > MAX_DOWNLOAD_BYTES:
                    return None
        image = Image.open(io.BytesIO(bytes(data)))
        image.load()
        if min(image.size) < 150:
            return None
        image = image.convert("RGB")
        image.thumbnail((800, 800))
        out = io.BytesIO()
        image.save(out, "JPEG", quality=85)
        return out.getvalue()
    except (requests.RequestException, OSError, ValueError):
        return None


def resolve_image(term: str) -> bytes | None:
    for url in candidates(term):
        image = download_image(url)
        if image:
            return image
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:8082/v1")
    parser.add_argument("--only-id", type=int)
    parser.add_argument("--ids", help="comma-separated product ids to process")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    selected_ids = None
    if args.ids:
        selected_ids = {int(x) for x in args.ids.split(",") if x.strip()}

    token = make_token()
    headers = {"Authorization": f"Bearer {token}"}
    products = requests.get(f"{args.api}/products/all", headers=headers, timeout=30).json()
    products.sort(key=lambda p: p["id"])

    processed = skipped = failed = 0
    for product in products:
        pid, name = product["id"], product["name"]
        if args.only_id and pid != args.only_id:
            continue
        if selected_ids is not None and pid not in selected_ids:
            continue
        if args.limit and processed >= args.limit:
            break

        image_url = product.get("imageUrl")
        if image_url and not GENERATED_IMAGE_RE.search(image_url) and not args.force:
            print(f"[skip] {pid:<3} {name!r} already has an uploaded image")
            skipped += 1
            continue

        term = search_term(name)
        if not term:
            print(f"[skip] {pid:<3} {name!r} has no usable search term")
            skipped += 1
            continue

        image = resolve_image(term)
        if not image:
            print(f"[fail] {pid:<3} {name!r} -> no image found for {term!r}")
            failed += 1
            continue

        if args.dry_run:
            print(f"[dry ] {pid:<3} {name!r} -> {term!r} ({len(image)} bytes)")
            processed += 1
            continue

        response = requests.put(f"{args.api}/products/{pid}/image", headers=headers,
                                files={"image": ("product.jpg", image, "image/jpeg")}, timeout=60)
        if response.status_code == 200:
            print(f"[ok  ] {pid:<3} {name!r} -> {term!r} ({len(image)} bytes)")
            processed += 1
        else:
            print(f"[fail] {pid:<3} {name!r} -> upload {response.status_code}: {response.text[:120]}")
            failed += 1
        time.sleep(0.4)

    print(f"\nprocessed={processed} skipped={skipped} failed={failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
