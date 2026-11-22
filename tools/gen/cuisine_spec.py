#!/usr/bin/env python3
"""The food expansion (overlay jars/04-foods, package com.heliosphere.foods.cuisine): every crop, seed, ingredient,
dish, cookware machine, hand tool and food buff, with the recipes, in one place. Everything else is generated from
this file: the runtime table assets/heliosphere/cuisine/cuisine.json that the mod registers from, the language
entries, the loot tables, blockstates and models, and the textures (from the 'look' notes).

    python3 tools/gen/cuisine_spec.py            validate and write cuisine.json
Item references: a bare id is an item of this expansion (heliosphere namespace); 'minecraft:x' and 'heliosphere:x'
are existing items (the latter from the other modules, e.g. heliosphere:starch, heliosphere:rock_salt).
"""
import json, os, sys

OUT = 'jars/04-foods/src/main/resources/assets/heliosphere/cuisine/cuisine.json'

# ---- food buffs of our own (MobEffects registered by the mod) ----
# id, Chinese, English, colour, what it does (the Java side implements it)
EFFECTS = [
    ('warmth', '御寒', 'Warmth', 0xFF8A3D, 'immune to the impact winter frost and to freezing in powder snow'),
    ('rad_shield', '抗辐射', 'Radiation Shield', 0x7CFF6A, 'radiation dose reaching the body cut to a quarter'),
    ('acid_shield', '耐酸', 'Acid Ward', 0xC8E04A, 'acid rain does not hurt'),
    ('nourished', '饱腹', 'Nourished', 0xE8B85A, 'hunger does not drop'),
]

# ---- crops: one seed each, four growth stages (age 0-3), planted on farmland or a hydroponic planter ----
# id, crop name zh/en, produce id, produce zh/en, raw food (nutrition, saturation modifier) or None,
# model ('crop' = four planes like wheat, 'cross' = two diagonal planes like a bush), grass-drop weight,
# look of the mature plant and of the produce item (for the textures)
CROPS = [
    ('rice', '水稻', 'Rice', 'rice', '大米', 'Rice', None, 'crop', 10,
     'tall slender green stalks with drooping golden grain heads', 'a small heap of white rice grains'),
    ('soybean', '大豆', 'Soybean', 'soybean', '大豆', 'Soybeans', None, 'cross', 10,
     'bushy plant with trifoliate leaves and hairy green-tan pods', 'three round pale-yellow beans'),
    ('corn', '玉米', 'Corn', 'corn', '玉米', 'Corn', (3, 0.5), 'crop', 10,
     'tall stalks with long leaves, a tassel on top and green-husked ears with yellow silk', 'a yellow corn cob half wrapped in green husk'),
    ('peanut', '花生', 'Peanut', 'peanut', '花生', 'Peanuts', (2, 0.4), 'cross', 8,
     'low clover-like plant, small yellow flowers', 'two tan wrinkled peanut shells'),
    ('sesame', '芝麻', 'Sesame', 'sesame', '芝麻', 'Sesame Seeds', None, 'crop', 6,
     'upright stalks with white bell flowers and green capsules along the stem', 'a little pile of cream and black sesame seeds'),
    ('tomato', '番茄', 'Tomato', 'tomato', '番茄', 'Tomato', (2, 0.4), 'cross', 10,
     'leafy vine bush with clusters of round red tomatoes', 'a round shiny red tomato with a green star of sepals'),
    ('chili', '辣椒', 'Chili Pepper', 'chili', '辣椒', 'Chili Pepper', (1, 0.2), 'cross', 9,
     'small bush with thin red chili peppers hanging down', 'a thin curved bright red chili with a green stem'),
    ('bell_pepper', '青椒', 'Bell Pepper', 'bell_pepper', '青椒', 'Bell Pepper', (2, 0.4), 'cross', 8,
     'bush with large glossy green bell peppers', 'a blocky glossy green bell pepper'),
    ('eggplant', '茄子', 'Eggplant', 'eggplant', '茄子', 'Eggplant', (2, 0.4), 'cross', 8,
     'bush with purple star flowers and long glossy purple fruit', 'a long glossy deep purple eggplant with a green cap'),
    ('cucumber', '黄瓜', 'Cucumber', 'cucumber', '黄瓜', 'Cucumber', (2, 0.3), 'cross', 8,
     'climbing vine with yellow flowers and long bumpy green cucumbers', 'a long dark green bumpy cucumber'),
    ('napa_cabbage', '大白菜', 'Napa Cabbage', 'napa_cabbage', '大白菜', 'Napa Cabbage', (2, 0.3), 'cross', 10,
     'a tall tight head of pale green-white crinkled leaves', 'an upright head of pale crinkled leaves, white at the base'),
    ('lettuce', '生菜', 'Lettuce', 'lettuce', '生菜', 'Lettuce', (1, 0.3), 'cross', 9,
     'a loose round rosette of frilly bright green leaves', 'a round frilly bright green head of lettuce'),
    ('spinach', '菠菜', 'Spinach', 'spinach', '菠菜', 'Spinach', (1, 0.3), 'cross', 8,
     'a low rosette of dark green spade-shaped leaves with red-pink roots', 'a bunch of dark green leaves with pink roots'),
    ('onion', '洋葱', 'Onion', 'onion', '洋葱', 'Onion', (1, 0.3), 'crop', 9,
     'tubular blue-green leaves above a swelling papery bulb', 'a round golden-brown onion with a dry tip'),
    ('garlic', '大蒜', 'Garlic', 'garlic', '大蒜', 'Garlic', None, 'crop', 8,
     'flat strap leaves above a white bulb', 'a white garlic bulb with clove lines'),
    ('ginger', '生姜', 'Ginger', 'ginger', '生姜', 'Ginger', None, 'crop', 7,
     'reed-like stems with narrow leaves', 'a knobbly beige ginger root'),
    ('scallion', '葱', 'Scallion', 'scallion', '葱', 'Scallion', None, 'crop', 9,
     'clumps of hollow upright green leaves', 'two long scallions, white below and green above'),
    ('radish', '白萝卜', 'Daikon Radish', 'radish', '白萝卜', 'Daikon Radish', (2, 0.3), 'cross', 8,
     'big leafy top, a thick white root showing at the soil', 'a long thick white root with green leaves'),
    ('sweet_potato', '红薯', 'Sweet Potato', 'sweet_potato', '红薯', 'Sweet Potato', (1, 0.3), 'cross', 9,
     'sprawling vine with heart-shaped leaves', 'a plump reddish-purple tuber'),
    ('taro', '芋头', 'Taro', 'taro', '芋头', 'Taro', None, 'cross', 6,
     'large heart-shaped elephant-ear leaves on tall stems', 'a hairy brown round corm with rings'),
    ('strawberry', '草莓', 'Strawberry', 'strawberry', '草莓', 'Strawberry', (2, 0.3), 'cross', 9,
     'low plant with trifoliate leaves, white flowers and red berries', 'a red strawberry with yellow seeds and a green top'),
    ('grape', '葡萄', 'Grape', 'grape', '葡萄', 'Grapes', (2, 0.3), 'crop', 7,
     'vine on a wooden stake with broad leaves and purple bunches', 'a bunch of purple grapes'),
    ('blueberry', '蓝莓', 'Blueberry', 'blueberry', '蓝莓', 'Blueberries', (1, 0.3), 'cross', 7,
     'small woody bush with dusty blue berries', 'a cluster of dusty dark blue berries'),
    ('pineapple', '菠萝', 'Pineapple', 'pineapple', '菠萝', 'Pineapple', (3, 0.4), 'cross', 5,
     'a rosette of long spiky leaves with a golden pineapple in the middle', 'a golden criss-crossed pineapple with a green crown'),
    ('tea', '茶树', 'Tea Bush', 'tea_leaves', '茶叶', 'Tea Leaves', None, 'cross', 6,
     'a rounded glossy dark green bush with fresh light-green tips', 'a few curled dark green tea leaves'),
    ('coffee_shrub', '咖啡树', 'Coffee Shrub', 'coffee_beans', '咖啡豆', 'Coffee Beans', None, 'cross', 5,
     'a shrub with glossy leaves and red coffee cherries along the branches', 'three brown roasted coffee beans with the centre crease'),
    ('sichuan_pepper', '花椒', 'Sichuan Pepper', 'sichuan_pepper', '花椒', 'Sichuan Pepper', None, 'cross', 5,
     'a thorny shrub with small leaves and clusters of red husked berries', 'a cluster of small split red-brown peppercorns'),
    ('mung_bean', '绿豆', 'Mung Bean', 'mung_bean', '绿豆', 'Mung Beans', None, 'cross', 7,
     'a bushy plant with long thin black-green pods', 'a little heap of small green beans'),
    ('barley', '大麦', 'Barley', 'barley', '大麦', 'Barley', None, 'crop', 7,
     'golden stalks with long-bearded grain heads', 'a bearded golden barley ear'),
    ('chinese_chive', '韭菜', 'Chinese Chive', 'chinese_chive', '韭菜', 'Chinese Chives', (1, 0.2), 'crop', 7,
     'dense clumps of flat dark green grass-like leaves', 'a bunch of flat dark green chives tied together'),
]

