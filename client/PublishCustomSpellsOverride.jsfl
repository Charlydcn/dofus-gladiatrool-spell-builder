var corePath = "file:///C|/PROJET%20DOFUS%20RETRO/client/resources/app/retroclient/modules/core.fla";
var doc = fl.openDocument(corePath);

if (doc == null)
{
    alert("core.fla introuvable : " + corePath);
}
else
{
    var frame = doc.getTimeline().layers[0].frames[0];
    var source = frame.actionScript;
    var oldZones = 'var zones = "";\n            var totalEffects = normalEffects.length + criticalEffects.length;\n            for (var z = 0; z < totalEffects; z++) zones += "Pa";';
    var newZones = 'var effectZone = p.length >= 19 && p[18].length >= 2 ? p[18] : "Pa";\n            var zones = "";\n            var totalEffects = normalEffects.length + criticalEffects.length;\n            for (var z = 0; z < totalEffects; z++) zones += effectZone;';

    source = source.split(oldZones).join(newZones);
    source = source.split('customSpellsLoader.load("custom_spells.json");')
                   .join('customSpellsLoader.load("custom_spells.json?cache=" + getTimer());');
    source = source.split('spellPatchesLoader.load("spell_patches.json");')
                   .join('spellPatchesLoader.load("spell_patches.json?cache=" + getTimer());');

    // Les quatre remplacements suivants rendent l'ancien override compatible
    // avec les icônes directes, les catégories de classe et les effets sans condition.
    source = source.split('result.push([Number(p[0]), Number(p[1]), Number(p[2]), -1, 0, 0, p[3]]);')
                   .join('result.push([Number(p[0]), Number(p[1]), Number(p[2]), -1, 0, 0]);');
    source = source.split('                b:iconModel.b,\n                c:iconModel.c,')
                   .join('                b:_global.API.datacenter.Player.Guild,\n                c:10,');
    source = source.split('                b:_global.API.datacenter.Player.Guild,\n                c:10,')
                   .join('                b:p.length >= 21 && Number(p[20]) > 0 ? Number(p[20]) : _global.API.datacenter.Player.Guild,\n                c:10,');
    source = source.split('                c:10,').join('                c:1,');

    if (source.indexOf('prototype._decodeCustomText') < 0)
    {
        source = source.split('        CustomSpellTranslator.prototype._buildCustomSpellText = function(spellID, encoded)')
                       .join('        CustomSpellTranslator.prototype._decodeCustomText = function(encoded)\n        {\n            var result = "";\n            for (var i = 0; i < encoded.length; i++)\n            {\n                if (encoded.charAt(i) == "%" && encoded.charAt(i + 1) == "u" && i + 5 < encoded.length)\n                {\n                    result += String.fromCharCode(parseInt(encoded.substr(i + 2, 4), 16));\n                    i += 5;\n                }\n                else if (encoded.charAt(i) == "%" && i + 2 < encoded.length)\n                {\n                    result += String.fromCharCode(parseInt(encoded.substr(i + 1, 2), 16));\n                    i += 2;\n                }\n                else result += encoded.charAt(i);\n            }\n            return result;\n        };\n\n        CustomSpellTranslator.prototype._buildCustomSpellText = function(spellID, encoded)');
    }
    source = source.split('n:unescape(p[0]),').join('n:this._decodeCustomText(p[0]),');
    source = source.split('d:unescape(p[1]),').join('d:this._decodeCustomText(p[1]),');
    source = source.split('patchedText.n = unescape(p[15]);').join('patchedText.n = this._decodeCustomText(p[15]);');
    source = source.split('patchedText.d = unescape(p[16]);').join('patchedText.d = this._decodeCustomText(p[16]);');
    source = source.split('result += String.fromCharCode(parseInt(encoded.substr(i + 1, 2), 16));')
                   .join('var byteValue = parseInt(encoded.substr(i + 1, 2), 16);\n                    var windows1252Bytes = [128,130,131,132,133,134,135,136,137,138,139,140,142,145,146,147,148,149,150,151,152,153,154,155,156,158,159];\n                    var windows1252Chars = [8364,8218,402,8222,8230,8224,8225,710,8240,352,8249,338,381,8216,8217,8220,8221,8226,8211,8212,732,8482,353,8250,339,382,376];\n                    for (var w = 0; w < windows1252Bytes.length; w++) if (windows1252Bytes[w] == byteValue) byteValue = windows1252Chars[w];\n                    result += String.fromCharCode(byteValue);');
    source = source.split('var windows1252 = {128:8364,130:8218,131:402,132:8222,133:8230,134:8224,135:8225,136:710,137:8240,138:352,139:8249,140:338,142:381,145:8216,146:8217,147:8220,148:8221,149:8226,150:8211,151:8212,152:732,153:8482,154:353,155:8250,156:339,158:382,159:376};\n                    result += String.fromCharCode(windows1252[byteValue] == undefined ? byteValue : windows1252[byteValue]);')
                   .join('var windows1252Bytes = [128,130,131,132,133,134,135,136,137,138,139,140,142,145,146,147,148,149,150,151,152,153,154,155,156,158,159];\n                    var windows1252Chars = [8364,8218,402,8222,8230,8224,8225,710,8240,352,8249,338,381,8216,8217,8220,8221,8226,8211,8212,732,8482,353,8250,339,382,376];\n                    for (var w = 0; w < windows1252Bytes.length; w++) if (windows1252Bytes[w] == byteValue) byteValue = windows1252Chars[w];\n                    result += String.fromCharCode(byteValue);');

    if (source.indexOf('var directIconID = p.length >= 20') < 0)
    {
        source = source.split('            var iconModel = this._getSpellTextBeforeCustom(iconSpellID);\n            if (iconModel == undefined) return undefined;')
                       .join('            var iconModel = this._getSpellTextBeforeCustom(iconSpellID);\n            if (iconModel == undefined) return undefined;\n            var directIconID = p.length >= 20 && p[19].length > 0 ? Number(p[19]) : undefined;\n            var iconProperties = iconModel.i;\n            if (directIconID != undefined)\n            {\n                iconProperties = {};\n                for (var iconKey in iconModel.i) iconProperties[iconKey] = iconModel.i[iconKey];\n                iconProperties.up = directIconID;\n            }');
        source = source.split('                i:iconModel.i,').join('                i:iconProperties,');
    }

    if (source.indexOf('p.length >= 13 && p[12].length > 0') < 0)
    {
        source = source.split('            patchedText.l6 = level;\n            return patchedText;')
                       .join('            patchedText.l6 = level;\n            if (p.length >= 13 && p[12].length > 0)\n            {\n                var iconModel = this._getSpellTextBeforeCustom(Number(p[12]));\n                if (iconModel != undefined)\n                {\n                    var iconProperties = iconModel.i;\n                    if (p.length >= 14 && p[13].length > 0)\n                    {\n                        iconProperties = {};\n                        for (var iconKey in iconModel.i) iconProperties[iconKey] = iconModel.i[iconKey];\n                        iconProperties.up = Number(p[13]);\n                    }\n                    patchedText.i = iconProperties;\n                }\n            }\n            return patchedText;');
    }
    // Reconstruction idempotente : retire les anciennes variantes et doublons,
    // puis réinsère une seule surcharge de texte avec le décodeur dédié.
    var decodedTextPatch = '            if (p.length >= 17 && p[14] == "1")\n            {\n                patchedText.n = this._decodeCustomText(p[15]);\n                patchedText.d = this._decodeCustomText(p[16]);\n            }\n';
    var legacyTextPatch = '            if (p.length >= 17 && p[14] == "1")\n            {\n                patchedText.n = unescape(p[15]);\n                patchedText.d = unescape(p[16]);\n            }\n';
    source = source.split(decodedTextPatch).join('');
    source = source.split(legacyTextPatch).join('');
    source = source.split('            return patchedText;\n        };')
                   .join(decodedTextPatch + '            return patchedText;\n        };');

    frame.actionScript = source;
    doc.save();
    doc.publish();
    doc.close(false);
}

fl.quit();
