package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * IMPORTANTE: Ahora usamos coincidencia EXACTA de los tags de Open Food Facts.
 * Los tags en la API van de lo general a lo específico, así que en la función
 * iteraremos al revés para atrapar siempre la categoría más precisa primero.
 */
enum class FoodCategory(val label: String, val emoji: String, val offTags: Set<String> = emptySet()) {
    
    RESTAURANTES("Restaurantes", "🏪"),

    PLATOS_PREPARADOS("Platos preparados", "🍱", setOf(
        "en:meals", "en:meals-with-meat", "en:pasta-dishes", "en:sandwiches", "en:pizzas-pies-and-quiches",
        "en:meals-with-chicken", "en:microwave-meals", "en:meals-with-fish", "en:beef-dishes", "en:poultry-meals",
        "en:prepared-salads", "en:frozen-ready-made-meals", "en:pizzas", "en:frozen-pizzas-and-pies",
        "en:rice-dishes", "en:entrees", "en:canned-meals", "en:canned-soups", "en:soups", "en:vegetable-soups", "en:broths"
    )),
    COMIDA_INSTANTANEA("Comida instantánea", "🍜", setOf(
        "en:instant-noodles", "en:dried-products-to-be-rehydrated"
    )),
    SUPLEMENTOS("Suplementos", "💊", setOf(
        "en:dietary-supplements", "en:bodybuilding-supplements", "en:protein-powders", "en:protein-bars", "en:capsules"
    )),
    CARNES_VEGETALES("Carnes vegetales", "🍔", setOf(
        "en:meat-alternatives", "en:meat-analogues", "en:plant-based-meats"
    )),

    PANADERIA("Panadería", "🥐", setOf(
        "en:biscuits-and-cakes", "en:pastries", "en:sweet-pastries-and-pies", "en:viennoiseries", "en:cakes",
        "en:biscuits", "en:biscuits-and-crackers", "en:brioches", "en:pies", "en:sweet-pies", "en:panettone",
        "en:wafers", "en:madeleines", "en:dry-biscuits", "en:filled-biscuits", "en:crepes-and-galettes",
        "en:shortbread-cookies", "en:cake-mixes", "en:baking-mixes", "en:dessert-mixes", "en:pastry-helpers"
    )),
    DULCES("Dulces", "🍬", setOf(
        "en:confectioneries", "en:candies", "en:sweet-snacks", "en:desserts", "en:dairy-desserts",
        "en:fermented-dairy-desserts", "en:frozen-desserts", "en:chocolate-candies", "en:bonbons",
        "en:fermented-dairy-desserts-with-fruits", "en:gummi-candies", "en:christmas-sweets", "en:chewing-gum",
        "en:non-dairy-desserts", "en:turron", "en:puddings", "en:syrups", "en:flavoured-syrups", "en:simple-syrups",
        "en:sweeteners", "en:sugars"
    )),
    CHOCOLATES("Chocolates", "🍫", setOf(
        "en:chocolates", "en:dark-chocolates", "en:chocolate-biscuits", "en:milk-chocolates",
        "en:chocolate-cakes", "en:chocolate-cereals", "en:cocoa-and-chocolate-powders", "en:cocoa-and-its-products"
    )),
    HELADOS("Helados", "🍦", setOf(
        "en:ice-creams-and-sorbets", "en:ice-creams", "en:ice-cream-tubs"
    )),
    SNACKS("Snacks", "🍿", setOf(
        "en:snacks", "en:salty-snacks", "en:chips-and-fries", "en:crisps", "en:appetizers", "en:potato-crisps",
        "en:crackers", "en:corn-chips", "en:popcorn", "en:salted-snacks", "en:breadsticks", "en:crackers-appetizers"
    )),

    GRANOS("Granos", "🌾", setOf(
        "en:rices", "en:long-grain-rices", "en:aromatic-rices", "en:indica-rices", "en:puffed-cereal-cakes", "en:puffed-corn-cakes"
    )),
    
    BEBIDAS_ALCOHOLICAS("Bebidas alcohólicas", "🍷", setOf(
        "en:alcoholic-beverages", "en:wines", "en:beers", "en:hard-liquors", "en:distilled-beverages",
        "en:red-wines", "en:country-specific-beers", "en:wines-from-france"
    )),
    BEBIDAS_VEGETALES("Bebidas vegetales", "🧋", setOf(
        "en:plant-based-beverages", "en:plant-based-milk-alternatives", "en:dairy-substitutes", "en:milk-substitutes"
    )),
    CAFE_INFUSIONES("Café e Infusiones", "☕", setOf(
        "en:teas", "en:coffees", "en:hot-beverages", "en:herbal-teas", "en:instant-coffees", "en:coffee-capsules",
        "en:green-teas", "en:tea-bags"
    )),
    BEBIDAS("Bebidas", "🥤", setOf(
        "en:beverages", "en:beverages-and-beverages-preparations", "en:carbonated-drinks", "en:sodas", "en:waters",
        "en:juices-and-nectars", "en:fruit-juices", "en:energy-drinks", "en:colas", "en:iced-teas", "en:mineral-waters",
        "en:sweetened-beverages", "en:fruit-based-beverages", "en:dairy-drinks", "en:tea-based-beverages",
        "en:fermented-drinks", "en:artificially-sweetened-beverages", "en:spring-waters", "en:instant-beverages",
        "en:non-alcoholic-beverages", "en:fruit-nectars", "en:orange-juices", "en:apple-juices", "en:dehydrated-beverages",
        "en:squeezed-juices", "en:diet-beverages", "en:cereal-based-drinks", "en:carbonated-waters", "en:natural-mineral-waters"
    )),