# ---- ingredients made in the kitchen (and eaten as they are, where noted) ----
# id, zh, en, food (nutrition, saturation modifier) or None, look
INGREDIENTS = [
    ('flour', '面粉', 'Flour', None, 'a small paper sack of white flour with a wheat ear printed on it'),
    ('dough', '面团', 'Dough', None, 'a soft round ball of pale dough with a dusting of flour'),
    ('noodles', '面条', 'Noodles', None, 'a coil of long pale yellow noodles'),
    ('table_salt', '食盐', 'Table Salt', None, 'a small white salt shaker with grey holes on top'),
    ('cooking_oil', '食用油', 'Cooking Oil', None, 'a glass bottle of golden yellow oil'),
    ('soy_sauce', '酱油', 'Soy Sauce', None, 'a small bottle of very dark brown sauce with a red cap'),
    ('vinegar', '醋', 'Vinegar', None, 'a small bottle of dark amber vinegar with a black cap'),
    ('tofu', '豆腐', 'Tofu', (3, 0.4), 'a smooth white cube of tofu'),
    ('cheese', '奶酪', 'Cheese', (3, 0.6), 'a yellow wedge of cheese with holes'),
    ('butter', '黄油', 'Butter', None, 'a pale yellow block of butter on a little paper'),
    ('kimchi', '泡菜', 'Kimchi', (3, 0.4), 'red-orange spicy pickled cabbage in a little bowl'),
    ('steamed_rice', '米饭', 'Steamed Rice', (6, 0.6), 'a bowl heaped with fluffy white rice'),
    ('tapioca_pearls', '珍珠粉圆', 'Tapioca Pearls', None, 'a small heap of glossy black pearls'),
]

