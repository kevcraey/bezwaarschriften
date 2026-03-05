/**
 * Bereken Sørensen-Dice coëfficiënt tussen twee strings.
 * Gebaseerd op bigrammen van de genormaliseerde tekst.
 * @param {string} a - Eerste string.
 * @param {string} b - Tweede string.
 * @return {number} Coëfficiënt tussen 0.0 en 1.0.
 */
function diceCoefficient(a, b) {
  const normA = a.toLowerCase().trim();
  const normB = b.toLowerCase().trim();
  if (normA === normB) return 1.0;
  if (normA.length < 2 || normB.length < 2) return 0.0;

  const bigrammen = (s) => {
    const set = new Map();
    for (let i = 0; i < s.length - 1; i++) {
      const bigram = s.substring(i, i + 2);
      set.set(bigram, (set.get(bigram) || 0) + 1);
    }
    return set;
  };

  const bigrammenA = bigrammen(normA);
  const bigrammenB = bigrammen(normB);
  let overlap = 0;
  for (const [bigram, count] of bigrammenA) {
    if (bigrammenB.has(bigram)) {
      overlap += Math.min(count, bigrammenB.get(bigram));
    }
  }

  const totaal = normA.length - 1 + normB.length - 1;
  return (2 * overlap) / totaal;
}

const GELIJKENIS_DREMPEL = 0.9;

/**
 * Groepeer individuele bezwaren op basis van passage-gelijkenis.
 * Retourneert array van { passage, bezwaren } gesorteerd op groepsgrootte (aflopend).
 * @param {Array<Object>} bezwaren - Array van bezwaar-objecten met een `passage` property.
 * @return {Array<{passage: string, bezwaren: Array<Object>}>} Gegroepeerde bezwaren.
 */
export function groepeerPassages(bezwaren) {
  if (!bezwaren || bezwaren.length === 0) return [];

  const groepen = [];

  for (const bezwaar of bezwaren) {
    let gevonden = false;
    for (const groep of groepen) {
      if (diceCoefficient(bezwaar.passage, groep.passage) >= GELIJKENIS_DREMPEL) {
        groep.bezwaren.push(bezwaar);
        if (bezwaar.passage.length > groep.passage.length) {
          groep.passage = bezwaar.passage;
        }
        gevonden = true;
        break;
      }
    }
    if (!gevonden) {
      groepen.push({passage: bezwaar.passage, bezwaren: [bezwaar]});
    }
  }

  groepen.sort((a, b) => b.bezwaren.length - a.bezwaren.length);
  return groepen;
}
