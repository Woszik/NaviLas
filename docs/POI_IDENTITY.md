# Identyfikacja POI BDL (Checkpoint 1A)

## Pola przeanalizowane

| Pole | Warstwy | Obserwacja |
|---|---|---|
| `foreign_key` | 0, 15, 17 | UUID; obecny w próbkach; wygląda na stabilny klucz biznesowy współdzielony z ekosystemem Czas w Las |
| `objectid` | wszystkie | OID ArcGIS — może się zmienić przy przeładowaniu usługi / republish |
| `tur_rec_pnt_id` | 15, 17 | Liczbowy ID domenowy obiektu punktowego |
| `tur_sleep_poly_id` | 0 | Liczbowy ID domenowy obszaru Zanocuj |
| `inv_nr` | 0, 15, 17 | Często `null` w próbkach — **nieużyteczny** jako klucz główny |

## Decyzja

Wewnętrzny identyfikator budujemy jako:

```text
bdl:{layerId}:{scheme}:{value}
```

Priorytet `scheme`:

1. **`foreign_key`** (preferowany) — najbardziej stabilny i obecny na używanych warstwach  
2. **`tur_rec_pnt_id`** (warstwy 15/17) lub **`tur_sleep_poly_id`** (warstwa 0)  
3. **`objectid`** — wyłącznie fallback

Przykład: `bdl:17:foreign_key:8a00f2dc-87e8-429e-9e0c-48ac6e1a4070`

Prefix warstwy zapobiega kolizjom, gdyby domenowe ID powtórzyły się między typami obiektów.

Implementacja: `pl.navilas.finder.data.bdl.BdlIdentity`.

## Geometria

- Warstwy **15** i **17**: geometria punktowa → `PoiGeometryKind.POINT`  
- Warstwa **0**: poligon → `PoiGeometryKind.AREA` + pierścienie w `areaRings`  
- `latitude` / `longitude` dla AREA = **centroid pomocniczy** (marker / sortowanie odległości), **nie** punkt docelowy nawigacji