# ---- dishes: id, zh, en, nutrition, saturation modifier, buffs [(effect, seconds, level)], container ('bowl',
# 'bottle' or None), look. Effects: vanilla ids without namespace, ours as heliosphere:<id>. ----
DISHES = [
    # staples and dim sum
    ('mantou', '馒头', 'Mantou', 5, 0.6, [], None, 'a plain round white steamed bun'),
    ('baozi', '包子', 'Baozi', 8, 0.8, [('heliosphere:nourished', 60, 0)], None, 'a white steamed bun with pleated twist on top'),
    ('xiaolongbao', '小笼包', 'Xiaolongbao', 4, 0.7, [('regeneration', 10, 0)], None, 'three small pleated soup dumplings in a bamboo steamer'),
    ('dumplings', '饺子', 'Dumplings', 6, 0.7, [('heliosphere:warmth', 180, 0)], None, 'four crescent dumplings on a plate'),
    ('ramen', '拉面', 'Ramen', 10, 0.8, [('heliosphere:nourished', 120, 0), ('regeneration', 20, 0)], 'bowl', 'a bowl of noodles in broth with beef slices, half an egg and scallion'),
    ('zhajiang_noodles', '炸酱面', 'Zhajiang Noodles', 10, 0.8, [('haste', 180, 0)], 'bowl', 'noodles topped with dark brown bean sauce and cucumber strips'),
    ('egg_fried_rice', '蛋炒饭', 'Egg Fried Rice', 8, 0.7, [('speed', 120, 0)], 'bowl', 'golden fried rice with egg bits and green scallion'),
    ('yangzhou_fried_rice', '扬州炒饭', 'Yangzhou Fried Rice', 10, 0.8, [('speed', 180, 0), ('haste', 180, 0)], 'bowl', 'fried rice with egg, pink pork, yellow corn and green scallion'),
    ('zongzi', '粽子', 'Zongzi', 8, 0.8, [('heliosphere:nourished', 90, 0)], None, 'a pyramid of sticky rice wrapped in green bamboo leaves tied with string'),
    ('tangyuan', '汤圆', 'Tangyuan', 4, 0.5, [('regeneration', 15, 0)], 'bowl', 'white glutinous balls in clear sweet soup'),
    ('mooncake', '月饼', 'Mooncake', 7, 0.7, [('luck', 300, 0)], None, 'a round golden-brown mooncake with a stamped pattern'),
    ('spring_rolls', '春卷', 'Spring Rolls', 6, 0.6, [('speed', 60, 0)], None, 'three crisp golden fried rolls'),
    ('youtiao', '油条', 'Youtiao', 5, 0.5, [], None, 'a long golden fried dough stick'),
    ('jianbing', '煎饼果子', 'Jianbing', 10, 0.8, [('speed', 180, 0), ('jump_boost', 180, 0)], None, 'a folded crepe with egg, scallion and a crisp cracker inside'),
    ('scallion_pancake', '葱油饼', 'Scallion Pancake', 6, 0.6, [('haste', 60, 0)], None, 'a round flaky pancake flecked with green scallion'),
    ('congee', '皮蛋瘦肉粥', 'Pork Congee', 9, 0.8, [('regeneration', 20, 1), ('heliosphere:rad_shield', 120, 0)], 'bowl', 'a bowl of creamy white rice porridge with pork shreds and dark egg pieces'),
    ('pumpkin_congee', '南瓜粥', 'Pumpkin Congee', 7, 0.7, [('regeneration', 15, 0)], 'bowl', 'a bowl of smooth orange pumpkin porridge'),
    # Sichuan and home cooking
    ('mapo_tofu', '麻婆豆腐', 'Mapo Tofu', 8, 0.8, [('heliosphere:warmth', 300, 0), ('fire_resistance', 120, 0)], 'bowl', 'white tofu cubes in glossy red chili sauce with minced pork'),
    ('kung_pao_chicken', '宫保鸡丁', 'Kung Pao Chicken', 9, 0.8, [('strength', 180, 0)], 'bowl', 'diced chicken with peanuts and dried red chilies'),
    ('yuxiang_pork', '鱼香肉丝', 'Yuxiang Pork', 9, 0.8, [('speed', 180, 0)], 'bowl', 'shredded pork with carrot strips in a red-brown sauce'),
    ('braised_pork', '红烧肉', 'Braised Pork Belly', 12, 0.9, [('absorption', 240, 1)], 'bowl', 'glossy dark red cubes of pork belly'),
    ('sweet_sour_ribs', '糖醋排骨', 'Sweet and Sour Ribs', 10, 0.8, [('haste', 180, 0)], 'bowl', 'glazed shiny red-brown ribs with sesame'),
    ('tomato_egg', '番茄炒蛋', 'Tomato and Egg', 7, 0.7, [('regeneration', 30, 0)], 'bowl', 'red tomato chunks with fluffy yellow scrambled egg'),
    ('pepper_pork', '青椒肉丝', 'Pepper Pork', 8, 0.7, [('haste', 120, 0)], 'bowl', 'green pepper strips with shredded pork'),
    ('di_san_xian', '地三鲜', 'Di San Xian', 7, 0.7, [('resistance', 60, 0)], 'bowl', 'potato, purple eggplant and green pepper in brown sauce'),
    ('garlic_eggplant', '鱼香茄子', 'Garlic Eggplant', 6, 0.6, [('night_vision', 120, 0)], 'bowl', 'soft purple eggplant strips in red garlic sauce'),
    ('stir_fried_greens', '蒜蓉青菜', 'Garlic Greens', 4, 0.5, [('health_boost', 180, 0)], 'bowl', 'bright green leafy greens with garlic bits'),
    ('hot_sour_soup', '酸辣汤', 'Hot and Sour Soup', 7, 0.7, [('heliosphere:warmth', 240, 0)], 'bowl', 'a dark brown soup with tofu strips and egg ribbons'),
    ('hot_pot', '火锅', 'Hot Pot', 14, 1.0, [('heliosphere:warmth', 480, 0), ('strength', 240, 0), ('regeneration', 30, 0)], 'bowl', 'a pot split into red chili broth and white broth with meat and vegetables'),
    ('malatang', '麻辣烫', 'Malatang', 9, 0.8, [('heliosphere:warmth', 240, 0), ('speed', 60, 0)], 'bowl', 'a bowl of red spicy broth with skewered vegetables, tofu and noodles'),
    ('pickled_fish', '酸菜鱼', 'Pickled Cabbage Fish', 10, 0.8, [('water_breathing', 300, 0)], 'bowl', 'white fish slices with pickled greens and red chili in yellow broth'),
    ('roast_duck', '北京烤鸭', 'Roast Duck', 14, 1.0, [('regeneration', 30, 1), ('absorption', 300, 1)], None, 'a glossy mahogany roast bird on a plate with pancakes'),
    ('smashed_cucumber', '拍黄瓜', 'Smashed Cucumber', 4, 0.5, [('fire_resistance', 180, 0)], 'bowl', 'smashed green cucumber pieces with garlic and red oil'),
    ('beef_tomato_stew', '番茄牛腩', 'Beef Tomato Stew', 12, 0.9, [('resistance', 180, 0)], 'bowl', 'red tomato stew with brown beef chunks and potato'),
    ('curry_rice', '咖喱饭', 'Curry Rice', 12, 0.9, [('heliosphere:warmth', 240, 0), ('strength', 120, 0)], 'bowl', 'white rice beside thick golden curry with carrot and potato'),
    # soups, drinks and sweets
    ('mung_bean_soup', '绿豆汤', 'Mung Bean Soup', 5, 0.5, [('heliosphere:rad_shield', 300, 0)], 'bowl', 'a bowl of pale green soup with soft green beans'),
    ('ginger_tea', '姜茶', 'Ginger Tea', 2, 0.3, [('heliosphere:warmth', 300, 0)], 'bottle', 'a bottle of warm amber ginger tea'),
    ('green_tea', '绿茶', 'Green Tea', 2, 0.3, [('haste', 180, 0), ('heliosphere:rad_shield', 120, 0)], 'bottle', 'a bottle of clear yellow-green tea'),
    ('bubble_tea', '珍珠奶茶', 'Bubble Tea', 5, 0.5, [('speed', 180, 0), ('jump_boost', 60, 0)], 'bottle', 'a tall cup of beige milk tea with black pearls at the bottom and a straw'),
    ('soy_milk', '豆浆', 'Soy Milk', 4, 0.5, [('regeneration', 10, 0)], 'bottle', 'a bottle of creamy white soy milk'),
    ('coffee', '咖啡', 'Coffee', 2, 0.3, [('haste', 180, 1), ('speed', 180, 0)], 'bottle', 'a cup of black coffee with steam'),
    ('grape_juice', '葡萄汁', 'Grape Juice', 4, 0.4, [('speed', 120, 0)], 'bottle', 'a bottle of deep purple juice'),
    ('pineapple_juice', '菠萝汁', 'Pineapple Juice', 4, 0.4, [('haste', 120, 0)], 'bottle', 'a bottle of bright yellow juice'),
    ('strawberry_milkshake', '草莓奶昔', 'Strawberry Milkshake', 5, 0.5, [('absorption', 180, 0)], 'bottle', 'a pink milkshake with cream on top'),
    ('yogurt', '酸奶', 'Yogurt', 4, 0.5, [('regeneration', 20, 0)], 'bottle', 'a small bottle of thick white yogurt'),
    ('sesame_soup', '芝麻糊', 'Black Sesame Soup', 6, 0.6, [('night_vision', 180, 0)], 'bowl', 'a bowl of smooth dark grey sesame paste'),
    ('tanghulu', '糖葫芦', 'Tanghulu', 4, 0.4, [('luck', 180, 0)], None, 'a stick of red berries in shiny clear candy'),
    ('roasted_sweet_potato', '烤红薯', 'Roasted Sweet Potato', 6, 0.6, [('heliosphere:warmth', 120, 0)], None, 'a split roasted sweet potato with orange flesh and dark skin'),
    ('popcorn', '爆米花', 'Popcorn', 2, 0.2, [], None, 'a striped red and white box of popcorn'),
    ('corn_chowder', '玉米浓汤', 'Corn Chowder', 8, 0.7, [('heliosphere:warmth', 120, 0)], 'bowl', 'a bowl of creamy yellow soup with corn kernels'),
    ('tomato_soup', '番茄汤', 'Tomato Soup', 7, 0.6, [('regeneration', 20, 0)], 'bowl', 'a bowl of smooth red tomato soup'),
    ('strawberry_cake', '草莓蛋糕', 'Strawberry Cake', 4, 0.5, [('regeneration', 15, 0)], None, 'a slice of white cream cake with a strawberry on top'),
    ('blueberry_pie', '蓝莓派', 'Blueberry Pie', 8, 0.7, [('night_vision', 240, 0)], None, 'a golden lattice pie with dark blue filling'),
    ('soda_crackers', '苏打饼干', 'Soda Crackers', 3, 0.4, [('heliosphere:acid_shield', 240, 0)], None, 'square pale crackers with docking holes'),
    # western
    ('pizza', '披萨', 'Pizza', 14, 0.9, [('strength', 180, 0), ('regeneration', 20, 0)], None, 'a round pizza with red sauce, melted cheese and green pepper'),
    ('hamburger', '汉堡', 'Hamburger', 13, 0.9, [('absorption', 180, 0)], None, 'a burger with sesame bun, beef patty, cheese, lettuce and tomato'),
    ('sandwich', '三明治', 'Sandwich', 7, 0.7, [('speed', 60, 0)], None, 'a triangle sandwich with chicken, lettuce and tomato'),
    ('salad', '蔬菜沙拉', 'Garden Salad', 6, 0.6, [('health_boost', 240, 0)], 'bowl', 'a bowl of lettuce, tomato and cucumber'),
    ('fries', '薯条', 'Fries', 3, 0.4, [], None, 'a red paper cup of golden fries'),
    ('pasta', '番茄意面', 'Tomato Pasta', 10, 0.8, [('strength', 120, 0)], 'bowl', 'pasta in red tomato sauce with cheese on top'),
    ('steak', '黄油牛排', 'Butter Steak', 10, 0.9, [('strength', 120, 1)], None, 'a seared brown steak with a pat of butter and grill marks'),
]

