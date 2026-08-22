// À coller dans la frame 1 de core.fla, puis republier core.swf une seule fois.
// Le générateur met ensuite à jour custom_spells.json sans nouvelle compilation.

if (_global.dofus != undefined
    && _global.dofus.utils != undefined
    && _global.dofus.utils.DofusTranslator != undefined)
{
    var CustomSpellTranslator = _global.dofus.utils.DofusTranslator;

    if (CustomSpellTranslator.prototype._getSpellTextBeforeCustom == undefined)
    {
        CustomSpellTranslator.prototype._getSpellTextBeforeCustom = CustomSpellTranslator.prototype.getSpellText;

        CustomSpellTranslator.prototype._buildCustomDamageEffects = function(encoded)
        {
            var result = [];
            if (encoded == undefined || encoded.length == 0) return result;
            var rows = encoded.split(";");
            for (var i = 0; i < rows.length; i++)
            {
                var p = rows[i].split(",");
                if (p.length >= 4)
                {
                    // Ne pas renseigner l'index 6 : le client l'interprète comme
                    // une condition d'effet et affiche sinon une aide « Conditions - ».
                    result.push([Number(p[0]), Number(p[1]), Number(p[2]), -1, 0, 0]);
                }
            }
            return result;
        };

        CustomSpellTranslator.prototype._decodeCustomText = function(encoded)
        {
            var result = "";
            for (var i = 0; i < encoded.length; i++)
            {
                if (encoded.charAt(i) == "%" && encoded.charAt(i + 1) == "u" && i + 5 < encoded.length)
                {
                    result += String.fromCharCode(parseInt(encoded.substr(i + 2, 4), 16));
                    i += 5;
                }
                else if (encoded.charAt(i) == "%" && i + 2 < encoded.length)
                {
                    var byteValue = parseInt(encoded.substr(i + 1, 2), 16);
                    var windows1252Bytes = [128,130,131,132,133,134,135,136,137,138,139,140,142,145,146,147,148,149,150,151,152,153,154,155,156,158,159];
                    var windows1252Chars = [8364,8218,402,8222,8230,8224,8225,710,8240,352,8249,338,381,8216,8217,8220,8221,8226,8211,8212,732,8482,353,8250,339,382,376];
                    for (var w = 0; w < windows1252Bytes.length; w++) if (windows1252Bytes[w] == byteValue) byteValue = windows1252Chars[w];
                    result += String.fromCharCode(byteValue);
                    i += 2;
                }
                else result += encoded.charAt(i);
            }
            return result;
        };

        CustomSpellTranslator.prototype._buildCustomSpellText = function(spellID, encoded)
        {
            var p = encoded.split("|");
            if (p.length < 17) return undefined;

            var normalEffects = this._buildCustomDamageEffects(p[15]);
            var criticalEffects = this._buildCustomDamageEffects(p[16]);
            var effectZone = p.length >= 19 && p[18].length >= 2 ? p[18] : "Pa";
            var zones = "";
            var totalEffects = normalEffects.length + criticalEffects.length;
            for (var z = 0; z < totalEffects; z++) zones += effectZone;

            var level = [
                normalEffects,
                criticalEffects,
                Number(p[2]),
                Number(p[3]),
                Number(p[4]),
                Number(p[5]),
                Number(p[6]),
                p[7] == "1",
                p[8] == "1",
                false,
                p[9] == "1",
                Number(p[10]),
                Number(p[11]),
                Number(p[12]),
                Number(p[13]),
                zones,
                [],
                [],
                1,
                p[14] == "1",
                Number(spellID) * 10 + 6
            ];

            var iconSpellID = p.length >= 18 ? Number(p[17]) : 176;
            var iconModel = this._getSpellTextBeforeCustom(iconSpellID);
            if (iconModel == undefined) return undefined;
            // p[19] est facultatif : les anciennes entrées gardent l'icône du sort modèle.
            var directIconID = p.length >= 20 && p[19].length > 0 ? Number(p[19]) : undefined;
            var iconProperties = iconModel.i;
            if (directIconID != undefined)
            {
                iconProperties = {};
                for (var iconKey in iconModel.i) iconProperties[iconKey] = iconModel.i[iconKey];
                iconProperties.up = directIconID;
            }

            var text = {
                n:this._decodeCustomText(p[0]),
                d:this._decodeCustomText(p[1]),
                i:iconProperties,
                // Tous les sorts créés par le builder sont des sorts de classe.
                // Ne pas hériter de la catégorie du sort utilisé comme modèle d'icône.
                b:p.length >= 21 && Number(p[20]) > 0 ? Number(p[20]) : _global.API.datacenter.Player.Guild,
                c:1,
                t:iconModel.t,
                o:iconModel.o,
                p:false,
                g:false
            };

            // Le client consulte l1 pendant l'initialisation, même si le serveur donne le grade 6.
            for (var grade = 1; grade <= 6; grade++) text["l" + grade] = level;
            return text;
        };

        CustomSpellTranslator.prototype._applyCustomGradePatch = function(spellText, encoded)
        {
            if (spellText == undefined || encoded == undefined) return spellText;
            var p = encoded.split("|");
            if (p.length < 12 || spellText.l6 == undefined) return spellText;

            // Copie l'objet et le grade 6 afin de conserver les effets, le nom et l'icône vanilla.
            var patchedText = {};
            for (var key in spellText) patchedText[key] = spellText[key];
            var originalLevel = spellText.l6;
            var level = [];
            for (var i = 0; i < originalLevel.length; i++) level[i] = originalLevel[i];

            level[2] = Number(p[0]);  // PA
            level[3] = Number(p[1]);  // PO min
            level[4] = Number(p[2]);  // PO max
            level[5] = Number(p[3]);  // CC
            level[6] = Number(p[4]);  // EC
            level[7] = p[5] == "1";   // ligne
            level[8] = p[6] == "1";   // ligne de vue
            level[10] = p[7] == "1";  // PO modifiable
            level[12] = Number(p[8]); // maximum par tour
            level[13] = Number(p[9]); // maximum par cible
            level[14] = Number(p[10]); // relance
            level[19] = p[11] == "1"; // EC termine le tour
            patchedText.l6 = level;

            // Champs facultatifs ajoutés après les 12 paramètres historiques.
            if (p.length >= 13 && p[12].length > 0)
            {
                var iconModel = this._getSpellTextBeforeCustom(Number(p[12]));
                if (iconModel != undefined)
                {
                    var iconProperties = iconModel.i;
                    if (p.length >= 14 && p[13].length > 0)
                    {
                        iconProperties = {};
                        for (var iconKey in iconModel.i) iconProperties[iconKey] = iconModel.i[iconKey];
                        iconProperties.up = Number(p[13]);
                    }
                    patchedText.i = iconProperties;
                }
            }
            if (p.length >= 17 && p[14] == "1")
            {
                patchedText.n = this._decodeCustomText(p[15]);
                patchedText.d = this._decodeCustomText(p[16]);
            }
            return patchedText;
        };

        CustomSpellTranslator.prototype.getSpellText = function(spellID)
        {
            var records = _global.CUSTOM_SPELL_RECORDS;
            var encoded = records == undefined ? undefined : records[String(spellID)];
            var spellText;
            if (encoded != undefined)
            {
                if (_global.CUSTOM_SPELL_TEXT_CACHE == undefined) _global.CUSTOM_SPELL_TEXT_CACHE = {};
                if (_global.CUSTOM_SPELL_TEXT_CACHE[spellID] == undefined)
                {
                    _global.CUSTOM_SPELL_TEXT_CACHE[spellID] = this._buildCustomSpellText(spellID, encoded);
                }
                spellText = _global.CUSTOM_SPELL_TEXT_CACHE[spellID];
            }
            else
            {
                spellText = this._getSpellTextBeforeCustom(spellID);
            }
            var patches = _global.CUSTOM_SPELL_GRADE_PATCHES;
            var patch = patches == undefined ? undefined : patches[String(spellID)];
            return this._applyCustomGradePatch(spellText, patch);
        };

        _global.CUSTOM_SPELL_RECORDS = {};
        _global.CUSTOM_SPELL_GRADE_PATCHES = {};
        _global.CUSTOM_SPELL_TEXT_CACHE = {};

        var customSpellsLoader = new LoadVars();
        customSpellsLoader.onData = function(rawData)
        {
            if (rawData == undefined || rawData.length == 0)
            {
                trace("[CUSTOM-SPELLS] custom_spells.json absent ou vide");
                return;
            }

            var parser = {};
            parser._parseJsonValue = _global.dofus.DofusLoader.prototype._parseJsonValue;
            parser._parseJsonFlat = _global.dofus.DofusLoader.prototype._parseJsonFlat;
            if (parser._parseJsonValue == undefined || parser._parseJsonFlat == undefined)
            {
                trace("[CUSTOM-SPELLS] parseur JSON du loader indisponible");
                return;
            }

            _global.CUSTOM_SPELL_RECORDS = parser._parseJsonFlat(rawData);
            _global.CUSTOM_SPELL_TEXT_CACHE = {};
            trace("[CUSTOM-SPELLS] données chargées");
        };
        // Évite qu'une ancienne version reste dans le cache après la création
        // d'un nouveau sort.
        customSpellsLoader.load("custom_spells.json?cache=" + getTimer());

        var spellPatchesLoader = new LoadVars();
        spellPatchesLoader.onData = function(rawData)
        {
            if (rawData == undefined || rawData.length == 0)
            {
                trace("[CUSTOM-SPELLS] spell_patches.json absent ou vide");
                return;
            }
            var parser = {};
            parser._parseJsonValue = _global.dofus.DofusLoader.prototype._parseJsonValue;
            parser._parseJsonFlat = _global.dofus.DofusLoader.prototype._parseJsonFlat;
            if (parser._parseJsonValue == undefined || parser._parseJsonFlat == undefined) return;
            _global.CUSTOM_SPELL_GRADE_PATCHES = parser._parseJsonFlat(rawData);
            trace("[CUSTOM-SPELLS] patchs de grades chargés");
        };
        spellPatchesLoader.load("spell_patches.json?cache=" + getTimer());
        trace("[CUSTOM-SPELLS] override installé");
    }
}
else
{
    trace("[CUSTOM-SPELLS] DofusTranslator indisponible");
}
