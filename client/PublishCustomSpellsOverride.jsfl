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

    frame.actionScript = source;
    doc.save();
    doc.publish();
    doc.close(false);
}

fl.quit();