# ---- cookware: machines (MachineType processors with the standard machine screen) ----
# id, zh, en, inputs, outputs, heat (needs a heat source below), power (FE/t, 0 = none), start button (choose the
# recipe in the list), look (for the block model and textures)
MACHINES = [
    ('wok', '炒锅', 'Wok', 4, 1, True, 0, True, 'a black round-bottomed wok with a long wooden handle, sitting on the block below (a low model, not a full cube)'),
    ('cooking_pot', '汤锅', 'Cooking Pot', 6, 1, True, 0, True, 'a steel stock pot with two side handles and a lid, half a block tall'),
    ('steamer', '蒸笼', 'Bamboo Steamer', 3, 1, True, 0, True, 'two stacked round bamboo steamer baskets with a woven lid, about 3/4 block tall'),
    ('oven', '电烤箱', 'Electric Oven', 6, 1, False, 30, True, 'a stainless steel oven with a dark glass door, a handle and two dials; glowing orange inside when lit'),
    ('grain_mill', '磨粉机', 'Grain Mill', 1, 2, False, 20, False, 'a cream-coloured mill with a hopper on top and a grinding chamber; spinning stones visible when lit'),
    ('juicer', '榨汁机', 'Juicer', 4, 2, False, 20, True, 'a white juicer with a clear jug showing coloured juice when lit'),
    ('fermenter', '发酵罐', 'Fermentation Crock', 4, 2, False, 0, True, 'a glazed brown ceramic crock with a water-sealed lid, wooden base'),
]
# hand tools used in the crafting grid: they stay in the grid and lose one durability point per use
TOOLS = [
    ('kitchen_knife', '菜刀', 'Kitchen Knife', 256, 'a broad Chinese cleaver with a steel blade and a wooden handle'),
    ('rolling_pin', '擀面杖', 'Rolling Pin', 256, 'a pale wooden rolling pin'),
    ('mortar_and_pestle', '研钵', 'Mortar and Pestle', 256, 'a grey stone mortar with a pestle in it'),
]
# blocks besides the machines
BLOCKS = [
    ('induction_stove', '电磁炉', 'Induction Stove', 'a black glass-topped induction hob on a steel base; the cooking ring glows red when powered. Counts as a heat source for the wok, pot and steamer placed on top. 12 FE/t while something cooks on it'),
    ('hydroponic_planter', '水培种植槽', 'Hydroponic Planter', 'a steel tray with nutrient solution and grow lights around the rim; any crop of this expansion (and wheat, carrots, potatoes, beetroot) grows on it without farmland or water, faster while powered (8 FE/t); gives light 12'),
]
# other items
OTHER = [
    ('seed_packet', '种子包', 'Seed Packet', 'a paper seed packet with a green sprout drawn on it; right-click: three random crop seeds'),
]