    SALSAS("Salsas", "🫙", setOf(
        "en:sauces", "en:condiments", "en:tomato-sauces", "en:salad-dressings", "en:mayonnaises", "en:ketchup",
        "en:mustards", "en:pestos", "en:vinegars", "en:pasta-sauces", "en:barbecue-sauces", "en:meal-sauces", "en:cooking-helpers"
    )),
    UNTABLES("Untables", "🥫", setOf(
        "en:spreads", "en:sweet-spreads", "en:jams", "en:dips", "en:peanut-butters", "en:chocolate-spreads",
        "en:hummus", "en:plant-based-spreads", "en:salted-spreads", "en:berry-jams", "en:legume-butters",
        "en:nut-butters", "en:rillettes", "en:hazelnut-spreads", "en:strawberry-jams", "en:cocoa-and-hazelnuts-spreads"
    )),
    FIAMBRES("Fiambres", "🥓", setOf(
        "en:prepared-meats", "en:hams", "en:sausages", "en:cured-sausages", "en:salami", "en:cured-hams",
        "en:white-hams", "en:terrines", "en:french-sausages", "en:italian-meat-products", "en:spanish-meat-products",
        "en:pate", "en:dry-sausages", "en:serrano-ham", "en:charcuteries-cuites", "en:charcuteries-diverses"
    )),
    QUESOS("Quesos", "🧀", setOf(
        "en:cheeses", "en:cow-cheeses", "en:hard-cheeses", "en:soft-cheeses", "en:mozzarella", "en:french-cheeses",
        "en:italian-cheeses", "en:uncooked-pressed-cheeses", "en:cream-cheeses", "en:soft-cheeses-with-bloomy-rind",
        "en:sheeps-milk-cheeses", "en:goat-cheeses", "en:pasteurized-cheeses", "en:comte", "en:emmentaler",
        "en:cheeses-of-the-netherlands", "en:cheeses-from-the-united-kingdom", "en:grated-cheese", "en:cheeses-from-england"
    )),
    YOGURT("Yogurt", "🥛", setOf(
        "en:yogurts", "en:fermented-milk-products", "en:fruit-yogurts", "en:plain-yogurts", "en:greek-style-yogurts", "en:fermented-foods"
    )),
    LECHE("Leche", "🥛", setOf(
        "en:dairies", "en:milks", "en:whole-milks", "en:semi-skimmed-milks", "en:uht-milks", "en:milks-liquid-and-powder", "en:homogenized-milks"
    )),

    PANES("Panes", "🥖", setOf(
        "en:breads", "en:sliced-breads", "en:special-breads", "en:white-breads", "en:flatbreads"
    )),
    PASTA("Pasta", "🍝", setOf(
        "en:pastas", "en:dry-pastas", "en:stuffed-pastas", "en:durum-wheat-pasta", "en:spaghetti", "en:noodles", "en:ravioli"
    )),
    CEREALES("Cereales", "🥣", setOf(
        "en:cereals-and-their-products", "en:breakfast-cereals", "en:mueslis", "en:flakes", "en:rolled-oats",
        "en:cereal-grains", "en:cereal-flakes", "en:extruded-cereals", "en:cereals-with-fruits", "en:rolled-flakes",
        "en:cereal-bars", "en:cereals-and-potatoes"
    )),
    HARINAS("Harinas", "🌾", setOf(
        "en:flours", "en:cereal-flours", "en:wheat-flours"
    )),

    POLLO("Pollo", "🍗", setOf(
        "en:poultries", "en:chickens", "en:chicken-and-its-products", "en:chicken-breasts", "en:turkeys",
        "en:turkey-and-its-products", "en:cooked-poultries", "en:turkey-cutlets", "en:breaded-chicken", "en:chicken-preparations"
    )),
    CERDO("Cerdo", "🐖", setOf(
        "en:pork-and-its-products", "en:pork"
    )),
    RES("Res", "🥩", setOf(
        "en:meats", "en:beef-and-its-products", "en:beef", "en:meats-and-their-products", "en:meat-preparations"
    )),
    PESCADO("Pescado", "🐟", setOf(
        "en:fishes", "en:fatty-fishes", "en:tunas", "en:salmons", "en:sardines", "en:fish-fillets", "en:canned-fishes",
        "en:smoked-fishes", "en:fish-preparations", "en:canned-tunas", "en:smoked-salmons", "en:canned-sardines",
        "en:tunas-in-oil", "en:fishes-and-their-products"
    )),
    MARISCOS("Mariscos", "🦞", setOf(
        "en:seafood", "en:crustaceans", "en:mollusc", "en:shrimps", "en:frozen-seafood"
    )),

