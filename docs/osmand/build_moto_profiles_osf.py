#!/usr/bin/env python3
"""Build NaviLas OsmAnd moto profile import package (.osf = zip)."""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

OUT = Path(__file__).with_name("NaviLas_osmand_moto_profiles.osf")

BROUTER_MOPED = {
    "stringKey": "brouter_moped",
    "userProfileName": "Brouter[moped]",
    "parent": "motorcycle",
    "iconName": "ic_action_motorcycle_dark",
    "iconColor": "ORANGE",
    "routingProfile": "motorcycle",
    "routeService": "BROUTER",
    "locIcon": "DEFAULT",
    "navIcon": "DEFAULT",
    "order": 36,
    "version": 1,
}

BROUTER_TREKKING = {
    "stringKey": "brouter_trekking",
    "userProfileName": "Brouter[trekking]",
    "parent": "motorcycle",
    "iconName": "ic_action_motorcycle_dark",
    "iconColor": "BLUE",
    "routingProfile": "motorcycle",
    "routeService": "BROUTER",
    "locIcon": "DEFAULT",
    "navIcon": "DEFAULT",
    "order": 37,
    "version": 1,
}


def profile_prefs() -> dict[str, str]:
    return {
        "route_service": "BROUTER",
        "routing_profile": "motorcycle",
        "derived_profile": "motorcycle",
        "user_profile_name": "",  # filled from appMode on import
    }


def main() -> None:
    items = {
        "version": 1,
        "items": [
            {
                "type": "PROFILE",
                "file": "profile_brouter_moped.json",
                "appMode": json.dumps(BROUTER_MOPED, separators=(",", ":")),
            },
            {
                "type": "PROFILE",
                "file": "profile_brouter_trekking.json",
                "appMode": json.dumps(BROUTER_TREKKING, separators=(",", ":")),
            },
            {
                "type": "GLOBAL",
                "file": "general_settings.json",
            },
        ],
    }

    global_prefs = {
        "available_application_modes": (
            "default,car,bicycle,pedestrian,public_transport,motorcycle,"
            "brouter_moped,brouter_trekking,"
        ),
    }

    moped_prefs = profile_prefs()
    moped_prefs["user_profile_name"] = "Brouter[moped]"
    trekking_prefs = profile_prefs()
    trekking_prefs["user_profile_name"] = "Brouter[trekking]"

    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("items.json", json.dumps(items, indent=2, ensure_ascii=False))
        zf.writestr("profile_brouter_moped.json", json.dumps(moped_prefs, indent=2))
        zf.writestr("profile_brouter_trekking.json", json.dumps(trekking_prefs, indent=2))
        zf.writestr("general_settings.json", json.dumps(global_prefs, indent=2))

    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