# ---- recipes: machine, inputs [(item, count)], output (item, count), seconds ----
# 'crafting' = shapeless recipe in the crafting table (tools stay in the grid)
R = []


def r(machine, inputs, output, count=1, seconds=0):
    R.append((machine, inputs, (output, count), seconds))


# preparation
r('crafting', [('mortar_and_pestle', 1), ('minecraft:wheat', 2)], 'flour', 1)
r('grain_mill', [('minecraft:wheat', 1)], 'flour', 1, 5)
r('grain_mill', [('barley', 1)], 'flour', 1, 5)
r('grain_mill', [('minecraft:potato', 2)], 'heliosphere:starch', 1, 8)
r('grain_mill', [('corn', 2)], 'heliosphere:starch', 1, 8)
r('grain_mill', [('sweet_potato', 2)], 'heliosphere:starch', 2, 8)
r('grain_mill', [('taro', 2)], 'heliosphere:starch', 1, 8)
r('grain_mill', [('heliosphere:rock_salt', 1)], 'table_salt', 3, 6)
r('crafting', [('mortar_and_pestle', 1), ('heliosphere:rock_salt', 1)], 'table_salt', 2)
r('crafting', [('flour', 2), ('minecraft:water_bucket', 1)], 'dough', 3)
r('crafting', [('dough', 1), ('rolling_pin', 1), ('kitchen_knife', 1)], 'noodles', 2)
r('juicer', [('peanut', 3), ('minecraft:glass_bottle', 1)], 'cooking_oil', 1, 8)
r('juicer', [('sesame', 4), ('minecraft:glass_bottle', 1)], 'cooking_oil', 1, 8)
r('juicer', [('soybean', 4), ('minecraft:glass_bottle', 1)], 'cooking_oil', 1, 8)
r('fermenter', [('soybean', 3), ('minecraft:wheat', 1), ('table_salt', 1), ('minecraft:glass_bottle', 1)], 'soy_sauce', 2, 90)
r('fermenter', [('rice', 3), ('minecraft:glass_bottle', 1)], 'vinegar', 2, 90)
r('fermenter', [('minecraft:milk_bucket', 1), ('table_salt', 1)], 'cheese', 3, 120)
r('fermenter', [('napa_cabbage', 2), ('chili', 1), ('garlic', 1), ('table_salt', 1)], 'kimchi', 3, 90)
r('fermenter', [('minecraft:milk_bucket', 1), ('minecraft:glass_bottle', 3)], 'yogurt', 3, 60)
r('juicer', [('minecraft:milk_bucket', 1)], 'butter', 2, 10)
r('cooking_pot', [('soybean', 3)], 'tofu', 2, 10)
r('cooking_pot', [('rice', 2), ('minecraft:bowl', 1)], 'steamed_rice', 1, 10)
r('cooking_pot', [('heliosphere:starch', 2), ('minecraft:sugar', 1)], 'tapioca_pearls', 3, 8)
# staples and dim sum
r('steamer', [('dough', 2)], 'mantou', 3, 12)
r('steamer', [('dough', 1), ('minecraft:porkchop', 1), ('scallion', 1)], 'baozi', 3, 15)
r('steamer', [('dough', 1), ('minecraft:porkchop', 1), ('ginger', 1)], 'xiaolongbao', 4, 15)
r('cooking_pot', [('dough', 1), ('minecraft:porkchop', 1), ('napa_cabbage', 1)], 'dumplings', 4, 12)
r('cooking_pot', [('dough', 1), ('minecraft:porkchop', 1), ('chinese_chive', 1)], 'dumplings', 4, 12)
r('cooking_pot', [('noodles', 1), ('minecraft:cooked_beef', 1), ('minecraft:egg', 1), ('scallion', 1), ('minecraft:bowl', 1)], 'ramen', 1, 15)
r('wok', [('noodles', 1), ('minecraft:porkchop', 1), ('soy_sauce', 1), ('cucumber', 1)], 'zhajiang_noodles', 1, 12)
r('wok', [('steamed_rice', 1), ('minecraft:egg', 1), ('scallion', 1)], 'egg_fried_rice', 1, 10)
r('wok', [('steamed_rice', 1), ('minecraft:egg', 1), ('minecraft:porkchop', 1), ('corn', 1)], 'yangzhou_fried_rice', 1, 12)
r('cooking_pot', [('rice', 2), ('minecraft:porkchop', 1), ('minecraft:bamboo', 2)], 'zongzi', 2, 20)
r('cooking_pot', [('heliosphere:starch', 2), ('sesame', 1), ('minecraft:sugar', 1), ('minecraft:bowl', 1)], 'tangyuan', 1, 10)
r('oven', [('flour', 1), ('minecraft:egg', 1), ('sesame', 1), ('minecraft:sugar', 1)], 'mooncake', 2, 15)
r('wok', [('dough', 1), ('napa_cabbage', 1), ('minecraft:carrot', 1), ('cooking_oil', 1)], 'spring_rolls', 3, 10)
r('wok', [('dough', 1), ('cooking_oil', 1)], 'youtiao', 3, 8)
r('wok', [('flour', 1), ('minecraft:egg', 1), ('scallion', 1), ('youtiao', 1)], 'jianbing', 1, 10)
r('wok', [('dough', 1), ('scallion', 1), ('cooking_oil', 1)], 'scallion_pancake', 2, 10)
r('cooking_pot', [('rice', 1), ('minecraft:porkchop', 1), ('minecraft:egg', 1), ('scallion', 1), ('minecraft:bowl', 1)], 'congee', 1, 20)
r('cooking_pot', [('minecraft:pumpkin', 1), ('rice', 1), ('minecraft:bowl', 2)], 'pumpkin_congee', 2, 15)
# Sichuan and home cooking
r('wok', [('tofu', 1), ('minecraft:porkchop', 1), ('chili', 1), ('sichuan_pepper', 1)], 'mapo_tofu', 2, 12)
r('wok', [('minecraft:chicken', 1), ('peanut', 1), ('chili', 1), ('scallion', 1)], 'kung_pao_chicken', 2, 12)
r('wok', [('minecraft:porkchop', 1), ('minecraft:carrot', 1), ('chili', 1), ('vinegar', 1)], 'yuxiang_pork', 2, 12)
r('cooking_pot', [('minecraft:porkchop', 2), ('soy_sauce', 1), ('minecraft:sugar', 1), ('ginger', 1), ('minecraft:bowl', 2)], 'braised_pork', 2, 30)
r('wok', [('minecraft:porkchop', 2), ('vinegar', 1), ('minecraft:sugar', 1)], 'sweet_sour_ribs', 2, 15)
r('wok', [('tomato', 2), ('minecraft:egg', 2)], 'tomato_egg', 2, 8)
r('wok', [('bell_pepper', 1), ('minecraft:porkchop', 1), ('soy_sauce', 1)], 'pepper_pork', 2, 10)
r('wok', [('minecraft:potato', 1), ('eggplant', 1), ('bell_pepper', 1), ('cooking_oil', 1)], 'di_san_xian', 2, 12)
r('wok', [('eggplant', 2), ('garlic', 1), ('chili', 1)], 'garlic_eggplant', 2, 10)
r('wok', [('spinach', 2), ('garlic', 1)], 'stir_fried_greens', 2, 6)
r('wok', [('lettuce', 2), ('garlic', 1)], 'stir_fried_greens', 2, 6)
r('cooking_pot', [('tofu', 1), ('minecraft:egg', 1), ('vinegar', 1), ('chili', 1), ('minecraft:bowl', 2)], 'hot_sour_soup', 2, 12)
r('cooking_pot', [('minecraft:beef', 1), ('minecraft:mutton', 1), ('napa_cabbage', 1), ('tofu', 1), ('chili', 1), ('sichuan_pepper', 1)], 'hot_pot', 2, 30)
r('cooking_pot', [('spinach', 1), ('tofu', 1), ('noodles', 1), ('chili', 1), ('minecraft:bowl', 1)], 'malatang', 1, 15)
r('cooking_pot', [('minecraft:cod', 1), ('kimchi', 1), ('chili', 1), ('minecraft:bowl', 1)], 'pickled_fish', 1, 15)
r('cooking_pot', [('minecraft:salmon', 1), ('kimchi', 1), ('chili', 1), ('minecraft:bowl', 1)], 'pickled_fish', 1, 15)
r('oven', [('minecraft:chicken', 2), ('scallion', 1), ('cucumber', 1), ('flour', 1)], 'roast_duck', 1, 40)
r('crafting', [('cucumber', 2), ('garlic', 1), ('vinegar', 1), ('kitchen_knife', 1), ('minecraft:bowl', 1)], 'smashed_cucumber', 1)
r('cooking_pot', [('minecraft:beef', 2), ('tomato', 2), ('minecraft:potato', 1), ('minecraft:bowl', 2)], 'beef_tomato_stew', 2, 30)
r('cooking_pot', [('steamed_rice', 2), ('minecraft:carrot', 1), ('minecraft:potato', 1), ('onion', 1), ('minecraft:chicken', 1)], 'curry_rice', 2, 20)
# soups, drinks and sweets
r('cooking_pot', [('mung_bean', 2), ('minecraft:sugar', 1), ('minecraft:bowl', 1)], 'mung_bean_soup', 1, 12)
r('cooking_pot', [('ginger', 1), ('minecraft:sugar', 1), ('minecraft:glass_bottle', 1)], 'ginger_tea', 1, 8)
r('cooking_pot', [('tea_leaves', 1), ('minecraft:glass_bottle', 1)], 'green_tea', 1, 6)
r('juicer', [('tea_leaves', 1), ('minecraft:milk_bucket', 1), ('tapioca_pearls', 1), ('minecraft:glass_bottle', 3)], 'bubble_tea', 3, 10)
r('juicer', [('soybean', 2), ('minecraft:glass_bottle', 1)], 'soy_milk', 1, 6)
r('cooking_pot', [('coffee_beans', 2), ('minecraft:glass_bottle', 1)], 'coffee', 1, 8)
r('juicer', [('grape', 3), ('minecraft:glass_bottle', 1)], 'grape_juice', 1, 6)
r('juicer', [('pineapple', 1), ('minecraft:glass_bottle', 2)], 'pineapple_juice', 2, 6)
r('juicer', [('strawberry', 2), ('minecraft:milk_bucket', 1), ('minecraft:sugar', 1), ('minecraft:glass_bottle', 3)], 'strawberry_milkshake', 3, 8)
r('cooking_pot', [('sesame', 3), ('minecraft:sugar', 1), ('minecraft:bowl', 1)], 'sesame_soup', 1, 10)
r('crafting', [('strawberry', 3), ('minecraft:sugar', 1), ('minecraft:stick', 1)], 'tanghulu', 1)
r('crafting', [('minecraft:sweet_berries', 3), ('minecraft:sugar', 1), ('minecraft:stick', 1)], 'tanghulu', 1)
r('oven', [('sweet_potato', 1)], 'roasted_sweet_potato', 1, 10)
r('cooking_pot', [('corn', 1), ('cooking_oil', 1), ('minecraft:sugar', 1)], 'popcorn', 4, 6)
r('cooking_pot', [('corn', 2), ('minecraft:milk_bucket', 1), ('butter', 1), ('minecraft:bowl', 2)], 'corn_chowder', 2, 15)
r('cooking_pot', [('tomato', 3), ('onion', 1), ('minecraft:bowl', 1)], 'tomato_soup', 1, 12)
r('oven', [('flour', 2), ('minecraft:egg', 2), ('strawberry', 3), ('minecraft:milk_bucket', 1), ('minecraft:sugar', 1)], 'strawberry_cake', 6, 30)
r('oven', [('flour', 1), ('blueberry', 3), ('minecraft:sugar', 1), ('minecraft:egg', 1)], 'blueberry_pie', 2, 20)
r('oven', [('flour', 2), ('heliosphere:sodium_carbonate', 1), ('table_salt', 1)], 'soda_crackers', 6, 15)
# western
r('oven', [('dough', 1), ('tomato', 1), ('cheese', 1), ('bell_pepper', 1)], 'pizza', 1, 25)
r('crafting', [('minecraft:bread', 1), ('minecraft:cooked_beef', 1), ('lettuce', 1), ('tomato', 1), ('cheese', 1)], 'hamburger', 1)
r('crafting', [('minecraft:bread', 1), ('minecraft:cooked_chicken', 1), ('lettuce', 1), ('tomato', 1), ('kitchen_knife', 1)], 'sandwich', 2)
r('crafting', [('lettuce', 1), ('tomato', 1), ('cucumber', 1), ('minecraft:bowl', 1)], 'salad', 1)
r('wok', [('minecraft:potato', 2), ('cooking_oil', 1), ('table_salt', 1)], 'fries', 3, 8)
r('cooking_pot', [('noodles', 1), ('tomato', 1), ('cheese', 1), ('garlic', 1), ('minecraft:bowl', 1)], 'pasta', 1, 12)
r('wok', [('minecraft:beef', 1), ('butter', 1), ('garlic', 1)], 'steak', 1, 10)