    FRUTAS("Frutas", "🍎", setOf(
        "en:fruits", "en:fresh-fruits", "en:berries", "en:tropical-fruits", "en:apple-compotes", "en:dates",
        "en:compotes", "en:fruits-based-foods", "en:dried-fruits"
    )),
    VERDURAS("Verduras", "🥦", setOf(
        "en:vegetables", "en:fresh-vegetables", "en:leaf-vegetables", "en:tomatoes", "en:fruit-and-vegetable-preserves",
        "en:canned-vegetables", "en:tomatoes-and-their-products", "en:pickles", "en:plant-based-pickles",
        "en:frozen-vegetables", "en:culinary-plants", "en:olives", "en:prepared-vegetables", "en:salads",
        "en:vegetable-rods", "en:mushrooms-and-their-products", "en:pickled-vegetables", "en:mushrooms",
        "en:green-olives", "en:vegetables-based-foods", "en:fruits-and-vegetables-based-foods"
    )),
    LEGUMBRES("Legumbres", "🫘", setOf(
        "en:legumes-and-their-products", "en:pulses", "en:lentils", "en:common-beans", "en:legumes",
        "en:legume-seeds", "en:canned-common-beans", "en:canned-legumes"
    )),
    FRUTOS_SECOS("Frutos secos", "🥜", setOf(
        "en:nuts", "en:almonds", "en:peanuts", "en:cashew-nuts", "en:shelled-nuts", "en:nuts-and-their-products"
    )),
    SEMILLAS("Semillas", "🌰", setOf(
        "en:seeds", "en:sunflower-seeds-and-their-products"
    )),
    
    ACEITES("Aceites", "🌻", setOf(
        "en:fats", "en:vegetable-oils", "en:olive-oils", "en:extra-virgin-olive-oils", "en:butters",
        "en:vegetable-fats", "en:virgin-olive-oils", "en:spreadable-fats", "en:dairy-spreads", "en:margarines",
        "en:animal-fats", "en:milkfat"
    )),
    ESPECIAS_HIERBAS("Especias y hierbas", "🌿", setOf(
        "en:spices", "en:herbs", "en:aromatic-plants", "en:salts"
    )),

    // ¡Aquí tienes tus huevos hipervitaminados!
    HUEVO("Huevo", "🥚", setOf(
        "en:eggs", "en:fresh-eggs", "en:chicken-eggs", "en:liquid-eggs", "en:egg-yolks", "en:egg-whites", "en:omelettes"
    )),
    OTROS("Otros", "🍽️", setOf(
        "en:groceries", "en:canned-foods", "en:frozen-foods", "en:farming-products", "en:dried-products"
    )),
    UNKNOWN("Otros/Desconocido", "❔")
}

// Esta función se queda por si el usuario hace una búsqueda escrita a mano 
// y no tenemos datos de Open Food Facts.
fun getFoodCategory(headline: String): FoodCategory {
    val h = removeAccents(headline.lowercase())
    
    // Como las offTags están en formato "en:algo", hacemos un pequeño fallback 
    // buscando palabras clave básicas si solo tenemos el título.
    return when {
        h.contains("tortita") || h.contains("maiz") -> FoodCategory.GRANOS
        h.contains("pan") -> FoodCategory.PANES
        h.contains("pollo") -> FoodCategory.POLLO
        h.contains("queso") -> FoodCategory.QUESOS
        h.contains("leche") -> FoodCategory.LECHE
        // ... puedes mantener tu lógica anterior aquí para las búsquedas de texto plano
        else -> FoodCategory.UNKNOWN
    }
}

fun getFoodCategoryFromTags(tags: List<String>?): FoodCategory {
    if (tags.isNullOrEmpty()) return FoodCategory.UNKNOWN

    // Iteramos al revés: del tag más específico (ej: en:puffed-corn-cakes) al más general.
    for (tag in tags.asReversed()) {
        val normalizedTag = tag.trim().lowercase()

        // Solo nos interesan los tags estandarizados en inglés
        if (!normalizedTag.startsWith("en:")) continue 

        // ¡Magia! Buscamos si el tag EXACTO existe en alguno de nuestros Sets
        val matchedCategory = FoodCategory.values().firstOrNull { category ->
            category.offTags.contains(normalizedTag)
        }
        
        if (matchedCategory != null && matchedCategory != FoodCategory.UNKNOWN) {
            return matchedCategory
        }
    }

    return FoodCategory.UNKNOWN
}

private fun removeAccents(s: String): String {
    return s
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('Á', 'A')
        .replace('É', 'E').replace('Í', 'I').replace('Ó', 'O')
        .replace('Ú', 'U').replace('ñ', 'n').replace('Ñ', 'N')
}

@Composable
fun FoodCategoryIcon(category: FoodCategory) {
    Text(text = category.emoji)
}