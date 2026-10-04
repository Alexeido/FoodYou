[![GitHub release (latest by date)](https://img.shields.io/github/v/release/Alexeido/FoodYou?color=black&label=Stable&logo=github)](https://github.com/Alexeido/FoodYou/releases/latest/)
[![GitHub all releases](https://img.shields.io/github/downloads/Alexeido/FoodYou/total?label=Downloads&logo=github)](https://github.com/Alexeido/FoodYou/releases/)
[![GitHub Repo stars](https://img.shields.io/github/stars/Alexeido/FoodYou?style=flat&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPHN2ZyBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIC05NjAgOTYwIDk2MCIgd2lkdGg9IjI0IiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPgogIDxwYXRoIGQ9Im0zNTQtMjQ3IDEyNi03NiAxMjYgNzctMzMtMTQ0IDExMS05Ni0xNDYtMTMtNTgtMTM2LTU4IDEzNS0xNDYgMTMgMTExIDk3LTMzIDE0M1pNMjMzLTgwbDY1LTI4MUw4MC01NTBsMjg4LTI1IDExMi0yNjUgMTEyIDI2NSAyODggMjUtMjE4IDE4OSA2NSAyODEtMjQ3LTE0OUwyMzMtODBabTI0Ny0zNTBaIiBzdHlsZT0iZmlsbDogcmdiKDI0NSwgMjI3LCA2Nik7Ii8%2BCjwvc3ZnPg%3D%3D&color=%23f8e444)](https://github.com/Alexeido/FoodYou/stargazers)

<div align="center">
    <img src="./metadata/en-US/images/featureGraphic.png" alt="Feature Graphic" />
</div>

<div align="center">

[<img src="https://s1.ax1x.com/2023/01/12/pSu1a36.png" alt="Download from GitHub" height="75">](https://github.com/Alexeido/FoodYou/releases)

</div>

**Food You** is a free, open-source food diary and nutrition tracker for Android, built with
[Material Design](https://m3.material.io/). It keeps your diary on your phone, and since 4.0 it can
also log food for you, keep several devices in sync, sit on your wrist and talk to your AI
assistant of choice.

> **About this fork.** Food You started as a fork of
> [maksimowiczm/FoodYou](https://github.com/maksimowiczm/FoodYou) by
> [Mateusz Maksimowicz](https://github.com/maksimowiczm), to whom all the foundations belong. The
> original project is no longer maintained, so this fork has been developed on its own since
> version 4.0, maintained by [Alexeido](https://github.com/Alexeido). Each release moves it further
> from upstream: an AI assistant, real recipes, sync, a Wear OS app and an MCP server are all
> specific to it. Package names still say `com.maksimowiczm.foodyou`, so existing installs keep
> updating.

## ✨ Features

<br>

<div align="center">
  <img src="metadata/en-US/images/phoneScreenshots/1.png" width="23%" alt="Modular Home Screen"/>
  <img src="metadata/en-US/images/phoneScreenshots/2.png" width="23%" alt="Comprehensive Food Databases"/>
  <img src="metadata/en-US/images/phoneScreenshots/3.png" width="23%" alt="Full Nutrition Tracking"/>
  <img src="metadata/en-US/images/phoneScreenshots/4.png" width="23%" alt="Recipe Creation"/>
</div>

<br>

### New in 4.x

- 🤖 **AI assistant.** Tell it, or show it a photo, of what you ate and it logs it. It plans whole
  days in a draft before writing anything, explains where a nutrient comes from, remembers what you
  tell it about yourself, and every change it makes can be undone and redone. It works with your
  own API key.
- 🍲 **Real recipes.** A dish is made of real foods with their own grams. You can change one
  ingredient for a single day without touching the recipe, and mark recipes as favourites.
- 🎯 **Nutrients you want to reach.** Pick calcium, iron, vitamin D, fibre or any other nutrient
  and it shows next to your macros on the home card, as a target to reach or a limit to stay under.
  The assistant takes it into account too.
- 🔄 **Sync across devices.** Your diary, goals, recipes and the assistant's memory stay the same on
  every phone within seconds, and keep working offline. It runs on your own server: see
  [`sync-server/`](sync-server/).
- ⌚ **Wear OS companion.** See today's meals and tick off what you've eaten from your watch. You
  pair it with a 6-digit code: [`wear/`](wear/).
- 🧠 **Use your diary from Claude and other assistants.** An [MCP](https://modelcontextprotocol.io)
  server reads and writes your diary, recipes and goals. It works with Claude Code or as a custom
  connector in the Claude apps: [`mcp-server/`](mcp-server/).
- 🗄️ **Your own food database.** Connect a self-hosted food database, with username and password,
  for text and barcode search alongside the built-in sources.
- ⬆️ **In-app updates.** You get a notice when a new version is out, and it downloads and installs
  without leaving the app.
- 🎨 **Redesigned search and diary.** Category icons, the brand on its own line, recently logged
  meals one tap away, and a goals card that animates as you tick food off.

### Since the beginning

- 🔒 **Privacy first.** No account required, and everything stays on your device unless you turn
  on sync with your own server.
- 🧩 **Modular home screen.** Arrange the cards that suit your habits.
- 📚 **Food databases.** Open Food Facts, USDA FoodData Central and the Swiss Food Composition
  Database.
- 🧪 **Full nutrition tracking.** Calories and macros, plus vitamins, minerals and other nutrients.
- 🎨 **Material You.** Adaptive theming and a modern UI.

## 🗂️ Repository

| Path | What it is |
|---|---|
| [`app/`](app/) | The Android app (Kotlin Multiplatform, Compose) |
| [`wear/`](wear/) | The Wear OS companion |
| [`sync-server/`](sync-server/) | Self-hosted sync server (FastAPI + SQLite) with a web admin panel |
| [`mcp-server/`](mcp-server/) | MCP server that gives AI assistants access to your diary |
| [`docs/sync/protocol.md`](docs/sync/protocol.md) | The sync protocol shared by the app, the watch and the MCP |
| [`shared/`](shared/) | Resources and the barcode scanner |

Build the app with `./gradlew :app:assembleDebug` and the watch app with
`./gradlew :wear:assembleDebug`. Each server has its own README and Dockerfile.

## 🤝 Contributing

- 💡 **Request a feature.** Open a [GitHub issue](https://github.com/Alexeido/FoodYou/issues).
- 🐞 **Report a bug.** Open a [GitHub issue](https://github.com/Alexeido/FoodYou/issues) and
  include the version shown in Settings → About.
- 🌍 **Translate.** Translations come from the original project on
  [Crowdin](https://crowdin.com/project/food-you). Strings added in this fork are in English and
  Spanish for now, so pull requests for other languages are welcome.
- ⭐ **Star the repository** if you find it useful.

## 🔄 Similar Open-Source Apps

- [OpenNutriTracker](https://github.com/simonoppowa/OpenNutriTracker)
- [Energize](https://codeberg.org/epinez/Energize)
- [FitBook](https://github.com/brandonp2412/FitBook)
- [Waistline](https://github.com/davidhealey/waistline)

## 💡 Credits

- [Mateusz Maksimowicz](https://github.com/maksimowiczm) — original author of Food You
- [ReadYou](https://github.com/Ashinch/ReadYou) — UI inspiration
- [Icons8](https://icons8.com) — sushi icon 🍣

## 📜 License

```
Copyright (C) 2024-2025 Mateusz Maksimowicz
Copyright (C) 2025-2026 Alexeido

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
```