# the cookware itself (crafting-table shapes are written by the data generator; listed here as ingredients)
CRAFT = {
    'kitchen_knife': [('minecraft:iron_ingot', 1), ('minecraft:iron_nugget', 2), ('minecraft:stick', 1)],   # not 2 ingots + stick: the iron sword
    'rolling_pin': [('minecraft:stick', 2), ('#minecraft:wooden_slabs', 1)],                                # not planks + 2 sticks: the shovel
    'mortar_and_pestle': [('minecraft:stone', 3), ('minecraft:stick', 1)],
    'wok': [('minecraft:iron_ingot', 5), ('minecraft:stick', 1)],
    'cooking_pot': [('minecraft:iron_ingot', 6), ('minecraft:bucket', 1)],
    'steamer': [('minecraft:bamboo', 8), ('minecraft:string', 1)],
    'fermenter': [('minecraft:terracotta', 4), ('minecraft:oak_slab', 2), ('minecraft:clay_ball', 2)],
    'seed_packet': [('minecraft:paper', 1), ('minecraft:wheat_seeds', 1), ('minecraft:beetroot_seeds', 1), ('minecraft:bone_meal', 1)],
}
# made at the assembly station (heliosphere:assembly_station), like the other electric machines
ASSEMBLY = {
    'oven': [('heliosphere:steel_plate', 4), ('heliosphere:copper_wire', 4), ('heliosphere:circuit_board', 1), ('minecraft:glass', 1), ('minecraft:furnace', 1)],
    'grain_mill': [('heliosphere:steel_plate', 3), ('heliosphere:electric_motor', 1), ('minecraft:grindstone', 1), ('minecraft:hopper', 1)],
    'juicer': [('heliosphere:steel_plate', 2), ('heliosphere:electric_motor', 1), ('minecraft:glass', 3), ('heliosphere:circuit_board', 1)],
    'induction_stove': [('heliosphere:steel_plate', 3), ('heliosphere:copper_wire', 6), ('heliosphere:circuit_board', 1), ('minecraft:black_stained_glass', 1)],
    'hydroponic_planter': [('heliosphere:steel_plate', 3), ('heliosphere:circuit_board', 1), ('minecraft:glass', 2), ('minecraft:glowstone_dust', 4), ('minecraft:bone_meal', 4)],
}


