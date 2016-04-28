# Web Analyzer

Paralelně prochází domény a ukládá si data o nich, včetně informace, zda-li používají Nette Framework.
Pokud v hlavičce stránky je uvedeno `X-Powered-By: Nette Framework`, je stránka uložena, že používá nette.

Nastavení pomocí přepínačů

`--threads 100` použije 100 procesů, každý proces zvlášť dostane url z databáze, 
stáhne a vyhodnotí stránku, odešle informace o stránce do databáze.
Výchozí hodnota je 10.

`--export` vypíše na standartní výstup všechny weby, jenž používají Nette Framework

