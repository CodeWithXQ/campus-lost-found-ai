# -*- coding: utf-8 -*-
"""
ImageNet 标签 → 校园失物招领「标准类别」映射表

设计原则（与后端 PostController 的 13 个标准类别严格对齐）：
- 只保留能映射到 12 个「具体类别」的 ImageNet 标签；无法归类的标签一律不放进表里，
  由 classify() 兜底返回「其他」，避免大量标签被硬塞进「其他」污染 top-5 匹配。
- 映射值只能是 12 个具体标准类别之一，不出现「其他」值。

13 个标准类别：手机 / 钱包/包 / 钥匙 / 证件/卡 / 书包/背包 / 书籍 / 衣物 /
                水杯 / 眼镜 / 耳机 / 电脑/电子设备 / 雨伞 / 其他(兜底)

已知局限（ImageNet 通用分类与校园物品错配的硬伤）：
- 「钥匙」在 ImageNet 中没有对应类别，只能用 keyring / padlock / combination lock 近似。
- 「耳机」在 ImageNet 中没有对应类别，只能用 stethoscope(听诊器) 近似（外形最接近）。
- 「证件/卡」在 ImageNet 中完全没有对应类别，只能用 envelope(信封) 近似，效果有限。
  以上三类识别准确率天然受限，彻底解决需换 CLIP 零样本分类或校园数据集微调。
"""