def ours():
    ids = {c[0] + '_seeds' for c in CROPS} | {c[3] for c in CROPS} | {c[0] for c in CROPS}
    ids |= {i[0] for i in INGREDIENTS} | {d[0] for d in DISHES} | {m[0] for m in MACHINES} | {t[0] for t in TOOLS}
    ids |= {b[0] for b in BLOCKS} | {o[0] for o in OTHER}
    return ids


def validate():
    items = {i[0] for i in INGREDIENTS} | {d[0] for d in DISHES} | {c[3] for c in CROPS} | {c[0] + '_seeds' for c in CROPS} \
        | {t[0] for t in TOOLS} | {o[0] for o in OTHER}
    item_list = [i[0] for i in INGREDIENTS] + [d[0] for d in DISHES] + [c[3] for c in CROPS] + [c[0] + '_seeds' for c in CROPS] \
        + [t[0] for t in TOOLS] + [o[0] for o in OTHER] + [m[0] for m in MACHINES] + [b[0] for b in BLOCKS]
    block_list = [c[0] for c in CROPS] + [m[0] for m in MACHINES] + [b[0] for b in BLOCKS]
    for lst in (item_list, block_list):
        dup = {n for n in lst if lst.count(n) > 1}
        assert not dup, dup
    machines = {m[0] for m in MACHINES} | {'crafting'}
    made = set()
    seen = {}
    for machine, inputs, (out, n), sec in R:
        assert machine in machines, machine
        for it, c in inputs:
            assert ':' in it or it in items, (it, out)
        assert ':' in out or out in items, out
        made.add(out)
        key = (machine, tuple(sorted(inputs)))
        assert key not in seen, ('identical inputs', key, out, seen.get(key))
        seen[key] = out
    for d in DISHES:
        assert d[0] in made, ('no recipe for', d[0])
        for e, s, a in d[5]:
            assert e.startswith('heliosphere:') and e.split(':')[1] in {x[0] for x in EFFECTS} or ':' not in e, e
    for i in INGREDIENTS:
        assert i[0] in made, ('no recipe for', i[0])
    return True


