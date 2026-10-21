-- Icon hint per catalog product, used by clients for placeholders while a product has no photo.
-- Vocabulary (documented in the OpenAPI contract, ProductSummary.iconKey):
--   apple banana carrot cherry citrus leafy-green pepper egg milk cheese beef sausage poultry fish
--   bread pastry grain legume pasta coffee tea soda beer sweets spice oil sauce nut
-- NULL falls back to the category's icon key. Merchants' private products start as NULL.

ALTER TABLE catalog_product ADD COLUMN icon_key varchar(40);

UPDATE catalog_product cp
   SET icon_key = CASE
         -- drinks before fruit ("Apfelschorle", "Sauerkirschnektar", "Mango Lassi")
         WHEN n ~ '(schorle|nektar|wasser|drink|lassi|kwas|getränk|bissap)' THEN 'soda'
         WHEN n ~ 'bier' THEN 'beer'
         WHEN n ~ '(kaffee|mokka)' THEN 'coffee'
         WHEN n ~ '(tee|chai)' THEN 'tea'
         -- sweets and pastries before bread and nuts ("Tahin-Halva mit Pistazien", "Dattel-Maamoul")
         WHEN n ~ '(halva|baklava|lokum|schokolade|marzipan|mleczko|gulab|maamoul|chin chin|honig)' THEN 'sweets'
         WHEN n ~ '(kuchen|bienenstich|croissant|franzbrötchen|börek)' THEN 'pastry'
         WHEN n ~ '(brot|brötchen|naan|pide|simit|ka''ak|brezel)' THEN 'bread'
         WHEN n ~ '(sirup|sauce|paste|tomatenmark|paprikamark|senf|harissa|^tahin$)' THEN 'sauce'
         WHEN n ~ '(granatapfel|kirsch)' THEN 'cherry'
         WHEN n ~ 'äpfel|apfel' THEN 'apple'
         WHEN n ~ 'banane' THEN 'banana'
         WHEN n ~ '(zitrone|mango)' THEN 'citrus'
         WHEN n ~ '(eier aus|^eier)' THEN 'egg'
         WHEN n ~ '(käse|halloumi|mozzarella|parmigiano|paneer|twaróg)' THEN 'cheese'
         WHEN n ~ '(milch|joghurt|ayran|kefir|kaymak|labneh|butter|ghee)' THEN 'milk'
         WHEN n ~ '(nudel|spaghetti|penne|ramen|gyoza|pierogi|samosa)' THEN 'pasta'
         WHEN n ~ '(hähnchen|huhn)' THEN 'poultry'
         WHEN n ~ '(sucuk|merguez|kabanos|krakauer|wurst)' THEN 'sausage'
         WHEN n ~ '(lamm|rind|gulasch|hackfleisch|pastırma)' THEN 'beef'
         WHEN n ~ '(fisch|tilapia)' THEN 'fish'
         WHEN n ~ '(reis|bulgur|couscous|buchweizen|freekeh|hafer|mehl|gari)' THEN 'grain'
         WHEN n ~ '(linsen|kichererbsen|dal|ful medames|hummus)' THEN 'legume'
         WHEN n ~ '(masala|kardamom|kurkuma|kreuzkümmel|sumach|za''atar|pul biber)' THEN 'spice'
         WHEN n ~ 'oliven' THEN 'oil'
         WHEN n ~ '(datteln|sonnenblumenkerne|pistazie)' THEN 'nut'
         WHEN n ~ '(paprika|chili|tomaten)' THEN 'pepper'
         WHEN n ~ '(möhren|rote bete|maniok|yam|kartoffel|ingwer|knoblauch|zwiebel|kürbis)' THEN 'carrot'
         WHEN n ~ '(spinat|grünkohl|minze|petersilie|dill|koriander|pak choi|okra|molokhia|weinblätter|zucchini|auberg|gurke|kimchi)' THEN 'leafy-green'
       END
  FROM (SELECT catalog_product_id, lower(name) AS n FROM catalog_product_translation WHERE locale = 'de') de
 WHERE de.catalog_product_id = cp.id;
