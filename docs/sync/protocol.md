# Food You sync protocol, v1

Food You can keep its diary in sync across devices through a **sync server** of the user's
choosing. This document is the contract between the app and that server: any server that
implements it works, and the reference implementation lives in [`sync-server/`](../../sync-server).

Sync is independent from the custom food database: a person can use one, the other, both or
neither, each with its own address and account.

## Model

The server stores **documents**. It does not understand food or meals: it keeps JSON fields and
merges them. That is what lets other clients (a watch app, an MCP server for an assistant) read and
write the same diary without the server changing.

```json
{
  "kind": "food_entry",
  "id": "8f1c2b9e0a7d4c3b9e8f7a6b5c4d3e2f",
  "seq": 51,
  "deleted": false,
  "fields": {
    "isEaten": {"value": 1, "clock": 1759484921337, "device": "a91f03c2d7e84b10"},
    "quantity": {"value": 150.0, "clock": 1759484000000, "device": "a91f03c2d7e84b10"}
  }
}
```

- `kind` - what the document is (see [Kinds](#kinds)). 1-32 characters `[a-z_]`.
- `id` - chosen by the client that creates the document; globally unique (the app uses 32 hex
  characters). 1-64 characters `[A-Za-z0-9_-]`. Never reused.
- `fields` - any JSON value per field. Each field carries the **clock** of its last change
  (milliseconds since the Unix epoch, as measured on the device that made the change) and the
  `device` that made it.
- `_deleted` - an ordinary field holding `true` or `false`. A document is deleted by setting it to
  `true`; documents are never removed, so a deletion reaches every device. `deleted` in responses
  is a convenience copy of its value.
- `seq` - assigned by the server, per account, increasing by one every time a document changes.

## Merging: last writer wins, per field

When a change arrives, each field is compared with the stored one and the **newer** wins:

1. higher `clock` wins;
2. same `clock`: higher `device` (compared as text) wins, so every server and client reach the same
   result.

Fields are independent: a device marking an entry as eaten and another changing its grams at the
same time both keep their change. Deleting is just another field, so an older edit cannot bring a
deleted document back, and an explicit newer `_deleted: false` (an undo) can.

Clocks more than **5 minutes in the future** of the server's clock are lowered to that limit, so a
device with a wrong clock cannot win every conflict forever.

## Endpoints

All endpoints except `/health` require **HTTP Basic** authentication with the account's username
and password. The app also sends `X-Device-Id` (an opaque id per installation).

### `POST /v1/sync`

Pushes local changes and pulls everything newer than the client's cursor, in one call.

Request:

```json
{
  "cursor": 48,
  "changes": [
    {
      "kind": "food_entry",
      "id": "8f1c2b9e0a7d4c3b9e8f7a6b5c4d3e2f",
      "fields": {"isEaten": {"value": 1, "clock": 1759484921337}}
    }
  ]
}
```

- `cursor` - the `cursor` of the last response the client applied; `0` the first time.
- `changes` - at most **500** per request, each document at most **256 KB** of JSON. Only the
  fields that changed are needed. `device` is taken from `X-Device-Id`.

Response `200`:

```json
{
  "cursor": 52,
  "more": false,
  "documents": [ { "kind": "...", "id": "...", "seq": 51, "deleted": false, "fields": {} } ]
}
```

- `documents` - every document with `seq` greater than the request's `cursor`, in `seq` order, at
  most **500**. It includes the documents the client just sent, already merged: that is the
  authoritative state.
- `more` - `true` when more documents remain. The client repeats the call with the new `cursor`
  and no changes until it is `false`.

The client stores `cursor` only after applying the documents.

### `GET /v1/status`

```json
{"account": "alexeido", "cursor": 52, "documents": 1840, "serverTime": 1759484930000}
```

Used to test the connection from the app's settings.

### `GET /health`

`{"status": "ok"}`, no authentication.

### Errors

| Code | Meaning |
|---|---|
| 400 | Malformed request; nothing was applied |
| 401 | Wrong username or password |
| 403 | Account disabled |
| 413 | Request or a document too large |

A request is applied **all or nothing**.

## Kinds

The app syncs these kinds. Field names are the columns of the app's own tables, so a client that
writes them (an MCP server, a watch) has to follow these shapes. Unknown fields are kept by the
server and ignored by the app.

### `meal`

A meal of the day, as configured in the app.

| Field | Type | |
|---|---|---|
| `name` | string | "Breakfast" |
| `fromHour`, `fromMinute`, `toHour`, `toMinute` | int | When it usually happens |
| `rank` | int | Order in the diary |
| `icon` | string or null | `mat:<id>` or `emoji:<char>` |

### `food_entry`

A food or a recipe logged in the diary. It carries its own copy of the food (`food`), so it never
depends on any food database.

| Field | Type | |
|---|---|---|
| `meal` | string | `id` of a `meal` document |
| `epochDay` | int | Days since 1970-01-01 |
| `measurement` | int | Unit: `0` gram, `1` package, `2` serving, `3` millilitre, `4` ounce, `5` fluid ounce |
| `quantity` | number | Amount in that unit |
| `isEaten` | 0 or 1 | Ticked in the diary |
| `position` | int | Order within the meal |
| `createdAt`, `updatedAt` | int | Epoch seconds |
| `createdByAssistant` | 0 or 1 | |
| `food` | object | `{"product": {...}}` or `{"recipe": {..., "ingredients": [...]}}` |

`food.product` has the product snapshot: `name`, the nutrients per 100 g/ml (`energy` in kcal,
`proteins`, `carbohydrates`, `fats` and the rest in grams; micronutrients carry their unit in the
name: `cholesterolMilli`, `vitaminDMicro`...), `packageWeight`, `servingWeight`, `isLiquid`,
`sourceType` (`0` user, `1` Open Food Facts, `2` USDA, `3` Swiss database, `4` custom database),
`sourceUrl`, `note`, `categories` (Open Food Facts tags joined by commas). `food.recipe` has `name`, `servings`,
`isLiquid`, `note`, `category` and `ingredients`, each with `measurement` (same codes),
`quantity` and its own `food`.

### `manual_entry`

Something logged by its macros alone (quick add), optionally with a list of ingredients by name.

| Field | Type | |
|---|---|---|
| `meal` | string | `id` of a `meal` document |
| `dateEpochDay` | int | |
| `name` | string | |
| `energy`, `proteins`, `carbohydrates`, `fats`, ... | number or null | Totals for the entry, same names and units as a product |
| `category` | string or null | |
| `isEaten`, `createdByAssistant` | 0 or 1 | |
| `position` | int | |
| `createdEpochSeconds`, `updatedEpochSeconds` | int | |
| `ingredients` | array | `[{"name": "...", "grams": 30.0, "position": 0}]` |

Booleans travel as `0`/`1`, and units and sources as the numeric codes above, because that is
how the app stores them.

### `recipe`

A recipe from the app's library, from app 4.2. These are different from the recipe copies inside
diary entries.

| Field | Type | |
|---|---|---|
| `name` | string | |
| `servings` | int | |
| `note` | string or null | |
| `isLiquid`, `isFavorite` | 0 or 1 | |
| `ingredients` | array | `[{"measurement": 0, "quantity": 80.0, "product": {...}}]` or `{..., "recipe": "<id of another recipe>"}` |

The `product` object carries every column of the app's `Product` table except `id`, `isFavorite`
and `isEdited`: `name`, `brand`, `barcode`, `sourceBarcode`, `packageWeight`, `servingWeight`,
`note`, `sourceType`, `sourceUrl`, `isLiquid`, `categories`, and every nutrient column, in the
same units as the diary copies. Missing values are `null`. The receiving phone reuses a library
product with exactly those values, or creates one. Send all the keys: if you leave one out, the
phone reads the product back with that key and sends the recipe back once. An ingredient that
points at a recipe the phone doesn't have yet waits until that recipe arrives.

### `memory`

What the assistant remembers about the person, shared by the app's assistant and the MCP. It is
one document per account, always with id `memory`. Each field is one remembered fact: key →
text, for example `"alergias": "lactosa"`. A forgotten fact is set to `null`. On a device's first
sync the facts are merged: the account's facts are applied, and the facts only that phone knew
are uploaded.

### `goals`

The daily goals, from app 4.1. One document per account, always with id `goals`. They are app
settings rather than rows, so the fields are not table columns. Each field is merged on its own, so
a whole week of goals counts as a single value. On a device's first sync the account's goals win,
as with everything else. If the account has none yet, that device's goals become the account's.

| Field | Type | |
|---|---|---|
| `separateDays` | bool | Whether each weekday has its own goals |
| `days` | object | `{"monday": {"map": {...}, "isDistribution": true}, ... "sunday": ...}` |
| `tracked` | array of strings | Nutrients the person wants to reach, e.g. `["Calcium"]` |

`map` keys are nutrient names (`Energy`, `Proteins`, `Calcium`, `VitaminD`...) and values are in
grams per day, with kcal for `Energy`. With `isDistribution`, `Proteins`, `Fats` and
`Carbohydrates` are shares of the energy (0.2 = 20 %) instead of grams; the grams are
kcal × share / 4 (9 for fat). Every weekday is present even without `separateDays`. Pick the
weekday of the date you want.

`tracked` never contains energy or the three macros. Sugar, added sugar, saturated and trans fat,
salt, sodium, cholesterol and caffeine are limits: going over is the problem. Every other nutrient
is a minimum to reach. To add up a tracked nutrient over a day, use the entries' columns: the field
name with a lower-case first letter, plus `Milli` (mg) or `Micro` (µg) where the app uses those
units (`Calcium` → `calciumMilli`). A product without the value contributes nothing, and the total
is then a minimum.

## When the app syncs

The app writes every change locally first and never waits for the network. A sync runs shortly
after each change, every 30 seconds while the app is open, when it goes to the background, and
every 15 minutes while closed (as often as Android allows).

The first sync of a device merges instead of overwriting: documents that exist in the account are
taken from the account, local ones are uploaded, and meals with the same name are joined rather
than duplicated.
