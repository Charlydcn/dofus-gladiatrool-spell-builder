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
                    result.push([Number(p[0]), Number(p[1]), Number(p[2]), -1, 0, 0, p[3]]);
                }
            }
            return result;
        };

        CustomSpellTranslator.prototype._buildCustomSpellText = function(spellID, encoded)
        {
            var p = encoded.split("|");
            if (p.length < 17) return undefined;

            var normalEffects = this._buildCustomDamageEffects(p[15]);
            var criticalEffects = this._buildCustomDamageEffects(p[16]);
            var zones = "";
            var totalEffects = normalEffects.length + criticalEffects.length;
            for (var z = 0; z < totalEffects; z++) zones += "Pa";

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

            var text = {
                n:unescape(p[0]),
                d:unescape(p[1]),
                i:iconModel.i,
                b:iconModel.b,
                c:iconModel.c,
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
        customSpellsLoader.load("custom_spells.json");

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
        spellPatchesLoader.load("spell_patches.json");
        trace("[CUSTOM-SPELLS] override installé");
    }
}
else
{
    trace("[CUSTOM-SPELLS] DofusTranslator indisponible");
}
