import {groepeerPassages} from '../src/js/passage-groepering.js';

describe('passage-groepering', () => {
  it('groepeert exact gelijke passages samen', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '007.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '012.txt', passage: 'Verkeer neemt toe'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen).to.have.length(2);
    expect(groepen[0].bezwaren).to.have.length(2);
    expect(groepen[0].passage).to.equal('De geluidshinder is onaanvaardbaar');
    expect(groepen[1].bezwaren).to.have.length(1);
  });

  it('groepeert fuzzy gelijkaardige passages (>=90% Dice)', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'De geluidshinder is onaanvaardbaar groot'},
      {bestandsnaam: '007.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '012.txt', passage: 'Verkeer neemt toe'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen).to.have.length(2);
    expect(groepen[0].passage).to.equal('De geluidshinder is onaanvaardbaar groot');
    expect(groepen[0].bezwaren).to.have.length(2);
  });

  it('houdt duidelijk verschillende passages apart', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '002.txt', passage: 'Het verkeer neemt dramatisch toe'},
      {bestandsnaam: '003.txt', passage: 'Natuurwaarden worden aangetast'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen).to.have.length(3);
  });

  it('sorteert groepen op aantal documenten (meest eerst)', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'Uniek bezwaar'},
      {bestandsnaam: '002.txt', passage: 'Veel voorkomend bezwaar'},
      {bestandsnaam: '003.txt', passage: 'Veel voorkomend bezwaar'},
      {bestandsnaam: '004.txt', passage: 'Veel voorkomend bezwaar'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen[0].bezwaren).to.have.length(3);
    expect(groepen[1].bezwaren).to.have.length(1);
  });

  it('retourneert lege array voor lege input', () => {
    expect(groepeerPassages([])).to.deep.equal([]);
  });

  it('behoudt alle originele bezwaar-data in groep', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'Geluid is te hard', extraVeld: 'waarde'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen[0].bezwaren[0].extraVeld).to.equal('waarde');
  });
});