def export():
    validate()
    def ref(x):
        return x if ':' in x else 'heliosphere:' + x
    data = {
        'effects': [{'id': e[0], 'zh': e[1], 'en': e[2], 'color': e[3]} for e in EFFECTS],
        'crops': [{'id': c[0], 'zh': c[1], 'en': c[2], 'seed': c[0] + '_seeds', 'produce': c[3], 'produceZh': c[4], 'produceEn': c[5],
                   'raw': None if c[6] is None else {'n': c[6][0], 's': c[6][1]}, 'model': c[7], 'grass': c[8]} for c in CROPS],
        'ingredients': [{'id': i[0], 'zh': i[1], 'en': i[2], 'food': None if i[3] is None else {'n': i[3][0], 's': i[3][1]}} for i in INGREDIENTS],
        'dishes': [{'id': d[0], 'zh': d[1], 'en': d[2], 'n': d[3], 's': d[4],
                    'effects': [{'effect': e if ':' in e else 'minecraft:' + e, 'seconds': s, 'level': a} for e, s, a in d[5]],
                    'container': d[6]} for d in DISHES],
        'machines': [{'id': m[0], 'zh': m[1], 'en': m[2], 'inputs': m[3], 'outputs': m[4], 'heat': m[5], 'power': m[6], 'gated': m[7]} for m in MACHINES],
        'tools': [{'id': t[0], 'zh': t[1], 'en': t[2], 'durability': t[3]} for t in TOOLS],
        'blocks': [{'id': b[0], 'zh': b[1], 'en': b[2]} for b in BLOCKS],
        'other': [{'id': o[0], 'zh': o[1], 'en': o[2]} for o in OTHER],
        'recipes': [{'machine': m, 'inputs': [[ref(i), c] for i, c in ins], 'output': [ref(o), n], 'seconds': s} for m, ins, (o, n), s in R if m != 'crafting'],
        'assembly': [{'output': ref(k), 'inputs': [[ref(i), c] for i, c in v]} for k, v in ASSEMBLY.items()],
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(f'{len(CROPS)} crops, {len(INGREDIENTS)} ingredients, {len(DISHES)} dishes, {len(MACHINES)} machines, {len(TOOLS)} tools, '
          f'{len(R)} recipes ({sum(1 for x in R if x[0] == "crafting")} crafting) -> {OUT}')


if __name__ == '__main__':
    export()
