function berekenBigramInfo(s) {
  const norm = s.toLowerCase().trim();
  const bigrammen = new Map();
  for (let i = 0; i < norm.length - 1; i++) {
    const bigram = norm.substring(i, i + 2);
    bigrammen.set(bigram, (bigrammen.get(bigram) || 0) + 1);
  }
  return {norm, bigrammen, lengte: norm.length};
}

function diceMetBigrammen(infoA, infoB) {
  if (infoA.norm === infoB.norm) return 1.0;
  if (infoA.lengte < 2 || infoB.lengte < 2) return 0.0;
  let overlap = 0;
  for (const [bigram, count] of infoA.bigrammen) {
    if (infoB.bigrammen.has(bigram)) {
      overlap += Math.min(count, infoB.bigrammen.get(bigram));
    }
  }
  return (2 * overlap) / (infoA.lengte - 1 + infoB.lengte - 1);
}

const GELIJKENIS_DREMPEL = 0.9;

/**
 * Groepeer individuele bezwaren op basis van passage-gelijkenis.
 * Retourneert array van { passage, bezwaren, maxScore } gesorteerd op maxScore (aflopend, nulls last).
 * @param {Array<Object>} bezwaren - Array van bezwaar-objecten met een `passage` property.
 * @return {Array<Object>} Gegroepeerde bezwaren met passage, bezwaren en maxScore.
 */
export function groepeerPassages(bezwaren) {
  if (!bezwaren || bezwaren.length === 0) return [];

  const groepen = [];
  const groepBigramInfo = [];

  for (const bezwaar of bezwaren) {
    const bezwaarInfo = berekenBigramInfo(bezwaar.passage);
    let gevonden = false;
    for (let i = 0; i < groepen.length; i++) {
      if (diceMetBigrammen(bezwaarInfo, groepBigramInfo[i]) >= GELIJKENIS_DREMPEL) {
        const groep = groepen[i];
        groep.bezwaren.push(bezwaar);
        if (bezwaar.scorePercentage != null) {
          groep.maxScore = groep.maxScore != null ?
            Math.max(groep.maxScore, bezwaar.scorePercentage) :
            bezwaar.scorePercentage;
        }
        if (bezwaar.passage.length > groep.passage.length) {
          groep.passage = bezwaar.passage;
          groepBigramInfo[i] = bezwaarInfo;
        }
        gevonden = true;
        break;
      }
    }
    if (!gevonden) {
      groepen.push({
        passage: bezwaar.passage,
        bezwaren: [bezwaar],
        maxScore: bezwaar.scorePercentage ?? null,
      });
      groepBigramInfo.push(bezwaarInfo);
    }
  }

  groepen.sort((a, b) => {
    if (a.maxScore == null && b.maxScore == null) return b.bezwaren.length - a.bezwaren.length;
    if (a.maxScore == null) return 1;
    if (b.maxScore == null) return -1;
    return b.maxScore - a.maxScore;
  });
  return groepen;
}
