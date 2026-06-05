// Ch1 数值骨架：progression.yaml 的懒加载访问层。所有成长/技能等级/球成本消费方读这里。
var Progression = {
  _cfg: null,
  cfg: function() {
    if (this._cfg === null) {
      var raw = engine.loadYaml("progression.yaml");
      this._cfg = (typeof Skill !== "undefined" && Skill && Skill._toJs) ? Skill._toJs(raw) : raw;
    }
    return this._cfg;
  },
  reset: function() { this._cfg = null; },
  cap: function() { return parseInt(this.cfg().level.cap); },
  xpForLevel: function(level) {
    var x = this.cfg().level.xp_curve;
    return Math.round(parseFloat(x.base) * Math.pow(level, parseFloat(x.exp)));
  },
  skillCap: function() { return parseInt(this.cfg().skill_level.cap); },
  // 解析一个技能 spec 的曲线参数：level_curve(对象覆盖/字符串选档) > tier > default
  resolveCurve: function(spec) {
    var sl = this.cfg().skill_level;
    var def = sl.tiers[sl.default];
    var lc = spec ? spec.level_curve : null;
    if (lc && typeof lc === "object") {
      return {
        start: parseInt(lc.start != null ? lc.start : def.start),
        per:   parseInt(lc.per   != null ? lc.per   : def.per)
      };
    }
    var name = (lc && typeof lc === "string") ? lc
             : (spec && spec.tier ? String(spec.tier) : sl.default);
    var t = sl.tiers[name] || def;
    return { start: parseInt(t.start), per: parseInt(t.per) };
  },
  skillLevelFor: function(charLevel, spec) {
    var p = this.resolveCurve(spec);
    var lv = 1 + Math.floor((charLevel - p.start) / p.per);
    var cap = this.skillCap();
    if (lv < 1) lv = 1;
    if (lv > cap) lv = cap;
    return lv;
  },
  orbCost: function() { return parseInt(this.cfg().orb.cost_per_evolution); },
  // 怪经验:base * 怪等级^exp。怪可在 CombatantMeta.xpReward 显式覆盖（难度/种族/精英/剧情，Ch3）。
  xpReward: function(monsterLevel) {
    var r = this.cfg().xp_reward || { base: 14, exp: 0.9 };
    var lvl = parseInt(monsterLevel || 1);
    return Math.round(parseFloat(r.base) * Math.pow(lvl, parseFloat(r.exp)));
  }
};
