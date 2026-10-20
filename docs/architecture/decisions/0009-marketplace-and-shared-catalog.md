# ADR 0009: Marketplace tenancy and a shared product catalog

- **Status:** Accepted, 2026-10-20

## Context
Dukkan becomes a marketplace of local markets (supermarkets, ethnic groceries,
bakeries, butchers, …). Merchants should not have to type every product from
scratch, and the product owner asked for product databases per cuisine
(Turkish, Arabic, Indian, …).

## Decision
- `market` is the tenant. Listings, promotions, banners, slots, carts and orders belong to exactly one market. A `market_owner` table links merchant accounts to their markets; requests for a market the caller doesn't own return 404.
- `catalog_product` is a shared master record: translations, keywords, cuisine tags, allergens, unit and image. A product is either shared (`owner_market_id` NULL) or private to one market.
- `market_product` is the listing: price, availability and maximum quantity. API product ids are listing ids.
- Geo search uses lat/lng and Haversine in SQL. PostGIS is deferred until delivery areas need polygons.

## Consequences
Product data quality improves over time as markets reuse shared records.
Moderating shared records, and promoting good private products into the shared
catalog, is an admin task for later. AI-assisted product capture from voice,
photo or video will create `catalog_product` drafts through the same model
(backlog).
