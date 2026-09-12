#!/usr/bin/env python3
"""Build NaviLas OsmAnd moto profile import package (.osf = zip).

OsmAnd offline routing (no BRouter). Same stringKey as previous BRouter
profiles so re-import replaces them; first install adds alongside Motocykl.
"""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "NaviLas_osmand_moto_profiles.osf"
ROUTING_XML = HERE / "navilas_moto_routing.xml"
ROUTING_IN_ZIP = "routing/navilas_moto_routing.xml"

# Keep stringKeys stable → OsmAnd import replaces previous NaviLas profiles.
SHORT = {
    "stringKey": "brouter_trekking",
    "userProfileName": "NaviLas krótka",
    "parent": "motorcycle",
    "iconName": "ic_action_enduro_motorcycle",
    "iconColor": "GREEN",
    "routingProfile": "navilas_moto_routing.xml/navilas_short",
    "routeService": "OSMAND",
    "locIcon": "DEFAULT",
    "navIcon": "DEFAULT",
    "order": 37,
    "version": 2,
}

SCENIC = {
    "stringKey": "brouter_moped",
    "userProfileName": "NaviLas kręta",
    "parent": "motorcycle",
    "iconName": "ic_action_offroad",
    "iconColor": "PURPLE",
    "routingProfile": "navilas_moto_routing.xml/navilas_scenic",
    "routeService": "OSMAND",
    "locIcon": "DEFAULT",
    "navIcon": "DEFAULT",
    "order": 36,
    "version": 2,
}


def profile_prefs(user_name: str, routing_profile: str) -> dict[str, str]:
    return {
        "route_service": "OSMAND",
        "routing_profile": routing_profile,
        "derived_profile": "motorcycle",
        "user_profile_name": user_name,
    }


def main() -> None:
    if not ROUTING_XML.is_file():
        raise SystemExit(f"Missing {ROUTING_XML}")

    items = {
        "version": 1,
        "items": [
            {
                "type": "FILE",
                "subtype": "routing_config",
                "file": f"/{ROUTING_IN_ZIP}",
            },
            {
                "type": "PROFILE",
                "file": "profile_navilas_short.json",
                "appMode": json.dumps(SHORT, separators=(",", ":")),
            },
            {
                "type": "PROFILE",
                "file": "profile_navilas_scenic.json",
                "appMode": json.dumps(SCENIC, separators=(",", ":")),
            },
            {
                "type": "GLOBAL",
                "file": "general_settings.json",
            },
        ],
    }

    # Enables Motocykl + NaviLas profiles; does not remove factory Motocykl.
    global_prefs = {
        "available_application_modes": (
            "default,car,bicycle,pedestrian,public_transport,motorcycle,"
            "brouter_moped,brouter_trekking,"
        ),
    }

    short_prefs = profile_prefs(
        SHORT["userProfileName"],
        SHORT["routingProfile"],
    )
    scenic_prefs = profile_prefs(
        SCENIC["userProfileName"],
        SCENIC["routingProfile"],
    )

    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("items.json", json.dumps(items, indent=2, ensure_ascii=False))
        zf.writestr("profile_navilas_short.json", json.dumps(short_prefs, indent=2))
        zf.writestr("profile_navilas_scenic.json", json.dumps(scenic_prefs, indent=2))
        zf.writestr("general_settings.json", json.dumps(global_prefs, indent=2))
        zf.write(ROUTING_XML, ROUTING_IN_ZIP)

    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