IMAGENET_CATEGORY_MAP = {
    # ============ 手机 ============
    "cellular telephone, cellular phone, cellphone, mobile phone": "手机",

    # ============ 钱包/包 ============
    "wallet": "钱包/包",
    "purse": "钱包/包",
    "handbag, bag, pocketbook": "钱包/包",
    "briefcase": "钱包/包",
    "shopping basket": "钱包/包",
    "suitcase, traveling bag, travelling bag, bag, grip": "钱包/包",

    # ============ 钥匙（近似：ImageNet 只有锁/钥匙扣，无独立钥匙） ============
    "keyring": "钥匙",
    "combination lock": "钥匙",
    "lock, padlock": "钥匙",
    "padlock": "钥匙",

    # ============ 证件/卡（近似：ImageNet 无证件卡类） ============
    "envelope": "证件/卡",

    # ============ 书包/背包 ============
    "backpack, back pack, knapsack, packsack, rucksack, haversack": "书包/背包",

    # ============ 书籍 ============
    "book jacket, dust cover, dust jacket, dust wrapper": "书籍",
    "bookshop, bookstore, bookstall": "书籍",
    "comic book": "书籍",
    "library": "书籍",
    "magazine": "书籍",
    "newspaper": "书籍",

    # ============ 衣物（含帽子/手套/鞋/毛巾，与后端 CategorySynonym 别名对齐） ============
    "abaya": "衣物",
    "academic gown, academic robe, judge's robe": "衣物",
    "apron": "衣物",
    "baseball cap": "衣物",
    "bath towel": "衣物",
    "bathing cap, swimming cap": "衣物",
    "bikini, two-piece": "衣物",
    "bonnet, poke bonnet": "衣物",
    "brassiere, bra, bandeau": "衣物",
    "cardigan": "衣物",
    "clog, geta, patten, sabot": "衣物",
    "cowboy boot": "衣物",
    "cowboy hat": "衣物",
    "crash helmet": "衣物",
    "football helmet": "衣物",
    "gown": "衣物",
    "hand towel": "衣物",
    "jean, blue jean, denim": "衣物",
    "jersey, T-shirt, tee shirt": "衣物",
    "kimono": "衣物",
    "lab coat, laboratory coat": "衣物",
    "maillot": "衣物",
    "military uniform": "衣物",
    "miniskirt, mini": "衣物",
    "mitten": "衣物",
    "overcoat": "衣物",
    "parka, windcheater, anorak": "衣物",
    "poncho": "衣物",
    "running shoe": "衣物",
    "sandal": "衣物",
    "sarong": "衣物",
    "shoe shop, shoe-shop, shoe store": "衣物",
    "shower cap": "衣物",
    "sock": "衣物",
    "sombrero": "衣物",
    "suit, suit of clothes": "衣物",
    "sweat, sweatpants": "衣物",
    "sweatshirt": "衣物",
    "swimming trunks, bathing trunks": "衣物",
    "trench coat": "衣物",
    "vestment": "衣物",
    "wool, woolen, woollen": "衣物",

    # ============ 水杯（含水瓶/水壶/茶壶/咖啡壶，与后端 CategorySynonym 别名对齐） ============
    "beer bottle": "水杯",
    "beer glass": "水杯",
    "coffee mug": "水杯",
    "coffeepot": "水杯",
    "cup": "水杯",
    "goblet": "水杯",
    "pitcher, ewer": "水杯",
    "pop bottle, soda bottle": "水杯",
    "teapot": "水杯",
    "water bottle": "水杯",
    "water jug": "水杯",
    "wine bottle": "水杯",

    # ============ 眼镜 ============
    "glasses, eyeglasses, spectacles": "眼镜",
    "goggles": "眼镜",
    "sunglass": "眼镜",
    "sunglasses, dark glasses, shades": "眼镜",

    # ============ 耳机（近似：ImageNet 无耳机类，stethoscope 外形最接近） ============
    "stethoscope": "耳机",

    # ============ 电脑/电子设备（含电脑/鼠标/键盘/相机/手表/电器等，与后端 CategorySynonym 别名对齐） ============
    "CD player": "电脑/电子设备",
    "PDA, personal digital assistant": "电脑/电子设备",
    "adapter": "电脑/电子设备",
    "air conditioner": "电脑/电子设备",
    "amplifier": "电脑/电子设备",
    "analog clock": "电脑/电子设备",
    "battery": "电脑/电子设备",
    "camcorder": "电脑/电子设备",
    "camera, photographic camera": "电脑/电子设备",
    "cassette": "电脑/电子设备",
    "ceiling fan": "电脑/电子设备",
    "charger": "电脑/电子设备",
    "coffee maker": "电脑/电子设备",
    "computer keyboard": "电脑/电子设备",
    "computer mouse, mouse": "电脑/电子设备",
    "dial telephone, dial phone": "电脑/电子设备",
    "digital camera": "电脑/电子设备",
    "digital watch": "电脑/电子设备",
    "dishwasher, dish washer, dishwashing machine": "电脑/电子设备",
    "electric fan, blower": "电脑/电子设备",
    "fax machine, facsimile, facsimile machine": "电脑/电子设备",
    "flash, photo flash": "电脑/电子设备",
    "flashlight, torch": "电脑/电子设备",
    "floppy disk, diskette": "电脑/电子设备",
    "freezer": "电脑/电子设备",
    "hand blower, blow dryer, blow drier, hair dryer, hairdryer": "电脑/电子设备",
    "hand-held computer": "电脑/电子设备",
    "hard disc, hard disk, fixed disk": "电脑/电子设备",
    "hourglass": "电脑/电子设备",
    "iPod": "电脑/电子设备",
    "iron, smoothing iron": "电脑/电子设备",
    "joystick": "电脑/电子设备",
    "keyboard, computer keyboard": "电脑/电子设备",
    "laptop, laptop computer": "电脑/电子设备",
    "lens cap, lens cover": "电脑/电子设备",
    "loudspeaker": "电脑/电子设备",
    "microphone, mike": "电脑/电子设备",
    "microwave, microwave oven": "电脑/电子设备",
    "modem": "电脑/电子设备",
    "monitor": "电脑/电子设备",
    "mouse, computer mouse": "电脑/电子设备",
    "notebook, notebook computer": "电脑/电子设备",
    "optical disc, compact disc": "电脑/电子设备",
    "palmtop": "电脑/电子设备",
    "photocopier": "电脑/电子设备",
    "power strip, plug strip, power strip, plug board": "电脑/电子设备",
    "printer": "电脑/电子设备",
    "projector": "电脑/电子设备",
    "radiator": "电脑/电子设备",
    "radio, wireless": "电脑/电子设备",
    "refrigerator, icebox": "电脑/电子设备",
    "remote control, remote": "电脑/电子设备",
    "scanner, optical scanner": "电脑/电子设备",
    "screen, CRT screen": "电脑/电子设备",
    "slide projector": "电脑/电子设备",
    "space bar": "电脑/电子设备",
    "space heater": "电脑/电子设备",
    "speaker": "电脑/电子设备",
    "stopwatch": "电脑/电子设备",
    "stopwatch, stop watch": "电脑/电子设备",
    "tape player": "电脑/电子设备",
    "television, television system": "电脑/电子设备",
    "toaster": "电脑/电子设备",
    "torch": "电脑/电子设备",
    "trackball": "电脑/电子设备",
    "typewriter keyboard": "电脑/电子设备",
    "vacuum, vacuum cleaner": "电脑/电子设备",
    "video camera": "电脑/电子设备",
    "videotape": "电脑/电子设备",
    "walkie-talkie": "电脑/电子设备",
    "wall clock": "电脑/电子设备",
    "wall socket, wall plug, electric outlet, electrical outlet, outlet, electric receptacle": "电脑/电子设备",
    "washer, automatic washer, washing machine": "电脑/电子设备",

    # ============ 雨伞 ============
    "umbrella": "雨伞",
}
