# Custom food source — recognized category tags

The app shows a small icon/emoji next to each search result based on the `categories` array of
the `Product` object (see [custom-food-source-openapi.yaml](custom-food-source-openapi.yaml)).
That matching happens in `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/search/FoodCategory.kt`
and only recognizes tags that:

- start with the `en:` prefix (any other prefix is ignored), and
- exactly match one of the tag strings listed below (these are the same tag slugs Open Food
  Facts uses, since the app currently reuses OFF's taxonomy).

If none of a product's tags match, it falls back to a generic "Otros/Desconocido" (❔) icon.

**Tip**: a product's `categories` array is checked from the *last* tag to the *first*, and the
first match wins — so put your most specific tag last if you send more than one.

| Icon | Category | Recognized `en:` tags |
|---|---|---|
| 🏪 | Restaurantes | *(no tags — manual/user category only)* |
| 🍱 | Platos preparados | `en:meals`, `en:meals-with-meat`, `en:pasta-dishes`, `en:sandwiches`, `en:pizzas-pies-and-quiches`, `en:meals-with-chicken`, `en:microwave-meals`, `en:meals-with-fish`, `en:beef-dishes`, `en:poultry-meals`, `en:prepared-salads`, `en:frozen-ready-made-meals`, `en:pizzas`, `en:frozen-pizzas-and-pies`, `en:rice-dishes`, `en:entrees`, `en:canned-meals`, `en:canned-soups`, `en:soups`, `en:vegetable-soups`, `en:broths` |
| 🍜 | Comida instantánea | `en:instant-noodles`, `en:dried-products-to-be-rehydrated` |
| 💊 | Suplementos | `en:dietary-supplements`, `en:bodybuilding-supplements`, `en:protein-powders`, `en:protein-bars`, `en:capsules` |
| 🍔 | Carnes vegetales | `en:meat-alternatives`, `en:meat-analogues`, `en:plant-based-meats` |
| 🥐 | Panadería | `en:biscuits-and-cakes`, `en:pastries`, `en:sweet-pastries-and-pies`, `en:viennoiseries`, `en:cakes`, `en:biscuits`, `en:biscuits-and-crackers`, `en:brioches`, `en:pies`, `en:sweet-pies`, `en:panettone`, `en:wafers`, `en:madeleines`, `en:dry-biscuits`, `en:filled-biscuits`, `en:crepes-and-galettes`, `en:shortbread-cookies`, `en:cake-mixes`, `en:baking-mixes`, `en:dessert-mixes`, `en:pastry-helpers` |
| 🍬 | Dulces | `en:confectioneries`, `en:candies`, `en:sweet-snacks`, `en:desserts`, `en:dairy-desserts`, `en:fermented-dairy-desserts`, `en:frozen-desserts`, `en:chocolate-candies`, `en:bonbons`, `en:fermented-dairy-desserts-with-fruits`, `en:gummi-candies`, `en:christmas-sweets`, `en:chewing-gum`, `en:non-dairy-desserts`, `en:turron`, `en:puddings`, `en:syrups`, `en:flavoured-syrups`, `en:simple-syrups`, `en:sweeteners`, `en:sugars` |
| 🍫 | Chocolates | `en:chocolates`, `en:dark-chocolates`, `en:chocolate-biscuits`, `en:milk-chocolates`, `en:chocolate-cakes`, `en:chocolate-cereals`, `en:cocoa-and-chocolate-powders`, `en:cocoa-and-its-products` |
| 🍦 | Helados | `en:ice-creams-and-sorbets`, `en:ice-creams`, `en:ice-cream-tubs` |
| 🍿 | Snacks | `en:snacks`, `en:salty-snacks`, `en:chips-and-fries`, `en:crisps`, `en:appetizers`, `en:potato-crisps`, `en:crackers`, `en:corn-chips`, `en:popcorn`, `en:salted-snacks`, `en:breadsticks`, `en:crackers-appetizers` |
| 🌾 | Granos | `en:rices`, `en:long-grain-rices`, `en:aromatic-rices`, `en:indica-rices`, `en:puffed-cereal-cakes`, `en:puffed-corn-cakes` |
| 🍷 | Bebidas alcohólicas | `en:alcoholic-beverages`, `en:wines`, `en:beers`, `en:hard-liquors`, `en:distilled-beverages`, `en:red-wines`, `en:country-specific-beers`, `en:wines-from-france` |
| 🧋 | Bebidas vegetales | `en:plant-based-beverages`, `en:plant-based-milk-alternatives`, `en:dairy-substitutes`, `en:milk-substitutes` |
| ☕ | Café e Infusiones | `en:teas`, `en:coffees`, `en:hot-beverages`, `en:herbal-teas`, `en:instant-coffees`, `en:coffee-capsules`, `en:green-teas`, `en:tea-bags` |
| 🥤 | Bebidas | `en:beverages`, `en:beverages-and-beverages-preparations`, `en:carbonated-drinks`, `en:sodas`, `en:waters`, `en:juices-and-nectars`, `en:fruit-juices`, `en:energy-drinks`, `en:colas`, `en:iced-teas`, `en:mineral-waters`, `en:sweetened-beverages`, `en:fruit-based-beverages`, `en:dairy-drinks`, `en:tea-based-beverages`, `en:fermented-drinks`, `en:artificially-sweetened-beverages`, `en:spring-waters`, `en:instant-beverages`, `en:non-alcoholic-beverages`, `en:fruit-nectars`, `en:orange-juices`, `en:apple-juices`, `en:dehydrated-beverages`, `en:squeezed-juices`, `en:diet-beverages`, `en:cereal-based-drinks`, `en:carbonated-waters`, `en:natural-mineral-waters` |
| 🫙 | Salsas | `en:sauces`, `en:condiments`, `en:tomato-sauces`, `en:salad-dressings`, `en:mayonnaises`, `en:ketchup`, `en:mustards`, `en:pestos`, `en:vinegars`, `en:pasta-sauces`, `en:barbecue-sauces`, `en:meal-sauces`, `en:cooking-helpers` |
| 🥫 | Untables | `en:spreads`, `en:sweet-spreads`, `en:jams`, `en:dips`, `en:peanut-butters`, `en:chocolate-spreads`, `en:hummus`, `en:plant-based-spreads`, `en:salted-spreads`, `en:berry-jams`, `en:legume-butters`, `en:nut-butters`, `en:rillettes`, `en:hazelnut-spreads`, `en:strawberry-jams`, `en:cocoa-and-hazelnuts-spreads` |
| 🥓 | Fiambres | `en:prepared-meats`, `en:hams`, `en:sausages`, `en:cured-sausages`, `en:salami`, `en:cured-hams`, `en:white-hams`, `en:terrines`, `en:french-sausages`, `en:italian-meat-products`, `en:spanish-meat-products`, `en:pate`, `en:dry-sausages`, `en:serrano-ham`, `en:charcuteries-cuites`, `en:charcuteries-diverses` |
| 🧀 | Quesos | `en:cheeses`, `en:cow-cheeses`, `en:hard-cheeses`, `en:soft-cheeses`, `en:mozzarella`, `en:french-cheeses`, `en:italian-cheeses`, `en:uncooked-pressed-cheeses`, `en:cream-cheeses`, `en:soft-cheeses-with-bloomy-rind`, `en:sheeps-milk-cheeses`, `en:goat-cheeses`, `en:pasteurized-cheeses`, `en:comte`, `en:emmentaler`, `en:cheeses-of-the-netherlands`, `en:cheeses-from-the-united-kingdom`, `en:grated-cheese`, `en:cheeses-from-england` |
| 🥛 | Yogurt | `en:yogurts`, `en:fermented-milk-products`, `en:fruit-yogurts`, `en:plain-yogurts`, `en:greek-style-yogurts`, `en:fermented-foods` |
| 🥛 | Leche | `en:dairies`, `en:milks`, `en:whole-milks`, `en:semi-skimmed-milks`, `en:uht-milks`, `en:milks-liquid-and-powder`, `en:homogenized-milks` |
| 🥖 | Panes | `en:breads`, `en:sliced-breads`, `en:special-breads`, `en:white-breads`, `en:flatbreads` |
| 🍝 | Pasta | `en:pastas`, `en:dry-pastas`, `en:stuffed-pastas`, `en:durum-wheat-pasta`, `en:spaghetti`, `en:noodles`, `en:ravioli` |
| 🥣 | Cereales | `en:cereals-and-their-products`, `en:breakfast-cereals`, `en:mueslis`, `en:flakes`, `en:rolled-oats`, `en:cereal-grains`, `en:cereal-flakes`, `en:extruded-cereals`, `en:cereals-with-fruits`, `en:rolled-flakes`, `en:cereal-bars`, `en:cereals-and-potatoes` |
| 🌾 | Harinas | `en:flours`, `en:cereal-flours`, `en:wheat-flours` |
| 🍗 | Pollo | `en:poultries`, `en:chickens`, `en:chicken-and-its-products`, `en:chicken-breasts`, `en:turkeys`, `en:turkey-and-its-products`, `en:cooked-poultries`, `en:turkey-cutlets`, `en:breaded-chicken`, `en:chicken-preparations` |
| 🐖 | Cerdo | `en:pork-and-its-products`, `en:pork` |
| 🥩 | Res | `en:meats`, `en:beef-and-its-products`, `en:beef`, `en:meats-and-their-products`, `en:meat-preparations` |
| 🐟 | Pescado | `en:fishes`, `en:fatty-fishes`, `en:tunas`, `en:salmons`, `en:sardines`, `en:fish-fillets`, `en:canned-fishes`, `en:smoked-fishes`, `en:fish-preparations`, `en:canned-tunas`, `en:smoked-salmons`, `en:canned-sardines`, `en:tunas-in-oil`, `en:fishes-and-their-products` |
| 🦞 | Mariscos | `en:seafood`, `en:crustaceans`, `en:mollusc`, `en:shrimps`, `en:frozen-seafood` |
| 🍎 | Frutas | `en:fruits`, `en:fresh-fruits`, `en:berries`, `en:tropical-fruits`, `en:apple-compotes`, `en:dates`, `en:compotes`, `en:fruits-based-foods`, `en:dried-fruits` |
| 🥦 | Verduras | `en:vegetables`, `en:fresh-vegetables`, `en:leaf-vegetables`, `en:tomatoes`, `en:fruit-and-vegetable-preserves`, `en:canned-vegetables`, `en:tomatoes-and-their-products`, `en:pickles`, `en:plant-based-pickles`, `en:frozen-vegetables`, `en:culinary-plants`, `en:olives`, `en:prepared-vegetables`, `en:salads`, `en:vegetable-rods`, `en:mushrooms-and-their-products`, `en:pickled-vegetables`, `en:mushrooms`, `en:green-olives`, `en:vegetables-based-foods`, `en:fruits-and-vegetables-based-foods` |
| 🫘 | Legumbres | `en:legumes-and-their-products`, `en:pulses`, `en:lentils`, `en:common-beans`, `en:legumes`, `en:legume-seeds`, `en:canned-common-beans`, `en:canned-legumes` |
| 🥜 | Frutos secos | `en:nuts`, `en:almonds`, `en:peanuts`, `en:cashew-nuts`, `en:shelled-nuts`, `en:nuts-and-their-products` |
| 🌰 | Semillas | `en:seeds`, `en:sunflower-seeds-and-their-products` |
| 🌻 | Aceites | `en:fats`, `en:vegetable-oils`, `en:olive-oils`, `en:extra-virgin-olive-oils`, `en:butters`, `en:vegetable-fats`, `en:virgin-olive-oils`, `en:spreadable-fats`, `en:dairy-spreads`, `en:margarines`, `en:animal-fats`, `en:milkfat` |
| 🌿 | Especias y hierbas | `en:spices`, `en:herbs`, `en:aromatic-plants`, `en:salts` |
| 🥚 | Huevo | `en:eggs`, `en:fresh-eggs`, `en:chicken-eggs`, `en:liquid-eggs`, `en:egg-yolks`, `en:egg-whites`, `en:omelettes` |
| 🍽️ | Otros | `en:groceries`, `en:canned-foods`, `en:frozen-foods`, `en:farming-products`, `en:dried-products` |
| ❔ | Otros/Desconocido | *(fallback — used when no tag matches, or `categories` is empty/omitted)* |

## Example

```json
{
  "name": "Pechuga de pollo",
  "categories": ["en:meats-and-their-products", "en:chicken-breasts"]
}
```

Matches `en:chicken-breasts` (checked last-to-first, so the most specific tag should go last) →
renders the 🍗 Pollo icon.

## If you'd rather not use this taxonomy

Sending an unrecognized tag (or omitting `categories` entirely) is completely fine — the product
just falls back to the generic ❔ icon. Nothing else about search, barcode lookup, or nutrition
data depends on `categories`.

Source of truth: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/search/FoodCategory.kt`
in the app repo. If that file changes, re-generate this list from it.
